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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.theme.VervanMono
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(documentId: String, onBack: () -> Unit, onOpenPdfPage: (documentId: String, page: Int) -> Unit = { _, _ -> }) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: DocumentViewerViewModel = viewModel(factory = viewModelFactory { initializer { DocumentViewerViewModel(app, documentId) } })
    val document by vm.document.collectAsState()
    val chunks by vm.chunks.collectAsState()
    val reindexing by vm.reindexing.collectAsState()
    val error by vm.error.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_document_preview), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    // "Open externally" lives on the document card below (tap the whole card) —
                    // this used to also have its own copy of the same action in the top bar,
                    // two buttons doing the identical thing on one small screen.
                    if (chunks.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val file = vm.exportExtractedText()
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, document?.displayName ?: "Extracted text")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, "Export extracted text"))
                            }
                        }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.ui_documentviewerscreen_90_export_extracted_text)) }
                    }
                    if (reindexing) {
                        androidx.compose.foundation.layout.Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { vm.reindex() }, enabled = document != null) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.knowledge_reindex)) }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.document_unavailable),
                message = loadError ?: stringResource(R.string.document_unavailable_message),
                recovery = stringResource(R.string.document_unavailable_recovery),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(top = Space.sm)
            )
            isLoading -> LoadingSkeletonList(rows = 7, modifier = Modifier.padding(top = Space.sm))
            document == null -> EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.document_not_found),
                body = stringResource(R.string.document_not_found_body),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = Modifier.fillMaxSize().padding(top = Space.sm),
                centered = true
            )
            else -> Column(Modifier.fillMaxSize()) {
            document?.let { doc ->
                val openOriginalDescription = stringResource(R.string.ui_documentviewerscreen_open_original, doc.displayName)
                val sectionsLabel = stringResource(R.string.ui_documentviewerscreen_sections, chunks.size)
                ModernistScreenHeader(
                    eyebrow = stringResource(R.string.ui_documentviewerscreen_126_source_file),
                    title = doc.displayName,
                    body = stringResource(R.string.ui_documentviewerscreen_128_citation_target_searchable_text_and_source_p),
                    modifier = Modifier.padding(start = Space.md, end = Space.md, top = Space.sm),
                    trailing = { ModernistTag("STORED ON DEVICE", active = true) },
                )
                val fileExists = java.io.File(doc.filePath).exists()
                Card(
                    onClick = { if (fileExists) com.vervan.chat.ui.common.openWithExternalApp(context, java.io.File(doc.filePath), doc.mimeType) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)
                        .semantics { contentDescription = openOriginalDescription },
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Surface(
                            shape = MaterialTheme.shapes.medium,
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
                                if (bytes < 1024 * 1024) "%.1f KB".format(java.util.Locale.getDefault(), bytes / 1024.0)
                                else "%.1f MB".format(java.util.Locale.getDefault(), bytes / (1024.0 * 1024.0))
                            } else stringResource(R.string.ui_documentviewerscreen_original_unavailable)
                            Text(
                                stringResource(R.string.ui_documentviewerscreen_file_summary, doc.mimeType.substringAfterLast('/').uppercase(), size, chunks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                            Row(Modifier.padding(top = Space.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.ui_documentviewerscreen_162_stored_on_this_device), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = if (fileExists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs)) {
                    Text(doc.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(sectionsLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let {
                OperationErrorCard(
                    title = stringResource(R.string.ui_documentviewerscreen_179_couldn_t_rebuild_this_index),
                    message = it,
                    recovery = stringResource(R.string.ui_documentviewerscreen_reindex_recovery),
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::reindex
                )
            }
            VervanSectionHeader(
                title = stringResource(R.string.ui_documentviewerscreen_188_searchable_text),
                count = chunks.size,
                modifier = Modifier.padding(horizontal = Space.md)
            )
            LazyColumn(Modifier.fillMaxSize().padding(Space.sm)) {
                items(chunks, key = { it.id }) { chunk ->
                    Card(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                        Column(Modifier.padding(Space.md)) {
                            if (chunk.sectionPath.isNotBlank()) {
                                Text(chunk.sectionPath, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, fontFamily = VervanMono)
                            }
                            Text(chunk.text, style = MaterialTheme.typography.bodySmall)
                            Row(
                                Modifier.fillMaxWidth().padding(top = Space.xs),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.ui_documentviewerscreen_tokens, chunk.tokenCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = VervanMono,
                                    modifier = Modifier.weight(1f)
                                )
                                chunk.pageNumber?.let { page ->
                                    com.vervan.chat.ui.common.VervanTextButton(onClick = { onOpenPdfPage(documentId, page) }) {
                                        Text(stringResource(R.string.ui_documentviewerscreen_page, page))
                                    }
                                }
                            }
                        }
                    }
                }
                if (chunks.isEmpty()) {
                    item {
                        Text(stringResource(R.string.ui_documentviewerscreen_221_no_searchable_text_re_index_if_the_source_fi), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg))
                    }
                }
            }
        }
        }
        }
    }
}
