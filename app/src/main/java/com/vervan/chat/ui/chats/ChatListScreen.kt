package com.vervan.chat.ui.chats

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanTopAppBar as MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ArchiveMenuItem
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.formatRelativeDay
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.ui.theme.vervanBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: ChatListViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatListViewModel(app) }
    })
    val chats by vm.chats.collectAsState()
    val filter by vm.filter.collectAsState()
    val projectNames by vm.projectNames.collectAsState()
    val folders by vm.folders.collectAsState()
    val folderNames by vm.folderNames.collectAsState()
    val lastMessageByChat by vm.lastMessageByChat.collectAsState()
    val modelNames by vm.modelNames.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<Chat?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Loading state — `chats` is a StateFlow seeded with emptyList(), so on cold start the
    // user used to briefly see "No chats here" before the first DB emit arrived. This flag
    // tracks the very first emit so we can show skeletons during that gap and a real empty
    // state only when the DB says it's actually empty.
    var initialLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(chats) { if (chats.isNotEmpty()) initialLoaded = true }
    // The DB also emits an empty list legitimately (a user with no chats). Use the chats' size
    // *or* having ever emitted anything — the simplest robust signal is "has the chats flow
    // ever been collected once" which is impossible to observe directly without snapshot.
    // Pragmatic proxy: once we observe any other list (folders, projects) emit non-empty OR
    // chats emit non-empty, the load is done.
    LaunchedEffect(folders, projectNames) {
        if (folders.isNotEmpty() || projectNames.isNotEmpty()) initialLoaded = true
    }
    val visibleChats = remember(chats, query) {
        if (query.isBlank()) chats else chats.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.draft.contains(query, ignoreCase = true) ||
                // Full-text search into the last message — closes the "search doesn't search what
                // they actually said" gap from the analysis. Doesn't search *all* messages (the
                // global Search screen does that), just the preview, which is what users typing
                // into a list filter expect.
                (lastMessageByChat[it.id]?.content?.contains(query, ignoreCase = true) == true)
        }
    }
    // Date-bucket the visible chats for sticky-header grouping (Today / Yesterday / Mar 14 / Older).
    // Pinned chats hoist to a separate "Pinned" bucket above date groups, matching every other
    // chat app — the previous list showed pinned chats inline and made pinning feel pointless.
    val buckets by remember(visibleChats, filter) {
        derivedStateOf { bucketChats(visibleChats, filter) }
    }

    Scaffold(
        topBar = {
            if (!selectionMode) MediumTopAppBar(
                title = {
                    Column {
                        Text("Chats")
                        Text(
                            "${chats.size} conversations · ${chats.count { it.pinned }} pinned",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Long-press a row to enter selection mode — no separate top-bar entry point,
                // so there's one consistent gesture for this everywhere instead of two paths
                // that can drift (a tap-triggered mode here, long-press-only somewhere else).
            ) else SelectionTopBar(
                selectedCount = selected.size,
                allSelected = selected.size == chats.size && chats.isNotEmpty(),
                onToggleSelectAll = { selected = if (selected.size == chats.size && chats.isNotEmpty()) emptySet() else chats.map { it.id }.toSet() },
                onExit = { selected = emptySet(); selectionMode = false },
                onDelete = {
                    val count = selected.size
                    vm.moveToTrash(selected)
                    selected = emptySet()
                    selectionMode = false
                    scope.launch { snackbarHostState.showSnackbar("Moved $count chat${if (count == 1) "" else "s"} to the recycle bin") }
                },
                deleteContentDescription = "Move selected to recycle bin",
                extraActions = {
                    // Archive/move-to-folder are extras this screen needs beyond the shared
                    // select-all + delete shape — kept exactly as before, just relocated into
                    // SelectionTopBar's extraActions slot instead of a bespoke top bar.
                    if (filter == ChatFilter.ARCHIVED) {
                        TextButton(
                            enabled = selected.isNotEmpty(),
                            onClick = { vm.unarchive(selected); selected = emptySet(); selectionMode = false }
                        ) { Text("Restore") }
                    } else {
                        IconButton(
                            enabled = selected.isNotEmpty(),
                            onClick = { vm.archive(selected); selected = emptySet(); selectionMode = false }
                        ) { Icon(Icons.Filled.Archive, "Archive selected") }
                    }
                    Box {
                        IconButton(onClick = { showFolders = true }, enabled = selected.isNotEmpty()) { Icon(Icons.Filled.Folder, "Move to folder") }
                        DropdownMenu(showFolders, { showFolders = false }) {
                            DropdownMenuItem(text = { Text("Default folder") }, onClick = { vm.moveToFolder(selected, null); selected = emptySet(); selectionMode = false; showFolders = false })
                            folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { vm.moveToFolder(selected, folder.id); selected = emptySet(); selectionMode = false; showFolders = false }) }
                        }
                    }
                }
            )
        },
        // The duplicate local FAB here used to collide with NavGraph's global ExtendedFAB on the
        // Chats tab — both rendered simultaneously at bottom-right. The global one already opens
        // the same flow via CreateSheet, so this screen no longer needs its own FAB.
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            ChatListHeader(
                query = query,
                onQueryChange = { query = it },
                filter = filter,
                onFilter = vm::setFilter
            )
            // Skeleton state during cold start — the previous behavior flashed "No chats here"
            // before the DB had emitted anything, which read as data loss.
            if (!initialLoaded && chats.isEmpty()) {
                LoadingSkeletonList(rows = 7, modifier = Modifier.padding(top = Space.md))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    buckets.forEach { (bucketLabel, bucketChats) ->
                        stickyHeader(key = bucketLabel) {
                            ChatBucketHeader(label = bucketLabel, count = bucketChats.size)
                        }
                        items(bucketChats, key = { "$bucketLabel-${it.id}" }) { chat ->
                            ChatListRow(
                                chat = chat,
                                projectName = chat.projectId?.let { projectNames[it] },
                                folderName = chat.folderId?.let { folderNames[it] },
                                modelName = chat.modelId?.let { modelNames[it] },
                                lastMessage = lastMessageByChat[chat.id],
                                onClick = { onOpenChat(chat.id) },
                                selected = chat.id in selected,
                                selectionMode = selectionMode,
                                onSelect = {
                                    selectionMode = true
                                    selected = if (chat.id in selected) selected - chat.id else selected + chat.id
                                },
                                onTogglePin = { vm.togglePin(chat) },
                                onToggleArchive = { vm.toggleArchive(chat) },
                                onMoveToTrash = {
                                    vm.moveToTrash(chat)
                                    scope.launch { snackbarHostState.showSnackbar("Moved chat to the recycle bin") }
                                },
                                onRename = { renameTarget = chat },
                                onDuplicate = { vm.duplicate(chat) },
                                onExport = {
                                    scope.launch {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, chat.title)
                                            putExtra(Intent.EXTRA_TEXT, vm.exportText(chat))
                                        }
                                        context.startActivity(Intent.createChooser(send, "Export chat"))
                                    }
                                }
                            )
                        }
                    }
                    if (buckets.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                title = if (query.isBlank()) "No chats here" else "No matching chats",
                                body = if (query.isBlank()) "Start a new private chat from the Create button." else "Try a different word or clear the search field.",
                                modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp)
                            )
                        }
                    }
                }
            }
        }
      }
    }

    renameTarget?.let { chat ->
        var title by remember(chat.id) { mutableStateOf(chat.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = { BoundedTextField(value = title, onValueChange = { title = it }, maxLength = 120, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(chat, title); renameTarget = null }, enabled = title.trim().isNotBlank() && title.length <= 120) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

/** Sticky header for a date/pinned bucket. Uses surfaceContainer (the same surface as the
 *  app bar) so it reads as a section anchor rather than another card mid-list. */
@Composable
private fun ChatBucketHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Group chats into ordered buckets for the sticky-header list. Pinned first (when the filter
 *  allows pinning), then date buckets (Today / Yesterday / weekday / month-day / Older).
 *  Returns ordered pairs so the LazyColumn just iterates. */
private fun bucketChats(chats: List<Chat>, filter: ChatFilter): List<Pair<String, List<Chat>>> {
    if (chats.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, List<Chat>>>()
    // Only hoist pinned chats when viewing all — Pinned filter shows *only* pinned (no need
    // for a section header), Archived filter doesn't allow pin emphasis.
    if (filter == ChatFilter.ALL) {
        val pinned = chats.filter { it.pinned }
        if (pinned.isNotEmpty()) result.add("Pinned" to pinned)
    }
    val forDateBuckets = if (filter == ChatFilter.ALL) chats.filterNot { it.pinned } else chats
    val now = System.currentTimeMillis()
    val byDay = forDateBuckets.groupBy { formatRelativeDay(it.updatedAt, now) }
    // Preserve a sensible date order rather than insertion order.
    val orderedLabels = byDay.keys.mapNotNull { label ->
        // Reconstruct an approximate epoch for ordering by finding the min updatedAt in that bucket.
        val minTs = byDay[label]?.minOfOrNull { it.updatedAt } ?: return@mapNotNull null
        Triple(label, minTs, byDay.getValue(label))
    }.sortedByDescending { it.second }
    orderedLabels.forEach { (label, _, list) -> result.add(label to list) }
    return result
}

@Composable
private fun ChatListHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ChatFilter,
    onFilter: (ChatFilter) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = Space.md)) {
        VervanSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search conversations"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            ChatFilter.entries.forEach { f ->
                VervanFilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(f.label()) }
                )
            }
        }
    }
}

