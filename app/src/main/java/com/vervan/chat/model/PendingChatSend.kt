package com.vervan.chat.model

/**
 * One-shot, in-memory handoff for "create a chat and immediately send this text" (Home's
 * "Ask anything" quick-ask). Deliberately not persisted to [Chat.draft] or any DB column: draft
 * means "text still sitting unsent in the composer, resume editing it" everywhere else in the
 * app (regular chat composer, fork, etc.) — reusing it here would make every chat opened from
 * Home look like an abandoned draft instead of an answered question, and would silently re-send
 * the text if the process died between creation and the ChatViewModel actually consuming it.
 *
 * Process-lifetime only, which is the correct scope: [HomeViewModel] stashes the text right
 * before navigating, and the chat screen's [com.vervan.chat.ui.chat.ChatViewModel] consumes it
 * (removing the entry) in its `init` block a moment later, all within the same process run. If
 * the process is killed in that narrow window, the worst case is the new chat opens empty
 * instead of pre-sent — never a duplicate or a phantom send.
 */
object PendingChatSend {
    private val pending = mutableMapOf<String, String>()

    fun stash(chatId: String, text: String) {
        pending[chatId] = text
    }

    /** Removes and returns the pending text for [chatId], or null if there is none — a plain
     * new-empty-chat open, or a chat this mailbox never held anything for. */
    fun consume(chatId: String): String? = pending.remove(chatId)
}
