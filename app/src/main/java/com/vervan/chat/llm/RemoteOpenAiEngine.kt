package com.vervan.chat.llm

import com.vervan.chat.system.SafeLog as Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.tools.ToolCallParser
import com.vervan.chat.validation.InputLimits
import org.json.JSONArray
import org.json.JSONObject

class RemoteOpenAiApiException(message: String, val httpStatus: Int? = null) : IOException(message)

/** OpenAI-compatible function definition sent to a remote chat-completions endpoint. */
data class RemoteToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject
)

/** Optional protocol controls for a remote request. Nullable sampling fields are only emitted
 * when the user explicitly configured a per-model override; strict providers should not receive
 * vendor-only fields merely because the app has a global default. */
data class RemoteRequestOptions(
    val messages: List<Pair<String, String>>? = null,
    val tools: List<RemoteToolDefinition> = emptyList(),
    val toolChoice: String? = null,
    val thinkingMode: String = "OFF",
    val supportsThinking: Boolean = false,
    val thinkingParameter: String? = null,
    val seed: Int? = null,
    val minP: Float? = null,
    val repetitionPenalty: Float? = null
)

/**
 * Reduces a stream of (content, reasoning) SSE deltas into the app's one text convention —
 * reasoning wrapped in `<think>…</think>`, same as the two on-device engines emit natively — so
 * [RemoteOpenAiEngine.generate]'s caller never has to know reasoning arrived on a separate field.
 * Kept as its own tiny state machine, not inlined into the SSE loop, purely so it's unit-testable
 * without standing up a fake HTTP server.
 */
internal class ReasoningStreamMerger {
    // Whether we're currently inside an emitted <think> block — not "has one ever been opened".
    // The old version used a pair of one-shot flags (opened-once, closed-once) that could never
    // re-open: a provider that resumes `reasoning_content` after `content` has already started
    // (interleaved reasoning/content chunks, not one clean reasoning-then-answer split) would hit
    // `!reasoningOpen` as false forever after the first open, so the later reasoning text got
    // appended straight into the content stream with no wrapping tag at all — reasoning printed
    // outside the thinking block, right in the middle of the visible answer.
    private var insideThink = false

    /** Text to emit for one delta, in order. Usually 0-2 pieces; the tag itself only appears the
     *  moment reasoning starts or ends — including re-starting after content already resumed. */
    fun accept(content: String?, reasoning: String?): List<String> = buildList {
        if (!reasoning.isNullOrEmpty()) {
            if (!insideThink) { add("<think>\n"); insideThink = true }
            add(reasoning)
        }
        if (!content.isNullOrEmpty()) {
            if (insideThink) { add("\n</think>\n"); insideThink = false }
            add(content)
        }
    }

    /** Call once the stream ends. A response that finishes still inside an open reasoning block
     *  (finish_reason cut it off, or the connection just closed) would otherwise leave
     *  ThinkingParser treating everything after as "still thinking" forever. */
    fun finish(): List<String> =
        if (insideThink) { insideThink = false; listOf("\n</think>\n") } else emptyList()
}

private fun readAllUtf8(stream: java.io.InputStream): String =
    stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

private fun describeHttpError(status: Int, body: String, context: String): String {
    val message = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
    return message ?: "$context (HTTP $status)${if (body.isNotBlank()) ": ${body.take(300)}" else ""}"
}

/** Throws [RemoteOpenAiApiException] if [connection]'s response wasn't 2xx — shared by
 * [RemoteOpenAiEngine.generate] and [RemoteOpenAiEngine.fetchModels]. */
private fun HttpURLConnection.requireSuccessResponse(context: String) {
    val status = responseCode
    if (status !in 200..299) {
        val errorBody = (errorStream ?: inputStream)?.use { readAllUtf8(it) }.orEmpty()
        throw RemoteOpenAiApiException(describeHttpError(status, errorBody, context), status)
    }
}

