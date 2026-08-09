package com.vervan.chat.data.backup

import com.vervan.chat.data.db.AppDatabase
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Expense
import com.vervan.chat.data.db.entities.FlashcardSet
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.MemoryScope
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.StudyCard
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.data.db.entities.TranscriptionProject
import com.vervan.chat.data.db.entities.TtsProject
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.system.toUserMessage
import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON export/import for user-authored Room content. Model files,
 * imported document files and media attachments are deliberately not included — those are large
 * binary assets tied to on-device paths, re-importing them belongs to Models/Knowledge, not
 * a content backup. Knowledge-base definitions are included, but their imported document
 * files are not. Hand-rolled org.json mapping per entity (matches how the rest
 * of the app already encodes JSON — Workflow steps, tool-call payloads) rather than pulling
 * in a serialization library for one screen.
 */
object BackupManager {
    private const val FORMAT_VERSION = 2
    private const val MIN_SUPPORTED_FORMAT_VERSION = 1
    private const val MAX_ITEMS_PER_COLLECTION = 100_000

    suspend fun export(db: AppDatabase, out: OutputStream) {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("includesBinaryFiles", false)

        root.put("workspaces", JSONArray(db.workspaceDao().observeAll().firstList().map { workspaceToJson(it) }))
        // Incognito mode — a temporary chat is excluded from export entirely, same
        // as it's excluded from search and smart collections.
        val exportableChats = db.chatDao().observeAllChats().firstList().filterNot { it.isTemporary }
        root.put("chats", JSONArray(exportableChats.map { chatToJson(it) }))
        val allMessages = exportableChats.flatMap { db.messageDao().getMessages(it.id) }
        root.put("messages", JSONArray(allMessages.map { messageToJson(it) }))
        root.put("notes", JSONArray(db.noteDao().observeAll().firstList().map { noteToJson(it) }))
        root.put("personas", JSONArray(db.personaDao().observePersonas().firstList().filter { !it.isBuiltIn }.map { personaToJson(it) }))
        root.put("templates", JSONArray(db.promptTemplateDao().observeAll().firstList().filter { !it.isBuiltIn }.map { templateToJson(it) }))
        root.put("workflows", JSONArray(db.workflowDao().observeAll().firstList().filter { !it.isBuiltIn }.map { workflowToJson(it) }))
        root.put("memories", JSONArray(db.memoryDao().observeAll().firstList().map { memoryToJson(it) }))
        root.put("projects", JSONArray(db.projectDao().observeAll().firstList().map { projectToJson(it) }))
        root.put("folders", JSONArray(db.folderDao().observeAll().firstList().map { folderToJson(it) }))
        root.put("savedOutputs", JSONArray(db.savedOutputDao().observeAll().firstList().map { savedOutputToJson(it) }))
        root.put("flashcardSets", JSONArray(db.flashcardSetDao().observeAll().firstList().map { flashcardSetToJson(it) }))
        root.put("studyCards", JSONArray(db.studyCardDao().observeAll().firstList().map { studyCardToJson(it) }))
        root.put("knowledgeBases", JSONArray(db.knowledgeBaseDao().observeAll().firstList().map { knowledgeBaseToJson(it) }))
        root.put("toolRuns", JSONArray(db.toolRunDao().observeAll().firstList().map { toolRunToJson(it) }))
        root.put("expenses", JSONArray(db.expenseDao().observeAll().firstList().map { expenseToJson(it) }))
        root.put("ttsProjects", JSONArray(db.ttsProjectDao().observeAll().firstList().map { ttsProjectToJson(it) }))
        root.put(
            "transcriptionProjects",
            JSONArray(db.transcriptionProjectDao().observeAll().firstList().map { transcriptionProjectToJson(it) })
        )

        out.writer().use { it.write(root.toString(2)) }
    }

