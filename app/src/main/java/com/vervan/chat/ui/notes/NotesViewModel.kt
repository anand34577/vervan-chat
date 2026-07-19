package com.vervan.chat.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotesListViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val notes: StateFlow<List<Note>> = db.noteDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createNote(): String {
        val note = Note()
        db.noteDao().upsert(note)
        return note.id
    }

    fun delete(note: Note) {
        viewModelScope.launch { db.noteDao().update(note.copy(deletedAt = System.currentTimeMillis())) }
    }

    /** Bulk soft-delete for selection-mode delete (notes are recoverable from the recycle bin,
     * same as [delete] above — no confirmation needed, matching ChatListScreen's pattern). */
    fun deleteAll(ids: Set<String>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            notes.value.filter { it.id in ids }.forEach { db.noteDao().update(it.copy(deletedAt = now)) }
        }
    }
}

enum class NoteAction(val label: String, val instruction: String) {
    SUMMARIZE("Summarize", "Summarize the following note concisely, preserving key facts:"),
    REWRITE("Rewrite", "Rewrite the following note for clarity, keeping the same meaning:"),
    EXTRACT_TASKS("Extract tasks", "Extract an action-item checklist from the following note. Output only the checklist:"),
    CONTINUE("Continue", "Continue writing the following note in the same voice and style, picking up where it leaves off:"),
    IMPROVE("Improve", "Improve the following note's grammar, spelling, and phrasing without changing its meaning or tone:")
}

class NoteEditorViewModel(private val app: VervanApp, private val noteId: String) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private var saveJob: Job? = null

    init {
        viewModelScope.launch { _note.value = db.noteDao().get(noteId) }
    }

    fun delete() {
        viewModelScope.launch { _note.value?.let { db.noteDao().update(it.copy(deletedAt = System.currentTimeMillis())) } }
    }

    val knowledgeBases = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Wires this note into a knowledge base (Phase 3, spec §17) as a plain-text document. */
    fun addToKnowledgeBase(kbId: String) {
        viewModelScope.launch {
            val current = _note.value ?: return@launch
            app.container.documentImportManager.importRawText(kbId, current.title, current.content)
        }
    }

    fun save(title: String, content: String, tags: String = _note.value?.tags ?: "") {
        saveJob?.cancel()
        _saving.value = true
        saveJob = viewModelScope.launch(NonCancellable) { persist(title, content, tags) }
    }

    fun scheduleSave(title: String, content: String, tags: String = _note.value?.tags ?: "") {
        saveJob?.cancel()
        _saving.value = true
        saveJob = viewModelScope.launch {
            delay(450)
            persist(title, content, tags)
        }
    }

    private suspend fun persist(title: String, content: String, tags: String) {
        val current = _note.value ?: run { _saving.value = false; return }
        val updated = current.copy(
            title = title.ifBlank { "Untitled note" }, content = content, tags = tags.trim(),
            updatedAt = System.currentTimeMillis()
        )
        db.noteDao().update(updated)
        _note.value = updated
        _saving.value = false
    }

    /** Runs [action] over [content] and returns the result — caller decides whether to replace or append. */
    fun runAction(action: NoteAction, content: String, onResult: (String) -> Unit) {
        if (content.isBlank() || _running.value) return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _running.value = false
                return@launch
            }
            val prompt = "${action.instruction}\n\n$content"
            var result = ""
            try {
                com.vervan.chat.llm.OneShotLlm.stream(app, prompt)?.collect { result += it }
                onResult(result)
            } catch (t: Throwable) {
                _error.value = "Generation failed: ${t.toUserMessage()}"
            }
            _running.value = false
        }
    }
}
