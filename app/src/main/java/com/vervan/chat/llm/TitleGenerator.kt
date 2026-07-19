package com.vervan.chat.llm

import com.vervan.chat.VervanApp
import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.flow.first

/**
 * One-shot AI title generation (Chat Screen spec §18-21) — same pattern as
 * StudyWorkspaceViewModel.generateSet: a non-conversational prompt through `engine.generate()`,
 * accumulate the whole response, post-process into a single clean line. Not part of the normal
 * multi-turn buildPrompt()/history path, so it never touches persona/memory/retrieval state.
 */
object TitleGenerator {

    /** Null return means "skip" (spec §21's fallbacks): no model available, or not enough
     * conversation content to summarize yet — callers must leave the existing title alone. */
    suspend fun generate(app: VervanApp, chatId: String): String? {
        val db = app.container.db
        val allMessages = db.messageDao().getMessages(chatId)
        val chat = db.chatDao().getChat(chatId) ?: return null
        val history = BranchUtil.pathTo(allMessages, chat.activeLeafId)
            .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
        // §21 "Empty or minimal chat" — skip rather than title off a single message.
        if (history.count { it.role == MessageRole.ASSISTANT } < 1 || history.size < 2) return null

        // Honor a per-chat model pin (Chat.modelId) before the app-wide active model, so the
        // model this resolves — and passes to OneShotLlm below — is the one the chat actually
        // just generated with and that is still resident.
        val model = chat.modelId?.let { id -> db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION } }
            ?: db.modelDao().getActiveModel(ModelRole.GENERATION) ?: return null
        // §21 "Context too large" — recent meaningful exchanges only, not the full transcript.
        val transcript = history.takeLast(10).joinToString("\n") { m ->
            "${if (m.role == MessageRole.USER) "User" else "Assistant"}: ${m.content.take(500)}"
        }
        val prompt = "Generate a short, specific title for this conversation. " +
            "3 to 8 words, no quotation marks, no trailing punctuation, no generic title like " +
            "\"New Chat\" or \"Conversation\". Respond with ONLY the title text, nothing else.\n\n" +
            "Conversation:\n$transcript"

        // Pass the chat's resolved model (honoring a per-chat Chat.modelId override), not the
        // app-wide active one — titling runs right after that model generated the reply, so this
        // reuses the resident model instead of swapping to the global default.
        val raw = OneShotLlm.run(app, prompt, model = model) ?: return null
        val title = raw.trim().trim('"', '“', '”').lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(80)
        return title.ifBlank { null }
    }
}
