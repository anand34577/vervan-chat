package com.vervan.chat.server

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.AudioNormalizer
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.canSupportAudio
import com.vervan.chat.data.db.entities.canSupportVision
import com.vervan.chat.model.DocumentImportOutcome
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.modelload.LoadTrigger
import com.vervan.chat.retrieval.RetrievalMode
import com.vervan.chat.llm.ThinkingPolicy
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.tools.ToolRegistry
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.ui.chat.ChatFormatting
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * local OpenAI-compatible API server. Implements just enough of the `/v1/chat/
 * completions`, `/v1/embeddings`, and `/v1/models` surface for an existing OpenAI-client config
 * (base URL + API key) to point at this app, reusing [com.vervan.chat.llm.LlmEngine]/
 * [com.vervan.chat.retrieval.EmbeddingEngine] exactly as every other caller does (through
 * [VervanApp.container]'s `withLlm`/`withEmbedding` mutex — one generation/embed in flight
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
    /** false ("Basic API") serves only the bare OpenAI-compatible endpoints plus a minimal
     * status page at `/` — no chat UI, no `/api` app-data surface at all (those routes 404).
     * true ("Full web app") additionally serves the rich chat/RAG/document/vision/audio web UI
     * and the `/api` endpoints it depends on (knowledge bases, document upload, etc.) — see
     * the `fullMode` guards throughout [serve]. This is a meaningfully bigger trust boundary
     * than the OpenAI-compatible surface (it can read/write app data, not just run inference),
     * so it's opt-in and separate from [requireAuth] rather than implied by it. */
    private val fullMode: Boolean,
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
        private const val MAX_MESSAGES = 256
        private const val MAX_MESSAGE_CHARS = 4L * 1024 * 1024
        private const val MAX_EMBEDDING_INPUTS = 64
        private const val MAX_EMBEDDING_CHARS = 1L * 1024 * 1024
        // Chat/completions gets a larger cap than the plain-text default (MAX_BODY_BYTES) — a
        // request carrying an inline base64 image or a few seconds of audio (see the vision/
        // audio content-part handling below) legitimately runs a few MB past a text-only chat.
        private const val MAX_CHAT_BODY_BYTES = 24L * 1024 * 1024
        private const val MAX_IMAGE_DATA_URL_CHARS = 20L * 1024 * 1024
        private const val MAX_AUDIO_BASE64_CHARS = 50L * 1024 * 1024
        // Document upload is base64-in-JSON, not a streamed multipart file — deliberately more
        // conservative than DocumentImportManager's own 256 MB source-file ceiling (that path
        // streams from a Uri; this one buffers the whole base64 body in memory via NanoHTTPD's
        // parseBody first, same reasoning MAX_BODY_BYTES documents for the request-body cap in
        // general).
        private const val MAX_DOCUMENT_UPLOAD_BYTES = 64L * 1024 * 1024
        private const val RAG_TOP_K = 5
        // Hard ceiling on one request's model-load + generation time. Without this, a wedged
        // native call (stuck GPU driver, a load that never returns) blocks the calling thread —
        // a NanoHTTPD worker thread for the non-streaming path, or a streamingScope coroutine
        // for the streaming path — forever. 5 minutes is far above any real chat-completions
        // turnaround but still bounds the failure instead of leaving it unbounded.
        private const val GENERATION_TIMEOUT_MS = 5 * 60 * 1000L
        // How many times the model may call a tool and be re-prompted with the result within one
        // chat-completions request. Matches the native chat's own hop budget: enough for a
        // list_tools → tool_details → call → answer discovery chain, bounded so a model that keeps
        // asking for tools can't spin a request indefinitely.
        private const val MAX_TOOL_HOPS = 6
        // Generation is serialized behind one engine mutex anyway, so extra concurrent requests
        // only queue — but each one still parks a NanoHTTPD worker thread (or a streaming
        // coroutine) for up to GENERATION_TIMEOUT_MS while it waits. Past a small backlog the
        // honest answer is 429 with Retry-After: a client that gets told to retry behaves far
        // better than one left hanging for five minutes behind a queue it can't see.
        private const val MAX_QUEUED_GENERATIONS = 8
        // Speech-to-text uploads are real audio files (multipart or base64), not chat text — the
        // same order of magnitude as an inline audio attachment.
        private const val MAX_AUDIO_UPLOAD_BYTES = 50L * 1024 * 1024
        private const val MAX_TTS_INPUT_CHARS = 8_000
        /** How often a streaming response carries a live tok/s + RAM frame. Fast enough to read as
         * live, slow enough that it stays a rounding error next to the token deltas themselves. */
        private const val LIVE_STATS_INTERVAL_MS = 700L
        private val ROLE_LIST = listOf(ModelRole.GENERATION, ModelRole.EMBEDDING)

        /** Request name → (asset path, MIME type) for everything the web app's own markup pulls in.
         * The Mermaid bundle is the same one the native app's in-chat diagram WebView uses; the
         * rest is the web app itself, split out of the page so the browser can cache it. */
        private val WEB_UI_ASSETS = mapOf(
            "mermaid.min.js" to ("mermaid/mermaid.min.js" to "text/javascript; charset=utf-8"),
            "app.js" to ("webui/app.js" to "text/javascript; charset=utf-8"),
            "render.js" to ("webui/render.js" to "text/javascript; charset=utf-8"),
            "app.css" to ("webui/app.css" to "text/css; charset=utf-8")
        )
    }

    /** Thinking + tool loop, shared with nothing else today but deliberately kept out of this
     * class: it's request-shaped policy, not HTTP plumbing. */
    private val runner = ApiChatRunner(app)

    /** See [MAX_QUEUED_GENERATIONS]. Permits are acquired before a generation starts and released
     * when it finishes — including on the streaming path, where the release happens inside the
     * producing coroutine rather than on the thread that acquired it. */
    private val generationSlots = java.util.concurrent.Semaphore(MAX_QUEUED_GENERATIONS)

    /** The rest of the app's data surface — notes, memories, personas, templates, projects,
     * workspaces, folders, tool runs, model residency. Kept in its own class because none of it is
     * inference: this file stays the OpenAI-compatible server plus the chat/document endpoints that
     * genuinely share its generation and attachment plumbing. */
    private val webAppApi = WebAppApi(app)

    /** NanoHTTPD never sets `TCP_NODELAY` on an accepted connection — Nagle's algorithm stays on
     * by default, which coalesces/delays the small writes a per-token SSE chunk is. Combined with
     * delayed ACKs, that's enough to make a genuinely-streaming response look fully buffered until
     * the connection goes idle: the exact "waits until the LLM finishes" symptom reported for both
     * the browser UI and a real OpenAI client, neither of which does anything special with the
     * socket themselves. `createClientHandler` is the one hook NanoHTTPD exposes before any
     * response is ever written on the connection, so the flag is set here rather than per-response. */
    override fun createClientHandler(finalAccept: java.net.Socket, inputStream: java.io.InputStream): ClientHandler {
        runCatching { finalAccept.tcpNoDelay = true }
        return super.createClientHandler(finalAccept, inputStream)
    }

    /** See [isCompressibleMime] — refusing gzip for `text/event-stream` is what makes SSE actually
     * stream instead of arriving in one piece when the turn ends. */
    override fun useGzipWhenAccepted(r: Response): Boolean =
        isCompressibleMime(r.mimeType) && super.useGzipWhenAccepted(r)

    override fun serve(session: IHTTPSession): Response = withCors(serveInner(session))

    /** Every response carries permissive CORS headers, and a preflight `OPTIONS` is answered
     * without reaching the auth gate or any handler. Without this, a browser-based OpenAI client
     * (Open WebUI, LibreChat, a local dev page) cannot call this server *at all* — the browser
     * blocks the request before it is ever sent, so "compatible with OpenAI clients" is only true
     * for non-browser ones. `*` is the right origin policy here specifically because the bearer
     * token is the access control: `Access-Control-Allow-Credentials` is deliberately NOT set, so
     * `*` stays legal and no browser will attach ambient cookies to a cross-origin call — a
     * caller must present the API key explicitly, exactly as a non-browser client does. */
    private fun withCors(response: Response): Response = response.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        // Lets a browser client read the RAG provenance header on a streaming response; without an
        // explicit expose-list, fetch() hides every non-safelisted response header.
        addHeader("Access-Control-Expose-Headers", "X-Vervan-Sources")
        addHeader("Access-Control-Max-Age", "86400")
    }

    private fun serveInner(session: IHTTPSession): Response {
        app.container.networkAuditLog.record("Local API request: ${session.method} ${session.uri}")

        // Preflight carries no credentials by design — answering it before the auth gate is what
        // the CORS spec requires, and it discloses nothing beyond the headers set in withCors().
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        }

        // The bundled web UI's page shell (HTML/CSS/JS) carries no user data of its own — it's
        // the same static asset for every visitor — so it's served before the auth gate below,
        // same reasoning a public login page is reachable before authentication. The UI's own
        // JS is what subsequently calls /v1/models and /v1/chat/completions *with* a token
        // (entered by the user, or carried in from Settings' "Open web UI" via a one-time
        // ?token= query param — see webui/index.html), which the auth gate still enforces
        // exactly as for any other client.
        if (session.method == Method.GET && session.uri in setOf("/", "/index.html")) {
            return serveWebUi()
        }
        // Static library assets the web UI's <script>/<link> tags pull in (currently just the
        // Mermaid diagram renderer, vendored at app/src/main/assets/mermaid/mermaid.min.js — the
        // same bundle the native app's in-chat diagram WebView already uses, see
        // MarkdownLite.kt's MermaidDiagram). Same no-auth reasoning as the page shell itself:
        // static library code, not user data. Name-list, not a path passthrough — see
        // serveStaticAsset's own traversal guard for why that still matters.
        if (session.method == Method.GET && session.uri.startsWith("/webui-assets/")) {
            // Fixed name → fixed asset. A map rather than appending the request path to a directory
            // so the request can never name a file this server did not intend to publish, which is
            // what keeps the whole /webui-assets prefix free of traversal exposure.
            val asset = WEB_UI_ASSETS[session.uri.removePrefix("/webui-assets/")]
                ?: return errorResponse(Response.Status.NOT_FOUND, "Unknown asset")
            return serveStaticAsset(asset.first, asset.second)
        }

        // Liveness probe + API description, same no-auth reasoning as the static routes above:
        // whether the server is up (and what it can do) is not user data. This has to sit before
        // the auth gate below to actually be reachable without a token — the two routes used to
        // be matched inside the post-auth `when` further down, which silently 401'd them despite
        // both this comment and openApiSpec()'s own `"security": []` on /health claiming
        // otherwise; a monitoring check or "test connection" probe hitting either route with no
        // key got turned away exactly like a real data endpoint would.
        if (session.method == Method.GET && (session.uri == "/health" || session.uri == "/v1/health")) {
            return jsonResponse(Response.Status.OK, JSONObject().put("status", "ok").put("full_mode", fullMode))
        }
        if (session.method == Method.GET && (session.uri == "/openapi.json" || session.uri == "/v1/openapi.json")) {
            return jsonResponse(Response.Status.OK, openApiSpec())
        }

        // Recorded after the static-asset routes above (fetching the page shell isn't a client
        // "using" the API yet) and before the handlers, so a request that fails auth still shows up
        // — an unrecognized address being turned away is exactly what the user needs to see.
        val authorized = if (!requireAuth) true else {
            val header = session.headers["authorization"] ?: session.headers["Authorization"]
            val authParts = header?.trim()?.split(Regex("\\s+"), limit = 2).orEmpty()
            val token = authParts.takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }
                ?.get(1).orEmpty()
            auth.verify(token)
        }
        app.container.apiClientRegistry.record(
            address = session.remoteIpAddress.orEmpty(),
            userAgent = (session.headers["user-agent"] ?: session.headers["User-Agent"]).orEmpty(),
            path = session.uri,
            authorized = authorized,
            authChecked = requireAuth
        )
        if (!authorized) {
            return errorResponse(
                Response.Status.UNAUTHORIZED, "Missing or invalid API key",
                ErrorType.INVALID_REQUEST, "invalid_api_key", null
            )
        }

        return try {
            when {
                session.method == Method.GET && session.uri == "/v1/models" -> handleModels()
                // `/v1/models/{id}` — the spec's single-model retrieve call. Some clients use it
                // to validate a configured model name before their first completion.
                session.method == Method.GET && session.uri.startsWith("/v1/models/") ->
                    handleRetrieveModel(Uri.decode(session.uri.removePrefix("/v1/models/")))
                session.method == Method.POST && session.uri == "/v1/chat/completions" -> handleChatCompletions(session)
                // Legacy text completions. Still the only endpoint some older integrations speak,
                // and it's a thin adapter over the same path — the prompt becomes a single user
                // turn and the result is reshaped into a `text_completion` object.
                session.method == Method.POST && session.uri == "/v1/completions" -> handleLegacyCompletions(session)
                session.method == Method.POST && session.uri == "/v1/embeddings" -> handleEmbeddings(session)
                session.method == Method.POST && session.uri == "/v1/audio/transcriptions" -> handleTranscriptions(session)
                session.method == Method.POST && session.uri == "/v1/audio/translations" ->
                    errorResponse(
                        Response.Status.NOT_IMPLEMENTED,
                        "Translation to English isn't supported — use /v1/audio/transcriptions",
                        ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, null
                    )
                session.method == Method.POST && session.uri == "/v1/audio/speech" -> handleSpeech(session)
                // /health and /openapi.json are handled above, before the auth gate — see that
                // comment for why.
                // Everything below reads or writes app data (knowledge bases, documents), not
                // just running inference — gated on fullMode regardless of auth, same reasoning
                // as the Settings screen's own "bigger trust boundary" warning for this mode.
                !fullMode && session.uri.startsWith("/api/") ->
                    errorResponse(Response.Status.NOT_FOUND, "Full web app mode is off — enable it in Settings to use this endpoint")
                session.method == Method.GET && session.uri == "/api/knowledge-bases" -> handleListKnowledgeBases()
                session.method == Method.POST && session.uri == "/api/knowledge-bases" -> handleCreateKnowledgeBase(session)
                session.method == Method.GET && session.uri == "/api/documents" -> handleListDocuments(session)
                session.method == Method.POST && session.uri == "/api/documents" -> handleUploadDocument(session)
                session.method == Method.POST && session.uri == "/api/documents/delete" -> handleDeleteDocument(session)
                session.method == Method.POST && session.uri == "/api/knowledge-bases/delete" -> handleDeleteKnowledgeBase(session)
                session.method == Method.GET && session.uri == "/api/chats" -> handleListChats(session)
                session.method == Method.GET && session.uri == "/api/chat" -> handleGetChat(session)
                session.method == Method.POST && session.uri == "/api/chats" -> handleCreateChat(session)
                session.method == Method.POST && session.uri == "/api/chats/update" -> handleUpdateChat(session)
                session.method == Method.POST && session.uri == "/api/chats/delete" -> handleDeleteChat(session)
                session.method == Method.GET && session.uri == "/api/messages" -> handleListMessages(session)
                session.method == Method.POST && session.uri == "/api/messages/delete" -> handleDeleteMessage(session)
                // Serves a stored message attachment's bytes. Without this the web app can list a
                // past conversation but renders it without the images/audio it actually contained —
                // `has_image` was a boolean pointing at nothing fetchable.
                session.method == Method.GET && session.uri == "/api/attachments" -> handleAttachment(session)
                session.method == Method.GET && session.uri == "/api/tools" -> handleListTools()
                session.method == Method.GET && session.uri == "/api/clients" -> handleListClients()
                else -> webAppApi.handle(session) ?: errorResponse(
                    Response.Status.NOT_FOUND, "Unknown endpoint",
                    ErrorType.NOT_FOUND, null, null
                )
            }
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            // Throwable, not just Exception — NanoHTTPD buffers request bodies with no size cap
            // enforced here, so an oversized Content-Length can OutOfMemoryError; that must not
            // crash the app just because a LAN client sent a bad request. The raw exception
            // message also used to go straight into the client-visible JSON error body.
            Log.e(TAG, "serve() failed for ${session.method} ${session.uri}", t)
            errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage())
        }
    }

    /** Serves the bundled web UI — `webui/full.html` (chat/RAG/documents/vision/audio) when
     * [fullMode] is on, `webui/basic.html` (a bare status/API-docs page, no chat) otherwise; see
     * the class doc comment on [fullMode] for why these are genuinely different trust surfaces,
     * not just a UI preference. Both get the current app theme (mode + accent color) injected
     * as a `<script>` right after `<head>`, ahead of the stylesheet, so the page opens already
     * matching the native app's theme instead of flashing the default green/system look first —
     * see [themeInjectionScript]. Read fresh from assets on every request rather than cached in
     * memory — this is a local server on a phone, not under real request load, and it keeps the
     * asset the single source of truth with no separate cache-invalidation path. */
    private fun serveWebUi(): Response = try {
        val assetName = if (fullMode) "webui/full.html" else "webui/basic.html"
        val html = app.assets.open(assetName).use { it.readBytes() }.toString(Charsets.UTF_8)
        val injected = html.replaceFirst("<head>", "<head>\n" + themeInjectionScript())
        val bytes = injected.toByteArray(Charsets.UTF_8)
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
    } catch (e: java.io.IOException) {
        Log.e(TAG, "serveWebUi() could not read the bundled asset", e)
        errorResponse(Response.Status.INTERNAL_ERROR, "Web UI asset missing")
    }

    /** Serves one fixed asset path — [assetPath] is always one of this file's own hardcoded
     * literals (see call sites), never derived from request input, so there's no actual path-
     * traversal exposure today; the explicit `assetPath.contains("..")` check is a belt-and-
     * braces guard against a future call site accidentally passing something request-derived. */
    private fun serveStaticAsset(assetPath: String, mimeType: String): Response {
        if (assetPath.contains("..")) return errorResponse(Response.Status.FORBIDDEN, "Invalid asset path")
        return try {
            val bytes = app.assets.open(assetPath).use { it.readBytes() }
            newFixedLengthResponse(Response.Status.OK, mimeType, java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: java.io.IOException) {
            Log.e(TAG, "serveStaticAsset() could not read $assetPath", e)
            errorResponse(Response.Status.NOT_FOUND, "Asset not found")
        }
    }

    /** `--accent-light`/`--accent-dark` are the two hex values the page's CSS derives its whole
     * accent palette from via `color-mix()` (see webui/full.html's stylesheet) — only the base
     * color needs injecting, not every hover/soft shade. `theme` mirrors the app's own
     * ThemeMode (SYSTEM/LIGHT/DARK); the page sets `documentElement.dataset.theme` from it so an
     * explicit Light/Dark choice in the native app wins over the browser's OS preference the
     * same way it does inside the app itself, while SYSTEM leaves the attribute unset and defers
     * to `prefers-color-scheme` exactly as before this existed. */
    private fun themeInjectionScript(): String {
        val settings = app.container.settingsRepository
        val themeMode = runBlocking { settings.themeMode.first() }
        val accent = runBlocking { settings.accentTheme.first() }
        val (accentLight, accentDark) = accentHex(accent)
        val json = JSONObject()
            .put("mode", themeMode.name)
            .put("accentLight", accentLight)
            .put("accentDark", accentDark)
        return "<script>window.__VERVAN_THEME = $json;</script>\n"
    }

    /** Mirrors the primary-color half of [com.vervan.chat.ui.theme]'s `DarkAccents`/
     * `LightAccents` maps for each [com.vervan.chat.data.settings.AccentTheme] — duplicated as
     * plain hex here (rather than depending on the Compose `Color`/theme module from a plain
     * HTTP handler) since it's five constant pairs, not a value worth a shared abstraction for. */
    private fun accentHex(accent: com.vervan.chat.data.settings.AccentTheme): Pair<String, String> = when (accent) {
        com.vervan.chat.data.settings.AccentTheme.AMBER -> "#9A6400" to "#F6B24E"
        com.vervan.chat.data.settings.AccentTheme.BLUE -> "#3D5FE0" to "#7C9AFF"
        com.vervan.chat.data.settings.AccentTheme.GREEN -> "#0F7A3D" to "#53E88B"
        com.vervan.chat.data.settings.AccentTheme.VIOLET -> "#6D46D6" to "#A78BFA"
        com.vervan.chat.data.settings.AccentTheme.ROSE -> "#B72B5D" to "#FB7185"
    }

    private fun handleModels(): Response {
        val data = JSONArray()
        runBlocking { modelListingJson() }.forEach { data.put(it) }
        return jsonResponse(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
    }

    /** `/v1/models/{id}` — the same object one entry of the list carries, or a 404 whose `code` is
     * `model_not_found`, which is what a client checking a configured model name is looking for. */
    private fun handleRetrieveModel(id: String): Response {
        val match = runBlocking { modelListingJson() }.firstOrNull { it.optString("id") == id }
            ?: return errorResponse(
                Response.Status.NOT_FOUND, "No model named \"$id\" is installed",
                ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, "model"
            )
        return jsonResponse(Response.Status.OK, match)
    }

    private suspend fun modelListingJson(): List<JSONObject> {
        val models = app.container.db.modelDao().observeModels().first()
            .filter { it.role == ModelRole.GENERATION || it.role == ModelRole.EMBEDDING }
        val activeGenerationId = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)?.id
        val activeEmbeddingId = app.container.db.modelDao().getActiveModel(ModelRole.EMBEDDING)?.id
        // Which model (if any) is actually resident right now, per role — the thing that makes
        // this listing report "the loaded model" rather than only "the configured default". A role
        // with nothing loaded reports every model as `not-loaded`; the default is still marked
        // `active`, so a client with no model loaded yet still knows what a bare request will get.
        val loadState = app.container.modelLoadCoordinator.state.value
        val loadedIds = ROLE_LIST.mapNotNull { role ->
            loadState[role]?.takeIf { it.phase == com.vervan.chat.modelload.ModelLoadPhase.READY }?.currentModelId
        }.toSet()
        val loadingIds = ROLE_LIST.mapNotNull { loadState[it]?.loadingModelId }.toSet()
        return models.map { m ->
            val activeId = if (m.role == ModelRole.EMBEDDING) activeEmbeddingId else activeGenerationId
            // id/object/owned_by are the only fields the OpenAI spec actually defines here — the
            // rest are additive, non-standard extensions the bundled web UI reads for its model
            // info panel. A strict OpenAI client that only looks at the standard three keys is
            // unaffected by the extras; nothing here removes or reshapes spec fields.
            JSONObject()
                    .put("id", m.displayName)
                    .put("object", "model")
                    // The row id, alongside the display name that `id` has to keep being for
                    // OpenAI clients. The web app needs it to write Chat.modelId when the user
                    // picks a model — two models can share a display name, so the name alone
                    // cannot identify which row a chat is pinned to.
                    .put("model_id", m.id)
                    // Part of the spec's model object and required by a few strict clients.
                    // Import time is the only creation timestamp this app has for a model.
                    .put("created", m.importedAt / 1000)
                    .put("owned_by", "local")
                    .put("role", m.role.name.lowercase())
                    .put("engine", m.engine.name)
                    .put("context_length", m.contextTokens ?: JSONObject.NULL)
                    .put("backend", m.lastWorkingBackend.name)
                    .put("active", m.id == activeId)
                    // LM Studio's own `/api/v0/models` reports exactly this loaded/not-loaded
                    // distinction, and it's what makes "show the default until something is
                    // loaded, then show what's loaded" answerable from one request. Additive, so
                    // a strict OpenAI client still only sees id/object/owned_by as before.
                    .put(
                        "state",
                        when {
                            m.id in loadedIds -> "loaded"
                            m.id in loadingIds -> "loading"
                            else -> "not-loaded"
                        }
                    )
                    // Non-null only while this model is the TTL-managed (JIT, API-loaded) one for
                    // its role — a model the user loaded in the app has no expiry and reports null.
                    .put(
                        "ttl_expires_at",
                        (if (m.id in loadedIds) app.container.modelLoadCoordinator.ttlDeadlineAt(m.role) else null)
                            ?.let { it / 1000 } ?: JSONObject.NULL
                    )
                    // Falls back to the hard physical-capability check (canSupportVision/Audio)
                    // until a real load proves it one way or the other (see ModelInfo.
                    // reconcileCapabilities) — same "can this model do it at all" gate the native
                    // chat UI uses to decide whether to show the attach/mic buttons.
                    .put("supports_vision", m.role == ModelRole.GENERATION && (m.supportsVision ?: m.canSupportVision()))
                    .put("supports_audio", m.role == ModelRole.GENERATION && (m.supportsAudio ?: m.canSupportAudio()))
                    .put("supports_thinking", m.supportsThinking ?: false)
                    .put("default_thinking_mode", m.defaultThinkingMode ?: "OFF")
                    .put("default_temperature", m.temperature ?: JSONObject.NULL)
                    .put("default_top_p", m.topP ?: JSONObject.NULL)
                    .put("default_min_p", m.minP ?: JSONObject.NULL)
                    .put("default_repetition_penalty", m.repetitionPenalty ?: JSONObject.NULL)
                    .put("default_max_output_tokens", m.maxOutputTokens ?: JSONObject.NULL)
        }
    }

    /** OpenAI-compatible `/v1/embeddings` — `input` is a single string or an array of strings
     * (the array-of-token-ids form of the real spec is out of scope: nothing in this app's own
     * pipeline ever produces raw token-id input for embedding). Reuses
     * [com.vervan.chat.retrieval.EmbeddingEngine] through the same coordinator/mutex path as
     * on-device RAG retrieval — an embedding request here waits for whatever's already loading
     * for that role exactly like every other embedding caller in the app, not a private fast path. */
    private fun handleEmbeddings(session: IHTTPSession): Response {
        val lengthHeader = session.headers["content-length"] ?: session.headers["Content-Length"]
        val declaredLength = lengthHeader?.toLongOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "A valid Content-Length header is required")
        if (declaredLength < 0 || declaredLength > MAX_BODY_BYTES) {
            return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Request body too large (max ${MAX_BODY_BYTES / (1024 * 1024)} MB)")
        }
        val body = HashMap<String, String>()
        session.parseBody(body)
        val postData = body["postData"] ?: "{}"
        val json = runCatching { JSONObject(postData) }.getOrElse {
            return errorResponse(Response.Status.BAD_REQUEST, "Request body must be valid JSON")
        }
        val inputs: List<String> = when (val input = json.opt("input")) {
            is String -> listOf(input)
            is JSONArray -> (0 until input.length()).map { input.optString(it, "") }
            else -> return errorResponse(Response.Status.BAD_REQUEST, "input must be a string or an array of strings")
        }
        if (inputs.isEmpty() || inputs.size > MAX_EMBEDDING_INPUTS) {
            return errorResponse(Response.Status.BAD_REQUEST, "input must contain between 1 and $MAX_EMBEDDING_INPUTS items")
        }
        val totalChars = inputs.sumOf { it.length.toLong() }
        if (totalChars > MAX_EMBEDDING_CHARS) {
            return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Input text is too large")
        }
        val requestedModel = json.optString("model").ifBlank { null }
        // `float` (the default) or `base64` — openai-python asks for base64 unless told otherwise,
        // so honoring it is what makes the *default* client configuration efficient rather than
        // merely tolerated.
        val encodingFormat = json.optString("encoding_format").ifBlank { "float" }
        if (encodingFormat != "float" && encodingFormat != "base64") {
            return errorResponse(
                Response.Status.BAD_REQUEST, "encoding_format must be \"float\" or \"base64\"",
                ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "encoding_format"
            )
        }
        // Matryoshka-style dimension truncation isn't something the on-device embedders support,
        // and silently ignoring the field would hand back vectors of a different size than the
        // caller asked for — which corrupts their index without any visible error.
        if (json.has("dimensions") && !json.isNull("dimensions")) {
            return errorResponse(
                Response.Status.BAD_REQUEST,
                "dimensions isn't supported — this model only produces vectors at its native size",
                ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_PARAMETER, "dimensions"
            )
        }

        val model = runBlocking {
            if (requestedModel != null) {
                app.container.db.modelDao().observeModels().first()
                    .firstOrNull { it.displayName == requestedModel && it.role == ModelRole.EMBEDDING }
            } else {
                app.container.db.modelDao().getActiveModel(ModelRole.EMBEDDING)
            }
        } ?: return if (requestedModel != null) {
            // Falling back to the default here (as this used to) silently serves a *different*
            // model than the caller named — the single most confusing failure mode for an
            // integration, since every response looks successful.
            errorResponse(
                Response.Status.NOT_FOUND, "No embedding model named \"$requestedModel\" is installed",
                ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, "model"
            )
        } else {
            errorResponse(
                Response.Status.BAD_REQUEST, "No embedding model available — set a default in Model Manager",
                ErrorType.INVALID_REQUEST, ErrorCode.MODEL_NOT_FOUND, "model"
            )
        }

        return try {
            runBlocking {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.API_REQUEST)
                    check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
                    val data = JSONArray()
                    inputs.forEachIndexed { index, text ->
                        val vector = com.vervan.chat.retrieval.embedWith(
                            model, text,
                            embeddingEngine = app.container.embeddingEngine,
                            embeddingMutex = app.container.embeddingMutex,
                            remoteOpenAiEngine = app.container.remoteOpenAiEngine,
                            remoteApiKeyStore = app.container.remoteApiKeyStore,
                            networkAuditLog = app.container.networkAuditLog
                        ) ?: throw IllegalStateException("Embedding failed for input #$index")
                        val embedding: Any = if (encodingFormat == "base64") {
                            encodeFloatsBase64(vector)
                        } else {
                            JSONArray().also { values -> vector.forEach { values.put(it.toDouble()) } }
                        }
                        data.put(JSONObject().put("object", "embedding").put("index", index).put("embedding", embedding))
                    }
                    val estimatedTokens = inputs.sumOf { com.vervan.chat.llm.estimateTokens(it) }
                    jsonResponse(
                        Response.Status.OK,
                        JSONObject()
                            .put("object", "list")
                            .put("data", data)
                            .put("model", model.displayName)
                            .put("usage", JSONObject().put("prompt_tokens", estimatedTokens).put("total_tokens", estimatedTokens))
                    )
                }
            }
        } catch (t: TimeoutCancellationException) {
            errorResponse(Response.Status.INTERNAL_ERROR, "Embedding timed out")
        } finally {
            // Embedding gets its own independent TTL clock — a server used only for /v1/embeddings
            // shouldn't hold a generation model resident, and vice versa.
            runBlocking { app.container.modelLoadCoordinator.touchTtl(ModelRole.EMBEDDING) }
        }
    }

    /** Thrown by the request-parsing helpers below to unwind straight out to a prepared error
     * [Response] — the alternative (threading a sealed result through six levels of message/
     * content-part parsing) buries the actual parsing logic in plumbing. Caught in
     * [handleChatCompletions] itself, never allowed to reach `serveInner`'s generic 500 handler. */
    private class ApiRequestException(val response: Response) : Exception(null, null, false, false)

    private fun badRequest(message: String, code: String? = null, param: String? = null): Nothing =
        throw ApiRequestException(errorResponse(Response.Status.BAD_REQUEST, message, ErrorType.INVALID_REQUEST, code, param))

    /** Everything one chat request asks for, once parsed and validated. */
    private class ChatRequest(
        val model: com.vervan.chat.data.db.entities.ModelInfo,
        val turns: List<Pair<String, String>>,
        val systemText: String,
        val lastUserText: String,
        val imageDataUrl: String?,
        val audio: Pair<String, String>?,
        val stream: Boolean,
        val includeUsage: Boolean,
        val sampling: ApiChatRunner.Sampling,
        val thinkingMode: String,
        val clientTools: List<ApiChatRunner.ClientTool>,
        val toolChoice: ApiChatRunner.ToolChoice,
        val appToolsEnabled: Boolean,
        val allowWriteTools: Boolean,
        val enabledAppToolIds: Set<String>,
        val knowledgeBaseIds: List<String>,
        val chatId: String?,
        val responseFormatInstruction: String?
    )

    /**
     * `/v1/chat/completions`. This is the endpoint third-party clients actually point at, so the
     * protocol details matter more here than anywhere else in this file: `finish_reason` on the
     * final streamed choice, `delta.role` on the first, all three `usage` token counts, a real
     * `error` frame if generation fails mid-stream, and a 404 (never a silent fallback) when the
     * requested model doesn't exist. Clients branch on every one of those.
     *
     * Beyond the wire format, a request here runs through [ApiChatRunner], so an API caller gets
     * the same behavior the native chat has: thinking split into `reasoning_content`, tool calls
     * either handed back as OpenAI `tool_calls` or executed on-device, and multi-hop tool loops.
     */
    private fun handleChatCompletions(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_CHAT_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val request = try {
            parseChatRequest(json)
        } catch (e: ApiRequestException) {
            return e.response
        }

        // Decode attachments up front, into request-scoped temp files. Rejecting rather than
        // silently dropping when the model can't use one keeps a client from believing its image
        // was seen.
        var imageFile: File? = null
        var audioFile: File? = null
        try {
            request.imageDataUrl?.let { url ->
                if (!(request.model.supportsVision ?: request.model.canSupportVision())) {
                    return errorResponse(
                        Response.Status.BAD_REQUEST, "${request.model.displayName} does not support image input",
                        ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "messages"
                    )
                }
                imageFile = decodeImageAttachment(url)
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "Could not decode the image attachment")
            }
            request.audio?.let { (data, format) ->
                if (!(request.model.supportsAudio ?: request.model.canSupportAudio())) {
                    return errorResponse(
                        Response.Status.BAD_REQUEST, "${request.model.displayName} does not support audio input",
                        ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "messages"
                    )
                }
                audioFile = decodeAudioAttachment(data, format)
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "Could not decode the audio attachment")
            }
        } catch (t: Throwable) {
            imageFile?.delete(); audioFile?.delete()
            return errorResponse(Response.Status.INTERNAL_ERROR, "Could not process attachment: ${t.toUserMessage()}", ErrorType.SERVER, ErrorCode.SERVER_ERROR, null)
        }

        var systemPrompt = request.systemText
        // A `chat_id` request is the web app continuing a real conversation, not a stateless
        // completion. Everything below used to be the client's job, which it could not actually do:
        // the browser only ever sends the newest user turn plus whatever is typed in the system box.
        // So the model got no history (every turn read as the first), no persona (changing it did
        // nothing), and only the knowledge bases the page happened to have selected. Rebuilding it
        // here from the stored chat is what makes the web app behave like the on-device chat.
        var effectiveTurns = request.turns
        var effectiveKbIds = request.knowledgeBaseIds
        if (fullMode && request.chatId != null) {
            val ctx = runBlocking { chatContext(request.chatId, request.knowledgeBaseIds) }
            if (ctx != null) {
                systemPrompt = listOf(ctx.systemPrefix, systemPrompt).filter { it.isNotBlank() }.joinToString("\n\n")
                effectiveKbIds = ctx.kbIds
                // Only supply history when the caller clearly didn't: the web app posts exactly one
                // user turn per send, but a third-party client is free to pass chat_id *and* the
                // full transcript, and prepending ours on top of theirs would feed the model every
                // earlier turn twice.
                effectiveTurns = if (request.turns.size <= 1) ctx.priorTurns + request.turns else request.turns
            }
        }

        // RAG runs before generation because the retrieved text has to be in the system prompt
        // before the first token is produced; the sources travel alongside the response either way
        // (a header on the streaming path, a field on the non-streaming one).
        var sourcesJson: String? = null
        if (fullMode && effectiveKbIds.isNotEmpty() && request.lastUserText.isNotBlank()) {
            val passages = runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(GENERATION_TIMEOUT_MS) { performRetrieval(effectiveKbIds, request.lastUserText) }
            }.orEmpty()
            if (passages.isNotEmpty()) {
                val contextLimit = runBlocking { app.container.settingsRepository.contextTokenLimit.first() }
                val trimmed = ChatFormatting.trimPassagesToBudget(passages, contextLimit)
                sourcesJson = ChatFormatting.sourcesToJson(trimmed)
                val sourcesBlock = trimmed.joinToString("\n\n") { p ->
                    val heading = if (p.sectionPath.isNotBlank()) "${p.documentName} — ${p.sectionPath}" else p.documentName
                    "[$heading]\n${p.excerpt}"
                }
                systemPrompt = (
                    systemPrompt +
                        "\n\nUse the following context from the user's knowledge base to help answer, " +
                        "citing the document name where relevant:\n\n" + sourcesBlock
                    ).trim()
            }
        }
        request.responseFormatInstruction?.let { systemPrompt = (systemPrompt + "\n\n" + it).trim() }

        fun cleanupAttachments() { imageFile?.delete(); audioFile?.delete() }

        // One permit per in-flight generation. Acquired here so the 429 goes out before anything
        // expensive happens; released by whichever path ends up owning the work.
        if (!generationSlots.tryAcquire()) {
            cleanupAttachments()
            return errorResponse(
                Response.Status.TOO_MANY_REQUESTS,
                "Too many generation requests are already queued on this device — retry shortly",
                ErrorType.RATE_LIMIT, ErrorCode.RATE_LIMIT_EXCEEDED, null
            ).apply { addHeader("Retry-After", "5") }
        }

        val runnerRequest = ApiChatRunner.Request(
            model = request.model,
            systemPrompt = systemPrompt,
            flatPrompt = buildFlatPrompt(effectiveTurns),
            turns = effectiveTurns,
            imagePath = imageFile?.absolutePath,
            audioPath = audioFile?.absolutePath,
            sampling = request.sampling,
            thinkingMode = request.thinkingMode,
            clientTools = request.clientTools,
            toolChoice = request.toolChoice,
            appToolsEnabled = request.appToolsEnabled,
            allowWriteTools = request.allowWriteTools,
            enabledAppToolIds = request.enabledAppToolIds,
            maxToolHops = MAX_TOOL_HOPS
        )
        val promptTokens = com.vervan.chat.llm.estimateTokens(
            systemPrompt + effectiveTurns.joinToString("\n") { it.second }
        )
        val completionId = "chatcmpl-${UUID.randomUUID()}"
        val modelName = request.model.displayName

        return if (request.stream) {
            streamChatCompletion(request, runnerRequest, completionId, modelName, promptTokens, sourcesJson, imageFile, audioFile)
        } else {
            try {
                blockingChatCompletion(request, runnerRequest, completionId, modelName, promptTokens, sourcesJson, imageFile, audioFile)
            } finally {
                generationSlots.release()
                // End-of-request TTL restart: a long turn shouldn't spend its idle window while it
                // was busy. Runs on the failure path too — a client that just errored is very
                // likely about to retry.
                runBlocking { app.container.modelLoadCoordinator.touchTtl(ModelRole.GENERATION) }
            }
        }
    }

    private fun blockingChatCompletion(
        request: ChatRequest,
        runnerRequest: ApiChatRunner.Request,
        completionId: String,
        modelName: String,
        promptTokens: Int,
        sourcesJson: String?,
        imageFile: File?,
        audioFile: File?
    ): Response {
        val answer = StringBuilder()
        val reasoning = StringBuilder()
        val appToolEvents = JSONArray()
        var toolCalls: List<ApiChatRunner.ClientCall> = emptyList()
        var finishReason = "stop"
        var completionTokens = 0
        val startedAtMs = System.currentTimeMillis()
        try {
            runBlocking {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    val loaded = app.container.modelLoadCoordinator.ensureLoaded(request.model, LoadTrigger.API_REQUEST)
                    check(loaded.success) { loaded.errorMessage ?: "Could not load ${request.model.displayName}" }
                    runner.run(runnerRequest).collect { event ->
                        when (event) {
                            is ApiChatRunner.Event.Answer -> answer.append(event.text)
                            is ApiChatRunner.Event.Reasoning -> reasoning.append(event.text)
                            is ApiChatRunner.Event.AppToolExecuted -> appToolEvents.put(appToolJson(event))
                            is ApiChatRunner.Event.ClientToolCalls -> toolCalls = event.calls
                            is ApiChatRunner.Event.Done -> {
                                finishReason = event.finishReason
                                completionTokens = event.completionTokens
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            imageFile?.delete(); audioFile?.delete()
            if (t is VirtualMachineError) throw t
            if (t is TimeoutCancellationException) {
                return errorResponse(
                    Response.Status.INTERNAL_ERROR, "Generation timed out after ${GENERATION_TIMEOUT_MS / 1000}s",
                    ErrorType.SERVER, ErrorCode.SERVER_ERROR, null
                )
            }
            Log.e(TAG, "chat completion failed", t)
            return errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage(), ErrorType.SERVER, ErrorCode.SERVER_ERROR, null)
        }
        val generationMs = System.currentTimeMillis() - startedAtMs
        val completionJson = chatCompletionJson(
            completionId, modelName, answer.toString(), reasoning.toString().takeIf { it.isNotBlank() },
            toolCalls, finishReason
        ).put("usage", usageJson(promptTokens, completionTokens, generationMs))
        sourcesJson?.let { completionJson.put("sources", JSONArray(it)) }
        if (appToolEvents.length() > 0) completionJson.put("app_tools", appToolEvents)

        if (request.chatId != null) {
            val persistedImagePath = imageFile?.let { persistAttachment(it) }
            val persistedAudioPath = audioFile?.let { persistAttachment(it) }
            runCatching {
                runBlocking {
                    persistTurn(
                        request.chatId, request.lastUserText, persistedImagePath, persistedAudioPath,
                        answer.toString(), generationMs, request.model, sourcesJson
                    )
                }
            }.onFailure { Log.e(TAG, "persistTurn() failed for chat ${request.chatId}", it) }
        }
        imageFile?.delete(); audioFile?.delete()
        return jsonResponse(Response.Status.OK, completionJson)
    }

    private fun streamChatCompletion(
        request: ChatRequest,
        runnerRequest: ApiChatRunner.Request,
        completionId: String,
        modelName: String,
        promptTokens: Int,
        sourcesJson: String?,
        imageFile: File?,
        audioFile: File?
    ): Response {
        val pipedIn = PipedInputStream(SSE_PIPE_SIZE)
        val pipedOut = PipedOutputStream(pipedIn)
        streamingScope.launch {
            val answer = StringBuilder()
            val startedAtMs = System.currentTimeMillis()
            var succeeded = false
            var completionTokens = 0

            suspend fun write(frame: String): Boolean = try {
                withTimeout(SSE_WRITE_TIMEOUT_MS) {
                    runInterruptible { pipedOut.write(frame.toByteArray()); pipedOut.flush() }
                }
                true
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "SSE client stopped reading; aborting stream", e)
                false
            }

            try {
                // Chrome (and some other browsers) buffer the first ~1-2 KB of a fetch() response
                // before exposing anything to a ReadableStream reader — a MIME-sniffing heuristic
                // that otherwise makes a short chat response look completely non-streaming. An SSE
                // comment line (RFC-legal: any line starting with `:` is ignored by spec-compliant
                // parsers) padded past that threshold defeats it with no client-side change.
                if (!write(": " + "0".repeat(2048) + "\n\n")) throw kotlinx.coroutines.CancellationException("SSE consumer gone")
                // The spec's opening frame: role announced, no content yet.
                if (!write(roleChunkFrame(completionId, modelName))) throw kotlinx.coroutines.CancellationException("SSE consumer gone")

                var finishReason = "stop"
                // Live tok/s + RAM, the same readout the app's own "Show generation stats" strip
                // shows while a response streams. Emitted as an additive `vervan_stats` frame at a
                // fixed cadence rather than per token: the numbers are only meaningful over an
                // interval, and a frame per token would multiply SSE traffic for no extra insight.
                var lastStatsAtMs = 0L
                suspend fun maybeWriteStats(force: Boolean = false): Boolean {
                    val now = System.currentTimeMillis()
                    if (!force && now - lastStatsAtMs < LIVE_STATS_INTERVAL_MS) return true
                    lastStatsAtMs = now
                    val elapsed = now - startedAtMs
                    val tokens = com.vervan.chat.llm.estimateTokens(answer.toString())
                    return write(liveStatsFrame(completionId, modelName, tokens, elapsed, readMemory()))
                }

                withTimeout(GENERATION_TIMEOUT_MS) {
                    val loaded = app.container.modelLoadCoordinator.ensureLoaded(request.model, LoadTrigger.API_REQUEST)
                    check(loaded.success) { loaded.errorMessage ?: "Could not load ${request.model.displayName}" }
                    runner.run(runnerRequest).collect { event ->
                        val frame = when (event) {
                            is ApiChatRunner.Event.Answer -> {
                                answer.append(event.text)
                                if (!maybeWriteStats()) throw kotlinx.coroutines.CancellationException("SSE consumer gone")
                                chatChunkFrame(completionId, modelName, JSONObject().put("content", event.text), null)
                            }
                            is ApiChatRunner.Event.Reasoning ->
                                chatChunkFrame(completionId, modelName, JSONObject().put("reasoning_content", event.text), null)
                            // Additive, non-standard frame: an on-device tool ran, which the client
                            // has nothing to execute but every reason to be able to show. Unknown
                            // top-level keys are ignored by every OpenAI client.
                            is ApiChatRunner.Event.AppToolExecuted ->
                                chatChunkFrame(completionId, modelName, JSONObject(), null)
                                    .replaceFirst("data: {", "data: {\"vervan_tool\": ${appToolJson(event)}, ")
                            is ApiChatRunner.Event.ClientToolCalls ->
                                chatChunkFrame(
                                    completionId, modelName,
                                    JSONObject().put("tool_calls", toolCallsJson(event.calls)), null
                                )
                            is ApiChatRunner.Event.Done -> {
                                finishReason = event.finishReason
                                completionTokens = event.completionTokens
                                null
                            }
                        }
                        if (frame != null && !write(frame)) throw kotlinx.coroutines.CancellationException("SSE consumer gone")
                    }
                }
                // The frame that tells the client the turn ended, and why. Its absence is what made
                // every stream look truncated to strict clients.
                write(chatChunkFrame(completionId, modelName, JSONObject(), finishReason))
                val generationMs = System.currentTimeMillis() - startedAtMs
                if (request.includeUsage) {
                    write(usageOnlyFrame(completionId, modelName, usageJson(promptTokens, completionTokens, generationMs)))
                }
                write("data: [DONE]\n\n")
                succeeded = true

                if (request.chatId != null) {
                    val persistedImagePath = imageFile?.let { persistAttachment(it) }
                    val persistedAudioPath = audioFile?.let { persistAttachment(it) }
                    runCatching {
                        persistTurn(
                            request.chatId, request.lastUserText, persistedImagePath, persistedAudioPath,
                            answer.toString(), generationMs, request.model, sourcesJson
                        )
                    }.onFailure { Log.e(TAG, "persistTurn() failed for chat ${request.chatId}", it) }
                }
            } catch (t: Throwable) {
                if (t is VirtualMachineError) throw t
                // A client that hung up (or a cancelled server scope) is not an error to report —
                // and writing to a pipe nobody is draining would block for the full write timeout
                // per frame before failing anyway. Only a real failure gets an error frame.
                val clientGone = t is kotlinx.coroutines.CancellationException && t !is TimeoutCancellationException
                if (clientGone) {
                    Log.i(TAG, "streaming chat completion ended early: ${t.message}")
                } else {
                    Log.e(TAG, "streaming chat completion failed", t)
                    // A real SSE `error` frame, not an assistant-text delta: a client must be able
                    // to tell a failure from something the model said.
                    val message = if (t is TimeoutCancellationException) {
                        "Generation timed out after ${GENERATION_TIMEOUT_MS / 1000}s"
                    } else {
                        t.toUserMessage()
                    }
                    runCatching { write(errorFrame(message, ErrorType.SERVER, ErrorCode.SERVER_ERROR)) }
                    runCatching { write("data: [DONE]\n\n") }
                }
            } finally {
                runCatching { pipedOut.close() }
                // persistAttachment() (above, when chatId != null && succeeded) copies into
                // permanent storage rather than moving — the temp upload under server-uploads/ is
                // always safe to delete here regardless of outcome, same as the non-streaming
                // path (blockingChatCompletion). Deleting it only on failure leaked one temp file
                // per attachment on every successful persisted (chatId != null) turn — the common
                // case for the bundled web UI.
                imageFile?.delete(); audioFile?.delete()
                generationSlots.release()
                // Restart the idle clock from *end of request* — a long stream would otherwise be
                // most of the way through its TTL window by the time it finished.
                app.container.modelLoadCoordinator.touchTtl(ModelRole.GENERATION)
            }
        }
        return newChunkedResponse(Response.Status.OK, "text/event-stream", pipedIn).also { response ->
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader("X-Accel-Buffering", "no")
            sourcesJson?.let { response.addHeader("X-Vervan-Sources", java.net.URLEncoder.encode(it, "UTF-8")) }
        }
    }

    /** System-wide RAM, as `ActivityManager` reports it — the figure the app's own generation-stats
     * strip shows. System-wide rather than per-app heap because model weights are native/mmap
     * allocations, which the per-app heap cap does not govern. */
    private fun readMemory(): JSONObject {
        val info = android.app.ActivityManager.MemoryInfo()
        (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getMemoryInfo(info)
        return JSONObject()
            .put("available_mb", info.availMem / (1024 * 1024))
            .put("total_mb", info.totalMem / (1024 * 1024))
            .put("low", info.lowMemory)
    }

    /** Additive SSE frame carrying live generation telemetry. `choices` is empty so a strict OpenAI
     * client parses and ignores it exactly as it does the usage-only frame. */
    private fun liveStatsFrame(
        id: String,
        model: String,
        tokens: Int,
        elapsedMs: Long,
        memory: JSONObject
    ): String {
        val stats = JSONObject()
            .put("tokens", tokens)
            .put("elapsed_ms", elapsedMs)
            .put("tokens_per_second", if (elapsedMs <= 0) 0.0 else tokens * 1000.0 / elapsedMs)
            .put("memory", memory)
        val body = JSONObject()
            .put("id", id)
            .put("object", "chat.completion.chunk")
            .put("created", System.currentTimeMillis() / 1000)
            .put("model", model)
            .put("choices", JSONArray())
            .put("vervan_stats", stats)
        return "data: $body\n\n"
    }

    private fun appToolJson(event: ApiChatRunner.Event.AppToolExecuted): JSONObject =
        JSONObject()
            .put("name", event.name)
            .put("params", event.params)
            .put("success", event.result.success)
            .put("summary", event.result.summary)

    // ---- request parsing ---------------------------------------------------------------------

    private fun parseChatRequest(json: JSONObject): ChatRequest {
        rejectUnsupportedParams(json)

        val messages = json.optJSONArray("messages")
            ?: badRequest("messages must be a JSON array", ErrorCode.INVALID_VALUE, "messages")
        if (messages.length() !in 1..MAX_MESSAGES) {
            badRequest("messages must contain between 1 and $MAX_MESSAGES items", ErrorCode.INVALID_VALUE, "messages")
        }

        val turns = mutableListOf<Pair<String, String>>()
        val systemText = StringBuilder()
        var contentChars = 0L
        val images = mutableListOf<String>()
        var audio: Pair<String, String>? = null
        // tool_call_id -> tool name, so a `role: "tool"` result can be labelled with the tool it
        // came from even though the spec only requires the id on that message.
        val toolCallNames = mutableMapOf<String, String>()
        // The last turn the *human* actually sent. Tracked separately from `turns` because tool
        // results are delivered as user turns too (see the `tool` branch below), and using one of
        // those as the RAG query — or persisting it as the user's message — would be wrong.
        var lastUserText = ""

        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i)
                ?: badRequest("Each message must be a JSON object", ErrorCode.INVALID_VALUE, "messages")
            val role = message.optString("role", "user").lowercase()
            val text = StringBuilder()
            when (val content = message.opt("content")) {
                null, JSONObject.NULL -> Unit // legal for an assistant turn that only has tool_calls
                is String -> {
                    contentChars += content.length
                    text.append(content)
                }
                is JSONArray -> {
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        when (part.optString("type")) {
                            "text", "input_text" -> {
                                val t = part.optString("text")
                                contentChars += t.length
                                if (text.isNotEmpty()) text.append(' ')
                                text.append(t)
                            }
                            "image_url", "input_image" -> {
                                val url = (part.optJSONObject("image_url")?.optString("url")
                                    ?: part.optString("image_url"))
                                    .takeIf { it.isNotBlank() } ?: continue
                                if (!url.startsWith("data:")) {
                                    // Fetching a remote URL would make this device issue an
                                    // outbound request on a caller's behalf — an egress path this
                                    // app deliberately doesn't have. Saying so beats the previous
                                    // behavior of base64-decoding the URL text and reporting a
                                    // misleading "could not decode the image".
                                    badRequest(
                                        "Only inline data: image URLs are supported — this device does not fetch remote images",
                                        ErrorCode.UNSUPPORTED_VALUE, "messages"
                                    )
                                }
                                if (url.length > MAX_IMAGE_DATA_URL_CHARS) {
                                    throw ApiRequestException(errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Image attachment is too large"))
                                }
                                images += url
                            }
                            "input_audio" -> part.optJSONObject("input_audio")?.let { a ->
                                val data = a.optString("data").takeIf { it.isNotBlank() } ?: return@let
                                if (data.length > MAX_AUDIO_BASE64_CHARS) {
                                    throw ApiRequestException(errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Audio attachment is too large"))
                                }
                                if (audio != null) {
                                    badRequest(
                                        "Only one audio attachment per request is supported",
                                        ErrorCode.UNSUPPORTED_VALUE, "messages"
                                    )
                                }
                                audio = data to a.optString("format").ifBlank { "webm" }
                            }
                        }
                    }
                }
                else -> badRequest("Each message content must be text or an array of content parts", ErrorCode.INVALID_VALUE, "messages")
            }
            if (contentChars > MAX_MESSAGE_CHARS) {
                throw ApiRequestException(errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Message content is too large"))
            }

            // An assistant turn the client is replaying to us may carry `tool_calls`. Rendering
            // them back into the `<tool_call>` form this app's models emit keeps the conversation
            // self-consistent: the model sees the call it made last turn in the same syntax it
            // produced, immediately before the result.
            message.optJSONArray("tool_calls")?.let { calls ->
                if (calls.length() > InputLimits.API_MAX_TOOLS) {
                    badRequest("Too many tool calls in one message", ErrorCode.INVALID_VALUE, "messages")
                }
                for (k in 0 until calls.length()) {
                    val call = calls.optJSONObject(k) ?: continue
                    val fn = call.optJSONObject("function") ?: continue
                    val name = fn.optString("name").takeIf { it.isNotBlank() } ?: continue
                    call.optString("id").takeIf { it.isNotBlank() }?.let { toolCallNames[it] = name }
                    val arguments = fn.optString("arguments").ifBlank { "{}" }
                    if (arguments.length > InputLimits.API_MAX_TOOL_PARAMETER_CHARS) {
                        badRequest("Tool arguments are too large", ErrorCode.INVALID_VALUE, "messages")
                    }
                    val params = runCatching { JSONObject(arguments) }.getOrElse { JSONObject() }
                    if (text.isNotEmpty()) text.append('\n')
                    text.append("<tool_call>${JSONObject().put("tool", name).put("params", params)}</tool_call>")
                }
            }

            when (role) {
                "system", "developer" -> {
                    // `developer` is the newer spelling of the system role; both shape behavior, so
                    // both are collected into the one real system turn.
                    if (systemText.isNotEmpty()) systemText.append("\n\n")
                    systemText.append(text)
                }
                "tool", "function" -> {
                    val name = message.optString("tool_call_id").takeIf { it.isNotBlank() }?.let { toolCallNames[it] }
                        ?: message.optString("name").ifBlank { "tool" }
                    // Delivered as a user turn rather than a `tool` turn on purpose: the models
                    // this app runs learned the result format from this app's own prompt protocol
                    // ("Tool X result: …"), and not every GGUF chat template even defines a tool
                    // role — an undefined role makes llama.cpp's template application fail outright.
                    turns += "user" to "Tool $name result: $text"
                }
                "assistant" -> turns += "assistant" to text.toString()
                else -> {
                    lastUserText = text.toString()
                    turns += "user" to text.toString()
                }
            }
        }
        if (turns.isEmpty() && systemText.isEmpty()) {
            badRequest("messages must contain at least one non-empty message", ErrorCode.INVALID_VALUE, "messages")
        }
        if (images.size > 1) {
            // The engine bridge takes exactly one image path per generation
            // (`LlmEngine.generate(imagePath: String?)`), so a second image cannot be passed
            // through. Refusing beats quietly using one and discarding the rest, which is what
            // "last one wins" did.
            badRequest(
                "Only one image per request is supported by the on-device engines",
                ErrorCode.UNSUPPORTED_VALUE, "messages"
            )
        }

        val requestedModel = json.optString("model").ifBlank { null }
            ?.also { if (it.length > 128) badRequest("model is too long", ErrorCode.INVALID_VALUE, "model") }
        val model = runBlocking {
            if (requestedModel != null) {
                app.container.db.modelDao().observeModels().first()
                    .firstOrNull { it.displayName == requestedModel && it.role == ModelRole.GENERATION }
            } else {
                app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
            }
        } ?: if (requestedModel != null) {
            // A 404 with `model_not_found`, never a silent substitution — see the same reasoning
            // in handleEmbeddings.
            throw ApiRequestException(
                errorResponse(
                    Response.Status.NOT_FOUND, "No model named \"$requestedModel\" is installed",
                    ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, "model"
                )
            )
        } else {
            badRequest("No generation model available — set a default in Model Manager", ErrorCode.MODEL_NOT_FOUND, "model")
        }

        val requestedTemperature = json.optDouble("temperature").takeIf { !it.isNaN() }
            ?.also { if (!it.isFinite() || it !in 0.0..2.0) badRequest("temperature must be between 0 and 2", ErrorCode.INVALID_VALUE, "temperature") }
            ?.toFloat()
        val requestedTopP = json.optDouble("top_p").takeIf { !it.isNaN() }
            ?.also { if (!it.isFinite() || it !in 0.0..1.0) badRequest("top_p must be between 0 and 1", ErrorCode.INVALID_VALUE, "top_p") }
            ?.toFloat()
        val params = runBlocking {
            com.vervan.chat.llm.resolveGenerationParams(
                model, app.container.settingsRepository,
                chatTemperature = requestedTemperature,
                chatTopP = requestedTopP
            )
        }
        // `max_completion_tokens` is the current spelling; `max_tokens` is the deprecated one every
        // existing integration still sends. Either is honored, the newer one wins.
        val requestedMaxTokens = json.optInt("max_completion_tokens", -1).takeIf { it > 0 }
            ?: json.optInt("max_tokens", -1).takeIf { it > 0 }
        requestedMaxTokens?.let {
            if (it !in 16..32_768) badRequest("max output tokens must be between 16 and 32768", ErrorCode.INVALID_VALUE, "max_completion_tokens")
        }
        val stops = when (val stop = json.opt("stop")) {
            is String -> listOf(stop)
            is JSONArray -> (0 until stop.length()).mapNotNull { stop.optString(it, null) }
            else -> null
        }?.filter { it.isNotBlank() }?.also { values ->
            if (values.size > InputLimits.API_MAX_STOP_SEQUENCES || values.any { it.length > InputLimits.API_MAX_STOP_SEQUENCE_CHARS }) {
                badRequest("stop must contain at most 8 sequences of 256 characters", ErrorCode.INVALID_VALUE, "stop")
            }
        }
        // frequency_penalty is OpenAI's additive logit penalty; llama.cpp's repetition penalty is
        // multiplicative and centered on 1.0. There's no exact conversion, but mapping a non-zero
        // frequency_penalty onto `1 + fp/2` puts the usual 0..2 request range into the usual
        // 1.0..2.0 penalty range, which is far closer to the caller's intent than ignoring it.
        // An explicit repetition_penalty (this app's own field) always wins.
        val frequencyPenalty = json.optDouble("frequency_penalty").takeIf { !it.isNaN() }
            ?.also { if (!it.isFinite() || it !in 0.0..2.0) badRequest("frequency_penalty must be between 0 and 2", ErrorCode.INVALID_VALUE, "frequency_penalty") }
            ?.takeIf { it != 0.0 }
        val requestedMinP = json.optDouble("min_p").takeIf { !it.isNaN() }
            ?.also { if (!it.isFinite() || it !in 0.0..1.0) badRequest("min_p must be between 0 and 1", ErrorCode.INVALID_VALUE, "min_p") }
        val requestedRepetitionPenalty = json.optDouble("repetition_penalty").takeIf { !it.isNaN() }
            ?.also { if (!it.isFinite() || it !in 1.0..2.0) badRequest("repetition_penalty must be between 1 and 2", ErrorCode.INVALID_VALUE, "repetition_penalty") }
        val sampling = ApiChatRunner.Sampling(
            temperature = params.temperature,
            topP = params.topP,
            topK = params.topK,
            seed = json.optInt("seed", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } ?: params.seed,
            minP = requestedMinP?.toFloat() ?: params.minP,
            repetitionPenalty = requestedRepetitionPenalty?.toFloat()
                ?: frequencyPenalty?.let { (1.0 + it / 2.0).toFloat() }
                ?: params.repetitionPenalty,
            maxOutputTokens = requestedMaxTokens ?: params.maxOutputTokens,
            stopSequences = stops?.takeIf { it.isNotEmpty() } ?: params.stopSequences
        )

        json.optJSONArray("tools")?.let {
            if (it.length() > InputLimits.API_MAX_TOOLS) {
                badRequest("Too many client tools (maximum ${InputLimits.API_MAX_TOOLS})", ErrorCode.INVALID_VALUE, "tools")
            }
        }
        val clientTools = ApiChatRunner.parseClientTools(json.optJSONArray("tools"))
        val toolChoice = ApiChatRunner.parseToolChoice(json.opt("tool_choice"))
        val settings = app.container.settingsRepository
        // App tools are opt-in twice over: the user's Settings toggle, and the request itself
        // (`app_tools`, defaulting to the setting). A client that wants only its own tools can turn
        // them off per request without touching the phone.
        val appToolsSetting = runBlocking { settings.apiServerAppTools.first() }
        val appToolsEnabled = (if (json.has("app_tools")) json.optBoolean("app_tools", appToolsSetting) else appToolsSetting) &&
            toolChoice != ApiChatRunner.ToolChoice.None
        val enabledAppToolIds = if (!appToolsEnabled) emptySet() else runBlocking {
            val disabled = settings.disabledToolIds.first()
            val allowed = ToolRegistry.tools.map { it.name }.filter { it !in disabled }.toSet()
            // `enabled_tools` lets a caller narrow the set for one request — the per-chat tool
            // picker in the app, and the same picker in the web app. It is intersected with the
            // Settings-derived set rather than replacing it, so a request can only ever turn a tool
            // *off*: a client must not be able to re-enable something the user disabled on the
            // phone. Absent (or empty) means "everything the settings allow", unchanged.
            val requested = json.optJSONArray("enabled_tools")
                ?.also { if (it.length() > InputLimits.API_MAX_TOOL_IDS) badRequest("Too many enabled tools", ErrorCode.INVALID_VALUE, "enabled_tools") }
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it, null)?.takeIf { id -> id.length <= 128 } }.toSet() }
            if (requested.isNullOrEmpty()) allowed else allowed intersect requested
        }

        return ChatRequest(
            model = model,
            turns = turns,
            systemText = systemText.toString().trim(),
            lastUserText = lastUserText,
            imageDataUrl = images.firstOrNull(),
            audio = audio,
            stream = json.optBoolean("stream", false),
            includeUsage = json.optJSONObject("stream_options")?.optBoolean("include_usage", false) ?: false,
            sampling = sampling,
            thinkingMode = resolveThinkingMode(json, model),
            clientTools = clientTools,
            toolChoice = toolChoice,
            appToolsEnabled = appToolsEnabled,
            allowWriteTools = runBlocking { settings.apiServerAllowWriteTools.first() },
            enabledAppToolIds = enabledAppToolIds,
            knowledgeBaseIds = json.optJSONArray("knowledge_base_ids")
                ?.also { if (it.length() > InputLimits.API_MAX_TOOL_IDS) badRequest("Too many knowledge base ids", ErrorCode.INVALID_VALUE, "knowledge_base_ids") }
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it, null)?.takeIf { id -> id.length <= 128 } } }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            chatId = json.optString("chat_id").ifBlank { null }.takeIf { fullMode },
            responseFormatInstruction = responseFormatInstruction(json)
        )
    }

    /**
     * Parameters that are part of the spec but that this server cannot honor. Rejecting them is
     * deliberate: a caller who asks for five completions, or for logprobs, and silently gets one
     * completion with no logprobs has no way to notice. Anything that *can* be honored (or that is
     * a no-op at its default value) is accepted instead — `n: 1`, `presence_penalty: 0` and
     * friends never reach here.
     */
    private fun rejectUnsupportedParams(json: JSONObject) {
        json.optInt("n", 1).let {
            if (it != 1) badRequest("Only n=1 is supported — this device generates one completion at a time", ErrorCode.UNSUPPORTED_VALUE, "n")
        }
        if (json.optBoolean("logprobs", false) || (json.has("top_logprobs") && !json.isNull("top_logprobs"))) {
            badRequest("logprobs are not available from the on-device engines", ErrorCode.UNSUPPORTED_PARAMETER, "logprobs")
        }
        json.optJSONObject("logit_bias")?.takeIf { it.length() > 0 }?.let {
            badRequest("logit_bias is not supported by the on-device engines", ErrorCode.UNSUPPORTED_PARAMETER, "logit_bias")
        }
    }

    /** Turns `response_format` into a prompt instruction. Neither on-device engine supports
     * grammar-constrained decoding through this app's bridge, so this is a strong request rather
     * than a hard guarantee — which is why the schema is included verbatim: a model that can see
     * the shape it must produce complies far more reliably than one told merely "return JSON". */
    private fun responseFormatInstruction(json: JSONObject): String? {
        val format = json.optJSONObject("response_format") ?: return null
        return when (format.optString("type")) {
            "json_object" -> "Respond with a single valid JSON object and nothing else — no prose, no code fences."
            "json_schema" -> {
                val schema = format.optJSONObject("json_schema")?.optJSONObject("schema")
                "Respond with a single valid JSON object and nothing else — no prose, no code fences." +
                    (schema?.let { " It must conform to this JSON Schema:\n$it" } ?: "")
            }
            "text", "" -> null
            else -> badRequest(
                "response_format.type must be \"text\", \"json_object\" or \"json_schema\"",
                ErrorCode.UNSUPPORTED_VALUE, "response_format"
            )
        }
    }

    /** Resolves how much the model should think. `reasoning_effort` is OpenAI's own field;
     * `thinking` is this app's, taking the same OFF/FAST/BALANCED/DEEP values the Configure screen
     * uses. Neither given means the model's own default, and [ThinkingPolicy.effectiveThinkingMode]
     * forces OFF for a model whose Thinking capability is off, exactly as in the native chat. */
    private fun resolveThinkingMode(json: JSONObject, model: com.vervan.chat.data.db.entities.ModelInfo): String {
        val requested = json.optString("thinking").ifBlank { null }?.uppercase()
            ?: when (json.optString("reasoning_effort").lowercase()) {
                "none" -> "OFF"
                "minimal", "low" -> "FAST"
                "medium" -> "BALANCED"
                "high" -> "DEEP"
                else -> null
            }
        if (requested != null && requested !in ThinkingPolicy.MODES) {
            badRequest("thinking must be one of ${ThinkingPolicy.MODES}", ErrorCode.UNSUPPORTED_VALUE, "thinking")
        }
        return ThinkingPolicy.effectiveThinkingMode(requested, model.defaultThinkingMode, model.supportsThinking)
    }

    /** The flattened "User: …\nAssistant:" prompt, for the two engines that don't take a turn list
     * (LiteRT-LM keeps its own Conversation object; the remote engine re-templates server-side).
     * The llama.cpp path ignores this in favor of the real turns — see `VervanApp.generate`. */
    private fun buildFlatPrompt(turns: List<Pair<String, String>>): String = buildString {
        turns.forEach { (role, content) ->
            appendLine("${role.replaceFirstChar(Char::uppercase)}: $content")
        }
        append("Assistant:")
    }

    /**
     * OpenAPI 3.1 description of this server, built at request time so it reflects the *running*
     * configuration — `full_mode` decides whether the `/api/` app-data surface exists at all, and
     * a spec that advertised endpoints this instance 404s would be worse than none.
     * (Note: no `/api` glob in this comment on purpose — Kotlin block comments nest, so a literal
     * slash-star-star sequence in KDoc opens a nested comment and swallows the rest of the file.)
     *
     * Hand-built rather than generated: there is no annotation-driven spec pipeline in this project,
     * and the endpoint list is small and changes rarely. Describes shapes callers actually branch on
     * (auth, streaming, the extension fields) instead of every optional sampling knob.
     */
    private fun openApiSpec(): JSONObject {
        fun schema(vararg pairs: Pair<String, Any>) = JSONObject().apply { pairs.forEach { put(it.first, it.second) } }
        fun jsonBody(schemaObj: JSONObject) = JSONObject()
            .put("required", true)
            .put("content", JSONObject().put("application/json", JSONObject().put("schema", schemaObj)))
        fun okJson(description: String) = JSONObject().put("200", JSONObject().put("description", description))

        val paths = JSONObject()

        paths.put("/health", JSONObject().put("get", JSONObject()
            .put("summary", "Liveness probe; also reports whether full web-app mode is on")
            .put("security", JSONArray())
            .put("responses", okJson("Server status"))))

        paths.put("/v1/models", JSONObject().put("get", JSONObject()
            .put("summary", "List installed models, with load state and TTL")
            .put("responses", okJson("Model list"))))

        paths.put("/v1/chat/completions", JSONObject().put("post", JSONObject()
            .put("summary", "Chat completion (streaming or blocking)")
            .put(
                "description",
                "OpenAI-compatible. Extensions beyond the standard body: `thinking` " +
                    "(off/fast/balanced/deep), `app_tools` (run this device's own tools in-process), " +
                    "`enabled_tools` (narrow that set for one request — it can only ever remove " +
                    "tools the user allowed on the device), `knowledge_base_ids` (RAG), and " +
                    "`chat_id` (full mode only: continue a stored conversation, so the server " +
                    "supplies that chat's persona, history and knowledge bases itself). Reasoning " +
                    "is returned as `reasoning_content`."
            )
            .put("requestBody", jsonBody(schema(
                "type" to "object",
                "required" to JSONArray(listOf("messages")),
                "properties" to schema(
                    "model" to schema("type" to "string"),
                    "messages" to schema("type" to "array", "items" to schema("type" to "object")),
                    "stream" to schema("type" to "boolean"),
                    "tools" to schema("type" to "array", "items" to schema("type" to "object")),
                    "tool_choice" to schema("oneOf" to JSONArray(listOf(schema("type" to "string"), schema("type" to "object")))),
                    "response_format" to schema("type" to "object"),
                    "thinking" to schema("type" to "string", "enum" to JSONArray(listOf("off", "fast", "balanced", "deep"))),
                    "app_tools" to schema("type" to "boolean"),
                    "enabled_tools" to schema("type" to "array", "items" to schema("type" to "string")),
                    "knowledge_base_ids" to schema("type" to "array", "items" to schema("type" to "string")),
                    "chat_id" to schema("type" to "string")
                )
            )))
            .put("responses", JSONObject()
                .put("200", JSONObject().put("description", "Completion, or an SSE stream when `stream` is true"))
                .put("401", JSONObject().put("description", "Missing or wrong API key"))
                .put("404", JSONObject().put("description", "Named model is not installed"))
                .put("429", JSONObject().put("description", "Too many concurrent generations on this device")))))

        paths.put("/v1/completions", JSONObject().put("post", JSONObject()
            .put("summary", "Legacy text completion")
            .put("requestBody", jsonBody(schema("type" to "object")))
            .put("responses", okJson("Completion"))))

        paths.put("/v1/embeddings", JSONObject().put("post", JSONObject()
            .put("summary", "Embeddings; `encoding_format` accepts float or base64")
            .put("requestBody", jsonBody(schema("type" to "object")))
            .put("responses", okJson("Embedding vectors"))))

        paths.put("/v1/audio/transcriptions", JSONObject().put("post", JSONObject()
            .put("summary", "Speech to text (multipart `file`, or JSON base64 audio)")
            .put("responses", okJson("Transcript"))))

        paths.put("/v1/audio/speech", JSONObject().put("post", JSONObject()
            .put("summary", "Text to speech; returns wav or pcm")
            .put("requestBody", jsonBody(schema("type" to "object")))
            .put("responses", okJson("Audio"))))

        if (fullMode) {
            // Only advertised in full mode — these read and write the user's own data, and in basic
            // mode every /api/** path deliberately 404s.
            listOf(
                "/api/chats" to "List or create chats",
                "/api/chat" to "Fetch one chat's configuration",
                "/api/messages" to "List a chat's messages",
                "/api/knowledge-bases" to "List or create knowledge bases",
                "/api/documents" to "List or upload knowledge-base documents",
                "/api/personas" to "List or save personas (built-ins are read-only)",
                "/api/templates" to "List or save prompt templates (built-ins are read-only)",
                "/api/workspaces" to "List or save workspaces (the default workspace is read-only)",
                "/api/memories" to "List or save memories",
                "/api/tools/run" to "Run one of this device's tools directly",
                "/api/models" to "List models, with load/unload and default-model actions"
            ).forEach { (path, summary) ->
                paths.put(path, JSONObject().put("get", JSONObject()
                    .put("summary", summary)
                    .put("responses", okJson("Result"))))
            }
        }

        return JSONObject()
            .put("openapi", "3.1.0")
            .put(
                "info", JSONObject()
                    .put("title", "Vervan on-device API")
                    .put("version", com.vervan.chat.BuildConfig.VERSION_NAME)
                    .put(
                        "description",
                        "OpenAI-compatible inference served from this Android device. All generation, " +
                            "retrieval and speech runs locally. " +
                            (if (fullMode) "Full web-app mode is ON, so the /api/** app-data endpoints are live."
                            else "Full web-app mode is OFF, so only the /v1/** inference endpoints are served.")
                    )
            )
            .put("servers", JSONArray().put(JSONObject().put("url", "/")))
            .put(
                "components", JSONObject().put(
                    "securitySchemes", JSONObject().put(
                        "bearerAuth", JSONObject()
                            .put("type", "http").put("scheme", "bearer")
                            .put("description", "The API key from Settings → API server. Omitted only when no key is set.")
                    )
                )
            )
            .put("security", JSONArray().put(JSONObject().put("bearerAuth", JSONArray())))
            .put("paths", paths)
    }

    /** The stored-chat context a `chat_id` request should generate against. */
    private class ChatContext(
        /** Persona instruction + project instructions, prepended to the client's system text. */
        val systemPrefix: String,
        /** Prior conversation on the active branch, oldest first, as (role, content) turns. */
        val priorTurns: List<Pair<String, String>>,
        /** The chat's own knowledge bases, unioned with any the caller named. */
        val kbIds: List<String>
    )

    /**
     * Rebuilds what the native chat would have had in context for [chatId]. Resolves the persona
     * through the same [ChatDefaults] chain the app uses (chat → folder → workspace → built-in), so
     * a persona picked in either place takes effect in both and the two can't drift.
     *
     * History is read on the *active branch* ([BranchUtil.pathTo]) and trimmed to the context
     * budget, exactly as the native loop does — a browser tab must not be able to push a huge chat
     * past the model's window just because it isn't the one doing the trimming.
     *
     * Deliberately excludes the newest user turn: the client sends that in `messages[]` and the
     * server only persists it after generation, so the DB holds prior turns only and appending the
     * request's own turns after these can't double it up.
     */
    private suspend fun chatContext(chatId: String, requestedKbIds: List<String>): ChatContext? {
        val db = app.container.db
        val chat = db.chatDao().getChat(chatId) ?: return null
        val folder = chat.folderId?.let { runCatching { db.folderDao().get(it) }.getOrNull() }
        val workspace = runCatching { db.workspaceDao().get(chat.workspaceId) }.getOrNull()

        val personaId = com.vervan.chat.model.ChatDefaults.personaId(chat, folder, workspace)
        val persona = runCatching { db.personaDao().getPersona(personaId) }.getOrNull()
        val projectInstructions = chat.projectId?.let { runCatching { db.projectDao().get(it)?.instructions }.getOrNull() }

        val systemPrefix = buildString {
            persona?.systemInstruction?.takeIf { it.isNotBlank() }?.let { instruction ->
                append(instruction)
                val traits = com.vervan.chat.data.repo.PersonaTraits.instructionFor(persona)
                if (traits.isNotBlank()) append('\n').append(traits)
            }
            projectInstructions?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
        }.trim()

        val history = com.vervan.chat.data.branch.BranchUtil
            .pathTo(db.messageDao().getMessages(chatId), chat.activeLeafId)
            .filter {
                it.role != com.vervan.chat.data.db.entities.MessageRole.SYSTEM &&
                    it.content.isNotBlank() &&
                    it.state == com.vervan.chat.data.db.entities.MessageState.COMPLETE
            }
        val contextLimit = runCatching { app.container.settingsRepository.contextTokenLimit.first() }.getOrDefault(4096)
        val priorTurns = ChatFormatting.trimHistoryToBudget(history, contextLimit).map { message ->
            val role = if (message.role == com.vervan.chat.data.db.entities.MessageRole.USER) "user" else "assistant"
            role to message.content
        }

        // Union, not replace: the page's picker can add a knowledge base for one question without
        // silently dropping the ones the chat was configured with on the phone.
        val kbIds = (chat.kbIdList() + requestedKbIds).distinct().filter { it.isNotBlank() }
        return ChatContext(systemPrefix, priorTurns, kbIds)
    }

    /** [com.vervan.chat.ui.chat.ChatViewModel]'s own retrieveSourcesInner, minimized: no query
     * expansion (needs a resident LLM this server call has no reason to also load) and no
     * settings-driven mode override beyond the user's plain default — just "load the embedding
     * model, retrieve, fall back to an overview skim if nothing matched" exactly as the native
     * chat does for the same two calls. */
    private suspend fun performRetrieval(kbIds: List<String>, query: String): List<com.vervan.chat.retrieval.SourcePassage> {
        val loaded = app.container.modelLoadCoordinator.ensureLoaded(ModelRole.EMBEDDING, LoadTrigger.API_REQUEST)
        if (!loaded.success) return emptyList()
        val mode = runCatching {
            RetrievalMode.valueOf(app.container.settingsRepository.defaultRetrievalMode.first())
        }.getOrDefault(RetrievalMode.HYBRID)
        // No app.container.withEmbedding{} wrap — see ChatViewModel.retrieveSourcesInner's comment.
        // retrieve()/retrieveOverviewFallback() already lock embeddingMutex themselves, only for
        // the actual embed call; wrapping the outside in that same non-reentrant mutex again
        // deadlocks instead of erroring.
        val direct = app.container.retrievalEngine.retrieve(kbIds, query, mode, RAG_TOP_K)
        return direct.ifEmpty { app.container.retrievalEngine.retrieveOverviewFallback(kbIds, RAG_TOP_K) }
    }

    /** Decodes a `data:image/...;base64,...` (or bare base64) image into a normalized temp JPEG
     * under app.cacheDir — same [ImageUtils.normalizeForModel] pass (EXIF-orient, downscale,
     * re-encode) the native import pipeline applies to every attached image, so a browser-side
     * photo gets identical treatment to one picked from the device gallery. Caller owns deleting
     * the returned file once generation is done (see `cleanupAttachments` at the call site). */
    private fun decodeImageAttachment(dataUrl: String): File? {
        val base64 = dataUrl.substringAfter(",", dataUrl)
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val dir = File(app.cacheDir, "server-uploads").apply { mkdirs() }
        val file = File(dir, "img-${UUID.randomUUID()}.jpg")
        return try {
            file.writeBytes(bytes)
            if (ImageUtils.normalizeForModel(file)) file else { file.delete(); null }
        } catch (t: Throwable) {
            file.delete()
            null
        }
    }

    /** Decodes base64 audio (whatever container the browser's `MediaRecorder` produced — webm/
     * opus by default, anything Android's own [android.media.MediaCodec] can decode) into the
     * 16 kHz mono PCM WAV the local audio-input path expects, via
     * [com.vervan.chat.audio.AudioNormalizer] — the exact same normalizer the on-device document/
     * voice-import pipeline uses, so the browser doesn't need to encode a specific format itself. */
    private fun decodeAudioAttachment(base64: String, format: String): File? {
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val dir = File(app.cacheDir, "server-uploads").apply { mkdirs() }
        val ext = format.lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "webm" }
        val rawFile = File(dir, "audio-src-${UUID.randomUUID()}.$ext")
        val wavFile = File(dir, "audio-${UUID.randomUUID()}.wav")
        return try {
            rawFile.writeBytes(bytes)
            AudioNormalizer.normalize(app, Uri.fromFile(rawFile), wavFile)
        } catch (t: Throwable) {
            Log.w(TAG, "decodeAudioAttachment(): normalize failed", t)
            wavFile.delete()
            null
        } finally {
            rawFile.delete()
        }
    }

    // ---- /api/* — full-mode-only app-data surface (knowledge bases + documents) ---------------

    private fun handleListKnowledgeBases(): Response = runBlocking {
        val kbs = app.container.db.knowledgeBaseDao().observeAll().first()
        val data = JSONArray()
        kbs.forEach { kb ->
            // No dedicated COUNT(*) query per KB (see DocumentDao) — getForKb already excludes
            // soft-deleted rows, and a personal knowledge base is small enough that this full
            // fetch-then-.size is cheap; not worth a new DAO query for a UI-only count.
            val count = app.container.db.documentDao().getForKb(kb.id).size
            data.put(
                JSONObject()
                    .put("id", kb.id)
                    .put("name", kb.name)
                    .put("description", kb.description)
                    .put("icon", kb.icon)
                    .put("document_count", count)
            )
        }
        jsonResponse(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
    }

    private fun handleCreateKnowledgeBase(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val name = json.optString("name").trim()
        if (name.isEmpty() || name.length > 200) {
            return errorResponse(Response.Status.BAD_REQUEST, "name must be 1-200 characters")
        }
        val description = json.optString("description").take(2000)
        return runBlocking {
            // Same endpoint creates and renames: an `id` that already exists is an edit, keeping
            // the row's icon/colour/defaults rather than replacing it with a fresh KnowledgeBase.
            // Without this the web app could create and delete a knowledge base but never rename
            // one, which the app itself allows.
            val existing = json.optString("id").takeIf { it.isNotBlank() }
                ?.let { app.container.db.knowledgeBaseDao().get(it) }
            val kb = (existing ?: KnowledgeBase(name = name)).copy(name = name, description = description)
            app.container.db.knowledgeBaseDao().upsert(kb)
            val count = app.container.db.documentDao().getForKb(kb.id).size
            jsonResponse(
                Response.Status.OK,
                JSONObject().put("id", kb.id).put("name", kb.name).put("description", kb.description)
                    .put("document_count", count)
            )
        }
    }

    private fun handleListDocuments(session: IHTTPSession): Response {
        val kbId = session.parameters["kb"]?.firstOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "kb query parameter is required")
        return runBlocking {
            val docs = app.container.db.documentDao().getForKb(kbId)
            val data = JSONArray()
            docs.forEach { doc ->
                data.put(
                    JSONObject()
                        .put("id", doc.id)
                        .put("display_name", doc.displayName)
                        .put("mime_type", doc.mimeType)
                        .put("status", doc.status.name.lowercase())
                        .put("failure_reason", doc.failureReason ?: JSONObject.NULL)
                        .put("imported_at", doc.importedAt)
                        .put("ocr_applied", doc.ocrApplied)
                )
            }
            jsonResponse(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
        }
    }

    /** Body: `{"knowledge_base_id","name","mime_type","data"}` — `data` is base64, not multipart.
     * `DocumentImportManager.import()` needs a real [Uri][android.net.Uri], not bytes, so this
     * stages the decoded content under a fresh per-upload temp directory *named after the
     * original file* first (so the manager's own [Uri]-based display-name/size lookups — which
     * work against `file://` through [android.content.ContentResolver] the same as `content://`
     * — resolve to the name the user actually uploaded, not a generated temp name) and hands it
     * a `Uri.fromFile(...)` — no FileProvider authority needed since this all stays in-process. */
    private fun handleUploadDocument(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_DOCUMENT_UPLOAD_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val kbId = json.optString("knowledge_base_id").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "knowledge_base_id is required")
        val name = json.optString("name").ifBlank { "document" }
        val base64 = json.optString("data").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "data (base64) is required")
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "data must be valid base64")
        if (bytes.isEmpty()) return errorResponse(Response.Status.BAD_REQUEST, "Uploaded file is empty")

        return runBlocking {
            if (app.container.db.knowledgeBaseDao().get(kbId) == null) {
                return@runBlocking errorResponse(Response.Status.BAD_REQUEST, "Unknown knowledge_base_id")
            }
            val safeName = name.replace(Regex("[/\\\\]"), "_").ifBlank { "document" }
                .let { if (it == ".." || it == ".") "document" else it }
            val uploadDir = File(app.cacheDir, "server-uploads/${UUID.randomUUID()}").apply { mkdirs() }
            val tempFile = File(uploadDir, safeName)
            try {
                withContext(Dispatchers.IO) { tempFile.writeBytes(bytes) }
                when (val outcome = app.container.documentImportManager.import(kbId, Uri.fromFile(tempFile))) {
                    is DocumentImportOutcome.Imported -> jsonResponse(
                        Response.Status.OK,
                        JSONObject()
                            .put("id", outcome.document.id)
                            .put("display_name", outcome.document.displayName)
                            .put("status", outcome.document.status.name.lowercase())
                            .put("failure_reason", outcome.document.failureReason ?: JSONObject.NULL)
                    )
                    is DocumentImportOutcome.Duplicate -> jsonResponse(
                        Response.Status.OK,
                        JSONObject()
                            .put("id", outcome.existing.id)
                            .put("display_name", outcome.existing.displayName)
                            .put("status", outcome.existing.status.name.lowercase())
                            .put("duplicate", true)
                    )
                    // A version conflict (same name+content-changed) needs an explicit user
                    // decision in the native UI (keep both / replace) — the web API has no such
                    // affordance yet, so it reports the conflict rather than silently picking a
                    // side, and leaves the existing document untouched.
                    is DocumentImportOutcome.VersionConflict -> {
                        File(outcome.tempFilePath).delete()
                        errorResponse(
                            Response.Status.CONFLICT,
                            "\"${outcome.existing.displayName}\" already exists with different content — delete it first, or rename the upload"
                        )
                    }
                }
            } finally {
                uploadDir.deleteRecursively()
            }
        }
    }

    private fun handleDeleteDocument(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val id = json.optString("id").ifBlank { null } ?: return errorResponse(Response.Status.BAD_REQUEST, "id is required")
        return runBlocking {
            val doc = app.container.db.documentDao().get(id) ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Document not found")
            app.container.documentImportManager.delete(doc)
            jsonResponse(Response.Status.OK, JSONObject().put("deleted", true))
        }
    }

    // ---- /api/chats, /api/messages — persists web-originated conversations into the same
    // `chats`/`messages` tables the native app itself reads, so a chat you have in the browser
    // shows up in the app's own chat list afterward (not a separate, web-only history) and vice
    // versa: `handleListChats`/`handleListMessages` list every chat, native-app-created ones
    // included, not just ones the web UI itself started. -----------------------------------------

    /** `?limit=&offset=` — a phone's chat history grows without bound, and the web app's sidebar
     * only ever shows a page of it. Defaults keep the previous "first 100, newest first" behavior
     * for a caller that passes neither. */
    private fun handleListChats(session: IHTTPSession): Response = runBlocking {
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        val offset = session.parameters["offset"]?.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val all = app.container.db.chatDao().observeListableChats().first()
        val page = all.drop(offset).take(limit)
        // One query for every listed chat's count, instead of one per chat inside the loop.
        val counts = page.associate { it.id to app.container.db.messageDao().countForChat(it.id) }
        val data = JSONArray()
        page.forEach { chat ->
            data.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("updated_at", chat.updatedAt)
                    .put("pinned", chat.pinned)
                    .put("archived", chat.archived)
                    .put("message_count", counts[chat.id] ?: 0)
            )
        }
        jsonResponse(
            Response.Status.OK,
            JSONObject().put("object", "list").put("data", data)
                .put("total", all.size).put("offset", offset).put("limit", limit)
        )
    }

    /** Rename / pin / archive. Every field is optional; only the ones present are changed, so the
     * web app can send just what the user touched. */
    private fun handleUpdateChat(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val id = json.optString("id").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "id is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "id")
        return runBlocking {
            val chat = app.container.db.chatDao().getChat(id)
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Chat not found", ErrorType.NOT_FOUND, null, "id")
            val title = json.optString("title").trim().takeIf { json.has("title") && it.isNotEmpty() }?.take(200)

            // Every field below is one the app's own Chat screen and Chat info screen can change.
            // Restricting the web app to title/pin/archive made its model, persona, thinking and
            // knowledge-base pickers per-request decorations that vanished on reload — the chat's
            // real configuration lives on the row, so this is what makes those controls mean
            // anything. `json.has(...)` throughout: absent means "leave alone", explicit null means
            // "clear", which is how a persona or folder gets unset rather than only ever set.
            fun optRef(key: String, current: String?): String? =
                if (!json.has(key)) current else json.optString(key).trim().ifBlank { null }
            fun optFloat(key: String, current: Float?): Float? =
                if (!json.has(key)) current else json.optDouble(key).takeIf { !it.isNaN() }?.toFloat()

            val requestedProfile = optRef("profile", chat.profile)?.uppercase()
            if (json.has("profile") && requestedProfile != null &&
                com.vervan.chat.llm.ModelProfileType.entries.none { it.id == requestedProfile }
            ) {
                return@runBlocking errorResponse(
                    Response.Status.BAD_REQUEST, "Unknown profile \"$requestedProfile\"",
                    ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "profile"
                )
            }
            val requestedThinking = optRef("thinking_mode", chat.thinkingMode)?.uppercase()
            if (json.has("thinking_mode") && requestedThinking != null &&
                requestedThinking !in ThinkingPolicy.MODES
            ) {
                return@runBlocking errorResponse(
                    Response.Status.BAD_REQUEST, "thinking_mode must be one of ${ThinkingPolicy.MODES}",
                    ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "thinking_mode"
                )
            }
            // A model id that does not exist would leave the chat pointing at nothing and fail at
            // send time with a far less obvious message.
            val requestedModelId = optRef("model_id", chat.modelId)
            if (json.has("model_id") && requestedModelId != null &&
                app.container.db.modelDao().get(requestedModelId) == null
            ) {
                return@runBlocking errorResponse(
                    Response.Status.NOT_FOUND, "No model with id \"$requestedModelId\"",
                    ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, "model_id"
                )
            }

            val updated = chat.copy(
                title = title ?: chat.title,
                // A rename from the web app is as deliberate as one from the phone, so it has to
                // set titleIsCustom too — otherwise the next persisted turn's auto-title would
                // overwrite the name the user just chose.
                titleIsCustom = if (title != null) true else chat.titleIsCustom,
                pinned = if (json.has("pinned")) json.optBoolean("pinned", chat.pinned) else chat.pinned,
                archived = if (json.has("archived")) json.optBoolean("archived", chat.archived) else chat.archived,
                // Incognito. Same semantics as the phone's own toggle: excluded from backup,
                // search and memory suggestions, and hard-deleted rather than binned on close.
                isTemporary = if (json.has("is_temporary")) json.optBoolean("is_temporary", chat.isTemporary) else chat.isTemporary,
                // The composer's unsent text, so an interrupted message is still there when the
                // chat is reopened — on the phone as well as in another browser tab.
                draft = if (json.has("draft")) json.optString("draft").take(20_000) else chat.draft,
                personaId = optRef("persona_id", chat.personaId),
                modelId = requestedModelId,
                projectId = optRef("project_id", chat.projectId),
                folderId = optRef("folder_id", chat.folderId),
                profile = requestedProfile ?: chat.profile,
                thinkingMode = requestedThinking,
                toolsEnabled = if (json.has("tools_enabled")) json.optBoolean("tools_enabled", chat.toolsEnabled) else chat.toolsEnabled,
                sourceGrounded = if (json.has("source_grounded")) json.optBoolean("source_grounded", chat.sourceGrounded) else chat.sourceGrounded,
                knowledgeBaseIds = if (!json.has("knowledge_base_ids")) chat.knowledgeBaseIds else {
                    json.optJSONArray("knowledge_base_ids")
                        ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it, null) }.filter { it.isNotBlank() } }
                        .orEmpty().joinToString(",")
                },
                // Per-chat tool overrides, as {"tool_name": true|false}. "true" forces a tool on for
                // this chat even when globally disabled, "false" forces it off even when globally
                // enabled, and an absent name inherits the Settings value — the same three-state
                // model as the app's own per-chat tool list, which is why this is a map rather than
                // a plain list of enabled names.
                toolOverrides = if (!json.has("tool_overrides")) chat.toolOverrides else {
                    val overrides = json.optJSONObject("tool_overrides")
                    if (overrides == null) "" else {
                        com.vervan.chat.data.db.entities.Chat.encodeToolOverrides(
                            overrides.keys().asSequence()
                                .filter { ToolRegistry.find(it) != null }
                                .associateWith { overrides.optBoolean(it) }
                        )
                    }
                },
                temperature = optFloat("temperature", chat.temperature),
                topP = optFloat("top_p", chat.topP),
                topK = if (!json.has("top_k")) chat.topK else json.optInt("top_k", -1).takeIf { it > 0 },
                updatedAt = System.currentTimeMillis()
            )
            app.container.db.chatDao().upsert(updated)
            jsonResponse(Response.Status.OK, chatConfigJson(updated))
        }
    }

    /** Soft delete, matching what the app's own swipe-to-delete does: the row keeps its
     * `deletedAt` stamp so it lands in Recently deleted and the existing retention sweep
     * ([com.vervan.chat.data.db.dao.ChatDao.purgeDeletedBefore]) is what finally removes it. A hard
     * delete from the web would bypass the undo the phone UI promises. */
    private fun handleDeleteChat(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val id = json.optString("id").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "id is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "id")
        return runBlocking {
            val chat = app.container.db.chatDao().getChat(id)
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Chat not found", ErrorType.NOT_FOUND, null, "id")
            app.container.db.chatDao().upsert(chat.copy(deletedAt = System.currentTimeMillis()))
            jsonResponse(Response.Status.OK, JSONObject().put("deleted", true).put("id", id))
        }
    }

    private fun handleDeleteMessage(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val id = json.optString("id").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "id is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "id")
        return runBlocking {
            val messages = app.container.db.messageDao()
            val chatId = json.optString("chat_id").ifBlank { null }
            val target = (chatId?.let { messages.getMessages(it) } ?: emptyList()).firstOrNull { it.id == id }
                ?: return@runBlocking errorResponse(
                    Response.Status.NOT_FOUND,
                    "Message not found — pass chat_id alongside id",
                    ErrorType.NOT_FOUND, null, "id"
                )
            messages.delete(target)
            // Deleting a leaf moves the chat's active leaf back to the message's parent, so the
            // native app's BranchUtil walk doesn't end up pointing at a row that no longer exists.
            val chat = app.container.db.chatDao().getChat(target.chatId)
            if (chat != null && chat.activeLeafId == target.id) {
                app.container.db.chatDao().upsert(chat.copy(activeLeafId = target.parentId, updatedAt = System.currentTimeMillis()))
            }
            jsonResponse(Response.Status.OK, JSONObject().put("deleted", true).put("id", id))
        }
    }

    /**
     * Serves one stored attachment's bytes. Two guards matter here: the requested id has to be a
     * real message row (the path never comes from the request — only the message id does, so no
     * caller can name an arbitrary file), and the resolved file has to sit inside this app's own
     * attachment directory, which catches a path stored by an older/odd code path from turning
     * into an arbitrary-file read.
     */
    private fun handleAttachment(session: IHTTPSession): Response {
        val messageId = session.parameters["message_id"]?.firstOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "message_id query parameter is required")
        val chatId = session.parameters["chat_id"]?.firstOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "chat_id query parameter is required")
        val kind = session.parameters["kind"]?.firstOrNull() ?: "image"
        return runBlocking {
            val message = app.container.db.messageDao().getMessages(chatId).firstOrNull { it.id == messageId }
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Message not found", ErrorType.NOT_FOUND, null, null)
            val path = when (kind) {
                "image" -> message.imagePath
                "audio" -> message.audioPath
                "voice" -> message.voiceRecordingPath
                else -> return@runBlocking errorResponse(Response.Status.BAD_REQUEST, "kind must be image, audio or voice")
            } ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "This message has no $kind attachment", ErrorType.NOT_FOUND, null, "kind")
            val file = File(path)
            val allowedRoots = listOf(File(app.filesDir, "message-attachments"), app.cacheDir, app.filesDir)
            val canonical = runCatching { file.canonicalFile }.getOrNull()
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Attachment file is missing", ErrorType.NOT_FOUND, null, null)
            val insideAppStorage = allowedRoots.any { root ->
                runCatching { canonical.path.startsWith(root.canonicalFile.path + File.separator) }.getOrDefault(false)
            }
            if (!insideAppStorage || !canonical.isFile) {
                return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Attachment file is missing", ErrorType.NOT_FOUND, null, null)
            }
            val mime = when {
                kind != "image" -> "audio/wav"
                canonical.name.endsWith(".png", ignoreCase = true) -> "image/png"
                canonical.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
            newFixedLengthResponse(Response.Status.OK, mime, canonical.inputStream(), canonical.length()).also {
                // Attachments are immutable once written (a new turn writes a new file), so the
                // browser can cache them for as long as it likes.
                it.addHeader("Cache-Control", "private, max-age=31536000, immutable")
            }
        }
    }

    /** What the model can be asked to run on this device, and whether the API is currently allowed
     * to run it — so the web app can show the same tool list the phone's Tools screen does instead
     * of guessing. */
    private fun handleListTools(): Response = runBlocking {
        val disabled = app.container.settingsRepository.disabledToolIds.first()
        val appToolsOn = app.container.settingsRepository.apiServerAppTools.first()
        val writesOn = app.container.settingsRepository.apiServerAllowWriteTools.first()
        val data = JSONArray()
        ToolRegistry.tools.forEach { tool ->
            data.put(
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put("category", tool.category.label)
                    .put("risk", tool.risk.name)
                    .put("enabled", tool.name !in disabled)
                    // The web app builds a run form from these, so a tool is usable from the
                    // browser without the caller having to know its signature in advance.
                    .put("parameters", JSONArray(tool.paramNames))
                    .put(
                        "callable_over_api",
                        appToolsOn && tool.name !in disabled &&
                            (tool.risk == com.vervan.chat.tools.ToolRisk.READ_ONLY || writesOn)
                    )
            )
        }
        jsonResponse(
            Response.Status.OK,
            JSONObject().put("object", "list").put("data", data)
                .put("app_tools_enabled", appToolsOn).put("write_tools_allowed", writesOn)
        )
    }

    /** Everything currently talking to this server — the same list the Settings screen shows, so a
     * browser session can see its own footprint (and anyone else's) without reaching for the phone. */
    private fun handleListClients(): Response {
        val data = JSONArray()
        app.container.apiClientRegistry.clients.value.forEach { client ->
            data.put(
                JSONObject()
                    .put("address", client.address)
                    .put("user_agent", client.userAgent)
                    .put("first_seen_at", client.firstSeenAt)
                    .put("last_seen_at", client.lastSeenAt)
                    .put("request_count", client.requestCount)
                    .put("last_path", client.lastPath)
                    .put("unauthorized_count", client.unauthorizedCount)
                    .put("authenticated", client.authenticated)
                    .put("local", client.isLocal)
            )
        }
        return jsonResponse(
            Response.Status.OK,
            JSONObject().put("object", "list").put("data", data).put("requires_api_key", requireAuth)
        )
    }

    private fun handleDeleteKnowledgeBase(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val id = json.optString("id").ifBlank { null }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "id is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "id")
        return runBlocking {
            val kb = app.container.db.knowledgeBaseDao().get(id)
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Knowledge base not found", ErrorType.NOT_FOUND, null, "id")
            // Documents go through the import manager's own delete so their chunks, embeddings and
            // on-disk copies are cleaned up too — deleting the parent row alone would orphan all
            // three.
            app.container.db.documentDao().getForKb(id).forEach { app.container.documentImportManager.delete(it) }
            app.container.db.knowledgeBaseDao().delete(kb)
            jsonResponse(Response.Status.OK, JSONObject().put("deleted", true).put("id", id))
        }
    }

    /** A chat's full editable configuration — the same set [handleUpdateChat] accepts, so the web
     * app can round-trip it rather than guessing which of its controls actually stuck. */
    private fun chatConfigJson(chat: com.vervan.chat.data.db.entities.Chat): JSONObject = JSONObject()
        .put("id", chat.id)
        .put("title", chat.title)
        .put("pinned", chat.pinned)
        .put("archived", chat.archived)
        .put("persona_id", chat.personaId ?: JSONObject.NULL)
        .put("model_id", chat.modelId ?: JSONObject.NULL)
        .put("project_id", chat.projectId ?: JSONObject.NULL)
        .put("folder_id", chat.folderId ?: JSONObject.NULL)
        .put("workspace_id", chat.workspaceId)
        .put("profile", chat.profile)
        .put("is_temporary", chat.isTemporary)
        .put("draft", chat.draft)
        .put("title_is_custom", chat.titleIsCustom)
        .put("previous_title", chat.previousTitle ?: JSONObject.NULL)
        .put("thinking_mode", chat.thinkingMode ?: JSONObject.NULL)
        .put("tools_enabled", chat.toolsEnabled)
        .put("tool_overrides", JSONObject(chat.toolOverrideMap() as Map<*, *>))
        .put("source_grounded", chat.sourceGrounded)
        .put("knowledge_base_ids", JSONArray(chat.kbIdList()))
        .put("temperature", chat.temperature ?: JSONObject.NULL)
        .put("top_p", chat.topP ?: JSONObject.NULL)
        .put("top_k", chat.topK ?: JSONObject.NULL)
        .put("updated_at", chat.updatedAt)

    /** `GET /api/chat?id=` — one chat's configuration. The list endpoint deliberately stays lean
     * (it renders a sidebar of up to 200 rows); this is what the chat view opens with. */
    private fun handleGetChat(session: IHTTPSession): Response {
        val id = session.parameters["id"]?.firstOrNull()?.trim().orEmpty()
        if (id.isBlank()) return errorResponse(Response.Status.BAD_REQUEST, "id is required")
        return runBlocking {
            val chat = app.container.db.chatDao().getChat(id)
                ?: return@runBlocking errorResponse(Response.Status.NOT_FOUND, "Chat not found", ErrorType.NOT_FOUND, null, "id")
            jsonResponse(Response.Status.OK, chatConfigJson(chat))
        }
    }

    private fun handleCreateChat(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val title = json.optString("title").trim().ifBlank { "New chat" }.take(200)
        return runBlocking {
            // A chat created from the browser belongs to the workspace the app currently has
            // active, and inherits that workspace's persona and default knowledge bases — exactly
            // what the app does for a chat started on the phone. Without this, web-created chats
            // silently landed in the default workspace with no persona, which is why they behaved
            // differently from ones started on the device.
            val workspace = runCatching {
                val activeId = app.container.settingsRepository.activeWorkspaceId.first()
                app.container.db.workspaceDao().get(activeId) ?: app.container.db.workspaceDao().getDefault()
            }.getOrNull()
            val chat = com.vervan.chat.data.db.entities.Chat(
                title = title,
                workspaceId = workspace?.id ?: com.vervan.chat.data.db.entities.Workspace.DEFAULT_WORKSPACE_ID,
                personaId = workspace?.personaId,
                profile = workspace?.defaultProfile?.takeIf { it.isNotBlank() } ?: "BALANCED",
                knowledgeBaseIds = workspace?.defaultKnowledgeBaseIds.orEmpty()
            )
            app.container.db.chatDao().upsert(chat)
            jsonResponse(Response.Status.OK, chatConfigJson(chat))
        }
    }

    private fun handleListMessages(session: IHTTPSession): Response {
        val chatId = session.parameters["chat_id"]?.firstOrNull()
            ?: return errorResponse(Response.Status.BAD_REQUEST, "chat_id query parameter is required")
        return runBlocking {
            val messages = app.container.db.messageDao().getMessages(chatId)
            val data = JSONArray()
            messages.forEach { m ->
                data.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("role", m.role.name.lowercase())
                        .put("content", m.content)
                        .put("created_at", m.createdAt)
                        .put("generation_ms", m.generationMs ?: JSONObject.NULL)
                        .put("token_count", m.tokenCount ?: JSONObject.NULL)
                        .put("model_name", m.modelName ?: JSONObject.NULL)
                        .put("has_image", !m.imagePath.isNullOrBlank())
                        .put("has_audio", !m.audioPath.isNullOrBlank())
                        // The fetchable counterpart of the has_* booleans. Without these the web
                        // app could tell that a past turn had an image but had no way to show it.
                        .put(
                            "image_url",
                            if (m.imagePath.isNullOrBlank()) JSONObject.NULL
                            else "/api/attachments?chat_id=$chatId&message_id=${m.id}&kind=image"
                        )
                        .put(
                            "audio_url",
                            if (m.audioPath.isNullOrBlank()) JSONObject.NULL
                            else "/api/attachments?chat_id=$chatId&message_id=${m.id}&kind=audio"
                        )
                        .put("state", m.state.name.lowercase())
                        .put("parent_id", m.parentId ?: JSONObject.NULL)
                        .put("reaction", m.reaction ?: JSONObject.NULL)
                        .put("feedback_reason", m.feedbackReason ?: JSONObject.NULL)
                        // Tool activity is part of what a turn *was*; a web transcript that drops it
                        // shows an answer with no explanation of where its facts came from.
                        .put("tool_call", m.toolCallJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
                        .put("tool_result", m.toolResultJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
                        .put("sources", m.sourcesJson?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray())
                )
            }
            jsonResponse(Response.Status.OK, JSONObject().put("object", "list").put("data", data))
        }
    }

    /** Appends the just-sent user turn and the model's reply to [chatId] as real [Message] rows —
     * called once generation actually succeeds, from both the streaming and non-streaming
     * branches of [handleChatCompletions]. Mirrors a linear (non-branching) slice of what
     * ChatViewModel does per turn: `parentId` chains off the chat's current `activeLeafId`, and
     * the chat's `activeLeafId`/`updatedAt` (and, for a still-default-titled chat, `title`) are
     * updated to match — so the native app's chat list and BranchUtil-based message loading both
     * treat this exactly like a turn that happened in-app, no special-casing needed on that side. */
    private suspend fun persistTurn(
        chatId: String,
        userText: String,
        userImagePath: String?,
        userAudioPath: String?,
        assistantText: String,
        generationMs: Long,
        model: com.vervan.chat.data.db.entities.ModelInfo,
        sourcesJson: String?
    ) {
        val chatDao = app.container.db.chatDao()
        val messageDao = app.container.db.messageDao()
        val chat = chatDao.getChat(chatId) ?: return
        val userMessage = com.vervan.chat.data.db.entities.Message(
            chatId = chatId,
            parentId = chat.activeLeafId,
            role = com.vervan.chat.data.db.entities.MessageRole.USER,
            content = userText,
            imagePath = userImagePath,
            audioPath = userAudioPath
        )
        messageDao.upsert(userMessage)
        val assistantMessage = com.vervan.chat.data.db.entities.Message(
            chatId = chatId,
            parentId = userMessage.id,
            role = com.vervan.chat.data.db.entities.MessageRole.ASSISTANT,
            content = assistantText,
            generationMs = generationMs,
            tokenCount = com.vervan.chat.llm.estimateTokens(assistantText),
            modelId = model.id,
            modelName = model.displayName,
            backend = model.lastWorkingBackend.name,
            sourcesJson = sourcesJson
        )
        messageDao.upsert(assistantMessage)
        // Only a chat that hasn't been given a real title yet (still the "New chat" default from
        // handleCreateChat, and never manually renamed) gets one auto-derived here — a plain
        // truncation of the first user turn, not a second LLM call, to keep a chat completion
        // request's cost to exactly the one generation the client actually asked for.
        val autoTitle = if (!chat.titleIsCustom && chat.title == "New chat" && userText.isNotBlank()) {
            userText.trim().take(60).let { if (userText.trim().length > 60) "$it…" else it }
        } else chat.title
        chatDao.upsert(chat.copy(activeLeafId = assistantMessage.id, updatedAt = System.currentTimeMillis(), title = autoTitle))
    }

    /** Copies a request-scoped temp attachment (see decodeImageAttachment/decodeAudioAttachment)
     * into the app's permanent per-message attachment storage instead of the usual cleanup
     * delete — only called when the turn is actually being persisted (`chat_id` present); an
     * unpersisted request still deletes its temp file exactly as before. Mirrors
     * MessageAttachmentCleanup's expectation that a Message's imagePath/audioPath survives for
     * the message's lifetime, not just for the one generate() call that used it. */
    private fun persistAttachment(tempFile: File): String? = try {
        val dir = File(app.filesDir, "message-attachments").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}-${tempFile.name}")
        tempFile.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyToLimited(output, InputLimits.MAX_IMAGE_BATCH_BYTES) }
        }
        dest.absolutePath
    } catch (t: Throwable) {
        Log.w(TAG, "persistAttachment() failed for ${tempFile.name}", t)
        null
    }

    // ---- /v1/completions, /v1/audio/* ----------------------------------------------------------

    /**
     * Legacy `/v1/completions`. Reshapes `prompt` into a single user turn, runs the same path as
     * chat completions, and returns a `text_completion` object. Kept deliberately thin — no tools,
     * no thinking split (the legacy object has nowhere to put either) — because its only job is to
     * let an older integration that never learned the chat endpoint still talk to this device.
     */
    private fun handleLegacyCompletions(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_CHAT_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val prompt = when (val p = json.opt("prompt")) {
            is String -> p
            // The spec allows an array of prompts; only the single-prompt form maps onto one
            // generation, and returning `n` completions isn't supported here either (see
            // rejectUnsupportedParams).
            is JSONArray -> if (p.length() == 1) p.optString(0) else return errorResponse(
                Response.Status.BAD_REQUEST, "Only a single prompt is supported",
                ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "prompt"
            )
            else -> return errorResponse(Response.Status.BAD_REQUEST, "prompt must be a string", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "prompt")
        }
        if (prompt.isBlank()) return errorResponse(Response.Status.BAD_REQUEST, "prompt must not be empty", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "prompt")

        // Rebuild it as a chat request and reuse that whole path rather than maintaining a second
        // generation pipeline. `stream` is dropped: the legacy streaming object differs from the
        // chat chunk shape, and a client old enough to need this endpoint is not streaming.
        val chatBody = JSONObject(json.toString())
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("stream", false)
        chatBody.remove("prompt")
        val request = try {
            parseChatRequest(chatBody)
        } catch (e: ApiRequestException) {
            return e.response
        }
        if (!generationSlots.tryAcquire()) {
            return errorResponse(
                Response.Status.TOO_MANY_REQUESTS,
                "Too many generation requests are already queued on this device — retry shortly",
                ErrorType.RATE_LIMIT, ErrorCode.RATE_LIMIT_EXCEEDED, null
            ).apply { addHeader("Retry-After", "5") }
        }
        val completionId = "cmpl-${UUID.randomUUID()}"
        val runnerRequest = ApiChatRunner.Request(
            model = request.model,
            systemPrompt = request.systemText,
            flatPrompt = buildFlatPrompt(request.turns),
            turns = request.turns,
            imagePath = null,
            audioPath = null,
            sampling = request.sampling,
            thinkingMode = request.thinkingMode,
            clientTools = emptyList(),
            toolChoice = ApiChatRunner.ToolChoice.None,
            appToolsEnabled = false,
            allowWriteTools = false,
            enabledAppToolIds = emptySet(),
            maxToolHops = 0
        )
        val promptTokens = com.vervan.chat.llm.estimateTokens(prompt)
        return try {
            val text = StringBuilder()
            var finishReason = "stop"
            var completionTokens = 0
            val startedAtMs = System.currentTimeMillis()
            runBlocking {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    val loaded = app.container.modelLoadCoordinator.ensureLoaded(request.model, LoadTrigger.API_REQUEST)
                    check(loaded.success) { loaded.errorMessage ?: "Could not load ${request.model.displayName}" }
                    runner.run(runnerRequest).collect { event ->
                        when (event) {
                            is ApiChatRunner.Event.Answer -> text.append(event.text)
                            is ApiChatRunner.Event.Done -> {
                                finishReason = event.finishReason
                                completionTokens = event.completionTokens
                            }
                            else -> Unit
                        }
                    }
                }
            }
            jsonResponse(
                Response.Status.OK,
                JSONObject()
                    .put("id", completionId)
                    .put("object", "text_completion")
                    .put("created", System.currentTimeMillis() / 1000)
                    .put("model", request.model.displayName)
                    .put(
                        "choices",
                        JSONArray().put(
                            JSONObject().put("index", 0).put("text", text.toString())
                                .put("logprobs", JSONObject.NULL).put("finish_reason", finishReason)
                        )
                    )
                    .put("usage", usageJson(promptTokens, completionTokens, System.currentTimeMillis() - startedAtMs))
            )
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            Log.e(TAG, "legacy completion failed", t)
            errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage(), ErrorType.SERVER, ErrorCode.SERVER_ERROR, null)
        } finally {
            generationSlots.release()
            runBlocking { app.container.modelLoadCoordinator.touchTtl(ModelRole.GENERATION) }
        }
    }

    /**
     * `/v1/audio/transcriptions` — Whisper-compatible speech to text, backed by
     * [OfflineDictationTranscriber] (the same whisper.cpp / audio-capable-model chain the chat
     * composer's dictation uses). Accepts the spec's `multipart/form-data` upload *and* a
     * `{"file": "<base64>", "format": "webm"}` JSON body, since a browser fetch() is far happier
     * building the latter.
     *
     * `response_format` supports `json` (default), `text` and `verbose_json`; the timestamped
     * `srt`/`vtt` variants are rejected rather than silently returned as plain text.
     */
    private fun handleTranscriptions(session: IHTTPSession): Response {
        val contentType = (session.headers["content-type"] ?: session.headers["Content-Type"]).orEmpty()
        var sourceFile: File? = null
        var responseFormat = "json"
        try {
            if (contentType.startsWith("multipart/form-data")) {
                // Every other body-reading path in this file (readJsonBody, WebAppApi.withBody)
                // rejects an oversized Content-Length before spooling anything to disk. This
                // branch only checked the spooled file's size *after* NanoHTTPD's parseBody()
                // had already written the whole body to a temp file — an unbounded multipart
                // upload could exhaust disk/memory before the MAX_AUDIO_UPLOAD_BYTES check ever
                // ran. Same declared-length gate as readJsonBody, applied up front here too.
                val declaredLength = (session.headers["content-length"] ?: session.headers["Content-Length"])?.toLongOrNull()
                if (declaredLength == null) {
                    return errorResponse(Response.Status.BAD_REQUEST, "A valid Content-Length header is required")
                }
                if (declaredLength < 0 || declaredLength > MAX_AUDIO_UPLOAD_BYTES) {
                    return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Audio file is too large (max ${MAX_AUDIO_UPLOAD_BYTES / (1024 * 1024)} MB)")
                }
                val files = HashMap<String, String>()
                session.parseBody(files)
                responseFormat = session.parameters["response_format"]?.firstOrNull()?.lowercase() ?: "json"
                // NanoHTTPD spools each uploaded part to its own temp file and hands back the path.
                val tempPath = files["file"]
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "A file part is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "file")
                val uploadedName = session.parameters["file"]?.firstOrNull().orEmpty()
                sourceFile = File(tempPath).takeIf { it.isFile && it.length() > 0 }
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "The uploaded file is empty")
                if (sourceFile.length() > MAX_AUDIO_UPLOAD_BYTES) {
                    return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Audio file is too large (max ${MAX_AUDIO_UPLOAD_BYTES / (1024 * 1024)} MB)")
                }
                // AudioNormalizer decodes by content, not by extension, so the original name is
                // only used to keep a recognizable suffix for logging.
                Log.i(TAG, "transcription upload: ${uploadedName.take(80)} (${sourceFile.length()} bytes)")
            } else {
                val json = when (val result = readJsonBody(session, MAX_AUDIO_UPLOAD_BYTES)) {
                    is BodyReadResult.Error -> return result.response
                    is BodyReadResult.Ok -> result.json
                }
                responseFormat = json.optString("response_format").ifBlank { "json" }.lowercase()
                val base64 = json.optString("file").ifBlank { json.optString("data") }.ifBlank { null }
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "file (base64) is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "file")
                val bytes = runCatching { Base64.decode(base64.substringAfter(",", base64), Base64.DEFAULT) }.getOrNull()
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "file must be valid base64")
                if (bytes.isEmpty()) return errorResponse(Response.Status.BAD_REQUEST, "The uploaded file is empty")
                val ext = json.optString("format").lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "webm" }
                val dir = File(app.cacheDir, "server-uploads").apply { mkdirs() }
                sourceFile = File(dir, "stt-${UUID.randomUUID()}.$ext").also { it.writeBytes(bytes) }
            }
            if (responseFormat !in setOf("json", "text", "verbose_json")) {
                return errorResponse(
                    Response.Status.BAD_REQUEST,
                    "response_format must be json, text or verbose_json — subtitle formats need word timings this engine doesn't expose",
                    ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "response_format"
                )
            }

            val wav = File(File(app.cacheDir, "server-uploads").apply { mkdirs() }, "stt-${UUID.randomUUID()}.wav")
            val source = sourceFile
            return try {
                val normalized = com.vervan.chat.audio.AudioNormalizer.normalize(app, Uri.fromFile(source), wav)
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "Could not decode that audio format")
                val result = runBlocking {
                    withTimeout(GENERATION_TIMEOUT_MS) {
                        com.vervan.chat.voice.OfflineDictationTranscriber.transcribe(
                            app, normalized, loadTrigger = LoadTrigger.API_REQUEST
                        )
                    }
                }
                val transcript = result.getOrElse { failure ->
                    return errorResponse(
                        Response.Status.SERVICE_UNAVAILABLE, failure.toUserMessage(),
                        ErrorType.SERVER, ErrorCode.SERVER_ERROR, null
                    )
                }
                if (responseFormat == "text") {
                    newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", transcript.text)
                } else {
                    val body = JSONObject().put("text", transcript.text)
                    if (responseFormat == "verbose_json") {
                        body.put("task", "transcribe").put("engine", transcript.engineLabel)
                    }
                    jsonResponse(Response.Status.OK, body)
                }
            } finally {
                wav.delete()
                runBlocking { app.container.modelLoadCoordinator.touchTtl(ModelRole.GENERATION) }
            }
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            Log.e(TAG, "transcription failed", t)
            return errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage(), ErrorType.SERVER, ErrorCode.SERVER_ERROR, null)
        } finally {
            sourceFile?.delete()
        }
    }

    /**
     * `/v1/audio/speech` — text to speech through the app's own Piper/Kokoro/Supertonic chain
     * (Android's system TTS is deliberately never used anywhere in this app, so it isn't a
     * fallback here either). Returns WAV; `response_format: "pcm"` returns raw 16-bit samples.
     * The compressed formats OpenAI also offers (mp3/opus/aac/flac) are rejected rather than
     * mislabelled, since nothing on this path encodes them.
     */
    private fun handleSpeech(session: IHTTPSession): Response {
        val json = when (val result = readJsonBody(session, MAX_BODY_BYTES)) {
            is BodyReadResult.Error -> return result.response
            is BodyReadResult.Ok -> result.json
        }
        val input = json.optString("input").trim()
        if (input.isEmpty()) return errorResponse(Response.Status.BAD_REQUEST, "input is required", ErrorType.INVALID_REQUEST, ErrorCode.INVALID_VALUE, "input")
        if (input.length > MAX_TTS_INPUT_CHARS) {
            return errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "input is too long (max $MAX_TTS_INPUT_CHARS characters)")
        }
        val format = json.optString("response_format").ifBlank { "wav" }.lowercase()
        if (format != "wav" && format != "pcm") {
            return errorResponse(
                Response.Status.BAD_REQUEST,
                "response_format must be \"wav\" or \"pcm\" — this device has no mp3/opus/aac/flac encoder on the speech path",
                ErrorType.INVALID_REQUEST, ErrorCode.UNSUPPORTED_VALUE, "response_format"
            )
        }
        val language = json.optString("language").ifBlank { "en" }
        val outFile = File(File(app.cacheDir, "server-uploads").apply { mkdirs() }, "tts-${UUID.randomUUID()}.wav")
        val selector = com.vervan.chat.voice.TtsEngineSelector(
            app.container.settingsRepository,
            com.vervan.chat.voice.PiperTtsEngine(app.container.db.ttsVoiceModelDao()),
            com.vervan.chat.voice.KokoroTtsEngine(app.container.db.ttsVoiceModelDao()),
            com.vervan.chat.voice.SupertonicTtsEngine(app.container.db.ttsVoiceModelDao(), app.container.settingsRepository)
        )
        return try {
            val bytes = runBlocking {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    val engine = selector.resolve()
                        ?: return@withTimeout null
                    val sentences = com.vervan.chat.voice.TtsFileGenerator.splitSentences(input)
                    val results = com.vervan.chat.voice.TtsFileGenerator.synthesizeSentences(sentences, engine, language)
                    com.vervan.chat.voice.TtsFileGenerator.mergeToFile(results, outFile)
                    outFile.inputStream().use { it.readBytesLimited(InputLimits.MAX_DECODED_AUDIO_BYTES) }
                }
            } ?: return errorResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                "No offline voice is installed — download one in Settings → Voice before using /v1/audio/speech",
                ErrorType.SERVER, ErrorCode.SERVER_ERROR, null
            )
            // WAV is what mergeToFile wrote; `pcm` is the same buffer minus the 44-byte header.
            val payload = if (format == "pcm" && bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
            val mime = if (format == "pcm") "audio/L16" else "audio/wav"
            newFixedLengthResponse(Response.Status.OK, mime, java.io.ByteArrayInputStream(payload), payload.size.toLong())
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            Log.e(TAG, "speech synthesis failed", t)
            errorResponse(Response.Status.INTERNAL_ERROR, t.toUserMessage(), ErrorType.SERVER, ErrorCode.SERVER_ERROR, null)
        } finally {
            outFile.delete()
            selector.releaseAll()
        }
    }

    /** Little-endian float32 array, base64-encoded — the exact layout OpenAI's `encoding_format:
     * "base64"` produces and that clients decode with `np.frombuffer(..., dtype="float32")`. */
    private fun encodeFloatsBase64(vector: FloatArray): String {
        val buffer = java.nio.ByteBuffer.allocate(vector.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private sealed class BodyReadResult {
        data class Ok(val json: JSONObject) : BodyReadResult()
        data class Error(val response: Response) : BodyReadResult()
    }

    private fun readJsonBody(session: IHTTPSession, maxBytes: Long): BodyReadResult {
        val lengthHeader = session.headers["content-length"] ?: session.headers["Content-Length"]
        val declaredLength = lengthHeader?.toLongOrNull()
            ?: return BodyReadResult.Error(errorResponse(Response.Status.BAD_REQUEST, "A valid Content-Length header is required"))
        if (declaredLength < 0 || declaredLength > maxBytes) {
            return BodyReadResult.Error(errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Request body too large (max ${maxBytes / (1024 * 1024)} MB)"))
        }
        val body = HashMap<String, String>()
        session.parseBody(body)
        val postData = body["postData"] ?: "{}"
        if (postData.toByteArray(Charsets.UTF_8).size > maxBytes) {
            return BodyReadResult.Error(errorResponse(Response.Status.PAYLOAD_TOO_LARGE, "Request body too large (max ${maxBytes / (1024 * 1024)} MB)"))
        }
        val json = runCatching { JSONObject(postData) }.getOrElse {
            return BodyReadResult.Error(errorResponse(Response.Status.BAD_REQUEST, "Request body must be valid JSON"))
        }
        return BodyReadResult.Ok(json)
    }


    private fun jsonResponse(status: Response.Status, json: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", json.toString())

    /** Default-shaped error: a client mistake. Every call site that means something more specific
     * (a missing model, an unsupported parameter, a queue that's full) uses the four-argument
     * overload below so `error.code` carries the machine-readable reason. */
    private fun errorResponse(status: Response.Status, message: String): Response =
        errorResponse(status, message, ErrorType.INVALID_REQUEST, null, null)

    private fun errorResponse(
        status: Response.Status,
        message: String,
        type: String,
        code: String?,
        param: String?
    ): Response = jsonResponse(status, openAiErrorJson(message, type, code, param))
}
