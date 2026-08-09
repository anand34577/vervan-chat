package com.vervan.chat.llm

import com.vervan.chat.VervanApp
import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.flow.first

/**
 * One-shot AI title generation (Chat Screen-21) — same pattern as
 * StudyWorkspaceViewModel.generateSet: a non-conversational prompt through `engine.generate()`,
 * accumulate the whole response, post-process into a single clean line. Not part of the normal
 * multi-turn buildPrompt()/history path, so it never touches persona/memory/retrieval state.
 */
object TitleGenerator {

    /** [title] is what to store either way. [isFallback] tells the two callers apart:
     * [ChatViewModel.generateTitle] (the manual "Regenerate" button) needs it to avoid claiming
     * "the AI regenerated and still landed on this title" when the AI actually produced nothing
     * usable — see the isFallback doc below. [ChatViewModel.maybeAutoGenerateTitle] (silent,
     * fire-and-forget) doesn't care and just uses [title] either way. */
    data class TitleResult(val title: String, val isFallback: Boolean)

    /** Null return means "skip" (fallbacks): no model available, or not enough
     * conversation content to summarize yet — callers must leave the existing title alone.
     * [avoidTitle], when supplied (manual "Regenerate title" only — see ChatViewModel.generateTitle),
     * is the chat's current title: told explicitly not to just repeat it, since a thin
     * conversation (e.g. one "Hi" exchange) gives the model so little to work with that repeated
     * sampling kept landing back on the same literal echo — regenerating looked like a no-op even
     * though a fresh call genuinely ran. */
    suspend fun generate(app: VervanApp, chatId: String, avoidTitle: String? = null): TitleResult? {
        val db = app.container.db
        val allMessages = db.messageDao().getMessages(chatId)
        val chat = db.chatDao().getChat(chatId) ?: return null
        val history = BranchUtil.pathTo(allMessages, chat.activeLeafId)
            .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
        // "Empty or minimal chat" — skip rather than title off a single message.
        if (history.count { it.role == MessageRole.ASSISTANT } < 1 || history.size < 2) return null

        // Same chain as ChatViewModel.resolveGenerationModelForChat: pin > currently loaded >
        // app-wide default. The middle rung is the important one. Without it, a chat with no
        // explicit pin resolved straight to the *default* model, and OneShotLlm's ensureLoaded()
        // then loaded it — evicting whatever the user was actually talking to. The visible symptom
        // was the model silently changing between the first and second reply of a chat (auto-titling
        // fires after the first reply), and every later chat starting on the default again.
        val model = chat.modelId?.let { id -> db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION } }
            ?: app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId
                ?.let { id -> db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION } }
            ?: db.modelDao().getActiveModel(ModelRole.GENERATION) ?: return null
        // "Context too large" — recent meaningful exchanges only, not the full transcript.
        val transcript = history.takeLast(10).joinToString("\n") { m ->
            "${if (m.role == MessageRole.USER) "User" else "Assistant"}: ${m.content.take(500)}"
        }
        // Same "don't produce internal reasoning" instruction the main chat path sends for
        // thinking-OFF on a native reasoner (ThinkingPolicy.reasoningInstruction) — a title request
        // has no UI to show a reasoning card in, so a model that reasons by default (Qwen3.5,
        // Gemma's own template, DeepSeek-style models) needs to be told not to here too, not just
        // during real replies.
        val suppressReasoning = ThinkingPolicy.reasoningInstruction("OFF", model.engine, isReasoningModel = true)
        val avoidClause = avoidTitle?.takeIf { it.isNotBlank() }?.let {
            " The current title is \"$it\" — give a different, more specific one; don't just repeat it " +
                "or a trivial variant of it."
        }.orEmpty()
        val prompt = "Generate a short, specific title for this conversation — describe the actual " +
            "topic or request in your own words, don't just copy the user's wording verbatim (a lone " +
            "\"Hi\" is not a title). 3 to 6 words, Title Case, no quotation marks, no trailing " +
            "punctuation, no generic title like \"New Chat\", \"Conversation\", or \"Greeting\"." +
            avoidClause +
            " Respond with ONLY the title text, nothing else. " +
            "$suppressReasoning\n\n" +
            "Conversation:\n$transcript"

        // Pass the chat's resolved model (honoring a per-chat Chat.modelId override), not the
        // app-wide active one — titling runs right after that model generated the reply, so this
        // reuses the resident model instead of swapping to the global default.
        // A title is ~3-8 words, but a reasoning model that ignores the instruction above still
        // needs enough budget to get through its own preamble and reach the actual title — 24
        // tokens wasn't enough, then 60 still wasn't for a model in a heavier thinking profile
        // (see ThinkingPolicy.reasoningInstruction's own doc: the instruction is a request the
        // model can ignore, not a guarantee). No fixed budget makes this impossible — a "Deep"
        // profile can reason for thousands of tokens — so [isFallback] below is the actual guard,
        // not this number; it's just sized generously enough that hitting it is the exception.
        val raw = OneShotLlm.run(app, prompt, model = model, maxOutputTokensOverride = 200) ?: return null
        // Strip reasoning the same way a real reply does — the model may reason regardless of what
        // was asked, and the raw text must never leak <think>/<|channel>thought/etc. as the title.
        val visible = ThinkingParser.parse(raw).answer
        val title = visible.trim().trim('"', '“', '”').lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(80)
        return if (title.isNotBlank()) {
            TitleResult(title, isFallback = false)
        } else {
            fallbackTitle(history)?.let { TitleResult(it, isFallback = true) }
        }
    }

    /** A reasoning model that spends its whole budget on an unclosed <think> block leaves nothing
     *  after stripping — rather than silently keeping "New chat", fall back to a title derived
     *  from the first real user message, same as a chat with auto-titling turned off would show
     *  until the user renames it, just without waiting for that. */
    private fun fallbackTitle(history: List<com.vervan.chat.data.db.entities.Message>): String? {
        val firstUserMessage = history.firstOrNull { it.role == MessageRole.USER }?.content?.trim() ?: return null
        val words = firstUserMessage.split(Regex("\\s+")).filter { it.isNotBlank() }.take(6)
        return words.joinToString(" ").take(80).ifBlank { null }
    }
}
