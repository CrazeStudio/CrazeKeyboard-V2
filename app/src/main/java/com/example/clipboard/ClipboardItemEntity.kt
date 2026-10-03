package com.example.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_items")
data class ClipboardItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
