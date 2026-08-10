package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IndexMaintenanceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    val documents: StateFlow<List<com.vervan.chat.data.db.entities.Document>> = reload
        .flatMapLatest { db.documentDao().observeAll() }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _loadError.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _loadError.value = throwable.toUserMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _busyDocumentId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val busyDocumentId: StateFlow<String?> = _busyDocumentId

    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun retryLoad() {
        _loadError.value = null
        reload.value += 1
    }

    fun reindexAll() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val docs = documents.value.filter { it.status == DocumentStatus.READY || it.status == DocumentStatus.FAILED }
                _status.value = "Re-indexing ${docs.size} documents…"
                ensureEmbeddingModelLoaded()
                docs.forEachIndexed { i, doc ->
                    _status.value = "Re-indexing ${i + 1}/${docs.size}: ${doc.displayName}"
                    app.container.documentImportManager.reindexLocal(doc.id)
                }
                _status.value = "Re-indexed ${docs.size} documents."
            } catch (t: Throwable) {
                _status.value = null
                _error.value = t.toUserMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    fun reindexOne(documentId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _busyDocumentId.value = documentId
            _error.value = null
            try {
                _status.value = "Re-indexing…"
                ensureEmbeddingModelLoaded()
                app.container.documentImportManager.reindexLocal(documentId)
                _status.value = "Done."
            } catch (t: Throwable) {
                _status.value = null
                _error.value = t.toUserMessage()
            } finally {
                _busy.value = false
                _busyDocumentId.value = null
            }
        }
    }

    private suspend fun ensureEmbeddingModelLoaded() {
        val active = db.modelDao().getActiveModel(ModelRole.EMBEDDING) ?: return
        val result = app.container.modelLoadCoordinator.ensureLoaded(
            active, com.vervan.chat.modelload.LoadTrigger.RAG_RETRIEVAL
        )
        require(result.success) { result.errorMessage ?: "Embedding model could not be loaded" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexMaintenanceScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: IndexMaintenanceViewModel = viewModel(factory = viewModelFactory { initializer { IndexMaintenanceViewModel(app) } })
    val documents by vm.documents.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val busyDocumentId by vm.busyDocumentId.collectAsState()
    val error by vm.error.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search index") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          Column(Modifier.fillMaxSize().padding(vertical = Space.sm)) {
            Text("Rebuild after changing the embedding model or when document search is wrong.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = Space.sm))
            Button(onClick = { vm.reindexAll() }, enabled = !busy, modifier = Modifier.padding(bottom = Space.sm)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = Space.sm))
                Text("Re-index all documents")
            }
            if (busy && busyDocumentId == null) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Rebuilding the search index",
                    body = status ?: "Preparing documents. Keep this screen open.",
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }
            loadError?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Documents unavailable",
                    message = it,
                    recovery = "Your indexed content is safe. Retry loading the document list.",
                    actionLabel = "Retry",
                    onAction = vm::retryLoad,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Index rebuild failed",
                    message = it,
                    recovery = "Documents are safe. Check the model and free storage, then try again.",
                    actionLabel = "Retry all",
                    onAction = vm::reindexAll,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }
            status?.takeIf { !busy }?.let {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                    colors = SurfaceRole.Card.cardColors(),
                    border = SurfaceRole.Card.border(),
                ) {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Space.md))
                }
            }
            HorizontalDivider()
            Text("Documents (${documents.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = Space.sm))
            when {
                loadError != null -> Unit
                isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.weight(1f))
                documents.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Description,
                    title = "No documents to index",
                    body = "Import a document into a knowledge base before rebuilding search.",
                    modifier = Modifier.weight(1f)
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(documents, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
                            colors = SurfaceRole.Card.cardColors(),
                            border = SurfaceRole.Card.border(),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(Space.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    OverflowTooltipText(
                                        text = doc.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(doc.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (busyDocumentId == doc.id) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        Modifier.padding(start = Space.sm, end = Space.sm).size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { vm.reindexOne(doc.id) },
                                        enabled = !busy,
                                        modifier = Modifier.padding(start = Space.sm),
                                    ) { Text("Re-index") }
                                }
                            }
                        }
                    }
                }
            }
          }
        }
    }
}
