package com.vervan.chat.ui.workflows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.launch

/** [workflowId] null creates a new workflow; non-null edits (a built-in opened here is
 * saved as a new custom copy — see [WorkflowEditorViewModel.save]). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkflowEditorScreen(workflowId: String?, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WorkflowEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkflowEditorViewModel(app, workflowId) }
    })
    val name by vm.name.collectAsState()
    val description by vm.description.collectAsState()
    val steps by vm.steps.collectAsState()
    val isBuiltIn by vm.isBuiltIn.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workflowId == null) "New workflow" else "Edit workflow") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = "Name",
                maxLength = ValidationLimits.WORKFLOW_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = "Description",
                maxLength = ValidationLimits.WORKFLOW_DESCRIPTION,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (isBuiltIn) {
                Text(
                "Saving creates an editable copy. The built-in stays unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Steps (${steps.size}/${ValidationLimits.WORKFLOW_STEP_COUNT})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    BoundedTextField(
                        value = step,
                        onValueChange = { vm.setStep(index, it) },
                        label = "Step ${index + 1} instruction",
                        maxLength = ValidationLimits.WORKFLOW_STEP,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.moveStep(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move step up")
                    }
                    IconButton(onClick = { vm.moveStep(index, 1) }, enabled = index < steps.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move step down")
                    }
                    IconButton(onClick = { vm.removeStep(index) }, enabled = steps.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove step")
                    }
                }
            }
            OutlinedButton(onClick = { vm.addStep() }, enabled = steps.size < ValidationLimits.WORKFLOW_STEP_COUNT) { Text("Add step") }

            val withinLimits = name.length <= ValidationLimits.WORKFLOW_NAME &&
                description.length <= ValidationLimits.WORKFLOW_DESCRIPTION &&
                steps.all { it.length <= ValidationLimits.WORKFLOW_STEP }
            ResponsiveActions(Modifier.padding(top = 16.dp)) {
                Button(enabled = withinLimits, onClick = { scope.launch { if (vm.save()) onBack() } }) { Text("Save") }
                if (workflowId != null && !isBuiltIn) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                }
            }
        }
        }
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete workflow?",
            body = "\"$name\" and its steps will be permanently deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
