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
    val memories: List<Memory> = emptyList()
) {
    val isEmpty get() = chats.isEmpty() && notes.isEmpty() && documents.isEmpty() &&
        personas.isEmpty() && messages.isEmpty() && memories.isEmpty()
}

/** Cross-content search (spec §29) — fans a single query out to every content DAO's own
 * LIKE-based search query. ponytail: debounce + sequential DAO hits, no FTS/ranking index —
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
                memories = db.memoryDao().search(text)
            )
            _searching.value = false
        }
    }
}
