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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vervan.chat.ui.common.VervanFloatingActionButton as FloatingActionButton
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ModernistListRow
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(onOpenProject: (String) -> Unit, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: ProjectsListViewModel = viewModel(factory = viewModelFactory { initializer { ProjectsListViewModel(app) } })
    val projects by vm.projects.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var pendingDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.project_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.project_new)) }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          if (error != null) {
            OperationErrorCard(
                title = stringResource(R.string.projects_unavailable),
                message = error ?: stringResource(R.string.projects_unavailable_message),
                recovery = stringResource(R.string.projects_unavailable_recovery),
                modifier = Modifier.padding(top = Space.md),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retry
            )
          } else if (isLoading) {
            LoadingSkeletonList(rows = 5, modifier = Modifier.padding(top = Space.md))
          } else if (projects.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Workspaces,
                title = stringResource(R.string.project_no_items),
                body = stringResource(R.string.project_no_items_body),
                modifier = Modifier.fillMaxSize(),
                centered = true,
                actionLabel = stringResource(R.string.project_new),
                onAction = { showCreate = true }
            )
          } else {
            Column(Modifier.fillMaxSize()) {
              FeatureHero(
                icon = Icons.Filled.Workspaces,
                eyebrow = stringResource(R.string.project_hero_eyebrow),
                title = stringResource(R.string.project_list_title),
                body = stringResource(R.string.project_hero_body),
                modifier = Modifier.padding(top = Space.sm)
              )
              VervanSectionHeader(stringResource(R.string.project_all), count = projects.size, actionLabel = stringResource(R.string.action_new), onAction = { showCreate = true })
              LazyColumn(
                  Modifier.fillMaxWidth().weight(1f),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md),
                  verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
              ) {
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
            title = { Text(stringResource(R.string.project_new)) },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.workspace_name), required = true, singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) { vm.createProject(name.trim()); showCreate = false }
                else android.widget.Toast.makeText(context, "Project name is required.", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    editing?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.action_rename) + " " + stringResource(R.string.project_list_title).lowercase().dropLast(1)) },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.workspace_name), required = true, singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) { vm.rename(project, name.trim()); editing = null }
                else android.widget.Toast.makeText(context, "Project name is required.", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pendingDelete?.let { project ->
        ConfirmDialog(
            title = stringResource(R.string.project_delete_title),
            body = stringResource(R.string.project_delete_body, project.name),
            confirmLabel = stringResource(R.string.project_delete_action),
            destructive = true,
            onConfirm = { vm.delete(project); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    ModernistListRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        onClick = onClick,
    ) {
        IconAffordance(icon = Icons.Filled.Workspaces, size = IconAffordanceSize.Default)
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            OverflowTooltipText(
                text = project.name,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                project.instructions.takeIf { it.isNotBlank() } ?: stringResource(R.string.project_open_workspace),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.project_actions)) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { showMenu = false; onRename() })
                DeleteMenuItem(onClick = { showMenu = false; onDelete() })
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
