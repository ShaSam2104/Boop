package com.shashsam.boop.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "friends",
    indices = [Index(value = ["ssid"], unique = true)]
)
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val displayName: String,
    val firstSeenTimestamp: Long,
    val lastSeenTimestamp: Long,
    val transferCount: Int = 1,
    val profileJson: String? = null,
    val profilePicPath: String? = null
)