    /**
     * same JSON shape as [export], scoped to one workspace's own chats/messages/
     * folders (its knowledge bases and documents are excluded, same as the full export already
     * excludes them everywhere — see this object's class doc). Every other category (notes,
     * personas, templates, workflows, memories, projects, saved outputs, flashcards) isn't
     * workspace-scoped at all in this schema, so it's simply left out rather than guessed at.
     * [import] reads either shape identically — it just upserts whatever categories are present.
     */
    suspend fun exportWorkspace(db: AppDatabase, workspaceId: String, out: OutputStream) {
        val workspace = db.workspaceDao().get(workspaceId) ?: throw IllegalArgumentException("No such workspace")
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("includesBinaryFiles", false)

        root.put("workspaces", JSONArray(listOf(workspaceToJson(workspace))))
        val exportableChats = db.chatDao().getForWorkspace(workspaceId).filterNot { it.isTemporary }
        root.put("chats", JSONArray(exportableChats.map { chatToJson(it) }))
        val allMessages = exportableChats.flatMap { db.messageDao().getMessages(it.id) }
        root.put("messages", JSONArray(allMessages.map { messageToJson(it) }))
        root.put("folders", JSONArray(db.folderDao().observeForWorkspace(workspaceId).firstList().map { folderToJson(it) }))

        out.writer().use { it.write(root.toString(2)) }
    }

    /** Returns a short summary of what was restored, or throws with a readable message on
     * malformed input. Every row upserts on its own primary key, so importing the same file
     * twice is a no-op the second time, not a duplicate. */
    suspend fun import(db: AppDatabase, input: InputStream): BackupSummary {
        // The doc comment above claims "throws with a readable message on malformed input", but
        // nothing here actually did that translation — a missing field (org.json's own
        // JSONException, e.g. "No value for createdAt") or an unrecognized enum value from a
        // newer backup format (IllegalArgumentException from MessageRole.valueOf etc.) reached
        // the caller as whatever raw internal message the parser happened to produce. Wrapping
        // the whole parse in one place makes that claim actually true.
        try {
            return importUnchecked(db, input)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            throw IllegalArgumentException("This backup file couldn't be read — it may be corrupted or from an incompatible version. (${t.toUserMessage()})", t)
        }
    }

