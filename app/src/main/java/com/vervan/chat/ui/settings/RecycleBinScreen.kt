package com.vervan.chat.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
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
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.chat.chatPreviewText
import com.vervan.chat.llm.ThinkingParser
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

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
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.error.collectAsState()
    var confirmEmptyAll by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var pendingSingleDelete by remember { mutableStateOf<BinItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val binItems = buildList {
        state.chats.forEach { item -> add(BinItem("chat_${item.id}", "Chats", Icons.AutoMirrored.Filled.Chat, cleanBinTitle(item.title, "Untitled chat"), item.deletedAt, { vm.restoreChat(item) }, { vm.deleteChatForever(item) })) }
        state.notes.forEach { item -> add(BinItem("note_${item.id}", "Notes", Icons.Filled.Edit, cleanBinTitle(item.title, "Untitled note"), item.deletedAt, { vm.restoreNote(item) }, { vm.deleteNoteForever(item) })) }
        state.documents.forEach { item -> add(BinItem("doc_${item.id}", "Documents", Icons.Filled.Description, cleanBinTitle(item.displayName, "Untitled document"), item.deletedAt, { vm.restoreDocument(item) }, { vm.deleteDocumentForever(item) })) }
        state.folders.forEach { item -> add(BinItem("folder_${item.id}", "Folders", Icons.Filled.Folder, cleanBinTitle(item.name, "Untitled folder"), item.deletedAt, { vm.restoreFolder(item) }, { vm.deleteFolderForever(item) })) }
        state.personas.forEach { item -> add(BinItem("persona_${item.id}", "Personas", Icons.Outlined.Person, cleanBinTitle(item.name, "Untitled persona"), item.deletedAt, { vm.restorePersona(item) }, { vm.deletePersonaForever(item) })) }
        state.workflows.forEach { item -> add(BinItem("workflow_${item.id}", "Workflows", Icons.Filled.Widgets, cleanBinTitle(item.name, "Untitled workflow"), item.deletedAt, { vm.restoreWorkflow(item) }, { vm.deleteWorkflowForever(item) })) }
        state.templates.forEach { item -> add(BinItem("template_${item.id}", "Prompt templates", Icons.Filled.Extension, cleanBinTitle(item.name, "Untitled template"), item.deletedAt, { vm.restoreTemplate(item) }, { vm.deleteTemplateForever(item) })) }
        state.projects.forEach { item -> add(BinItem("project_${item.id}", "Projects", Icons.Filled.Workspaces, cleanBinTitle(item.name, "Untitled project"), item.deletedAt, { vm.restoreProject(item) }, { vm.deleteProjectForever(item) })) }
        state.memories.forEach { item -> add(BinItem("memory_${item.id}", "Memories", Icons.Filled.Psychology, cleanBinTitle(item.text, "Untitled memory"), item.deletedAt, { vm.restoreMemory(item) }, { vm.deleteMemoryForever(item) })) }
        state.savedOutputs.forEach { item -> add(BinItem("output_${item.id}", "Saved outputs", Icons.Filled.Bookmark, cleanBinTitle(item.content, "Untitled output"), item.deletedAt, { vm.restoreSavedOutput(item) }, { vm.deleteSavedOutputForever(item) })) }
    }
    val totalCount = binItems.size
    val selectedItems = binItems.filter { it.key in selectedKeys }
    val categories = listOf("All") + binItems.map { it.section }.distinct()
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    val visibleItems = if (selectedCategory == "All") binItems else binItems.filter { it.section == selectedCategory }
    val visibleSelectedCount = visibleItems.count { it.key in selectedKeys }
    LaunchedEffect(categories) {
        if (selectedCategory !in categories) selectedCategory = "All"
    }

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
                    allSelected = visibleSelectedCount == visibleItems.size && visibleItems.isNotEmpty(),
                    onToggleSelectAll = {
                        selectedKeys = if (visibleSelectedCount == visibleItems.size) {
                            selectedKeys - visibleItems.map { it.key }.toSet()
                        } else {
                            selectedKeys + visibleItems.map { it.key }
                        }
                    },
                    onExit = ::leaveSelectionMode,
                    onDelete = { confirmDeleteSelection = true },
                    deleteEnabled = selectedItems.isNotEmpty(),
                    deleteContentDescription = stringResource(R.string.recycle_delete_selected),
                    extraActions = {
                        IconButton(
                            enabled = selectedItems.isNotEmpty(),
                            onClick = {
                                val count = selectedItems.size
                                selectedItems.forEach { it.restore() }
                                leaveSelectionMode()
                                scope.launch { snackbarHostState.showSnackbar(app.getString(R.string.recycle_restored_count, count)) }
                            }
                        ) { Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.recycle_restore_selected)) }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.recycle_title))
                            if (!state.isEmpty) Text(
                                if (totalCount == 1) stringResource(R.string.recycle_item_count_one) else stringResource(R.string.recycle_item_count_many, totalCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (loadError != null) {
            OperationErrorCard(
                title = stringResource(R.string.recycle_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.recycle_unavailable_recovery),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retry,
                modifier = Modifier.padding(padding).padding(Space.md)
            )
            return@Scaffold
        }
        if (isLoading) {
            LoadingSkeletonList(rows = 7, modifier = Modifier.padding(padding).padding(Space.md))
            return@Scaffold
        }
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.recycle_empty),
                body = stringResource(R.string.recycle_empty_body, RETENTION_DAYS),
                modifier = Modifier.padding(padding).fillMaxSize(),
                centered = true
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
             item(key = "category_filters") {
                 FlowRow(
                     modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
                     horizontalArrangement = Arrangement.spacedBy(Space.sm),
                     verticalArrangement = Arrangement.spacedBy(Space.xs)
                 ) {
                     categories.forEach { category ->
                         val count = if (category == "All") totalCount else binItems.count { it.section == category }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            shape = MaterialTheme.shapes.extraSmall,
                            label = { Text(stringResource(R.string.ui_recyclebin_category_count, category, count), maxLines = 1) }
                         )
                     }
                 }
             }
             if (visibleItems.isEmpty()) {
                 item(key = "empty_category") {
                     EmptyState(
                         icon = Icons.Filled.Delete,
                        title = stringResource(R.string.recycle_no_category, selectedCategory),
                        body = stringResource(R.string.recycle_choose_category)
                     )
                 }
             }
             visibleItems.groupBy { it.section }.forEach { (section, sectionItems) ->
                 item(key = "section_$section") {
                     SectionLabel(if (selectedCategory == "All") section else selectedCategory, count = sectionItems.size)
                 }
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
                            scope.launch { snackbarHostState.showSnackbar(app.getString(R.string.recycle_restored_count, 1)) }
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
            title = stringResource(R.string.recycle_empty_title),
            body = stringResource(R.string.recycle_empty_body_confirm, totalCount),
            confirmLabel = stringResource(R.string.recycle_delete_forever),
            destructive = true,
            onConfirm = { vm.emptyTrash(); confirmEmptyAll = false },
            onDismiss = { confirmEmptyAll = false }
        )
    }
    if (confirmDeleteSelection) {
        val count = selectedItems.size
        ConfirmDialog(
            title = stringResource(R.string.recycle_delete_selected_title),
            body = stringResource(R.string.recycle_delete_selected_body, count),
            confirmLabel = stringResource(R.string.recycle_delete_forever),
            destructive = true,
            onConfirm = {
                selectedItems.forEach { it.deleteForever() }
                confirmDeleteSelection = false
                leaveSelectionMode()
                scope.launch { snackbarHostState.showSnackbar(app.getString(R.string.recycle_deleted_count, count)) }
            },
            onDismiss = { confirmDeleteSelection = false }
        )
    }
    pendingSingleDelete?.let { item ->
        val itemTitle = item.title.take(60).ifBlank { app.getString(R.string.action_item) }
        ConfirmDialog(
            title = stringResource(R.string.recycle_delete_forever_title),
            body = stringResource(R.string.recycle_delete_item_body, itemTitle),
            confirmLabel = stringResource(R.string.recycle_delete_forever),
            destructive = true,
            onConfirm = {
                item.deleteForever()
                pendingSingleDelete = null
                scope.launch { snackbarHostState.showSnackbar(app.getString(R.string.recycle_delete_forever)) }
            },
            onDismiss = { pendingSingleDelete = null }
        )
    }
}

