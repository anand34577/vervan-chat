package com.vervan.chat.ui.projects

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ValidationLimits
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(onOpenProject: (String) -> Unit, onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ProjectsListViewModel = viewModel(factory = viewModelFactory { initializer { ProjectsListViewModel(app) } })
    val projects by vm.projects.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "New project") }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          if (projects.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Workspaces,
                title = "No projects yet",
                body = "Keep related chats, notes, and instructions together.",
                modifier = Modifier,
                actionLabel = "New project",
                onAction = { showCreate = true }
            )
          } else {
            Column(Modifier.fillMaxSize()) {
              FeatureHero(
                icon = Icons.Filled.Workspaces,
                eyebrow = "Focused work",
                title = "Projects",
                body = "Create a focused space for related work.",
                modifier = Modifier.padding(top = Space.sm)
              )
              VervanSectionHeader("All projects", count = projects.size, actionLabel = "New", onAction = { showCreate = true })
              LazyColumn(Modifier.fillMaxSize()) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onRename = { editing = project },
                        onDelete = { pendingDelete = project }
                    )
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
            title = { Text("New project") },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, placeholder = "Name", singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.createProject(name.trim()); showCreate = false } }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    editing?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename project") },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, placeholder = "Name", singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(project, name.trim()); editing = null } }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { project ->
        ConfirmDialog(
            title = "Move project to recycle bin?",
            body = "Move \"${project.name}\" to the bin? Its chats and notes are kept.",
            confirmLabel = "Move to recycle bin",
            destructive = true,
            onConfirm = { vm.delete(project); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = vervanBorder()
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconAffordance(icon = Icons.Filled.Workspaces, size = IconAffordanceSize.Default)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    project.instructions.takeIf { it.isNotBlank() } ?: "Open project workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Project actions") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DeleteMenuItem(onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}