private fun ChatFilter.label(): String = when (this) {
    ChatFilter.ALL -> "All"
    ChatFilter.PINNED -> "Pinned"
    ChatFilter.ARCHIVED -> "Archived"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatListRow(
    chat: Chat,
    projectName: String?,
    folderName: String?,
    modelName: String?,
    lastMessage: com.vervan.chat.data.db.entities.Message?,
    onClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    // §7.2.1 swipe right = pin, swipe left = archive. Observe the reached anchor, perform the
    // action, then reset so the row remains reusable after the list re-sorts/re-filters.
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onTogglePin()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onToggleArchive()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val toStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toStart) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            val icon = if (toStart) Icons.Filled.PushPin else Icons.Filled.Archive
            val label = if (toStart) (if (chat.pinned) "Unpin" else "Pin") else (if (chat.archived) "Unarchive" else "Archive")
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = Space.xs)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(horizontal = Space.xxl),
                horizontalArrangement = if (toStart) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!toStart) Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Space.sm))
                Icon(icon, contentDescription = label)
                if (toStart) Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.sm))
            }
        }
    ) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else vervanBorder(com.vervan.chat.ui.theme.VervanBorderProminence.Subtle)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableItem(
                    selectionMode = selectionMode,
                    onClick = onClick,
                    onToggleSelected = onSelect,
                    onEnterSelection = onSelect
                )
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode || selected) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelect() },
                    colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
                )
            }
            IconAffordance(
                icon = Icons.AutoMirrored.Filled.Chat,
                size = IconAffordanceSize.Default,
                tint = if (chat.pinned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                containerColor = if (chat.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        relativeTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall,
                        fontFamily = VervanMono, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Preview line prefers the last actual message (chat-app convention); falls back
                // to the draft (a Notes-style preview) only when the chat has no messages yet.
                val previewText = lastMessage?.content?.takeIf { it.isNotBlank() }
                    ?: chat.draft.takeIf { it.isNotBlank() }
                previewText?.let {
                    val prefix = if (lastMessage != null && lastMessage.role == com.vervan.chat.data.db.entities.MessageRole.USER) "You: " else ""
                    Text(
                        "$prefix$it",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    // Model badge — for a multi-engine offline LLM app this is the single most
                    // useful identity cue (chat-app users immediately want to know "which model
                    // did I use here?"). Suppressed when no model is set (the global default
                    // applies, which is true for most chats and would be noisy to show on every row).
                    modelName?.let { SemanticChip(it, ChipTone.Neutral) }
                    // Only render folder chip when the chat is in a *named* folder — showing
                    // "Default" on every un-filed chat was pure clutter.
                    folderName?.let { SemanticChip(it, ChipTone.Neutral) }
                    projectName?.let { SemanticChip(it, ChipTone.Neutral) }
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Chat options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(if (chat.pinned) "Unpin" else "Pin") }, onClick = { onTogglePin(); showMenu = false })
                    DropdownMenuItem(text = { Text(if (selected) "Unselect" else "Select") }, onClick = { onSelect(); showMenu = false })
                    ArchiveMenuItem(archived = chat.archived, onClick = { onToggleArchive(); showMenu = false })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { onRename(); showMenu = false })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDuplicate(); showMenu = false })
                    DropdownMenuItem(text = { Text("Export") }, onClick = { onExport(); showMenu = false })
                    DeleteMenuItem(onClick = { onMoveToTrash(); showMenu = false })
                }
            }
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
        diffMin < 60 * 24 * 30 -> "${diffMin / (60 * 24)}d"
        // Beyond 30 days, switch to a date so a 90-day-old chat stops reading "90d" forever.
        else -> {
            val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochMs))
        }
    }
}
