package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelEngine

/**
 * The prompt-engineered "thinking mode" policy (spec §15) — extracted out of `ChatViewModel` so
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
        val base = when (mode) {
            "FAST" -> "Before answering, briefly think through the problem in 1-2 sentences wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
            "BALANCED" -> "Before answering, think through the problem step by step wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
            "DEEP" -> "Before answering, think through the problem thoroughly, considering multiple angles and edge cases, wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
            else -> if (isReasoningModel) "Answer directly and concisely. Do not produce any internal reasoning, analysis, or <think> sections — reply with only the final answer." else ""
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
     * Assistant-message prefill (spec §15) — the actual enforcement mechanism behind
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
        isReasoningModel: Boolean
    ): String? {
        if (engine != ModelEngine.LLAMA_CPP || !isReasoningModel) return null
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
        isReasoningModel: Boolean
    ): Int {
        if (engine != ModelEngine.LLAMA_CPP || !isReasoningModel) return -1
        return when (mode) {
            "FAST" -> 256
            "BALANCED" -> 1024
            "DEEP" -> 4096
            else -> -1 // OFF: the prefill already closed the block, nothing to cap
        }
    }
}
