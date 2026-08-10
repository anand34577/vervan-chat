package com.vervan.chat.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.retrieval.SourcePassage
import com.vervan.chat.ui.tools.SearchableTool
import com.vervan.chat.ui.tools.searchableTools
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchResults(
    val chats: List<Chat> = emptyList(),
    val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList(),
    val passages: List<SourcePassage> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val messages: List<Message> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val projects: List<Project> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val templates: List<PromptTemplate> = emptyList(),
    val workflows: List<Workflow> = emptyList(),
    val savedOutputs: List<SavedOutput> = emptyList(),
    val tools: List<SearchableTool> = emptyList(),
    val toolRuns: List<ToolRun> = emptyList(),
) {
    val isEmpty get() = chats.isEmpty() && notes.isEmpty() && documents.isEmpty() && passages.isEmpty() &&
        personas.isEmpty() && messages.isEmpty() && memories.isEmpty() && projects.isEmpty() &&
        workspaces.isEmpty() && folders.isEmpty() && knowledgeBases.isEmpty() && templates.isEmpty() &&
        workflows.isEmpty() && savedOutputs.isEmpty() && tools.isEmpty() && toolRuns.isEmpty()
}

/** Cross-content search — fans a single query out to bounded DAO queries, then combines the
 * results into the existing groups. The fan-out is concurrent so a slow category does not make
 * every other category wait, while each DAO caps its own result set instead of loading whole
 * tables into memory. */
class SearchViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var searchJob: Job? = null
    private var requestId = 0L

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        val currentRequest = ++requestId
        if (text.isBlank()) {
            _results.value = SearchResults()
            _searching.value = false
            _error.value = null
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            _error.value = null
            delay(250)
            val searchText = text.trim()
            try {
                val temporaryChatIds = db.chatDao().getTemporaryChatIds().toSet()
                coroutineScope {
                    val chats = async { db.chatDao().search(searchText).filterNot { it.isTemporary } }
                    val notes = async { db.noteDao().search(searchText) }
                    val documents = async { db.documentDao().search(searchText) }
                    val passages = async { searchPassages(searchText) }
                    val personas = async { db.personaDao().search(searchText) }
                    val messages = async { db.messageDao().search(searchText).filterNot { it.chatId in temporaryChatIds } }
                    val memories = async { db.memoryDao().search(searchText) }
                    val projects = async { db.projectDao().search(searchText) }
                    val workspaces = async { db.workspaceDao().search(searchText) }
                    val folders = async { db.folderDao().search(searchText) }
                    val knowledgeBases = async { db.knowledgeBaseDao().search(searchText) }
                    val templates = async { db.promptTemplateDao().search(searchText) }
                    val workflows = async { db.workflowDao().search(searchText) }
                    val savedOutputs = async { db.savedOutputDao().search(searchText) }
                    val tools = async { ranked(searchableTools, searchText) { listOf(it.label, it.description) } }
                    val toolRuns = async { db.toolRunDao().search(searchText) }

                    val newResults = SearchResults(
                        chats = chats.await(),
                        notes = notes.await(),
                        documents = documents.await(),
                        passages = passages.await(),
                        personas = personas.await(),
                        messages = messages.await(),
                        memories = memories.await(),
                        projects = projects.await(),
                        workspaces = workspaces.await(),
                        folders = folders.await(),
                        knowledgeBases = knowledgeBases.await(),
                        templates = templates.await(),
                        workflows = workflows.await(),
                        savedOutputs = savedOutputs.await(),
                        tools = tools.await(),
                        toolRuns = toolRuns.await(),
                    )
                    if (currentRequest == requestId) _results.value = newResults
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (currentRequest == requestId) {
                    _results.value = SearchResults()
                    _error.value = t.toUserMessage()
                }
            } finally {
                if (currentRequest == requestId) _searching.value = false
            }
        }
    }

    /** Global keyword search over indexed document text (across every knowledge base), not just
     * document titles — [DocumentDao.search] only matches [Document.displayName]. Reuses the same
     * [ChunkFts][com.vervan.chat.data.db.entities.ChunkFts] index [RetrievalEngine][com.vervan.chat.retrieval.RetrievalEngine]
     * queries for chat retrieval. */
    private suspend fun searchPassages(query: String): List<SourcePassage> {
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        if (terms.isEmpty()) return emptyList()
        val matchQuery = terms.joinToString(" OR ") { "\"$it\"" }
        val chunkIds = db.chunkDao().matchFtsAll(matchQuery, 20)
        if (chunkIds.isEmpty()) return emptyList()
        val chunks = db.chunkDao().getByIds(chunkIds)
        val docNames = mutableMapOf<String, String>()
        return chunks.map { chunk ->
            val docName = docNames.getOrPut(chunk.documentId) { db.documentDao().get(chunk.documentId)?.displayName ?: "Unknown" }
            SourcePassage(chunk.id, chunk.documentId, docName, chunk.sectionPath, chunk.text, 1f, chunk.pageNumber)
        }
    }

    /** Exact and prefix title matches beat body-only matches without requiring a full FTS index. */
    private fun <T> ranked(items: List<T>, query: String, fields: (T) -> List<String>): List<T> {
        val q = query.trim().lowercase()
        return items.mapNotNull { item ->
            val values = fields(item).map { it.lowercase() }
            val score = values.mapIndexedNotNull { index, value ->
                when {
                    value == q -> 400 - index
                    value.startsWith(q) -> 300 - index
                    value.split(Regex("\\s+")).any { it.startsWith(q) } -> 200 - index
                    q in value -> 100 - index
                    else -> null
                }
            }.maxOrNull()
            score?.let { it to item }
        }.sortedByDescending { it.first }.take(20).map { it.second }
    }
}