/**
 * Calls an external OpenAI-compatible `/chat/completions` endpoint — the bring-your-own-API-key
 * counterpart to [LlmEngine]/[LlamaCppEngine]'s on-device inference. Stateless by design: unlike
 * the two native engines, there is no local weight file or hardware backend to load/unload, so
 * this class holds no per-model session — every call is fully parameterized (see [generate]) and
 * safe to invoke concurrently for different [com.vervan.chat.data.db.entities.ModelInfo] rows.
 * [ModelLoadCoordinator][com.vervan.chat.modelload.ModelLoadCoordinator] reflects this by never
 * routing a `REMOTE_API` model through a native load at all — see its short-circuit for that
 * engine.
 *
 * Talks plain [HttpURLConnection] over SSE, same convention as the rest of this app's networking
 * ([com.vervan.chat.modeldownload.HttpRangeDownloader], [com.vervan.chat.server.LocalApiServer])
 * rather than pulling in a new HTTP client dependency.
 */
class RemoteOpenAiEngine {

    /**
     * @param baseUrl e.g. `"https://api.openai.com/v1"` — `/chat/completions` is appended here,
     *   with any trailing slash on [baseUrl] tolerated.
     * @param prompt the fully-assembled turn text (already includes conversation history, same
     *   convention [AppContainer.generate][com.vervan.chat.VervanApp.AppContainer.generate]'s
     *   other two branches use) — sent as the single trailing `user` message.
     * @param systemPrompt sent as a leading `system`-role message when non-blank, same semantics
     *   as [LlmEngine.generate]'s own `systemPrompt` parameter.
     * @param imagePath a filesystem path (same convention as [LlmEngine.generate]/
     *   [LlamaCppEngine.generate]) read, base64-encoded, and sent as an `image_url` data-URI
     *   content part per the standard OpenAI vision message shape. Caller (`AppContainer.generate`)
     *   already gates this on `model.supportsVision == true` — the endpoint itself is trusted to
     *   400 if the selected model doesn't actually accept it.
     * @param audioPath sent as an `input_audio` content part (the `gpt-4o-audio-preview` shape);
     *   same trust boundary as [imagePath].
     */
    fun generate(
        baseUrl: String,
        apiKey: String,
        remoteModelId: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxOutputTokens: Int,
        stopSequences: List<String> = emptyList(),
        imagePath: String? = null,
        audioPath: String? = null,
        // Not part of the OpenAI spec itself (OpenAI's own API has no top_k), but a vendor
        // extension nearly every self-hosted OpenAI-compatible server accepts (vLLM, LM Studio,
        // Ollama, text-generation-webui) — sent only when the user explicitly set it, so a strict
        // implementation that 400s on an unrecognized field never sees it.
        topK: Int? = null,
        options: RemoteRequestOptions = RemoteRequestOptions()
    ): Flow<String> = flow {
        withTimeout(GENERATION_TIMEOUT_MS) {
        Log.i(TAG, "generate(): starting request to model=$remoteModelId")
        val url = URL(endpointUrl(baseUrl))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val body = requestBody(
                remoteModelId, prompt, systemPrompt, temperature, topP, maxOutputTokens, stopSequences,
                imagePath, audioPath, topK, options
            )
            withContext(Dispatchers.IO) {
                connection.outputStream.use { it.writeUtf8(body) }
                connection.requireSuccessResponse("Remote generation request failed")
            }
            val reader = withContext(Dispatchers.IO) {
                BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            }
            // Reasoning-capable OpenAI-compatible servers (vLLM, LM Studio, DeepSeek's own API,
            // most others that expose `enable_thinking`) stream reasoning as its own
            // `delta.reasoning_content` (or `delta.reasoning`) field, separate from `delta.content`
            // — unlike the two on-device engines, which emit thinking inline as literal <think>
            // tags in one text stream. ReasoningStreamMerger re-wraps it in that same convention so
            // every downstream consumer (ThinkingParser, the collapsible reasoning card, exports)
            // needs exactly one code path regardless of which engine produced it.
            val merger = ReasoningStreamMerger()
            val nativeToolCalls = linkedMapOf<Int, NativeToolCall>()
            var eventType: String? = null
            reader.use { r ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = withContext(Dispatchers.IO) { r.readLine() } ?: break
                    if (line.startsWith("event:")) {
                        eventType = line.removePrefix("event:").trim()
                        continue
                    }
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (eventType.equals("error", ignoreCase = true)) {
                        throw RemoteOpenAiApiException("Remote provider reported a streaming error")
                    }
                    eventType = null
                    val error = runCatching { JSONObject(data).optJSONObject("error") }.getOrNull()
                    if (error != null) {
                        val message = error.optString("message").ifBlank { "Remote provider returned an error" }
                        throw RemoteOpenAiApiException(message)
                    }
                    val delta = try {
                        deltaParts(data)
                    } catch (e: Exception) {
                        // Never put streamed model output or provider payloads into logs. The
                        // response can contain private prompts, attachments, or tool arguments.
                        Log.w(TAG, "generate(): could not parse an SSE chunk; skipping malformed event", e)
                        null
                    } ?: continue
                    merger.accept(delta.content, delta.reasoning).forEach { emit(it) }
                    delta.toolCalls.forEach { part ->
                        val call = nativeToolCalls.getOrPut(part.index) { NativeToolCall() }
                        part.name?.let { call.name = it }
                        if (!part.arguments.isNullOrEmpty()) call.arguments.append(part.arguments)
                    }
                }
            }
            merger.finish().forEach { emit(it) }
            nativeToolCalls.values.forEach { call ->
                val name = call.name?.takeIf { it.isNotBlank() } ?: return@forEach
                val params = runCatching { JSONObject(call.arguments.toString()) }.getOrNull()
                    ?: JSONObject()
                emit(
                    JSONObject()
                        .put("tool", name)
                        .put("params", params)
                        .let { "<tool_call>$it</tool_call>" }
                )
            }
            Log.i(TAG, "generate(): request to model=$remoteModelId completed")
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "generate(): request to model=$remoteModelId failed", t)
            throw t
        } finally {
            // Unblocks any read still parked in `r.readLine()` on cancellation — mirrors
            // HttpRangeDownloader's own connection.disconnect()-in-finally pattern.
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { connection.disconnect() }
        }
        }
    }

    /**
     * Calls an external OpenAI-compatible `/embeddings` endpoint — the [ModelEngine.REMOTE_API]
     * counterpart to [EmbeddingEngine][com.vervan.chat.retrieval.EmbeddingEngine]'s on-device
     * models, used the same way by [embedWith][com.vervan.chat.retrieval.embedWith] for RAG
     * retrieval, memory recall, and document indexing.
     */
    suspend fun embed(baseUrl: String, apiKey: String, remoteModelId: String, text: String): Result<FloatArray> =
        embedBatch(baseUrl, apiKey, remoteModelId, listOf(text)).map { it.single() }

    /**
     * Batched counterpart of [embed] — the `/embeddings` endpoint accepts `input` as either a
     * single string or an array, so a document with hundreds of chunks doesn't have to pay one
     * HTTP round trip per chunk (see [com.vervan.chat.model.DocumentImportManager.persistChunks]'s
     * batch size choice for why: this is what turns "one request per chunk" into "one request per
     * ~32 chunks", which matters both for import speed and for not hammering a rate-limited API).
     * `data` items carry their own `index`, sorted back into request order here rather than
     * trusted to arrive in order — the spec doesn't guarantee it, and OpenAI's own docs note
     * providers may return results out of order.
     */
    suspend fun embedBatch(baseUrl: String, apiKey: String, remoteModelId: String, texts: List<String>): Result<List<FloatArray>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (texts.isEmpty()) return@runCatching emptyList()
                val url = URL(normalizedBaseUrl(baseUrl) + "/embeddings")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }
                try {
                    val input = JSONArray().apply { texts.forEach { put(it) } }
                    val body = JSONObject().put("model", remoteModelId).put("input", input).toString()
                    connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                    connection.requireSuccessResponse("Embedding request failed")
                    val data = JSONObject(readAllUtf8(connection.inputStream)).getJSONArray("data")
                    val byIndex = arrayOfNulls<FloatArray>(texts.size)
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val embedding = item.getJSONArray("embedding")
                        val index = item.optInt("index", i)
                        if (index !in byIndex.indices) continue
                        byIndex[index] = FloatArray(embedding.length()) { embedding.getDouble(it).toFloat() }
                    }
                    byIndex.map { it ?: throw RemoteOpenAiApiException("Embedding response missing an entry") }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun endpointUrl(baseUrl: String): String = normalizedBaseUrl(baseUrl) + "/chat/completions"

    private fun normalizedBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        baseUrlError(trimmed)?.let { throw RemoteOpenAiApiException(it) }
        return java.net.URI(trimmed).toString().trimEnd('/')
    }

    private fun OutputStream.writeUtf8(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
        flush()
    }

    internal fun requestBody(
        remoteModelId: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxOutputTokens: Int,
        stopSequences: List<String>,
        imagePath: String? = null,
        audioPath: String? = null,
        topK: Int? = null,
        options: RemoteRequestOptions = RemoteRequestOptions()
    ): String {
        val messages = messagesJson(prompt, systemPrompt, imagePath, audioPath, options.messages)
        // OpenAI reasoning endpoints reject ordinary sampling controls. A null parameter means
        // the default `reasoning_effort` protocol, so omit both standard and vendor sampling
        // knobs in that mode; custom providers can opt into their own compatible field explicitly.
        val strictReasoning = options.supportsThinking &&
            (options.thinkingParameter == null || options.thinkingParameter.equals("reasoning_effort", ignoreCase = true))
        val json = JSONObject()
            .put("model", remoteModelId)
            .put("messages", messages)
            .put("stream", true)
            .put(
                if (strictReasoning) {
                    "max_completion_tokens"
                } else "max_tokens",
                maxOutputTokens
            )
        if (!strictReasoning) {
            json.put("temperature", temperature.toDouble())
                .put("top_p", topP.toDouble())
        }
        if (stopSequences.isNotEmpty()) {
            json.put("stop", JSONArray(stopSequences))
        }
        if (topK != null && !strictReasoning) json.put("top_k", topK)
        options.seed?.let { json.put("seed", it) }
        if (!strictReasoning) {
            options.minP?.let { json.put("min_p", it.toDouble()) }
            options.repetitionPenalty?.let { json.put("repetition_penalty", it.toDouble()) }
        }
        if (options.tools.isNotEmpty()) {
            json.put("tools", JSONArray().apply {
                options.tools.forEach { tool ->
                    put(
                        JSONObject()
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", tool.name)
                                    .put("description", tool.description)
                                    .put("parameters", tool.parameters)
                            )
                    )
                }
            })
            options.toolChoice?.let { json.put("tool_choice", it) }
        }
        if (options.supportsThinking) {
            val effort = when (options.thinkingMode) {
                "FAST" -> "low"
                "BALANCED" -> "medium"
                "DEEP" -> "high"
                else -> "none"
            }
            when (options.thinkingParameter?.trim()?.lowercase()) {
                "enable_thinking", "enable-thinking" -> json.put("enable_thinking", options.thinkingMode != "OFF")
                "thinking", "think" -> json.put("thinking", options.thinkingMode.lowercase())
                else -> json.put(options.thinkingParameter?.trim().takeUnless { it.isNullOrBlank() } ?: "reasoning_effort", effort)
            }
        }
        return json.toString()
    }

    /** Builds one canonical OpenAI message array. [fallbackMessages] is used by the normal chat
     * path and the local API server; when absent this preserves the legacy prompt/system shape.
     * Attachments are added to the final user turn so replayed history is never re-encoded. */
    private fun messagesJson(
        prompt: String,
        systemPrompt: String?,
        imagePath: String?,
        audioPath: String?,
        fallbackMessages: List<Pair<String, String>>?
    ): JSONArray {
        val source = fallbackMessages?.takeIf { it.isNotEmpty() }
            ?: buildList {
                if (!systemPrompt.isNullOrBlank()) add("system" to systemPrompt)
                add("user" to prompt)
            }
        val result = JSONArray()
        var lastToolCallId: String? = null
        var toolCallCounter = 0
        source.forEach { (rawRole, rawContent) ->
            val role = rawRole.lowercase().let { if (it == "developer") "developer" else it }
            val message = JSONObject().put("role", role)
            if (role == "assistant") {
                val parsed = ToolCallParser.parseAll(rawContent)
                val cleanContent = ToolCallParser.stripAll(rawContent, parsed.calls.map { it.rawBlock })
                if (cleanContent.isNotBlank()) message.put("content", cleanContent)
                else message.put("content", JSONObject.NULL)
                if (parsed.calls.isNotEmpty()) {
                    val calls = JSONArray()
                    parsed.calls.forEach { call ->
                        val id = "vervan-call-${toolCallCounter++}"
                        lastToolCallId = id
                        calls.put(
                            JSONObject()
                                .put("id", id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", call.name).put("arguments", call.params.toString())
                                )
                        )
                    }
                    message.put("tool_calls", calls)
                }
            } else if (role == "tool") {
                message.put("content", rawContent)
                message.put("tool_call_id", lastToolCallId ?: "vervan-call-0")
            } else {
                message.put("content", rawContent)
            }
            result.put(message)
        }

        val attachmentIndex = (0 until result.length()).reversed().firstOrNull { index ->
            result.optJSONObject(index)?.optString("role") == "user"
        }
        if (imagePath != null || audioPath != null) {
            if (attachmentIndex == null) {
                result.put(JSONObject().put("role", "user").put("content", attachmentContent(prompt, imagePath, audioPath)))
            } else {
                val message = result.getJSONObject(attachmentIndex)
                val text = message.optString("content", "")
                message.put("content", attachmentContent(text, imagePath, audioPath))
            }
        }
        return result
    }

    private fun attachmentContent(text: String, imagePath: String?, audioPath: String?): JSONArray = JSONArray().apply {
        put(JSONObject().put("type", "text").put("text", text))
        imagePath?.let { path ->
            encodeFileAsDataUri(path, defaultMime = "image/jpeg")?.let { dataUri ->
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUri)))
            }
        }
        audioPath?.let { path ->
            encodeFileAsBase64(path)?.let { (base64, format) ->
                put(JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", base64).put("format", format)))
            }
        }
    }

    /** Reads [path] off disk and returns a `data:<mime>;base64,...` URI for an `image_url` content
     *  part, or null if the file can't be read (attachment silently dropped rather than failing
     *  the whole turn — same tolerance [deltaContent] uses for a malformed SSE chunk). */
    private fun encodeFileAsDataUri(path: String, defaultMime: String): String? {
        val file = java.io.File(path)
        require(file.isFile) { "Attachment file could not be read: ${file.name}" }
        val bytes = file.inputStream().use { it.readBytesLimited(InputLimits.MAX_NORMALIZED_IMAGE_BYTES) }
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: defaultMime
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$base64"
    }

    /** Reads [path] off disk for an `input_audio` content part, returning (base64, format) where
     *  format is the bare extension (`"wav"`, `"mp3"`) the OpenAI audio content shape expects —
     *  or null if the file can't be read. */
    private fun encodeFileAsBase64(path: String): Pair<String, String>? {
        val file = java.io.File(path)
        require(file.isFile) { "Audio attachment could not be read: ${file.name}" }
        val bytes = file.inputStream().use { it.readBytesLimited(InputLimits.MAX_DECODED_AUDIO_BYTES) }
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        return base64 to file.extension.lowercase().ifBlank { "wav" }
    }

    private data class ToolCallDelta(val index: Int, val name: String?, val arguments: String?)
    private class NativeToolCall {
        var name: String? = null
        val arguments = StringBuilder()
    }

    /** Extracts content, reasoning, and native function-call deltas from one
     * `choices[0].delta` OpenAI-compatible SSE chunk. */
    private data class DeltaParts(
        val content: String?,
        val reasoning: String?,
        val toolCalls: List<ToolCallDelta> = emptyList()
    )

    /** [DeltaParts.reasoning] checks `reasoning_content` (the vLLM/DeepSeek/LM Studio field name)
     *  then `reasoning` (OpenRouter's) — whichever the server actually sends, or neither. */
    private fun deltaParts(data: String): DeltaParts? {
        val choices = JSONObject(data).optJSONArray("choices") ?: return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        val reasoning = delta.optString("reasoning_content", "").takeIf { it.isNotEmpty() }
            ?: delta.optString("reasoning", "").takeIf { it.isNotEmpty() }
        val toolCalls = mutableListOf<ToolCallDelta>()
        delta.optJSONArray("tool_calls")?.let { calls ->
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                val function = call.optJSONObject("function") ?: continue
                toolCalls += ToolCallDelta(
                    index = call.optInt("index", index),
                    name = function.optString("name", "").takeIf { it.isNotBlank() },
                    arguments = function.optString("arguments", "").takeIf { it.isNotEmpty() }
                )
            }
        }
        // Older OpenAI-compatible servers use the pre-tool_calls function_call field.
        delta.optJSONObject("function_call")?.let { function ->
            toolCalls += ToolCallDelta(
                index = 0,
                name = function.optString("name", "").takeIf { it.isNotBlank() },
                arguments = function.optString("arguments", "").takeIf { it.isNotEmpty() }
            )
        }
        return DeltaParts(
            delta.optString("content", "").takeIf { it.isNotEmpty() },
            reasoning,
            toolCalls
        )
    }

    companion object {
        private const val TAG = "RemoteOpenAiEngine"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val GENERATION_TIMEOUT_MS = 15 * 60 * 1000L

        /** Human-readable reason [baseUrl] is unusable, or null when it's fine. */
        fun baseUrlError(baseUrl: String): String? {
            val trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return "Enter the API base URL."
            val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
                ?: return "That isn't a valid URL."
            return when {
                uri.scheme == null -> "Include the scheme, e.g. https://api.openai.com/v1"
                !uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.scheme.equals("http", ignoreCase = true) ->
                    "The URL must start with https:// or http://"
                uri.host.isNullOrBlank() -> "That URL is missing a host name."
                uri.userInfo != null -> "Remove the username or password from the URL; enter it only in the API key field."
                uri.rawQuery != null -> "Remove the query string from the base URL."
                uri.rawFragment != null -> "Remove the fragment from the base URL."
                uri.port !in -1..65535 -> "That URL has an invalid port."
                uri.path.split('/').any { it == ".." } -> "The base URL cannot contain parent-directory segments."
                uri.scheme.equals("http", ignoreCase = true) && !isCleartextHostAllowed(uri.host) ->
                    "Use https:// for network endpoints. Plain HTTP is allowed only for loopback or emulator-host services."
                else -> null
            }
        }

        private fun isCleartextHostAllowed(host: String?): Boolean {
            val normalized = host?.trim()?.trim('[', ']')?.lowercase() ?: return false
            return normalized == "localhost" ||
                normalized == "127.0.0.1" ||
                normalized == "10.0.2.2" ||
                normalized == "10.0.3.2"
        }
        // Per-read timeout, not a whole-request budget. Self-hosted models may spend more than a
        // minute loading weights or between long reasoning bursts; a five-minute stall bound still
        // fails dead connections while avoiding false failures during legitimate cold starts.
        private const val READ_TIMEOUT_MS = 300_000

        /**
         * The provider's own model catalog, from its `/models` endpoint — so the user can pick from
         * a real list instead of hand-typing an id that only fails on first send. This doubles as
         * the reachability/auth check the add-model dialog used to spend a separate "Test
         * connection" button on: it's the cheapest universally-supported endpoint, and a wrong
         * URL or bad key fails here rather than on the user's first chat message.
         *
         * Ids only: the endpoint reports no capability information worth trusting
         * (`/models` returns little beyond ids and ownership, and providers disagree on the rest),
         * which is exactly why capabilities are chosen per model in the UI instead of guessed here.
         *
         * Tolerates both the spec's `{"data":[{"id":…}]}` envelope and the bare `[{"id":…}]` or
         * `["id"]` shapes self-hosted servers sometimes return. Sorted, de-duplicated.
         */
        suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    val trimmed = baseUrl.trim()
                    baseUrlError(trimmed)?.let { throw RemoteOpenAiApiException(it) }
                    val url = URL(java.net.URI(trimmed).toString().trimEnd('/') + "/models")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = CONNECT_TIMEOUT_MS
                        if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    val body = try {
                        connection.requireSuccessResponse("Could not list models")
                        readAllUtf8(connection.inputStream)
                    } finally {
                        connection.disconnect()
                    }
                    val array = runCatching { org.json.JSONObject(body).optJSONArray("data") }.getOrNull()
                        ?: runCatching { org.json.JSONArray(body) }.getOrNull()
                        ?: throw RemoteOpenAiApiException("The endpoint's /models response wasn't in a recognized format")
                    val ids = (0 until array.length()).mapNotNull { i ->
                        array.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                            ?: array.optString(i).takeIf { it.isNotBlank() }
                    }
                    if (ids.isEmpty()) throw RemoteOpenAiApiException("The endpoint reported no available models")
                    ids.distinct().sorted()
                }
            }
        }

        private const val DISCOVERY_TIMEOUT_MS = 30_000L
    }
}
