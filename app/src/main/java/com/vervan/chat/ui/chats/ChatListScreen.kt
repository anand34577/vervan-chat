package com.vervan.chat.ui.chats

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
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
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.formatRelativeDay
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.ui.theme.vervanBorder
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onNewChat: (() -> Unit)? = null) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val vm: ChatListViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatListViewModel(app) }
    })
    val chats by vm.chats.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val projectNames by vm.projectNames.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderNames by vm.folderNames.collectAsStateWithLifecycle()
    val lastMessageByChat by vm.lastMessageByChat.collectAsStateWithLifecycle()
    val modelNames by vm.modelNames.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<Chat?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var savedFilter by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        savedFilter.takeIf { it.isNotBlank() }
            ?.let { name -> ChatFilter.entries.firstOrNull { it.name == name }?.let(vm::setFilter) }
    }
    val visibleChats = remember(chats, query, lastMessageByChat) {
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
    androidx.activity.compose.BackHandler(enabled = selectionMode) {
        selected = emptySet()
        selectionMode = false
    }
    // Selection never outlives the rows it refers to (e.g. after a filter change).
    androidx.compose.runtime.LaunchedEffect(chats) {
        val ids = chats.map { it.id }.toSet()
        if (selected.any { it !in ids }) {
            selected = selected intersect ids
            if (selected.isEmpty()) selectionMode = false
        }
    }
    val pinnedLabel = stringResource(R.string.chat_filter_pinned)
    val buckets by remember(visibleChats, filter, pinnedLabel) {
        derivedStateOf { bucketChats(visibleChats, filter, pinnedLabel) }
    }

    Scaffold(
        // The navigation shell already reserves the bottom navigation and gesture area.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!selectionMode) MediumTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chat_list_title))
                        Text(
                            run {
                                val count = androidx.compose.ui.res.pluralStringResource(R.plurals.chat_list_count, chats.size, chats.size)
                                val pinned = chats.count { it.pinned }
                                if (pinned > 0) stringResource(R.string.chat_list_pinned_suffix, count, pinned) else count
                            },
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
                // Scoped to the visible (search-filtered) rows: "select all" must never pick up
                // chats the current search is hiding, or a bulk delete would trash them unseen.
                allSelected = visibleChats.isNotEmpty() && visibleChats.all { it.id in selected },
                onToggleSelectAll = {
                    selected = if (visibleChats.isNotEmpty() && visibleChats.all { it.id in selected }) emptySet()
                    else visibleChats.map { it.id }.toSet()
                },
                onExit = { selected = emptySet(); selectionMode = false },
                onDelete = {
                    val count = selected.size
                    val trashed = chats.filter { it.id in selected }
                    vm.moveToTrash(selected)
                    selected = emptySet()
                    selectionMode = false
                    scope.launch {
                        if (snackbarHostState.showSnackbar(
                                localResources.getQuantityString(R.plurals.chat_list_moved_to_bin, count, count),
                                localResources.getString(R.string.action_undo)
                            ) == SnackbarResult.ActionPerformed
                        ) vm.restoreFromTrash(trashed)
                    }
                },
                deleteContentDescription = "Move selected to recycle bin",
                extraActions = {
                    // Archive/move-to-folder are extras this screen needs beyond the shared
                    // select-all + delete shape — kept exactly as before, just relocated into
                    // SelectionTopBar's extraActions slot instead of a bespoke top bar.
                    if (filter == ChatFilter.ARCHIVED) {
                        TextButton(
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                val count = selected.size
                                vm.unarchive(selected)
                                selected = emptySet()
                                selectionMode = false
                                scope.launch {
                                    if (snackbarHostState.showSnackbar(localResources.getQuantityString(R.plurals.chat_list_restored, count, count), localResources.getString(R.string.action_view)) == SnackbarResult.ActionPerformed) {
                                        vm.setFilter(ChatFilter.ALL)
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.backup_restore_action)) }
                    } else {
                        IconButton(
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                val ids = selected
                                val count = ids.size
                                vm.archive(ids)
                                selected = emptySet()
                                selectionMode = false
                                scope.launch {
                                    if (snackbarHostState.showSnackbar(
                                            localResources.getQuantityString(R.plurals.chat_list_archived, count, count),
                                            localResources.getString(R.string.action_undo)
                                        ) == SnackbarResult.ActionPerformed
                                    ) vm.unarchive(ids)
                                }
                            }
                        ) { Icon(Icons.Filled.Archive, stringResource(R.string.chat_list_archive_selected)) }
                    }
                    Box {
                        IconButton(onClick = { showFolders = true }, enabled = selected.isNotEmpty()) { Icon(Icons.Filled.Folder, stringResource(R.string.chat_list_move_to_folder)) }
                        DropdownMenu(showFolders, { showFolders = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_list_default_folder)) }, onClick = { vm.moveToFolder(selected, null); selected = emptySet(); selectionMode = false; showFolders = false })
                            folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = { vm.moveToFolder(selected, folder.id); selected = emptySet(); selectionMode = false; showFolders = false }) }
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
                onFilter = {
                    savedFilter = it.name
                    vm.setFilter(it)
                }
            )
            // Skeleton state during cold start — the previous behavior flashed "No chats here"
            // before the DB had emitted anything, which read as data loss.
            if (error != null) {
                OperationErrorCard(
                    title = stringResource(R.string.chat_list_unavailable),
                    message = error ?: stringResource(R.string.chat_list_unavailable_message),
                    recovery = stringResource(R.string.chat_list_unavailable_recovery),
                    modifier = Modifier.padding(top = Space.md),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry
                )
            } else if (isLoading) {
                LoadingSkeletonList(rows = 7, modifier = Modifier.padding(top = Space.md))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.md),
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
                                    selected = if (chat.id in selected) selected - chat.id else selected + chat.id
                                    // Unselecting the last row leaves selection mode, like every
                                    // other list on Android, instead of an empty "0 selected" bar.
                                    selectionMode = selected.isNotEmpty()
                                },
                                onTogglePin = { vm.togglePin(chat) },
                                onToggleArchive = {
                                    val restoring = chat.archived
                                    vm.toggleArchive(chat)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            localResources.getString(if (restoring) R.string.chat_list_restored_one else R.string.chat_list_archived_one),
                                            actionLabel = localResources.getString(if (restoring) R.string.action_view else R.string.action_undo)
                                        )
                                        if (restoring && result == SnackbarResult.ActionPerformed) vm.setFilter(ChatFilter.ALL)
                                        if (!restoring && result == SnackbarResult.ActionPerformed) vm.unarchive(setOf(chat.id))
                                    }
                                },
                                onMoveToTrash = {
                                    vm.moveToTrash(chat)
                                    scope.launch {
                                        if (snackbarHostState.showSnackbar(
                                                localResources.getString(R.string.chat_list_moved_one),
                                                localResources.getString(R.string.action_undo)
                                            ) == SnackbarResult.ActionPerformed
                                        ) vm.restoreFromTrash(listOf(chat))
                                    }
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
                                title = if (query.isBlank()) stringResource(R.string.chat_list_empty) else stringResource(R.string.chat_list_no_matches),
                                body = if (query.isBlank()) stringResource(R.string.chat_list_empty_body) else stringResource(R.string.chat_list_no_matches_body),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                                // A direct way out of the empty list instead of pointing at a
                                // button somewhere else on screen.
                                actionLabel = if (query.isBlank() && filter == ChatFilter.ALL && onNewChat != null) stringResource(R.string.create_new_chat) else null,
                                onAction = onNewChat
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
            title = { Text(stringResource(R.string.chat_rename)) },
            text = { BoundedTextField(value = title, onValueChange = { title = it }, maxLength = 120, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(chat, title); renameTarget = null }, enabled = title.trim().isNotBlank() && title.length <= 120) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) } }
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
private fun bucketChats(chats: List<Chat>, filter: ChatFilter, pinnedLabel: String): List<Pair<String, List<Chat>>> {
    if (chats.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, List<Chat>>>()
    // Only hoist pinned chats when viewing all — Pinned filter shows *only* pinned (no need
    // for a section header), Archived filter doesn't allow pin emphasis.
    if (filter == ChatFilter.ALL) {
        val pinned = chats.filter { it.pinned }
        if (pinned.isNotEmpty()) result.add(pinnedLabel to pinned)
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
            placeholder = stringResource(R.string.chat_list_search_placeholder)
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
                    label = { Text(stringResource(f.labelRes())) }
                )
            }
        }
    }
}