    private suspend fun importUnchecked(db: AppDatabase, input: InputStream): BackupSummary {
        val bytes = input.readBytesLimited(ImportLimits.MAX_BACKUP_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        val formatVersion = root.optInt("formatVersion", 0)
        require(formatVersion in MIN_SUPPORTED_FORMAT_VERSION..FORMAT_VERSION) {
            when {
                formatVersion == 0 -> "The selected file is not a versioned Vervan backup."
                formatVersion > FORMAT_VERSION ->
                    "This backup was created by a newer Vervan version (format $formatVersion). Update Vervan before restoring it."
                else -> "Backup format $formatVersion is no longer supported."
            }
        }
        val workspaces = root.optJSONArray("workspaces")?.toObjectList()?.map { workspaceFromJson(it) } ?: emptyList()
        val chats = root.optJSONArray("chats")?.toObjectList()?.map { chatFromJson(it) } ?: emptyList()
        val messages = root.optJSONArray("messages")?.toObjectList()?.map { messageFromJson(it) } ?: emptyList()
        val notes = root.optJSONArray("notes")?.toObjectList()?.map { noteFromJson(it) } ?: emptyList()
        val personas = root.optJSONArray("personas")?.toObjectList()?.map { personaFromJson(it) } ?: emptyList()
        val templates = root.optJSONArray("templates")?.toObjectList()?.map { templateFromJson(it) } ?: emptyList()
        val workflows = root.optJSONArray("workflows")?.toObjectList()?.map { workflowFromJson(it) } ?: emptyList()
        val memories = root.optJSONArray("memories")?.toObjectList()?.map { memoryFromJson(it) } ?: emptyList()
        val projects = root.optJSONArray("projects")?.toObjectList()?.map { projectFromJson(it) } ?: emptyList()
        val folders = root.optJSONArray("folders")?.toObjectList()?.map { folderFromJson(it) } ?: emptyList()
        val savedOutputs = root.optJSONArray("savedOutputs")?.toObjectList()?.map { savedOutputFromJson(it) } ?: emptyList()
        val flashcardSets = root.optJSONArray("flashcardSets")?.toObjectList()?.map { flashcardSetFromJson(it) } ?: emptyList()
        val studyCards = root.optJSONArray("studyCards")?.toObjectList()?.map { studyCardFromJson(it) } ?: emptyList()
        val knowledgeBases = root.optJSONArray("knowledgeBases")?.toObjectList()?.map { knowledgeBaseFromJson(it) } ?: emptyList()
        val toolRuns = root.optJSONArray("toolRuns")?.toObjectList()?.map { toolRunFromJson(it) } ?: emptyList()
        val expenses = root.optJSONArray("expenses")?.toObjectList()?.map { expenseFromJson(it) } ?: emptyList()
        val ttsProjects = root.optJSONArray("ttsProjects")?.toObjectList()?.map { ttsProjectFromJson(it) } ?: emptyList()
        val transcriptionProjects = root.optJSONArray("transcriptionProjects")
            ?.toObjectList()
            ?.map { transcriptionProjectFromJson(it) }
            ?: emptyList()

        db.withTransaction {
            workspaces.forEach { db.workspaceDao().upsert(it) }
            chats.forEach { db.chatDao().upsert(it) }
            messages.forEach { db.messageDao().upsert(it) }
            notes.forEach { db.noteDao().upsert(it) }
            personas.forEach { db.personaDao().upsert(it) }
            templates.forEach { db.promptTemplateDao().upsert(it) }
            workflows.forEach { db.workflowDao().upsert(it) }
            memories.forEach { db.memoryDao().upsert(it) }
            projects.forEach { db.projectDao().upsert(it) }
            folders.forEach { db.folderDao().upsert(it) }
            savedOutputs.forEach { db.savedOutputDao().upsert(it) }
            flashcardSets.forEach { db.flashcardSetDao().upsert(it) }
            db.studyCardDao().insertAll(studyCards)
            knowledgeBases.forEach { db.knowledgeBaseDao().upsert(it) }
            toolRuns.forEach { db.toolRunDao().upsert(it) }
            expenses.forEach { db.expenseDao().upsert(it) }
            ttsProjects.forEach { db.ttsProjectDao().upsert(it) }
            transcriptionProjects.forEach { db.transcriptionProjectDao().upsert(it) }
        }

        return BackupSummary(
            chats.size, notes.size, personas.size, templates.size, workflows.size, memories.size,
            projects.size, folders.size, savedOutputs.size, flashcardSets.size, studyCards.size,
            knowledgeBases.size, workspaces.size, toolRuns.size, expenses.size, ttsProjects.size,
            transcriptionProjects.size
        )
    }

    private suspend fun <T> Flow<List<T>>.firstList(): List<T> = first()

    private fun JSONArray.toObjectList(): List<JSONObject> {
        require(length() <= MAX_ITEMS_PER_COLLECTION) { "Backup contains too many records in one collection" }
        return (0 until length()).map { getJSONObject(it) }
    }

    private fun workspaceToJson(w: Workspace) = JSONObject().apply {
        put("id", w.id); put("name", w.name); put("description", w.description); put("personaId", w.personaId)
        put("isDefault", w.isDefault); put("archived", w.archived)
        put("createdAt", w.createdAt); put("updatedAt", w.updatedAt); put("lastActiveAt", w.lastActiveAt)
        put("autoTitleGeneration", w.autoTitleGeneration)
        put("lockEnabled", w.lockEnabled)
        put("defaultProfile", w.defaultProfile ?: JSONObject.NULL)
        put("defaultKnowledgeBaseIds", w.defaultKnowledgeBaseIds)
    }
    private fun workspaceFromJson(o: JSONObject) = Workspace(
        id = o.getString("id"),
        name = o.getString("name"),
        description = o.optString("description"),
        personaId = o.optString("personaId", "builtin-general"),
        isDefault = o.optBoolean("isDefault"),
        archived = o.optBoolean("archived"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        lastActiveAt = o.getLong("lastActiveAt"),
        autoTitleGeneration = o.optBoolean("autoTitleGeneration"),
        lockEnabled = o.optBoolean("lockEnabled"),
        defaultProfile = o.optStringOrNull("defaultProfile"),
        defaultKnowledgeBaseIds = o.optString("defaultKnowledgeBaseIds")
    )

    private fun chatToJson(c: Chat) = JSONObject().apply {
        put("id", c.id); put("title", c.title); put("personaId", c.personaId ?: JSONObject.NULL)
        put("workspaceId", c.workspaceId)
        put("modelId", c.modelId ?: JSONObject.NULL); put("projectId", c.projectId ?: JSONObject.NULL)
        put("folderId", c.folderId ?: JSONObject.NULL)
        put("draft", c.draft); put("pinned", c.pinned); put("archived", c.archived)
        put("sourceGrounded", c.sourceGrounded); put("toolsEnabled", c.toolsEnabled)
        put("thinkingMode", c.thinkingMode ?: JSONObject.NULL); put("profile", c.profile)
        put("activeLeafId", c.activeLeafId ?: JSONObject.NULL); put("knowledgeBaseIds", c.knowledgeBaseIds)
        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
        put("temperature", c.temperature ?: JSONObject.NULL)
        put("topP", c.topP ?: JSONObject.NULL)
        put("topK", c.topK ?: JSONObject.NULL)
        put("scrollAnchorMessageId", c.scrollAnchorMessageId ?: JSONObject.NULL)
        put("scrollAnchorOffsetPx", c.scrollAnchorOffsetPx)
        put("titleIsCustom", c.titleIsCustom)
        put("previousTitle", c.previousTitle ?: JSONObject.NULL)
        put("screenshotBlocked", c.screenshotBlocked)
        put("toolOverrides", c.toolOverrides)
        put("contextSummary", c.contextSummary ?: JSONObject.NULL)
        put("summaryCoversUpToMessageId", c.summaryCoversUpToMessageId ?: JSONObject.NULL)
    }
    private fun chatFromJson(o: JSONObject) = Chat(
        id = o.getString("id"), title = o.getString("title"), workspaceId = o.optString("workspaceId", Workspace.DEFAULT_WORKSPACE_ID), personaId = o.optStringOrNull("personaId"),
        modelId = o.optStringOrNull("modelId"), projectId = o.optStringOrNull("projectId"),
        folderId = o.optStringOrNull("folderId"),
        draft = o.optString("draft"),
        pinned = o.optBoolean("pinned"), archived = o.optBoolean("archived"), sourceGrounded = o.optBoolean("sourceGrounded"),
        toolsEnabled = o.optBoolean("toolsEnabled"), thinkingMode = o.optStringOrNull("thinkingMode"),
        profile = o.optString("profile", "BALANCED"),
        activeLeafId = o.optStringOrNull("activeLeafId"),
        knowledgeBaseIds = o.optString("knowledgeBaseIds"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        deletedAt = null,
        temperature = o.optFloatOrNull("temperature"),
        topP = o.optFloatOrNull("topP"),
        topK = o.optIntOrNull("topK"),
        scrollAnchorMessageId = o.optStringOrNull("scrollAnchorMessageId"),
        scrollAnchorOffsetPx = o.optInt("scrollAnchorOffsetPx"),
        titleIsCustom = o.optBoolean("titleIsCustom"),
        previousTitle = o.optStringOrNull("previousTitle"),
        screenshotBlocked = o.optBoolean("screenshotBlocked"),
        toolOverrides = o.optString("toolOverrides"),
        contextSummary = o.optStringOrNull("contextSummary"),
        summaryCoversUpToMessageId = o.optStringOrNull("summaryCoversUpToMessageId")
    )

    private fun messageToJson(m: Message) = JSONObject().apply {
        put("id", m.id); put("chatId", m.chatId); put("parentId", m.parentId ?: JSONObject.NULL)
        put("role", m.role.name); put("content", m.content); put("state", m.state.name)
        put("imagePath", m.imagePath ?: JSONObject.NULL)
        put("documentId", m.documentId ?: JSONObject.NULL)
        put("audioPath", m.audioPath ?: JSONObject.NULL)
        put("voiceRecordingPath", m.voiceRecordingPath ?: JSONObject.NULL)
        put("inputModality", m.inputModality)
        put("transcriptMetadataJson", m.transcriptMetadataJson ?: JSONObject.NULL)
        put("outputModalities", m.outputModalities)
        put("playbackMetadataJson", m.playbackMetadataJson ?: JSONObject.NULL)
        put("sourcesJson", m.sourcesJson ?: JSONObject.NULL)
        put("memoryActivityJson", m.memoryActivityJson ?: JSONObject.NULL)
        put("toolCallJson", m.toolCallJson ?: JSONObject.NULL); put("toolResultJson", m.toolResultJson ?: JSONObject.NULL)
        put("createdAt", m.createdAt)
        put("generationMs", m.generationMs ?: JSONObject.NULL)
        put("tokenCount", m.tokenCount ?: JSONObject.NULL)
        put("modelId", m.modelId ?: JSONObject.NULL)
        put("modelName", m.modelName ?: JSONObject.NULL)
        put("backend", m.backend ?: JSONObject.NULL)
        put("profile", m.profile ?: JSONObject.NULL)
        put("thinkingMode", m.thinkingMode ?: JSONObject.NULL)
        put("reaction", m.reaction ?: JSONObject.NULL)
        put("feedbackReason", m.feedbackReason ?: JSONObject.NULL)
    }
    private fun messageFromJson(o: JSONObject) = Message(
        id = o.getString("id"), chatId = o.getString("chatId"), parentId = o.optStringOrNull("parentId"),
        role = MessageRole.valueOf(o.getString("role")), content = o.getString("content"),
        state = MessageState.valueOf(o.optString("state", "COMPLETE")), imagePath = o.optStringOrNull("imagePath"),
        documentId = o.optStringOrNull("documentId"),
        audioPath = o.optStringOrNull("audioPath"),
        voiceRecordingPath = o.optStringOrNull("voiceRecordingPath"),
        inputModality = o.optString("inputModality", "TEXT"),
        transcriptMetadataJson = o.optStringOrNull("transcriptMetadataJson"),
        outputModalities = o.optString("outputModalities", "TEXT"),
        playbackMetadataJson = o.optStringOrNull("playbackMetadataJson"),
        sourcesJson = o.optStringOrNull("sourcesJson"), memoryActivityJson = o.optStringOrNull("memoryActivityJson"),
        toolCallJson = o.optStringOrNull("toolCallJson"),
        toolResultJson = o.optStringOrNull("toolResultJson"), createdAt = o.getLong("createdAt"),
        generationMs = o.optLongOrNull("generationMs"),
        tokenCount = o.optIntOrNull("tokenCount"),
        modelId = o.optStringOrNull("modelId"),
        modelName = o.optStringOrNull("modelName"),
        backend = o.optStringOrNull("backend"),
        profile = o.optStringOrNull("profile"),
        thinkingMode = o.optStringOrNull("thinkingMode"),
        reaction = o.optStringOrNull("reaction"),
        feedbackReason = o.optStringOrNull("feedbackReason")
    )

    private fun noteToJson(n: Note) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("content", n.content); put("projectId", n.projectId ?: JSONObject.NULL)
        put("folderId", n.folderId ?: JSONObject.NULL)
        put("tags", n.tags)
        put("pinned", n.pinned); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
    }
    private fun noteFromJson(o: JSONObject) = Note(
        id = o.getString("id"), title = o.getString("title"), content = o.optString("content"),
        projectId = o.optStringOrNull("projectId"), folderId = o.optStringOrNull("folderId"),
        tags = o.optString("tags"), pinned = o.optBoolean("pinned"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), deletedAt = null
    )

    private fun personaToJson(p: Persona) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("description", p.description); put("systemInstruction", p.systemInstruction)
        put("tone", p.tone); put("formality", p.formality); put("conciseness", p.conciseness)
        put("creativity", p.creativity); put("responseLength", p.responseLength); put("language", p.language)
        put("avatarPath", p.avatarPath ?: JSONObject.NULL)
    }
    private fun personaFromJson(o: JSONObject) = Persona(
        id = o.getString("id"), name = o.getString("name"), description = o.optString("description"),
        systemInstruction = o.getString("systemInstruction"), isBuiltIn = false,
        tone = o.optString("tone", "NEUTRAL"),
        formality = o.optString("formality", "NEUTRAL"),
        conciseness = o.optString("conciseness", "NORMAL"),
        creativity = o.optDouble("creativity", 0.5).toFloat(),
        responseLength = o.optString("responseLength", "BALANCED"),
        language = o.optString("language"),
        avatarPath = o.optStringOrNull("avatarPath")
    )

    private fun templateToJson(t: PromptTemplate) = JSONObject().apply {
        put("id", t.id); put("name", t.name); put("description", t.description); put("body", t.body)
    }
    private fun templateFromJson(o: JSONObject) = PromptTemplate(
        id = o.getString("id"), name = o.getString("name"), description = o.optString("description"),
        body = o.getString("body"), isBuiltIn = false
    )

    private fun workflowToJson(w: Workflow) = JSONObject().apply {
        put("id", w.id); put("name", w.name); put("description", w.description); put("stepsJson", w.stepsJson)
    }
    private fun workflowFromJson(o: JSONObject) = Workflow(
        id = o.getString("id"), name = o.getString("name"), description = o.optString("description"),
        stepsJson = o.getString("stepsJson"), isBuiltIn = false
    )

    private fun memoryToJson(m: Memory) = JSONObject().apply {
        put("id", m.id); put("text", m.text); put("scope", m.scope.name); put("scopeRefId", m.scopeRefId ?: JSONObject.NULL)
        put("enabled", m.enabled); put("createdAt", m.createdAt); put("key", m.key ?: JSONObject.NULL)
    }
    private fun memoryFromJson(o: JSONObject) = Memory(
        id = o.getString("id"), text = o.getString("text"), scope = MemoryScope.valueOf(o.optString("scope", "GLOBAL")),
        scopeRefId = o.optStringOrNull("scopeRefId"), enabled = o.optBoolean("enabled", true),
        createdAt = o.getLong("createdAt"), key = o.optStringOrNull("key")
    )

    private fun projectToJson(p: Project) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("instructions", p.instructions); put("personaId", p.personaId ?: JSONObject.NULL)
        put("workspaceId", p.workspaceId)
        put("createdAt", p.createdAt)
    }
    private fun projectFromJson(o: JSONObject) = Project(
        id = o.getString("id"), name = o.getString("name"), instructions = o.optString("instructions"),
        personaId = o.optStringOrNull("personaId"),
        // Older backups predate project workspaces — land those in the Default Workspace.
        workspaceId = o.optString("workspaceId", Workspace.DEFAULT_WORKSPACE_ID),
        createdAt = o.getLong("createdAt")
    )

