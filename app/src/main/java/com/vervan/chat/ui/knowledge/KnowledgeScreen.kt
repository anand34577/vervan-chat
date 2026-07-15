package com.vervan.chat.ui.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.DocumentStatus
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnowledgeScreen(onOpenKb: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: KnowledgeViewModel = viewModel(factory = viewModelFactory { initializer { KnowledgeViewModel(app) } })
    val kbs by vm.knowledgeBases.collectAsState()
    val kbStats by vm.kbStats.collectAsState()
    val indexing by vm.indexingDocuments.collectAsState()
    val recentDocuments by vm.recentDocuments.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

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
                body = "Organize documents into searchable bases. Retrieval, citations, and indexes stay on this device."
            )
            VervanSectionHeader("Knowledge bases", count = kbs.size, actionLabel = "New", onAction = { showCreate = true })
            if (kbs.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Build your first knowledge base",
                    body = "Group PDFs, notes, and documents so chats can retrieve exact passages and cite their sources.",
                    actionLabel = "Create knowledge base",
                    onAction = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                kbs.forEach { kb ->
                    val stats = kbStats[kb.id]
                    KbCard(kb, docCount = stats?.first ?: 0, allReady = stats?.second ?: true, onClick = { onOpenKb(kb.id) })
                }
                if (kbs.isNotEmpty()) {
                    Card(
                        onClick = { showCreate = true },
                        modifier = Modifier.widthIn(min = 180.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.padding(Space.lg)) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("New base", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.xl))
                            Text("Create a document collection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (indexing.isNotEmpty()) {
                VervanSectionHeader("Indexing queue", count = indexing.size)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(Space.lg)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Space.sm))
                        indexing.forEach { doc -> DocRow(doc) }
                    }
                }
            }

            if (recentDocuments.isNotEmpty()) {
                VervanSectionHeader("Recent documents", count = recentDocuments.size)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(Space.lg)) {
                        recentDocuments.take(8).forEach { doc -> DocRow(doc) }
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
        modifier = Modifier.widthIn(min = 180.dp, max = 300.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(Space.lg)) {
            Box(
                Modifier.size(30.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Text(kb.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.xl))
            Text(
                "$docCount document(s) · ${if (allReady) "ready" else "indexing"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocRow(doc: Document) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(doc.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                doc.failureReason ?: doc.status.name.lowercase().replaceFirstChar { it.uppercase() },
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