private fun ChatFilter.labelRes(): Int = when (this) {
    ChatFilter.ALL -> R.string.chat_filter_all
    ChatFilter.PINNED -> R.string.chat_filter_pinned
    ChatFilter.ARCHIVED -> R.string.chat_filter_archived
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
    // Swipe right to pin and left to archive. The reveal stays bounded so the row never leaves
    // the viewport, then returns to rest before the list re-sorts or filters the changed chat.
    val density = LocalDensity.current
    val maxRevealPx = remember(density) { with(density) { 104.dp.toPx() } }
    val actionThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }
    var swipeOffsetPx by remember(chat.id) { mutableFloatStateOf(0f) }
    val pinLabel = if (chat.pinned) stringResource(R.string.chat_unpin) else stringResource(R.string.chat_pin)
    val archiveLabel = if (chat.archived) stringResource(R.string.chat_unarchive) else stringResource(R.string.action_archive)
    val draggableState = rememberDraggableState { delta ->
        swipeOffsetPx = clampedChatSwipeOffset(swipeOffsetPx, delta, maxRevealPx)
    }
    val actionModifier = if (selectionMode) Modifier else Modifier
        .draggable(
            state = draggableState,
            orientation = Orientation.Horizontal,
            onDragStopped = {
                val action = when {
                    swipeOffsetPx >= actionThresholdPx -> onTogglePin
                    swipeOffsetPx <= -actionThresholdPx -> onToggleArchive
                    else -> null
                }
                // Do not remove/disable this draggable while its onDragStopped coroutine is
                // suspended: doing so cancels the modifier node that owns the animation and
                // leaves the card frozen at its revealed offset. The finally block also makes
                // an interrupted animation snap home before applying the committed action.
                try {
                    animate(
                        initialValue = swipeOffsetPx,
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 160)
                    ) { value, _ -> swipeOffsetPx = value }
                } finally {
                    swipeOffsetPx = 0f
                    action?.invoke()
                }
            }
        )
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction("$pinLabel chat") { onTogglePin(); true },
                CustomAccessibilityAction("$archiveLabel chat") { onToggleArchive(); true }
            )
        }
    Box(modifier = Modifier.fillMaxWidth().then(actionModifier)) {
        val revealsPin = swipeOffsetPx >= 0f
        val color = if (revealsPin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        val icon = if (revealsPin) Icons.Filled.PushPin else Icons.Filled.Archive
        val label = if (revealsPin) pinLabel else archiveLabel
        Row(
            Modifier
                .matchParentSize()
                .padding(vertical = 3.dp)
                .clip(MaterialTheme.shapes.large)
                .background(color)
                .padding(horizontal = Space.xxl),
            horizontalArrangement = if (revealsPin) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!revealsPin) Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Space.sm))
            Icon(icon, contentDescription = null)
            if (revealsPin) Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.sm))
        }
    // Each conversation is a resting card on the page — container tint + border from the
    // surface-role system, so the history reads as a stack of distinct, tappable conversations
    // rather than an undifferentiated flat list. Selection swaps to the app-wide selected tint.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .graphicsLayer { translationX = swipeOffsetPx },
        shape = MaterialTheme.shapes.large,
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
        else com.vervan.chat.ui.theme.SurfaceRole.Card.border()
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
            // Colored initial avatar — a stable per-chat accent (hashed from the id) with the
            // title's first letter, so the list scans by color+letter the way modern chat and
            // mail apps do, instead of forty identical chat glyphs.
            run {
                val accent = com.vervan.chat.ui.theme.vervanAccentFor(chat.id.hashCode())
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            // Pinned chats carry the brand gradient — the app-wide "important"
                            // mark — while everything else keeps its stable categorical accent.
                            if (chat.pinned) com.vervan.chat.ui.theme.vervanBrandFill()
                            else androidx.compose.ui.graphics.SolidColor(accent.container)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = chat.title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                    val fg = if (chat.pinned) MaterialTheme.colorScheme.onPrimary else accent.onContainer
                    if (initial != null) {
                        Text(initial.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OverflowTooltipText(
                        text = chat.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.pinned) {
                        Icon(
                            Icons.Filled.PushPin, contentDescription = stringResource(R.string.chat_filter_pinned),
                            modifier = Modifier.size(14.dp).padding(end = 2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        relativeTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall,
                        fontFamily = VervanMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
                // Preview line prefers the last actual message (chat-app convention); falls back
                // to the draft (a Notes-style preview) only when the chat has no messages yet.
                val previewText = lastMessage?.let {
                    com.vervan.chat.ui.chat.chatPreviewText(it.content, it.role == com.vervan.chat.data.db.entities.MessageRole.USER)
                }?.takeIf { it.isNotBlank() }
                    ?: chat.draft.takeIf { it.isNotBlank() }
                previewText?.let {
                    val prefix = if (lastMessage != null && lastMessage.role == com.vervan.chat.data.db.entities.MessageRole.USER) "You: " else ""
                    Text(
                        stringResource(R.string.chat_list_arg1_arg2, prefix, it),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                ResponsiveActions(Modifier.padding(top = Space.xs)) {
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
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(if (chat.pinned) stringResource(R.string.chat_unpin) else stringResource(R.string.chat_pin)) }, onClick = { onTogglePin(); showMenu = false })
                    DropdownMenuItem(text = { Text(if (selected) stringResource(R.string.chat_list_unselect) else stringResource(R.string.chat_list_select)) }, onClick = { onSelect(); showMenu = false })
                    ArchiveMenuItem(archived = chat.archived, onClick = { onToggleArchive(); showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { onRename(); showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.persona_duplicate)) }, onClick = { onDuplicate(); showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_list_export)) }, onClick = { onExport(); showMenu = false })
                    DeleteMenuItem(onClick = { onMoveToTrash(); showMenu = false })
                }
            }
        }
    }
    }
}

internal fun clampedChatSwipeOffset(currentOffset: Float, dragDelta: Float, maxReveal: Float): Float =
    (currentOffset + dragDelta).coerceIn(-maxReveal, maxReveal)
