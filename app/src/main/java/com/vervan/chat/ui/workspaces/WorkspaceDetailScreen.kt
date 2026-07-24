package com.vervan.chat.ui.workspaces

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ArchiveMenuItem
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailScreen(
    workspaceId: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkspaceDetailViewModel = viewModel(factory = viewModelFactory { initializer { WorkspaceDetailViewModel(app, workspaceId) } })
    val workspace by vm.workspace.collectAsState()
    val personas by vm.personas.collectAsState()
    val activeWorkspaceId by vm.activeWorkspaceId.collectAsState()
    val chats by vm.chats.collectAsState()
    val folders by vm.folders.collectAsState()
    val projects by vm.projects.collectAsState()
    val activeChatCount by vm.activeChatCount.collectAsState()
    val folderCount by vm.folderCount.collectAsState()
    val documentCount by vm.documentCount.collectAsState()
    val scope = rememberCoroutineScope()

    // same CreateDocument/export pattern as BackupScreen.kt, scoped to this
    // workspace's own chats/messages/folders (see BackupManager.exportWorkspace).
    val exportFileName = remember(workspaceId) {
        "vervan-workspace-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json"
    }
    var exportResult by remember { mutableStateOf<String?>(null) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportResult = try {
                app.contentResolver.openOutputStream(uri)?.use { com.vervan.chat.data.backup.BackupManager.exportWorkspace(app.container.db, workspaceId, it) }
                "Workspace exported."
            } catch (e: Exception) {
                "Export failed. ${e.toUserMessage()}"
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf(setOf<String>()) }
    var showBatchTitleOptions by remember { mutableStateOf(false) }
    var batchPaused by remember { mutableStateOf(false) }
    val batchProgress by vm.batchProgress.collectAsState()
    val confirmationMessage by vm.confirmationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportResult) {
        exportResult?.let { snackbarHostState.showSnackbar(it); exportResult = null }
    }
    LaunchedEffect(confirmationMessage) {
        confirmationMessage?.let { snackbarHostState.showSnackbar(it); vm.clearConfirmation() }
    }

    val ws = workspace
    val isActive = ws?.id == activeWorkspaceId

    // a locked workspace requires a fresh unlock before switching into it, regardless
    // of whether the app-wide lock is currently satisfied (this reuses AppLockManager/LockScreen
    // rather than a parallel auth UI — successfully authenticating here also happens to satisfy
    // the app-wide lock if that was separately active, which is fine: either way the user just
    // proved presence).
    var pendingUnlockForActivate by remember { mutableStateOf(false) }
    val isAppLocked by app.container.appLockManager.isLocked.collectAsState()
    LaunchedEffect(isAppLocked) {
        if (pendingUnlockForActivate && !isAppLocked) {
            pendingUnlockForActivate = false
            vm.setActive()
        }
    }
    val appLockMethodName by app.container.settingsRepository.appLockMethod.collectAsState(initial = "BIOMETRIC")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ws?.name ?: "Workspace", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workspace menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!isActive) {
                            DropdownMenuItem(text = { Text("Set active") }, onClick = {
                                showMenu = false
                                if (ws?.lockEnabled == true) {
                                    app.container.appLockManager.lockNow()
                                    pendingUnlockForActivate = true
                                } else {
                                    vm.setActive()
                                }
                            })
                        }
                        if (ws != null) {
                            DropdownMenuItem(text = { Text("Export workspace") }, onClick = { showMenu = false; exportLauncher.launch(exportFileName) })
                        }
                        if (ws?.isDefault == false) {
                            DropdownMenuItem(text = { Text("Edit workspace") }, onClick = { showMenu = false; editing = true })
                            ArchiveMenuItem(archived = ws.archived, onClick = {
                                showMenu = false
                                if (ws.archived) vm.restore() else vm.archive()
                            })
                            DeleteMenuItem(permanent = true, onClick = { showMenu = false; pendingDelete = true })
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (ws == null) return@Scaffold
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            if (ws.description.isNotBlank()) {
                Text(ws.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                buildString {
                    append(personas.find { it.id == ws.personaId }?.name ?: "No persona")
                    if (isActive) append(" · Active")
                    if (ws.archived) append(" · Archived")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Workspace → projects and folders → chats, notes, and knowledge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )

            // status summary — horizontally scrollable stat cards (phone space rule:
            // "show four or fewer primary statistics at once").
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Chats", activeChatCount.toString())
                StatCard("Projects", projects.size.toString())
                StatCard("Folders", folderCount.toString())
                StatCard("Documents", documentCount.toString())
            }

            val newChat: () -> Unit = { scope.launch { onOpenChat(vm.createChat()) } }
            if (ws.archived) {
                Text(
                    "This workspace is archived — restore it from the menu to start new chats.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
                )
            } else {
                OutlinedButton(onClick = newChat) { Text("New chat") }
            }

            // Chat Screen — workspace-scoped auto title generation toggle.
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Automatically generate chat titles", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Applies to every chat in this workspace; manually renamed titles are never overwritten.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(checked = ws.autoTitleGeneration, onCheckedChange = { vm.setAutoTitleGeneration(it) })
            }

            // per-workspace lock (e.g. a "Personal" workspace kept separate from
            // "Work"). Only offered once app-lock credentials actually exist somewhere —
            // otherwise switching it on would require an unlock nothing can ever satisfy.
            val lockCredentialsExist = app.container.appLockManager.hasPin() ||
                androidx.biometric.BiometricManager.from(LocalContext.current)
                    .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lock this workspace", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (lockCredentialsExist) "Requires biometrics or your PIN to switch into this workspace."
                        else "Set up app lock in Settings → Security first.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = ws.lockEnabled,
                    enabled = lockCredentialsExist || ws.lockEnabled,
                    onCheckedChange = { vm.setLockEnabled(it) }
                )
            }

            // per-workspace defaults for chats created inside it (WorkspaceManager.applyDefaults).
            Text("Default for new chats in this workspace", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.vervan.chat.llm.ModelProfileType.entries.forEach { p ->
                    androidx.compose.material3.FilterChip(
                        selected = ws.defaultProfile == p.id,
                        onClick = { vm.setDefaultProfile(if (ws.defaultProfile == p.id) null else p.id) },
                        label = { Text(p.label) }
                    )
                }
            }
            var showDefaultKbPicker by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showDefaultKbPicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    if (ws.defaultKbIdList().isEmpty()) "Default knowledge bases: none"
                    else "Default knowledge bases: ${ws.defaultKbIdList().size} selected"
                )
            }
            if (showDefaultKbPicker) {
                WorkspaceKbPickerDialog(
                    initiallySelected = ws.defaultKbIdList().toSet(),
                    onDismiss = { showDefaultKbPicker = false },
                    onConfirm = { ids -> vm.setDefaultKnowledgeBaseIds(ids); showDefaultKbPicker = false }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Folders (${folders.size})", style = MaterialTheme.typography.titleSmall)
            folders.forEach { folder ->
                Card(onClick = { onOpenFolder(folder.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectionMode) "${selectedChatIds.size} selected" else "Recent chats (${chats.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                if (selectionMode) {
                    Row {
                        TextButton(
                            onClick = { showBatchTitleOptions = true },
                            enabled = selectedChatIds.isNotEmpty()
                        ) { Text("Generate titles") }
                        TextButton(onClick = { selectionMode = false; selectedChatIds = emptySet() }) { Text("Cancel") }
                    }
                }
            }
            if (chats.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.md).clickable(enabled = !ws.archived, onClick = newChat),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAffordance(icon = Icons.AutoMirrored.Filled.Chat, size = IconAffordanceSize.Default)
                    Column(Modifier.padding(start = Space.md)) {
                        Text("No chats yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tap to start the first chat in this workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                chats.take(20).forEach { chat ->
                    WorkspaceChatCard(
                        chat = chat,
                        folderName = folders.find { it.id == chat.folderId }?.name,
                        personaName = personas.find { it.id == chat.personaId }?.name ?: personas.find { it.id == ws.personaId }?.name,
                        selectionMode = selectionMode,
                        selected = chat.id in selectedChatIds,
                        onClick = {
                            if (selectionMode) {
                                selectedChatIds = if (chat.id in selectedChatIds) selectedChatIds - chat.id else selectedChatIds + chat.id
                            } else {
                                onOpenChat(chat.id)
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedChatIds = selectedChatIds + chat.id
                        }
                    )
                }
            }
        }
        }
    }

    if (editing && ws != null) {
        EditWorkspaceDialog(
            name = ws.name,
            description = ws.description,
            personas = personas,
            selectedPersonaId = ws.personaId,
            onDismiss = { editing = false },
            onSave = { name, description, personaId ->
                vm.rename(name, description)
                vm.setPersona(personaId)
                editing = false
            }
        )
    }

    if (pendingDelete && ws != null) {
        ConfirmDialog(
            title = "Delete workspace forever?",
            body = "Permanently delete \"${ws.name}\" and all its content?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = { scope.launch { vm.delete(); pendingDelete = false; onBack() } },
            onDismiss = { pendingDelete = false }
        )
    }

    // Chat Screen — batch AI title generation options + progress.
    if (showBatchTitleOptions) {
        AlertDialog(
            onDismissRequest = { showBatchTitleOptions = false },
            title = { Text("Generate titles for ${selectedChatIds.size} chats") },
            text = { Text("Custom titles stay unchanged. Only default or generated titles are replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.startTitleBatch(selectedChatIds.toList(), onlyUntitled = true)
                    batchPaused = false
                    showBatchTitleOptions = false
                    selectionMode = false
                    selectedChatIds = emptySet()
                }) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showBatchTitleOptions = false }) { Text("Cancel") } }
        )
    }

    batchProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { if (progress.done) vm.dismissBatchProgress() },
            title = { Text(if (progress.done) "Titles generated" else "Generating chat titles") },
            text = {
                Column {
                    Text("${progress.completed} of ${progress.total} completed")
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    if (!progress.done) progress.currentChatTitle?.let { Text("Current: $it", style = MaterialTheme.typography.bodySmall) }
                    if (progress.failed > 0) Text("${progress.failed} failed", style = MaterialTheme.typography.labelSmall)
                    if (progress.skipped > 0) Text("${progress.skipped} skipped (already titled)", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                if (progress.done) {
                    TextButton(onClick = { vm.dismissBatchProgress() }) { Text("Done") }
                } else if (batchPaused) {
                    TextButton(onClick = { vm.resumeTitleBatch(); batchPaused = false }) { Text("Resume") }
                } else {
                    TextButton(onClick = { vm.pauseTitleBatch(); batchPaused = true }) { Text("Pause") }
                }
            },
            dismissButton = {
                if (!progress.done) {
                    TextButton(onClick = { vm.cancelTitleBatch(); batchPaused = false }) { Text("Cancel") }
                }
            }
        )
    }

    // Rendered via Dialog (its own window) rather than a plain sibling composable, so it
    // overlays full-screen regardless of how the NavHost lays out this route's content.
    if (pendingUnlockForActivate) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { pendingUnlockForActivate = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.vervan.chat.ui.lock.LockScreen(
                activity = LocalContext.current as androidx.fragment.app.FragmentActivity,
                appLockManager = app.container.appLockManager,
                method = runCatching { com.vervan.chat.security.AppLockMethod.valueOf(appLockMethodName) }
                    .getOrDefault(com.vervan.chat.security.AppLockMethod.BIOMETRIC)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceChatCard(
    chat: com.vervan.chat.data.db.entities.Chat,
    folderName: String?,
    personaName: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    var preview by remember(chat.id) { mutableStateOf<String?>(null) }
    var messageCount by remember(chat.id) { mutableStateOf(0) }
    LaunchedEffect(chat.id, chat.updatedAt) {
        preview = app.container.db.messageDao().getLatestForChat(chat.id)?.content
        messageCount = app.container.db.messageDao().countForChat(chat.id)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 4.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        relativeTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)
                    )
                }
                preview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    listOfNotNull(folderName ?: "Unfoldered", personaName, "$messageCount messages").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
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

@Composable
private fun StatCard(label: String, value: String) {
    Card {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditWorkspaceDialog(
    name: String,
    description: String,
    personas: List<com.vervan.chat.data.db.entities.Persona>,
    selectedPersonaId: String,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, personaId: String) -> Unit
) {
    var nameField by remember { mutableStateOf(name) }
    var descriptionField by remember { mutableStateOf(description) }
    var selectedPersona by remember { mutableStateOf(personas.find { it.id == selectedPersonaId } ?: personas.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit workspace") },
        text = {
            Column {
                BoundedTextField(
                    value = nameField, onValueChange = { nameField = it }, placeholder = "Name",
                    singleLine = true, maxLength = ValidationLimits.WORKSPACE_NAME,
                    modifier = Modifier.fillMaxWidth()
                )
                BoundedTextField(
                    value = descriptionField, onValueChange = { descriptionField = it }, placeholder = "Description",
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
                            DropdownMenuItem(text = { Text(persona.name) }, onClick = { selectedPersona = persona; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedPersona?.let { onSave(nameField.trim(), descriptionField.trim(), it.id) } },
                enabled = nameField.isNotBlank() && nameField.length <= ValidationLimits.WORKSPACE_NAME &&
                    descriptionField.length <= ValidationLimits.WORKSPACE_DESCRIPTION && selectedPersona != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** simple multi-select KB checklist for a workspace's default-KB set. Deliberately
 * separate from ChatScreen's private SourcePickerDialog (that one also bundles a per-chat
 * sourceGrounded on/off switch this doesn't need). */
@Composable
private fun WorkspaceKbPickerDialog(
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val kbs by app.container.db.knowledgeBaseDao().observeAll().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(initiallySelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default knowledge bases") },
        text = {
            Column {
                if (kbs.isEmpty()) {
                    Text("No knowledge bases yet. Import a document in Knowledge.", style = MaterialTheme.typography.bodySmall)
                }
                kbs.forEach { kb ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = selected.contains(kb.id),
                            onCheckedChange = { checked -> selected = if (checked) selected + kb.id else selected - kb.id }
                        )
                        Text(kb.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
