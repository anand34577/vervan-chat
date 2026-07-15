package com.vervan.chat.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderDetailScreen(folderId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit, onOpenNote: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: FolderDetailViewModel = viewModel(factory = viewModelFactory { initializer { FolderDetailViewModel(app, folderId) } })
    val folder by vm.folder.collectAsState()
    val chats by vm.chats.collectAsState()
    val notes by vm.notes.collectAsState()
    val personas by vm.personas.collectAsState()
    val models by vm.models.collectAsState()
    val knowledgeBases = app.container.db.knowledgeBaseDao().observeAll().collectAsState(initial = emptyList()).value
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "Folder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = { renaming = true }, enabled = folder != null) { Text("Rename") }
                    TextButton(onClick = { folder?.let { vm.delete(it); onBack() } }, enabled = folder != null) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Folder defaults", style = MaterialTheme.typography.titleSmall)
            Text("New chats in this folder start with these defaults.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))

            Text("Default persona", style = MaterialTheme.typography.labelMedium)
            Text(
                if (folder?.defaultPersonaId == null) "Inherited from workspace" else "Set by folder",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowChips(
                options = listOf("None" to null) + personas.map { it.name to it.id },
                selected = folder?.defaultPersonaId,
                onSelect = { vm.setDefaultPersona(it) }
            )

            Text("Default model", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowChips(
                options = listOf("None" to null) + models.filter { it.role == ModelRole.GENERATION }.map { it.displayName to it.id },
                selected = folder?.defaultModelId,
                onSelect = { vm.setDefaultModel(it) }
            )

            Text("Default sources", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            MultiSelectChips(
                options = knowledgeBases.map { it.name to it.id },
                selected = folder?.kbIdList() ?: emptyList(),
                onToggle = { ids -> vm.setDefaultKbs(ids) }
            )

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { onOpenChat(vm.createChat()) } }) { Text("New chat here") }
                OutlinedButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) { Text("New note here") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Chats (${chats.size})", style = MaterialTheme.typography.titleSmall)
            chats.forEach { chat ->
                Card(onClick = { onOpenChat(chat.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(chat.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text("Notes (${notes.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            notes.forEach { note ->
                Card(onClick = { onOpenNote(note.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(note.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }

    folder?.let { current ->
        if (renaming) {
            var name by remember(current.id) { mutableStateOf(current.name) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text("Rename folder") },
                text = { BoundedTextField(value = name, onValueChange = { name = it }, singleLine = true, maxLength = ValidationLimits.FOLDER_NAME) },
                confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(name.trim()); renaming = false } }, enabled = name.isNotBlank()) { Text("Save") } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<Pair<String, String?>>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, id) ->
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label, maxLines = 1) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectChips(options: List<Pair<String, String>>, selected: List<String>, onToggle: (List<String>) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, id) ->
            val isSelected = id in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(if (isSelected) selected - id else selected + id) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}
