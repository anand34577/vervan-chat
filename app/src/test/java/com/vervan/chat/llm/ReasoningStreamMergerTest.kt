package com.vervan.chat.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningStreamMergerTest {

    @Test
    fun `wraps reasoning deltas in think tags and lets content through untouched`() {
        val merger = ReasoningStreamMerger()
        val out = StringBuilder()
        merger.accept(null, "weighing ").forEach(out::append)
        merger.accept(null, "options").forEach(out::append)
        merger.accept("The answer", null).forEach(out::append)
        merger.accept(" is 42.", null).forEach(out::append)
        out.append(merger.finish().joinToString(""))
        assertEquals("<think>\nweighing options\n</think>\nThe answer is 42.", out.toString())
    }

    @Test
    fun `plain content with no reasoning field is untouched`() {
        val merger = ReasoningStreamMerger()
        val out = StringBuilder()
        merger.accept("Hello", null).forEach(out::append)
        merger.accept(" world", null).forEach(out::append)
        out.append(merger.finish().joinToString(""))
        assertEquals("Hello world", out.toString())
    }

    // finish_reason (or a dropped connection) can end the stream while reasoning is still the
    // last thing that arrived — the block must still close so the whole answer isn't swallowed.
    @Test
    fun `stream ending mid-reasoning still closes the tag`() {
        val merger = ReasoningStreamMerger()
        val out = StringBuilder()
        merger.accept(null, "still thinking").forEach(out::append)
        out.append(merger.finish().joinToString(""))
        assertEquals("<think>\nstill thinking\n</think>\n", out.toString())
    }

    @Test
    fun `finish after content already closed the tag emits nothing more`() {
        val merger = ReasoningStreamMerger()
        merger.accept(null, "reasoning")
        merger.accept("answer", null)
        assertEquals(emptyList<String>(), merger.finish())
    }

    // A provider that resumes reasoning_content after content has already started (interleaved,
    // not one clean reasoning-then-answer split) must re-open a fresh <think> block, not dump the
    // later reasoning text straight into the content stream unwrapped.
    @Test
    fun `reasoning resuming after content re-opens a new think block`() {
        val merger = ReasoningStreamMerger()
        val out = StringBuilder()
        merger.accept(null, "first thought").forEach(out::append)
        merger.accept("partial answer", null).forEach(out::append)
        merger.accept(null, "second thought").forEach(out::append)
        merger.accept(" rest of answer", null).forEach(out::append)
        out.append(merger.finish().joinToString(""))
        assertEquals(
            "<think>\nfirst thought\n</think>\npartial answer<think>\nsecond thought\n</think>\n rest of answer",
            out.toString()
        )
    }

    @Test
    fun `a chunk carrying both fields at once emits reasoning close then content`() {
        val merger = ReasoningStreamMerger()
        val out = StringBuilder()
        merger.accept(null, "reasoning").forEach(out::append)
        merger.accept("answer", "more reasoning in the same chunk").forEach(out::append)
        assertEquals("<think>\nreasoningmore reasoning in the same chunk\n</think>\nanswer", out.toString())
    }
}
