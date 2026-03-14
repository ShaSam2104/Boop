package com.shashsam.boop.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "TransferManager"

/** Size of each read/write chunk in bytes. */
private const val CHUNK_SIZE = 16 * 1024  // 16 KB

/** Milliseconds to wait for an incoming client connection on the [ServerSocket]. */
private const val SERVER_ACCEPT_TIMEOUT_MS = 30_000

/**
 * A snapshot of the current file transfer state.
 *
 * @param bytesTransferred Bytes transferred so far.
 * @param totalBytes       Total bytes to transfer (from the wire header).
 * @param isComplete       `true` when the transfer finished successfully.
 * @param savedUri         [Uri] of the saved file on the Receiver's device (set on completion).
 * @param error            Non-null when an error occurred.
 */
data class TransferProgress(
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false,
    val savedUri: Uri? = null,
    val error: String? = null
) {
    /**
     * Transfer progress as a fraction in the range [0, 1].
     * Safe to use directly as the value for `LinearProgressIndicator`.
     */
    val fraction: Float
        get() = if (totalBytes > 0L)
            (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else 0f
}

/**
 * File metadata exchanged in the TCP wire header before the raw byte payload.
 */
internal data class FileTransferHeader(
    val name: String,
    val size: Long,
    val mimeType: String
)

/**
 * Manages TCP file transfers over an established Wi-Fi Direct connection.
 *
 * ## Wire format
 * ```
 * [4 bytes] name length (big-endian Int)
 * [n bytes] file name (UTF-8)
 * [8 bytes] file size (big-endian Long)
 * [4 bytes] MIME type length (big-endian Int)
 * [m bytes] MIME type (UTF-8)
 * [size bytes] raw file bytes
 * ```
 *
 * ## Usage
 * ```kotlin
 * // Sender
 * TransferManager.sendFile(context, fileUri, port = 8765).collect { progress -> … }
 *
 * // Receiver (connect to GROUP_OWNER_IP = "192.168.49.1")
 * TransferManager.receiveFile(context, "192.168.49.1", port = 8765).collect { progress -> … }
 * ```
 *
 * All I/O runs on [Dispatchers.IO].
 */
object TransferManager {

    /**
     * **Sender side** — opens a [ServerSocket] on [port], accepts one client, sends
     * the file at [fileUri] from MediaStore, and emits [TransferProgress] updates.
     *
     * @param context Application context for [ContentResolver][android.content.ContentResolver].
     * @param fileUri URI of the file to send (from the file picker).
     * @param port    TCP port to listen on.
     */
    fun sendFile(context: Context, fileUri: Uri, port: Int): Flow<TransferProgress> = flow {
        Log.d(TAG, "sendFile: uri=$fileUri port=$port")
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = ServerSocket(port).apply { soTimeout = SERVER_ACCEPT_TIMEOUT_MS }
                Log.d(TAG, "Listening on port $port (timeout=${SERVER_ACCEPT_TIMEOUT_MS}ms)")
                emit(TransferProgress())

                clientSocket = serverSocket.accept()
                Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")

                val header = resolveFileHeader(context, fileUri)
                    ?: throw IllegalArgumentException("Cannot resolve file metadata for: $fileUri")
                Log.d(TAG, "Sending — name=${header.name} size=${header.size} mime=${header.mimeType}")

                val dataOut = DataOutputStream(clientSocket.getOutputStream().buffered())
                writeHeader(dataOut, header)

                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    streamBytes(
                        source = inputStream,
                        destination = clientSocket.getOutputStream(),
                        totalSize = header.size
                    ) { transferred ->
                        emit(TransferProgress(transferred, header.size))
                    }
                    dataOut.flush()
                    Log.d(TAG, "File sent: ${header.size} bytes")
                    emit(TransferProgress(header.size, header.size, isComplete = true))
                } ?: throw IllegalStateException("Cannot open InputStream for: $fileUri")

            } catch (e: Exception) {
                Log.e(TAG, "sendFile error", e)
                emit(TransferProgress(error = e.message ?: "Send failed"))
            } finally {
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
                Log.d(TAG, "sendFile cleanup done")
            }
        }
    }

    /**
     * **Receiver side** — connects to [groupOwnerAddress]:[port], reads the incoming
     * file, saves it via MediaStore (scoped-storage compliant), and emits
     * [TransferProgress] updates.
     *
     * @param context            Application context.
     * @param groupOwnerAddress  IP of the Wi-Fi Direct Group Owner (typically `192.168.49.1`).
     * @param port               TCP port to connect to.
     */
    fun receiveFile(
        context: Context,
        groupOwnerAddress: String,
        port: Int
    ): Flow<TransferProgress> = flow {
        Log.d(TAG, "receiveFile: host=$groupOwnerAddress port=$port")
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket(groupOwnerAddress, port)
                Log.d(TAG, "Connected to $groupOwnerAddress:$port")
                emit(TransferProgress())

                val dataIn = DataInputStream(socket.getInputStream().buffered())
                val header = readHeader(dataIn)
                Log.d(TAG, "Receiving — name=${header.name} size=${header.size} mime=${header.mimeType}")

                val outputUri = insertMediaStoreEntry(context, header)
                    ?: throw IllegalStateException("Failed to create MediaStore entry for: ${header.name}")

                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    streamBytes(
                        source = socket.getInputStream(),
                        destination = outputStream,
                        totalSize = header.size
                    ) { transferred ->
                        emit(TransferProgress(transferred, header.size))
                    }

                    // Remove IS_PENDING flag to publish the file (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.update(
                            outputUri,
                            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                            null, null
                        )
                    }
                    Log.d(TAG, "File received: ${header.size} bytes → $outputUri")
                    emit(TransferProgress(header.size, header.size, isComplete = true, savedUri = outputUri))
                } ?: throw IllegalStateException("Cannot open OutputStream for: $outputUri")

            } catch (e: Exception) {
                Log.e(TAG, "receiveFile error", e)
                emit(TransferProgress(error = e.message ?: "Receive failed"))
            } finally {
                runCatching { socket?.close() }
                Log.d(TAG, "receiveFile cleanup done")
            }
        }
    }

    // ─── Wire format helpers ──────────────────────────────────────────────────

    private fun writeHeader(out: DataOutputStream, header: FileTransferHeader) {
        val nameBytes = header.name.toByteArray(Charsets.UTF_8)
        val mimeBytes = header.mimeType.toByteArray(Charsets.UTF_8)
        out.writeInt(nameBytes.size)
        out.write(nameBytes)
        out.writeLong(header.size)
        out.writeInt(mimeBytes.size)
        out.write(mimeBytes)
        out.flush()
        Log.d(TAG, "Header written (name=${nameBytes.size}B mime=${mimeBytes.size}B size=${header.size})")
    }

    private fun readHeader(inp: DataInputStream): FileTransferHeader {
        val nameLen = inp.readInt()
        val nameBytes = ByteArray(nameLen).also { inp.readFully(it) }
        val size = inp.readLong()
        val mimeLen = inp.readInt()
        val mimeBytes = ByteArray(mimeLen).also { inp.readFully(it) }
        return FileTransferHeader(
            name = String(nameBytes, Charsets.UTF_8),
            size = size,
            mimeType = String(mimeBytes, Charsets.UTF_8)
        )
    }

    // ─── Byte streaming ───────────────────────────────────────────────────────

    /**
     * Pumps bytes from [source] to [destination] in [CHUNK_SIZE] chunks, calling
     * [onProgress] after each chunk with the running byte count.
     *
     * @return Total bytes transferred.
     */
    private suspend fun streamBytes(
        source: InputStream,
        destination: OutputStream,
        totalSize: Long,
        onProgress: suspend (Long) -> Unit
    ): Long {
        val buffer = ByteArray(CHUNK_SIZE)
        var bytesTransferred = 0L
        var read: Int
        while (source.read(buffer).also { read = it } != -1) {
            destination.write(buffer, 0, read)
            bytesTransferred += read
            val pct = if (totalSize > 0) bytesTransferred * 100 / totalSize else 0
            Log.d(TAG, "Progress: $bytesTransferred/$totalSize bytes ($pct%)")
            onProgress(bytesTransferred)
        }
        destination.flush()
        return bytesTransferred
    }

    // ─── MediaStore helpers ───────────────────────────────────────────────────

    /**
     * Resolves [FileTransferHeader] for [uri] using the [ContentResolver].
     */
    private fun resolveFileHeader(context: Context, uri: Uri): FileTransferHeader? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                ),
                null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "boop_file" else "boop_file"
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                FileTransferHeader(name = name, size = size, mimeType = mime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve file header for $uri", e)
            null
        }
    }

    /**
     * Inserts a new [MediaStore] entry for the file to be received.
     *
     * - **API 29+**: uses [MediaStore.Downloads] with `IS_PENDING = 1` for atomic writes.
     * - **API 26–28**: uses [Environment.DIRECTORY_DOWNLOADS] via legacy file path.
     *
     * @return URI of the new [MediaStore] entry, or `null` on failure.
     */
    private fun insertMediaStoreEntry(context: Context, header: FileTransferHeader): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, header.name)
                    put(MediaStore.Downloads.MIME_TYPE, header.mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA,
                        java.io.File(downloadsDir, header.name).absolutePath)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, header.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, header.mimeType)
                }
                context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert MediaStore entry for ${header.name}", e)
            null
        }
    }
}
