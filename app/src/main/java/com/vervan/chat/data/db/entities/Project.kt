package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val instructions: String = "",
    val personaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Recycle bin coverage (Phase 6, spec §34).
    val deletedAt: Long? = null
)
