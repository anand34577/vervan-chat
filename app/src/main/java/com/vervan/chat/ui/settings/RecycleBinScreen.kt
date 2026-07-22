package com.vervan.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

private const val RETENTION_DAYS = 30

private data class BinItem(
    val key: String,
    val section: String,
    val icon: ImageVector,
    val title: String,
    val deletedAt: Long?,
    val restore: () -> Unit,
    val deleteForever: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: RecycleBinViewModel = viewModel(factory = viewModelFactory { initializer { RecycleBinViewModel(app) } })
    val state by vm.state.collectAsState()
    var confirmEmptyAll by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var pendingSingleDelete by remember { mutableStateOf<BinItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val binItems = buildList {
        state.chats.forEach { item -> add(BinItem("chat_${item.id}", "Chats", Icons.AutoMirrored.Filled.Chat, item.title, item.deletedAt, { vm.restoreChat(item) }, { vm.deleteChatForever(item) })) }
        state.notes.forEach { item -> add(BinItem("note_${item.id}", "Notes", Icons.Filled.Edit, item.title, item.deletedAt, { vm.restoreNote(item) }, { vm.deleteNoteForever(item) })) }
        state.documents.forEach { item -> add(BinItem("doc_${item.id}", "Documents", Icons.Filled.Description, item.displayName, item.deletedAt, { vm.restoreDocument(item) }, { vm.deleteDocumentForever(item) })) }
        state.folders.forEach { item -> add(BinItem("folder_${item.id}", "Folders", Icons.Filled.Folder, item.name, item.deletedAt, { vm.restoreFolder(item) }, { vm.deleteFolderForever(item) })) }
        state.personas.forEach { item -> add(BinItem("persona_${item.id}", "Personas", Icons.Outlined.Person, item.name, item.deletedAt, { vm.restorePersona(item) }, { vm.deletePersonaForever(item) })) }
        state.workflows.forEach { item -> add(BinItem("workflow_${item.id}", "Workflows", Icons.Filled.Widgets, item.name, item.deletedAt, { vm.restoreWorkflow(item) }, { vm.deleteWorkflowForever(item) })) }
        state.templates.forEach { item -> add(BinItem("template_${item.id}", "Prompt templates", Icons.Filled.Extension, "/${item.name}", item.deletedAt, { vm.restoreTemplate(item) }, { vm.deleteTemplateForever(item) })) }
        state.projects.forEach { item -> add(BinItem("project_${item.id}", "Projects", Icons.Filled.Workspaces, item.name, item.deletedAt, { vm.restoreProject(item) }, { vm.deleteProjectForever(item) })) }
        state.memories.forEach { item -> add(BinItem("memory_${item.id}", "Memories", Icons.Filled.Psychology, item.text.take(60), item.deletedAt, { vm.restoreMemory(item) }, { vm.deleteMemoryForever(item) })) }
        state.savedOutputs.forEach { item -> add(BinItem("output_${item.id}", "Saved outputs", Icons.Filled.Bookmark, item.content.take(60), item.deletedAt, { vm.restoreSavedOutput(item) }, { vm.deleteSavedOutputForever(item) })) }
    }
    val totalCount = binItems.size
    val selectedItems = binItems.filter { it.key in selectedKeys }

    LaunchedEffect(binItems.map { it.key }) {
        selectedKeys = selectedKeys.intersect(binItems.mapTo(mutableSetOf()) { it.key })
        if (selectedKeys.isEmpty()) selectionMode = false
    }

    fun leaveSelectionMode() {
        selectedKeys = emptySet()
        selectionMode = false
    }

    fun toggleSelection(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedKeys.size,
                    allSelected = selectedKeys.size == totalCount && totalCount > 0,
                    onToggleSelectAll = {
                        selectedKeys = if (selectedKeys.size == totalCount) emptySet() else binItems.mapTo(mutableSetOf()) { it.key }
                    },
                    onExit = ::leaveSelectionMode,
                    onDelete = { confirmDeleteSelection = true },
                    deleteEnabled = selectedItems.isNotEmpty(),
                    deleteContentDescription = "Delete selected forever",
                    extraActions = {
                        IconButton(
                            enabled = selectedItems.isNotEmpty(),
                            onClick = {
                                val count = selectedItems.size
                                selectedItems.forEach { it.restore() }
                                leaveSelectionMode()
                                scope.launch { snackbarHostState.showSnackbar("Restored $count item${if (count == 1) "" else "s"}") }
                            }
                        ) { Icon(Icons.Filled.Restore, contentDescription = "Restore selected") }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Recycle bin")
                            if (!state.isEmpty) Text(
                                "$totalCount item${if (totalCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.Delete,
                title = "Nothing here",
                body = "Deleted items stay here for $RETENTION_DAYS days.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        PageContainer(Modifier.padding(padding)) {
          LazyColumn(Modifier.fillMaxSize()) {
            item {
                BinSummary(
                    totalCount = totalCount,
                    onRestoreAll = {
                        vm.restoreAll()
                        scope.launch { snackbarHostState.showSnackbar("Restored all $totalCount items") }
                    },
                    onEmpty = { confirmEmptyAll = true }
                )
            }
            binItems.groupBy { it.section }.forEach { (section, sectionItems) ->
                item(key = "section_$section") { SectionLabel(section, count = sectionItems.size) }
                items(sectionItems, key = { it.key }) { item ->
                    BinRow(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.key in selectedKeys,
                        onToggleSelection = { toggleSelection(item.key) },
                        onEnterSelection = {
                            selectionMode = true
                            selectedKeys = selectedKeys + item.key
                        },
                        onRestore = {
                            item.restore()
                            scope.launch { snackbarHostState.showSnackbar("Restored ${item.title.ifBlank { "item" }}") }
                        },
                        onRequestDelete = { pendingSingleDelete = item }
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Space.xxl)) }
          }
        }
    }

    if (confirmEmptyAll) {
        ConfirmDialog(
            title = "Empty recycle bin?",
            body = "Permanently delete all $totalCount item${if (totalCount == 1) "" else "s"}?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = { vm.emptyTrash(); confirmEmptyAll = false },
            onDismiss = { confirmEmptyAll = false }
        )
    }
    if (confirmDeleteSelection) {
        val count = selectedItems.size
        ConfirmDialog(
            title = "Delete selected items?",
            body = "Permanently delete $count selected item${if (count == 1) "" else "s"}?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                selectedItems.forEach { it.deleteForever() }
                confirmDeleteSelection = false
                leaveSelectionMode()
                scope.launch { snackbarHostState.showSnackbar("Deleted $count item${if (count == 1) "" else "s"} forever") }
            },
            onDismiss = { confirmDeleteSelection = false }
        )
    }
    pendingSingleDelete?.let { item ->
        ConfirmDialog(
            title = "Delete forever?",
            body = "Permanently delete \"${item.title.take(60).ifBlank { "Untitled item" }}\"?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                item.deleteForever()
                pendingSingleDelete = null
                scope.launch { snackbarHostState.showSnackbar("Item deleted forever") }
            },
            onDismiss = { pendingSingleDelete = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BinSummary(totalCount: Int, onRestoreAll: () -> Unit, onEmpty: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = vervanBorder()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAffordance(
                icon = Icons.Filled.Delete,
                size = IconAffordanceSize.Default,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text("$totalCount deleted item${if (totalCount == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Items are removed automatically after $RETENTION_DAYS days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ResponsiveActions(Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.md)) {
            androidx.compose.material3.FilledTonalButton(onClick = onRestoreAll) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Restore all", modifier = Modifier.padding(start = Space.sm))
            }
            androidx.compose.material3.OutlinedButton(onClick = onEmpty) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text("Empty bin", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Space.sm))
            }
        }
    }
}

/** Days left before [RecycleBinScreen]'s auto-purge (see VervanApp.RECYCLE_BIN_RETENTION_MS)
 * takes an item — null [deletedAt] (shouldn't happen for a row that's actually in the bin, but
 * defensive) reads as "recently deleted" rather than crashing on the date math. */
private fun daysLeft(deletedAt: Long?): Int {
    if (deletedAt == null) return RETENTION_DAYS
    val elapsedDays = (System.currentTimeMillis() - deletedAt) / (24L * 60 * 60 * 1000)
    return (RETENTION_DAYS - elapsedDays).toInt().coerceIn(0, RETENTION_DAYS)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BinRow(
    item: BinItem,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onRestore: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val remaining = daysLeft(item.deletedAt)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .selectableItem(
                selectionMode = selectionMode,
                onClick = {},
                onToggleSelected = onToggleSelection,
                onEnterSelection = onEnterSelection
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = Space.xs)
                )
            }
            IconAffordance(
                icon = item.icon,
                size = IconAffordanceSize.Compact,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (remaining <= 0) "Purging soon" else "$remaining day${if (remaining == 1) "" else "s"} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remaining <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
