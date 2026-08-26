package com.vervan.chat.ui.personas

import androidx.compose.foundation.BorderStroke
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
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaTestBenchScreen(personaId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: PersonaTestBenchViewModel = viewModel(factory = viewModelFactory { initializer { PersonaTestBenchViewModel(app, personaId) } })
    val persona by vm.persona.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val prompt by vm.samplePrompt.collectAsState()
    val response by vm.response.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(stringResource(R.string.ui_personatestbench_test_title, persona?.name ?: stringResource(R.string.ui_personatestbench_persona))) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            when {
                loadError != null -> OperationErrorCard(
                    title = stringResource(R.string.persona_unavailable),
                    message = loadError.orEmpty(),
                    recovery = stringResource(R.string.ui_personatestbench_persona_recovery),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retryLoad,
                    modifier = Modifier.padding(Space.md)
                )
                isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
                persona == null -> EmptyState(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.persona_not_found),
                    body = stringResource(R.string.persona_not_found_body),
                    modifier = Modifier.fillMaxSize(),
                    centered = true,
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack
                )
                else -> persona?.let { p ->
                    Column(
                        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        ModernistScreenHeader(
                            eyebrow = stringResource(R.string.ui_personatestbenchscreen_102_persona_test_bench),
                            title = p.name,
                            body = stringResource(R.string.ui_personatestbenchscreen_104_try_a_prompt_against_this_persona_and_inspec),
                            trailing = { ModernistTag(if (running) "RUNNING" else "READY", active = running) }
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                                Text(stringResource(R.string.persona_system_instruction), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    p.systemInstruction,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = Space.sm)
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = Space.md),
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                                ) {
                                    BenchMetric("TOKEN ESTIMATE", "${p.systemInstruction.length / 4}", Modifier.weight(1f))
                                    BenchMetric("RESPONSE STYLE", p.conciseness.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                                Text(stringResource(R.string.ui_personatestbenchscreen_140_prompt), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Use a concrete request to see how this persona responds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xs)
                                )
                                BoundedTextField(
                                    value = prompt,
                                    onValueChange = vm::setPrompt,
                                    maxLength = ValidationLimits.PERSONA_TEST_PROMPT,
                                    modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                                    minLines = 3
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = Space.md),
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                                ) {
                                    Button(
                                        onClick = vm::run,
                                        enabled = !running && prompt.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        else Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(R.string.ui_personatestbenchscreen_166_run_test), modifier = Modifier.padding(start = Space.xs))
                                    }
                                    OutlinedButton(
                                        onClick = vm::reset,
                                        enabled = !running,
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(R.string.action_reset), modifier = Modifier.padding(start = Space.xs))
                                    }
                                }
                            }
                        }

                        if (running) {
                            com.vervan.chat.ui.common.OperationProgressCard(
                                title = stringResource(R.string.ui_personatestbenchscreen_183_testing_this_persona),
                                body = stringResource(R.string.ui_personatestbenchscreen_184_the_response_will_appear_here_when_generatio)
                            )
                        }
                        error?.let {
                            OperationErrorCard(
                                title = stringResource(R.string.ui_personatestbenchscreen_189_persona_test_could_not_run),
                                message = it,
                                recovery = stringResource(R.string.ui_personatestbench_model_recovery)
                            )
                        }
                        response?.let { resp ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                                    Text(stringResource(R.string.ui_personatestbenchscreen_203_response_preview), style = MaterialTheme.typography.titleSmall)
                                    Text(resp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
