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
import org.json.JSONArray
import org.json.JSONObject

class RemoteOpenAiApiException(message: String, val httpStatus: Int? = null) : IOException(message)

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
 * [RemoteOpenAiEngine.generate] and [RemoteOpenAiEngine.testConnection]. */
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
        stopSequences: List<String> = emptyList()
    ): Flow<String> = flow {
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
            val body = requestBody(remoteModelId, prompt, systemPrompt, temperature, topP, maxOutputTokens, stopSequences)
            withContext(Dispatchers.IO) {
                connection.outputStream.use { it.writeUtf8(body) }
                connection.requireSuccessResponse("Remote generation request failed")
            }
            val reader = withContext(Dispatchers.IO) {
                BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            }
            reader.use { r ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = withContext(Dispatchers.IO) { r.readLine() } ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = try {
                        deltaContent(data)
                    } catch (e: Exception) {
                        Log.w(TAG, "generate(): could not parse SSE chunk, skipping: $data", e)
                        null
                    }
                    if (!delta.isNullOrEmpty()) emit(delta)
                }
            }
        } finally {
            // Unblocks any read still parked in `r.readLine()` on cancellation — mirrors
            // HttpRangeDownloader's own connection.disconnect()-in-finally pattern.
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { connection.disconnect() }
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
        stopSequences: List<String>
    ): String {
        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
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
        return json.toString()
    }

    /** Extracts `choices[0].delta.content` from one `data: {...}` SSE chunk — the OpenAI
     * streaming chat-completions shape every OpenAI-compatible provider mirrors. */
    private fun deltaContent(data: String): String? {
        val choices = JSONObject(data).optJSONArray("choices") ?: return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        return delta.optString("content", "").takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "RemoteOpenAiEngine"
        private const val CONNECT_TIMEOUT_MS = 15_000

        /**
         * Human-readable reason [baseUrl] is unusable, or null when it's fine.
         *
         * HTTPS is required, not preferred. targetSdk 35 with no `usesCleartextTraffic` override
         * means the platform already refuses a cleartext connection, so an `http://` endpoint
         * could never have leaked the bearer key — but it failed as an opaque
         * `IOException`/`UnknownServiceException` at request time rather than telling the user the
         * one thing they needed to change. Checking here also keeps the requirement true if a
         * cleartext exemption is ever added to the manifest for some unrelated reason.
         */
        fun baseUrlError(baseUrl: String): String? {
            val trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return "Enter the API base URL."
            val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
                ?: return "That isn't a valid URL."
            return when {
                uri.scheme == null -> "Include the scheme, e.g. https://api.openai.com/v1"
                !uri.scheme.equals("https", ignoreCase = true) ->
                    "Only https:// endpoints are supported — an API key must never travel unencrypted."
                uri.host.isNullOrBlank() -> "That URL is missing a host name."
                else -> null
            }
        }
        // Generous per-read timeout, not a whole-request budget — a real streaming response can
        // legitimately take minutes as long as tokens keep arriving; this only bounds how long a
        // single stalled read (dead connection, provider hang) can block before failing loudly.
        private const val READ_TIMEOUT_MS = 60_000

        /** Best-effort reachability/auth check for the "Add remote model" UI — a real
         * `/chat/completions` call would work even with a wrong model id (some providers 400 only
         * once billing/model resolution runs deeper), so this hits the cheaper, more universally
         * supported `/models` list endpoint instead and treats any 2xx as success. */
        suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(baseUrl.trimEnd('/') + "/models")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = CONNECT_TIMEOUT_MS
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                }
                try {
                    connection.requireSuccessResponse("Connection test failed")
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
