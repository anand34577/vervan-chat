package com.vervan.chat.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.system.pruneOldExports
import com.vervan.chat.system.toUserMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DocumentViewerViewModel(private val app: VervanApp, private val documentId: String) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    val document: StateFlow<Document?> = reload
        .flatMapLatest { db.documentDao().observeAll() }
        .map { list -> list.find { it.id == documentId } }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _loadError.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _loadError.value = throwable.toUserMessage()
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chunks: StateFlow<List<Chunk>> = db.chunkDao().observeForDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reindexing = MutableStateFlow(false)
    val reindexing: StateFlow<Boolean> = _reindexing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun retryLoad() {
        _loadError.value = null
        reload.value += 1
    }

    /** Re-indexes this document from its stored file (index repair). */
    fun reindex() {
        if (_reindexing.value) return
        viewModelScope.launch {
            _reindexing.value = true
            _error.value = null
            try {
                val doc = document.value ?: return@launch
                val file = java.io.File(doc.filePath)
                if (!file.exists()) {
                    _error.value = "The original file is no longer available. Re-import it to rebuild the index."
                    return@launch
                }
                db.chunkDao().deleteForDocument(documentId)
                db.documentDao().update(doc.copy(status = com.vervan.chat.data.db.entities.DocumentStatus.EXTRACTING))
                app.container.documentImportManager.reindexLocal(documentId)
            } catch (t: Throwable) {
                _error.value = "Re-indexing failed: ${t.toUserMessage()}"
            } finally {
                _reindexing.value = false
            }
        }
    }

    /** Exports the extracted text — the same sections shown under "Searchable text" above, in
     * reading order (chunks are already ordered by [com.vervan.chat.data.db.dao.ChunkDao.observeForDocument]),
     * not the original source file (that's what "Open with another app" is for). Same
     * exports-dir/pruning pattern as TranscriptionViewModel.exportTxt. */
    suspend fun exportExtractedText(): File = withContext(Dispatchers.IO) {
        val doc = document.value
        val dir = File(app.filesDir, "exports").apply { mkdirs() }
        pruneOldExports(dir)
        val name = (doc?.displayName ?: "document").substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "document" }.take(60)
        val file = File(dir, "$name-extracted.txt")
        file.writeText(chunks.value.joinToString("\n\n") { it.text })
        file
    }
}
