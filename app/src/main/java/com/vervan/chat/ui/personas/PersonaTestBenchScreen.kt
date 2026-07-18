package com.vervan.chat.ui.personas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.VervanMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaTestBenchScreen(personaId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: PersonaTestBenchViewModel = viewModel(factory = viewModelFactory { initializer { PersonaTestBenchViewModel(app, personaId) } })
    val persona by vm.persona.collectAsState()
    val prompt by vm.samplePrompt.collectAsState()
    val response by vm.response.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test: ${persona?.name ?: "Persona"}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            persona?.let { p ->
                Text("System instruction", style = MaterialTheme.typography.labelMedium)
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(p.systemInstruction, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                Text("Token cost (chars/4 estimate)", style = MaterialTheme.typography.labelMedium)
                Text("${p.systemInstruction.length / 4} tokens", style = MaterialTheme.typography.bodyMedium, fontFamily = VervanMono, modifier = Modifier.padding(bottom = 12.dp))
            }

            Text("Sample prompt", style = MaterialTheme.typography.labelMedium)
            BoundedTextField(
                value = prompt,
                onValueChange = { vm.setPrompt(it) },
                maxLength = ValidationLimits.PERSONA_TEST_PROMPT,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                minLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.run() }, enabled = !running && persona != null) {
                    if (running) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Run")
                }
                OutlinedButton(onClick = { vm.reset() }, enabled = !running) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Reset")
                }
            }

            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Testing this persona",
                    body = "Testing the persona with your sample prompt."
                )
            }

            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Persona test couldn't run",
                    message = it,
                    recovery = "Your work is safe. Load a model, then try again.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            response?.let { resp ->
                Text("Response preview", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(resp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
            }
        }
        }
    }
}
