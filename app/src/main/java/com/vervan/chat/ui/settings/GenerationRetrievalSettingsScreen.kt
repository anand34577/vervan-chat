package com.vervan.chat.ui.settings

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GenerationRetrievalSettingsScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val retrievalMode by vm.defaultRetrievalMode.collectAsStateWithLifecycle()
    val queryExpansionEnabled by vm.queryExpansionEnabled.collectAsStateWithLifecycle()
    val contextLimit by vm.contextTokenLimit.collectAsStateWithLifecycle()
    val includePastThinking by vm.includePastThinkingInContext.collectAsStateWithLifecycle()
    val responseLength by vm.responseLength.collectAsStateWithLifecycle()
    val responseTone by vm.responseTone.collectAsStateWithLifecycle()
    val temperature by vm.temperature.collectAsStateWithLifecycle()
    val topP by vm.topP.collectAsStateWithLifecycle()
    val topK by vm.topK.collectAsStateWithLifecycle()
    val preferredBackend by vm.preferredBackend.collectAsStateWithLifecycle()
    val allowLowMemoryModelLoads by vm.allowLowMemoryModelLoads.collectAsStateWithLifecycle()
    val backgroundUnloadMinutes by vm.backgroundUnloadMinutes.collectAsStateWithLifecycle()
    val autoContextSummarization by vm.autoContextSummarization.collectAsStateWithLifecycle()
    val showGenerationStats by vm.showGenerationStats.collectAsStateWithLifecycle()
    val maxNumImages by vm.maxNumImages.collectAsStateWithLifecycle()
    val randomSeed by vm.randomSeed.collectAsStateWithLifecycle()
    val expertMode by vm.expertMode.collectAsStateWithLifecycle()
    val minP by vm.minP.collectAsStateWithLifecycle()
    val repetitionPenalty by vm.repetitionPenalty.collectAsStateWithLifecycle()
    val maxOutputTokens by vm.maxOutputTokens.collectAsStateWithLifecycle()
    val cpuThreads by vm.cpuThreads.collectAsStateWithLifecycle()
    val nBatch by vm.nBatch.collectAsStateWithLifecycle()
    val nUbatch by vm.nUbatch.collectAsStateWithLifecycle()
    val useMlock by vm.useMlock.collectAsStateWithLifecycle()
    val flashAttentionMode by vm.flashAttentionMode.collectAsStateWithLifecycle()
    val kvCacheType by vm.kvCacheType.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.generation_retrieval_settings_responses_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_search_context))
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.generation_retrieval_settings_default_retrieval_mode), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        listOf("KEYWORD", "SEMANTIC", "HYBRID", "EXACT_PHRASE").forEach { mode ->
                            VervanFilterChip(
                                selected = retrievalMode == mode,
                                onClick = { vm.setDefaultRetrievalMode(mode) },
                                label = { Text(mode.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.generation_retrieval_settings_semantic_search_needs_an_embed),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = queryExpansionEnabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setQueryExpansionEnabled).heightIn(min = 48.dp).padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_rewrite_search_queries), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Asks the model for a couple of alternate phrasings before searching, to catch " +
                                    "passages that use different words than your question. Costs an extra response " +
                                    "before every grounded reply.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = queryExpansionEnabled, onCheckedChange = null)
                    }
                    Text(stringResource(R.string.generation_retrieval_settings_context_budget), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = contextLimit.toFloat(), onValueChange = { vm.setContextTokenLimit(it.toInt()) },
                            // Ceiling raised to 128K (steps kept at the original 1024-token
                            // granularity, just extended across the wider range) — default is
                            // still 4096, set separately in SettingsRepository.
                            valueRange = 1024f..131072f, steps = 126, modifier = Modifier.weight(1f)
                        )
                        Text(stringResource(R.string.generation_retrieval_settings_arg1_tok, contextLimit), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Space.sm))
                    }
                    Text(
                        stringResource(R.string.generation_retrieval_settings_sets_the_target_shown_in),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.toggleable(value = includePastThinking, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setIncludePastThinkingInContext).heightIn(min = 48.dp).fillMaxWidth().padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_include_past_thinking_in_conte), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Off by default: a past reply's reasoning is stripped before it's sent back as " +
                                    "history, so it only counts against context once, not on every later turn. " +
                                    "Turning this on lets the model see its own past reasoning too — uses more " +
                                    "context per turn, and on a long reasoning-heavy chat is what causes replies " +
                                    "to get cut off once context fills up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includePastThinking, onCheckedChange = null)
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = autoContextSummarization, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setAutoContextSummarization).heightIn(min = 48.dp).padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_summarize_long_chats), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.generation_retrieval_settings_keeps_older_context_in_a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoContextSummarization, onCheckedChange = null)
                    }
                }
            }

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_response_style))
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        stringResource(R.string.generation_retrieval_settings_sets_the_default_style_for),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.generation_retrieval_settings_length), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                    Row(Modifier.padding(top = Space.sm).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        listOf("CONCISE", "BALANCED", "DETAILED").forEach { length ->
                            VervanFilterChip(
                                selected = responseLength == length,
                                onClick = { vm.setResponseLength(length) },
                                label = { Text(length.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text(stringResource(R.string.persona_tone), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                    Row(Modifier.padding(top = Space.sm).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        listOf("NEUTRAL", "CASUAL", "FORMAL").forEach { tone ->
                            VervanFilterChip(
                                selected = responseTone == tone,
                                onClick = { vm.setResponseTone(tone) },
                                label = { Text(tone.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            if (expertMode) {
            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_raw_generation_parameters))
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    GenerationSlider("Temperature", temperature, "%.2f", 0f..2f, onChange = vm::setTemperature)
                    GenerationSlider("Top-p", topP, "%.2f", 0.1f..1f, onChange = vm::setTopP)
                    GenerationSlider("Top-k", topK.toFloat(), "%.0f", 1f..64f) { vm.setTopK(it.toInt()) }
                    GenerationSlider("Min-p", minP, "%.2f", 0f..1f, onChange = vm::setMinP)
                    GenerationSlider("Repetition penalty", repetitionPenalty, "%.2f", 1f..2f, onChange = vm::setRepetitionPenalty)
                    GenerationSlider("Max output tokens", maxOutputTokens.toFloat(), "%.0f", 64f..4096f) { vm.setMaxOutputTokens(it.toInt()) }
                    GenerationSlider("Max images/prompt", maxNumImages.toFloat(), "%.0f", 1f..4f) { vm.setMaxNumImages(it.toInt()) }

                    Text(stringResource(R.string.generation_retrieval_settings_random_seed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                    Text(
                        stringResource(R.string.generation_retrieval_settings_leave_blank_for_varied_output),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var seedText by remember(randomSeed) { mutableStateOf(if (randomSeed < 0) "" else randomSeed.toString()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = seedText,
                            onValueChange = { input ->
                                seedText = input.filter { it.isDigit() }.take(10)
                                vm.setRandomSeed(seedText.toIntOrNull() ?: -1)
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(top = Space.sm)
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            seedText = kotlin.random.Random.nextInt(0, Int.MAX_VALUE).toString()
                            vm.setRandomSeed(seedText.toIntOrNull() ?: -1)
                        }) { Text(stringResource(R.string.generation_retrieval_settings_randomize)) }
                    }
                }
            }
            } else {
                SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_response_behavior))
                Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(stringResource(R.string.generation_retrieval_settings_creativity), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.generation_retrieval_settings_choose_how_closely_replies_sho),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs, bottom = Space.sm)
                        )
                        val selected = when {
                            temperature <= 0.45f -> "FOCUSED"
                            temperature >= 1.05f -> "CREATIVE"
                            else -> "BALANCED"
                        }
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            listOf("FOCUSED" to "Focused", "BALANCED" to "Balanced", "CREATIVE" to "Creative").forEach { (id, label) ->
                                VervanFilterChip(
                                    selected = selected == id,
                                    onClick = {
                                        when (id) {
                                            "FOCUSED" -> { vm.setTemperature(0.3f); vm.setTopP(0.85f); vm.setTopK(24); vm.setMinP(0.08f) }
                                            "CREATIVE" -> { vm.setTemperature(1.15f); vm.setTopP(0.98f); vm.setTopK(56); vm.setMinP(0.03f) }
                                            else -> { vm.setTemperature(0.8f); vm.setTopP(0.95f); vm.setTopK(40); vm.setMinP(0.05f) }
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            when (selected) {
                                "FOCUSED" -> "Best for facts, summaries, and precise instructions."
                                "CREATIVE" -> stringResource(R.string.generation_retrieval_settings_best_for_ideas_writing_and)
                                else -> stringResource(R.string.generation_retrieval_settings_a_reliable_mix_of_accuracy)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                }
            }

            if (expertMode) {
                SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_advanced_llama_cpp))
                Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(
                            stringResource(R.string.generation_retrieval_settings_defaults_for_llama_cpp_gguf),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GenerationSlider("CPU threads (0 = auto)", cpuThreads.toFloat(), "%.0f", 0f..16f) { vm.setCpuThreads(it.toInt()) }
                        GenerationSlider("Batch size (n_batch)", nBatch.toFloat(), "%.0f", 128f..4096f, steps = 30) { vm.setNBatch(it.toInt()) }
                        GenerationSlider("Physical batch size (n_ubatch)", nUbatch.toFloat(), "%.0f", 32f..2048f, steps = 30) { vm.setNUbatch(it.toInt()) }
                        // Vulkan device index is no longer surfaced — llama.cpp is locked to the
                        // CPU backend in this build, so per-device Vulkan selection doesn't apply.

                        Row(Modifier.fillMaxWidth().toggleable(value = useMlock, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setUseMlock).heightIn(min = 48.dp).padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.generation_retrieval_settings_lock_model_in_ram_mlock), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = useMlock, onCheckedChange = null)
                        }

                        Text(stringResource(R.string.generation_retrieval_settings_flash_attention), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            listOf("AUTO" to "Auto", "ON" to "On", "OFF" to "Off").forEach { (value, label) ->
                                VervanFilterChip(selected = flashAttentionMode == value, onClick = { vm.setFlashAttentionMode(value) }, label = { Text(label) })
                            }
                        }

                        Text(stringResource(R.string.generation_retrieval_settings_kv_cache_type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            listOf("f16", "q8_0", "q4_0").forEach { value ->
                                VervanFilterChip(selected = kvCacheType == value, onClick = { vm.setKvCacheType(value) }, label = { Text(value) })
                            }
                        }
                    }
                }
            }

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_generation_retrieval_settings_model_engine))
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        stringResource(R.string.generation_retrieval_settings_default_loading_options_per_mo),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = allowLowMemoryModelLoads, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setAllowLowMemoryModelLoads).heightIn(min = 48.dp).padding(top = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_allow_low_memory_model_loads), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.generation_retrieval_settings_load_models_that_may_exceed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = allowLowMemoryModelLoads, onCheckedChange = null)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_free_memory_in_the_background), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (backgroundUnloadMinutes > 0) "Unload the model after $backgroundUnloadMinutes minutes away, so Android doesn't close the app. It reloads when you return."
                                else stringResource(R.string.generation_retrieval_settings_the_model_stays_loaded_while),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        com.vervan.chat.ui.common.LabeledSwitch(
                            label = stringResource(R.string.generation_retrieval_settings_free_memory_in_the_background),
                            checked = backgroundUnloadMinutes > 0,
                            onCheckedChange = { on -> vm.setBackgroundUnloadMinutes(if (on) 10 else 0) }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = showGenerationStats, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setShowGenerationStats).heightIn(min = 48.dp).padding(top = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.generation_retrieval_settings_show_generation_stats), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.generation_retrieval_settings_show_time_and_tokens_per),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showGenerationStats, onCheckedChange = null)
                    }
                    Text(if (expertMode) stringResource(R.string.generation_retrieval_settings_backend) else stringResource(R.string.generation_retrieval_settings_performance_preference), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.md))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        (if (expertMode) {
                            listOf("AUTO" to "Auto", "GPU" to "GPU only", "CPU" to "CPU only", "NPU" to "NPU only")
                        } else {
                            listOf("AUTO" to "Recommended", "GPU" to "Faster", "CPU" to "Compatible")
                        }).forEach { (value, label) ->
                            VervanFilterChip(
                                selected = preferredBackend == value,
                                onClick = { vm.setPreferredBackend(value) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }
    }
}
