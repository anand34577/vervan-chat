package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "projects", indices = [Index("workspaceId")])
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    // A project belongs to exactly one workspace, same as chats and folders — previously projects
    // floated outside the workspace hierarchy (a project could span workspaces), the one place the
    // containment model was inconsistent. Defaults to the Default Workspace so pre-existing rows
    // (migration 41->42) stay valid rather than orphaned.
    val workspaceId: String = Workspace.DEFAULT_WORKSPACE_ID,
    val instructions: String = "",
    val personaId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Recycle bin coverage (Phase 6, spec §34).
    val deletedAt: Long? = null
)
