package com.vervan.chat.ui.workspaces

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ArchiveMenuItem
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ContextGuideCard
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.chat.chatPreviewText
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
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
    var showExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirmation by remember { mutableStateOf("") }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            exportPassword = ""
            exportPasswordConfirmation = ""
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            exportResult = try {
                val output = requireNotNull(app.contentResolver.openOutputStream(uri)) {
                    "The selected location could not be opened."
                }
                output.use {
                    com.vervan.chat.data.backup.BackupManager.exportWorkspaceEncrypted(app.container.db, workspaceId, it, exportPassword)
                }
                "Workspace exported."
            } catch (e: Exception) {
                "Export failed. ${e.toUserMessage()}"
            }
            exportPassword = ""
            exportPasswordConfirmation = ""
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

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = {
                showExportPassword = false
                exportPassword = ""
                exportPasswordConfirmation = ""
            },
                title = { Text(stringResource(R.string.workspace_protect_export)) },
            text = {
                Column {
                    Text(
                        "Use at least 8 characters. You will need this password to restore the export; Vervan cannot recover it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it.take(128) },
                label = { Text(stringResource(R.string.workspace_export_password)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        isError = exportPassword.isNotEmpty() && exportPassword.length < 8,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                    )
                    OutlinedTextField(
                        value = exportPasswordConfirmation,
                        onValueChange = { exportPasswordConfirmation = it.take(128) },
                label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        isError = exportPasswordConfirmation.isNotEmpty() && exportPasswordConfirmation != exportPassword,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                    )
                }
            },
            confirmButton = {
                com.vervan.chat.ui.common.VervanButton(
                    onClick = { showExportPassword = false; exportLauncher.launch(exportFileName) },
                    enabled = exportPassword.length >= 8 && exportPassword == exportPasswordConfirmation
            ) { Text(stringResource(R.string.backup_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportPassword = false
                    exportPassword = ""
                    exportPasswordConfirmation = ""
            }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val ws = workspace
    val isActive = ws?.id == activeWorkspaceId
    val personaNamesById = remember(personas) { personas.associate { it.id to it.name } }
    val folderNamesById = remember(folders) { folders.associate { it.id to it.name } }

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
                title = { OverflowTooltipText(ws?.name ?: "Workspace") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workspace_menu)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!isActive) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.workspace_set_active)) }, onClick = {
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
                    DropdownMenuItem(text = { Text(stringResource(R.string.workspace_export_encrypted)) }, onClick = { showMenu = false; showExportPassword = true })
                        }
                        if (ws?.isDefault == false) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.workspace_edit)) }, onClick = { showMenu = false; editing = true })
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
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            if (ws.description.isNotBlank()) {
                Text(ws.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Space.xs))
            }
            Text(
                buildString {
                    append(personaNamesById[ws.personaId] ?: "No persona")
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
            ContextGuideCard(
                icon = Icons.AutoMirrored.Filled.Chat,
            title = stringResource(R.string.workspace_one_space),
                body = stringResource(R.string.ui_workspacedetailscreen_283_new_chats_inherit_this_space_s_persona_respo),
                modifier = Modifier.padding(top = Space.md),
                accentIndex = 2,
            )

            // Keep the four primary counts in one equal-width row. The old horizontally scrolling
            // cards had different intrinsic widths, so labels and values appeared to drift as the
            // user swiped the row. Equal cells make the summary scan as one coherent status bar.
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                StatCard("Chats", activeChatCount.toString(), Modifier.weight(1f))
                StatCard("Projects", projects.size.toString(), Modifier.weight(1f))
                StatCard("Folders", folderCount.toString(), Modifier.weight(1f))
                StatCard("Documents", documentCount.toString(), Modifier.weight(1f))
            }

            val newChat: () -> Unit = { scope.launch { onOpenChat(vm.createChat()) } }
            if (ws.archived) {
                Text(
                    "This workspace is archived — restore it from the menu to start new chats.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
                )
            } else {
            OutlinedButton(onClick = newChat) { Text(stringResource(R.string.workspace_new_chat)) }
            }

            // Chat Screen — workspace-scoped auto title generation toggle.
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workspace_auto_titles), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Applies to every chat in this workspace; manually renamed titles are never overwritten.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                com.vervan.chat.ui.common.VervanToggle(checked = ws.autoTitleGeneration, onCheckedChange = { vm.setAutoTitleGeneration(it) })
            }

            // per-workspace lock (e.g. a "Personal" workspace kept separate from
            // "Work"). Only offered once app-lock credentials actually exist somewhere —
            // otherwise switching it on would require an unlock nothing can ever satisfy.
            val lockCredentialsExist = app.container.appLockManager.hasPin() ||
                androidx.biometric.BiometricManager.from(LocalContext.current)
                    .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workspace_lock), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (lockCredentialsExist) "Requires biometrics or your PIN to switch into this workspace."
                        else "Set up app lock in Settings → Privacy & security first.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                com.vervan.chat.ui.common.VervanToggle(
                    checked = ws.lockEnabled,
                    enabled = lockCredentialsExist || ws.lockEnabled,
                    onCheckedChange = { vm.setLockEnabled(it) }
                )
            }

            // per-workspace defaults for chats created inside it (WorkspaceManager.applyDefaults).
                Text(stringResource(R.string.workspace_default_new_chats), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.xs))
            Row(Modifier.padding(top = Space.xs).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                com.vervan.chat.llm.ModelProfileType.entries.forEach { p ->
                    androidx.compose.material3.FilterChip(
                        selected = ws.defaultProfile == p.id,
                        onClick = { vm.setDefaultProfile(if (ws.defaultProfile == p.id) null else p.id) },
                        shape = MaterialTheme.shapes.small,
                        label = { Text(p.label) }
                    )
                }
            }
            var showDefaultKbPicker by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showDefaultKbPicker = true },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            ) {
                Text(
                    if (ws.defaultKbIdList().isEmpty()) "Default knowledge bases: none"
                    else "Default knowledge bases: ${ws.defaultKbIdList().size} selected",
                    maxLines = 2,
                )
            }
            if (showDefaultKbPicker) {
                WorkspaceKbPickerDialog(
                    initiallySelected = ws.defaultKbIdList().toSet(),
                    onDismiss = { showDefaultKbPicker = false },
                    onConfirm = { ids -> vm.setDefaultKnowledgeBaseIds(ids); showDefaultKbPicker = false }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = Space.md))

                Text(stringResource(R.string.workspace_folders, folders.size), style = MaterialTheme.typography.titleSmall)
            if (folders.isNotEmpty()) {
                SectionCard(
                    modifier = Modifier.padding(top = Space.xs),
                    items = folders.map { folder ->
                        {
                            SectionRow(title = folder.name, onClick = { onOpenFolder(folder.id) })
                        }
                    }
                )
            }

            Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                Text(
                    if (selectionMode) "${selectedChatIds.size} selected" else "Recent chats (${chats.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                if (selectionMode) {
                    ResponsiveActions(Modifier.padding(top = Space.xs)) {
                        TextButton(
                            onClick = { showBatchTitleOptions = true },
                            enabled = selectedChatIds.isNotEmpty()
            ) { Text(stringResource(R.string.workspace_generate_titles)) }
            TextButton(onClick = { selectionMode = false; selectedChatIds = emptySet() }) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            if (chats.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.md).clickable(enabled = !ws.archived, role = Role.Button, onClick = newChat),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAffordance(icon = Icons.AutoMirrored.Filled.Chat, size = IconAffordanceSize.Default)
                    Column(Modifier.padding(start = Space.md)) {
                Text(stringResource(R.string.workspace_empty_chats), style = MaterialTheme.typography.titleSmall)
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
                        folderName = chat.folderId?.let(folderNamesById::get),
                        personaName = chat.personaId?.let(personaNamesById::get) ?: personaNamesById[ws.personaId],
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
            title = stringResource(R.string.workspace_delete_title),
            body = stringResource(R.string.ui_workspacedetailscreen_delete_workspace_body, ws.name),
            confirmLabel = stringResource(R.string.action_delete_forever),
            destructive = true,
            onConfirm = { scope.launch { vm.delete(); pendingDelete = false; onBack() } },
            onDismiss = { pendingDelete = false }
        )
    }

    // Chat Screen — batch AI title generation options + progress.
    if (showBatchTitleOptions) {
        AlertDialog(
            onDismissRequest = { showBatchTitleOptions = false },
            title = { Text(stringResource(R.string.workspace_generate_selected_title, selectedChatIds.size)) },
            text = { Text(stringResource(R.string.workspace_generate_selected_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.startTitleBatch(selectedChatIds.toList(), onlyUntitled = true)
                    batchPaused = false
                    showBatchTitleOptions = false
                    selectionMode = false
                    selectedChatIds = emptySet()
            }) { Text(stringResource(R.string.workspace_generate)) }
            },
            dismissButton = { TextButton(onClick = { showBatchTitleOptions = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    batchProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { if (progress.done) vm.dismissBatchProgress() },
            title = { Text(stringResource(if (progress.done) R.string.workspace_titles_generated else R.string.workspace_generating_titles)) },
            text = {
                Column {
                    Text(stringResource(R.string.workspace_progress, progress.completed, progress.total))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm)
                    )
                    if (!progress.done) progress.currentChatTitle?.let { Text(stringResource(R.string.workspace_current, it), style = MaterialTheme.typography.bodySmall) }
                    if (progress.failed > 0) Text(stringResource(R.string.workspace_failed_count, progress.failed), style = MaterialTheme.typography.labelSmall)
                    if (progress.skipped > 0) Text(stringResource(R.string.workspace_skipped_count, progress.skipped), style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                if (progress.done) {
                    TextButton(onClick = { vm.dismissBatchProgress() }) { Text(stringResource(R.string.action_done)) }
                } else if (batchPaused) {
                    TextButton(onClick = { vm.resumeTitleBatch(); batchPaused = false }) { Text(stringResource(R.string.workspace_resume)) }
                } else {
                    TextButton(onClick = { vm.pauseTitleBatch(); batchPaused = true }) { Text(stringResource(R.string.workspace_pause)) }
                }
            },
            dismissButton = {
                if (!progress.done) {
                    TextButton(onClick = { vm.cancelTitleBatch(); batchPaused = false }) { Text(stringResource(R.string.action_cancel)) }
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
        preview = app.container.db.messageDao().getLatestForChat(chat.id)?.let { latest ->
            chatPreviewText(
                latest.content,
                latest.role == com.vervan.chat.data.db.entities.MessageRole.USER
            ).takeIf { it.isNotBlank() }
        }
        messageCount = app.container.db.messageDao().countForChat(chat.id)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            SurfaceRole.Raised.cardColors()
        },
        border = SurfaceRole.Raised.border(),
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = Space.xs))
            }
            IconAffordance(
                icon = Icons.AutoMirrored.Filled.Chat,
                size = IconAffordanceSize.Default,
                modifier = Modifier.padding(end = Space.md),
            )
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverflowTooltipText(
                        text = chat.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        relativeTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
                // Always reserve one preview line. This keeps every recent-chat card aligned even
                // when a message is reasoning-only, empty after sanitization, or simply shorter
                // than its neighbours.
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(top = Space.xs)
                ) {
                    preview?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    listOfNotNull(folderName ?: "No folder", personaName, "$messageCount messages").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Column(
            Modifier.padding(horizontal = Space.xs, vertical = Space.md).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        title = { Text(stringResource(R.string.workspace_edit)) },
        text = {
            Column {
                BoundedTextField(
                    value = nameField, onValueChange = { nameField = it }, placeholder = stringResource(R.string.workspace_name),
                    singleLine = true, maxLength = ValidationLimits.WORKSPACE_NAME,
                    modifier = Modifier.fillMaxWidth()
                )
                BoundedTextField(
                    value = descriptionField, onValueChange = { descriptionField = it }, placeholder = stringResource(R.string.workspace_description),
                    maxLength = ValidationLimits.WORKSPACE_DESCRIPTION,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = Space.sm)) {
                    OutlinedTextField(
                        value = selectedPersona?.name ?: stringResource(R.string.workspace_select_persona),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workspace_persona)) },
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
        title = { Text(stringResource(R.string.workspace_default_knowledge)) },
        text = {
            Column {
                if (kbs.isEmpty()) {
                    Text(stringResource(R.string.workspace_no_knowledge), style = MaterialTheme.typography.bodySmall)
                }
                kbs.forEach { kb ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = selected.contains(kb.id),
                            onCheckedChange = { checked -> selected = if (checked) selected + kb.id else selected - kb.id }
                        )
                        OverflowTooltipText(
                            text = kb.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
