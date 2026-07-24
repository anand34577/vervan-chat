package com.vervan.chat.model

import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Workspace

/**
 * The single source of truth for how a chat's effective persona and model resolve from the
 * workspace hierarchy. Precedence is **live** — read from the current rows on every use, not
 * stamped into the chat at creation — so changing a default at any level immediately propagates to
 * every chat below it that hasn't set its own override:
 *
 *   persona: chat.personaId → folder.defaultPersonaId → workspace.personaId → "builtin-general"
 *   model:   chat.modelId   → folder.defaultModelId    → (caller's loaded/active fallback)
 *
 * Before this, the persona chain was implemented twice (a display Flow and a suspend prompt path)
 * and skipped folders entirely, while folder persona/model were copied into the chat at creation —
 * so a later folder-default change silently didn't apply. Both paths now route through here.
 *
 * Project sits on a separate axis (a chat can be in both a project and a folder), so project
 * persona is deliberately NOT in this chain — a project contributes its *instructions* to the
 * prompt and scopes memory, but never overrides the workspace-hierarchy persona. That keeps
 * "which persona wins" unambiguous when a chat is in both.
 */
object ChatDefaults {
    const val DEFAULT_PERSONA_ID = "builtin-general"

    fun personaId(chat: Chat?, folder: Folder?, workspace: Workspace?): String =
        chat?.personaId
            ?: folder?.defaultPersonaId?.takeIf { it.isNotBlank() }
            ?: workspace?.personaId?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PERSONA_ID

    /** The explicitly-chosen model id (chat override, else folder default), or null meaning "fall
     *  back to whatever the app has loaded/active" — the caller owns that final rung because it
     *  needs live model-load state, not a stored id. */
    fun modelId(chat: Chat?, folder: Folder?): String? =
        chat?.modelId?.takeIf { it.isNotBlank() }
            ?: folder?.defaultModelId?.takeIf { it.isNotBlank() }
}
