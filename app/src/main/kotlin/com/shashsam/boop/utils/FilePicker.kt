package com.shashsam.boop.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private const val TAG = "FilePicker"

/**
 * Metadata about a file selected by the user, resolved via [android.content.ContentResolver].
 *
 * @param uri       Content URI of the selected file.
 * @param name      Display name of the file.
 * @param size      Size in bytes.
 * @param mimeType  MIME type (e.g. `"image/jpeg"`, `"video/mp4"`, `"application/pdf"`).
 */
data class FileMetadata(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
) {
    /**
     * Human-readable file size (e.g. `"12.3 MB"`).
     */
    val formattedSize: String
        get() = when {
            size >= 1_073_741_824L -> "%.1f GB".format(size / 1_073_741_824.0)
            size >= 1_048_576L     -> "%.1f MB".format(size / 1_048_576.0)
            size >= 1_024L         -> "%.1f KB".format(size / 1_024.0)
            else                   -> "$size B"
        }
}

/**
 * Compose helper that creates an [ActivityResultContracts.OpenDocument] launcher.
 *
 * The [onResult] callback is invoked with resolved [FileMetadata] if the user picks
 * a file, or with `null` if the picker is cancelled or metadata resolution fails.
 *
 * ### Usage
 * ```kotlin
 * val picker = rememberFilePicker { metadata ->
 *     if (metadata != null) viewModel.startSending(metadata.uri)
 * }
 * // …
 * Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Pick file") }
 * ```
 */
@Composable
fun rememberFilePicker(
    onResult: (FileMetadata?) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "File picked: $uri")
            onResult(resolveFileMetadata(context, uri))
        } else {
            Log.d(TAG, "File picker cancelled")
            onResult(null)
        }
    }
}

/**
 * Resolves [FileMetadata] for [uri] using the [android.content.ContentResolver].
 *
 * @return [FileMetadata] on success, `null` if the query fails or returns no rows.
 */
fun resolveFileMetadata(context: Context, uri: Uri): FileMetadata? {
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
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            FileMetadata(uri = uri, name = name, size = size, mimeType = mimeType)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to resolve file metadata for $uri", e)
        null
    }
}
