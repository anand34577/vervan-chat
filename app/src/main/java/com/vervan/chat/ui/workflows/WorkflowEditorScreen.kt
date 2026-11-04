package com.vervan.chat.ui.workflows

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.filled.Description
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
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
    val name by vm.name.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val isBuiltIn by vm.isBuiltIn.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val recordFound by vm.recordFound.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val guardedBack = com.vervan.chat.ui.common.rememberUnsavedChangesGuard(vm::hasUnsavedChanges, onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workflowId == null) "New workflow" else "Edit workflow") },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.workflow_editor_workflow_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.workflow_editor_your_other_workflows_are_safe),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
            workflowId != null && !recordFound -> EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.workflow_editor_workflow_not_found),
                body = stringResource(R.string.workflow_editor_this_workflow_may_have_been),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = stringResource(R.string.workspace_name),
                maxLength = ValidationLimits.WORKFLOW_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = stringResource(R.string.workspace_description),
                maxLength = ValidationLimits.WORKFLOW_DESCRIPTION,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )
            if (isBuiltIn) {
                Text(
                stringResource(R.string.persona_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            Text(
                stringResource(R.string.workflow_editor_steps_arg1_arg2, steps.size, ValidationLimits.WORKFLOW_STEP_COUNT),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Space.lg, bottom = Space.xs)
            )
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.Top) {
                    BoundedTextField(
                        value = step,
                        onValueChange = { vm.setStep(index, it) },
                        label = stringResource(R.string.workflow_editor_step_arg1_instruction, index + 1),
                        maxLength = ValidationLimits.WORKFLOW_STEP,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.moveStep(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.workflow_editor_move_step_up))
                    }
                    IconButton(onClick = { vm.moveStep(index, 1) }, enabled = index < steps.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.workflow_editor_move_step_down))
                    }
                    IconButton(onClick = { vm.removeStep(index) }, enabled = steps.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workflow_editor_remove_step))
                    }
                }
            }
            OutlinedButton(onClick = { vm.addStep() }, enabled = steps.size < ValidationLimits.WORKFLOW_STEP_COUNT) { Text(stringResource(R.string.workflow_editor_add_step)) }

            val withinLimits = name.length <= ValidationLimits.WORKFLOW_NAME &&
                description.length <= ValidationLimits.WORKFLOW_DESCRIPTION &&
                steps.all { it.length <= ValidationLimits.WORKFLOW_STEP }
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                Button(enabled = withinLimits, onClick = { scope.launch { if (vm.save()) onBack() } }) { Text(stringResource(R.string.action_save)) }
                if (workflowId != null && !isBuiltIn) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text(stringResource(R.string.library_delete)) }
                }
            }
            }
        }
    }
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.workflow_editor_delete_workflow),
            body = stringResource(R.string.workflow_editor_arg1_and_its_steps_will, name),
            confirmLabel = stringResource(R.string.library_delete),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
