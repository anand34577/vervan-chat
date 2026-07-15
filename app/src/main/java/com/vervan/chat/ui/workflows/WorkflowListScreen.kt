package com.vervan.chat.ui.workflows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Workflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(onOpenWorkflow: (String) -> Unit, onNewWorkflow: () -> Unit, onEditWorkflow: (String) -> Unit, onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkflowListViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkflowListViewModel(app) }
    })
    val workflows by vm.workflows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflows") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { IconButton(onClick = onNewWorkflow) { Icon(Icons.Filled.Add, contentDescription = "New workflow") } }
            )
        }
    ) { padding ->
        if (workflows.isEmpty()) {
            com.vervan.chat.ui.common.EmptyState(
                icon = Icons.Filled.Widgets,
                title = "No workflows yet",
                body = "Chain prompts into a repeatable multi-step task.",
                modifier = Modifier.padding(padding),
                actionLabel = "New workflow",
                onAction = onNewWorkflow
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
                items(workflows, key = { it.id }) { wf ->
                    WorkflowRow(wf, onClick = { onOpenWorkflow(wf.id) }, onEdit = { onEditWorkflow(wf.id) })
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(workflow: Workflow, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (workflow.description.isNotBlank()) {
                    Text(workflow.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("${workflow.steps.size} step(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit workflow (built-ins save as a copy)")
            }
        }
    }
}
