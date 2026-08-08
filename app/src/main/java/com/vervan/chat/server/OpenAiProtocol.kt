package com.vervan.chat.server

import com.vervan.chat.server.ApiChatRunner.ClientCall
import org.json.JSONArray
import org.json.JSONObject

/**
 * The exact JSON shapes OpenAI clients parse, in one place.
 *
 * These matter more than they look: real clients branch on fields this app previously omitted.
 * `finish_reason` is how the OpenAI SDK, LangChain, LiteLLM and the Vercel AI SDK decide a stream
 * ended cleanly rather than being cut off; the first chunk's `delta.role` is how several of them
 * decide a message has started at all; `usage.prompt_tokens` is what anything doing context or
 * cost accounting reads. Keeping them here (rather than inline in the handlers) is also what
 * makes them testable without standing up a server.
 */

/**
 * Whether a response of this MIME type may be gzip-compressed by the HTTP layer.
 *
 * NanoHTTPD's own rule is "compress anything whose MIME type contains `text/` or `/json`", and
 * `text/event-stream` satisfies it. Its compressed path wraps the socket in a plain
 * `GZIPOutputStream`, which only flushes on `finish()` — so every SSE frame written during
 * generation stayed inside the compressor until the response ended, and both the browser UI and
 * third-party OpenAI clients saw a whole reply appear at once instead of streaming. Browsers and
 * most client libraries always advertise gzip, so this was not an edge case.
 *
 * A stream is never compressible here for that reason. Everything else keeps compression, where it
 * is a real win over a phone's Wi-Fi link and the body is produced in one shot anyway.
 */
internal fun isCompressibleMime(mimeType: String?): Boolean =
    mimeType != null && !mimeType.contains("event-stream", ignoreCase = true)

/** OpenAI's error `type` values. `code` is the machine-readable half clients actually switch on
 * (`model_not_found`, `context_length_exceeded`, …) and is carried separately. */
internal object ErrorType {
    const val INVALID_REQUEST = "invalid_request_error"
    const val NOT_FOUND = "not_found_error"
    const val SERVER = "server_error"
    const val RATE_LIMIT = "rate_limit_error"
}

internal object ErrorCode {
    const val MODEL_NOT_FOUND = "model_not_found"
    const val UNSUPPORTED_PARAMETER = "unsupported_parameter"
    const val UNSUPPORTED_VALUE = "unsupported_value"
    const val INVALID_VALUE = "invalid_value"
    const val CONTEXT_LENGTH_EXCEEDED = "context_length_exceeded"
    const val RATE_LIMIT_EXCEEDED = "rate_limit_exceeded"
    const val SERVER_ERROR = "server_error"
}

/** `{"error": {"message", "type", "param", "code"}}` — the full four-field shape, not just
 * message+type. A client that surfaces `error.code` to *its* user (most do) shows something
 * actionable instead of a generic failure. */
internal fun openAiErrorJson(message: String, type: String, code: String?, param: String?): JSONObject =
    JSONObject().put(
        "error",
        JSONObject()
            .put("message", message)
            .put("type", type)
            .put("param", param ?: JSONObject.NULL)
            .put("code", code ?: JSONObject.NULL)
    )

/**
 * `usage`, with all three token counts the spec defines. [promptTokens] is an estimate from the
 * same [com.vervan.chat.llm.estimateTokens] approximation the native chat UI uses for its own
 * tok/s readout — neither on-device engine exposes its real tokenizer count at this layer, so an
 * estimate that matches what the app itself reports is more useful than omitting the field.
 * `generation_ms`/`tokens_per_second` are additive extras the bundled web UI's stats line reads.
 */
internal fun usageJson(promptTokens: Int, completionTokens: Int, generationMs: Long): JSONObject {
    val seconds = generationMs / 1000.0
    return JSONObject()
        .put("prompt_tokens", promptTokens)
        .put("completion_tokens", completionTokens)
        .put("total_tokens", promptTokens + completionTokens)
        .put("generation_ms", generationMs)
        .put("tokens_per_second", if (seconds > 0) completionTokens / seconds else 0.0)
}

