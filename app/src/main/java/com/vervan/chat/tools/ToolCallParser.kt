package com.vervan.chat.tools

import org.json.JSONObject

/**
 * Gemma-class on-device models don't have a stable native function-calling API exposed
 * through MediaPipe yet, so tool calls are prompt-engineered: the model is instructed to
 * emit `<tool_call>{"tool": "name", "params": {...}}</tool_call>` when it wants to use one.
 * This parses that block back out. ponytail: one call per block, first match only — the
 * app-side loop in ChatViewModel is what allows multiple sequential calls, not the model
 * emitting several in one response.
 */
object ToolCallParser {
    private val pattern = Regex("<tool_call>([\\s\\S]*?)</tool_call>")

    fun parse(text: String): ToolCall? {
        val match = pattern.find(text) ?: return null
        return try {
            val json = JSONObject(match.groupValues[1].trim())
            val name = json.optString("tool").takeIf { it.isNotBlank() } ?: return null
            val params = json.optJSONObject("params") ?: JSONObject()
            ToolCall(name, params, match.value)
        } catch (e: Exception) {
            null
        }
    }

    fun stripToolCall(text: String, call: ToolCall): String = text.replace(call.rawBlock, "").trim()
}
