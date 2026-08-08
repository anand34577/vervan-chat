package com.vervan.chat.server

import com.vervan.chat.llm.ThinkingParser
import com.vervan.chat.tools.ToolCallParser

/**
 * Splits a raw generation stream into the two things an OpenAI client is allowed to see —
 * `delta.content` (the answer) and `delta.reasoning_content` (the model's thinking) — while
 * keeping `<tool_call>` markup out of both.
 *
 * The hard part is that both [ThinkingParser] and [ToolCallParser] work on *complete* text, and
 * a stream can stop anywhere: mid-`<thi`, mid-`<tool_ca`, mid-JSON. Emitting eagerly and
 * retracting later isn't possible over SSE — a delta that has been sent is sent. So [push]
 * only ever parses a **stable prefix** of the raw text: everything up to the last `<` that
 * hasn't been closed by a `>` yet. A partial tag is therefore never classified (and never
 * emitted) until the stream reveals what it actually is, which makes the visible text
 * monotonically non-decreasing and each [push] a pure append.
 *
 * Not thread-safe — one instance per generation, driven from that generation's own collector.
 */
class ApiStreamSplitter {
    private val raw = StringBuilder()
    private var emittedAnswer = 0
    private var emittedReasoning = 0

    /** Everything the model has produced this hop, markup included — what gets fed back into the
     * next tool hop's history, and what [ToolCallParser.parseAll] is run against at the end. */
    val rawText: String get() = raw.toString()

    /** Appends [chunk] and returns whatever newly became safe to send, as
     * `(answerDelta, reasoningDelta)`. Either may be empty; both are empty while the stream is
     * inside an unresolved tag. */
    fun push(chunk: String): Pair<String, String> {
        raw.append(chunk)
        return recompute()
    }

    /** Flushes whatever the stable-prefix rule was still holding back once generation has ended
     * and no more text can arrive — at that point the full raw text *is* the stable prefix, so a
     * trailing unterminated `<` is just literal output the client should see. */
    fun finish(): Pair<String, String> = recompute(stable = raw.toString())

    private fun recompute(stable: String = stablePrefix(raw)): Pair<String, String> {
        // Tool-call blocks first: they are markup the client already receives as structured
        // `tool_calls`, so their raw JSON must never also appear as assistant text.
        val withoutTools = ToolCallParser.stripForDisplay(stable)
        val parsed = ThinkingParser.parse(withoutTools)
        val answerDelta = appendOnlyDelta(parsed.answer, emittedAnswer)
        emittedAnswer += answerDelta.length
        val reasoning = parsed.reasoning.orEmpty()
        val reasoningDelta = appendOnlyDelta(reasoning, emittedReasoning)
        emittedReasoning += reasoningDelta.length
        return answerDelta to reasoningDelta
    }

    /** The suffix of [current] past [alreadySent] characters — empty when the text shrank instead
     * of growing. The stable-prefix rule makes shrinking unreachable in practice; this is the
     * belt-and-braces path that keeps a parser edge case from producing a garbled delta (or a
     * negative substring index) rather than merely a missing one. */
    private fun appendOnlyDelta(current: String, alreadySent: Int): String =
        if (current.length <= alreadySent) "" else current.substring(alreadySent)

    companion object {
        /** [text] truncated at the last `<` that has no `>` after it — i.e. the longest prefix
         * containing no partially-received tag. Text with no open `<` is returned whole. */
        fun stablePrefix(text: CharSequence): String {
            val lastOpen = text.lastIndexOf('<')
            if (lastOpen < 0) return text.toString()
            val lastClose = text.lastIndexOf('>')
            if (lastClose > lastOpen) return text.toString()
            return text.substring(0, lastOpen)
        }
    }
}
