package com.vervan.chat.ui.notes

import com.vervan.chat.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotesListViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val notes: StateFlow<List<Note>> = reload
        .flatMapLatest { db.noteDao().observeAll() }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _error.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _error.value = throwable.toUserMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry() {
        _error.value = null
        reload.value += 1
    }

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

    /** Undo counterpart to [deleteAll]'s snackbar, mirroring ChatListViewModel.restoreFromTrash. */
    fun restoreAll(notes: List<Note>) {
        viewModelScope.launch { notes.forEach { db.noteDao().update(it.copy(deletedAt = null)) } }
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _recordFound = MutableStateFlow(false)
    val recordFound: StateFlow<Boolean> = _recordFound

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private var saveJob: Job? = null
    /** The latest edit not yet written. Flushed in [onCleared] so a back gesture inside the
     * autosave debounce window can't drop the last keystrokes. */
    @Volatile private var pendingEdit: Triple<String, String, String>? = null

    init {
        load()
    }

    fun retryLoad() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                _note.value = db.noteDao().get(noteId)
                _recordFound.value = _note.value != null
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _recordFound.value = false
                _loadError.value = t.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun delete() {
        // Drop any queued autosave first — flushing it in onCleared would rewrite the row from
        // the pre-delete copy and silently restore the note from the recycle bin.
        saveJob?.cancel()
        pendingEdit = null
        viewModelScope.launch(NonCancellable) {
            _note.value?.let { current ->
                val deleted = current.copy(deletedAt = System.currentTimeMillis())
                db.noteDao().update(deleted)
                _note.value = deleted
            }
        }
    }

    val knowledgeBases = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Wires this note into a knowledge base as a plain-text document. */
    fun addToKnowledgeBase(kbId: String) {
        viewModelScope.launch {
            val current = _note.value ?: return@launch
            app.container.documentImportManager.importRawText(kbId, current.title, current.content)
        }
    }

    fun save(title: String, content: String, tags: String = _note.value?.tags ?: "") {
        saveJob?.cancel()
        _saving.value = true
        pendingEdit = Triple(title, content, tags)
        saveJob = viewModelScope.launch(NonCancellable) { persist(title, content, tags) }
    }

    fun scheduleSave(title: String, content: String, tags: String = _note.value?.tags ?: "") {
        saveJob?.cancel()
        _saving.value = true
        pendingEdit = Triple(title, content, tags)
        saveJob = viewModelScope.launch {
            delay(450)
            persist(title, content, tags)
        }
    }

    override fun onCleared() {
        val edit = pendingEdit
        val loaded = _note.value
        app.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (edit != null) persist(edit.first, edit.second, edit.third)
                // A note opened from "New note"/"Quick note" and left without typing anything
                // is removed instead of accumulating blank "Untitled note" rows.
                val latest = db.noteDao().get(noteId) ?: return@runCatching
                val untouched = loaded != null && latest.content.isBlank() && latest.tags.isBlank() &&
                    (latest.title.isBlank() || latest.title == "Untitled note") && latest.deletedAt == null
                if (untouched) db.noteDao().hardDelete(noteId)
            }.onFailure { Log.w("NoteEditorViewModel", "Final note save failed", it) }
        }
    }

    private suspend fun persist(title: String, content: String, tags: String) {
        val current = _note.value ?: run { _saving.value = false; return }
        if (pendingEdit == Triple(title, content, tags)) pendingEdit = null
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
                _error.value = app.getString(R.string.notes_no_model_is_ready_open)
                _running.value = false
                return@launch
            }
            val prompt = "${action.instruction}\n\n$content"
            var result = ""
            try {
                com.vervan.chat.llm.OneShotLlm.stream(app, prompt)?.collect { result += it }
                onResult(result)
            } catch (t: Throwable) {
                Log.e(TAG, "runAction(${action.name}) failed", t)
                _error.value = app.getString(R.string.notes_generation_failed_arg1, t.toUserMessage())
            }
            _running.value = false
        }
    }

    companion object {
        private const val TAG = "NoteEditorViewModel"
    }
}
