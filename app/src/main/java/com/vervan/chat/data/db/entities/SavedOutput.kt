package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "saved_outputs")
data class SavedOutput(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val sourceChatId: String? = null,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Recycle bin coverage.
    val deletedAt: Long? = null
)
