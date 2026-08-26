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
import androidx.compose.material.icons.filled.Description
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
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
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: WorkflowEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkflowEditorViewModel(app, workflowId) }
    })
    val name by vm.name.collectAsState()
    val description by vm.description.collectAsState()
    val steps by vm.steps.collectAsState()
    val isBuiltIn by vm.isBuiltIn.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val recordFound by vm.recordFound.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workflowId == null) "New workflow" else "Edit workflow") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.ui_workfloweditorscreen_88_workflow_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.ui_workflow_editor_lookup_recovery),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
            workflowId != null && !recordFound -> EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.ui_workfloweditorscreen_98_workflow_not_found),
                body = stringResource(R.string.ui_workfloweditorscreen_99_this_workflow_may_have_been_deleted_or_moved),
                modifier = Modifier.fillMaxSize(),
                centered = true,
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = stringResource(R.string.workspace_name),
                required = true,
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
                "Saving creates an editable copy. The built-in stays unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            Text(
                stringResource(R.string.ui_workflow_steps_count, steps.size, ValidationLimits.WORKFLOW_STEP_COUNT),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Space.lg, bottom = Space.xs)
            )
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.Top) {
                    BoundedTextField(
                        value = step,
                        onValueChange = { vm.setStep(index, it) },
                        label = stringResource(R.string.ui_workflow_step_instruction, index + 1),
                        required = true,
                        maxLength = ValidationLimits.WORKFLOW_STEP,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.moveStep(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.ui_workfloweditorscreen_140_move_step_up))
                    }
                    IconButton(onClick = { vm.moveStep(index, 1) }, enabled = index < steps.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.ui_workfloweditorscreen_143_move_step_down))
                    }
                    IconButton(onClick = { vm.removeStep(index) }, enabled = steps.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_workfloweditorscreen_146_remove_step))
                    }
                }
            }
            OutlinedButton(onClick = { vm.addStep() }, enabled = steps.size < ValidationLimits.WORKFLOW_STEP_COUNT) { Text(stringResource(R.string.ui_workfloweditorscreen_150_add_step)) }

            val withinLimits = name.length <= ValidationLimits.WORKFLOW_NAME &&
                description.length <= ValidationLimits.WORKFLOW_DESCRIPTION &&
                steps.all { it.length <= ValidationLimits.WORKFLOW_STEP }
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                Button(enabled = withinLimits, onClick = {
                    scope.launch {
                        if (vm.save()) onBack()
                        else android.widget.Toast.makeText(context, vm.saveError.value ?: "Complete the required fields before saving.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.action_save)) }
                if (workflowId != null && !isBuiltIn) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text(stringResource(R.string.action_delete)) }
                }
            }
            }
        }
    }
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.ui_workfloweditorscreen_172_delete_workflow),
            body = stringResource(R.string.ui_workflow_delete_body, name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
