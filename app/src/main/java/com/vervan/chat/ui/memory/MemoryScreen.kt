package com.vervan.chat.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanContentWidth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit = {}, onOpenSuggestions: () -> Unit = {}, highlightMemoryId: String? = null) {
    val app = LocalContext.current.applicationContext as VervanApp
    val resources = LocalResources.current
    val memories by app.container.db.memoryDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingSuggestions by app.container.db.memorySuggestionDao().observePendingCount().collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // null = closed; a blank Memory = adding; an existing one = editing it.
    var editing by remember { mutableStateOf<Memory?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<Set<String>?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.action_undo)
    val filtered = remember(memories, query) {
        if (query.isBlank()) memories else memories.filter { it.text.contains(query, true) || it.key?.contains(query, true) == true }
    }
    val visibleSelected = selected intersect filtered.map { it.id }.toSet()
    fun exitSelection() { selectionMode = false; selected = emptySet() }
    BackHandler(enabled = selectionMode) { exitSelection() }

    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            app.container.db.memoryDao().softDeleteMany(ids.toList())
            val message = resources.getQuantityString(R.plurals.memory_removed, ids.size, ids.size)
            if (snackbarHostState.showSnackbar(message, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                app.container.db.memoryDao().restoreMany(ids.toList())
            }
        }
    }

    val listState = rememberLazyListState()
    // Rows sit after the hero, the snapshot card, the section header and (when shown) search.
    val rowsOffset = 3 + if (memories.size > SEARCH_THRESHOLD) 1 else 0
    LaunchedEffect(memories, highlightMemoryId) {
        if (highlightMemoryId != null) {
            val index = memories.indexOfFirst { it.id == highlightMemoryId }
            if (index >= 0) listState.animateScrollToItem(index + rowsOffset)
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = visibleSelected.size,
                    allSelected = filtered.isNotEmpty() && visibleSelected.size == filtered.size,
                    onToggleSelectAll = {
                        selected = if (filtered.isNotEmpty() && visibleSelected.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
                    },
                    onExit = { exitSelection() },
                    onDelete = { pendingBulkDelete = visibleSelected },
                    deleteContentDescription = stringResource(R.string.library_delete),
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.memory_memory)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    },
                    actions = {
                        IconButton(onClick = onOpenSuggestions) {
                            BadgedBox(badge = { if (pendingSuggestions > 0) Badge { Text(stringResource(R.string.memory_arg1, pendingSuggestions)) } }) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = stringResource(R.string.memory_suggested_title))
                            }
                        }
                        if (memories.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.memory_select)) },
                                        leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                        onClick = { menuOpen = false; selectionMode = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.memory_forget_all), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; pendingBulkDelete = memories.map { it.id }.toSet() },
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { editing = Memory(text = "") }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.memory_add_memory)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = VervanContentWidth.standard) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = Space.sm, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item {
                FeatureHero(
                    eyebrow = "Private and approved",
                    title = stringResource(R.string.memory_what_the_assistant_remembers),
                    body = stringResource(R.string.memory_review_details_saved_for_future),
                    icon = Icons.Filled.Lightbulb
                )
            }
            // Zeros add nothing on an empty screen; the empty state below says what to do.
            if (memories.isNotEmpty()) {
                item {
                    MemorySnapshotCard(
                        total = memories.size,
                        enabled = memories.count { it.enabled }
                    )
                }
                item {
                    VervanSectionHeader(
                        title = stringResource(R.string.memory_saved_memories),
                        count = memories.size,
                        topPadding = 0.dp,
                        bottomPadding = 0.dp
                    )
                }
                if (memories.size > SEARCH_THRESHOLD) {
                    item { VervanSearchField(query, { query = it }, stringResource(R.string.memory_search)) }
                }
            }
            if (memories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Lightbulb,
                        title = stringResource(R.string.memory_no_memories_saved),
                        body = stringResource(R.string.memory_save_a_preference_fact_or),
                        actionLabel = stringResource(R.string.memory_add_memory),
                        onAction = { editing = Memory(text = "") }
                    )
                }
            } else if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.memory_no_match),
                        body = stringResource(R.string.tool_history_no_match_body),
                        actionLabel = stringResource(R.string.action_clear),
                        onAction = { query = "" }
                    )
                }
            }
            items(filtered, key = { it.id }) { memory ->
                val highlighted = memory.id == highlightMemoryId
                val isSelected = memory.id in visibleSelected
                Card(
                    Modifier.fillMaxWidth(),
                    colors = when {
                        isSelected -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        highlighted -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else -> SurfaceRole.Card.cardColors()
                    },
                    border = when {
                        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                        highlighted -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        else -> SurfaceRole.Card.border()
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        Modifier
                            .selectableItem(
                                selectionMode = selectionMode,
                                onClick = { editing = memory },
                                onToggleSelected = { selected = if (memory.id in selected) selected - memory.id else selected + memory.id },
                                onEnterSelection = { selectionMode = true; selected = selected + memory.id },
                            )
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = { selected = if (it) selected + memory.id else selected - memory.id })
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                memory.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (memory.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val subtitle = listOfNotNull(
                                memory.scope.name.lowercase().replaceFirstChar { it.titlecase() },
                                memory.key?.let { "key: $it" },
                                if (!memory.enabled) "Paused" else null,
                            ).joinToString(" · ")
                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!selectionMode) {
                            com.vervan.chat.ui.common.LabeledSwitch(label = "Use memory: " + memory.text.take(60), checked = memory.enabled, onCheckedChange = { checked ->
                                scope.launch { app.container.db.memoryDao().update(memory.copy(enabled = checked)) }
                            })
                        }
                    }
                }
            }
        }
        }
    }

    editing?.let { target ->
        val isNew = target.text.isEmpty() && memories.none { it.id == target.id }
        var text by rememberSaveable(target.id) { mutableStateOf(target.text) }
        var key by rememberSaveable(target.id) { mutableStateOf(target.key.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isNew) stringResource(R.string.memory_new_memory) else stringResource(R.string.memory_edit_memory)) },
            text = {
                Column {
                    BoundedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = stringResource(R.string.memory_e_g_prefers_concise_answers),
                        maxLength = ValidationLimits.MEMORY_TEXT
                    )
                    BoundedTextField(
                        value = key, onValueChange = { key = it },
                        placeholder = stringResource(R.string.memory_key_optional_e_g_tone),
                        singleLine = true,
                        maxLength = ValidationLimits.MEMORY_KEY,
                        supportingText = stringResource(R.string.memory_using_the_same_key_replaces),
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && text.length <= ValidationLimits.MEMORY_TEXT && key.length <= ValidationLimits.MEMORY_KEY,
                    onClick = {
                        val trimmedKey = key.trim().ifBlank { null }
                        scope.launch {
                            val sameKey = trimmedKey?.let {
                                app.container.db.memoryDao().findByKey(it, target.scope, target.scopeRefId)
                            }?.takeIf { it.id != target.id }
                            if (!isNew && sameKey != null) {
                                // Editing onto another memory's key: that one is replaced, as the hint says.
                                app.container.db.memoryDao().softDeleteMany(listOf(sameKey.id))
                            }
                            val base = if (isNew) (sameKey ?: target) else target
                            app.container.memoryRepository.upsert(base.copy(text = text.trim(), key = trimmedKey))
                        }
                        editing = null
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = { editing = null; remove(setOf(target.id)) }) {
                            Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }

    pendingBulkDelete?.let { ids ->
        val all = ids.size == memories.size
        ConfirmDialog(
            title = if (all) stringResource(R.string.memory_forget_all_title) else stringResource(R.string.memory_delete_memory),
            body = resources.getQuantityString(R.plurals.memory_bulk_delete_body, ids.size, ids.size),
            confirmLabel = if (all) stringResource(R.string.memory_forget_all) else stringResource(R.string.library_delete),
            destructive = true,
            onConfirm = {
                pendingBulkDelete = null
                exitSelection()
                remove(ids)
            },
            onDismiss = { pendingBulkDelete = null }
        )
    }
}

private const val SEARCH_THRESHOLD = 5

@Composable
private fun MemorySnapshotCard(total: Int, enabled: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            MemoryMetric(total.toString(), "Saved", Modifier.weight(1f))
            MemoryMetric(enabled.toString(), "Enabled", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            MemoryMetric((total - enabled).coerceAtLeast(0).toString(), "Paused", Modifier.weight(1f), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    androidx.compose.foundation.layout.Column(
        modifier.padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
