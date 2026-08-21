package com.vervan.chat.llm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteOpenAiRequestTest {

    @Test
    fun `preserves turns, tools, reasoning controls, and explicit sampling overrides`() {
        val body = RemoteOpenAiEngine().requestBody(
            remoteModelId = "reasoner",
            prompt = "fallback",
            systemPrompt = "fallback system",
            temperature = 0.7f,
            topP = 0.9f,
            maxOutputTokens = 800,
            stopSequences = emptyList(),
            options = RemoteRequestOptions(
                messages = listOf("system" to "Use concise answers", "user" to "Hello"),
                tools = listOf(
                    RemoteToolDefinition(
                        "lookup",
                        "Look something up",
                        JSONObject().put("type", "object").put("properties", JSONObject())
                    )
                ),
                toolChoice = "auto",
                thinkingMode = "DEEP",
                supportsThinking = true,
                thinkingParameter = "enable_thinking",
                seed = 42,
                minP = 0.08f,
                repetitionPenalty = 1.12f
            )
        )
        val json = JSONObject(body)
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Use concise answers", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("Hello", messages.getJSONObject(1).getString("content"))
        assertTrue(json.getBoolean("enable_thinking"))
        assertEquals(800, json.getInt("max_tokens"))
        assertEquals(42, json.getInt("seed"))
        assertEquals(0.08, json.getDouble("min_p"), 0.0001)
        assertEquals(1.12, json.getDouble("repetition_penalty"), 0.0001)
        assertEquals("auto", json.getString("tool_choice"))
        assertEquals("lookup", json.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
    }

    @Test
    fun `adds attachments only to the final user turn`() {
        val audio = File.createTempFile("vervan-request", ".wav")
        try {
            audio.writeBytes(byteArrayOf(1, 2, 3))
            val body = RemoteOpenAiEngine().requestBody(
                remoteModelId = "vision",
                prompt = "fallback",
                systemPrompt = null,
                temperature = 0.7f,
                topP = 0.9f,
                maxOutputTokens = 100,
                stopSequences = emptyList(),
                audioPath = audio.absolutePath,
                options = RemoteRequestOptions(messages = listOf("user" to "old", "assistant" to "answer", "user" to "new"))
            )
            val messages = JSONObject(body).getJSONArray("messages")
            assertTrue(messages.getJSONObject(0).getString("content") == "old")
            val finalContent = messages.getJSONObject(2).getJSONArray("content")
            assertEquals("new", finalContent.getJSONObject(0).getString("text"))
            assertEquals("input_audio", finalContent.getJSONObject(1).getString("type"))
        } finally {
            audio.delete()
        }
    }

    @Test
    fun `strict reasoning requests omit incompatible sampling controls`() {
        val json = JSONObject(
            RemoteOpenAiEngine().requestBody(
                remoteModelId = "o-model",
                prompt = "hello",
                systemPrompt = null,
                temperature = 0.7f,
                topP = 0.9f,
                maxOutputTokens = 400,
                stopSequences = emptyList(),
                topK = 40,
                options = RemoteRequestOptions(supportsThinking = true, thinkingMode = "BALANCED", minP = 0.1f, repetitionPenalty = 1.1f)
            )
        )
        assertEquals("medium", json.getString("reasoning_effort"))
        assertEquals(400, json.getInt("max_completion_tokens"))
        assertTrue(!json.has("temperature"))
        assertTrue(!json.has("top_p"))
        assertTrue(!json.has("top_k"))
        assertTrue(!json.has("min_p"))
        assertTrue(!json.has("repetition_penalty"))
    }
}
