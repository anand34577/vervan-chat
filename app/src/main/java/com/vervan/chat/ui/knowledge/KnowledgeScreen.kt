package com.vervan.chat.ui.knowledge

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
    val kbs by vm.knowledgeBases.collectAsState()
    val kbsLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val kbStats by vm.kbStats.collectAsState()
    val indexing by vm.indexingDocuments.collectAsState()
    val recentDocuments by vm.recentDocuments.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val totalDocuments = kbStats.values.sumOf { it.first }
    val readyBases = kbStats.values.count { it.second }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text("Knowledge")
                        Text("Searchable, cited, on-device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "New knowledge base") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm)) {
            FeatureHero(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                eyebrow = "Grounded answers",
                title = "Your private knowledge",
                body = "Organize documents for private search and cited answers.",
                trailing = {
                    SemanticChip(
                        text = if (indexing.isEmpty()) "On-device" else "Processing",
                        tone = if (indexing.isEmpty()) ChipTone.Success else ChipTone.Warning
                    )
                }
            )
            KnowledgeSnapshotCard(
                knowledgeBaseCount = kbs.size,
                documentCount = totalDocuments,
                readyBaseCount = readyBases,
                indexingCount = indexing.size
            )
            VervanSectionHeader("Knowledge bases", count = kbs.size, actionLabel = "New", onAction = { showCreate = true })
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
                    title = "Build your first knowledge base",
                    body = "Group documents so chats can find and cite exact passages.",
                    actionLabel = "Create knowledge base",
                    onAction = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)
                )
            }
            if (error == null && !kbsLoading) {
            AdaptiveCardFlow {
                kbs.forEach { kb ->
                    val stats = kbStats[kb.id]
                    Box(Modifier.weight(1f).heightIn(min = 132.dp)) {
                        KbCard(
                            kb,
                            docCount = stats?.first ?: 0,
                            allReady = stats?.second ?: true,
                            onClick = { onOpenKb(kb.id) }
                        )
                    }
                }
                if (kbs.isNotEmpty()) {
                    Box(Modifier.weight(1f).heightIn(min = 132.dp)) {
                        ActionTile(
                            icon = Icons.Filled.Add,
                            title = "New knowledge base",
                            body = "Create a document collection",
                            onClick = { showCreate = true },
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            if (indexing.isNotEmpty()) {
                VervanSectionHeader("Indexing queue", count = indexing.size)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
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
                VervanSectionHeader("Recent documents", count = recentDocuments.size)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
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
            title = { Text("New knowledge base") },
            text = {
                com.vervan.chat.ui.common.BoundedTextField(
                    value = name, onValueChange = { name = it }, placeholder = "Name",
                    singleLine = true, maxLength = com.vervan.chat.ui.common.ValidationLimits.KNOWLEDGE_BASE_NAME
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (name.isNotBlank()) { vm.createKnowledgeBase(name.trim()); showCreate = false } },
                    enabled = name.isNotBlank() && name.length <= com.vervan.chat.ui.common.ValidationLimits.KNOWLEDGE_BASE_NAME
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun KbCard(kb: KnowledgeBase, docCount: Int, allReady: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    OverflowTooltipText(text = kb.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$docCount ${if (docCount == 1) "document" else "documents"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SemanticChip(
                    text = if (allReady) "Ready" else "Indexing",
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
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            KnowledgeMetric(knowledgeBaseCount.toString(), "Bases", Modifier.weight(1f))
            KnowledgeMetric(documentCount.toString(), "Documents", Modifier.weight(1f))
            KnowledgeMetric(
                if (indexingCount == 0) "$readyBaseCount" else "$indexingCount",
                if (indexingCount == 0) "Ready" else "Indexing",
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
            Text(
                if (doc.status in setOf(DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED)) {
                    doc.failureReason.toUserMessage()
                } else {
                    doc.failureReason ?: doc.status.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val (label, tone) = when (doc.status) {
            DocumentStatus.READY -> "Ready" to ChipTone.Success
            DocumentStatus.FAILED, DocumentStatus.UNSUPPORTED -> "Failed" to ChipTone.Error
            else -> "Indexing" to ChipTone.Warning
        }
        SemanticChip(label, tone)
    }
}
