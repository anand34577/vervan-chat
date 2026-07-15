package com.vervan.chat.ui.memory

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MemorySuggestion
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ValidationLimits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySuggestionsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: MemorySuggestionsViewModel = viewModel(factory = viewModelFactory { initializer { MemorySuggestionsViewModel(app) } })
    val pending by vm.pending.collectAsState()
    var conflictDialog by remember { mutableStateOf<Pair<MemorySuggestion, com.vervan.chat.data.db.entities.Memory>?>(null) }
    var editDialog by remember { mutableStateOf<MemorySuggestion?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory suggestions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No pending suggestions.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Suggestions land here when something looks worth remembering. Nothing is saved until you accept it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
                items(pending, key = { it.id }) { suggestion ->
                    val conflict = vm.conflictFor(suggestion)
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(suggestion.scope.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (suggestion.key != null) Text("· key: ${suggestion.key}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (conflict != null) {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Text("Conflicts with existing: \"${conflict.text.take(60)}\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = {
                                    if (conflict != null) conflictDialog = suggestion to conflict
                                    else vm.accept(suggestion, overwriteConflict = false)
                                }) { Icon(Icons.Filled.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { vm.reject(suggestion) }) { Icon(Icons.Filled.Close, contentDescription = "Reject") }
                                IconButton(onClick = { editDialog = suggestion }) { Icon(Icons.Filled.Edit, contentDescription = "Edit and accept") }
                                IconButton(onClick = { menuFor = suggestion.id }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                                DropdownMenu(expanded = menuFor == suggestion.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Never suggest this type") },
                                        enabled = suggestion.key != null,
                                        onClick = { menuFor = null; vm.blockType(suggestion) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    conflictDialog?.let { (suggestion, conflict) ->
        AlertDialog(
            onDismissRequest = { conflictDialog = null },
            title = { Text("Conflict") },
            text = {
                Column {
                    Text("A memory with key \"${suggestion.key}\" already exists:")
                    Text("\"${conflict.text}\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Suggested new value:")
                    Text("\"${suggestion.text}\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Replace the existing one, or keep both?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.accept(suggestion, overwriteConflict = true)
                    conflictDialog = null
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.accept(suggestion, overwriteConflict = false)
                    conflictDialog = null
                }) { Text("Keep both") }
            }
        )
    }

    editDialog?.let { suggestion ->
        var text by remember(suggestion.id) { mutableStateOf(suggestion.text) }
        var key by remember(suggestion.id) { mutableStateOf(suggestion.key ?: "") }
        AlertDialog(
            onDismissRequest = { editDialog = null },
            title = { Text("Edit and accept") },
            text = {
                Column {
                    BoundedTextField(value = text, onValueChange = { text = it }, maxLength = ValidationLimits.MEMORY_TEXT)
                    BoundedTextField(
                        value = key, onValueChange = { key = it }, singleLine = true,
                        placeholder = "Key (optional)", maxLength = ValidationLimits.MEMORY_KEY,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { vm.editAndAccept(suggestion, text, key.trim().ifBlank { null }); editDialog = null }
                ) { Text("Accept") }
            },
            dismissButton = { TextButton(onClick = { editDialog = null }) { Text("Cancel") } }
        )
    }
}
