package com.vervan.chat.ui.writing

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.DiffViewer
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WritingWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val vm: WritingWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { WritingWorkspaceViewModel(app) } })
    val revision by vm.revision.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var original by rememberSaveable { mutableStateOf("") }
    var targetLanguage by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(WritingAction.REWRITE) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.writing_workspace_writing_workspace)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(contentPadding = padding, modifier = Modifier.imePadding(), maxContentWidth = 840.dp) {
            com.vervan.chat.ui.tools.ToolIntro(
                icon = androidx.compose.material.icons.Icons.Filled.EditNote,
                title = stringResource(R.string.writing_workspace_writing_workspace),
                body = stringResource(R.string.ww_intro)
            )
            com.vervan.chat.ui.tools.ToolStep(1, stringResource(R.string.tool_step_add_text))
            BoundedTextField(
                value = original, onValueChange = { original = it },
                label = stringResource(R.string.writing_workspace_your_text), minLines = 5, maxLength = ValidationLimits.WRITING_INPUT,
                modifier = Modifier.fillMaxWidth()
            )
            com.vervan.chat.ui.tools.ToolStep(2, stringResource(R.string.tool_step_choose_action))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                WritingAction.entries.forEach { action ->
                    VervanFilterChip(selected = selected == action, onClick = { selected = action }, label = { Text(action.label) }, enabled = !running)
                }
            }
            if (selected == WritingAction.TRANSLATE) {
                OutlinedTextField(
                    value = targetLanguage, onValueChange = { targetLanguage = it.take(80) },
                    label = { Text(stringResource(R.string.ww_translate_into)) },
                    placeholder = { Text(stringResource(R.string.ww_translate_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                )
            }
            com.vervan.chat.ui.tools.ToolPrimaryButton(
                label = selected.label,
                icon = androidx.compose.material.icons.Icons.Filled.EditNote,
                onClick = { vm.run(selected, original, targetLanguage) },
                blockedReason = when {
                    original.isBlank() -> stringResource(R.string.tool_blocked_add_text)
                    selected == WritingAction.TRANSLATE && targetLanguage.isBlank() -> stringResource(R.string.ww_blocked_language)
                    else -> null
                },
                running = running
            )
            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.writing_workspace_preparing_the_revision),
                    body = stringResource(R.string.writing_workspace_rewriting_locally_your_original_stays),
                    modifier = Modifier.padding(top = Space.lg)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.writing_workspace_couldn_t_complete_the_writing),
                    message = it,
                    recovery = stringResource(R.string.writing_workspace_your_text_is_safe_shorten),
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            if (revision.isNotBlank()) {
                com.vervan.chat.ui.tools.ToolStep(3, stringResource(R.string.ww_step_compare), hint = stringResource(R.string.ww_step_compare_hint))
                DiffViewer(
                    original = original,
                    transformed = revision,
                    onReplace = { original = revision },
                    onCopy = {
                        clipboard.setText(revision, scope)
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.writing_workspace_copied_to_clipboard)) }
                    }
                )
                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                    TextButton(onClick = {
                        vm.saveAsNote(original.take(60))
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_added_to_notes)) }
                    }) { Text(stringResource(R.string.chat_add_note)) }
                    TextButton(onClick = {
                        vm.saveToLibrary()
                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.writing_workspace_saved_to_library)) }
                    }) { Text(stringResource(R.string.writing_workspace_save_to_library)) }
                }
            }
        }
    }
}
