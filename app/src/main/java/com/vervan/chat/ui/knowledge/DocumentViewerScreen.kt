package com.vervan.chat.ui.knowledge

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
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
    val reindexing by vm.reindexing.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document preview", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(
                        enabled = document?.filePath?.let { java.io.File(it).exists() } == true,
                        onClick = {
                            val doc = document ?: return@IconButton
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(doc.filePath)
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(uri, doc.mimeType)
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Open with…")) }
                        }
                    ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open with another app") }
                    if (reindexing) {
                        androidx.compose.foundation.layout.Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { vm.reindex() }) { Icon(Icons.Filled.Refresh, contentDescription = "Re-index") }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize()) {
            document?.let { doc ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.padding(Space.md).size(32.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                            Text(doc.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            val file = java.io.File(doc.filePath)
                            val size = if (file.exists()) {
                                val bytes = file.length()
                                if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0) else "%.1f MB".format(bytes / (1024.0 * 1024.0))
                            } else "Original unavailable"
                            Text(
                                "${doc.mimeType.substringAfterLast('/').uppercase()} · $size · ${chunks.size} sections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                            Row(Modifier.padding(top = Space.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Private on this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                        IconButton(
                            enabled = java.io.File(doc.filePath).exists(),
                            onClick = { com.vervan.chat.ui.common.openWithExternalApp(context, java.io.File(doc.filePath), doc.mimeType) }
                        ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open original document") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(doc.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${chunks.size} sections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let {
                com.vervan.chat.ui.common.ErrorCard("Couldn't rebuild this index", it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            VervanSectionHeader(
                title = "Searchable text",
                count = chunks.size,
                modifier = Modifier.padding(horizontal = Space.md)
            )
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
                        Text("No searchable text. Re-index if the source file is available.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        }
    }
}
