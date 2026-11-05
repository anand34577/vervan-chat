package com.vervan.chat.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.relativeTime
import com.vervan.chat.ui.common.selectableItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanTopAppBar
import com.vervan.chat.ui.common.copyText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolRunHistoryScreen(
    onBack: () -> Unit,
    onContinueInChat: (String) -> Unit,
    onRerun: (String) -> Unit,
    highlightId: String? = null,
) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val vm: ToolRunHistoryViewModel = viewModel(factory = viewModelFactory { initializer { ToolRunHistoryViewModel(app) } })
    val runs by vm.runs.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by remember(highlightId) { mutableStateOf(highlightId) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveSuccessMessage = stringResource(R.string.tool_history_save_success)
    val saveErrorMessage = stringResource(R.string.tool_history_save_error)
    val deleteErrorMessage = stringResource(R.string.tool_history_delete_error)
    val undoLabel = stringResource(R.string.action_undo)
    val filtered = remember(runs, query) {
        if (query.isBlank()) runs else runs.filter {
            it.toolName.contains(query, true) || it.input.contains(query, true) || it.output.contains(query, true)
        }
    }
    // Drop ids that vanished (deleted elsewhere, filtered out) so the count never lies.
    val visibleSelected = selected intersect filtered.map { it.id }.toSet()
    fun exitSelection() { selectionMode = false; selected = emptySet() }
    BackHandler(enabled = selectionMode) { exitSelection() }

    fun remove(ids: Set<String>) {
        scope.launch {
            val result = vm.delete(ids).await()
            if (result.isFailure) {
                snackbarHostState.showSnackbar(deleteErrorMessage)
                return@launch
            }
            val message = localResources.getQuantityString(R.plurals.tool_history_removed, ids.size, ids.size)
            if (snackbarHostState.showSnackbar(message, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                vm.restore(ids)
            }
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
                    onDelete = { pendingDelete = visibleSelected },
                    deleteContentDescription = stringResource(R.string.action_remove),
                )
            } else {
                VervanTopAppBar(
                    title = { Text(stringResource(R.string.tool_run_history_run_history)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    actions = {
                        if (runs.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tool_history_select)) },
                                        leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                        onClick = { menuOpen = false; selectionMode = true; expandedId = null },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tool_history_clear_all), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; pendingDelete = runs.map { it.id }.toSet() },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            Column(Modifier.fillMaxSize()) {
                if (runs.isNotEmpty() || query.isNotBlank()) {
                    VervanSearchField(query, { query = it }, stringResource(R.string.tool_history_search), Modifier.padding(vertical = Space.sm))
                }
                if (error != null) {
                    OperationErrorCard(
                        title = stringResource(R.string.tool_history_unavailable),
                        message = error ?: stringResource(R.string.tool_history_unavailable_message),
                        recovery = stringResource(R.string.tool_history_unavailable_recovery),
                        modifier = Modifier.padding(top = Space.md),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = vm::retry
                    )
                } else if (isLoading) {
                    LoadingSkeletonList(rows = 6, modifier = Modifier.padding(top = Space.md))
                } else if (filtered.isEmpty() && query.isNotBlank()) {
                    EmptyState(
                        Icons.Filled.SearchOff,
                        stringResource(R.string.tool_history_no_match),
                        stringResource(R.string.tool_history_no_match_body),
                        actionLabel = stringResource(R.string.action_clear),
                        onAction = { query = "" },
                    )
                } else if (filtered.isEmpty()) {
                    EmptyState(Icons.Filled.History, stringResource(R.string.tool_history_empty_title), stringResource(R.string.tool_history_empty_body))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                        contentPadding = PaddingValues(bottom = Space.xl),
                    ) {
                        items(filtered, key = { it.id }) { run ->
                            ToolRunCard(
                                run = run,
                                expanded = !selectionMode && expandedId == run.id,
                                selectionMode = selectionMode,
                                selected = run.id in visibleSelected,
                                onToggle = { expandedId = if (expandedId == run.id) null else run.id },
                                onToggleSelected = { selected = if (run.id in selected) selected - run.id else selected + run.id },
                                onEnterSelection = { selectionMode = true; expandedId = null; selected = selected + run.id },
                                onCopy = {
                                    val content = run.output.ifBlank { run.input }
                                    context.getSystemService(android.content.ClipboardManager::class.java)
                                        .copyText(content, scope, run.toolName)
                                    scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_copied)) }
                                },
                                onSave = {
                                    scope.launch {
                                        val result = vm.save(run).await()
                                        snackbarHostState.showSnackbar(if (result.isSuccess) saveSuccessMessage else saveErrorMessage)
                                    }
                                },
                                onContinue = { onContinueInChat(run.output.ifBlank { run.input }) },
                                onShare = {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, run.toolName)
                                        putExtra(Intent.EXTRA_TEXT, run.output.ifBlank { run.input })
                                    }, "Share result"))
                                },
                                onRerun = { onRerun(run.toolRoute) },
                                // One run goes straight away with Undo; only bulk removal asks first.
                                onDelete = { remove(setOf(run.id)) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { ids ->
        val clearingAll = ids.size == runs.size
        ConfirmDialog(
            title = if (clearingAll) stringResource(R.string.tool_history_clear_all_title) else stringResource(R.string.tool_run_history_remove_this_run),
            body = localResources.getQuantityString(R.plurals.tool_history_clear_all_body, ids.size, ids.size),
            confirmLabel = if (clearingAll) stringResource(R.string.action_clear) else stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                exitSelection()
                remove(ids)
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolRunCard(
    run: ToolRun,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onShare: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else SurfaceRole.Card.cardColors(),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else SurfaceRole.Card.border(),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .selectableItem(selectionMode, onClick = onToggle, onToggleSelected = onToggleSelected, onEnterSelection = onEnterSelection)
                .padding(Space.md)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() }, modifier = Modifier.padding(end = Space.sm).size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(run.toolName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        run.state.name.lowercase().replaceFirstChar { it.titlecase() } +
                            (run.modelName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (run.state) {
                            ToolRunState.FAILED -> MaterialTheme.colorScheme.error
                            ToolRunState.RUNNING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(relativeTime(run.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!selectionMode) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.tool_run_history_collapse_run) else stringResource(R.string.tool_run_history_expand_run),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.xs).size(20.dp)
                    )
                }
            }
            Text(
                runPreview(run.output.ifBlank { run.errorMessage ?: run.input }),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) 20 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm),
            )
            if (expanded) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    RunAction(Icons.Filled.Replay, stringResource(R.string.tool_run_history_re_run), onRerun)
                    RunAction(Icons.Filled.PlayArrow, "Continue", onContinue)
                    RunAction(Icons.Filled.ContentCopy, "Copy", onCopy)
                    RunAction(Icons.Filled.Share, "Share", onShare)
                    RunAction(Icons.Filled.Save, "Save", onSave)
                    RunAction(Icons.Filled.Delete, stringResource(R.string.action_remove), onDelete)
                }
            }
        }
    }
}

@Composable
private fun RunAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
}


/** What a run's card shows: code fences dropped, and a JSON result (a quiz, a scan) summarised
 *  as its first readable value instead of raw brackets. Copy/Share still use the full output. */
internal fun runPreview(raw: String): String {
    val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    if (!(text.startsWith("[") || text.startsWith("{"))) return text
    val json = runCatching { org.json.JSONTokener(text).nextValue() }.getOrNull() ?: return text
    fun firstString(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is org.json.JSONObject -> listOf("question", "title", "name", "merchant", "text").firstNotNullOfOrNull { v.optString(it).takeIf { s -> s.isNotBlank() } }
            ?: v.keys().asSequence().firstNotNullOfOrNull { firstString(v.opt(it)) }
        is org.json.JSONArray -> (0 until v.length()).asSequence().firstNotNullOfOrNull { firstString(v.opt(it)) }
        else -> null
    }
    val count = (json as? org.json.JSONArray)?.length()
    val first = firstString(json).orEmpty()
    return if (count != null) "$count items · $first" else first.ifBlank { text }
}
