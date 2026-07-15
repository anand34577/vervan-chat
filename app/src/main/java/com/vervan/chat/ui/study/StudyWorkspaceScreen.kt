package com.vervan.chat.ui.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.ValidationMessage
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyWorkspaceScreen(onBack: () -> Unit, onOpenSet: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: StudyWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { StudyWorkspaceViewModel(app) } })
    val setNames by vm.setNames.collectAsState()
    val generating by vm.generating.collectAsState()
    val error by vm.error.collectAsState()

    var showGenerate by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingSingleDelete by remember { mutableStateOf<String?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == setNames.size && setNames.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == setNames.size && setNames.isNotEmpty()) emptySet() else setNames.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { confirmBulkDelete = true },
                    deleteContentDescription = "Delete selected sets"
                )
            } else {
                TopAppBar(
                    title = { Text("Study workspace") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = { IconButton(onClick = { showGenerate = true }) { Icon(Icons.Filled.Add, contentDescription = "New flashcard set") } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (setNames.isEmpty() && !showGenerate) {
                Column(Modifier.padding(24.dp)) {
                    Text("No flashcard sets yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paste some study material and generate a set to review.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = { showGenerate = true }) { Text("Generate a set") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    items(setNames, key = { it }) { name ->
                        val isSelected = name in selected
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .selectableItem(
                                    selectionMode = selectionMode,
                                    onClick = { onOpenSet(name) },
                                    onToggleSelected = { selected = if (isSelected) selected - name else selected + name },
                                    onEnterSelection = { selectionMode = true; selected = selected + name }
                                ),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { selected = if (isSelected) selected - name else selected + name }
                                    )
                                }
                                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (!selectionMode) {
                                    TextButton(onClick = { pendingSingleDelete = name }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingSingleDelete?.let { name ->
        ConfirmDialog(
            title = "Delete flashcard set?",
            body = "\"$name\" and all of its cards will be permanently deleted. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteSet(name); pendingSingleDelete = null },
            onDismiss = { pendingSingleDelete = null }
        )
    }

    if (confirmBulkDelete) {
        val count = selected.size
        ConfirmDialog(
            title = "Delete selected sets?",
            body = "$count flashcard set${if (count == 1) "" else "s"} and all of their cards will be permanently deleted. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                selected.forEach { vm.deleteSet(it) }
                confirmBulkDelete = false
                selected = emptySet()
                selectionMode = false
            },
            onDismiss = { confirmBulkDelete = false }
        )
    }

    if (showGenerate) {
        var setName by remember { mutableStateOf("") }
        var sourceText by remember { mutableStateOf("") }
        var cardCount by remember { mutableFloatStateOf(8f) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!generating) showGenerate = false },
            title = { Text("New flashcard set") },
            text = {
                Column {
                    BoundedTextField(
                        value = setName, onValueChange = { setName = it }, label = "Set name",
                        singleLine = true, maxLength = ValidationLimits.STUDY_SET_NAME,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BoundedTextField(
                        value = sourceText, onValueChange = { sourceText = it }, label = "Study material",
                        minLines = 5, maxLength = ValidationLimits.STUDY_SOURCE,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text("Cards: ${cardCount.toInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Slider(value = cardCount, onValueChange = { cardCount = it }, valueRange = 1f..100f, steps = 98)
                    error?.let { ValidationMessage(it, modifier = Modifier.padding(top = 8.dp)) }
                    if (generating) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(20.dp), strokeWidth = 2.dp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.generateSet(setName, sourceText, cardCount.toInt()) { showGenerate = false; onOpenSet(setName) } },
                    enabled = !generating
                ) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showGenerate = false }, enabled = !generating) { Text("Cancel") } }
        )
    }
}
