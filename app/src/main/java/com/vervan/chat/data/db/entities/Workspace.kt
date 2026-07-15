package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Workspace (Workspace System spec §1-3) — top container for a persona, chats, folders, and
 * documents. Exactly one workspace is active at a time; "active" is tracked in
 * SettingsRepository (a global selection), not as a field here.
 *
 * [isDefault] marks the single permanent Default Workspace seeded at cold start (§2, id
 * [DEFAULT_WORKSPACE_ID]) — its name/persona/archive/delete state is locked in the UI layer.
 */
@Entity(tableName = "workspaces")
data class Workspace(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val personaId: String,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    // Auto title generation scope (spec §20) — kept at workspace level per request, not a
    // global app setting and not per-chat: every chat in this workspace either gets
    // auto-generated titles or none do.
    val autoTitleGeneration: Boolean = false,
    // Privacy hardening — WorkspaceManager.setActive() requires AppLockManager to be unlocked
    // before switching into a workspace with this set (e.g. a "Personal" workspace kept
    // separate from "Work"). Independent of the app-wide lock in SettingsRepository.
    val lockEnabled: Boolean = false,
    // Per-workspace defaults for new chats created inside it (falls back to the app-global
    // SettingsRepository default when null/blank, same fallback shape as Chat's own fields).
    val defaultProfile: String? = null,
    val defaultKnowledgeBaseIds: String = ""
) {
    fun defaultKbIdList(): List<String> = defaultKnowledgeBaseIds.split(',').filter { it.isNotBlank() }

    companion object {
        const val DEFAULT_WORKSPACE_ID = "default"
    }
}
