package com.vervan.chat.ui.workflows

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
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
    val workflow by vm.workflow.collectAsState()
    val steps by vm.steps.collectAsState()
    val running by vm.running.collectAsState()
    val paused by vm.paused.collectAsState()
    val error by vm.error.collectAsState()
    val knowledgeBases by vm.knowledgeBases.collectAsState()
    val sourceKbIds by vm.sourceKbIds.collectAsState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var showSourcePicker by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { vm.readFile(it)?.let { text -> input = text } } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workflow?.name ?: "Workflow") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showSourcePicker = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sources",
                            tint = if (sourceKbIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
       androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding().padding(vertical = Space.lg)) {
            BoundedTextField(
                value = input,
                onValueChange = { input = it },
                label = "Input text",
                maxLength = ValidationLimits.WORKFLOW_RUN_INPUT,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            ResponsiveActions(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { pickFile.launch(arrayOf("text/plain", "text/markdown", "application/pdf", "*/*")) }, enabled = !running) {
                    Text("Import file")
                }
                if (running) {
                    OutlinedButton(onClick = { vm.pauseRun() }) { Text("Pause") }
                    OutlinedButton(onClick = { vm.cancelRun() }) { Text("Cancel") }
                } else if (paused) {
                    Button(onClick = { vm.resumeRun() }) { Text("Resume") }
                    OutlinedButton(onClick = { vm.cancelRun() }) { Text("Cancel") }
                } else {
                    Button(onClick = { vm.run(input) }, enabled = input.isNotBlank()) {
                        Text("Run")
                    }
                }
            }
            error?.let { ErrorCard("Workflow couldn't continue", it, Modifier.padding(top = 8.dp)) }

            // §7.5 vertical stepper — the current (or next-to-run) step stays expanded with its
            // live output; finished steps collapse to a one-line summary, and steps that
            // haven't started yet show as a dimmed pending row.
            val currentIndex = steps.indexOfFirst { !it.done }.let { if (it < 0) steps.lastIndex else it }
            LazyColumn(Modifier.padding(top = 12.dp)) {
                items(steps.size) { index ->
                    val step = steps[index]
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (step.done) {
                                    Icon(
                                        Icons.Filled.CheckCircle, contentDescription = "Done",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
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
                                    CircularProgressIndicator(Modifier.padding(top = 8.dp).size(16.dp), strokeWidth = 2.dp)
                                }
                            } else if (step.done) {
                                Text(
                                    step.output.take(80).ifBlank { "(empty)" }, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (step.done && index == steps.lastIndex) {
                                ResponsiveActions(Modifier.padding(top = 8.dp)) {
                                    TextButton(onClick = { vm.saveAsLibraryOutput(step.output) }) { Text("Save to library") }
                                    TextButton(onClick = { vm.saveAsNote(step.output) }) { Text("Save as note") }
                                }
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
            title = { Text("Pull sources into this run") },
            text = {
                Column {
                    if (knowledgeBases.isEmpty()) {
                        Text("No knowledge bases yet. Import a document in Knowledge.")
                    }
                    knowledgeBases.forEach { kb ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(kb.id),
                                onCheckedChange = { checked -> selected = if (checked) selected + kb.id else selected - kb.id }
                            )
                            Text(kb.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setSourceKbIds(selected); showSourcePicker = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showSourcePicker = false }) { Text("Cancel") } }
        )
    }
}
