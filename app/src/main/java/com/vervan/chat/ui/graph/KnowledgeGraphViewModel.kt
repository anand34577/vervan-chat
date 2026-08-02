package com.vervan.chat.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MemoryScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ego-graph over the app's existing containers (Workspace/Project/Folder/Chat/Note/Knowledge
 * Base/Document/Memory/Persona) — only ever loads the 1-hop neighborhood of whatever node is
 * centered, never the whole graph at once, so it scales the same way SearchViewModel does (fine
 * at personal-library scale, no dedicated join tables or ANN index needed). Structural edges come
 * from real foreign keys; "cites"/"recalled"/"saved here" edges are recovered from
 * [com.vervan.chat.data.db.entities.Message.sourcesJson] and
 * [com.vervan.chat.data.db.entities.Message.memoryActivityJson] — the only place that
 * chat→document/chat→memory provenance is actually recorded today.
 */
class KnowledgeGraphViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    private val _center = MutableStateFlow<GraphNode?>(null)
    val center: StateFlow<GraphNode?> = _center

    private val _neighbors = MutableStateFlow<List<GraphEdge>>(emptyList())
    val neighbors: StateFlow<List<GraphEdge>> = _neighbors

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val backStack = ArrayDeque<GraphNode>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _searchResults = MutableStateFlow<List<GraphNode>>(emptyList())
    val searchResults: StateFlow<List<GraphNode>> = _searchResults
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val workspaceId = app.container.settingsRepository.activeWorkspaceId.first()
            val workspace = db.workspaceDao().get(workspaceId) ?: db.workspaceDao().getDefault()
            workspace?.let { open(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), pushHistory = false) }
        }
    }

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            _searchResults.value = searchNodes(text)
        }
    }

    fun open(node: GraphNode) = open(node, pushHistory = true)

    private fun open(node: GraphNode, pushHistory: Boolean) {
        if (pushHistory) _center.value?.let { backStack.addLast(it) }
        _center.value = node
        _canGoBack.value = backStack.isNotEmpty()
        _query.value = ""
        _searchResults.value = emptyList()
        viewModelScope.launch {
            _loading.value = true
            _neighbors.value = runCatching { loadNeighbors(node) }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    fun back() {
        val prev = backStack.removeLastOrNull() ?: return
        _center.value = prev
        _canGoBack.value = backStack.isNotEmpty()
        viewModelScope.launch {
            _loading.value = true
            _neighbors.value = runCatching { loadNeighbors(prev) }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    private suspend fun searchNodes(q: String): List<GraphNode> {
        val results = mutableListOf<GraphNode>()
        db.workspaceDao().observeAll().first().filter { it.name.contains(q, ignoreCase = true) }
            .forEach { results += GraphNode(it.id, GraphNodeType.WORKSPACE, it.name) }
        db.projectDao().observeAll().first().filter { it.name.contains(q, ignoreCase = true) }
            .forEach { results += GraphNode(it.id, GraphNodeType.PROJECT, it.name) }
        db.folderDao().observeAll().first().filter { it.name.contains(q, ignoreCase = true) }
            .forEach { results += GraphNode(it.id, GraphNodeType.FOLDER, it.name) }
        db.chatDao().search(q).forEach { results += GraphNode(it.id, GraphNodeType.CHAT, it.title) }
        db.noteDao().search(q).forEach { results += GraphNode(it.id, GraphNodeType.NOTE, it.title) }
        db.knowledgeBaseDao().observeAll().first().filter { it.name.contains(q, ignoreCase = true) }
            .forEach { results += GraphNode(it.id, GraphNodeType.KNOWLEDGE_BASE, it.name) }
        db.documentDao().search(q).forEach { results += GraphNode(it.id, GraphNodeType.DOCUMENT, it.displayName) }
        db.memoryDao().search(q).forEach { results += GraphNode(it.id, GraphNodeType.MEMORY, it.text.take(60)) }
        db.personaDao().search(q).forEach { results += GraphNode(it.id, GraphNodeType.PERSONA, it.name) }
        return results.take(30)
    }

    private suspend fun loadNeighbors(node: GraphNode): List<GraphEdge> = when (node.type) {
        GraphNodeType.WORKSPACE -> workspaceNeighbors(node.id)
        GraphNodeType.PROJECT -> projectNeighbors(node.id)
        GraphNodeType.FOLDER -> folderNeighbors(node.id)
        GraphNodeType.CHAT -> chatNeighbors(node.id)
        GraphNodeType.NOTE -> noteNeighbors(node.id)
        GraphNodeType.KNOWLEDGE_BASE -> knowledgeBaseNeighbors(node.id)
        GraphNodeType.DOCUMENT -> documentNeighbors(node.id)
        GraphNodeType.MEMORY -> memoryNeighbors(node.id)
        GraphNodeType.PERSONA -> personaNeighbors(node.id)
    }

    private suspend fun workspaceNeighbors(id: String): List<GraphEdge> {
        val workspace = db.workspaceDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        db.personaDao().getPersona(workspace.personaId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "uses persona")
        }
        db.projectDao().observeForWorkspace(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "project")
        }
        db.folderDao().observeForWorkspace(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.FOLDER, it.name), "folder")
        }
        db.chatDao().getForWorkspace(id)
            .filter { it.deletedAt == null && it.projectId == null && it.folderId == null }
            .take(15)
            .forEach { edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "chat") }
        db.documentDao().getForWorkspace(id).take(15).forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.DOCUMENT, it.displayName), "document")
        }
        return edges
    }

    private suspend fun projectNeighbors(id: String): List<GraphEdge> {
        val project = db.projectDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        db.workspaceDao().get(project.workspaceId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), "in workspace")
        }
        project.personaId?.let { pid ->
            db.personaDao().getPersona(pid)?.let {
                edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "uses persona")
            }
        }
        db.chatDao().observeForProject(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "chat")
        }
        db.noteDao().observeForProject(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.NOTE, it.title), "note")
        }
        return edges
    }

    private suspend fun folderNeighbors(id: String): List<GraphEdge> {
        val folder = db.folderDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        db.workspaceDao().get(folder.workspaceId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), "in workspace")
        }
        folder.defaultPersonaId?.let { pid ->
            db.personaDao().getPersona(pid)?.let {
                edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "default persona")
            }
        }
        db.chatDao().observeForFolder(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "chat")
        }
        db.noteDao().observeForFolder(id).first().forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.NOTE, it.title), "note")
        }
        folder.kbIdList().forEach { kbId ->
            db.knowledgeBaseDao().get(kbId)?.let {
                edges += GraphEdge(GraphNode(it.id, GraphNodeType.KNOWLEDGE_BASE, it.name), "default KB")
            }
        }
        return edges
    }

    private suspend fun chatNeighbors(id: String): List<GraphEdge> {
        val chat = db.chatDao().getChat(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        db.workspaceDao().get(chat.workspaceId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), "in workspace")
        }
        chat.projectId?.let { pid ->
            db.projectDao().get(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "in project") }
        }
        chat.folderId?.let { fid ->
            db.folderDao().get(fid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.FOLDER, it.name), "in folder") }
        }
        chat.personaId?.let { pid ->
            db.personaDao().getPersona(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "uses persona") }
        }
        chat.kbIdList().forEach { kbId ->
            db.knowledgeBaseDao().get(kbId)?.let {
                edges += GraphEdge(GraphNode(it.id, GraphNodeType.KNOWLEDGE_BASE, it.name), "searches")
            }
        }

        // Citation/memory provenance only lives on individual messages (see class doc) — walk
        // the chat's messages once and dedupe by id so a document cited in ten turns still shows
        // as a single edge.
        val citedDocs = linkedMapOf<String, String>()
        val recalledMemories = linkedMapOf<String, String>()
        val savedMemories = linkedMapOf<String, String>()
        db.messageDao().getMessages(id).forEach { message ->
            message.sourcesJson?.let { json ->
                runCatching {
                    val arr = JSONArray(json)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        citedDocs[o.getString("documentId")] = o.optString("documentName")
                    }
                }
            }
            message.memoryActivityJson?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.optJSONArray("recalled")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            recalledMemories[o.getString("id")] = o.optString("text")
                        }
                    }
                    obj.optJSONArray("saved")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            savedMemories[o.getString("id")] = o.optString("text")
                        }
                    }
                }
            }
        }
        citedDocs.entries.take(10).forEach { (docId, name) ->
            edges += GraphEdge(GraphNode(docId, GraphNodeType.DOCUMENT, name.ifBlank { "Document" }), "cites")
        }
        recalledMemories.entries.take(10).forEach { (memId, text) ->
            edges += GraphEdge(GraphNode(memId, GraphNodeType.MEMORY, text.take(60)), "recalled")
        }
        savedMemories.entries.take(10).forEach { (memId, text) ->
            edges += GraphEdge(GraphNode(memId, GraphNodeType.MEMORY, text.take(60)), "saved here")
        }
        return edges
    }

    private suspend fun noteNeighbors(id: String): List<GraphEdge> {
        val note = db.noteDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        note.projectId?.let { pid ->
            db.projectDao().get(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "in project") }
        }
        note.folderId?.let { fid ->
            db.folderDao().get(fid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.FOLDER, it.name), "in folder") }
        }
        return edges
    }

    private suspend fun knowledgeBaseNeighbors(id: String): List<GraphEdge> {
        val kb = db.knowledgeBaseDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        kb.defaultPersonaId?.let { pid ->
            db.personaDao().getPersona(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "default persona") }
        }
        kb.defaultProjectId?.let { pid ->
            db.projectDao().get(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "default project") }
        }
        db.documentDao().observeForKb(id).first().take(20).forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.DOCUMENT, it.displayName), "document")
        }
        db.chatDao().observeAllChats().first().filter { it.kbIdList().contains(id) }.take(15).forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "searches this")
        }
        return edges
    }

    private suspend fun documentNeighbors(id: String): List<GraphEdge> {
        val document = db.documentDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        db.knowledgeBaseDao().get(document.knowledgeBaseId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.KNOWLEDGE_BASE, it.name), "in knowledge base")
        }
        db.workspaceDao().get(document.workspaceId)?.let {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), "in workspace")
        }
        db.messageDao().chatIdsReferencingDocument(id).distinct().take(15).forEach { chatId ->
            db.chatDao().getChat(chatId)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "cited by") }
        }
        return edges
    }

    private suspend fun memoryNeighbors(id: String): List<GraphEdge> {
        val memory = db.memoryDao().get(id) ?: return emptyList()
        val edges = mutableListOf<GraphEdge>()
        when (memory.scope) {
            MemoryScope.PERSONA -> memory.scopeRefId?.let { pid ->
                db.personaDao().getPersona(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PERSONA, it.name), "scoped to persona") }
            }
            MemoryScope.PROJECT -> memory.scopeRefId?.let { pid ->
                db.projectDao().get(pid)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "scoped to project") }
            }
            MemoryScope.GLOBAL -> {}
        }
        db.messageDao().chatIdsReferencingMemory(id).distinct().take(15).forEach { chatId ->
            db.chatDao().getChat(chatId)?.let { edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "used in chat") }
        }
        return edges
    }

    private suspend fun personaNeighbors(id: String): List<GraphEdge> {
        val edges = mutableListOf<GraphEdge>()
        db.workspaceDao().observeAll().first().filter { it.personaId == id }.forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.WORKSPACE, it.name), "uses this")
        }
        db.projectDao().observeAll().first().filter { it.personaId == id }.forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.PROJECT, it.name), "uses this")
        }
        db.knowledgeBaseDao().observeAll().first().filter { it.defaultPersonaId == id }.forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.KNOWLEDGE_BASE, it.name), "default persona")
        }
        db.chatDao().observeAllChats().first().filter { it.personaId == id && it.deletedAt == null }.take(15).forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.CHAT, it.title), "uses this")
        }
        db.memoryDao().observeAll().first().filter { it.scope == MemoryScope.PERSONA && it.scopeRefId == id }.take(15).forEach {
            edges += GraphEdge(GraphNode(it.id, GraphNodeType.MEMORY, it.text.take(60)), "scoped here")
        }
        return edges
    }
}
