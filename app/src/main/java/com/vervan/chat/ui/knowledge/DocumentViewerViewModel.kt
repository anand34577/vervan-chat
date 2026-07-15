package com.vervan.chat.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.Document
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocumentViewerViewModel(private val app: VervanApp, private val documentId: String) : ViewModel() {
    private val db = app.container.db

    val document: StateFlow<Document?> = db.documentDao().observeAll()
        .map { list -> list.find { it.id == documentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chunks: StateFlow<List<Chunk>> = db.chunkDao().observeForDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Re-indexes this document from its stored file (spec §42 index repair). */
    fun reindex() {
        viewModelScope.launch {
            val doc = document.value ?: return@launch
            val file = java.io.File(doc.filePath)
            if (!file.exists()) return@launch
            db.chunkDao().deleteForDocument(documentId)
            db.documentDao().update(doc.copy(status = com.vervan.chat.data.db.entities.DocumentStatus.EXTRACTING))
            // Re-run ingestion through the import manager by re-importing the local file.
            app.container.documentImportManager.reindexLocal(documentId)
        }
    }
}
