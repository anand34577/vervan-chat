package com.vervan.chat.server

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        } catch (e: Exception) {
            errorResponse(Response.Status.INTERNAL_ERROR, e.message ?: "Internal error")
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
        val body = HashMap<String, String>()
        session.parseBody(body)
        val json = JSONObject(body["postData"] ?: "{}")
        val messages = json.optJSONArray("messages") ?: JSONArray()
        val stream = json.optBoolean("stream", false)
        val requestedModel = json.optString("model").ifBlank { null }
        val prompt = buildPrompt(messages)

        val model = runBlocking {
            requestedModel?.let { name ->
                app.container.db.modelDao().observeModels().first().firstOrNull { it.displayName == name && it.role == ModelRole.GENERATION }
            } ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
        } ?: return errorResponse(Response.Status.BAD_REQUEST, "No generation model available")

        val completionId = "chatcmpl-${System.currentTimeMillis()}"
        return if (stream) {
            val pipedIn = PipedInputStream()
            val pipedOut = PipedOutputStream(pipedIn)
            streamingScope.launch {
                try {
                    app.container.withLlm { engine ->
                        if (engine.loadedModelPath != model.filePath) engine.load(model.filePath)
                        engine.generate(prompt).collect { chunk ->
                            pipedOut.write(sseChunk(completionId, model.displayName, chunk).toByteArray())
                            pipedOut.flush()
                        }
                    }
                    pipedOut.write("data: [DONE]\n\n".toByteArray())
                } catch (e: Exception) {
                    runCatching { pipedOut.write(sseChunk(completionId, model.displayName, "[error: ${e.message}]").toByteArray()) }
                } finally {
                    runCatching { pipedOut.close() }
                }
            }
            newChunkedResponse(Response.Status.OK, "text/event-stream", pipedIn)
        } else {
            val text = runBlocking {
                val sb = StringBuilder()
                app.container.withLlm { engine ->
                    if (engine.loadedModelPath != model.filePath) engine.load(model.filePath)
                    engine.generate(prompt).collect { chunk -> sb.append(chunk) }
                }
                sb.toString()
            }
            jsonResponse(Response.Status.OK, chatCompletionJson(completionId, model.displayName, text))
        }
    }

    /** No chat template applied — this is a much simpler prompt than
     * [com.vervan.chat.ui.chat.ChatViewModel]'s (no persona/memory/retrieval/tools), by design:
     * an OpenAI-API client is expected to send the full context it wants in `messages` itself. */
    private fun buildPrompt(messages: JSONArray): String = buildString {
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "user")
            val content = m.optString("content")
            if (role == "system") { appendLine(content); appendLine() }
            else appendLine("${role.replaceFirstChar(Char::uppercase)}: $content")
        }
        append("Assistant:")
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
