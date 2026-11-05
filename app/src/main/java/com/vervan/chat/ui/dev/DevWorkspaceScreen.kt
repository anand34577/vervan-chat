package com.vervan.chat.ui.dev

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val vm: DevWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { DevWorkspaceViewModel(app) } })
    val output by vm.output.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(DevAction.entries.first()) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_workspace_developer_workspace)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().padding(vertical = Space.lg).verticalScroll(rememberScrollState())) {
            com.vervan.chat.ui.tools.ToolIntro(
                icon = androidx.compose.material.icons.Icons.Filled.Code,
                title = stringResource(R.string.dev_workspace_developer_workspace),
                body = stringResource(R.string.dev_intro)
            )
            com.vervan.chat.ui.tools.ToolStep(1, stringResource(R.string.dev_step_code))
            BoundedTextField(
                value = code, onValueChange = { code = it },
                label = stringResource(R.string.dev_workspace_paste_code), minLines = 6, maxLength = ValidationLimits.DEVELOPER_INPUT,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            com.vervan.chat.ui.tools.ToolStep(2, stringResource(R.string.tool_step_choose_action))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                DevAction.entries.forEach { action ->
                    VervanFilterChip(selected = selected == action, onClick = { selected = action }, label = { Text(action.label) }, enabled = !running)
                }
            }
            com.vervan.chat.ui.tools.ToolPrimaryButton(
                label = selected.label,
                icon = androidx.compose.material.icons.Icons.Filled.Code,
                onClick = { vm.run(selected, code) },
                blockedReason = if (code.isBlank()) stringResource(R.string.dev_blocked) else null,
                running = running
            )
            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.dev_workspace_working_on_the_code),
                    body = stringResource(R.string.dev_workspace_analyzing_your_input_with_the),
                    modifier = Modifier.padding(top = Space.lg)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.dev_workspace_couldn_t_complete_the_code),
                    message = it,
                    recovery = stringResource(R.string.dev_workspace_your_code_is_safe_shorten),
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            if (output.isNotBlank()) {
                com.vervan.chat.ui.tools.ToolStep(3, stringResource(R.string.tool_step_review_result))
                Card(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        com.vervan.chat.ui.common.MarkdownLiteText(output, modifier = Modifier.padding(Space.md))
                    }
                }
                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                    TextButton(onClick = {
                        clipboard.setText(output, scope)
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.dev_workspace_copied_to_clipboard)) }
                    }) { Text(stringResource(R.string.library_copy)) }
                    TextButton(onClick = {
                        vm.saveAsNote(code.take(60))
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_added_to_notes)) }
                    }) { Text(stringResource(R.string.chat_add_note)) }
                    TextButton(onClick = {
                        vm.saveToLibrary()
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.dev_workspace_saved_to_library)) }
                    }) { Text(stringResource(R.string.dev_workspace_save_to_library)) }
                }
            }
        }
        }
    }
}
