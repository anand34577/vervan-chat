package com.vervan.chat.ui.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ArchiveMenuItem
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(onBack: () -> Unit, onOpenWorkspace: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkspacesViewModel = viewModel(factory = viewModelFactory { initializer { WorkspacesViewModel(app) } })
    val workspaces by vm.workspaces.collectAsState()
    val archived by vm.archivedWorkspaces.collectAsState()
    val activeId by vm.activeWorkspaceId.collectAsState()
    val personas by vm.personas.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Workspace?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val confirmationMessage by vm.confirmationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(confirmationMessage) {
        confirmationMessage?.let { snackbarHostState.showSnackbar(it); vm.clearConfirmation() }
    }
    // The Default Workspace can never be deleted (WorkspaceManager.delete enforces this) — it's
    // excluded from selection entirely so select-all/bulk-delete can't try and fail on it.
    val selectableWorkspaces = remember(workspaces, archived) { (workspaces + archived).filterNot { it.isDefault } }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == selectableWorkspaces.size && selectableWorkspaces.isNotEmpty(),
                    onToggleSelectAll = {
                        selected = if (selected.size == selectableWorkspaces.size && selectableWorkspaces.isNotEmpty()) emptySet() else selectableWorkspaces.map { it.id }.toSet()
                    },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { pendingBulkDelete = true },
                    deleteContentDescription = "Delete selected workspaces forever"
                )
            } else {
                TopAppBar(
                    title = { Text("Workspaces") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "New workspace") }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          LazyColumn(Modifier.fillMaxWidth()) {
            item {
                FeatureHero(
                    icon = Icons.Filled.Dashboard,
                    eyebrow = "Separate contexts",
                    title = "Your workspaces",
                    body = "Separate personal, work, and research content and defaults.",
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            item { VervanSectionHeader("Active workspaces", count = workspaces.size, actionLabel = "New", onAction = { showCreate = true }) }
            items(workspaces, key = { it.id }) { workspace ->
                WorkspaceCard(
                    workspace = workspace,
                    isActive = workspace.id == activeId,
                    chatCount = vm.chatCount(workspace.id).collectAsState(initial = 0).value,
                    personaName = personas.find { it.id == workspace.personaId }?.name ?: "—",
                    onClick = { onOpenWorkspace(workspace.id) },
                    onSetActive = { vm.setActive(workspace) },
                    onArchive = { vm.archive(workspace) },
                    onDelete = { pendingDelete = workspace },
                    selectionMode = selectionMode,
                    selected = workspace.id in selected,
                    onToggleSelected = { selected = if (workspace.id in selected) selected - workspace.id else selected + workspace.id },
                    onEnterSelection = if (!workspace.isDefault) ({ selectionMode = true; selected = selected + workspace.id }) else null
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Archived (${archived.size})",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        TextButton(onClick = { showArchived = !showArchived }) {
                            Text(if (showArchived) "Hide" else "Show")
                        }
                    }
                }
                if (showArchived) {
                    items(archived, key = { it.id }) { workspace ->
                        WorkspaceCard(
                            workspace = workspace,
                            isActive = false,
                            chatCount = vm.chatCount(workspace.id).collectAsState(initial = 0).value,
                            personaName = personas.find { it.id == workspace.personaId }?.name ?: "—",
                            onClick = { onOpenWorkspace(workspace.id) },
                            onRestore = { vm.restore(workspace) },
                            onDelete = { pendingDelete = workspace },
                            selectionMode = selectionMode,
                            selected = workspace.id in selected,
                            onToggleSelected = { selected = if (workspace.id in selected) selected - workspace.id else selected + workspace.id },
                            onEnterSelection = if (!workspace.isDefault) ({ selectionMode = true; selected = selected + workspace.id }) else null
                        )
                    }
                }
            }
          }
        }
    }

    if (showCreate) {
        CreateWorkspaceDialog(
            personas = personas,
            onDismiss = { showCreate = false },
            onCreate = { name, description, personaId ->
                vm.create(name, description, personaId)
                showCreate = false
            }
        )
    }

    pendingDelete?.let { workspace ->
        ConfirmDialog(
            title = "Delete workspace forever?",
            body = "Permanently delete \"${workspace.name}\" and all its content?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = { vm.delete(workspace); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    if (pendingBulkDelete) {
        val toDelete = selectableWorkspaces.filter { it.id in selected }
        ConfirmDialog(
            title = "Delete selected workspaces forever?",
            body = "Permanently delete ${toDelete.size} workspace${if (toDelete.size == 1) "" else "s"} and all their content?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                vm.deleteAll(toDelete)
                pendingBulkDelete = false
                selected = emptySet()
                selectionMode = false
            },
            onDismiss = { pendingBulkDelete = false }
        )
    }
}

