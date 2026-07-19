package com.vervan.chat.ui.study

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.ValidationMessage
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.selectableItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyWorkspaceScreen(onBack: () -> Unit, onOpenSet: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: StudyWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { StudyWorkspaceViewModel(app) } })
    val sets by vm.sets.collectAsState()
    val generating by vm.generating.collectAsState()
    val generationStage by vm.generationStage.collectAsState()
    val error by vm.error.collectAsState()

    var showGenerate by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingSingleDelete by remember { mutableStateOf<String?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val names = sets.map { it.name }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == sets.size && sets.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == sets.size && sets.isNotEmpty()) emptySet() else names.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { confirmBulkDelete = true },
                    deleteContentDescription = "Delete selected sets"
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Study")
                            Text("${sets.size} deck${if (sets.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { vm.clearError(); showGenerate = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create study deck")
                        }
                    }
                )
            }
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        if (sets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.School,
                title = "Build your first study deck",
                body = "Turn study material into cards and review what you miss.",
                actionLabel = "Create deck",
                onAction = { vm.clearError(); showGenerate = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("Small reviews, stronger recall", style = MaterialTheme.typography.titleSmall)
                                Text("Answer before revealing each card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(sets, key = { it.name }) { set ->
                    StudySetCard(
                        set = set,
                        selected = set.name in selected,
                        selectionMode = selectionMode,
                        onOpen = { onOpenSet(set.name) },
                        onDelete = { pendingSingleDelete = set.name },
                        onToggleSelected = { selected = if (set.name in selected) selected - set.name else selected + set.name },
                        onEnterSelection = { selectionMode = true; selected = selected + set.name }
                    )
                }
            }
        }
        }
    }

    pendingSingleDelete?.let { name ->
        ConfirmDialog(
            title = "Delete study deck?",
            body = "Permanently delete \"$name\" and all its cards?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteSet(name); pendingSingleDelete = null },
            onDismiss = { pendingSingleDelete = null }
        )
    }

    if (confirmBulkDelete) {
        val count = selected.size
        ConfirmDialog(
            title = "Delete selected decks?",
            body = "Permanently delete $count deck${if (count == 1) "" else "s"} and all their cards?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                selected.forEach(vm::deleteSet)
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
        var focus by remember { mutableStateOf("") }
        var cardCount by remember { mutableFloatStateOf(12f) }
        var cardStyle by remember { mutableStateOf("balanced") }
        AlertDialog(
            onDismissRequest = { if (!generating) showGenerate = false },
            title = { Text("Create a study deck") },
            text = {
                if (generating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp))
                            }
                            Text(generationStage, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                            Text(
                                "This can take a moment when the model needs to load.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                            Text(
                                "Everything stays on this device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Add what you want to learn. You can refine the deck before creating it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoundedTextField(
                        value = setName, onValueChange = { setName = it }, label = "Deck name",
                        singleLine = true, maxLength = ValidationLimits.STUDY_SET_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !generating
                    )
                    BoundedTextField(
                        value = sourceText, onValueChange = { sourceText = it }, label = "Study material",
                        minLines = 5, maxLength = ValidationLimits.STUDY_SOURCE,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !generating
                    )
                    BoundedTextField(
                        value = focus, onValueChange = { focus = it }, label = "Learning goal (optional)",
                        singleLine = true, maxLength = ValidationLimits.STUDY_SET_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !generating
                    )
                    Text("Card style", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("balanced" to "Balanced", "active-recall" to "Active recall", "concept-focused" to "Concepts").forEach { (value, label) ->
                            VervanFilterChip(selected = cardStyle == value, onClick = { cardStyle = value }, label = { Text(label) }, enabled = !generating)
                        }
                    }
                    Text("${cardCount.toInt()} cards", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Slider(value = cardCount, onValueChange = { cardCount = it }, valueRange = 5f..30f, steps = 24, enabled = !generating)
                    error?.let { ValidationMessage(it, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.generateSet(setName.trim(), sourceText, cardCount.toInt(), focus, cardStyle) { showGenerate = false; onOpenSet(setName.trim()) } },
                    enabled = !generating && setName.isNotBlank() && sourceText.isNotBlank()
                ) { Text("Generate deck") }
            },
            dismissButton = { TextButton(onClick = { showGenerate = false }, enabled = !generating) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StudySetCard(
    set: StudySetSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    val progress = if (set.cardCount == 0) 0f else set.masteredCount.toFloat() / set.cardCount
    val (accent, accentContainer, onAccentContainer) = when ((set.name.hashCode() and Int.MAX_VALUE) % 3) {
        0 -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        1 -> Triple(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    val masteryLabel = when {
        set.masteredCount == set.cardCount && set.cardCount > 0 -> "Mastered"
        set.masteredCount > 0 -> "In progress"
        else -> "New deck"
    }
    Card(
        modifier = Modifier.fillMaxWidth().selectableItem(selectionMode, onOpen, onToggleSelected, onEnterSelection),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else accentContainer.copy(alpha = 0.34f)),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else accent.copy(alpha = 0.26f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accentContainer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = accent, modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(set.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Surface(shape = CircleShape, color = accentContainer) {
                            Text(
                                masteryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onAccentContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Text(
                        set.description.ifBlank { "${set.cardCount} cards ready to review" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.14f)
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${set.masteredCount} of ${set.cardCount} mastered", style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(
                        listOfNotNull(
                            set.accuracyPercent?.let { "$it% accuracy" },
                            set.lastStudiedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
                        ).joinToString(" · ").ifBlank { "Not reviewed yet" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!selectionMode) TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
