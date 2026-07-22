package com.vervan.chat.model

import androidx.room.withTransaction
import com.vervan.chat.data.db.AppDatabase
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Orchestrates the multi-DAO operations the Workspace System spec describes as single actions
 * (switch active, archive, restore, permanently delete) — same role DocumentImportManager plays
 * for documents, not a general repository layer (this codebase's ViewModels otherwise call DAOs
 * directly, see SettingsRepository/AppContainer).
 */
class WorkspaceManager(
    private val db: AppDatabase,
    private val documentImportManager: DocumentImportManager,
    private val settingsRepository: SettingsRepository
) {
    /** §5: selecting a workspace immediately makes it active; §2/§19 an archived workspace can
     * never be made active — callers must restore it first. */
    suspend fun setActive(workspace: Workspace) {
        check(!workspace.archived) { "Archived workspace cannot be active" }
        db.workspaceDao().update(workspace.copy(lastActiveAt = System.currentTimeMillis()))
        settingsRepository.setActiveWorkspaceId(workspace.id)
    }

    /** Phase E — fills a brand-new chat's model profile / knowledge bases from its workspace's
     * configured defaults. Callers must apply this immediately after constructing the [Chat]
     * (all current call sites do) — it unconditionally prefers the workspace's default over
     * whatever the [Chat] constructor's own defaults left in place, so it isn't safe to call
     * against a chat that already has real, user-set state. No-op if the workspace has neither
     * default configured, or doesn't exist. */
    suspend fun applyDefaults(chat: Chat): Chat {
        val workspace = db.workspaceDao().get(chat.workspaceId) ?: return chat
        return chat.copy(
            profile = workspace.defaultProfile?.takeIf { it.isNotBlank() } ?: chat.profile,
            knowledgeBaseIds = chat.knowledgeBaseIds.ifBlank { workspace.defaultKnowledgeBaseIds }
        )
    }

    suspend fun create(name: String, description: String, personaId: String): Workspace {
        val workspace = Workspace(name = name.trim(), description = description.trim(), personaId = personaId)
        db.workspaceDao().upsert(workspace)
        return workspace
    }

    /** §12: hides the workspace and everything in it, and falls back to Default if it was
     * active — an archived workspace can never remain the active one. */
    suspend fun archive(workspace: Workspace) {
        check(!workspace.isDefault) { "Default Workspace cannot be archived" }
        db.workspaceDao().update(workspace.copy(archived = true))
        if (settingsRepository.activeWorkspaceId.first() == workspace.id) {
            db.workspaceDao().getDefault()?.let { setActive(it) }
        }
    }

    suspend fun restore(workspace: Workspace) {
        db.workspaceDao().update(workspace.copy(archived = false))
    }

    /**
     * Permanently deletes a custom workspace and everything scoped to it (§13): its folders,
     * chats (and their messages/tool-audit records), and documents. Falls back to the Default
     * Workspace if the deleted one was active (§5/§19 — deleting the active workspace requires
     * switching first, enforced here rather than trusting the caller already did it).
     */
    suspend fun delete(workspace: Workspace) {
        check(!workspace.isDefault) { "Default Workspace cannot be deleted" }
        // Document cleanup runs outside the DB transaction below since it also touches the
        // filesystem (copied file + embedded chunks) via DocumentImportManager.
        db.documentDao().getForWorkspace(workspace.id).forEach { documentImportManager.delete(it) }
        db.withTransaction {
            db.chatDao().getForWorkspace(workspace.id).forEach { chat ->
                db.messageDao().deleteForChat(chat.id)
                db.toolAuditDao().deleteForChat(chat.id)
            }
            db.chatDao().deleteForWorkspace(workspace.id)
            db.folderDao().deleteForWorkspace(workspace.id)
            // Projects now belong to a workspace too — soft-delete them (recoverable from the
            // recycle bin) rather than leaving them orphaned pointing at a deleted workspace.
            db.projectDao().softDeleteForWorkspace(workspace.id, System.currentTimeMillis())
            db.workspaceDao().delete(workspace)
        }
        if (settingsRepository.activeWorkspaceId.first() == workspace.id) {
            db.workspaceDao().getDefault()?.let { setActive(it) }
        }
    }
}
