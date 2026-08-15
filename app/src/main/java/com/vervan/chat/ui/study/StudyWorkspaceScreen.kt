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
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ModernistMetricStrip
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.ValidationMessage
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

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
                    deleteContentDescription = stringResource(R.string.study_delete_selected)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.study_title))
                            Text(if (sets.size == 1) stringResource(R.string.study_deck_count_one) else stringResource(R.string.study_deck_count_many, sets.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                    actions = {
                        IconButton(onClick = { vm.clearError(); showGenerate = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.study_create_deck))
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
                title = stringResource(R.string.study_empty_title),
                body = stringResource(R.string.study_empty_body),
                actionLabel = stringResource(R.string.study_create),
                onAction = { vm.clearError(); showGenerate = true },
                modifier = Modifier.fillMaxSize(),
                centered = true
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Space.md, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item {
                    ModernistScreenHeader(
                        eyebrow = stringResource(R.string.ui_studyworkspacescreen_137_active_recall),
                        title = stringResource(R.string.study_recall_title),
                        body = stringResource(R.string.study_recall_body),
                        trailing = { ModernistTag("${sets.size} DECKS", active = sets.isNotEmpty()) }
                    )
                    ModernistMetricStrip(
                        metrics = listOf(
                            "DECKS" to sets.size.toString(),
                            "CARDS" to sets.sumOf { it.cardCount }.toString(),
                            "MODE" to "RECALL"
                        ),
                        modifier = Modifier.padding(top = Space.md)
                    )
                    StudySnapshotCard(sets, modifier = Modifier.padding(top = Space.md, bottom = Space.xs))
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
            title = stringResource(R.string.study_delete_title),
            body = stringResource(R.string.study_delete_body, name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.deleteSet(name); pendingSingleDelete = null },
            onDismiss = { pendingSingleDelete = null }
        )
    }

    if (confirmBulkDelete) {
        val count = selected.size
        ConfirmDialog(
            title = stringResource(R.string.study_delete_many_title),
            body = stringResource(R.string.study_delete_many_body, count),
            confirmLabel = stringResource(R.string.action_delete),
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
            title = { Text(stringResource(R.string.study_create_title)) },
            text = {
                if (generating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm)
                    ) {
                        Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(Space.md))
                            }
                            Text(generationStage, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.md))
                            Text(
                                stringResource(R.string.study_generation_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.lg))
                            Text(
                                stringResource(R.string.study_privacy_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.sm)
                            )
                        }
                    }
                } else Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.study_form_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoundedTextField(
                        value = setName, onValueChange = { setName = it }, label = stringResource(R.string.study_deck_name),
                        singleLine = true, maxLength = ValidationLimits.STUDY_SET_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md), enabled = !generating
                    )
                    BoundedTextField(
                        value = sourceText, onValueChange = { sourceText = it }, label = stringResource(R.string.study_material),
                        minLines = 5, maxLength = ValidationLimits.STUDY_SOURCE,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm), enabled = !generating
                    )
                    BoundedTextField(
                        value = focus, onValueChange = { focus = it }, label = stringResource(R.string.study_goal),
                        singleLine = true, maxLength = ValidationLimits.STUDY_SET_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm), enabled = !generating
                    )
                    Text(stringResource(R.string.study_card_style), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.md))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        val cardStyles = listOf(
                            "balanced" to stringResource(R.string.persona_balanced),
                            "active-recall" to stringResource(R.string.study_active_recall),
                            "concept-focused" to stringResource(R.string.study_concepts)
                        )
                        cardStyles.forEach { (value, label) ->
                            VervanFilterChip(selected = cardStyle == value, onClick = { cardStyle = value }, label = { Text(label) }, enabled = !generating)
                        }
                    }
                    Text(stringResource(R.string.study_cards_count, cardCount.toInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.sm))
                    Slider(value = cardCount, onValueChange = { cardCount = it }, valueRange = 5f..30f, steps = 24, enabled = !generating)
                    error?.let { ValidationMessage(it, modifier = Modifier.padding(top = Space.sm)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.generateSet(setName.trim(), sourceText, cardCount.toInt(), focus, cardStyle) { showGenerate = false; onOpenSet(setName.trim()) } },
                    enabled = !generating && setName.isNotBlank() && sourceText.isNotBlank()
                ) { Text(stringResource(R.string.study_generate)) }
            },
            dismissButton = { TextButton(onClick = { showGenerate = false }, enabled = !generating) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun StudySnapshotCard(sets: List<StudySetSummary>, modifier: Modifier = Modifier) {
    val totalCards = sets.sumOf { it.cardCount }
    val masteredCards = sets.sumOf { it.masteredCount }
    val remainingCards = (totalCards - masteredCards).coerceAtLeast(0)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            StudyMetric(sets.size.toString(), stringResource(R.string.study_decks), Modifier.weight(1f))
            StudyMetric(totalCards.toString(), stringResource(R.string.study_cards), Modifier.weight(1f))
            StudyMetric(
                if (remainingCards == 0 && totalCards > 0) stringResource(R.string.study_done) else remainingCards.toString(),
                if (remainingCards == 0 && totalCards > 0) stringResource(R.string.study_mastered) else stringResource(R.string.study_to_review),
                Modifier.weight(1f),
                accent = if (remainingCards == 0 && totalCards > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun StudyMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    Column(modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        set.masteredCount == set.cardCount && set.cardCount > 0 -> stringResource(R.string.study_mastered)
        set.masteredCount > 0 -> stringResource(R.string.study_in_progress)
        else -> stringResource(R.string.study_new_deck)
    }
    val cardsReadyLabel = stringResource(R.string.study_cards_ready, set.cardCount)
    val accuracyLabel = set.accuracyPercent?.let { stringResource(R.string.study_accuracy, it) }
    Card(
        modifier = Modifier.fillMaxWidth().selectableItem(selectionMode, onOpen, onToggleSelected, onEnterSelection),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else accentContainer.copy(alpha = 0.34f)),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else accent.copy(alpha = 0.26f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accentContainer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = accent, modifier = Modifier.padding(Space.md))
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OverflowTooltipText(
                            text = set.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = CircleShape,
                            color = accentContainer,
                            modifier = Modifier.padding(start = Space.sm),
                        ) {
                            Text(
                                masteryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = onAccentContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs)
                            )
                        }
                    }
                    Text(
                        set.description.ifBlank { cardsReadyLabel },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                color = accent,
                trackColor = accent.copy(alpha = 0.14f)
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.study_mastery, set.masteredCount, set.cardCount), style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(
                        listOfNotNull(
                            accuracyLabel,
                            set.lastStudiedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
                        ).joinToString(" · ").ifBlank { stringResource(R.string.study_not_reviewed) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!selectionMode) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.padding(start = Space.sm),
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
