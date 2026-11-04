package com.vervan.chat.ui.personas

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Person
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaTestBenchScreen(personaId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: PersonaTestBenchViewModel = viewModel(factory = viewModelFactory { initializer { PersonaTestBenchViewModel(app, personaId) } })
    val persona by vm.persona.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val prompt by vm.samplePrompt.collectAsStateWithLifecycle()
    val response by vm.response.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(stringResource(R.string.persona_test_bench_title, persona?.name ?: stringResource(R.string.chat_persona))) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.persona_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.persona_test_bench_your_persona_is_safe_retry),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retryLoad,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
            persona == null -> EmptyState(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.persona_not_found),
                body = stringResource(R.string.persona_not_found_body),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack
            )
            else -> Column(Modifier.fillMaxSize().imePadding().padding(vertical = Space.lg).verticalScroll(rememberScrollState())) {
            persona?.let { p ->
                Text(stringResource(R.string.persona_system_instruction), style = MaterialTheme.typography.labelMedium)
                Card(Modifier.fillMaxWidth().padding(bottom = Space.md)) {
                    Text(p.systemInstruction, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Space.md))
                }
                Text(stringResource(R.string.persona_test_bench_token_cost_chars_4_estimate), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.persona_test_bench_arg1_tokens, p.systemInstruction.length / 4), style = MaterialTheme.typography.bodyMedium, fontFamily = VervanMono, modifier = Modifier.padding(bottom = Space.md))
            }

            Text(stringResource(R.string.persona_test_bench_sample_prompt), style = MaterialTheme.typography.labelMedium)
            BoundedTextField(
                value = prompt,
                onValueChange = { vm.setPrompt(it) },
                maxLength = ValidationLimits.PERSONA_TEST_PROMPT,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.md),
                minLines = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Button(onClick = { vm.run() }, enabled = !running && persona != null) {
                    if (running) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.persona_test_bench_run))
                }
                OutlinedButton(onClick = { vm.reset() }, enabled = !running) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_reset))
                }
            }

            if (running) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.persona_test_bench_testing_this_persona),
                    body = stringResource(R.string.persona_test_bench_testing_the_persona_with_your)
                )
            }

            error?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.persona_test_bench_persona_test_couldn_t_run),
                    message = it,
                    recovery = stringResource(R.string.persona_test_bench_your_work_is_safe_load),
                    modifier = Modifier.padding(top = Space.md)
                )
            }

            response?.let { resp ->
                Text(stringResource(R.string.persona_test_bench_response_preview), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg))
                Card(Modifier.fillMaxWidth()) {
                    Text(resp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.md))
                }
            }
            }
        }
    }
}
}
