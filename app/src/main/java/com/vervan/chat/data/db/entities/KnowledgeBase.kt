package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "knowledge_bases")
data class KnowledgeBase(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // display customization and default-context wiring. Chunking profile
    // deliberately not added yet (lowest-value field of this group per the plan).
    val icon: String = "MenuBook",
    val color: String? = null,
    val defaultPersonaId: String? = null,
    val defaultProjectId: String? = null,
    val autoIndex: Boolean = true
)
