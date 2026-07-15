package com.vervan.chat.llm

/**
 * Splits a `<thinking>...</thinking>` block out of a raw model response — spec §15's
 * thinking mode, built as a prompt instruction (see [com.vervan.chat.ui.chat.ChatViewModel])
 * since `tasks-genai` exposes no native reasoning-mode API. If the model didn't emit the
 * tag (wrong mode, or it just ignored the instruction), [Parsed.reasoning] is null and
 * [Parsed.answer] is the full text unchanged — always safe to call on any message.
 */
object ThinkingParser {
    data class Parsed(val reasoning: String?, val answer: String)

    private val PATTERN = Regex("<thinking>([\\s\\S]*?)</thinking>", RegexOption.IGNORE_CASE)

    fun parse(content: String): Parsed {
        val match = PATTERN.find(content) ?: return Parsed(null, content)
        val reasoning = match.groupValues[1].trim()
        val answer = content.removeRange(match.range).trim()
        return Parsed(reasoning.ifBlank { null }, answer)
    }
}
