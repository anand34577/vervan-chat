package com.vervan.chat.server

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The splitter is what stands between a raw model stream and what an OpenAI client is allowed to
 * see, so the cases that matter are the ones where a naive implementation leaks: a half-received
 * tag, a `<think>` block, and `<tool_call>` JSON. Feeding the same text one character at a time and
 * asserting the concatenated deltas is the real test — anything that only works when chunks land
 * on tag boundaries passes a coarser test and fails in production.
 */
class ApiStreamSplitterTest {

    private fun drain(vararg chunks: String): Pair<String, String> {
        val splitter = ApiStreamSplitter()
        val answer = StringBuilder()
        val reasoning = StringBuilder()
        chunks.forEach { chunk ->
            val (a, r) = splitter.push(chunk)
            answer.append(a)
            reasoning.append(r)
        }
        val (a, r) = splitter.finish()
        answer.append(a)
        reasoning.append(r)
        return answer.toString() to reasoning.toString()
    }

    private fun drainCharByChar(text: String): Pair<String, String> =
        drain(*text.map { it.toString() }.toTypedArray())

    @Test
    fun plainTextPassesThroughUnchanged() {
        val (answer, reasoning) = drainCharByChar("Hello there, how can I help?")
        assertEquals("Hello there, how can I help?", answer)
        assertEquals("", reasoning)
    }

    @Test
    fun thinkingGoesToReasoningAndNeverToTheAnswer() {
        val (answer, reasoning) = drainCharByChar("<think>let me count: 2+2</think>The answer is 4.")
        assertEquals("The answer is 4.", answer)
        assertEquals("let me count: 2+2", reasoning)
    }

    @Test
    fun toolCallMarkupIsHiddenFromBothStreams() {
        val (answer, reasoning) = drainCharByChar(
            "Checking that.<tool_call>{\"tool\": \"battery_level\", \"params\": {}}</tool_call>"
        )
        assertEquals("Checking that.", answer)
        assertEquals("", reasoning)
    }

    @Test
    fun aPartialTagIsNeverEmittedAsText() {
        // The regression this class exists for: mid-stream the text ends with `<to`, which a naive
        // "strip what you can, emit the rest" pass would send to the client and then be unable to
        // take back once the tag turned out to be a tool call.
        val splitter = ApiStreamSplitter()
        val emitted = StringBuilder()
        listOf("Sure. ", "<to", "ol_call>{\"tool\": \"x\", \"params\": {}}", "</tool_call>").forEach {
            emitted.append(splitter.push(it).first)
        }
        assertEquals("Sure.", emitted.toString().trim())
        emitted.append(splitter.finish().first)
        assertEquals("Sure.", emitted.toString().trim())
    }

    @Test
    fun anUnterminatedAngleBracketIsFlushedAsLiteralTextAtTheEnd() {
        // Held back while the stream is live (it might still become a tag), but once generation is
        // over it is just text the model wrote, and dropping it would silently truncate the answer.
        val (answer, _) = drainCharByChar("2 < 3")
        assertEquals("2 < 3", answer)
    }

    @Test
    fun rawTextKeepsTheMarkupForTheToolLoop() {
        val splitter = ApiStreamSplitter()
        splitter.push("ok<tool_call>{\"tool\": \"x\", \"params\": {}}</tool_call>")
        splitter.finish()
        assertEquals("ok<tool_call>{\"tool\": \"x\", \"params\": {}}</tool_call>", splitter.rawText)
    }

    @Test
    fun stablePrefixStopsAtAnUnclosedTag() {
        assertEquals("abc", ApiStreamSplitter.stablePrefix("abc<thi"))
        assertEquals("abc<think>x", ApiStreamSplitter.stablePrefix("abc<think>x"))
        assertEquals("plain", ApiStreamSplitter.stablePrefix("plain"))
    }
}
