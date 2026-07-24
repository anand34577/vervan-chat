package com.vervan.chat.llm

/**
 * Splits a `<thinking>...</thinking>` block out of a raw model response —
 * thinking mode, built as a prompt instruction (see [com.vervan.chat.ui.chat.ChatViewModel])
 * since `tasks-genai` exposes no native reasoning-mode API. If the model didn't emit the
 * tag (wrong mode, or it just ignored the instruction), [Parsed.reasoning] is null and
 * [Parsed.answer] is the full text unchanged — always safe to call on any message.
 */
object ThinkingParser {
    data class Parsed(val reasoning: String?, val answer: String)

    private val COMPLETE = Regex(
        "<\\s*(thinking|think)\\s*>([\\s\\S]*?)<\\s*/\\s*\\1\\s*>",
        RegexOption.IGNORE_CASE
    )
    private val STREAMING = Regex("<\\s*(thinking|think)\\s*>([\\s\\S]*)$", RegexOption.IGNORE_CASE)

    fun parse(content: String): Parsed {
        val match = COMPLETE.find(content)
        if (match != null) {
            val reasoning = match.groupValues[2].trim()
            val answer = content.removeRange(match.range).trim()
            return Parsed(reasoning.ifBlank { null }, answer)
        }
        val streaming = STREAMING.find(content)
        if (streaming != null) {
            val reasoning = streaming.groupValues[2].trim()
            val answer = content.substring(0, streaming.range.first).trim()
            return Parsed(reasoning.ifBlank { null }, answer)
        }
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<") && listOf("<think>", "<thinking>").any { it.startsWith(trimmed, ignoreCase = true) }) {
            return Parsed(null, "")
        }
        return Parsed(null, content)
    }
}
