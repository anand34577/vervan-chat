package com.vervan.chat.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ChipInputField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.DiffViewer
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: NoteEditorViewModel = viewModel(factory = viewModelFactory { initializer { NoteEditorViewModel(app, noteId) } })
    val note by vm.note.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val recordFound by vm.recordFound.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val saving by vm.saving.collectAsState()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(note) {
        if (note != null && !loaded) {
            title = note!!.title
            content = note!!.content
            tags = note!!.tags
            loaded = true
        }
    }

    var showActions by remember { mutableStateOf(false) }
    var showKbPicker by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var pendingResult by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val knowledgeBases by vm.knowledgeBases.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Note")
                        if (loaded) {
                            Text(
                                if (saving) "Saving…" else "Saved on this device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.save(title, content, tags); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { previewMode = !previewMode }) {
                        Icon(Icons.Filled.Preview, contentDescription = if (previewMode) "Edit" else "Preview markdown")
                    }
                    IconButton(onClick = { showActions = true }, enabled = !running) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI actions")
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        NoteAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    showActions = false
                                    vm.runAction(action, content) { result -> pendingResult = result }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { vm.save(title, content, tags); showKbPicker = true }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Add to knowledge base")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding).imePadding(), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = "Note unavailable",
                message = loadError.orEmpty(),
                recovery = "Your other notes are safe. Retry the lookup or return to the notes list.",
                actionLabel = "Retry",
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
            !recordFound -> EmptyState(
                icon = Icons.Filled.Preview,
                title = "Note not found",
                body = "This note may have been deleted or moved to the recycle bin.",
                modifier = Modifier.fillMaxSize(),
                centered = true,
                actionLabel = "Back",
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
            BoundedTextField(
                value = title,
                onValueChange = { title = it; vm.scheduleSave(it, content, tags) },
                maxLength = ValidationLimits.NOTE_TITLE,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Title"
            )
            if (running) {
                Row(Modifier.padding(top = Space.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = Space.sm), strokeWidth = 2.dp)
                    Text("Updating the note on this device…")
                }
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Couldn't complete the note action",
                    message = it,
                    recovery = "Your note is safe. Check the model or shorten the selection, then try again.",
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            ChipInputField(
                items = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                onItemsChange = { newTags ->
                    val joined = newTags.joinToString(",")
                    tags = joined
                    vm.scheduleSave(title, content, joined)
                },
                label = "Tags",
                maxItemLength = ValidationLimits.NOTE_TAG,
                maxItemCount = ValidationLimits.NOTE_TAG_COUNT,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )
            if (previewMode) {
                MarkdownLiteText(
                    content.ifBlank { "(empty)" },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm).weight(1f)
                )
            } else {
                BoundedTextField(
                    value = content,
                    onValueChange = { content = it; vm.scheduleSave(title, it, tags) },
                    maxLength = ValidationLimits.NOTE_CONTENT,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm).weight(1f),
                    placeholder = "Write here… Markdown is supported"
                )
            }
            pendingResult?.let { result ->
                DiffViewer(
                    original = content,
                    transformed = result,
                    modifier = Modifier.padding(top = Space.sm),
                    onReplace = { content = result; vm.save(title, result, tags); pendingResult = null },
                    onCancel = { pendingResult = null }
                )
            }
            }
        }
    }
    }

    if (showKbPicker) {
        AlertDialog(
            onDismissRequest = { showKbPicker = false },
            title = { Text("Add to knowledge base") },
            text = {
                Column {
                    if (knowledgeBases.isEmpty()) {
                        Text("No knowledge bases yet. Create one in Knowledge.")
                    }
                    knowledgeBases.forEach { kb ->
                        Text(
                            kb.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.addToKnowledgeBase(kb.id); showKbPicker = false }
                                .padding(vertical = Space.md)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showKbPicker = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete note?",
            body = "\"${title.ifBlank { "Untitled note" }}\" will be moved to the recycle bin.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(); onDeleted() },
            onDismiss = { confirmDelete = false }
        )
    }
}