    private fun folderToJson(f: Folder) = JSONObject().apply {
        put("id", f.id); put("name", f.name)
        put("workspaceId", f.workspaceId)
        put("defaultPersonaId", f.defaultPersonaId ?: JSONObject.NULL)
        put("defaultModelId", f.defaultModelId ?: JSONObject.NULL)
        put("defaultKnowledgeBaseIds", f.defaultKnowledgeBaseIds)
        put("color", f.color); put("createdAt", f.createdAt)
    }
    private fun folderFromJson(o: JSONObject) = Folder(
        id = o.getString("id"), name = o.getString("name"),
        workspaceId = o.optString("workspaceId", Workspace.DEFAULT_WORKSPACE_ID),
        defaultPersonaId = o.optStringOrNull("defaultPersonaId"),
        defaultModelId = o.optStringOrNull("defaultModelId"),
        defaultKnowledgeBaseIds = o.optString("defaultKnowledgeBaseIds"),
        color = o.optString("color", "#E8A33D"),
        createdAt = o.getLong("createdAt"),
        deletedAt = null
    )

    private fun savedOutputToJson(o: SavedOutput) = JSONObject().apply {
        put("id", o.id); put("content", o.content); put("sourceChatId", o.sourceChatId ?: JSONObject.NULL)
        put("label", o.label); put("createdAt", o.createdAt)
    }
    private fun savedOutputFromJson(o: JSONObject) = SavedOutput(
        id = o.getString("id"),
        content = o.getString("content"),
        sourceChatId = o.optStringOrNull("sourceChatId"),
        label = o.optString("label"),
        createdAt = o.getLong("createdAt"),
        deletedAt = null
    )

