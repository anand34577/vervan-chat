package com.vervan.chat.ui.memory

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
import androidx.compose.runtime.collectAsState
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
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit = {}, onOpenSuggestions: () -> Unit = {}, highlightMemoryId: String? = null) {
    val app = LocalContext.current.applicationContext as VervanApp
    val memories by app.container.db.memoryDao().observeAll().collectAsState(initial = emptyList())
    val pendingSuggestions by app.container.db.memorySuggestionDao().observePendingCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
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
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(8.dp), state = listState) {
            item {
                Text(
                    "Only memories you approve are used. Nothing here was added without you confirming it.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(memories, key = { it.id }) { memory ->
                val highlighted = memory.id == highlightMemoryId
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = if (highlighted) {
                        androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        androidx.compose.material3.CardDefaults.cardColors()
                    }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(memory.text, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val subtitle = memory.scope.name + (memory.key?.let { " · key: $it" } ?: "")
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Switch(checked = memory.enabled, onCheckedChange = { checked ->
                            scope.launch { app.container.db.memoryDao().update(memory.copy(enabled = checked)) }
                        })
                        TextButton(onClick = { scope.launch { app.container.db.memoryDao().update(memory.copy(deletedAt = System.currentTimeMillis())) } }) { Text("Delete") }
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
                        supportingText = "Saving with the same key replaces the old value instead of adding a duplicate",
                        modifier = Modifier.padding(top = 8.dp)
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
                            app.container.db.memoryDao().upsert(memory)
                        }
                    }
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
