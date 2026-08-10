package com.vervan.chat.ui.projects

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.ValidationLimits
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Note
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

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
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
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
                title = { OverflowTooltipText(project?.name ?: stringResource(R.string.project_list_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.project_actions)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { showMenu = false; editingName = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { showMenu = false; pendingDelete = true })
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            when {
                error != null -> OperationErrorCard(
                    title = stringResource(R.string.project_unavailable),
                    message = error ?: stringResource(R.string.project_unavailable_message),
                    recovery = stringResource(R.string.project_unavailable_recovery),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry
                )
                isLoading -> LoadingSkeletonList(rows = 7)
                project == null -> EmptyState(
                    icon = Icons.Filled.Workspaces,
                    title = stringResource(R.string.project_not_found),
                    body = stringResource(R.string.project_not_found_body),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack
                )
                else -> {
            Text(stringResource(R.string.project_instructions), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.project_instructions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoundedTextField(
                value = instructions,
                onValueChange = { instructions = it; vm.saveInstructions(it) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.lg),
                placeholder = stringResource(R.string.project_instructions_placeholder),
                maxLength = ValidationLimits.PROJECT_INSTRUCTIONS
            )

            ResponsiveActions {
                OutlinedButton(onClick = { scope.launch { onOpenChat(vm.createChat()) } }) { Text(stringResource(R.string.project_new_chat)) }
                OutlinedButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) { Text(stringResource(R.string.project_new_note)) }
            }

            VervanSectionHeader(stringResource(R.string.folder_chats), count = chats.size)
            if (chats.isEmpty()) {
                Text(
                    stringResource(R.string.project_chats_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SectionCard(
                    items = chats.map { chat ->
                        {
                            SectionRow(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                title = chat.title,
                                subtitle = relativeTime(chat.updatedAt),
                                onClick = { onOpenChat(chat.id) }
                            )
                        }
                    }
                )
            }

            VervanSectionHeader(stringResource(R.string.folder_notes), count = notes.size)
            if (notes.isEmpty()) {
                Text(
                    stringResource(R.string.project_notes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SectionCard(
                    items = notes.map { note ->
                        {
                            SectionRow(
                                icon = Icons.AutoMirrored.Filled.Note,
                                title = note.title,
                                subtitle = relativeTime(note.updatedAt),
                                onClick = { onOpenNote(note.id) }
                            )
                        }
                    }
                )
            }
                }
            }
        }
    }

    if (editingName && project != null) {
        var name by remember(project!!.id) { mutableStateOf(project!!.name) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.action_rename) + " " + stringResource(R.string.project_list_title).lowercase().dropLast(1)) },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.workspace_name), singleLine = true, maxLength = ValidationLimits.PROJECT_NAME) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(name.trim()); editingName = false } }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (pendingDelete && project != null) {
        ConfirmDialog(
            title = stringResource(R.string.project_delete_one_title, project!!.name),
            body = stringResource(R.string.project_delete_one_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.delete(); pendingDelete = false; onBack() },
            onDismiss = { pendingDelete = false }
        )
    }
}
