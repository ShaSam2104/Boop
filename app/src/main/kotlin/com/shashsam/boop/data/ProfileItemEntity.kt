package com.shashsam.boop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_items")
data class ProfileItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,      // "link", "email", "phone"
    val label: String,     // "GitHub", "Work Email", etc.
    val value: String,     // URL / email / phone number
    val size: String,      // "half" (1x1) or "full" (2x1)
    val sortOrder: Int
)
