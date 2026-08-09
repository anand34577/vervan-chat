package com.vervan.chat.server

import android.util.Log
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.MemoryScope
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.modelload.ModelLoadPhase
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.tools.ToolRegistry
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "second screen" half of the local server: the app's own workspace data, as opposed to
 * [LocalApiServer]'s OpenAI-compatible inference surface.
 *
 * The split is deliberate. [LocalApiServer] owns everything a third-party OpenAI client talks to
 * (`/v1/...`) plus the chat/message/document endpoints that are entangled with generation and
 * attachment persistence. Everything here is plain workspace state — notes, memories, personas,
 * prompt templates, saved outputs, projects, workspaces, folders, tool runs, model residency — that
 * the web UI needs in order to be a real second view of the app rather than a chat box that happens
 * to share a database.
 *
 * Two rules hold for every handler below:
 *
 * 1. **Deletes are soft.** Each of these entities is covered by the app's recycle bin
 *    (`deletedAt`), and a delete issued from the browser has to land in the same bin a delete
 *    issued on the phone does — otherwise the web UI is quietly the one destructive surface in
 *    the product. Rows are stamped, never removed.
 * 2. **Writes go through the same entities the app uses**, copied field-by-field from the current
 *    row, so a field the web UI does not know about (a chat's scroll anchor, a persona's avatar,
 *    a memory's cached embedding) survives an edit made in the browser instead of being reset to
 *    its default.
 *
 * Auth, CORS, `fullMode` gating and client recording all happen in [LocalApiServer] before
 * [handle] is ever called; this class assumes an authorized request.
 */
internal class WebAppApi(private val app: VervanApp) {

    companion object {
        private const val TAG = "WebAppApi"
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024
        /** Document attachments and OCR images are base64 in a JSON body, so the body cap has to
         * be raised well above the plain-JSON one for those two routes. */
        private const val MAX_UPLOAD_BODY_BYTES = 64L * 1024 * 1024
        private const val MAX_ATTACHMENT_BYTES = 40 * 1024 * 1024
        /** Free-text search fans out across six tables; each contributes at most this many rows so
         * one very common word can't return a five-figure result set to a phone-hosted browser. */
        private const val SEARCH_LIMIT_PER_TYPE = 20
        private const val TOOL_RUN_LIMIT = 100
    }

    /** Returns null when [session] is not one of this class's routes, so [LocalApiServer] can carry
     * on matching its own. */
    fun handle(session: IHTTPSession): Response? {
        val get = session.method == Method.GET
        val post = session.method == Method.POST
        return try {
            when {
                get && session.uri == "/api/overview" -> handleOverview()
                get && session.uri == "/api/system" -> handleSystem()
                get && session.uri == "/api/recycle-bin" -> handleListRecycleBin()
                post && session.uri == "/api/recycle-bin/restore" -> handleRecycleBin(session, restore = true)
                post && session.uri == "/api/recycle-bin/purge" -> handleRecycleBin(session, restore = false)
                get && session.uri == "/api/search" -> handleSearch(session)

                // Chat actions the app's own overflow menu offers. Kept here rather than in
                // LocalApiServer because none of them is inference plumbing — they are the same
                // workspace-data operations as everything else in this file.
                post && session.uri == "/api/chats/duplicate" -> handleDuplicateChat(session)
                post && session.uri == "/api/chats/generate-title" -> handleGenerateTitle(session)
                post && session.uri == "/api/chats/restore-title" -> handleRestoreTitle(session)
                post && session.uri == "/api/chats/add-to-knowledge-base" -> handleChatToKnowledgeBase(session)
                post && session.uri == "/api/messages/react" -> handleReactToMessage(session)
                post && session.uri == "/api/messages/fork" -> handleForkChat(session)
                post && session.uri == "/api/chats/attach-document" -> handleAttachDocument(session)
                post && session.uri == "/api/ocr" -> handleOcr(session)

                get && session.uri == "/api/notes" -> handleListNotes()
                post && session.uri == "/api/notes" -> handleSaveNote(session)
                post && session.uri == "/api/notes/delete" -> handleDeleteNote(session)

                get && session.uri == "/api/memories" -> handleListMemories()
                post && session.uri == "/api/memories" -> handleSaveMemory(session)
                post && session.uri == "/api/memories/delete" -> handleDeleteMemory(session)

                get && session.uri == "/api/personas" -> handleListPersonas()
                post && session.uri == "/api/personas" -> handleSavePersona(session)
                post && session.uri == "/api/personas/delete" -> handleDeletePersona(session)

                get && session.uri == "/api/templates" -> handleListTemplates()
                post && session.uri == "/api/templates" -> handleSaveTemplate(session)
                post && session.uri == "/api/templates/delete" -> handleDeleteTemplate(session)

                get && session.uri == "/api/saved-outputs" -> handleListSavedOutputs()
                post && session.uri == "/api/saved-outputs" -> handleSaveSavedOutput(session)
                post && session.uri == "/api/saved-outputs/delete" -> handleDeleteSavedOutput(session)

                get && session.uri == "/api/projects" -> handleListProjects()
                post && session.uri == "/api/projects" -> handleSaveProject(session)
                post && session.uri == "/api/projects/delete" -> handleDeleteProject(session)

                get && session.uri == "/api/workspaces" -> handleListWorkspaces()
                post && session.uri == "/api/workspaces" -> handleSaveWorkspace(session)
                post && session.uri == "/api/workspaces/delete" -> handleDeleteWorkspace(session)

                get && session.uri == "/api/folders" -> handleListFolders()
                post && session.uri == "/api/folders" -> handleSaveFolder(session)
                post && session.uri == "/api/folders/delete" -> handleDeleteFolder(session)

                get && session.uri == "/api/tool-runs" -> handleListToolRuns()
                post && session.uri == "/api/tools/run" -> handleRunTool(session)

                get && session.uri == "/api/models" -> handleListModels()
                post && session.uri == "/api/models/load" -> handleLoadModel(session)
                post && session.uri == "/api/models/unload" -> handleUnloadModel(session)
                post && session.uri == "/api/models/default" -> handleSetDefaultModel(session)

                else -> null
            }
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            Log.e(TAG, "handler failed for ${session.method} ${session.uri}", t)
            error(Response.Status.INTERNAL_ERROR, t.toUserMessage())
        }
    }

    // ---------------------------------------------------------------- overview / search

    /** Backs the web UI's home screen in a single round trip. Counting six tables separately from
     * the browser would be six requests on a phone-hosted connection for what is one screen. */
    private fun handleOverview(): Response = runBlocking {
        val db = app.container.db
        val chats = db.chatDao().observeChats().first()
        val notes = db.noteDao().observeAll().first()
        val memories = db.memoryDao().observeAll().first()
        val kbs = db.knowledgeBaseDao().observeAll().first()
        val documents = db.documentDao().observeAll().first()
        val projects = db.projectDao().observeAll().first()
        val workspaces = db.workspaceDao().observeActive().first()
        val activeWorkspaceId = app.container.settingsRepository.activeWorkspaceId.first()

        val counts = JSONObject()
            .put("chats", chats.size)
            .put("notes", notes.size)
            .put("memories", memories.size)
            .put("knowledge_bases", kbs.size)
            .put("documents", documents.size)
            .put("projects", projects.size)

        val recentChats = JSONArray()
        chats.take(8).forEach { chat ->
            recentChats.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("pinned", chat.pinned)
                    .put("updated_at", chat.updatedAt)
            )
        }
        val recentNotes = JSONArray()
        notes.take(6).forEach { note ->
            recentNotes.put(
                JSONObject().put("id", note.id).put("title", note.title).put("updated_at", note.updatedAt)
            )
        }

        json(
            JSONObject()
                .put("counts", counts)
                .put("recent_chats", recentChats)
                .put("recent_notes", recentNotes)
                .put("models", modelStateJson())
                .put("workspaces", JSONArray(workspaces.map { workspaceJson(it) }))
                .put("active_workspace_id", activeWorkspaceId)
        )
    }

    /** The app's global search, as one federated result set. Each DAO's own `search` query is
     * reused rather than re-implemented here so the browser and the phone agree on what matches. */
    private fun handleSearch(session: IHTTPSession): Response {
        val query = session.parameters["q"]?.firstOrNull()?.trim().orEmpty()
        if (query.isBlank()) return json(JSONObject().put("results", JSONArray()))
        return runBlocking {
            val db = app.container.db
            val results = JSONArray()
            fun add(type: String, id: String, title: String, snippet: String, extra: JSONObject? = null) {
                results.put(
                    JSONObject()
                        .put("type", type).put("id", id)
                        .put("title", title).put("snippet", snippet.take(220))
                        .apply { extra?.keys()?.forEach { put(it, extra.get(it)) } }
                )
            }
            db.chatDao().search(query).take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("chat", it.id, it.title, "", JSONObject().put("updated_at", it.updatedAt)) }
            db.noteDao().search(query).take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("note", it.id, it.title, it.content) }
            db.memoryDao().search(query).take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("memory", it.id, it.text.take(60), it.text) }
            db.personaDao().search(query).take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("persona", it.id, it.name, it.description) }
            db.documentDao().observeAll().first()
                .filter { it.displayName.contains(query, ignoreCase = true) }
                .take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("document", it.id, it.displayName, "") }
            db.knowledgeBaseDao().observeAll().first()
                .filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
                .take(SEARCH_LIMIT_PER_TYPE)
                .forEach { add("knowledge_base", it.id, it.name, it.description) }
            json(JSONObject().put("query", query).put("results", results))
        }
    }

    // ---------------------------------------------------------------- system / recycle bin

    /** Device state the web app shows in its header and generation strip: RAM, thermal status and
     * which models are resident. Cheap enough to poll on a few-second cadence. */
    private fun handleSystem(): Response = runBlocking {
        val info = android.app.ActivityManager.MemoryInfo()
        (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getMemoryInfo(info)
        json(
            JSONObject()
                .put(
                    "memory",
                    JSONObject()
                        .put("available_mb", info.availMem / (1024 * 1024))
                        .put("total_mb", info.totalMem / (1024 * 1024))
                        .put("low", info.lowMemory)
                )
                .put("models", modelStateJson())
        )
    }

    /**
     * Everything currently in the recycle bin, across the eight soft-deletable types.
     *
     * The web app previously had no way to reach any of it, which made its delete buttons look
     * permanent when they are not — the app's own Recently deleted screen restores exactly these
     * rows, and a background sweep hard-deletes them after the retention window.
     */
    private fun handleListRecycleBin(): Response = runBlocking {
        val db = app.container.db
        val items = JSONArray()
        fun add(type: String, id: String, title: String, deletedAt: Long?) {
            items.put(
                JSONObject().put("type", type).put("id", id)
                    .put("title", title.ifBlank { "Untitled" })
                    .put("deleted_at", deletedAt ?: JSONObject.NULL)
            )
        }
        db.chatDao().observeDeleted().first().forEach { add("chat", it.id, it.title, it.deletedAt) }
        db.noteDao().observeDeleted().first().forEach { add("note", it.id, it.title, it.deletedAt) }
        db.memoryDao().observeDeleted().first().forEach { add("memory", it.id, it.text.take(70), it.deletedAt) }
        db.personaDao().observeDeleted().first().forEach { add("persona", it.id, it.name, it.deletedAt) }
        db.promptTemplateDao().observeDeleted().first().forEach { add("template", it.id, "/" + it.name, it.deletedAt) }
        db.savedOutputDao().observeDeleted().first().forEach { add("saved-output", it.id, it.label.ifBlank { it.content.take(60) }, it.deletedAt) }
        db.projectDao().observeDeleted().first().forEach { add("project", it.id, it.name, it.deletedAt) }
        db.folderDao().observeDeleted().first().forEach { add("folder", it.id, it.name, it.deletedAt) }
        db.documentDao().observeDeleted().first().forEach { add("document", it.id, it.displayName, it.deletedAt) }
        json(JSONObject().put("items", items))
    }

    /**
     * Restores a binned row (clears `deletedAt`) or purges it for good.
     *
     * A purge here is the only genuinely irreversible operation the web app exposes, which is why
     * it is a separate endpoint from the ordinary delete rather than a flag on it — nothing can
     * reach it by accident, and the browser confirms it explicitly first.
     */
    private fun handleRecycleBin(session: IHTTPSession, restore: Boolean): Response = withBody(session) { body ->
        runBlocking {
            val db = app.container.db
            val id = body.optString("id")
            when (body.optString("type")) {
                "chat" -> db.chatDao().getChat(id)?.let { row ->
                    if (restore) db.chatDao().upsert(row.copy(deletedAt = null)) else db.chatDao().delete(row)
                }
                "note" -> db.noteDao().get(id)?.let { row ->
                    if (restore) db.noteDao().upsert(row.copy(deletedAt = null)) else db.noteDao().delete(row)
                }
                "memory" -> db.memoryDao().get(id)?.let { row ->
                    if (restore) db.memoryDao().upsert(row.copy(deletedAt = null)) else db.memoryDao().delete(row)
                }
                // PersonaDao.getPersona() filters `deletedAt IS NULL`, so a binned persona is
                // unreachable through it — resolved from the deleted list instead, same as
                // SavedOutputDao which has no id lookup at all.
                "persona" -> db.personaDao().observeDeleted().first().find { it.id == id }?.let { row ->
                    if (restore) db.personaDao().upsert(row.copy(deletedAt = null)) else db.personaDao().delete(row)
                }
                "template" -> db.promptTemplateDao().get(id)?.let { row ->
                    if (restore) db.promptTemplateDao().upsert(row.copy(deletedAt = null)) else db.promptTemplateDao().delete(row)
                }
                "saved-output" -> db.savedOutputDao().observeDeleted().first().find { it.id == id }?.let { row ->
                    if (restore) db.savedOutputDao().upsert(row.copy(deletedAt = null)) else db.savedOutputDao().delete(row)
                }
                "project" -> db.projectDao().get(id)?.let { row ->
                    if (restore) db.projectDao().upsert(row.copy(deletedAt = null)) else db.projectDao().delete(row)
                }
                "folder" -> db.folderDao().get(id)?.let { row ->
                    if (restore) db.folderDao().upsert(row.copy(deletedAt = null)) else db.folderDao().delete(row)
                }
                // A document owns a file and an index, so purging it goes through the import
                // manager rather than a bare row delete — otherwise the bytes and its chunks
                // outlive the row.
                "document" -> db.documentDao().get(id)?.let { row ->
                    if (restore) db.documentDao().upsert(row.copy(deletedAt = null))
                    else app.container.documentImportManager.delete(row)
                }
                else -> return@runBlocking badRequest("Unknown recycle bin item type")
            } ?: return@runBlocking notFound("item")
            ok()
        }
    }

    // ---------------------------------------------------------------- chat actions

    /** Mirrors ChatViewModel.duplicate: a full copy of the chat and its message tree with fresh
     * ids, remapping `parentId` and `activeLeafId` so the copy's branch structure survives. Done in
     * one transaction — a half-copied conversation would be worse than none. */
    private fun handleDuplicateChat(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val db = app.container.db
            val original = db.chatDao().getChat(body.optString("id")) ?: return@runBlocking notFound("chat")
            var copyId = ""
            db.withTransaction {
                val all = db.messageDao().getMessages(original.id)
                val ids = all.associate { it.id to java.util.UUID.randomUUID().toString() }
                val copy = original.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${original.title} copy",
                    activeLeafId = original.activeLeafId?.let(ids::get),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null
                )
                copyId = copy.id
                db.chatDao().upsert(copy)
                all.forEach { message ->
                    db.messageDao().upsert(
                        message.copy(
                            id = ids.getValue(message.id),
                            chatId = copy.id,
                            parentId = message.parentId?.let(ids::get)
                        )
                    )
                }
            }
            json(JSONObject().put("id", copyId))
        }
    }

    /** Asks the loaded model to name the conversation, exactly as the app's "Generate title" does —
     * same [TitleGenerator], so a title generated from the browser is the one the phone would have
     * produced. The old name is kept in `previousTitle` so it can be put back. */
    private fun handleGenerateTitle(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.chatDao()
            val chat = dao.getChat(body.optString("id")) ?: return@runBlocking notFound("chat")
            val result = com.vervan.chat.llm.TitleGenerator.generate(app, chat.id)
                ?: return@runBlocking badRequest("Not enough conversation yet to generate a title")
            val updated = chat.copy(
                title = result.title, previousTitle = chat.title,
                titleIsCustom = false, updatedAt = System.currentTimeMillis()
            )
            dao.upsert(updated)
            json(JSONObject().put("id", updated.id).put("title", updated.title).put("previous_title", chat.title))
        }
    }

    private fun handleRestoreTitle(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.chatDao()
            val chat = dao.getChat(body.optString("id")) ?: return@runBlocking notFound("chat")
            val previous = chat.previousTitle ?: return@runBlocking badRequest("This chat has no previous title")
            val updated = chat.copy(
                title = previous, previousTitle = chat.title,
                titleIsCustom = true, updatedAt = System.currentTimeMillis()
            )
            dao.upsert(updated)
            json(JSONObject().put("id", updated.id).put("title", updated.title))
        }
    }

    /** Files the whole transcript into a knowledge base so it becomes retrievable context later —
     * the app's "Add to knowledge base" chat action. */
    private fun handleChatToKnowledgeBase(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val db = app.container.db
            val chat = db.chatDao().getChat(body.optString("chat_id")) ?: return@runBlocking notFound("chat")
            val kbId = body.optString("knowledge_base_id")
            if (db.knowledgeBaseDao().get(kbId) == null) return@runBlocking notFound("knowledge base")
            val transcript = db.messageDao().getMessages(chat.id).joinToString("\n\n") { message ->
                val who = when (message.role) {
                    com.vervan.chat.data.db.entities.MessageRole.USER -> "You"
                    com.vervan.chat.data.db.entities.MessageRole.ASSISTANT -> "Assistant"
                    else -> "System"
                }
                "$who: ${message.content}"
            }
            if (transcript.isBlank()) return@runBlocking badRequest("This chat has nothing to file yet")
            app.container.documentImportManager.importRawText(kbId, chat.title, transcript)
            ok()
        }
    }

    /** 👍/👎 on an assistant reply, plus the optional reason the app collects after a 👎. Stored on
     * the message beside its model/profile/backend snapshot, which is what makes the feedback
     * answerable later ("which preset keeps getting this reason"). */
    private fun handleReactToMessage(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.messageDao()
            val chatId = body.optString("chat_id")
            val target = dao.getMessages(chatId).firstOrNull { it.id == body.optString("id") }
                ?: return@runBlocking notFound("message")
            val reaction = body.optString("reaction").takeIf { it.isNotBlank() }
            if (reaction != null && reaction != "up" && reaction != "down") {
                return@runBlocking badRequest("reaction must be \"up\", \"down\", or empty to clear")
            }
            dao.upsert(
                target.copy(
                    reaction = reaction,
                    // A reason only belongs to a 👎; clearing or flipping the reaction drops it
                    // rather than leaving a reason attached to a reaction it no longer explains.
                    feedbackReason = if (reaction == "down") body.optString("reason").takeIf { it.isNotBlank() } else null
                )
            )
            json(JSONObject().put("id", target.id).put("reaction", reaction ?: JSONObject.NULL))
        }
    }

    /**
     * Forks a chat at a message: a new conversation containing everything up to and including that
     * turn, leaving the original untouched. The app's own "Branch from here".
     *
     * The ancestor walk follows `parentId` from the fork point upward rather than slicing by
     * timestamp, so forking inside an already-branched conversation copies that branch and not
     * whatever else happens to be older.
     */
    private fun handleForkChat(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val db = app.container.db
            val chatId = body.optString("chat_id")
            val original = db.chatDao().getChat(chatId) ?: return@runBlocking notFound("chat")
            val all = db.messageDao().getMessages(chatId)
            val byId = all.associateBy { it.id }
            var cursor = byId[body.optString("message_id")] ?: return@runBlocking notFound("message")

            val lineage = ArrayList<com.vervan.chat.data.db.entities.Message>()
            val seen = HashSet<String>()
            while (true) {
                // Defensive: a cycle in parentId would otherwise spin here forever.
                if (!seen.add(cursor.id)) break
                lineage.add(cursor)
                cursor = cursor.parentId?.let { byId[it] } ?: break
            }
            lineage.reverse()

            var forkId = ""
            db.withTransaction {
                val ids = lineage.associate { it.id to java.util.UUID.randomUUID().toString() }
                val fork = original.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${original.title} (branch)",
                    activeLeafId = lineage.lastOrNull()?.let { ids[it.id] },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null
                )
                forkId = fork.id
                db.chatDao().upsert(fork)
                lineage.forEach { message ->
                    db.messageDao().upsert(
                        message.copy(
                            id = ids.getValue(message.id),
                            chatId = fork.id,
                            parentId = message.parentId?.let(ids::get)
                        )
                    )
                }
            }
            json(JSONObject().put("id", forkId).put("message_count", lineage.size))
        }
    }

    /**
     * Attaches a document to a chat, mirroring ChatViewModel.attachDocument: the file is imported
     * into a private one-document knowledge base named after it, that base is added to the chat and
     * source grounding is switched on, so the next message retrieves against it.
     *
     * A failed import takes its throwaway knowledge base with it — otherwise every bad upload would
     * leave an empty "Attached: …" entry cluttering the Knowledge screen.
     */
    private fun handleAttachDocument(session: IHTTPSession): Response = withBody(session, MAX_UPLOAD_BODY_BYTES) { body ->
        runBlocking {
            val db = app.container.db
            val chat = db.chatDao().getChat(body.optString("chat_id")) ?: return@runBlocking notFound("chat")
            val name = body.optString("name").ifBlank { "document" }
            val base64 = body.optString("data").takeIf { it.isNotBlank() }
                ?: return@runBlocking badRequest("data (base64) is required")
            val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }
                .getOrElse { return@runBlocking badRequest("data must be valid base64") }
            if (bytes.size > MAX_ATTACHMENT_BYTES) {
                return@runBlocking error(Response.Status.PAYLOAD_TOO_LARGE, "That file is too large to attach")
            }

            val kb = com.vervan.chat.data.db.entities.KnowledgeBase(name = "Attached: $name")
            val temp = java.io.File(app.cacheDir, "webui-attach-${System.currentTimeMillis()}-$name")
            try {
                temp.writeBytes(bytes)
                db.knowledgeBaseDao().upsert(kb)
                // reuseExistingByHash=true — same reasoning as ChatViewModel.attachDocument: this
                // call always creates its own throwaway single-document KB, so a duplicate can
                // only mean the content already lives in a *different* KB (this chat's own earlier
                // attachment, or another chat's).
                val outcome = app.container.documentImportManager.import(kb.id, android.net.Uri.fromFile(temp), reuseExistingByHash = true)
                // The KB id every downstream step (grounding, the response body) must reference —
                // the throwaway `kb` created above only when Imported actually used it. Reusing
                // `kb.id` for a Duplicate (as this used to) grounded the chat against an empty KB
                // with zero documents in it while the real content sat under a different id.
                val document: com.vervan.chat.data.db.entities.Document
                val effectiveKbId: String
                when (outcome) {
                    is com.vervan.chat.model.DocumentImportOutcome.Imported -> {
                        document = outcome.document
                        effectiveKbId = kb.id
                    }
                    is com.vervan.chat.model.DocumentImportOutcome.Duplicate -> {
                        db.knowledgeBaseDao().delete(kb)
                        document = outcome.existing
                        effectiveKbId = outcome.existing.knowledgeBaseId
                    }
                    is com.vervan.chat.model.DocumentImportOutcome.VersionConflict -> {
                        document = app.container.documentImportManager.resolveVersionConflict(
                            outcome.existing, outcome.tempFilePath, outcome.mimeType, outcome.newHash, replace = true
                        )
                        effectiveKbId = kb.id
                    }
                }
                if (document.status != com.vervan.chat.data.db.entities.DocumentStatus.READY) {
                    app.container.documentImportManager.delete(document)
                    db.knowledgeBaseDao().get(effectiveKbId)?.let { db.knowledgeBaseDao().delete(it) }
                    return@runBlocking badRequest(
                        document.failureReason ?: "That document could not be read (${document.status.name.lowercase()})"
                    )
                }
                db.chatDao().upsert(
                    chat.copy(
                        knowledgeBaseIds = (chat.kbIdList() + effectiveKbId).distinct().joinToString(","),
                        sourceGrounded = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                json(
                    JSONObject()
                        .put("document_id", document.id)
                        .put("knowledge_base_id", effectiveKbId)
                        .put("name", document.displayName)
                        .put(
                            "grounded",
                            com.vervan.chat.retrieval.embeddingReady(
                                app.container.db.modelDao().getActiveModel(ModelRole.EMBEDDING),
                                app.container.embeddingEngine
                            )
                        )
                )
            } catch (t: Throwable) {
                if (t is VirtualMachineError) throw t
                Log.e(TAG, "document attach failed for chat ${chat.id}", t)
                runCatching { db.knowledgeBaseDao().get(kb.id)?.let { db.knowledgeBaseDao().delete(it) } }
                badRequest(t.toUserMessage())
            } finally {
                temp.delete()
            }
        }
    }

    /** Text out of an image, using the same on-device extractor the app's OCR flows use. Returned
     * rather than sent anywhere: the browser drops it into the composer for the user to edit, which
     * is what the app does too. */
    private fun handleOcr(session: IHTTPSession): Response = withBody(session, MAX_UPLOAD_BODY_BYTES) { body ->
        runBlocking {
            val base64 = body.optString("data").takeIf { it.isNotBlank() }
                ?: return@runBlocking badRequest("data (base64) is required")
            val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }
                .getOrElse { return@runBlocking badRequest("data must be valid base64") }
            if (bytes.size > MAX_ATTACHMENT_BYTES) {
                return@runBlocking error(Response.Status.PAYLOAD_TOO_LARGE, "That image is too large")
            }
            val temp = java.io.File(app.cacheDir, "webui-ocr-${System.currentTimeMillis()}.jpg")
            try {
                temp.writeBytes(bytes)
                com.vervan.chat.model.ImageUtils.fixOrientation(temp)
                val text = com.vervan.chat.model.OcrExtractor.extractFromImage(temp)
                json(JSONObject().put("text", text).put("empty", text.isBlank()))
            } finally {
                temp.delete()
            }
        }
    }

    // ---------------------------------------------------------------- notes

    private fun handleListNotes(): Response = runBlocking {
        val notes = app.container.db.noteDao().observeAll().first()
        json(JSONObject().put("notes", JSONArray(notes.map { noteJson(it) })))
    }

    private fun handleSaveNote(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.noteDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val note = (existing ?: Note()).copy(
                title = body.optString("title", existing?.title ?: "Untitled note").ifBlank { "Untitled note" },
                content = body.optString("content", existing?.content.orEmpty()),
                projectId = body.optString("project_id").takeIf { it.isNotBlank() } ?: existing?.projectId,
                folderId = body.optString("folder_id").takeIf { it.isNotBlank() } ?: existing?.folderId,
                pinned = body.optBoolean("pinned", existing?.pinned ?: false),
                tags = body.optString("tags", existing?.tags.orEmpty()),
                updatedAt = System.currentTimeMillis()
            )
            dao.upsert(note)
            json(JSONObject().put("note", noteJson(note)))
        }
    }

    private fun handleDeleteNote(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.noteDao()
            val note = dao.get(body.optString("id")) ?: return@runBlocking notFound("note")
            dao.upsert(note.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- memories

    private fun handleListMemories(): Response = runBlocking {
        val memories = app.container.db.memoryDao().observeAll().first()
        json(JSONObject().put("memories", JSONArray(memories.map { memoryJson(it) })))
    }

    private fun handleSaveMemory(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.memoryDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val text = body.optString("text", existing?.text.orEmpty()).trim()
            if (text.isBlank()) return@runBlocking badRequest("A memory needs some text")
            val scope = runCatching {
                MemoryScope.valueOf(body.optString("scope", existing?.scope?.name ?: "GLOBAL"))
            }.getOrDefault(MemoryScope.GLOBAL)
            // The stored embedding belongs to the *old* text; keeping it after an edit would make
            // this memory retrievable by its previous wording. Cleared so it is rebuilt on demand.
            val textChanged = existing != null && existing.text != text
            val memory = (existing ?: Memory(text = text)).copy(
                text = text,
                scope = scope,
                scopeRefId = body.optString("scope_ref_id").takeIf { it.isNotBlank() }
                    ?: existing?.scopeRefId.takeIf { scope == existing?.scope },
                enabled = body.optBoolean("enabled", existing?.enabled ?: true),
                key = body.optString("key").takeIf { it.isNotBlank() } ?: existing?.key,
                embedding = if (textChanged) null else existing?.embedding,
                embeddingModelId = if (textChanged) null else existing?.embeddingModelId
            )
            dao.upsert(memory)
            json(JSONObject().put("memory", memoryJson(memory)))
        }
    }

    private fun handleDeleteMemory(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.memoryDao()
            val memory = dao.get(body.optString("id")) ?: return@runBlocking notFound("memory")
            dao.upsert(memory.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- personas

    private fun handleListPersonas(): Response = runBlocking {
        val personas = app.container.db.personaDao().observePersonas().first()
        json(JSONObject().put("personas", JSONArray(personas.map { personaJson(it) })))
    }

    private fun handleSavePersona(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.personaDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.getPersona(it) }
            // Same rule as delete below, which the app's Library screen already enforces: a
            // built-in is a fixed reference point, and editing one through the web app would
            // silently redefine "Vervan" for every chat that inherits it. Duplicate it instead.
            if (existing?.isBuiltIn == true) {
                return@runBlocking badRequest("Built-in personas can't be edited — duplicate it and edit the copy")
            }
            val name = body.optString("name", existing?.name.orEmpty()).trim()
            if (name.isBlank()) return@runBlocking badRequest("A persona needs a name")
            val persona = (existing ?: Persona(name = name, systemInstruction = "")).copy(
                name = name,
                description = body.optString("description", existing?.description.orEmpty()),
                systemInstruction = body.optString("system_instruction", existing?.systemInstruction.orEmpty()),
                tone = body.optString("tone", existing?.tone ?: "NEUTRAL"),
                formality = body.optString("formality", existing?.formality ?: "NEUTRAL"),
                conciseness = body.optString("conciseness", existing?.conciseness ?: "NORMAL"),
                creativity = body.optDouble("creativity", (existing?.creativity ?: 0.5f).toDouble()).toFloat(),
                responseLength = body.optString("response_length", existing?.responseLength ?: "BALANCED"),
                language = body.optString("language", existing?.language.orEmpty())
            )
            dao.upsert(persona)
            json(JSONObject().put("persona", personaJson(persona)))
        }
    }

    private fun handleDeletePersona(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.personaDao()
            val persona = dao.getPersona(body.optString("id")) ?: return@runBlocking notFound("persona")
            // Built-ins are undeletable in the app's own Library screen; the web UI is a second
            // view of the same data, not a way around that rule.
            if (persona.isBuiltIn) return@runBlocking badRequest("Built-in personas can't be deleted")
            dao.upsert(persona.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- prompt templates

    private fun handleListTemplates(): Response = runBlocking {
        val templates = app.container.db.promptTemplateDao().observeAll().first()
        json(JSONObject().put("templates", JSONArray(templates.map { templateJson(it) })))
    }

    private fun handleSaveTemplate(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.promptTemplateDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val name = body.optString("name", existing?.name.orEmpty()).trim().removePrefix("/")
            val templateBody = body.optString("body", existing?.body.orEmpty())
            // Mirrors the built-in persona rule above and the delete guard below: /shorten and
            // friends are fixed built-ins, and silently rewriting one through the web app would
            // change what that slash command does everywhere, including on the phone.
            if (existing?.isBuiltIn == true) {
                return@runBlocking badRequest("Built-in prompt templates can't be edited — duplicate it and edit the copy")
            }
            if (name.isBlank()) return@runBlocking badRequest("A template needs a command name")
            if (templateBody.isBlank()) return@runBlocking badRequest("A template needs a body")
            val template = (existing ?: PromptTemplate(name = name, body = templateBody)).copy(
                name = name,
                description = body.optString("description", existing?.description.orEmpty()),
                body = templateBody
            )
            dao.upsert(template)
            json(JSONObject().put("template", templateJson(template)))
        }
    }

    private fun handleDeleteTemplate(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.promptTemplateDao()
            val template = dao.get(body.optString("id")) ?: return@runBlocking notFound("template")
            if (template.isBuiltIn) return@runBlocking badRequest("Built-in templates can't be deleted")
            dao.upsert(template.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- saved outputs

    private fun handleListSavedOutputs(): Response = runBlocking {
        val outputs = app.container.db.savedOutputDao().observeAll().first()
        json(JSONObject().put("saved_outputs", JSONArray(outputs.map { savedOutputJson(it) })))
    }

    private fun handleSaveSavedOutput(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.savedOutputDao()
            // SavedOutputDao has no get(id) — it is a small, fully-observed list in the app — so an
            // edit resolves the existing row from the observed list. Without this an edit would
            // insert a second row under a fresh id instead of replacing the first.
            val existing = body.optString("id").takeIf { it.isNotBlank() }
                ?.let { id -> dao.observeAll().first().find { it.id == id } }
            val content = body.optString("content", existing?.content.orEmpty()).trim()
            if (content.isBlank()) return@runBlocking badRequest("Nothing to save")
            val output = (existing ?: SavedOutput(content = content)).copy(
                content = content,
                label = body.optString("label", existing?.label.orEmpty()),
                sourceChatId = body.optString("source_chat_id").takeIf { it.isNotBlank() } ?: existing?.sourceChatId
            )
            dao.upsert(output)
            json(JSONObject().put("saved_output", savedOutputJson(output)))
        }
    }

    private fun handleDeleteSavedOutput(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.savedOutputDao()
            val id = body.optString("id")
            val output = dao.observeAll().first().find { it.id == id }
                ?: return@runBlocking notFound("saved output")
            dao.upsert(output.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- projects

    private fun handleListProjects(): Response = runBlocking {
        val projects = app.container.db.projectDao().observeAll().first()
        json(JSONObject().put("projects", JSONArray(projects.map { projectJson(it) })))
    }

    private fun handleSaveProject(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.projectDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val name = body.optString("name", existing?.name.orEmpty()).trim()
            if (name.isBlank()) return@runBlocking badRequest("A project needs a name")
            val project = (existing ?: Project(name = name)).copy(
                name = name,
                instructions = body.optString("instructions", existing?.instructions.orEmpty()),
                personaId = body.optString("persona_id").takeIf { it.isNotBlank() } ?: existing?.personaId,
                workspaceId = body.optString("workspace_id").takeIf { it.isNotBlank() }
                    ?: existing?.workspaceId ?: Workspace.DEFAULT_WORKSPACE_ID
            )
            dao.upsert(project)
            json(JSONObject().put("project", projectJson(project)))
        }
    }

    private fun handleDeleteProject(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.projectDao()
            val project = dao.get(body.optString("id")) ?: return@runBlocking notFound("project")
            dao.upsert(project.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- workspaces

    private fun handleListWorkspaces(): Response = runBlocking {
        val workspaces = app.container.db.workspaceDao().observeAll().first()
        val activeId = app.container.settingsRepository.activeWorkspaceId.first()
        json(
            JSONObject()
                .put("workspaces", JSONArray(workspaces.map { workspaceJson(it) }))
                .put("active_workspace_id", activeId)
        )
    }

    private fun handleSaveWorkspace(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.workspaceDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val name = body.optString("name", existing?.name.orEmpty()).trim()
            if (name.isBlank()) return@runBlocking badRequest("A workspace needs a name")
            // A workspace requires a persona; a brand-new one created from the browser inherits
            // whichever persona the default workspace uses rather than inventing one.
            val fallbackPersona = existing?.personaId
                ?: dao.getDefault()?.personaId
                ?: app.container.db.personaDao().observePersonas().first().firstOrNull()?.id
                ?: return@runBlocking badRequest("No persona available to attach to a new workspace")
            // The Default Workspace's identity is fixed — the app's own Workspaces screen hides
            // "Edit workspace" and archive for it (only delete was blocked here before, so the web
            // app could still rename it, repoint its persona, or archive it and strand every chat
            // that lives there). Auto-title stays editable: the native screen leaves that switch
            // outside its isDefault guard too, because it's a preference, not identity.
            val isDefaultWorkspace = existing?.isDefault == true
            val workspace = (existing ?: Workspace(name = name, personaId = fallbackPersona)).copy(
                name = if (isDefaultWorkspace) existing.name else name,
                description = if (isDefaultWorkspace) existing.description
                    else body.optString("description", existing?.description.orEmpty()),
                personaId = if (isDefaultWorkspace) existing.personaId
                    else body.optString("persona_id").takeIf { it.isNotBlank() } ?: fallbackPersona,
                archived = if (isDefaultWorkspace) false else body.optBoolean("archived", existing?.archived ?: false),
                autoTitleGeneration = body.optBoolean("auto_title", existing?.autoTitleGeneration ?: false),
                updatedAt = System.currentTimeMillis()
            )
            dao.upsert(workspace)
            if (body.optBoolean("activate")) {
                app.container.settingsRepository.setActiveWorkspaceId(workspace.id)
            }
            json(JSONObject().put("workspace", workspaceJson(workspace)))
        }
    }

    private fun handleDeleteWorkspace(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.workspaceDao()
            val workspace = dao.get(body.optString("id")) ?: return@runBlocking notFound("workspace")
            // Mirrors the app's own rule: the Default Workspace is permanent, because every chat,
            // folder and project falls back to it.
            if (workspace.isDefault) return@runBlocking badRequest("The default workspace can't be deleted")
            dao.upsert(workspace.copy(archived = true, updatedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- folders

    private fun handleListFolders(): Response = runBlocking {
        val folders = app.container.db.folderDao().observeAll().first()
        json(JSONObject().put("folders", JSONArray(folders.map { folderJson(it) })))
    }

    private fun handleSaveFolder(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.folderDao()
            val existing = body.optString("id").takeIf { it.isNotBlank() }?.let { dao.get(it) }
            val name = body.optString("name", existing?.name.orEmpty()).trim()
            if (name.isBlank()) return@runBlocking badRequest("A folder needs a name")
            val folder = (existing ?: Folder(name = name)).copy(
                name = name,
                color = body.optString("color", existing?.color ?: "#E8A33D"),
                workspaceId = body.optString("workspace_id").takeIf { it.isNotBlank() }
                    ?: existing?.workspaceId ?: Workspace.DEFAULT_WORKSPACE_ID
            )
            dao.upsert(folder)
            json(JSONObject().put("folder", folderJson(folder)))
        }
    }

    private fun handleDeleteFolder(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val dao = app.container.db.folderDao()
            val folder = dao.get(body.optString("id")) ?: return@runBlocking notFound("folder")
            dao.upsert(folder.copy(deletedAt = System.currentTimeMillis()))
            ok()
        }
    }

    // ---------------------------------------------------------------- tools

    private fun handleListToolRuns(): Response = runBlocking {
        val runs = app.container.db.toolRunDao().observeRecent(TOOL_RUN_LIMIT).first()
        json(JSONObject().put("tool_runs", JSONArray(runs.map { toolRunJson(it) })))
    }

    /**
     * Runs one on-device tool directly, outside a conversation — the browser equivalent of the
     * app's Tools tab.
     *
     * Write-capable tools are refused unless "Let API clients use this device's tools" *and* its
     * nested write switch are both on. In the app a write tool is gated behind an explicit
     * confirmation dialog on the turn it is proposed; there is no such dialog here, so the
     * settings toggle is the only standing consent, and without it this endpoint stays read-only.
     */
    private fun handleRunTool(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val settings = app.container.settingsRepository
            if (!settings.apiServerAppTools.first()) {
                return@runBlocking error(
                    Response.Status.FORBIDDEN,
                    "Tools are off for API clients — turn on \"Let API clients use this device's tools\" in Settings"
                )
            }
            val name = body.optString("tool").trim()
            val definition = ToolRegistry.find(name) ?: return@runBlocking notFound("tool \"$name\"")
            if (definition.risk != com.vervan.chat.tools.ToolRisk.READ_ONLY &&
                !settings.apiServerAllowWriteTools.first()
            ) {
                return@runBlocking error(
                    Response.Status.FORBIDDEN,
                    "\"$name\" can change data on this device — allow write tools in Settings first"
                )
            }
            val params = body.optJSONObject("params") ?: JSONObject()
            val result = runCatching { definition.execute(app, params) }.getOrElse { t ->
                if (t is VirtualMachineError) throw t
                com.vervan.chat.tools.ToolResult(success = false, summary = t.toUserMessage())
            }
            // Same audit row the in-chat path writes: a tool run started from the browser has to be
            // as visible in the app's tool history as one the model asked for.
            runCatching {
                app.container.db.toolAuditDao().insert(
                    com.vervan.chat.data.db.entities.ToolAudit(
                        toolName = name,
                        paramsJson = params.toString(),
                        success = result.success,
                        summary = result.summary,
                        risk = definition.risk.name
                    )
                )
            }.onFailure { Log.w(TAG, "tool audit write failed for $name", it) }
            json(
                JSONObject()
                    .put("tool", name)
                    .put("success", result.success)
                    .put("summary", result.summary)
            )
        }
    }

    // ---------------------------------------------------------------- models

    private fun handleListModels(): Response = runBlocking { json(modelStateJson()) }

    private fun handleLoadModel(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val model = app.container.db.modelDao().get(body.optString("id"))
                ?: return@runBlocking notFound("model")
            val result = app.container.modelLoadCoordinator.loadManually(model)
            if (!result.success) {
                return@runBlocking error(
                    Response.Status.SERVICE_UNAVAILABLE,
                    result.errorMessage ?: "Could not load ${model.displayName}"
                )
            }
            json(modelStateJson())
        }
    }

    private fun handleUnloadModel(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val role = runCatching { ModelRole.valueOf(body.optString("role", "GENERATION")) }
                .getOrElse { return@runBlocking badRequest("Unknown model role") }
            app.container.modelLoadCoordinator.unload(role)
            json(modelStateJson())
        }
    }

    private fun handleSetDefaultModel(session: IHTTPSession): Response = withBody(session) { body ->
        runBlocking {
            val model = app.container.db.modelDao().get(body.optString("id"))
                ?: return@runBlocking notFound("model")
            app.container.modelLoadCoordinator.setDefault(model)
            json(modelStateJson())
        }
    }

    /** Residency plus capabilities for both roles, in the shape the web UI's model panel reads.
     * `ttl_expires_at` is what lets the browser show a live countdown to an idle unload rather than
     * discovering after the fact that the model went away. */
    private suspend fun modelStateJson(): JSONObject {
        val coordinator = app.container.modelLoadCoordinator
        val models = app.container.db.modelDao().observeModels().first()
        val state = coordinator.state.value
        val roles = JSONObject()
        listOf(ModelRole.GENERATION, ModelRole.EMBEDDING).forEach { role ->
            val info = state[role]
            roles.put(
                role.name.lowercase(),
                JSONObject()
                    // currentModelId is only meaningful once the role reached READY — during a
                    // load it already names the incoming model, which would make the UI claim a
                    // model is loaded a few seconds before it is.
                    .put(
                        "loaded_model_id",
                        info?.currentModelId?.takeIf { info.phase == ModelLoadPhase.READY } ?: JSONObject.NULL
                    )
                    .put("default_model_id", info?.defaultModelId ?: JSONObject.NULL)
                    .put("phase", (info?.phase ?: ModelLoadPhase.UNLOADED).name)
                    .put("loading_model_id", info?.loadingModelId ?: JSONObject.NULL)
                    .put("ttl_expires_at", coordinator.ttlDeadlineAt(role) ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("roles", roles)
            .put("ttl_seconds", app.container.settingsRepository.apiModelTtlSeconds.first())
            .put(
                "models",
                JSONArray(
                    models.map { model ->
                        JSONObject()
                            .put("id", model.id)
                            .put("name", model.displayName)
                            .put("role", model.role.name)
                            .put("engine", model.engine.name)
                            .put("size_bytes", model.fileSizeBytes)
                            .put("is_default", model.isActive)
                            .put("context_tokens", model.contextTokens ?: JSONObject.NULL)
                            .put(
                                "capabilities",
                                JSONObject()
                                    .put("vision", model.supportsVision == true)
                                    .put("audio", model.supportsAudio == true)
                                    .put("tools", model.supportsTools == true)
                                    .put("thinking", model.supportsThinking == true)
                            )
                    }
                )
            )
    }

    // ---------------------------------------------------------------- entity → JSON

    private fun noteJson(note: Note): JSONObject = JSONObject()
        .put("id", note.id).put("title", note.title).put("content", note.content)
        .put("pinned", note.pinned).put("tags", note.tags)
        .put("project_id", note.projectId ?: JSONObject.NULL)
        .put("folder_id", note.folderId ?: JSONObject.NULL)
        .put("created_at", note.createdAt).put("updated_at", note.updatedAt)

    private fun memoryJson(memory: Memory): JSONObject = JSONObject()
        .put("id", memory.id).put("text", memory.text)
        .put("scope", memory.scope.name)
        .put("scope_ref_id", memory.scopeRefId ?: JSONObject.NULL)
        .put("enabled", memory.enabled)
        .put("key", memory.key ?: JSONObject.NULL)
        .put("created_at", memory.createdAt)

    private fun personaJson(persona: Persona): JSONObject = JSONObject()
        .put("id", persona.id).put("name", persona.name)
        .put("description", persona.description)
        .put("system_instruction", persona.systemInstruction)
        .put("built_in", persona.isBuiltIn)
        .put("tone", persona.tone).put("formality", persona.formality)
        .put("conciseness", persona.conciseness).put("creativity", persona.creativity)
        .put("response_length", persona.responseLength).put("language", persona.language)

    private fun templateJson(template: PromptTemplate): JSONObject = JSONObject()
        .put("id", template.id).put("name", template.name)
        .put("description", template.description).put("body", template.body)
        .put("built_in", template.isBuiltIn)

    private fun savedOutputJson(output: SavedOutput): JSONObject = JSONObject()
        .put("id", output.id).put("content", output.content).put("label", output.label)
        .put("source_chat_id", output.sourceChatId ?: JSONObject.NULL)
        .put("created_at", output.createdAt)

    private fun projectJson(project: Project): JSONObject = JSONObject()
        .put("id", project.id).put("name", project.name)
        .put("instructions", project.instructions)
        .put("persona_id", project.personaId ?: JSONObject.NULL)
        .put("workspace_id", project.workspaceId)
        .put("created_at", project.createdAt)

    private fun workspaceJson(workspace: Workspace): JSONObject = JSONObject()
        .put("id", workspace.id).put("name", workspace.name)
        .put("description", workspace.description)
        .put("persona_id", workspace.personaId)
        .put("is_default", workspace.isDefault).put("archived", workspace.archived)
        .put("auto_title", workspace.autoTitleGeneration)
        .put("updated_at", workspace.updatedAt)

    private fun folderJson(folder: Folder): JSONObject = JSONObject()
        .put("id", folder.id).put("name", folder.name).put("color", folder.color)
        .put("workspace_id", folder.workspaceId).put("created_at", folder.createdAt)

    private fun toolRunJson(run: com.vervan.chat.data.db.entities.ToolRun): JSONObject = JSONObject()
        .put("id", run.id).put("route", run.toolRoute).put("name", run.toolName)
        .put("input", run.input.take(500)).put("output", run.output.take(4000))
        .put("state", run.state.name)
        .put("error", run.errorMessage ?: JSONObject.NULL)
        .put("model_name", run.modelName ?: JSONObject.NULL)
        .put("created_at", run.createdAt).put("updated_at", run.updatedAt)

    // ---------------------------------------------------------------- plumbing

    /** Reads and parses a JSON body, or short-circuits with the appropriate 4xx. Mirrors
     * [LocalApiServer.readJsonBody]'s size checks — this class's routes are small JSON writes, so
     * the cap is correspondingly smaller than the chat-completions one. */
    private inline fun withBody(
        session: IHTTPSession,
        maxBytes: Long = MAX_BODY_BYTES,
        block: (JSONObject) -> Response
    ): Response {
        val lengthHeader = session.headers["content-length"] ?: session.headers["Content-Length"]
        val declared = lengthHeader?.toLongOrNull()
            ?: return badRequest("A valid Content-Length header is required")
        if (declared < 0 || declared > maxBytes) {
            return error(Response.Status.PAYLOAD_TOO_LARGE, "Request body too large")
        }
        val map = HashMap<String, String>()
        runCatching { session.parseBody(map) }.onFailure {
            return badRequest("Could not read the request body")
        }
        val parsed = runCatching { JSONObject(map["postData"] ?: "{}") }
            .getOrElse { return badRequest("Request body must be valid JSON") }
        return block(parsed)
    }

    private fun json(body: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body.toString())

    private fun ok(): Response = json(JSONObject().put("ok", true))

    private fun error(status: Response.Status, message: String): Response =
        newFixedLengthResponse(
            status, "application/json",
            openAiErrorJson(message, ErrorType.INVALID_REQUEST, null, null).toString()
        )

    private fun badRequest(message: String): Response = error(Response.Status.BAD_REQUEST, message)

    private fun notFound(what: String): Response =
        newFixedLengthResponse(
            Response.Status.NOT_FOUND, "application/json",
            openAiErrorJson("No such $what", ErrorType.NOT_FOUND, ErrorCode.MODEL_NOT_FOUND, null).toString()
        )
}
