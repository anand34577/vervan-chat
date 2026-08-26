package com.vervan.chat.ui.writing

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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
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
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WritingWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { WritingWorkspaceViewModel(app) } })
    val revision by vm.revision.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.writing_workspace_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(contentPadding = padding, modifier = Modifier.imePadding(), maxContentWidth = 840.dp) {
            BoundedTextField(
                value = original, onValueChange = { original = it },
                label = stringResource(R.string.writing_text_label), minLines = 4, maxLength = ValidationLimits.WRITING_INPUT,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetLanguage, onValueChange = { targetLanguage = it.take(80) },
                    label = { Text(stringResource(R.string.writing_target_language_label)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                WritingAction.entries.forEach { action ->
                    VervanFilterChip(selected = false, onClick = { vm.run(action, original, targetLanguage) }, label = { Text(action.label) }, enabled = !running)
                }
            }
            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.ui_writingworkspacescreen_100_preparing_the_revision),
                    body = stringResource(R.string.ui_writingworkspacescreen_101_rewriting_locally_your_original_stays_unchan),
                    modifier = Modifier.padding(top = Space.lg)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.ui_writingworkspacescreen_107_couldn_t_complete_the_writing_action),
                    message = it,
                    recovery = stringResource(R.string.ui_writingworkspace_text_recovery),
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            if (revision.isNotBlank()) {
                Text(stringResource(R.string.writing_revision), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.xl, bottom = Space.xs))
                DiffViewer(
                    original = original,
                    transformed = revision,
                    onReplace = { original = revision },
                    onCopy = {
                        clipboard.setText(revision, scope)
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                    }
                )
                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                    TextButton(onClick = {
                        vm.saveAsNote(original.take(60))
                        scope.launch { snackbarHostState.showSnackbar("Added to notes") }
                    }) { Text(stringResource(R.string.chat_add_note)) }
                    TextButton(onClick = {
                        vm.saveToLibrary()
                        scope.launch { snackbarHostState.showSnackbar("Saved to library") }
                    }) { Text(stringResource(R.string.writing_save_library)) }
                }
            }
        }
    }
}
