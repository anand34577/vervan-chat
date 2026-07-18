package com.vervan.chat.ui.folders

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersListScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: FoldersViewModel = viewModel(factory = viewModelFactory { initializer { FoldersViewModel(app) } })
    val folders by vm.folders.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == folders.size && folders.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == folders.size && folders.isNotEmpty()) emptySet() else folders.map { it.id }.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { confirmBulkDelete = true },
                    deleteContentDescription = "Delete selected folders"
                )
            } else {
                TopAppBar(
                    title = { Text("Folders") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "New folder") }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Folder,
                title = "No folders yet",
                body = "Group chats and notes with shared AI defaults.",
                modifier = Modifier
            )
          } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = folder.id in selected
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .selectableItem(
                                selectionMode = selectionMode,
                                onClick = { onOpenFolder(folder.id) },
                                onToggleSelected = { selected = if (isSelected) selected - folder.id else selected + folder.id },
                                onEnterSelection = { selectionMode = true; selected = selected + folder.id }
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (selectionMode) {
                                Checkbox(checked = isSelected, onCheckedChange = { selected = if (isSelected) selected - folder.id else selected + folder.id })
                            }
                            Column(Modifier.weight(1f)) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                val defaults = buildList {
                                    if (folder.defaultPersonaId != null) add("persona")
                                    if (folder.defaultModelId != null) add("model")
                                    if (folder.kbIdList().isNotEmpty()) add("${folder.kbIdList().size} source${if (folder.kbIdList().size > 1) "s" else ""}")
                                }
                                if (defaults.isNotEmpty()) {
                                    Text(defaults.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
          }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New folder") },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, placeholder = "Name", singleLine = true, maxLength = ValidationLimits.FOLDER_NAME) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.create(name.trim()); showCreate = false } }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (confirmBulkDelete) {
        val count = selected.size
        ConfirmDialog(
            title = "Delete selected folders?",
            body = "Delete $count folder${if (count == 1) "" else "s"}? Their items will become unfiled.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmBulkDelete = false
                vm.deleteAll(selected)
                selected = emptySet()
                selectionMode = false
                scope.launch { snackbarHostState.showSnackbar("Deleted $count folder${if (count == 1) "" else "s"}") }
            },
            onDismiss = { confirmBulkDelete = false }
        )
    }
}
