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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IndexMaintenanceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val documents: StateFlow<List<com.vervan.chat.data.db.entities.Document>> = db.documentDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _busyDocumentId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val busyDocumentId: StateFlow<String?> = _busyDocumentId

    fun reindexAll() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val docs = documents.value.filter { it.status == DocumentStatus.READY || it.status == DocumentStatus.FAILED }
                _status.value = "Re-indexing ${docs.size} documents…"
                ensureEmbeddingModelLoaded()
                docs.forEachIndexed { i, doc ->
                    _status.value = "Re-indexing ${i + 1}/${docs.size}: ${doc.displayName}"
                    app.container.documentImportManager.reindexLocal(doc.id)
                }
                _status.value = "Re-indexed ${docs.size} documents."
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
            try {
                _status.value = "Re-indexing…"
                ensureEmbeddingModelLoaded()
                app.container.documentImportManager.reindexLocal(documentId)
                _status.value = "Done."
            } finally {
                _busy.value = false
                _busyDocumentId.value = null
            }
        }
    }

    private suspend fun ensureEmbeddingModelLoaded() {
        val active = db.modelDao().getActiveModel(ModelRole.EMBEDDING) ?: return
        app.container.withEmbedding { engine ->
            if (engine.loadedModelPath != active.filePath) engine.load(active.filePath, active.tokenizerPath)
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Index maintenance") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Text("Re-build the search index after changing the embedding model, or to repair a corrupted index. Existing chunks are replaced.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = { vm.reindexAll() }, enabled = !busy, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Re-index all documents")
            }
            if (busy && busyDocumentId == null) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            status?.let {
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
            HorizontalDivider()
            Text("Documents (${documents.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(documents, key = { it.id }) { doc ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(doc.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(doc.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (busyDocumentId == doc.id) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                            } else {
                                OutlinedButton(onClick = { vm.reindexOne(doc.id) }, enabled = !busy) { Text("Re-index") }
                            }
                        }
                    }
                }
            }
        }
    }
}
