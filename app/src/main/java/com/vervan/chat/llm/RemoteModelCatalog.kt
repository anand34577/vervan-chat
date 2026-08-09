package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelRole

/**
 * Interpretation of a provider's `/models` list — the ids come back with no role, capability, or
 * type information whatsoever (`{"id": …, "object": "model", "owned_by": …}` is the whole payload
 * most servers send), so everything useful has to be inferred from the id string itself.
 *
 * Kept separate from [RemoteOpenAiEngine] (which only does transport) and from the dialog (which
 * only does layout) so the guessing rules are testable on their own and have one home to grow in.
 */
object RemoteModelCatalog {

    /**
     * Which role an id most likely fills.
     *
     * Embedding models are named for what they are, essentially universally — `text-embedding-*`
     * (OpenAI's own convention), `*-embedding-*`, `nomic-embed-text`, `bge-*`, `*-reranker-*`.
     * That naming is the only signal available short of calling `/embeddings` against every id and
     * watching what fails, so it's what this matches on.
     *
     * A guess, never a verdict: the dialog shows the inferred role and lets it be overridden,
     * because a local server can serve anything under any name.
     */
    fun inferRole(modelId: String): ModelRole {
        val id = modelId.lowercase()
        return if (EMBEDDING_MARKERS.any { it in id }) ModelRole.EMBEDDING else ModelRole.GENERATION
    }

    /** Substrings, not whole-word matches: real ids glue them onto the rest of the name with any
     *  separator or none at all (`qwen3-embedding-0.6b`, `text-embedding-3-large`, `bge-m3`). */
    private val EMBEDDING_MARKERS = listOf(
        "embedding", "embed", "-bge-", "bge-m3", "reranker", "rerank"
    )

    /**
     * Best-effort vision guess from the id alone — the same kind of naming signal [inferRole]
     * uses, for the same reason: `/models` carries nothing else. Covers both models whose name
     * says so directly (`-vl-`, `vision`, `llava`, `pixtral`, ...) and named families that are
     * multimodal by default in their current generation (Gemma 3+, GPT-4o+, Claude 3+, Gemini,
     * Llama 3.2+, Qwen2-VL/2.5-VL, Phi-3.5/4 multimodal).
     *
     * Wrong in both directions some of the time — a `bonsai-27b` with no family marker reads as
     * text-only even if it happens to be multimodal, and a family match can't tell a specific
     * fine-tune apart from its base. That is why this only sets the dialog's starting toggle,
     * never bypasses it — same "guess, not a verdict" rule [inferRole] documents.
     */
    fun inferVision(modelId: String): Boolean {
        val id = modelId.lowercase()
        if (EMBEDDING_MARKERS.any { it in id }) return false
        return VISION_MARKERS.any { it in id }
    }

    private val VISION_MARKERS = listOf(
        "vision", "-vl-", "-vl", "vl-", "multimodal", "llava", "pixtral", "moondream",
        "internvl", "minicpm-v", "cogvlm", "idefics",
        "gemma-3", "gemma-4", "qwen2-vl", "qwen2.5-vl", "qwen3-vl",
        "llama-3.2", "llama-4", "gpt-4o", "gpt-4.1", "gpt-5", "claude-3", "claude-4",
        "claude-5", "gemini", "phi-3.5-vision", "phi-4-multimodal"
    )
}
