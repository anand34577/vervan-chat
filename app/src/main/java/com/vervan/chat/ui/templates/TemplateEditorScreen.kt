package com.vervan.chat.ui.templates

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(templateId: String?, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: TemplateEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { TemplateEditorViewModel(app, templateId) }
    })
    val name by vm.name.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
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
                title = { Text(if (templateId == null) "New template" else "Edit template") },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    if (templateId != null && !isBuiltIn) {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text(stringResource(R.string.library_delete)) }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.template_editor_template_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.template_editor_your_other_templates_are_safe),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 5, modifier = Modifier.padding(Space.md))
            templateId != null && !recordFound -> EmptyState(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.template_editor_template_not_found),
                body = stringResource(R.string.template_editor_this_template_may_have_been),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            if (isBuiltIn) {
                Text(
                stringResource(R.string.persona_copy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = stringResource(R.string.template_editor_slash_command_name),
                prefix = "/", maxLength = ValidationLimits.TEMPLATE_COMMAND_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Space.md)
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = stringResource(R.string.workspace_description),
                maxLength = ValidationLimits.TEMPLATE_DESCRIPTION,
                modifier = Modifier.fillMaxWidth().padding(top = Space.md)
            )
            BoundedTextField(
                value = body, onValueChange = vm::setBody, label = stringResource(R.string.template_editor_template_body),
                maxLength = ValidationLimits.TEMPLATE_BODY, minLines = 5,
                supportingText = stringResource(R.string.template_editor_use_input_where_the_text),
                modifier = Modifier.fillMaxWidth().padding(top = Space.md)
            )
            if (name.isNotBlank()) {
                Text(
                    stringResource(R.string.template_editor_preview_arg1_optional_input, name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            val withinLimits = name.length <= ValidationLimits.TEMPLATE_COMMAND_NAME &&
                description.length <= ValidationLimits.TEMPLATE_DESCRIPTION &&
                body.length <= ValidationLimits.TEMPLATE_BODY
            Button(
                enabled = withinLimits,
                onClick = { scope.launch { if (vm.save()) onBack() } },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
            ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.template_editor_delete_template),
            body = stringResource(R.string.template_editor_arg1_will_move_to_the, name),
            confirmLabel = stringResource(R.string.library_delete),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