@Composable
private fun BinSummary(totalCount: Int, onRestoreAll: () -> Unit, onEmpty: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.sm),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAffordance(
                    icon = Icons.Filled.Delete,
                    size = IconAffordanceSize.Default,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(stringResource(R.string.recycle_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.recycle_summary_body, RETENTION_DAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                if (totalCount == 1) stringResource(R.string.recycle_recoverable_count_one) else stringResource(R.string.recycle_recoverable_count_many, totalCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Space.lg)
            )
            Text(
                stringResource(R.string.recycle_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs)
            )
            ResponsiveActions(Modifier.padding(top = Space.md)) {
                com.vervan.chat.ui.common.VervanFilledTonalButton(onClick = onRestoreAll) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.recycle_restore_all), modifier = Modifier.padding(start = Space.sm))
                }
                com.vervan.chat.ui.common.VervanOutlinedButton(onClick = onEmpty) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.recycle_empty_action), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Space.sm))
                }
            }
        }
    }
}

private fun cleanBinTitle(raw: String, fallback: String): String {
    val visible = ThinkingParser.parse(raw).answer
    return chatPreviewText(visible, isUser = true)
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
        .ifBlank { fallback }
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
    val displayTitle = item.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.recycle_untitled)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .selectableItem(
                selectionMode = selectionMode,
                // A recycle-bin row has no "open" destination (its content is gone) — restore/
                // delete are the only actions, both already reachable via the trailing icons.
                // Tapping used to no-op while still showing a ripple, a dead affordance; tap now
                // does the same thing long-press does (enter selection), so the ripple means something.
                onClick = onEnterSelection,
                onToggleSelected = onToggleSelection,
                onEnterSelection = onEnterSelection
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else SurfaceRole.Card.border(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Text(displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (remaining <= 0) stringResource(R.string.recycle_purging_soon)
                    else if (remaining == 1) stringResource(R.string.recycle_days_left_one)
                    else stringResource(R.string.recycle_days_left_many, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remaining <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.recycle_restore), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.recycle_delete_forever), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
