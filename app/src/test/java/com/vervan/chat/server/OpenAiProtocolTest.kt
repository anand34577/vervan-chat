package com.vervan.chat.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the wire format down field by field. Every assertion here corresponds to something a real
 * client reads: the OpenAI SDK and LangChain need `finish_reason` and the opening `delta.role`,
 * anything doing context accounting needs `usage.prompt_tokens`, and a client showing a useful
 * failure message needs `error.code`. These were the fields that were missing, so they're the ones
 * worth a test that fails loudly if they go missing again.
 */
class OpenAiProtocolTest {

    private fun frameBody(frame: String): JSONObject {
        assertTrue("every SSE frame must start with `data: `", frame.startsWith("data: "))
        assertTrue("every SSE frame must end with a blank line", frame.endsWith("\n\n"))
        return JSONObject(frame.removePrefix("data: ").trim())
    }

    @Test
    fun openingFrameAnnouncesTheAssistantRole() {
        val json = frameBody(roleChunkFrame("id-1", "gemma"))
        val delta = json.getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
        assertEquals("assistant", delta.getString("role"))
        assertEquals("chat.completion.chunk", json.getString("object"))
    }

    @Test
    fun finalFrameCarriesFinishReason() {
        val choice = frameBody(chatChunkFrame("id-1", "gemma", JSONObject(), "stop"))
            .getJSONArray("choices").getJSONObject(0)
        assertEquals("stop", choice.getString("finish_reason"))
    }

    @Test
    fun contentFramesLeaveFinishReasonNull() {
        val choice = frameBody(chatChunkFrame("id-1", "gemma", JSONObject().put("content", "hi"), null))
            .getJSONArray("choices").getJSONObject(0)
        assertTrue("a mid-stream frame must not claim the turn ended", choice.isNull("finish_reason"))
        assertEquals("hi", choice.getJSONObject("delta").getString("content"))
    }

    @Test
    fun usageFrameHasNoPhantomChoice() {
        val json = frameBody(usageOnlyFrame("id-1", "gemma", usageJson(10, 20, 1000)))
        assertEquals(0, json.getJSONArray("choices").length())
        assertEquals(30, json.getJSONObject("usage").getInt("total_tokens"))
    }

    @Test
    fun usageReportsAllThreeTokenCounts() {
        val usage = usageJson(promptTokens = 12, completionTokens = 8, generationMs = 2000)
        assertEquals(12, usage.getInt("prompt_tokens"))
        assertEquals(8, usage.getInt("completion_tokens"))
        assertEquals(20, usage.getInt("total_tokens"))
        assertEquals(4.0, usage.getDouble("tokens_per_second"), 0.001)
    }

    @Test
    fun zeroDurationDoesNotDivideByZero() {
        assertEquals(0.0, usageJson(1, 1, 0).getDouble("tokens_per_second"), 0.0001)
    }

    @Test
    fun errorsCarryTypeAndCode() {
        val error = openAiErrorJson("No model named \"x\"", ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, "model")
            .getJSONObject("error")
        assertEquals(ErrorType.NOT_FOUND, error.getString("type"))
        assertEquals(ErrorCode.MODEL_NOT_FOUND, error.getString("code"))
        assertEquals("model", error.getString("param"))
    }

    @Test
    fun errorFrameIsAnErrorNotAContentDelta() {
        val json = frameBody(errorFrame("boom", ErrorType.SERVER, ErrorCode.SERVER_ERROR))
        assertTrue(json.has("error"))
        assertFalse("a failure must never be delivered as assistant text", json.has("choices"))
    }

    @Test
    fun completionCarriesReasoningSeparatelyFromContent() {
        val message = chatCompletionJson("id", "gemma", "4", "2+2 is 4", emptyList(), "stop")
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        assertEquals("4", message.getString("content"))
        assertEquals("2+2 is 4", message.getString("reasoning_content"))
    }

    @Test
    fun completionOmitsReasoningWhenTheModelDidNotThink() {
        val message = chatCompletionJson("id", "gemma", "4", null, emptyList(), "stop")
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        assertFalse(message.has("reasoning_content"))
    }

    @Test
    fun toolCallsUseTheSpecShapeWithArgumentsAsAString() {
        val calls = listOf(ApiChatRunner.ClientCall("call_1", "get_weather", """{"city":"Pune"}"""))
        val completion = chatCompletionJson("id", "gemma", "", null, calls, "tool_calls")
        val message = completion.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val call = message.getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("function", call.getString("type"))
        assertEquals("get_weather", call.getJSONObject("function").getString("name"))
        // A JSON *string*, not an object — clients call JSON.parse on it.
        assertEquals("""{"city":"Pune"}""", call.getJSONObject("function").getString("arguments"))
        assertTrue("a tool-calling turn has null content, not empty string", message.isNull("content"))
        assertEquals("tool_calls", completion.getJSONArray("choices").getJSONObject(0).getString("finish_reason"))
    }

    // The regression these guard: NanoHTTPD gzips any `text/*` response, and GZIPOutputStream only
    // flushes on finish(), so compressing an SSE stream held every delta until generation ended —
    // "the reply only appears when it's finished", in the browser and in third-party clients alike.
    @Test
    fun streamingResponsesAreNeverCompressed() {
        assertFalse(isCompressibleMime("text/event-stream"))
        assertFalse(isCompressibleMime("text/event-stream; charset=utf-8"))
        assertFalse(isCompressibleMime("TEXT/EVENT-STREAM"))
    }

    @Test
    fun ordinaryResponsesStayCompressible() {
        assertTrue(isCompressibleMime("application/json"))
        assertTrue(isCompressibleMime("text/html; charset=utf-8"))
        assertTrue(isCompressibleMime("text/javascript; charset=utf-8"))
        assertTrue(isCompressibleMime("text/css; charset=utf-8"))
        // A response with no declared type is NanoHTTPD's own "don't compress" case anyway.
        assertFalse(isCompressibleMime(null))
    }

    @Test
    fun nullCodeAndParamSerializeAsJsonNull() {
        val error = openAiErrorJson("bad", ErrorType.INVALID_REQUEST, null, null).getJSONObject("error")
        assertTrue(error.isNull("code"))
        assertTrue(error.isNull("param"))
        assertNull(error.opt("nonexistent"))
    }
}
