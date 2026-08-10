package com.vervan.chat.llm

import android.util.Log
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
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.validation.InputLimits
import org.json.JSONArray
import org.json.JSONObject

class RemoteOpenAiApiException(message: String, val httpStatus: Int? = null) : IOException(message)

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
        topK: Int? = null
    ): Flow<String> = flow {
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
                imagePath, audioPath, topK
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
            reader.use { r ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = withContext(Dispatchers.IO) { r.readLine() } ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = try {
                        deltaParts(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "generate(): could not parse SSE chunk, skipping: $data", e)
                        null
                    } ?: continue
                    merger.accept(delta.content, delta.reasoning).forEach { emit(it) }
                }
            }
            merger.finish().forEach { emit(it) }
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
                val url = URL(baseUrl.trimEnd('/') + "/embeddings")
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

    private fun endpointUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/chat/completions"

    private fun OutputStream.writeUtf8(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
        flush()
    }

    private fun requestBody(
        remoteModelId: String,
        prompt: String,
        systemPrompt: String?,
        temperature: Float,
        topP: Float,
        maxOutputTokens: Int,
        stopSequences: List<String>,
        imagePath: String? = null,
        audioPath: String? = null,
        topK: Int? = null
    ): String {
        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        // Plain string content when there's nothing to attach — every OpenAI-compatible server
        // accepts that shape, including ones that would choke on a single-element content array.
        // The multipart `content: [...]` form only appears once there's an attachment to carry.
        val userContent: Any = if (imagePath == null && audioPath == null) {
            prompt
        } else {
            JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                imagePath?.let { path ->
                    encodeFileAsDataUri(path, defaultMime = "image/jpeg")?.let { dataUri ->
                        put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUri)))
                    }
                }
                audioPath?.let { path ->
                    encodeFileAsBase64(path)?.let { (base64, format) ->
                        put(
                            JSONObject().put("type", "input_audio")
                                .put("input_audio", JSONObject().put("data", base64).put("format", format))
                        )
                    }
                }
            }
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        val json = JSONObject()
            .put("model", remoteModelId)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", temperature.toDouble())
            .put("top_p", topP.toDouble())
            .put("max_tokens", maxOutputTokens)
        if (stopSequences.isNotEmpty()) {
            json.put("stop", JSONArray(stopSequences))
        }
        if (topK != null) json.put("top_k", topK)
        return json.toString()
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
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return "data:$mime;base64,$base64"
    }

    /** Reads [path] off disk for an `input_audio` content part, returning (base64, format) where
     *  format is the bare extension (`"wav"`, `"mp3"`) the OpenAI audio content shape expects —
     *  or null if the file can't be read. */
    private fun encodeFileAsBase64(path: String): Pair<String, String>? {
        val file = java.io.File(path)
        require(file.isFile) { "Audio attachment could not be read: ${file.name}" }
        val bytes = file.inputStream().use { it.readBytesLimited(InputLimits.MAX_DECODED_AUDIO_BYTES) }
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return base64 to file.extension.lowercase().ifBlank { "wav" }
    }

    /** Extracts `choices[0].delta.content` from one `data: {...}` SSE chunk — the OpenAI
     * streaming chat-completions shape every OpenAI-compatible provider mirrors. */
    private data class DeltaParts(val content: String?, val reasoning: String?)

    /** [DeltaParts.reasoning] checks `reasoning_content` (the vLLM/DeepSeek/LM Studio field name)
     *  then `reasoning` (OpenRouter's) — whichever the server actually sends, or neither. */
    private fun deltaParts(data: String): DeltaParts? {
        val choices = JSONObject(data).optJSONArray("choices") ?: return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        val reasoning = delta.optString("reasoning_content", "").takeIf { it.isNotEmpty() }
            ?: delta.optString("reasoning", "").takeIf { it.isNotEmpty() }
        return DeltaParts(delta.optString("content", "").takeIf { it.isNotEmpty() }, reasoning)
    }

    companion object {
        private const val TAG = "RemoteOpenAiEngine"
        private const val CONNECT_TIMEOUT_MS = 15_000

        /**
         * Human-readable reason [baseUrl] is unusable, or null when it's fine.
         *
         * Both `http` and `https` are accepted. HTTPS was originally required so a bearer key
         * couldn't travel in the clear, but that also ruled out the most common self-hosted case —
         * llama.cpp/Ollama/vLLM on a machine on the user's own LAN, which serves plain HTTP and has
         * no certificate to present. Cleartext is allowed at the manifest level for that reason (see
         * `usesCleartextTraffic`), and the add-model dialog warns whenever the URL is `http://`
         * rather than silently accepting it.
         */
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
                else -> null
            }
        }
        // Generous per-read timeout, not a whole-request budget — a real streaming response can
        // legitimately take minutes as long as tokens keep arriving; this only bounds how long a
        // single stalled read (dead connection, provider hang) can block before failing loudly.
        private const val READ_TIMEOUT_MS = 60_000

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
                val url = URL(baseUrl.trimEnd('/') + "/models")
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
}
