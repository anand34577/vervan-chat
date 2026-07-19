package com.vervan.chat.ui.dev

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevWorkspaceScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: DevWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { DevWorkspaceViewModel(app) } })
    val output by vm.output.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer workspace") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            BoundedTextField(
                value = code, onValueChange = { code = it },
                label = "Paste code", minLines = 6, maxLength = ValidationLimits.DEVELOPER_INPUT,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DevAction.entries.forEach { action ->
                    VervanFilterChip(selected = false, onClick = { vm.run(action, code) }, label = { Text(action.label) }, enabled = !running)
                }
            }
            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Working on the code",
                    body = "Analyzing your input with the local model.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Couldn't complete the code action",
                    message = it,
                    recovery = "Your code is safe. Shorten it or load a compatible model, then try again.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (output.isNotBlank()) {
                Text("Result", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(output, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(12.dp))
                }
                ResponsiveActions(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { clipboard.setText(output, scope) }) { Text("Copy") }
                    TextButton(onClick = { vm.saveAsNote(code.take(60)) }) { Text("Add to note") }
                    TextButton(onClick = { vm.saveToLibrary() }) { Text("Save to library") }
                }
            }
        }
        }
    }
}
