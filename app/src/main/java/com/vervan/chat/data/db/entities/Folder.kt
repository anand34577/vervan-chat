package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Manual folder (spec §28.2) — a lightweight grouping of chats/notes with optional defaults
 * (persona, model, knowledge bases). Spec says one optional nesting level; we keep it flat
 * here (no parentFolderId) because deep trees are what Projects are for, and flat folders
 * are simpler to reason about. Soft-deletable.
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    // Workspace System spec §6/§11: a folder belongs to exactly one workspace, same one as
    // the chats inside it.
    val workspaceId: String = Workspace.DEFAULT_WORKSPACE_ID,
    val defaultPersonaId: String? = null,
    val defaultModelId: String? = null,
    val defaultKnowledgeBaseIds: String = "",
    val color: String = "#E8A33D",
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    fun kbIdList(): List<String> = defaultKnowledgeBaseIds.split(',').filter { it.isNotBlank() }
}
