package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo

/**
 * The prompt-engineered "thinking mode" policy — extracted out of `ChatViewModel` so
 * the three coupled decisions (what to tell the model, what to prefill, and the hard native
 * budget) live together, next to [ThinkingParser] which undoes their effect on the display side,
 * and can be unit-tested without a ViewModel. All three are pure functions of their inputs.
 *
 * Neither `tasks-genai` (LiteRT-LM) nor llama.cpp's non-Jinja `llama_chat_apply_template` exposes
 * a native reasoning-budget flag, so [reasoningInstruction] asks the model to wrap reasoning in
 * `<thinking>` tags; [assistantPrefillFor] forces an open/closed `<think>` block for llama.cpp so
 * the outcome doesn't depend on the model complying; and [reasoningBudgetFor] hands the native
 * loop a hard token cap that force-injects `</think>`.
 */
object ThinkingPolicy {

    /** Valid thinking-mode values, in escalating order. */
    val MODES = listOf("OFF", "FAST", "BALANCED", "DEEP")

    /**
     * Adds the model-native activation token at the beginning of the system turn. The token and
     * protocol come from [ThinkingSpec], not from a model-name branch, so future model packages
     * can declare their own activation text through metadata or the Configure screen.
     *
     * [mode] is already the resolved mode, so OFF deliberately leaves the system prompt alone.
     * [ThinkingSpec.forModel] handles persisted metadata, embedded templates, catalog entries,
     * and the compatibility fallback for older model rows.
     */
    fun withModelThinkingActivation(systemPrompt: String, model: ModelInfo?, mode: String): String {
        if (mode == "OFF" || model == null || model.supportsThinking == false) return systemPrompt
        val spec = ThinkingSpec.forModel(model)
        if (spec.activation != ThinkingSpec.Activation.SYSTEM_TOKEN) return systemPrompt
        val token = spec.enableText?.takeIf { it.isNotBlank() } ?: return systemPrompt
        if (systemPrompt.trimStart().startsWith(token)) return systemPrompt
        return buildString {
            append(token)
            if (systemPrompt.isNotBlank()) {
                append('\n')
                append(systemPrompt)
            }
        }.trim()
    }

    /**
     * Resolves the capability → model-default → chat-override hierarchy into the single mode
     * actually used for a generation: a chat override wins if set, otherwise the model's own
     * default, otherwise OFF — but a model whose Thinking capability is off is always OFF
     * regardless of what either level requests, and an unrecognized/stale mode value falls back
     * to OFF rather than being sent to the engine.
     */
    fun effectiveThinkingMode(chatOverride: String?, modelDefault: String?, supportsThinking: Boolean?): String {
        if (supportsThinking == false) return "OFF"
        val mode = chatOverride ?: modelDefault ?: "OFF"
        return if (mode in MODES) mode else "OFF"
    }

