package com.vervan.chat.tools

import org.json.JSONObject

/**
 * Gemma-class on-device models don't have a stable native function-calling API exposed
 * through MediaPipe yet, so tool calls are prompt-engineered: the model is instructed to
 * emit `<tool_call>{"tool": "name", "params": {...}}</tool_call>` when it wants to use one.
 * This parses that block back out. The tag match is case/whitespace-tolerant (mirrors
 * [com.vervan.chat.llm.ThinkingParser]/[com.vervan.chat.llm.ClarificationParser]) since a
 * small on-device model emitting `<Tool_Call>` or `<tool_call >` is a real, observed failure
 * mode, not a hypothetical one — a strict literal match silently dropped those calls.
 */
object ToolCallParser {
    /** [malformed] holds the raw `<tool_call>` blocks that matched the tag but failed to
     * parse as `{"tool": "...", "params": {...}}` — callers use this to tell the model its
     * call didn't go through, instead of silently dropping it. */
    data class ParseResult(val calls: List<ToolCall>, val malformed: List<String>)

    private val COMPLETE = Regex("<\\s*tool_call\\s*>([\\s\\S]*?)<\\s*/\\s*tool_call\\s*>", RegexOption.IGNORE_CASE)
    private val STREAMING = Regex("<\\s*tool_call\\s*>([\\s\\S]*)$", RegexOption.IGNORE_CASE)
    private val CODE_FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

    /** First well-formed tool call in [text], or null if there isn't one. Kept for callers
     * that only ever act on a single call per turn; see [parseAll] for the full set. */
    fun parse(text: String): ToolCall? = parseAll(text).calls.firstOrNull()

    /** Every `<tool_call>` block in [text], split into ones that parsed successfully and raw
     * blocks that matched the tag but weren't valid `{"tool": ..., "params": ...}` JSON. */
    fun parseAll(text: String): ParseResult {
        val calls = mutableListOf<ToolCall>()
        val malformed = mutableListOf<String>()
        for (match in COMPLETE.findAll(text)) {
            val body = match.groupValues[1].trim().replace(CODE_FENCE, "").trim()
            val call = parseOne(body, match.value)
            if (call != null) calls.add(call) else malformed.add(match.value)
        }
        return ParseResult(calls, malformed)
    }

    private fun parseOne(body: String, rawBlock: String): ToolCall? = try {
        val json = JSONObject(body)
        val name = json.optString("tool").takeIf { it.isNotBlank() }
        if (name == null) null else ToolCall(name, json.optJSONObject("params") ?: JSONObject(), rawBlock)
    } catch (e: Exception) {
        null
    }

    fun stripToolCall(text: String, call: ToolCall): String = text.replace(call.rawBlock, "").trim()

    /** Removes every block in [calls] (parsed or malformed — both are raw matches of the same
     * tag) from [text], so a second/unexecuted call doesn't linger as visible raw JSON. */
    fun stripAll(text: String, calls: Collection<String>): String {
        var result = text
        for (block in calls) result = result.replace(block, "")
        return result.trim()
    }

    /** Hides `<tool_call>` markup from live/finished display text: complete blocks are removed
     * outright, and a still-open tag with no closer yet (mid-stream) is cut off entirely —
     * same streaming-safe pattern as [com.vervan.chat.llm.ThinkingParser]/
     * [com.vervan.chat.llm.ClarificationParser], so raw tool-call JSON never flashes in the
     * chat bubble while the model is typing it out.
     */
    fun stripForDisplay(text: String): String {
        val withoutComplete = COMPLETE.replace(text, "")
        val streaming = STREAMING.find(withoutComplete) ?: return withoutComplete.trim()
        return withoutComplete.substring(0, streaming.range.first).trim()
    }
}