/** `tool_calls`, in the `{"id","type":"function","function":{"name","arguments"}}` shape. Note
 * `arguments` is a JSON *string*, not an object — that's the spec, and clients `JSON.parse` it. */
internal fun toolCallsJson(calls: List<ClientCall>): JSONArray {
    val array = JSONArray()
    calls.forEachIndexed { index, call ->
        array.put(
            JSONObject()
                .put("index", index)
                .put("id", call.id)
                .put("type", "function")
                .put(
                    "function",
                    JSONObject().put("name", call.name).put("arguments", call.argumentsJson)
                )
        )
    }
    return array
}

/** A complete (non-streaming) `chat.completion`. `reasoning_content` is only present when the
 * model actually produced thinking — the field name matches what DeepSeek's API popularized and
 * what Open WebUI/LibreChat already render, so a thinking model behaves over this API the way it
 * does in the app instead of leaking raw `<think>` tags into `content`. */
internal fun chatCompletionJson(
    id: String,
    model: String,
    content: String,
    reasoning: String?,
    toolCalls: List<ClientCall>,
    finishReason: String
): JSONObject {
    val message = JSONObject().put("role", "assistant").put("content", content)
    if (!reasoning.isNullOrBlank()) message.put("reasoning_content", reasoning)
    if (toolCalls.isNotEmpty()) {
        message.put("tool_calls", toolCallsJson(toolCalls))
        // A tool-calling turn's content is conventionally null rather than "", which is what
        // clients check before deciding to render a message bubble at all.
        if (content.isBlank()) message.put("content", JSONObject.NULL)
    }
    return JSONObject()
        .put("id", id)
        .put("object", "chat.completion")
        .put("created", System.currentTimeMillis() / 1000)
        .put("model", model)
        .put(
            "choices",
            JSONArray().put(
                JSONObject().put("index", 0).put("message", message).put("finish_reason", finishReason)
            )
        )
}

/** One `chat.completion.chunk` SSE frame. [delta] carries whatever changed; [finishReason] is
 * non-null only on the final choice-bearing frame. */
internal fun chatChunkFrame(
    id: String,
    model: String,
    delta: JSONObject,
    finishReason: String?,
    usage: JSONObject? = null
): String {
    val choice = JSONObject().put("index", 0).put("delta", delta)
        .put("finish_reason", finishReason ?: JSONObject.NULL)
    val json = JSONObject()
        .put("id", id)
        .put("object", "chat.completion.chunk")
        .put("created", System.currentTimeMillis() / 1000)
        .put("model", model)
        .put("choices", JSONArray().put(choice))
    if (usage != null) json.put("usage", usage)
    return "data: $json\n\n"
}

/** The usage-carrying frame OpenAI sends when `stream_options.include_usage` is set: `choices` is
 * an empty array, not a choice with an empty delta, so a client that maps choices doesn't see a
 * phantom extra one. */
internal fun usageOnlyFrame(id: String, model: String, usage: JSONObject): String {
    val json = JSONObject()
        .put("id", id)
        .put("object", "chat.completion.chunk")
        .put("created", System.currentTimeMillis() / 1000)
        .put("model", model)
        .put("choices", JSONArray())
        .put("usage", usage)
    return "data: $json\n\n"
}

/** The opening frame of every stream. The spec's first delta announces the role and carries no
 * content; several clients build their message object from exactly this. */
internal fun roleChunkFrame(id: String, model: String): String =
    chatChunkFrame(id, model, JSONObject().put("role", "assistant").put("content", ""), null)

/** An error that happens *after* the response headers are already on the wire. Sent as a real
 * `error` frame rather than as assistant text — a failure must never be indistinguishable from
 * the model's answer, which is what writing "[error: …]" into `delta.content` did. */
internal fun errorFrame(message: String, type: String, code: String?): String =
    "data: ${openAiErrorJson(message, type, code, null)}\n\n"
