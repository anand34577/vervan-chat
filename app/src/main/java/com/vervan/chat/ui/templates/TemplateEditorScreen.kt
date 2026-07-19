package com.vervan.chat.ui.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
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
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(templateId: String?, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: TemplateEditorViewModel = viewModel(factory = viewModelFactory {
        initializer { TemplateEditorViewModel(app, templateId) }
    })
    val name by vm.name.collectAsState()
    val description by vm.description.collectAsState()
    val body by vm.body.collectAsState()
    val isBuiltIn by vm.isBuiltIn.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "New template" else "Edit template") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (templateId != null && !isBuiltIn) {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isBuiltIn) {
                Text(
                "Saving creates an editable copy. The built-in stays unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BoundedTextField(
                value = name, onValueChange = vm::setName, label = "Slash-command name",
                prefix = "/", maxLength = ValidationLimits.TEMPLATE_COMMAND_NAME, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            BoundedTextField(
                value = description, onValueChange = vm::setDescription, label = "Description",
                maxLength = ValidationLimits.TEMPLATE_DESCRIPTION,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            BoundedTextField(
                value = body, onValueChange = vm::setBody, label = "Template body",
                maxLength = ValidationLimits.TEMPLATE_BODY, minLines = 5,
                supportingText = "Use {{input}} where the text after the command should go",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            if (name.isNotBlank()) {
                Text(
                    "Preview: /$name optional input",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val withinLimits = name.length <= ValidationLimits.TEMPLATE_COMMAND_NAME &&
                description.length <= ValidationLimits.TEMPLATE_DESCRIPTION &&
                body.length <= ValidationLimits.TEMPLATE_BODY
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(
                    enabled = withinLimits,
                    onClick = { scope.launch { if (vm.save()) onBack() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
        }
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete template?",
            body = "\"/$name\" will be permanently deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.delete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
