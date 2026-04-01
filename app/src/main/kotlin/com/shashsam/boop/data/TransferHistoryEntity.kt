package com.shashsam.boop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val timestamp: Long,
    val wasSender: Boolean,
    val fileUriString: String?,
    val peerUlid: String? = null
)
