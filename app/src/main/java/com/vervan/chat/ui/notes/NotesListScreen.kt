package com.vervan.chat.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vervan.chat.ui.common.VervanFloatingActionButton as FloatingActionButton
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.ModernistListRow
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: NotesListViewModel = viewModel(factory = viewModelFactory { initializer { NotesListViewModel(app) } })
    val notes by vm.notes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == notes.size && notes.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == notes.size && notes.isNotEmpty()) emptySet() else notes.map { it.id }.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = {
                        val count = selected.size
                        val trashed = notes.filter { it.id in selected }
                        vm.deleteAll(selected)
                        selected = emptySet()
                        selectionMode = false
                        scope.launch {
                            if (snackbarHostState.showSnackbar(
                                    app.resources.getQuantityString(R.plurals.notes_moved_to_recycle_bin, count, count),
                                    app.getString(R.string.action_undo)
                                ) == SnackbarResult.ActionPerformed
                            ) vm.restoreAll(trashed)
                        }
                    },
                    deleteContentDescription = app.getString(R.string.action_recycle)
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.folder_notes)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_new_note))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            if (error != null) {
                OperationErrorCard(
                    title = stringResource(R.string.notes_unavailable),
                    message = error ?: stringResource(R.string.notes_unavailable_message),
                    recovery = stringResource(R.string.notes_unavailable_recovery),
                    modifier = Modifier.padding(top = Space.md),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry
                )
            } else if (isLoading) {
                LoadingSkeletonList(rows = 6, modifier = Modifier.padding(top = Space.md))
            } else if (notes.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Note,
                    title = stringResource(R.string.notes_empty_title),
                    body = stringResource(R.string.notes_empty_body),
                    modifier = Modifier.fillMaxSize(),
                    centered = true,
                    actionLabel = stringResource(R.string.notes_write_action),
                    onAction = { scope.launch { onOpenNote(vm.createNote()) } }
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            selectionMode = selectionMode,
                            selected = note.id in selected,
                            onClick = { onOpenNote(note.id) },
                            onToggleSelected = { selected = if (note.id in selected) selected - note.id else selected + note.id },
                            onEnterSelection = { selectionMode = true; selected = selected + note.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    ModernistListRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)
            .selectableItem(
                selectionMode = selectionMode,
                onClick = onClick,
                onToggleSelected = onToggleSelected,
                onEnterSelection = onEnterSelection
            ),
        selected = selected
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
            )
        } else {
            IconAffordance(Icons.AutoMirrored.Filled.Note, size = IconAffordanceSize.Default)
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverflowTooltipText(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (note.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.chat_filter_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Space.sm).size(14.dp)
                    )
                }
            }
            if (note.content.isNotBlank()) {
                Text(
                    note.content.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                Text(
                    relativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = Space.sm)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                            .padding(horizontal = Space.sm, vertical = 1.dp)
                    )
                }
            }
        }
        /* legacy card body intentionally replaced by a selected list row */
        /*
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                    colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
                )
            } else {
                IconAffordance(Icons.AutoMirrored.Filled.Note, size = IconAffordanceSize.Default)
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OverflowTooltipText(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.chat_filter_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Space.sm).size(14.dp)
                        )
                    }
                }
                if (note.content.isNotBlank()) {
                    Text(
                        note.content.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                    Text(
                        relativeTime(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                    note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = Space.sm)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                .padding(horizontal = Space.sm, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        */
    }
}
