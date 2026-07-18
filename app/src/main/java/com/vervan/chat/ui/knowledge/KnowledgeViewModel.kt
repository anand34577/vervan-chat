package com.vervan.chat.ui.knowledge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KnowledgeViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val knowledgeBases: StateFlow<List<KnowledgeBase>> = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments: StateFlow<List<Document>> = db.documentDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** kbId -> (document count, all ready?) — cheap in-memory grouping, fine at personal-library scale. */
    val kbStats: StateFlow<Map<String, Pair<Int, Boolean>>> = recentDocuments
        .map { docs -> docs.groupBy { it.knowledgeBaseId }.mapValues { (_, d) -> d.size to d.all { it.status == DocumentStatus.READY } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val indexingDocuments: StateFlow<List<Document>> = db.documentDao().observeIndexing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createKnowledgeBase(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { db.knowledgeBaseDao().upsert(KnowledgeBase(name = name)) }
    }

    fun delete(kb: KnowledgeBase) {
        viewModelScope.launch {
            db.documentDao().getForKb(kb.id).forEach { app.container.documentImportManager.delete(it) }
            db.knowledgeBaseDao().delete(kb)
        }
    }
}

class KnowledgeBaseDetailViewModel(private val app: VervanApp, private val kbId: String) : ViewModel() {
    private val db = app.container.db
    private val docImport = app.container.documentImportManager

    val documents = db.documentDao().observeForKb(kbId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Set when [importDocument] finds a same-named, different-content document already in
     * this KB (Phase 3, spec §20) — the screen shows a replace/keep-both dialog. */
    private val _pendingVersionConflict = MutableStateFlow<com.vervan.chat.model.DocumentImportOutcome.VersionConflict?>(null)
    val pendingVersionConflict: StateFlow<com.vervan.chat.model.DocumentImportOutcome.VersionConflict?> = _pendingVersionConflict

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            _error.value = null
            try {
                ensureEmbeddingModelLoaded()
                when (val result = docImport.import(kbId, uri)) {
                    is com.vervan.chat.model.DocumentImportOutcome.VersionConflict -> _pendingVersionConflict.value = result
                    is com.vervan.chat.model.DocumentImportOutcome.Duplicate -> _error.value = "\"${result.existing.displayName}\" is already imported with identical content."
                    is com.vervan.chat.model.DocumentImportOutcome.Imported -> { /* observed via documents Flow */ }
                }
            } catch (e: Exception) {
                _error.value = "Import failed. ${e.toUserMessage()}"
            }
            _importing.value = false
        }
    }

    /** Re-indexes a Failed/Unsupported document from its stored file — same recovery path
     * as [DocumentViewerViewModel.reindex], reachable here so a failed document doesn't
     * force a trip to the document viewer just to retry. */
    fun reindex(document: Document) {
        viewModelScope.launch {
            _error.value = null
            try {
                db.chunkDao().deleteForDocument(document.id)
                db.documentDao().update(document.copy(status = DocumentStatus.EXTRACTING))
                docImport.reindexLocal(document.id)
            } catch (e: Exception) {
                _error.value = "Re-indexing failed. ${e.toUserMessage()}"
            }
        }
    }

    fun resolveVersionConflict(replace: Boolean) {
        val conflict = _pendingVersionConflict.value ?: return
        _pendingVersionConflict.value = null
        viewModelScope.launch {
            _importing.value = true
            try {
                ensureEmbeddingModelLoaded()
                docImport.resolveVersionConflict(conflict.existing, conflict.tempFilePath, conflict.mimeType, conflict.newHash, replace)
            } catch (e: Exception) {
                _error.value = "Import failed. ${e.toUserMessage()}"
            }
            _importing.value = false
        }
    }

    fun dismissVersionConflict() {
        val conflict = _pendingVersionConflict.value ?: return
        _pendingVersionConflict.value = null
        java.io.File(conflict.tempFilePath).delete()
    }

    private suspend fun ensureEmbeddingModelLoaded() {
        val active = db.modelDao().getActiveModel(ModelRole.EMBEDDING) ?: return
        val result = app.container.modelLoadCoordinator.ensureLoaded(
            active, com.vervan.chat.modelload.LoadTrigger.RAG_RETRIEVAL
        )
        if (!result.success) {
            // Import still proceeds — chunks are keyword-searchable without embeddings.
                _error.value = "Semantic search is unavailable. Keyword search still works."
        }
    }

    fun deleteDocument(document: com.vervan.chat.data.db.entities.Document) {
        viewModelScope.launch { docImport.softDelete(document) }
    }

    /** Bulk soft-delete for selection-mode delete on the document list. */
    fun deleteDocuments(ids: Set<String>) {
        viewModelScope.launch {
            documents.value.filter { it.id in ids }.forEach { docImport.softDelete(it) }
        }
    }

    fun deleteKnowledgeBase(onDone: () -> Unit) {
        viewModelScope.launch {
            db.knowledgeBaseDao().get(kbId)?.let { kb ->
                db.documentDao().getForKb(kb.id).forEach { app.container.documentImportManager.delete(it) }
                db.knowledgeBaseDao().delete(kb)
            }
            onDone()
        }
    }
}
