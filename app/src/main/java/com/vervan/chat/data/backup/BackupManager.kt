package com.vervan.chat.data.backup

import com.vervan.chat.data.db.AppDatabase
import com.vervan.chat.data.db.entities.Chat
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
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.db.entities.Workspace
import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON export/import for everything a user actually authored (spec §33). Model files,
 * knowledge bases, and imported documents are deliberately NOT included — those are large
 * binary assets tied to on-device paths, re-importing them belongs to Models/Knowledge, not
 * a settings backup. ponytail: hand-rolled org.json mapping per entity (matches how the rest
 * of the app already encodes JSON — Workflow steps, tool-call payloads) rather than pulling
 * in a serialization library for one screen.
 */
object BackupManager {
    private const val FORMAT_VERSION = 1

    suspend fun export(db: AppDatabase, out: OutputStream) {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("workspaces", JSONArray(db.workspaceDao().observeAll().firstList().map { workspaceToJson(it) }))
        // Incognito mode (Phase B) — a temporary chat is excluded from export entirely, same
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

        out.writer().use { it.write(root.toString(2)) }
    }

    /**
     * Phase E — same JSON shape as [export], scoped to one workspace's own chats/messages/
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
        val root = JSONObject(input.bufferedReader().readText())
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
        }

        return BackupSummary(chats.size, notes.size, personas.size, templates.size, workflows.size, memories.size, projects.size, folders.size, savedOutputs.size, flashcardSets.size, studyCards.size, knowledgeBases.size, workspaces.size)
    }

    private suspend fun <T> Flow<List<T>>.firstList(): List<T> = first()

    private fun JSONArray.toObjectList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun workspaceToJson(w: Workspace) = JSONObject().apply {
        put("id", w.id); put("name", w.name); put("description", w.description); put("personaId", w.personaId)
        put("isDefault", w.isDefault); put("archived", w.archived)
        put("createdAt", w.createdAt); put("updatedAt", w.updatedAt); put("lastActiveAt", w.lastActiveAt)
        put("autoTitleGeneration", w.autoTitleGeneration)
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
        autoTitleGeneration = o.optBoolean("autoTitleGeneration")
    )

    private fun chatToJson(c: Chat) = JSONObject().apply {
        put("id", c.id); put("title", c.title); put("personaId", c.personaId ?: JSONObject.NULL)
        put("workspaceId", c.workspaceId)
        put("modelId", c.modelId ?: JSONObject.NULL); put("projectId", c.projectId ?: JSONObject.NULL)
        put("folderId", c.folderId ?: JSONObject.NULL)
        put("draft", c.draft); put("pinned", c.pinned); put("archived", c.archived)
        put("sourceGrounded", c.sourceGrounded); put("toolsEnabled", c.toolsEnabled)
        put("thinkingMode", c.thinkingMode); put("profile", c.profile)
        put("activeLeafId", c.activeLeafId ?: JSONObject.NULL); put("knowledgeBaseIds", c.knowledgeBaseIds)
        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
    }
    private fun chatFromJson(o: JSONObject) = Chat(
        id = o.getString("id"), title = o.getString("title"), workspaceId = o.optString("workspaceId", Workspace.DEFAULT_WORKSPACE_ID), personaId = o.optStringOrNull("personaId"),
        modelId = o.optStringOrNull("modelId"), projectId = o.optStringOrNull("projectId"),
        folderId = o.optStringOrNull("folderId"),
        draft = o.optString("draft"),
        pinned = o.optBoolean("pinned"), archived = o.optBoolean("archived"), sourceGrounded = o.optBoolean("sourceGrounded"),
        toolsEnabled = o.optBoolean("toolsEnabled"), thinkingMode = o.optString("thinkingMode", "OFF"),
        profile = o.optString("profile", "BALANCED"),
        activeLeafId = o.optStringOrNull("activeLeafId"),
        knowledgeBaseIds = o.optString("knowledgeBaseIds"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        deletedAt = null
    )

    private fun messageToJson(m: Message) = JSONObject().apply {
        put("id", m.id); put("chatId", m.chatId); put("parentId", m.parentId ?: JSONObject.NULL)
        put("role", m.role.name); put("content", m.content); put("state", m.state.name)
        put("imagePath", m.imagePath ?: JSONObject.NULL); put("audioPath", m.audioPath ?: JSONObject.NULL)
        put("sourcesJson", m.sourcesJson ?: JSONObject.NULL)
        put("toolCallJson", m.toolCallJson ?: JSONObject.NULL); put("toolResultJson", m.toolResultJson ?: JSONObject.NULL)
        put("createdAt", m.createdAt)
    }
    private fun messageFromJson(o: JSONObject) = Message(
        id = o.getString("id"), chatId = o.getString("chatId"), parentId = o.optStringOrNull("parentId"),
        role = MessageRole.valueOf(o.getString("role")), content = o.getString("content"),
        state = MessageState.valueOf(o.optString("state", "COMPLETE")), imagePath = o.optStringOrNull("imagePath"),
        audioPath = o.optStringOrNull("audioPath"),
        sourcesJson = o.optStringOrNull("sourcesJson"), toolCallJson = o.optStringOrNull("toolCallJson"),
        toolResultJson = o.optStringOrNull("toolResultJson"), createdAt = o.getLong("createdAt")
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
    }
    private fun personaFromJson(o: JSONObject) = Persona(
        id = o.getString("id"), name = o.getString("name"), description = o.optString("description"),
        systemInstruction = o.getString("systemInstruction"), isBuiltIn = false,
        tone = o.optString("tone", "NEUTRAL"),
        formality = o.optString("formality", "NEUTRAL"),
        conciseness = o.optString("conciseness", "NORMAL"),
        creativity = o.optDouble("creativity", 0.5).toFloat(),
        responseLength = o.optString("responseLength", "BALANCED"),
        language = o.optString("language")
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
        put("createdAt", p.createdAt)
    }
    private fun projectFromJson(o: JSONObject) = Project(
        id = o.getString("id"), name = o.getString("name"), instructions = o.optString("instructions"),
        personaId = o.optStringOrNull("personaId"), createdAt = o.getLong("createdAt")
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

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)
}

data class BackupSummary(
    val chats: Int, val notes: Int, val personas: Int,
    val templates: Int, val workflows: Int, val memories: Int, val projects: Int,
    val folders: Int = 0, val savedOutputs: Int = 0, val flashcardSets: Int = 0,
    val studyCards: Int = 0, val knowledgeBases: Int = 0, val workspaces: Int = 0
)
