package com.vervan.chat.ui.workflows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(onOpenWorkflow: (String) -> Unit, onNewWorkflow: () -> Unit, onEditWorkflow: (String) -> Unit, onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkflowListViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkflowListViewModel(app) }
    })
    val workflows by vm.workflows.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectableIds = remember(workflows) { workflows.filterNot { it.isBuiltIn }.map { it.id }.toSet() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) SelectionTopBar(
                selectedCount = selected.size,
                allSelected = selectableIds.isNotEmpty() && selectableIds.all { it in selected },
                onToggleSelectAll = { selected = if (selectableIds.all { it in selected }) emptySet() else selectableIds },
                onExit = { selected = emptySet() },
                onDelete = {
                    val ids = selected
                    vm.deleteAll(ids)
                    selected = emptySet()
                    scope.launch { snackbarHostState.showSnackbar("Moved ${ids.size} workflow${if (ids.size == 1) "" else "s"} to the recycle bin") }
                },
                deleteContentDescription = "Delete selected workflows"
            ) else TopAppBar(
                title = { Text("Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { IconButton(onClick = onNewWorkflow) { Icon(Icons.Filled.Add, contentDescription = "New workflow") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          if (workflows.isEmpty()) {
            com.vervan.chat.ui.common.EmptyState(
                icon = Icons.Filled.Widgets,
                title = "No workflows yet",
                body = "Chain prompts into a repeatable multi-step task.",
                modifier = Modifier,
                actionLabel = "New workflow",
                onAction = onNewWorkflow
            )
          } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm)) {
                items(workflows, key = { it.id }) { wf ->
                    WorkflowRow(
                        workflow = wf,
                        onClick = { onOpenWorkflow(wf.id) },
                        onEdit = { onEditWorkflow(wf.id) },
                        selectionMode = selected.isNotEmpty(),
                        selected = wf.id in selected,
                        onToggleSelected = { selected = if (wf.id in selected) selected - wf.id else selected + wf.id },
                        onEnterSelection = { if (!wf.isBuiltIn) selected = selected + wf.id }
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: Workflow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableItem(
            selectionMode, onClick, onToggleSelected, onEnterSelection, selectable = !workflow.isBuiltIn
        ),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else SurfaceRole.Card.cardColors(),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !workflow.isBuiltIn) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            } else if (!selectionMode) {
                IconAffordance(Icons.Filled.Widgets, size = IconAffordanceSize.Default)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = Space.md))
            }
            Column(Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (workflow.description.isNotBlank()) {
                    Text(workflow.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text(
                    "${workflow.steps.size} step${if (workflow.steps.size == 1) "" else "s"}${if (workflow.isBuiltIn) " · Built-in" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!selectionMode) IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit workflow (built-ins save as a copy)")
            }
        }
    }
}
