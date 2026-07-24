package com.vervan.chat.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThinkingParserTest {

    @Test
    fun `extracts a complete think block and keeps the answer`() {
        val parsed = ThinkingParser.parse("<think>weighing options</think>The answer is 42.")
        assertEquals("weighing options", parsed.reasoning)
        assertEquals("The answer is 42.", parsed.answer)
    }

    @Test
    fun `supports the thinking tag spelling too`() {
        val parsed = ThinkingParser.parse("<thinking>hmm</thinking>Done.")
        assertEquals("hmm", parsed.reasoning)
        assertEquals("Done.", parsed.answer)
    }

    // The OFF-mode guarantee: whenever thinking is OFF, ChatViewModel strips via .answer, so a
    // model that ignores the instruction and emits a block anyway must not leak it to the user.
    @Test
    fun `off-strip drops the reasoning and returns only the answer`() {
        val parsed = ThinkingParser.parse("<think>should be hidden</think>Visible reply.")
        assertEquals("Visible reply.", parsed.answer)
    }

    // The OFF prefill injects an empty "<think>\n\n</think>" — that must reduce to a null reasoning
    // and leave the real answer untouched, not surface a blank Reasoning card.
    @Test
    fun `empty think block yields null reasoning`() {
        val parsed = ThinkingParser.parse("<think>\n\n</think>Straight to the point.")
        assertNull(parsed.reasoning)
        assertEquals("Straight to the point.", parsed.answer)
    }

    @Test
    fun `plain text with no tags passes through unchanged`() {
        val parsed = ThinkingParser.parse("Just a normal answer.")
        assertNull(parsed.reasoning)
        assertEquals("Just a normal answer.", parsed.answer)
    }

    // Mid-stream: the closing tag hasn't arrived yet. Everything after the open tag is reasoning,
    // anything before it is the (usually empty) answer-so-far.
    @Test
    fun `streaming open tag treats trailing text as reasoning`() {
        val parsed = ThinkingParser.parse("<think>still reasoning and not done")
        assertEquals("still reasoning and not done", parsed.reasoning)
        assertEquals("", parsed.answer)
    }

    // A partially-streamed opening tag ("<thi") must not be rendered as literal answer text while
    // the rest of the tag is still arriving.
    @Test
    fun `partial opening tag prefix is suppressed, not shown`() {
        val parsed = ThinkingParser.parse("<thi")
        assertNull(parsed.reasoning)
        assertEquals("", parsed.answer)
    }

    @Test
    fun `tag matching tolerates whitespace and case`() {
        val parsed = ThinkingParser.parse("< Think >reasoning< / THINK >Answer.")
        assertEquals("reasoning", parsed.reasoning)
        assertEquals("Answer.", parsed.answer)
    }
}
