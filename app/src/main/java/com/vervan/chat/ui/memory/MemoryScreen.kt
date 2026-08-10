package com.vervan.chat.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanContentWidth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit = {}, onOpenSuggestions: () -> Unit = {}, highlightMemoryId: String? = null) {
    val app = LocalContext.current.applicationContext as VervanApp
    val memories by app.container.db.memoryDao().observeAll().collectAsState(initial = emptyList())
    val pendingSuggestions by app.container.db.memorySuggestionDao().observePendingCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Memory?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(memories, highlightMemoryId) {
        if (highlightMemoryId != null) {
            val index = memories.indexOfFirst { it.id == highlightMemoryId }
            if (index >= 0) listState.animateScrollToItem(index + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenSuggestions) {
                        BadgedBox(badge = { if (pendingSuggestions > 0) Badge { Text("$pendingSuggestions") } }) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = "Suggested memories")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add memory") }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = VervanContentWidth.standard) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item {
                FeatureHero(
                    eyebrow = "Private and approved",
                    title = "What the assistant remembers",
                    body = "Review details saved for future chats. Vervan uses relevant memories when available.",
                    icon = Icons.Filled.Lightbulb
                )
            }
            item {
                MemorySnapshotCard(
                    total = memories.size,
                    enabled = memories.count { it.enabled }
                )
            }
            item {
                VervanSectionHeader(
                    title = "Saved memories",
                    count = memories.size,
                    topPadding = 0.dp,
                    bottomPadding = 0.dp
                )
            }
            if (memories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Lightbulb,
                        title = "No memories saved",
                        body = "Save a preference, fact, or instruction for future chats."
                    )
                }
            }
            items(memories, key = { it.id }) { memory ->
                val highlighted = memory.id == highlightMemoryId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = if (highlighted) {
                        androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        SurfaceRole.Card.cardColors()
                    },
                    border = if (highlighted) {
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    } else {
                        SurfaceRole.Card.border()
                    },
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(memory.text, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val subtitle = memory.scope.name + (memory.key?.let { " · key: $it" } ?: "") +
                                if (memory.embedding != null) " · Semantic ready" else ""
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Switch(checked = memory.enabled, onCheckedChange = { checked ->
                            scope.launch { app.container.db.memoryDao().update(memory.copy(enabled = checked)) }
                        })
                        TextButton(onClick = { pendingDelete = memory }, modifier = Modifier.padding(start = Space.xs)) { Text("Delete") }
                    }
                }
            }
        }
        }
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New memory") },
            text = {
                androidx.compose.foundation.layout.Column {
                    BoundedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = "e.g. Prefers concise answers",
                        maxLength = ValidationLimits.MEMORY_TEXT
                    )
                    BoundedTextField(
                        value = key, onValueChange = { key = it },
                        placeholder = "Key (optional, e.g. \"tone\")",
                        singleLine = true,
                        maxLength = ValidationLimits.MEMORY_KEY,
                supportingText = "Using the same key replaces its current value",
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && text.length <= ValidationLimits.MEMORY_TEXT && key.length <= ValidationLimits.MEMORY_KEY,
                    onClick = {
                    if (text.isNotBlank()) {
                        scope.launch {
                            val trimmedKey = key.trim().ifBlank { null }
                            val existing = trimmedKey?.let {
                                app.container.db.memoryDao().findByKey(it, com.vervan.chat.data.db.entities.MemoryScope.GLOBAL, null)
                            }
                            val memory = if (existing != null) {
                                existing.copy(text = text, key = trimmedKey)
                            } else {
                                Memory(text = text, key = trimmedKey)
                            }
                            app.container.memoryRepository.upsert(memory)
                        }
                    }
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { memory ->
        ConfirmDialog(
            title = "Delete memory?",
            body = "\"${memory.text}\" will be permanently deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                scope.launch { app.container.db.memoryDao().update(memory.copy(deletedAt = System.currentTimeMillis())) }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun MemorySnapshotCard(total: Int, enabled: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            MemoryMetric(total.toString(), "Saved", Modifier.weight(1f))
            MemoryMetric(enabled.toString(), "Enabled", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            MemoryMetric((total - enabled).coerceAtLeast(0).toString(), "Paused", Modifier.weight(1f), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    androidx.compose.foundation.layout.Column(
        modifier.padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
