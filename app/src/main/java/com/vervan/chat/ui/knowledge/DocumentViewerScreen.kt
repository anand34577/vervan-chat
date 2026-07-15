package com.vervan.chat.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.theme.VervanMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(documentId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: DocumentViewerViewModel = viewModel(factory = viewModelFactory { initializer { DocumentViewerViewModel(app, documentId) } })
    val document by vm.document.collectAsState()
    val chunks by vm.chunks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.displayName ?: "Document", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { vm.reindex() }) { Icon(Icons.Filled.Refresh, contentDescription = "Re-index") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            document?.let { doc ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(doc.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${chunks.size} sections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(chunks, key = { it.id }) { chunk ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            if (chunk.sectionPath.isNotBlank()) {
                                Text(chunk.sectionPath, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, fontFamily = VervanMono)
                            }
                            Text(chunk.text, style = MaterialTheme.typography.bodySmall)
                            Text("~${chunk.tokenCount} tokens", style = MaterialTheme.typography.labelSmall, fontFamily = VervanMono, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                if (chunks.isEmpty()) {
                    item {
                        Text("No indexed content. Tap re-index if the source file is available.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
