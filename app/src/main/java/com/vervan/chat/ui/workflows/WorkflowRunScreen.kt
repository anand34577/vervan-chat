package com.vervan.chat.ui.workflows

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkflowRunScreen(workflowId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkflowRunViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkflowRunViewModel(app, workflowId) }
    })
    val workflow by vm.workflow.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val workflowFound by vm.workflowFound.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val knowledgeBases by vm.knowledgeBases.collectAsStateWithLifecycle()
    val sourceKbIds by vm.sourceKbIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    var showSourcePicker by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { vm.readFile(it)?.let { text -> input = text } } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(workflow?.name ?: stringResource(R.string.workflow_run_workflow)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showSourcePicker = true }, enabled = workflowFound && !isLoading) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.chat_sources),
                            tint = if (sourceKbIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.workflow_run_workflow_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.workflow_run_your_workflow_is_safe_retry),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 7, modifier = Modifier.padding(Space.md))
            !workflowFound -> EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.workflow_run_workflow_not_found),
                body = stringResource(R.string.workflow_run_this_workflow_may_have_been),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().padding(vertical = Space.lg)) {
            BoundedTextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.workflow_run_input_text),
                maxLength = ValidationLimits.WORKFLOW_RUN_INPUT,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            ResponsiveActions(Modifier.padding(top = Space.sm)) {
                OutlinedButton(onClick = { pickFile.launch(arrayOf("text/plain", "text/markdown", "application/pdf", "*/*")) }, enabled = !running) {
                    Text(stringResource(R.string.transcription_import_file))
                }
                if (running) {
                    OutlinedButton(onClick = { vm.pauseRun() }) { Text(stringResource(R.string.action_pause)) }
                    OutlinedButton(onClick = { vm.cancelRun() }) { Text(stringResource(R.string.action_cancel)) }
                } else if (paused) {
                    Button(onClick = { vm.resumeRun() }) { Text(stringResource(R.string.workspace_resume)) }
                    OutlinedButton(onClick = { vm.cancelRun() }) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    Button(onClick = { vm.run(input) }, enabled = input.isNotBlank()) {
                        Text(stringResource(R.string.workflow_run_run))
                    }
                }
            }
            error?.let { ErrorCard("Workflow couldn't continue", it, Modifier.padding(top = Space.sm)) }

            // vertical stepper — the current (or next-to-run) step stays expanded with its
            // live output; finished steps collapse to a one-line summary, and steps that
            // haven't started yet show as a dimmed pending row.
            val currentIndex = steps.indexOfFirst { !it.done }.let { if (it < 0) steps.lastIndex else it }
            LazyColumn(Modifier.padding(top = Space.md)) {
                items(steps.size) { index ->
                    val step = steps[index]
                    Card(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                        Column(Modifier.padding(Space.md)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (step.done) {
                                    Icon(
                                        Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.action_done),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).padding(end = Space.xs)
                                    )
                                }
                                Text(
                                    "Step ${index + 1}: ${step.instruction.take(80)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (index > currentIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (index == currentIndex) {
                                if (step.output.isNotBlank()) {
                                    MarkdownLiteText(step.output, modifier = Modifier.padding(top = Space.xs))
                                } else if (running && !step.done) {
                                    CircularProgressIndicator(Modifier.padding(top = Space.sm).size(16.dp), strokeWidth = 2.dp)
                                }
                            } else if (step.done) {
                                Text(
                                    step.output.take(80).ifBlank { "(empty)" }, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.xs)
                                )
                            }
                            if (step.done && index == steps.lastIndex) {
                                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                                    TextButton(onClick = { vm.saveAsLibraryOutput(step.output) }) { Text(stringResource(R.string.workflow_run_save_to_library)) }
                                    TextButton(onClick = { vm.saveAsNote(step.output) }) { Text(stringResource(R.string.workflow_run_save_as_note)) }
                                }
                            }
                        }
                    }
                }
            }
            }
      }
    }

    if (showSourcePicker) {
        var selected by remember { mutableStateOf(sourceKbIds) }
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = { Text(stringResource(R.string.workflow_run_pull_sources_into_this_run)) },
            text = {
                Column {
                    if (knowledgeBases.isEmpty()) {
                        Text(stringResource(R.string.workspace_no_knowledge))
                    }
                    knowledgeBases.forEach { kb ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(kb.id),
                                onCheckedChange = { checked -> selected = if (checked) selected + kb.id else selected - kb.id }
                            )
                            OverflowTooltipText(
                                text = kb.name,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setSourceKbIds(selected); showSourcePicker = false }) { Text(stringResource(R.string.action_done)) } },
            dismissButton = { TextButton(onClick = { showSourcePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
}
