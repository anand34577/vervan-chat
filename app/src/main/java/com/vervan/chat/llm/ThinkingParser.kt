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

    // Model families use all of these spellings for the same hidden reasoning channel. Keep the
    // variants here instead of making each preview/bubble renderer guess which tag to remove.
    private const val TAGS = "(?:think(?:s|ing)?|analysis|reasoning|thoughts?)"

    private val OPEN = Regex(
        // Last alternative: Gemma's own template on LM Studio/vLLM emits a literal
        // "<|channel>thought" open marker — no pipe before the closing angle bracket, and
        // "thought" glued directly on rather than being the tag name itself.
        "(?is)<\\s*$TAGS(?:\\s+[^>]*)?>|<\\|(?:$TAGS|begin_of_thought)\\|>|<\\|channel>thought"
    )
    private val CLOSE = Regex(
        // Gemma's matching close marker is bare "<channel|>", not "<|channel|>" — deliberately
        // asymmetric with its own open marker above.
        "(?is)<\\s*/\\s*$TAGS\\s*>|<\\|/(?:$TAGS)\\|>|<\\|end_(?:$TAGS|of_thought)\\|>|<channel\\|>"
    )
    private val PARTIAL_OPEN_CANDIDATES = listOf(
        "<think>", "<thinks>", "<thinking>", "<analysis>", "<reasoning>", "<thought>", "<thoughts>",
        "<|think|>", "<|thinks|>", "<|thinking|>", "<|analysis|>", "<|reasoning|>", "<|thought|>", "<|thoughts|>",
        "<|begin_of_thought|>", "<|channel>thought"
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
