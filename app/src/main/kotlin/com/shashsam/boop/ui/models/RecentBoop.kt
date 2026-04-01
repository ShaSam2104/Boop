package com.shashsam.boop.ui.models

import android.net.Uri

/**
 * Represents a completed file transfer for the "Recent Boops" list on the Home screen.
 * Stored in-memory only (lost on app restart).
 *
 * @param fileUri URI of the transferred file. Non-null on the Receiver side (saved to
 *               MediaStore), null on the Sender side (original file URI is not persisted).
 */
data class RecentBoop(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val timestamp: Long,
    val wasSender: Boolean,
    val fileUri: Uri? = null,
    val peerUlid: String? = null
)
