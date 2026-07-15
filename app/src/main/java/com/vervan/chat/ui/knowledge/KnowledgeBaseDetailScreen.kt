package com.vervan.chat.ui.knowledge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.ui.common.JobProgressCard
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults

private enum class DocFilter(val label: String) { ALL("All"), READY("Ready"), PROCESSING("Processing"), FAILED("Failed"), UNSUPPORTED("Unsupported") }

private fun DocumentStatus.matchesFilter(filter: DocFilter): Boolean = when (filter) {
    DocFilter.ALL -> true
    DocFilter.READY -> this == DocumentStatus.READY
    DocFilter.FAILED -> this == DocumentStatus.FAILED
    DocFilter.UNSUPPORTED -> this == DocumentStatus.UNSUPPORTED
    DocFilter.PROCESSING -> this in setOf(
        DocumentStatus.READING, DocumentStatus.OCR_RUNNING, DocumentStatus.EXTRACTING,
        DocumentStatus.CHUNKING, DocumentStatus.EMBEDDING
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseDetailScreen(kbId: String, onBack: () -> Unit, onOpenDocument: (String) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: KnowledgeBaseDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { KnowledgeBaseDetailViewModel(app, kbId) }
    })
    val documents by vm.documents.collectAsState()
    val importing by vm.importing.collectAsState()
    val error by vm.error.collectAsState()
    val pendingVersionConflict by vm.pendingVersionConflict.collectAsState()
    var confirmDeleteKb by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DocFilter.ALL) }
    val visibleDocuments = remember(documents, filter) { documents.filter { it.status.matchesFilter(filter) } }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDeleteDocs by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importDocument(it) }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == visibleDocuments.size && visibleDocuments.isNotEmpty(),
                    onToggleSelectAll = {
                        selected = if (selected.size == visibleDocuments.size && visibleDocuments.isNotEmpty()) emptySet() else visibleDocuments.map { it.id }.toSet()
                    },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { confirmBulkDeleteDocs = true },
                    deleteContentDescription = "Delete selected documents"
                )
            } else {
                TopAppBar(
                    title = { Text("Documents") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { confirmDeleteKb = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete knowledge base")
                        }
                    }
                    // Long-press a document row to enter selection mode — no separate
                    // top-bar entry point, matching every other list screen in the app.
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(
                onClick = {
                    pickFile.launch(
                        arrayOf(
                            "text/*", "application/pdf", "application/epub+zip",
                            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "*/*"
                        )
                    )
                },
                enabled = !importing
            ) {
                Text(if (importing) "Importing…" else "Import document")
            }
            if (importing) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(20.dp), strokeWidth = 2.dp)
            error?.let { ErrorCard("Couldn't import this document", it, Modifier.padding(top = 8.dp)) }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                DocFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} (${documents.count { it.status.matchesFilter(f) }})") }
                    )
                }
            }

            if (visibleDocuments.isEmpty()) {
                com.vervan.chat.ui.common.EmptyState(
                    icon = Icons.Filled.Description,
                    title = if (documents.isEmpty()) "No documents yet" else "No ${filter.label.lowercase()} documents",
                    body = if (documents.isEmpty()) "Import a file to make it searchable in chat." else "Try a different filter."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(visibleDocuments, key = { it.id }) { doc ->
                        DocumentRow(
                            document = doc,
                            onDelete = { vm.deleteDocument(doc) },
                            onOpen = { onOpenDocument(doc.id) },
                            onRetry = { vm.reindex(doc) },
                            selectionMode = selectionMode,
                            selected = doc.id in selected,
                            onToggleSelected = { selected = if (doc.id in selected) selected - doc.id else selected + doc.id },
                            onEnterSelection = { selectionMode = true; selected = selected + doc.id }
                        )
                    }
                }
            }
        }
    }

    pendingVersionConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { vm.dismissVersionConflict() },
            title = { Text("\"${conflict.existing.displayName}\" already exists") },
            text = {
                Text(
                    "A document with this name is already in this knowledge base, but the content is different. " +
                        "Replace the old version, or keep both?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.resolveVersionConflict(replace = true) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { vm.resolveVersionConflict(replace = false) }) { Text("Keep both") } }
        )
    }

    if (confirmDeleteKb) {
        ConfirmDialog(
            title = "Delete knowledge base?",
            body = "This removes the knowledge base and its imported documents. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDeleteKb = false; vm.deleteKnowledgeBase(onBack) },
            onDismiss = { confirmDeleteKb = false }
        )
    }

    if (confirmBulkDeleteDocs) {
        val count = selected.size
        ConfirmDialog(
            title = "Delete selected documents?",
            body = "$count document${if (count == 1) "" else "s"} and their indexed chunks will be removed from this knowledge base.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                vm.deleteDocuments(selected)
                confirmBulkDeleteDocs = false
                selected = emptySet()
                selectionMode = false
            },
            onDismiss = { confirmBulkDeleteDocs = false }
        )
    }
}

/** §7.3.2 staged import progress — Reading → OCR → Extracting → Chunking → Embedding → Ready,
 * driven directly by [DocumentStatus] (the pipeline already tracks these exact stages; this
 * was previously rendered as a plain "Extracting…" text line instead of a real progress card). */
private fun DocumentStatus.stageIndex(): Int = when (this) {
    DocumentStatus.READING -> 0
    DocumentStatus.OCR_RUNNING -> 1
    DocumentStatus.EXTRACTING -> 2
    DocumentStatus.CHUNKING -> 3
    DocumentStatus.EMBEDDING -> 4
    else -> 5
}
private val STAGE_LABELS = listOf("Reading", "Running OCR", "Extracting", "Chunking", "Embedding", "Ready")

@Composable
private fun DocumentRow(
    document: Document,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val inProgress = document.status !in setOf(DocumentStatus.READY, DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED)
    if (inProgress) {
        JobProgressCard(
            title = document.displayName,
            stage = STAGE_LABELS[document.status.stageIndex()],
            progress = (document.status.stageIndex() + 1) / 5f,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .selectableItem(
                selectionMode = selectionMode,
                onClick = onOpen,
                onToggleSelected = onToggleSelected,
                onEnterSelection = onEnterSelection
            ),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            }
            Column(Modifier.weight(1f)) {
                Text(document.displayName)
                val failed = document.status == DocumentStatus.FAILED || document.status == DocumentStatus.UNSUPPORTED
                val statusText = when (document.status) {
                    DocumentStatus.READY -> document.failureReason
                        ?: if (document.ocrApplied) "Ready (OCR text) — tap to view" else "Ready — tap to view"
                    DocumentStatus.OCR_RUNNING -> "Running OCR (scanned PDF)…"
                    DocumentStatus.FAILED -> "Failed: ${document.failureReason}"
                    DocumentStatus.UNSUPPORTED -> "Unsupported: ${document.failureReason}"
                    else -> document.status.name.lowercase().replaceFirstChar { it.uppercase() } + "…"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (failed && !selectionMode) {
                    TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Retry", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (!selectionMode) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${document.displayName}\"?",
            body = "This removes the document and its indexed chunks from this knowledge base.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}
