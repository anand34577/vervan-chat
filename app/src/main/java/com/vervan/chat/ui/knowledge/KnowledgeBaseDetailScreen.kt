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
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.ui.common.JobProgressCard
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.PageContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

private enum class DocFilter(val labelRes: Int) {
    ALL(com.vervan.chat.R.string.chat_filter_all),
    READY(com.vervan.chat.R.string.knowledge_filter_ready),
    PROCESSING(com.vervan.chat.R.string.knowledge_filter_processing),
    FAILED(com.vervan.chat.R.string.knowledge_filter_failed),
    UNSUPPORTED(com.vervan.chat.R.string.knowledge_filter_unsupported)
}

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
fun KnowledgeBaseDetailScreen(
    kbId: String,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit = {},
    showBackButton: Boolean = true
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: KnowledgeBaseDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { KnowledgeBaseDetailViewModel(app, kbId) }
    })
    val documents by vm.documents.collectAsState()
    val documentsLoading by vm.documentsLoading.collectAsState()
    val documentsLoadError by vm.documentsLoadError.collectAsState()
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
                    deleteContentDescription = stringResource(R.string.library_delete_selected),
                    extraActions = {
                        IconButton(
                            onClick = {
                                vm.reindexDocuments(selected)
                                selected = emptySet()
                                selectionMode = false
                            },
                            enabled = selected.isNotEmpty()
                        ) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.knowledge_reindex_selected)) }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.knowledge_documents_title)) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Filled.Checklist, contentDescription = stringResource(R.string.knowledge_select_documents))
                        }
                        IconButton(onClick = { confirmDeleteKb = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.knowledge_delete_base))
                        }
                    }
                )
            }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
            FeatureHero(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                eyebrow = stringResource(R.string.knowledge_collection_eyebrow),
                title = stringResource(R.string.knowledge_collection_title),
                body = stringResource(R.string.knowledge_collection_body)
            )
            Button(
                onClick = {
                    pickFile.launch(
                        // "*/*" used to be tacked onto the end of this list, which made the whole
                        // filter a no-op — the system picker shows everything the moment any entry
                        // is "*/*", so it let the user pick a file type the import pipeline can't
                        // read at all (only to fail later at extraction). Listing only the types
                        // DocumentImportManager/Chunker actually support keeps the picker itself
                        // from offering something doomed to fail.
                        arrayOf(
                            "text/*", "application/pdf", "application/epub+zip",
                            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        )
                    )
                },
                enabled = !importing,
                shape = MaterialTheme.shapes.small,
            ) {
                if (importing) CircularProgressIndicator(Modifier.size(18.dp).padding(end = Space.xs), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text(if (importing) stringResource(R.string.knowledge_importing) else stringResource(R.string.knowledge_import_document))
            }
            error?.let { ErrorCard(stringResource(R.string.knowledge_import_error), it, Modifier.padding(top = Space.sm)) }

            if (documentsLoadError != null) {
                OperationErrorCard(
                    title = stringResource(R.string.knowledge_documents_unavailable),
                    message = documentsLoadError ?: stringResource(R.string.knowledge_documents_unavailable_message),
                    recovery = stringResource(R.string.knowledge_documents_unavailable_recovery),
                    modifier = Modifier.padding(top = Space.md),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retryDocumentsLoad
                )
            } else if (documentsLoading) {
                LoadingSkeletonList(rows = 7, modifier = Modifier.padding(top = Space.md))
            } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.sm),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
            ) {
                DocFilter.entries.forEach { f ->
                    VervanFilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(stringResource(R.string.ui_knowledge_filter_count, stringResource(f.labelRes), documents.count { it.status.matchesFilter(f) })) }
                    )
                }
            }

            if (visibleDocuments.isEmpty()) {
                com.vervan.chat.ui.common.EmptyState(
                    icon = Icons.Filled.Description,
                    title = if (documents.isEmpty()) stringResource(R.string.knowledge_no_documents) else stringResource(R.string.knowledge_no_filtered_documents, stringResource(filter.labelRes).lowercase()),
                    body = if (documents.isEmpty()) stringResource(R.string.knowledge_import_to_search) else stringResource(R.string.knowledge_try_filter)
                )
            } else {
                val embedProgress by app.container.documentImportManager.embedProgress.collectAsState()
                LazyColumn(
                    Modifier.fillMaxSize().padding(top = Space.sm),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md)
                ) {
                    items(visibleDocuments, key = { it.id }) { doc ->
                        DocumentRow(
                            document = doc,
                            embedProgress = embedProgress?.takeIf { it.documentId == doc.id },
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
        }
    }

    pendingVersionConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { vm.dismissVersionConflict() },
            title = { Text(stringResource(R.string.knowledge_conflict_title, conflict.existing.displayName)) },
            text = {
                Text(
                    stringResource(R.string.knowledge_conflict_body)
                )
            },
            confirmButton = { TextButton(onClick = { vm.resolveVersionConflict(replace = true) }) { Text(stringResource(R.string.action_replace)) } },
            dismissButton = { TextButton(onClick = { vm.resolveVersionConflict(replace = false) }) { Text(stringResource(R.string.memory_keep_both)) } }
        )
    }

    if (confirmDeleteKb) {
        ConfirmDialog(
            title = stringResource(R.string.knowledge_delete_base_title),
            body = stringResource(R.string.knowledge_delete_base_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDeleteKb = false; vm.deleteKnowledgeBase(onBack) },
            onDismiss = { confirmDeleteKb = false }
        )
    }

    if (confirmBulkDeleteDocs) {
        val count = selected.size
        ConfirmDialog(
            title = stringResource(R.string.knowledge_delete_selected_title),
            body = stringResource(R.string.knowledge_delete_selected_body, count, if (count == 1) "" else "s"),
            confirmLabel = stringResource(R.string.action_delete),
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

/** staged import progress — Reading → OCR → Extracting → Chunking → Embedding → Ready,
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
@Composable
private fun DocumentRow(
    document: Document,
    // Real "N of M chunks embedded" for the one stage that can otherwise sit at a flat, unmoving
    // "Embedding…" for a long time (a remote embedding model means one HTTP round trip per chunk)
    // — see DocumentImportManager.EmbedProgress. Null outside that stage, or when this row isn't
    // the document currently embedding.
    embedProgress: com.vervan.chat.model.DocumentImportManager.EmbedProgress?,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    val inProgress = document.status !in setOf(DocumentStatus.READY, DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED)
    if (inProgress) {
        // Blend the fine-grained within-stage fraction into the coarse 5-stage one instead of
        // jumping straight from 4/5 to 5/5 the instant embedding starts.
        val withinStage = embedProgress?.let { it.done.toFloat() / it.total.coerceAtLeast(1) } ?: 0f
        JobProgressCard(
            title = document.displayName,
            stage = embedProgress?.let { stringResource(R.string.knowledge_embedding_progress, it.done, it.total) } ?: stringResource(
                when (document.status) {
                    DocumentStatus.READING -> R.string.knowledge_stage_reading
                    DocumentStatus.OCR_RUNNING -> R.string.knowledge_stage_ocr
                    DocumentStatus.EXTRACTING -> R.string.knowledge_stage_extracting
                    DocumentStatus.CHUNKING -> R.string.knowledge_stage_chunking
                    DocumentStatus.EMBEDDING -> R.string.knowledge_stage_embedding
                    else -> R.string.knowledge_stage_ready
                }
            ),
            progress = (document.status.stageIndex() + withinStage) / 5f,
            modifier = Modifier.padding(vertical = Space.xs)
        )
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)
            .selectableItem(
                selectionMode = selectionMode,
                onClick = onOpen,
                onToggleSelected = onToggleSelected,
                onEnterSelection = onEnterSelection
            ),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else SurfaceRole.Card.cardColors(),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            } else {
                IconAffordance(Icons.Filled.Description, size = IconAffordanceSize.Default)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = Space.md))
            }
            Column(Modifier.weight(1f)) {
                OverflowTooltipText(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                val failed = document.status == DocumentStatus.FAILED || document.status == DocumentStatus.UNSUPPORTED
                val statusText = when (document.status) {
                    DocumentStatus.READY -> document.failureReason
                        ?: if (document.ocrApplied) stringResource(R.string.knowledge_ready_ocr) else stringResource(R.string.knowledge_ready_tap)
                    DocumentStatus.OCR_RUNNING -> stringResource(R.string.knowledge_running_ocr)
                    DocumentStatus.FAILED -> stringResource(R.string.knowledge_failed_status, document.failureReason.toUserMessage())
                    DocumentStatus.UNSUPPORTED -> stringResource(R.string.knowledge_unsupported_status, document.failureReason.toUserMessage())
                    else -> stringResource(R.string.knowledge_stage_status, document.status.name.lowercase().replaceFirstChar { it.uppercase() })
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (failed && !selectionMode) {
                    TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (!selectionMode) {
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { showActions = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.knowledge_document_actions)) }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.knowledge_reindex)) },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            onClick = { showActions = false; onRetry() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showActions = false; confirmDelete = true }
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.knowledge_delete_document_title, document.displayName),
            body = stringResource(R.string.knowledge_delete_document_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}
