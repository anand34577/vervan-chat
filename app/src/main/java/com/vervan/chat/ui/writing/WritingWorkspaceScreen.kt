package com.vervan.chat.ui.writing

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.DiffViewer
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.ValidationLimits

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Writing workspace") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            BoundedTextField(
                value = original, onValueChange = { original = it },
                label = "Your text", minLines = 4, maxLength = ValidationLimits.WRITING_INPUT,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetLanguage, onValueChange = { targetLanguage = it },
                label = { Text("Target language (for Translate)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WritingAction.entries.forEach { action ->
                    FilterChip(selected = false, onClick = { vm.run(action, original, targetLanguage) }, label = { Text(action.label) }, enabled = !running)
                }
            }
            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Preparing the revision",
                    body = "Rewriting locally. Your original stays unchanged.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Couldn't complete the writing action",
                    message = it,
                    recovery = "Your text is safe. Shorten it or check the model, then try again.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (revision.isNotBlank()) {
                Text("Revision", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                DiffViewer(
                    original = original,
                    transformed = revision,
                    onReplace = { original = revision },
                    onCopy = { clipboard.setText(revision, scope) }
                )
                ResponsiveActions(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { vm.saveAsNote(original.take(60)) }) { Text("Add to note") }
                    TextButton(onClick = { vm.saveToLibrary() }) { Text("Save to library") }
                }
            }
        }
        }
    }
}