    /**
     * The instruction text appended to the prompt. Empty for OFF on a non-reasoning model, so a
     * chat that never touches thinking pays no prompt cost. For a llama.cpp model it also appends
     * the literal `/think`/`/no_think` tokens Qwen3-family GGUF models were fine-tuned on plus a
     * soft token-budget hint. This is a *request* the model can ignore — [assistantPrefillFor] and
     * [reasoningBudgetFor] are what actually enforce the outcome for llama.cpp.
     */
    fun reasoningInstruction(
        mode: String,
        engine: ModelEngine = ModelEngine.LITERT_LM,
        // True for models that reason natively (e.g. DeepSeek-R1). For those, OFF must actively
        // *suppress* reasoning — an empty instruction leaves the model free to keep thinking, which
        // is exactly why the per-chat OFF override appeared to do nothing. Display-side stripping
        // (see suppressReasoning in ChatViewModel.runGenerationLoop) is the hard guarantee on top.
        isReasoningModel: Boolean = false
    ): String {
        // A native reasoner already has its own reasoning channel — a separate `reasoning_content`
        // stream for REMOTE_API (see ReasoningStreamMerger), or its own unprompted <think> block
        // for LiteRT-LM/llama.cpp — so telling it to ALSO wrap its visible answer in literal
        // <thinking></thinking> tags is a second, redundant instruction on top of a channel it's
        // already using correctly. A model trying to satisfy both ends up narrating the instruction
        // itself ("the instructions say to wrap thinking in tags...") into the *answer* text instead
        // of just answering — reasoning bleeding out past the collapsed thinking card, mid-reply.
        // A non-reasoning model has no such channel, so asking it to role-play one via literal tags
        // is still the only way to get any visible reasoning out of it at all.
        val base = if (isReasoningModel) {
            when (mode) {
                "FAST" -> "Keep your reasoning brief before answering."
                "BALANCED" -> "Think through the problem step by step before answering."
                "DEEP" -> "Think through the problem thoroughly, considering multiple angles and edge cases, before answering."
                else -> "Answer directly and concisely. Do not produce any internal reasoning, analysis, or <think> sections — reply with only the final answer."
            }
        } else {
            when (mode) {
                "FAST" -> "Before answering, briefly think through the problem in 1-2 sentences wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
                "BALANCED" -> "Before answering, think through the problem step by step wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
                "DEEP" -> "Before answering, think through the problem thoroughly, considering multiple angles and edge cases, wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
                else -> ""
            }
        }
        if (engine != ModelEngine.LLAMA_CPP) return base
        return when (mode) {
            "FAST" -> "$base /think (keep your reasoning under roughly 256 tokens)"
            "BALANCED" -> "$base /think (keep your reasoning under roughly 1024 tokens)"
            "DEEP" -> "$base /think (keep your reasoning under roughly 4096 tokens)"
            else -> "$base /no_think".trim()
        }
    }

    /**
     * Assistant-message prefill — the actual enforcement mechanism behind
     * [reasoningInstruction]'s text. llama.cpp's prompt gets this appended right after the chat
     * template's assistant-turn-start tokens (see `nativeGenerate`'s `assistantPrefill`), so
     * generation literally continues from an already-open or already-closed `<think>` block.
     * `null` for OFF/non-thinking models leaves the prompt untouched.
     */
    fun assistantPrefillFor(
        mode: String,
        engine: ModelEngine,
        // Only force a <think> block on a model that actually reasons. Prefilling "<think>\n" onto
        // a non-reasoning GGUF (e.g. a plain Gemma) would make it emit stray reasoning tags and,
        // worse, arm the native reasoning-budget counter against ordinary answer tokens — so the
        // </think> auto-inject could fire mid-answer. null here leaves such models untouched.
        isReasoningModel: Boolean,
        thinkingSpec: ThinkingSpec = ThinkingSpec()
    ): String? {
        if (
            engine != ModelEngine.LLAMA_CPP ||
            !isReasoningModel ||
            thinkingSpec.activation == ThinkingSpec.Activation.SYSTEM_TOKEN
        ) return null
        return if (mode == "OFF") "<think>\n\n</think>\n\n" else "<think>\n"
    }

    /**
     * The hard reasoning-token budget handed to llama.cpp's native loop (`nativeGenerate`), which
     * force-injects `</think>` once the model has spent this many tokens still inside an open
     * `<think>` block. Returns -1 ("no cap / not applicable") for anything but a reasoning
     * llama.cpp model in a non-OFF mode, since those are exactly the cases where
     * [assistantPrefillFor] opens a `<think>` block for the budget to bound. LiteRT-LM's SDK
     * exposes no equivalent native hook, so its budget stays a prompt hint.
     */
    fun reasoningBudgetFor(
        mode: String,
        engine: ModelEngine,
        isReasoningModel: Boolean,
        thinkingSpec: ThinkingSpec = ThinkingSpec()
    ): Int {
        if (
            engine != ModelEngine.LLAMA_CPP ||
            !isReasoningModel ||
            thinkingSpec.activation == ThinkingSpec.Activation.SYSTEM_TOKEN
        ) return -1
        return when (mode) {
            "FAST" -> 256
            "BALANCED" -> 1024
            "DEEP" -> 4096
            else -> -1 // OFF: the prefill already closed the block, nothing to cap
        }
    }
}
