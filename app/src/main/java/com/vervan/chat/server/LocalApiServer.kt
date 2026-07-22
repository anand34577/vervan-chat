package com.vervan.chat.server

import android.util.Log
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.system.toUserMessage
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase J — local OpenAI-compatible API server. Implements just enough of the `/v1/chat/
 * completions` and `/v1/models` surface for an existing OpenAI-client config (base URL + API
 * key) to point at this app, reusing [com.vervan.chat.llm.LlmEngine] exactly as every other
 * caller does (through [VervanApp.container]'s `withLlm` mutex — one generation in flight
 * against the shared engine at a time, same as chat).
 *
 * `serve()` runs synchronously on one of NanoHTTPD's own worker threads per connection — using
 * `runBlocking` to call into the app's suspend functions from there is intentional, not an
 * oversight: that's exactly the "one blocking thread per request" shape this kind of embedded
 * server is meant to have.
 */
class LocalApiServer(
    hostname: String?,
    port: Int,
    private val app: VervanApp,
    private val auth: ApiServerAuth,
    private val requireAuth: Boolean,
    /** Owned by the caller (ApiServerService) — cancelled there on stop, so a streaming
     * response's background generation never outlives the server itself. */
    private val streamingScope: CoroutineScope
) : NanoHTTPD(hostname, port) {

    companion object {
        private const val TAG = "LocalApiServer"
        // PipedInputStream's default 1 KB buffer fills in a single chunk under bursty token
        // production; 64 KB smooths that out without allocating a large heap.
        private const val SSE_PIPE_SIZE = 64 * 1024
        // If the HTTP client disconnects mid-stream, NanoHTTPD stops draining the pipe and the
        // producer coroutine's pipedOut.write() blocks forever — the original try/finally never
        // ran because nothing cancelled the coroutine from inside the blocking call. Each write
        // is wrapped in withTimeout(runInterruptible{ write }) so a stalled consumer breaks the
        // underlying wait() via Thread.interrupt() and the coroutine tears down cleanly instead
        // of leaking for the life of the server.
        private const val SSE_WRITE_TIMEOUT_MS = 30_000L
        // NanoHTTPD buffers the whole request body in memory before serve() runs, and this app
        // has no reverse proxy in front to cap it — a LAN client sending a huge Content-Length
        // could OOM the process. serve()'s catch(Throwable) already survives an OOM, but rejecting
        // an oversized body up front with a clean 413 is cheaper than letting it allocate first.
        // 8 MB is far above any legitimate chat-completions payload (messages are text).
        private const val MAX_BODY_BYTES = 8L * 1024 * 1024
    }

    override fun serve(session: IHTTPSession): Response {
        app.container.networkAuditLog.record("Local API request: ${session.method} ${session.uri}")

        if (requireAuth) {
            val header = session.headers["authorization"] ?: session.headers["Authorization"]
            val token = header?.removePrefix("Bearer ")?.trim().orEmpty()
            if (!auth.verify(token)) return errorResponse(Response.Status.UNAUTHORIZED, "Missing or invalid API key")
        }

        return try {
            when {
                session.method == Method.GET && session.uri == "/v1/models" -> handleModels()
                session.method == Method.POST && session.uri == "/v1/chat/completions" -> handleChatCompletions(session)
                else -> errorResponse(Response.Status.NOT_FOUND, "Unknown endpoint")
            }
        } catch (t: Throwable) {
            // Throwable, not just Exception — NanoHTTPD buffers request bodies with no size cap
            // enforced here, so an oversized Content-Length can OutOfMemoryError; that must not
            // crash the app just because a LAN client sent a bad request. The raw exception
            // message also used to go straight into the client-visible JSON error body.
            Log.e(TAG, "serve() failed for ${session.method} ${session.uri}", t)
            errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage())
        }
    }

    private fun handleModels(): Response {
        val models = runBlocking { app.container.db.modelDao().observeModels().first() }
            .filter { it.role == ModelRole.GENERATION }
        val data = JSONArray()
        models.forEach { m ->
            data.put(JSONObject().put("id", m.displayName).put("object", "model").put("owned_by", "local"))
        }
        return jsonResponse(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
    }

    private fun handleChatCompletions(session: IHTTPSession): Response {
        // Reject an oversized body before parseBody() buffers it all into memory (see MAX_BODY_BYTES).
        val declaredLength = (session.headers["content-length"] ?: session.headers["Content-Length"])?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Request body too large (max ${MAX_BODY_BYTES / (1024 * 1024)} MB)")
        }
        val body = HashMap<String, String>()
        session.parseBody(body)
        val json = JSONObject(body["postData"] ?: "{}")
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val stream = json.optBoolean("stream", false)
        val requestedModel = json.optString("model").ifBlank { null }
        val (systemPrompt, prompt) = buildPrompt(messages)

        val model = runBlocking {
            requestedModel?.let { name ->
                app.container.db.modelDao().observeModels().first().firstOrNull { it.displayName == name && it.role == ModelRole.GENERATION }
            } ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
        } ?: return errorResponse(Response.Status.BAD_REQUEST, "No generation model available")

        val completionId = "chatcmpl-${System.currentTimeMillis()}"
        return if (stream) {
            val pipedIn = PipedInputStream(SSE_PIPE_SIZE)
            val pipedOut = PipedOutputStream(pipedIn)
            streamingScope.launch {
                try {
                    val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, com.vervan.chat.modelload.LoadTrigger.CHAT_SEND)
                    check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
                    val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
                    app.container.generate(
                        model, prompt, null, null, params.temperature, params.topP, params.topK, params.seed,
                        params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences,
                        systemPrompt = systemPrompt
                    ).collect { chunk ->
                        val bytes = sseChunk(completionId, model.displayName, chunk).toByteArray()
                        // Abort the stream if the consumer has stopped draining — see SSE_WRITE_TIMEOUT_MS.
                        try {
                            withTimeout(SSE_WRITE_TIMEOUT_MS) {
                                runInterruptible { pipedOut.write(bytes); pipedOut.flush() }
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.w(TAG, "SSE client stopped reading; aborting stream", e)
                            throw kotlinx.coroutines.CancellationException("SSE consumer gone")
                        }
                    }
                    try {
                        withTimeout(SSE_WRITE_TIMEOUT_MS) {
                            runInterruptible { pipedOut.write("data: [DONE]\n\n".toByteArray()) }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "SSE client stopped reading before [DONE]; aborting", e)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "streaming chat completion failed", t)
                    runCatching { pipedOut.write(sseChunk(completionId, model.displayName, "[error: ${t.toUserMessage()}]").toByteArray()) }
                } finally {
                    runCatching { pipedOut.close() }
                }
            }
            newChunkedResponse(Response.Status.OK, "text/event-stream", pipedIn)
        } else {
            val text = runBlocking {
                val sb = StringBuilder()
                val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, com.vervan.chat.modelload.LoadTrigger.CHAT_SEND)
                check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
                val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
                app.container.generate(
                    model, prompt, null, null, params.temperature, params.topP, params.topK, params.seed,
                    params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences,
                    systemPrompt = systemPrompt
                ).collect { chunk -> sb.append(chunk) }
                sb.toString()
            }
            jsonResponse(Response.Status.OK, chatCompletionJson(completionId, model.displayName, text))
        }
    }

    /** No chat template *content* assembly beyond role separation — a much simpler prompt than
     * [com.vervan.chat.ui.chat.ChatViewModel]'s (no persona/memory/retrieval/tools), by design: an
     * OpenAI-API client is expected to send the full context it wants in `messages` itself. Still
     * returns `system`-role messages separately (first) from the rest (second, flattened + a
     * trailing "Assistant:") so the llama.cpp bridge can send them as a real `"system"` template
     * turn instead of folding them into the `"user"` turn — a client sending a `system` message
     * is explicitly asking for system-role behavior; silently downgrading it to user-role text
     * would defeat the point of an OpenAI-compatible endpoint. */
    private fun buildPrompt(messages: JSONArray): Pair<String, String> {
        val system = StringBuilder()
        val user = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "user")
            val content = m.optString("content")
            if (role == "system") { system.appendLine(content); system.appendLine() }
            else user.appendLine("${role.replaceFirstChar(Char::uppercase)}: $content")
        }
        user.append("Assistant:")
        return system.toString() to user.toString()
    }

    private fun sseChunk(id: String, model: String, delta: String): String {
        val json = JSONObject()
            .put("id", id).put("object", "chat.completion.chunk").put("created", System.currentTimeMillis() / 1000)
            .put("model", model)
            .put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject().put("content", delta))))
        return "data: $json\n\n"
    }

    private fun chatCompletionJson(id: String, model: String, content: String): JSONObject =
        JSONObject()
            .put("id", id).put("object", "chat.completion").put("created", System.currentTimeMillis() / 1000)
            .put("model", model)
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put("index", 0)
                        .put("message", JSONObject().put("role", "assistant").put("content", content))
                        .put("finish_reason", "stop")
                )
            )

    private fun jsonResponse(status: Response.Status, json: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", json.toString())

    private fun errorResponse(status: Response.Status, message: String): Response =
        jsonResponse(status, JSONObject().put("error", JSONObject().put("message", message).put("type", "invalid_request_error")))
}
