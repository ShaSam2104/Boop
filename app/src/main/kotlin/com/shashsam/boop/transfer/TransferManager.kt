package com.shashsam.boop.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "TransferManager"

/** Size of each read/write chunk in bytes. */
private const val CHUNK_SIZE = 256 * 1024  // 256 KB

/** Milliseconds to wait for an incoming client connection on the [ServerSocket]. */
private const val SERVER_ACCEPT_TIMEOUT_MS = 30_000

/** Socket buffer size for send/receive. */
private const val SOCKET_BUFFER_SIZE = 512 * 1024  // 512 KB

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
    val error: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileIndex: Int = 0,
    val totalFiles: Int = 1,
    val friendRequest: ProfileData? = null,
    val friendProfile: ProfileData? = null
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

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    /**
     * Force-closes any active server socket. Call before starting a new transfer
     * to avoid EADDRINUSE errors from stale sockets after cancel + re-send.
     */
    fun cleanup() {
        activeServerSocket?.let { socket ->
            Log.d(TAG, "cleanup: closing active server socket")
            runCatching { socket.close() }
            activeServerSocket = null
        }
    }

    /**
     * Creates a [ServerSocket] with SO_REUSEADDR to avoid EADDRINUSE on rapid reuse.
     */
    private fun createServerSocket(port: Int): ServerSocket {
        return ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
            soTimeout = SERVER_ACCEPT_TIMEOUT_MS
        }
    }

    /**
     * Configures socket buffers and TCP_NODELAY for optimal transfer speed.
     */
    private fun Socket.configureForTransfer() {
        sendBufferSize = SOCKET_BUFFER_SIZE
        receiveBufferSize = SOCKET_BUFFER_SIZE
        tcpNoDelay = true
    }

    /**
     * Connects to [host]:[port] with retry logic. The sender's server socket may not
     * be accepting yet when the receiver's Wi-Fi Direct connection completes, so we
     * retry a few times with a short delay.
     */
    private fun connectWithRetry(host: String, port: Int, maxRetries: Int = 5, delayMs: Long = 500): Socket {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.configureForTransfer()
                if (attempt > 0) {
                    Log.d(TAG, "TCP connect succeeded on attempt ${attempt + 1}")
                }
                return socket
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    Log.d(TAG, "TCP connect attempt ${attempt + 1} failed: ${e.message}, retrying in ${delayMs}ms")
                    Thread.sleep(delayMs)
                }
            }
        }
        throw lastException ?: java.io.IOException("Failed to connect to $host:$port after $maxRetries retries")
    }

    /**
     * **Sender side** — opens a [ServerSocket] on [port], accepts one client, sends
     * the file at [fileUri] from MediaStore, and emits [TransferProgress] updates.
     *
     * @param context Application context for [ContentResolver][android.content.ContentResolver].
     * @param fileUri URI of the file to send (from the file picker).
     * @param port    TCP port to listen on.
     */
    fun sendFile(context: Context, fileUri: Uri, port: Int): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "sendFile: uri=$fileUri port=$port")
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = createServerSocket(port)
                activeServerSocket = serverSocket
                Log.d(TAG, "Listening on port $port (timeout=${SERVER_ACCEPT_TIMEOUT_MS}ms)")
                send(TransferProgress())

                clientSocket = serverSocket.accept()
                clientSocket.configureForTransfer()
                Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")

                val header = resolveFileHeader(context, fileUri)
                    ?: throw IllegalArgumentException("Cannot resolve file metadata for: $fileUri")
                Log.d(TAG, "Sending — name=${header.name} size=${header.size} mime=${header.mimeType}")

                val dataOut = DataOutputStream(clientSocket.getOutputStream().buffered(CHUNK_SIZE))
                writeHeader(dataOut, header)

                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    streamBytes(
                        source = inputStream,
                        destination = dataOut,
                        totalSize = header.size
                    ) { transferred ->
                        send(TransferProgress(transferred, header.size))
                    }
                    dataOut.flush()
                    Log.d(TAG, "File sent: ${header.size} bytes")
                    send(TransferProgress(header.size, header.size, isComplete = true, fileName = header.name, mimeType = header.mimeType))
                } ?: throw IllegalStateException("Cannot open InputStream for: $fileUri")

            } catch (e: Exception) {
                Log.e(TAG, "sendFile error", e)
                send(TransferProgress(error = e.message ?: "Send failed"))
            } finally {
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
                activeServerSocket = null
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
        port: Int,
        customLocationUri: Uri? = null
    ): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "receiveFile: host=$groupOwnerAddress port=$port customLocation=$customLocationUri")
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = connectWithRetry(groupOwnerAddress, port)
                Log.d(TAG, "Connected to $groupOwnerAddress:$port")
                send(TransferProgress())

                val dataIn = DataInputStream(socket.getInputStream().buffered(CHUNK_SIZE))
                val header = readHeader(dataIn)
                Log.d(TAG, "Receiving — name=${header.name} size=${header.size} mime=${header.mimeType}")

                val outputUri = insertMediaStoreEntry(context, header, customLocationUri)
                    ?: throw IllegalStateException("Failed to create entry for: ${header.name}")

                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    streamBytes(
                        source = dataIn,
                        destination = outputStream,
                        totalSize = header.size
                    ) { transferred ->
                        send(TransferProgress(transferred, header.size))
                    }

                    // Remove IS_PENDING flag for MediaStore entries only (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaStoreUri(outputUri)) {
                        context.contentResolver.update(
                            outputUri,
                            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                            null, null
                        )
                    }
                    Log.d(TAG, "File received: ${header.size} bytes → $outputUri")
                    send(TransferProgress(header.size, header.size, isComplete = true, savedUri = outputUri, fileName = header.name, mimeType = header.mimeType))
                } ?: throw IllegalStateException("Cannot open OutputStream for: $outputUri")

            } catch (e: Exception) {
                Log.e(TAG, "receiveFile error", e)
                send(TransferProgress(error = e.message ?: "Receive failed"))
            } finally {
                runCatching { socket?.close() }
                Log.d(TAG, "receiveFile cleanup done")
            }
        }
    }

    /**
     * **Sender side** — sends multiple files over a single TCP connection.
     * Wire format: `[4 bytes fileCount] + [per-file: header + bytes]...`
     */
    fun sendFiles(context: Context, fileUris: List<Uri>, port: Int): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "sendFiles: ${fileUris.size} files, port=$port")
        val totalFiles = fileUris.size
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = createServerSocket(port)
                activeServerSocket = serverSocket
                Log.d(TAG, "Listening on port $port for multi-file transfer")
                send(TransferProgress(totalFiles = totalFiles))

                clientSocket = serverSocket.accept()
                clientSocket.configureForTransfer()
                Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")

                val dataOut = DataOutputStream(clientSocket.getOutputStream().buffered(CHUNK_SIZE))
                dataOut.writeInt(totalFiles)
                dataOut.flush()

                for ((index, fileUri) in fileUris.withIndex()) {
                    val header = resolveFileHeader(context, fileUri)
                        ?: throw IllegalArgumentException("Cannot resolve file metadata for: $fileUri")
                    Log.d(TAG, "Sending file ${index + 1}/$totalFiles — name=${header.name} size=${header.size}")

                    writeHeader(dataOut, header)
                    context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                        streamBytes(
                            source = inputStream,
                            destination = dataOut,
                            totalSize = header.size
                        ) { transferred ->
                            send(TransferProgress(transferred, header.size, fileIndex = index, totalFiles = totalFiles))
                        }
                        dataOut.flush()
                    } ?: throw IllegalStateException("Cannot open InputStream for: $fileUri")

                    // Emit per-file completion (not overall completion until last file)
                    if (index == totalFiles - 1) {
                        send(TransferProgress(header.size, header.size, isComplete = true, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    } else {
                        send(TransferProgress(header.size, header.size, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    }
                }
                Log.d(TAG, "All $totalFiles files sent")
            } catch (e: Exception) {
                Log.e(TAG, "sendFiles error", e)
                send(TransferProgress(error = e.message ?: "Multi-file send failed"))
            } finally {
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
                activeServerSocket = null
                Log.d(TAG, "sendFiles cleanup done")
            }
        }
    }

    /**
     * **Receiver side** — receives multiple files over a single TCP connection.
     * Expects wire format: `[4 bytes fileCount] + [per-file: header + bytes]...`
     */
    fun receiveFiles(
        context: Context,
        groupOwnerAddress: String,
        port: Int,
        customLocationUri: Uri? = null
    ): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "receiveFiles: host=$groupOwnerAddress port=$port customLocation=$customLocationUri")
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = connectWithRetry(groupOwnerAddress, port)
                Log.d(TAG, "Connected to $groupOwnerAddress:$port")

                val dataIn = DataInputStream(socket.getInputStream().buffered(CHUNK_SIZE))
                val totalFiles = dataIn.readInt()
                Log.d(TAG, "Expecting $totalFiles files")
                send(TransferProgress(totalFiles = totalFiles))

                for (index in 0 until totalFiles) {
                    val header = readHeader(dataIn)
                    Log.d(TAG, "Receiving file ${index + 1}/$totalFiles — name=${header.name} size=${header.size}")

                    val outputUri = insertMediaStoreEntry(context, header, customLocationUri)
                        ?: throw IllegalStateException("Failed to create entry for: ${header.name}")

                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        streamBytes(
                            source = dataIn,
                            destination = outputStream,
                            totalSize = header.size
                        ) { transferred ->
                            send(TransferProgress(transferred, header.size, fileIndex = index, totalFiles = totalFiles))
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaStoreUri(outputUri)) {
                            context.contentResolver.update(
                                outputUri,
                                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                                null, null
                            )
                        }
                    } ?: throw IllegalStateException("Cannot open OutputStream for: $outputUri")

                    if (index == totalFiles - 1) {
                        send(TransferProgress(header.size, header.size, isComplete = true, savedUri = outputUri, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    } else {
                        send(TransferProgress(header.size, header.size, savedUri = outputUri, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    }
                }
                Log.d(TAG, "All $totalFiles files received")
            } catch (e: Exception) {
                Log.e(TAG, "receiveFiles error", e)
                send(TransferProgress(error = e.message ?: "Multi-file receive failed"))
            } finally {
                runCatching { socket?.close() }
                Log.d(TAG, "receiveFiles cleanup done")
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
     * Progress is throttled to emit only when percentage changes (max 101 callbacks).
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
        var lastPct = -1
        while (bytesTransferred < totalSize) {
            val toRead = minOf(CHUNK_SIZE.toLong(), totalSize - bytesTransferred).toInt()
            val read = source.read(buffer, 0, toRead)
            if (read == -1) break
            destination.write(buffer, 0, read)
            bytesTransferred += read
            val pct = if (totalSize > 0) ((bytesTransferred * 100) / totalSize).toInt() else 0
            if (pct != lastPct) {
                lastPct = pct
                onProgress(bytesTransferred)
            }
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

    // ─── Friend exchange variants ──────────────────────────────────────────

    /**
     * **Sender side** — sends files, then waits for optional friend request from receiver.
     * If a friend request arrives, emits [TransferProgress.friendRequest] and waits
     * for a decision via [friendDecision]. Responds with ACK + profile or NAK.
     */
    fun sendFilesWithFriendExchange(
        context: Context,
        fileUris: List<Uri>,
        port: Int,
        senderProfile: ProfileData?,
        friendDecision: kotlinx.coroutines.CompletableDeferred<Boolean>?
    ): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "sendFilesWithFriendExchange: ${fileUris.size} files, port=$port")
        val totalFiles = fileUris.size
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = createServerSocket(port)
                activeServerSocket = serverSocket
                send(TransferProgress(totalFiles = totalFiles))

                clientSocket = serverSocket.accept()
                clientSocket.configureForTransfer()
                Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")

                val dataOut = DataOutputStream(clientSocket.getOutputStream().buffered(CHUNK_SIZE))

                if (totalFiles > 1) {
                    dataOut.writeInt(totalFiles)
                    dataOut.flush()
                }

                for ((index, fileUri) in fileUris.withIndex()) {
                    val header = resolveFileHeader(context, fileUri)
                        ?: throw IllegalArgumentException("Cannot resolve file metadata for: $fileUri")

                    writeHeader(dataOut, header)
                    context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                        streamBytes(
                            source = inputStream,
                            destination = dataOut,
                            totalSize = header.size
                        ) { transferred ->
                            send(TransferProgress(transferred, header.size, fileIndex = index, totalFiles = totalFiles))
                        }
                        dataOut.flush()
                    } ?: throw IllegalStateException("Cannot open InputStream for: $fileUri")

                    if (index == totalFiles - 1) {
                        send(TransferProgress(header.size, header.size, isComplete = true, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    } else {
                        send(TransferProgress(header.size, header.size, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    }
                }
                Log.d(TAG, "All files sent, waiting for friend request...")

                // Post-transfer: wait for optional friend request from receiver
                try {
                    clientSocket.soTimeout = 15_000
                    val dataIn = DataInputStream(clientSocket.getInputStream().buffered(CHUNK_SIZE))
                    val friendReq = readFriendRequest(dataIn)
                    if (friendReq != null) {
                        Log.d(TAG, "Friend request received from: ${friendReq.displayName}")
                        send(TransferProgress(isComplete = true, friendRequest = friendReq))

                        // Wait for user decision
                        val accepted = try {
                            kotlinx.coroutines.withTimeout(30_000) {
                                friendDecision?.await() ?: false
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Friend decision timeout or error", e)
                            false
                        }

                        sendFriendResponse(dataOut, accepted, if (accepted) senderProfile else null)
                        if (accepted && senderProfile != null) {
                            send(TransferProgress(isComplete = true, friendProfile = friendReq))
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.d(TAG, "No friend request received (timeout)")
                } catch (e: java.io.EOFException) {
                    Log.d(TAG, "Receiver closed connection without friend request")
                }

            } catch (e: Exception) {
                Log.e(TAG, "sendFilesWithFriendExchange error", e)
                send(TransferProgress(error = e.message ?: "Send failed"))
            } finally {
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
                activeServerSocket = null
            }
        }
    }

    /**
     * **Receiver side** — receives files, then optionally initiates friend exchange.
     */
    fun receiveFilesWithFriendExchange(
        context: Context,
        groupOwnerAddress: String,
        port: Int,
        fileCount: Int,
        becomeFriends: Boolean,
        localProfile: ProfileData?,
        customLocationUri: Uri? = null
    ): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "receiveFilesWithFriendExchange: host=$groupOwnerAddress fileCount=$fileCount becomeFriends=$becomeFriends customLocation=$customLocationUri")
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = connectWithRetry(groupOwnerAddress, port)

                val dataIn = DataInputStream(socket.getInputStream().buffered(CHUNK_SIZE))
                val totalFiles = if (fileCount > 1) dataIn.readInt() else 1
                send(TransferProgress(totalFiles = totalFiles))

                for (index in 0 until totalFiles) {
                    val header = readHeader(dataIn)

                    val outputUri = insertMediaStoreEntry(context, header, customLocationUri)
                        ?: throw IllegalStateException("Failed to create entry for: ${header.name}")

                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        streamBytes(
                            source = dataIn,
                            destination = outputStream,
                            totalSize = header.size
                        ) { transferred ->
                            send(TransferProgress(transferred, header.size, fileIndex = index, totalFiles = totalFiles))
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaStoreUri(outputUri)) {
                            context.contentResolver.update(
                                outputUri,
                                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                                null, null
                            )
                        }
                    } ?: throw IllegalStateException("Cannot open OutputStream for: $outputUri")

                    if (index == totalFiles - 1) {
                        send(TransferProgress(header.size, header.size, isComplete = true, savedUri = outputUri, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    } else {
                        send(TransferProgress(header.size, header.size, savedUri = outputUri, fileName = header.name, mimeType = header.mimeType, fileIndex = index, totalFiles = totalFiles))
                    }
                }

                // Post-transfer: initiate friend exchange if requested
                if (becomeFriends && localProfile != null) {
                    Log.d(TAG, "Initiating friend exchange...")
                    val dataOut = DataOutputStream(socket.getOutputStream().buffered(CHUNK_SIZE))
                    sendFriendRequest(dataOut, localProfile)

                    socket.soTimeout = 15_000
                    val (accepted, senderProfile) = readFriendResponse(dataIn)
                    if (accepted && senderProfile != null) {
                        Log.d(TAG, "Friend exchange accepted by sender: ${senderProfile.displayName}")
                        send(TransferProgress(isComplete = true, friendProfile = senderProfile))
                    } else {
                        Log.d(TAG, "Friend exchange declined by sender")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "receiveFilesWithFriendExchange error", e)
                send(TransferProgress(error = e.message ?: "Receive failed"))
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    // ─── Profile transfer (NFC profile sharing) ─────────────────────────────

    /**
     * Sender side for profile sharing — opens a ServerSocket, sends profile data.
     */
    fun sendProfile(profileData: ProfileData, port: Int): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "sendProfile: port=$port")
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            try {
                serverSocket = createServerSocket(port)
                activeServerSocket = serverSocket
                send(TransferProgress())

                clientSocket = serverSocket.accept()
                clientSocket.configureForTransfer()

                val dataOut = DataOutputStream(clientSocket.getOutputStream().buffered(CHUNK_SIZE))
                sendFriendRequest(dataOut, profileData)
                // Ensure all buffered data reaches the receiver's TCP stack before closing.
                // Without this, close() may discard in-flight data on some devices.
                clientSocket.getOutputStream().flush()
                send(TransferProgress(isComplete = true, friendProfile = profileData))

            } catch (e: Exception) {
                Log.e(TAG, "sendProfile error", e)
                send(TransferProgress(error = e.message ?: "Profile send failed"))
            } finally {
                // Graceful shutdown: signal EOF to receiver before closing
                runCatching { clientSocket?.shutdownOutput() }
                runCatching { clientSocket?.close() }
                runCatching { serverSocket?.close() }
                activeServerSocket = null
            }
        }
    }

    /**
     * Receiver side for profile sharing — connects and reads profile data.
     */
    fun receiveProfile(groupOwnerAddress: String, port: Int): Flow<TransferProgress> = channelFlow {
        Log.d(TAG, "receiveProfile: host=$groupOwnerAddress port=$port")
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = connectWithRetry(groupOwnerAddress, port)

                val dataIn = DataInputStream(socket.getInputStream().buffered(CHUNK_SIZE))
                val profile = readFriendRequest(dataIn)
                if (profile != null) {
                    send(TransferProgress(isComplete = true, friendProfile = profile))
                } else {
                    send(TransferProgress(error = "Failed to read profile data"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "receiveProfile error", e)
                send(TransferProgress(error = e.message ?: "Profile receive failed"))
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    /**
     * Creates an output entry for the file to be received.
     *
     * When [customLocationUri] is non-null, creates the file in the SAF-selected folder
     * using [DocumentsContract]. Otherwise falls back to [MediaStore.Downloads].
     *
     * - **API 29+**: uses [MediaStore.Downloads] with `IS_PENDING = 1` for atomic writes.
     * - **API 26–28**: uses [Environment.DIRECTORY_DOWNLOADS] via legacy file path.
     *
     * @return URI of the new entry, or `null` on failure.
     */
    private fun insertMediaStoreEntry(
        context: Context,
        header: FileTransferHeader,
        customLocationUri: Uri? = null
    ): Uri? {
        // Custom SAF-based location
        if (customLocationUri != null) {
            return try {
                val treeDocId = DocumentsContract.getTreeDocumentId(customLocationUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                    customLocationUri, treeDocId
                )
                DocumentsContract.createDocument(
                    context.contentResolver, parentDocUri, header.mimeType, header.name
                )
            } catch (e: Exception) {
                Log.w(TAG, "Custom location failed for ${header.name}, falling back to Downloads", e)
                // Fall through to default MediaStore
                insertMediaStoreEntryDefault(context, header)
            }
        }

        return insertMediaStoreEntryDefault(context, header)
    }

    /** Default MediaStore-based file creation in Downloads. */
    private fun insertMediaStoreEntryDefault(context: Context, header: FileTransferHeader): Uri? {
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

    /** Returns true if the URI is a MediaStore content:// URI (needs IS_PENDING cleanup). */
    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.authority == "media" || uri.toString().startsWith("content://media/")
}
