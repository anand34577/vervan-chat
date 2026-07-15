package com.vervan.chat.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ValidationLimits
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ProjectDashboardViewModel = viewModel(factory = viewModelFactory { initializer { ProjectDashboardViewModel(app, projectId) } })
    val project by vm.project.collectAsState()
    val chats by vm.chats.collectAsState()
    val notes by vm.notes.collectAsState()
    val scope = rememberCoroutineScope()

    var instructions by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(project) {
        if (project != null && !loaded) {
            instructions = project!!.instructions
            loaded = true
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Project", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Project menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; editingName = true })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; pendingDelete = true })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Instructions", style = MaterialTheme.typography.titleSmall)
            Text(
                "Applied to every chat in this project, alongside its persona.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoundedTextField(
                value = instructions,
                onValueChange = { instructions = it; vm.saveInstructions(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                placeholder = "e.g. Always answer in formal English and cite sources when available.",
                maxLength = ValidationLimits.PROJECT_INSTRUCTIONS
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { onOpenChat(vm.createChat()) } }) { Text("New chat") }
                OutlinedButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) { Text("New note") }
            }

            Text("Chats", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            chats.forEach { chat ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { onOpenChat(chat.id) }) {
                    Text(chat.title, modifier = Modifier.padding(12.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            Text("Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            notes.forEach { note ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { onOpenNote(note.id) }) {
                    Text(note.title, modifier = Modifier.padding(12.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (editingName && project != null) {
        var name by remember(project!!.id) { mutableStateOf(project!!.name) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Rename project") },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, placeholder = "Name", singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(name.trim()); editingName = false } }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } }
        )
    }

    if (pendingDelete && project != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete \"${project!!.name}\"?") },
            text = { Text("Its chats and notes are kept but unlinked from the project. This can be recovered from the recycle bin.") },
            confirmButton = { TextButton(onClick = { vm.delete(); pendingDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("Cancel") } }
        )
    }
}