    private fun flashcardSetToJson(s: FlashcardSet) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("description", s.description)
        put("createdAt", s.createdAt); put("lastStudiedAt", s.lastStudiedAt ?: JSONObject.NULL)
    }
    private fun flashcardSetFromJson(o: JSONObject) = FlashcardSet(
        id = o.getString("id"),
        name = o.getString("name"),
        description = o.optString("description"),
        createdAt = o.getLong("createdAt"),
        lastStudiedAt = o.optLongOrNull("lastStudiedAt")
    )

    private fun studyCardToJson(c: StudyCard) = JSONObject().apply {
        put("id", c.id); put("setName", c.setName); put("question", c.question); put("answer", c.answer)
        put("timesReviewed", c.timesReviewed); put("timesCorrect", c.timesCorrect); put("createdAt", c.createdAt)
    }
    private fun studyCardFromJson(o: JSONObject) = StudyCard(
        id = o.getString("id"),
        setName = o.getString("setName"),
        question = o.getString("question"),
        answer = o.getString("answer"),
        timesReviewed = o.optInt("timesReviewed"),
        timesCorrect = o.optInt("timesCorrect"),
        createdAt = o.getLong("createdAt")
    )

    private fun knowledgeBaseToJson(k: KnowledgeBase) = JSONObject().apply {
        put("id", k.id); put("name", k.name); put("description", k.description); put("createdAt", k.createdAt)
        put("icon", k.icon); put("color", k.color ?: JSONObject.NULL)
        put("defaultPersonaId", k.defaultPersonaId ?: JSONObject.NULL)
        put("defaultProjectId", k.defaultProjectId ?: JSONObject.NULL)
        put("autoIndex", k.autoIndex)
    }
    private fun knowledgeBaseFromJson(o: JSONObject) = KnowledgeBase(
        id = o.getString("id"),
        name = o.getString("name"),
        description = o.optString("description"),
        createdAt = o.getLong("createdAt"),
        icon = o.optString("icon", "MenuBook"),
        color = o.optStringOrNull("color"),
        defaultPersonaId = o.optStringOrNull("defaultPersonaId"),
        defaultProjectId = o.optStringOrNull("defaultProjectId"),
        autoIndex = o.optBoolean("autoIndex", true)
    )

    private fun toolRunToJson(run: ToolRun) = JSONObject().apply {
        put("id", run.id); put("toolRoute", run.toolRoute); put("toolName", run.toolName)
        put("input", run.input); put("output", run.output); put("state", run.state.name)
        put("errorMessage", run.errorMessage ?: JSONObject.NULL)
        put("modelId", run.modelId ?: JSONObject.NULL); put("modelName", run.modelName ?: JSONObject.NULL)
        put("backend", run.backend ?: JSONObject.NULL)
        put("createdAt", run.createdAt); put("updatedAt", run.updatedAt)
    }
    private fun toolRunFromJson(o: JSONObject) = ToolRun(
        id = o.getString("id"),
        toolRoute = o.getString("toolRoute"),
        toolName = o.getString("toolName"),
        input = o.optString("input"),
        output = o.optString("output"),
        state = ToolRunState.valueOf(o.optString("state", ToolRunState.COMPLETED.name)).let {
            if (it == ToolRunState.RUNNING) ToolRunState.INTERRUPTED else it
        },
        errorMessage = o.optStringOrNull("errorMessage"),
        modelId = o.optStringOrNull("modelId"),
        modelName = o.optStringOrNull("modelName"),
        backend = o.optStringOrNull("backend"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = null
    )

    private fun expenseToJson(expense: Expense) = JSONObject().apply {
        put("id", expense.id); put("merchant", expense.merchant); put("amount", expense.amount)
        put("currency", expense.currency); put("category", expense.category)
        put("paymentMethod", expense.paymentMethod); put("note", expense.note)
        put("date", expense.date); put("createdAt", expense.createdAt)
    }
    private fun expenseFromJson(o: JSONObject) = Expense(
        id = o.getString("id"),
        merchant = o.getString("merchant"),
        amount = o.getDouble("amount"),
        currency = o.optString("currency"),
        category = o.optString("category"),
        paymentMethod = o.optString("paymentMethod"),
        note = o.optString("note"),
        date = o.getLong("date"),
        createdAt = o.getLong("createdAt")
    )

    private fun ttsProjectToJson(project: TtsProject) = JSONObject().apply {
        put("id", project.id); put("title", project.title); put("sourceText", project.sourceText)
        put("engine", project.engine); put("voiceVariant", project.voiceVariant)
        put("language", project.language); put("audioPath", project.audioPath)
        put("durationMs", project.durationMs); put("createdAt", project.createdAt)
    }
    private fun ttsProjectFromJson(o: JSONObject) = TtsProject(
        id = o.getString("id"),
        title = o.getString("title"),
        sourceText = o.getString("sourceText"),
        engine = o.getString("engine"),
        voiceVariant = o.getString("voiceVariant"),
        language = o.getString("language"),
        audioPath = o.getString("audioPath"),
        durationMs = o.getLong("durationMs"),
        createdAt = o.getLong("createdAt")
    )

    private fun transcriptionProjectToJson(project: TranscriptionProject) = JSONObject().apply {
        put("id", project.id); put("fileName", project.fileName); put("audioPath", project.audioPath)
        put("durationMs", project.durationMs); put("transcript", project.transcript)
        put("engine", project.engine); put("modelVariant", project.modelVariant); put("status", project.status)
        put("errorMessage", project.errorMessage ?: JSONObject.NULL)
        put("segmentsJson", project.segmentsJson ?: JSONObject.NULL)
        put("createdAt", project.createdAt); put("updatedAt", project.updatedAt)
    }
    private fun transcriptionProjectFromJson(o: JSONObject) = TranscriptionProject(
        id = o.getString("id"),
        fileName = o.getString("fileName"),
        audioPath = o.getString("audioPath"),
        durationMs = o.getLong("durationMs"),
        transcript = o.optString("transcript"),
        engine = o.optString("engine", "WHISPER_CPP"),
        modelVariant = o.getString("modelVariant"),
        status = o.optString("status", "DONE").let {
            if (it == "PENDING" || it == "TRANSCRIBING") "CANCELLED" else it
        },
        errorMessage = o.optStringOrNull("errorMessage"),
        segmentsJson = o.optStringOrNull("segmentsJson"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (!has(key) || isNull(key)) null else getDouble(key).toFloat()
}

data class BackupSummary(
    val chats: Int, val notes: Int, val personas: Int,
    val templates: Int, val workflows: Int, val memories: Int, val projects: Int,
    val folders: Int = 0, val savedOutputs: Int = 0, val flashcardSets: Int = 0,
    val studyCards: Int = 0, val knowledgeBases: Int = 0, val workspaces: Int = 0,
    val toolRuns: Int = 0, val expenses: Int = 0, val ttsProjects: Int = 0,
    val transcriptionProjects: Int = 0
)
