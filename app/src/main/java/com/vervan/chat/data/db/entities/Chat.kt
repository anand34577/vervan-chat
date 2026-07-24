package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chats",
    // Every one of these backs a WHERE clause ChatDao actually runs (workspace/folder/project
    // scoped lists, plus the messages.chatId EXISTS subquery in the main chat-list query relies
    // on messages' own index, not this one) — see Migration(36, 37).
    indices = [Index("workspaceId"), Index("folderId"), Index("projectId")]
)
data class Chat(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    // Workspace System: every chat belongs to exactly one workspace. Defaults to
    // the Default Workspace so pre-existing rows (migration 22->23) and any call site that
    // forgets to stamp this stay valid rather than orphaned.
    val workspaceId: String = Workspace.DEFAULT_WORKSPACE_ID,
    val personaId: String? = null,
    val modelId: String? = null,
    val projectId: String? = null,
    val folderId: String? = null,
    val draft: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val sourceGrounded: Boolean = false,
    val toolsEnabled: Boolean = false,
    // Model profile id (ModelProfileType.id) — shapes context budget, retrieval depth, output
    // length and default thinking mode. Defaults to BALANCED. See llm.ModelProfiles.
    val profile: String = "BALANCED",
    // OFF/FAST/BALANCED/DEEP — prompt-engineered, not a native reasoning-mode API (tasks-genai
    // doesn't expose one). See ChatViewModel.reasoningInstruction / llm/ThinkingParser.
    val thinkingMode: String = "OFF",
    // Tip of the currently active branch. Null means empty chat (no messages yet).
    val activeLeafId: String? = null,
    // comma-separated KB ids instead of a join table — a chat only ever
    // needs "which KBs am I asking against", not a queryable many-to-many. Add a
    // join table if cross-KB reporting ever needs it.
    val knowledgeBaseIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Soft delete — non-null means "in the recycle bin", see RecycleBinScreen. A background
    // sweep (SettingsViewModel.purgeExpiredRecycleBin) hard-deletes rows older than 30 days.
    val deletedAt: Long? = null,
    // Per-chat sampler overrides — null means "inherit" (model override, then the
    // app-global SettingsRepository value). No maxOutputTokens field: LiteRT-LM's engine only
    // takes a max-token budget at model load() time (engine-wide), not per generate() call, so
    // a per-chat override here would be a field that silently does nothing — left out rather
    // than faked.
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    // Scroll-position restore — the message the user was reading when they last
    // left this chat, plus its pixel offset within the viewport, so reopening restores the
    // exact reading position instead of always jumping to the latest message.
    val scrollAnchorMessageId: String? = null,
    val scrollAnchorOffsetPx: Int = 0,
    // Title generation — false means the title is still "temporary/automatic"
    // and eligible for auto-generation; a manual rename sets this true, permanently opting the
    // chat out of auto-title overwrites. previousTitle backs the "Restore previous title" menu
    // action after an AI (re)generation replaces it.
    val titleIsCustom: Boolean = false,
    val previousTitle: String? = null,
    // Privacy hardening — forces FLAG_SECURE on the window while this chat is open, blocking
    // screenshots/screen recording and showing a blank thumbnail in the recent-apps switcher.
    val screenshotBlocked: Boolean = false,
    // Incognito mode: no memory-suggestion writes, excluded from backup/search/smart
    // collections, hard-deleted (not soft-deleted to the recycle bin) on close or app-cold-start
    // sweep. See VervanApp.onCreate and ChatScreen's onDispose.
    val isTemporary: Boolean = false,
    // Per-chat tool overrides on top of the global Settings → Tools enable/disable list —
    // "toolId=1" forces it on for this chat even if globally disabled, "toolId=0" forces it off
    // even if globally enabled. Absent from the map means "inherit the global setting". Same
    // hand-rolled delimited-string shape as knowledgeBaseIds above, for the same reason: this
    // only ever needs "what's this chat's override for tool X", never a queryable join.
    val toolOverrides: String = "",
    // Long-chat context management — a running summary of turns older than
    // [summaryCoversUpToMessageId], generated in the background once history approaches the
    // model's context budget (see ChatViewModel.maybeSummarizeOlderHistory). Substituted for
    // the raw dropped turns in buildPromptSections instead of just omitting them outright, so a
    // long conversation on a small-context model degrades to "knows the gist of what came
    // before" rather than "forgot everything before the last few turns". Null means no summary
    // has been generated yet (short chat, or the feature is off) — the old drop-oldest-silently
    // behavior applies unchanged.
    val contextSummary: String? = null,
    val summaryCoversUpToMessageId: String? = null
) {
    fun kbIdList(): List<String> = knowledgeBaseIds.split(',').filter { it.isNotBlank() }

    fun toolOverrideMap(): Map<String, Boolean> = toolOverrides.split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val (id, flag) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            id to (flag == "1")
        }.toMap()

    companion object {
        fun encodeToolOverrides(map: Map<String, Boolean>): String =
            map.entries.joinToString(",") { (id, on) -> "$id=${if (on) "1" else "0"}" }
    }
}

