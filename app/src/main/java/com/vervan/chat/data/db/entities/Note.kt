package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled note",
    val content: String = "",
    val projectId: String? = null,
    val folderId: String? = null,
    val pinned: Boolean = false,
    // Phase 4, spec §21 — comma-separated tags; simple string beats a join table at this scale.
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
