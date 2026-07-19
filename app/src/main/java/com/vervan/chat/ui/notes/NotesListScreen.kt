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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
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
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: NotesListViewModel = viewModel(factory = viewModelFactory { initializer { NotesListViewModel(app) } })
    val notes by vm.notes.collectAsState()
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
                        vm.deleteAll(selected)
                        selected = emptySet()
                        selectionMode = false
                        scope.launch { snackbarHostState.showSnackbar("Moved $count note${if (count == 1) "" else "s"} to the recycle bin") }
                    },
                    deleteContentDescription = "Move selected to recycle bin"
                )
            } else {
                TopAppBar(
                    title = { Text("Notes") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            if (notes.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Note,
                    title = "No notes yet",
                    body = "Capture ideas, meeting notes, and anything worth keeping.",
                    actionLabel = "Write a note",
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)
            .selectableItem(
                selectionMode = selectionMode,
                onClick = onClick,
                onToggleSelected = onToggleSelected,
                onEnterSelection = onEnterSelection
            ),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else SurfaceRole.Card.cardColors(),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
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
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Space.sm).size(14.dp)
                        )
                    }
                }
                if (note.content.isNotBlank()) {
                    Text(
                        note.content.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                    Text(
                        relativeTime(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), VervanExtraShapes.pill)
                                .padding(horizontal = Space.sm, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
