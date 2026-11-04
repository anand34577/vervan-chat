package com.vervan.chat.llm

/**
 * Model families whose decoder keeps a running state (short convolutions, Mamba/SSM, gated-delta
 * linear attention) instead of only a per-position KV cache. LiteRT-LM does not reset that state
 * between conversations on one Engine (LiteRT-LM issue #3165), so [LlmEngine] gives these models
 * a fresh Engine for every turn. Matched on the file name because .litertlm metadata does not
 * declare it.
 */
object RecurrentStateModels {
    private val PATTERN = Regex(
        "(?i)(^|[^a-z])(lfm[0-9]|liquid|qwen3[._-]?5|qwen3[._-]?next|granite-?4[._-]?[0-9]*-?h|mamba|jamba|rwkv|falcon-?h1|decider)"
    )

    fun matches(fileName: String): Boolean = PATTERN.containsMatchIn(fileName)
}
