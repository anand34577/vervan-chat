package com.vervan.chat.ui.knowledge

import com.vervan.chat.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import com.vervan.chat.ui.common.VervanTopAppBar as MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.AdaptiveCardFlow
import com.vervan.chat.ui.common.ActionTile
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.system.toUserMessage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnowledgeScreen(onOpenKb: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: KnowledgeViewModel = viewModel(factory = viewModelFactory { initializer { KnowledgeViewModel(app) } })
    val kbs by vm.knowledgeBases.collectAsStateWithLifecycle()
    val kbsLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val kbStats by vm.kbStats.collectAsStateWithLifecycle()
    val indexing by vm.indexingDocuments.collectAsStateWithLifecycle()
    val recentDocuments by vm.recentDocuments.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val totalDocuments = kbStats.values.sumOf { it.first }
    val readyBases = kbStats.values.count { it.second }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.knowledge_knowledge))
                        Text(stringResource(R.string.knowledge_searchable_cited_on_device), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.knowledge_new_knowledge_base)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm)) {
            if (kbs.isNotEmpty()) {
                KnowledgeSnapshotCard(
                    knowledgeBaseCount = kbs.size,
                    documentCount = totalDocuments,
                    readyBaseCount = readyBases,
                    indexingCount = indexing.size
                )
                VervanSectionHeader(stringResource(R.string.knowledge_section_bases), count = kbs.size)
            }
            if (error != null) {
                OperationErrorCard(
                    title = stringResource(com.vervan.chat.R.string.knowledge_unavailable),
                    message = error ?: stringResource(com.vervan.chat.R.string.knowledge_unavailable_message),
                    recovery = stringResource(com.vervan.chat.R.string.knowledge_unavailable_recovery),
                    modifier = Modifier.padding(vertical = Space.md),
                    actionLabel = stringResource(com.vervan.chat.R.string.action_retry),
                    onAction = vm::retry
                )
            } else if (kbsLoading) {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (kbs.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.knowledge_build_your_first_knowledge_base),
                    body = stringResource(R.string.knowledge_group_documents_so_chats_can),
                    actionLabel = stringResource(R.string.knowledge_create_knowledge_base),
                    onAction = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)
                )
            }
            if (error == null && !kbsLoading) {
            AdaptiveCardFlow {
                kbs.forEach { kb ->
                    val stats = kbStats[kb.id]
                    Box(Modifier.weight(1f)) {
                        KbCard(
                            kb,
                            docCount = stats?.first ?: 0,
                            allReady = stats?.second ?: true,
                            onClick = { onOpenKb(kb.id) }
                        )
                    }
                }
                if (kbs.isNotEmpty()) {
                    Box(Modifier.weight(1f)) {
                        ActionTile(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.knowledge_new_knowledge_base),
                            body = stringResource(R.string.knowledge_create_a_document_collection),
                            onClick = { showCreate = true },
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            if (indexing.isNotEmpty()) {
                VervanSectionHeader(stringResource(R.string.knowledge_section_queue), count = indexing.size)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = SurfaceRole.Card.cardColors(),
                    border = SurfaceRole.Card.border()
                ) {
                    Column(Modifier.padding(Space.lg)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Space.sm))
                        indexing.forEach { doc -> DocRow(doc) }
                    }
                }
            }

            if (recentDocuments.isNotEmpty()) {
                VervanSectionHeader(stringResource(R.string.knowledge_section_recent), count = recentDocuments.size)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = SurfaceRole.Card.cardColors(),
                    border = SurfaceRole.Card.border()
                ) {
                    Column(Modifier.padding(Space.lg)) {
                        recentDocuments.take(8).forEach { doc -> DocRow(doc) }
                    }
                }
            }
            }
            Box(Modifier.padding(bottom = Space.xxl))
          }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.knowledge_new_knowledge_base)) },
            text = {
                com.vervan.chat.ui.common.BoundedTextField(
                    value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.workspace_name),
                    singleLine = true, maxLength = com.vervan.chat.ui.common.ValidationLimits.KNOWLEDGE_BASE_NAME
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) { vm.createKnowledgeBase(name.trim()); showCreate = false } },
                    enabled = name.isNotBlank() && name.length <= com.vervan.chat.ui.common.ValidationLimits.KNOWLEDGE_BASE_NAME
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun KbCard(kb: KnowledgeBase, docCount: Int, allReady: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    OverflowTooltipText(text = kb.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.knowledge_documents_count, docCount, docCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SemanticChip(
                    text = if (allReady) stringResource(R.string.knowledge_ready) else stringResource(R.string.knowledge_indexing),
                    tone = if (allReady) ChipTone.Success else ChipTone.Warning
                )
            }
        }
    }
}

@Composable
private fun KnowledgeSnapshotCard(
    knowledgeBaseCount: Int,
    documentCount: Int,
    readyBaseCount: Int,
    indexingCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            KnowledgeMetric(knowledgeBaseCount.toString(), stringResource(R.string.knowledge_metric_bases), Modifier.weight(1f))
            KnowledgeMetric(documentCount.toString(), stringResource(R.string.knowledge_metric_documents), Modifier.weight(1f))
            KnowledgeMetric(
                if (indexingCount == 0) "$readyBaseCount" else "$indexingCount",
                stringResource(if (indexingCount == 0) R.string.knowledge_ready else R.string.knowledge_indexing),
                Modifier.weight(1f),
                accent = if (indexingCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun KnowledgeMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    Column(modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocRow(doc: Document) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            OverflowTooltipText(
                text = doc.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
            // The chip on the right already names the status; the second line is only for the
            // reason a document failed, which the chip cannot carry.
            if (doc.status in setOf(DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED)) {
                Text(
                    doc.failureReason.toUserMessage(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        val (label, tone) = when (doc.status) {
            DocumentStatus.READY -> stringResource(R.string.knowledge_ready) to ChipTone.Success
            DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED -> stringResource(R.string.knowledge_failed) to ChipTone.Error
            else -> stringResource(R.string.knowledge_indexing) to ChipTone.Warning
        }
        SemanticChip(label, tone)
    }
}
