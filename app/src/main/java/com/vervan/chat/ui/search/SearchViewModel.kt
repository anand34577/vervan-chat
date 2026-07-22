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
import com.vervan.chat.ui.tools.SearchableTool
import com.vervan.chat.ui.tools.searchableTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SearchResults(
    val chats: List<Chat> = emptyList(),
    val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList(),
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
    val isEmpty get() = chats.isEmpty() && notes.isEmpty() && documents.isEmpty() &&
        personas.isEmpty() && messages.isEmpty() && memories.isEmpty() && projects.isEmpty() &&
        workspaces.isEmpty() && folders.isEmpty() && knowledgeBases.isEmpty() && templates.isEmpty() &&
        workflows.isEmpty() && savedOutputs.isEmpty() && tools.isEmpty() && toolRuns.isEmpty()
}

/** Cross-content search (spec §29) — fans a single query out to every content DAO's own
 * LIKE-based search query. debounce + sequential DAO hits, no FTS/ranking index —
 * fine at personal-library scale, revisit if any one table grows past a few thousand rows. */
class SearchViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private var searchJob: Job? = null

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _results.value = SearchResults()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _searching.value = true
            delay(250)
            // Incognito mode (Phase B) — a temporary chat, and its messages, never surface in
            // search. Filtered in-memory after the DAO call rather than a new SQL join, same
            // "fine at personal-library scale" tradeoff this ViewModel already makes elsewhere.
            val chats = db.chatDao().search(text).filterNot { it.isTemporary }
            val temporaryChatIds = db.chatDao().observeAllChats().first().filter { it.isTemporary }.map { it.id }.toSet()
            _results.value = SearchResults(
                chats = chats,
                notes = db.noteDao().search(text),
                documents = db.documentDao().search(text),
                personas = db.personaDao().search(text),
                messages = db.messageDao().search(text).filterNot { it.chatId in temporaryChatIds },
                memories = db.memoryDao().search(text),
                projects = ranked(db.projectDao().observeAll().first(), text) { listOf(it.name, it.instructions) },
                workspaces = ranked(db.workspaceDao().observeAll().first(), text) { listOf(it.name, it.description) },
                folders = ranked(db.folderDao().observeAll().first(), text) { listOf(it.name) },
                knowledgeBases = ranked(db.knowledgeBaseDao().observeAll().first(), text) { listOf(it.name, it.description) },
                templates = ranked(db.promptTemplateDao().observeAll().first(), text) { listOf(it.name, it.description, it.body) },
                workflows = ranked(db.workflowDao().observeAll().first(), text) { listOf(it.name, it.description) },
                savedOutputs = ranked(db.savedOutputDao().observeAll().first(), text) { listOf(it.label, it.content) },
                tools = ranked(searchableTools, text) { listOf(it.label, it.description) },
                toolRuns = ranked(db.toolRunDao().observeAll().first(), text) { listOf(it.toolName, it.input, it.output) },
            )
            _searching.value = false
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
