package com.vervan.chat.llm

/**
 * Separates model reasoning from the visible answer.
 *
 * Models do not all use the same spelling: some emit <think>, some emit <thinking> or
 * <analysis>, and streaming can expose a partial opening tag or an unclosed block. This parser
 * deliberately treats every supported reasoning block as non-answer text until it is closed.
 * The raw content is retained for the collapsible reasoning card, while [answer] is safe for the
 * normal chat renderer.
 */
object ThinkingParser {
    data class Parsed(
        val reasoning: String?,
        val answer: String,
        val hasThinking: Boolean = reasoning != null,
        val thinkingInProgress: Boolean = false
    )

    private const val TAGS = "(?:think(?:ing)?|analysis|reasoning|thought)"

    private val OPEN = Regex(
        "(?is)<\\s*$TAGS(?:\\s+[^>]*)?>|<\\|(?:$TAGS|begin_of_thought)\\|>"
    )
    private val CLOSE = Regex(
        "(?is)<\\s*/\\s*$TAGS\\s*>|<\\|/(?:$TAGS)\\|>|<\\|end_(?:$TAGS|of_thought)\\|>"
    )
    private val PARTIAL_OPEN_CANDIDATES = listOf(
        "<think>", "<thinking>", "<analysis>", "<reasoning>", "<thought>",
        "<|think|>", "<|thinking|>", "<|analysis|>", "<|reasoning|>", "<|thought|>",
        "<|begin_of_thought|>"
    )

    fun parse(content: String): Parsed {
        if (content.isEmpty()) return Parsed(null, content)

        val visible = StringBuilder()
        val reasoning = StringBuilder()
        var cursor = 0
        var foundThinking = false
        var thinkingInProgress = false

        while (cursor < content.length) {
            val open = OPEN.find(content, cursor) ?: break
            foundThinking = true
            visible.append(content, cursor, open.range.first)

            val reasoningStart = open.range.last + 1
            val close = CLOSE.find(content, reasoningStart)
            if (close == null) {
                if (reasoning.isNotEmpty()) reasoning.append('\n')
                reasoning.append(content, reasoningStart, content.length)
                thinkingInProgress = true
                cursor = content.length
                break
            }

            if (reasoning.isNotEmpty()) reasoning.append('\n')
            reasoning.append(content, reasoningStart, close.range.first)
            cursor = close.range.last + 1
        }

        if (foundThinking) {
            if (cursor < content.length) visible.append(content, cursor, content.length)
            return Parsed(
                reasoning = reasoning.toString().trim().ifBlank { null },
                answer = visible.toString().trim(),
                hasThinking = true,
                thinkingInProgress = thinkingInProgress
            )
        }

        // A streamed opening tag can arrive over several callbacks. Hide the incomplete suffix
        // instead of briefly rendering "<thi" or similar parser noise in the chat bubble.
        val partialStart = content.lastIndexOf('<')
        if (partialStart >= 0) {
            val partial = content.substring(partialStart).trim().lowercase()
            if (!partial.contains('>') && PARTIAL_OPEN_CANDIDATES.any { it.startsWith(partial) }) {
                return Parsed(
                    reasoning = null,
                    answer = content.substring(0, partialStart).trim(),
                    hasThinking = true,
                    thinkingInProgress = true
                )
            }
        }

        return Parsed(null, content)
    }
}
