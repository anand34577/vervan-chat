package com.vervan.chat.ui.knowledge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

class SourcePassageViewModel(private val app: VervanApp, private val chunkId: String) : ViewModel() {
    private val db = app.container.db

    private val _chunk = MutableStateFlow<Chunk?>(null)
    val chunk: StateFlow<Chunk?> = _chunk

    private val _neighbors = MutableStateFlow<List<Chunk>>(emptyList())
    val neighbors: StateFlow<List<Chunk>> = _neighbors

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val c = db.chunkDao().getChunk(chunkId)
                _chunk.value = c
                if (c != null) {
                    // Load all chunks for the same document so the passage is shown in context.
                    val all = db.chunkDao().observeForDocument(c.documentId).first()
                    _neighbors.value = all
                    _document.value = db.documentDao().get(c.documentId)
                } else {
                    _neighbors.value = emptyList()
                    _document.value = null
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _error.value = t.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePassageScreen(chunkId: String, onBack: () -> Unit, onOpenPdfPage: (documentId: String, page: Int) -> Unit = { _, _ -> }) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SourcePassageViewModel = viewModel(factory = viewModelFactory { initializer { SourcePassageViewModel(app, chunkId) } })
    val chunk by vm.chunk.collectAsState()
    val neighbors by vm.neighbors.collectAsState()
    val document by vm.document.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(chunk, neighbors) {
        val target = chunk ?: return@LaunchedEffect
        val index = neighbors.indexOfFirst { it.id == target.id }
        if (index >= 0) listState.scrollToItem(index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source passage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    val page = chunk?.pageNumber
                    val docId = document?.id
                    if (page != null && docId != null) {
                        IconButton(onClick = { onOpenPdfPage(docId, page) }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "View page $page in PDF")
                        }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            when {
                error != null -> OperationErrorCard(
                    title = stringResource(R.string.source_passage_unavailable),
                    message = error.orEmpty(),
                    recovery = stringResource(R.string.source_passage_unavailable_recovery),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry,
                    modifier = Modifier.padding(Space.md)
                )
                isLoading -> LoadingSkeletonList(rows = 5, modifier = Modifier.padding(Space.md))
                chunk == null -> EmptyState(
                    icon = Icons.Filled.PictureAsPdf,
                    title = stringResource(R.string.source_passage_not_found),
                    body = stringResource(R.string.source_passage_not_found_body),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(Space.sm)) {
                    items(neighbors, key = { it.id }) { c ->
                        val isTarget = c.id == chunk?.id
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = Space.xs)
                        ) {
                            Column(Modifier.padding(Space.md)) {
                                if (c.sectionPath.isNotBlank()) {
                                    Text(c.sectionPath, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    c.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isTarget) {
                                    Text(
                                        stringResource(R.string.source_cited_passage),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
