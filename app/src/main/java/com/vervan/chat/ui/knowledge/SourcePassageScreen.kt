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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vervan.chat.data.db.entities.Chunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcePassageViewModel(private val app: VervanApp, private val chunkId: String) : ViewModel() {
    private val db = app.container.db

    private val _chunk = MutableStateFlow<Chunk?>(null)
    val chunk: StateFlow<Chunk?> = _chunk

    private val _neighbors = MutableStateFlow<List<Chunk>>(emptyList())
    val neighbors: StateFlow<List<Chunk>> = _neighbors

    init {
        viewModelScope.launch {
            val c = db.chunkDao().getChunk(chunkId)
            _chunk.value = c
            if (c != null) {
                // Load all chunks for the same document so the passage is shown in context.
                val all = db.chunkDao().observeForDocument(c.documentId).first()
                _neighbors.value = all
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePassageScreen(chunkId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SourcePassageViewModel = viewModel(factory = viewModelFactory { initializer { SourcePassageViewModel(app, chunkId) } })
    val chunk by vm.chunk.collectAsState()
    val neighbors by vm.neighbors.collectAsState()
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            items(neighbors, key = { it.id }) { c ->
                val isTarget = c.id == chunk?.id
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        if (c.sectionPath.isNotBlank()) {
                            Text(c.sectionPath, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            c.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isTarget) {
                            Text("↳ cited passage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