@Composable
private fun WorkspaceCard(
    workspace: Workspace,
    isActive: Boolean,
    chatCount: Int,
    personaName: String,
    onClick: () -> Unit,
    onSetActive: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onEnterSelection: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    // A protected workspace (currently just the Default Workspace, onEnterSelection == null)
    // never enters selection behavior at all — even while the list is in selection mode, tapping
    // or long-pressing this row always just opens it, since it can neither be selected nor deleted.
    val participatesInSelection = selectionMode && onEnterSelection != null
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .selectableItem(
                selectionMode = participatesInSelection,
                onClick = onClick,
                onToggleSelected = onToggleSelected,
                onEnterSelection = onEnterSelection ?: {}
            ),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (participatesInSelection) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            }
            IconAffordance(
                icon = Icons.Filled.Dashboard,
                size = IconAffordanceSize.Default,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        workspace.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Icon(
                            Icons.Filled.CheckCircle, contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    if (workspace.archived) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archived", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                // Compact card fields only (Android phone space rule §9/§18): persona, chat
                // count, last activity — advanced stats live on the detail screen.
                Text(
                    "$personaName · $chatCount chat${if (chatCount == 1) "" else "s"} · ${relativeTime(workspace.lastActiveAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workspace actions") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (onSetActive != null && !isActive) {
                        DropdownMenuItem(text = { Text("Set active") }, onClick = { showMenu = false; onSetActive() })
                    }
                    if (onArchive != null && !workspace.isDefault) {
                        ArchiveMenuItem(archived = false, onClick = { showMenu = false; onArchive() })
                    }
                    if (onRestore != null) {
                        ArchiveMenuItem(archived = true, onClick = { showMenu = false; onRestore() })
                    }
                    if (!workspace.isDefault) {
                        DeleteMenuItem(permanent = true, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWorkspaceDialog(
    personas: List<Persona>,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, personaId: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedPersona by remember { mutableStateOf(personas.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New workspace") },
        text = {
            Column {
                BoundedTextField(
                    value = name, onValueChange = { name = it }, placeholder = "Name",
                    singleLine = true, maxLength = ValidationLimits.WORKSPACE_NAME,
                    modifier = Modifier.fillMaxWidth()
                )
                BoundedTextField(
                    value = description, onValueChange = { description = it }, placeholder = "Description",
                    maxLength = ValidationLimits.WORKSPACE_DESCRIPTION,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = selectedPersona?.name ?: "Select persona",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Persona") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        personas.forEach { persona ->
                            DropdownMenuItem(
                                text = { Text(persona.name) },
                                onClick = { selectedPersona = persona; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedPersona?.let { onCreate(name.trim(), description.trim(), it.id) } },
                enabled = name.isNotBlank() && name.length <= ValidationLimits.WORKSPACE_NAME &&
                    description.length <= ValidationLimits.WORKSPACE_DESCRIPTION && selectedPersona != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun relativeTime(epochMs: Long): String {
    val diffMin = (System.currentTimeMillis() - epochMs) / 60000
    return when {
        diffMin < 1 -> "now"
        diffMin < 60 -> "${diffMin}m"
        diffMin < 60 * 24 -> "${diffMin / 60}h"
        else -> "${diffMin / (60 * 24)}d"
    }
}
