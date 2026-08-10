package com.vervan.chat.ui.settings

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

    val retrievalMode by vm.defaultRetrievalMode.collectAsState()
    val queryExpansionEnabled by vm.queryExpansionEnabled.collectAsState()
    val contextLimit by vm.contextTokenLimit.collectAsState()
    val includePastThinking by vm.includePastThinkingInContext.collectAsState()
    val responseLength by vm.responseLength.collectAsState()
    val responseTone by vm.responseTone.collectAsState()
    val temperature by vm.temperature.collectAsState()
    val topP by vm.topP.collectAsState()
    val topK by vm.topK.collectAsState()
    val preferredBackend by vm.preferredBackend.collectAsState()
    val allowLowMemoryModelLoads by vm.allowLowMemoryModelLoads.collectAsState()
    val autoContextSummarization by vm.autoContextSummarization.collectAsState()
    val showGenerationStats by vm.showGenerationStats.collectAsState()
    val maxNumImages by vm.maxNumImages.collectAsState()
    val randomSeed by vm.randomSeed.collectAsState()
    val expertMode by vm.expertMode.collectAsState()
    val minP by vm.minP.collectAsState()
    val repetitionPenalty by vm.repetitionPenalty.collectAsState()
    val maxOutputTokens by vm.maxOutputTokens.collectAsState()
    val cpuThreads by vm.cpuThreads.collectAsState()
    val nBatch by vm.nBatch.collectAsState()
    val nUbatch by vm.nUbatch.collectAsState()
    val useMlock by vm.useMlock.collectAsState()
    val flashAttentionMode by vm.flashAttentionMode.collectAsState()
    val kvCacheType by vm.kvCacheType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Responses & search") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            SectionLabel("Search & context")
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text("Default retrieval mode", style = MaterialTheme.typography.bodyMedium)
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
                        "Semantic search needs an embedding model. Without one, Vervan uses keywords.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Rewrite search queries", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Asks the model for a couple of alternate phrasings before searching, to catch " +
                                    "passages that use different words than your question. Costs an extra response " +
                                    "before every grounded reply.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = queryExpansionEnabled, onCheckedChange = vm::setQueryExpansionEnabled)
                    }
                    Text("Context budget", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = contextLimit.toFloat(), onValueChange = { vm.setContextTokenLimit(it.toInt()) },
                            // Ceiling raised to 128K (steps kept at the original 1024-token
                            // granularity, just extended across the wider range) — default is
                            // still 4096, set separately in SettingsRepository.
                            valueRange = 1024f..131072f, steps = 126, modifier = Modifier.weight(1f)
                        )
                        Text("$contextLimit tok", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Space.sm))
                    }
                    Text(
                        "Sets the target shown in Context inspector.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Include past thinking in context", style = MaterialTheme.typography.titleSmall)
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
                        Switch(checked = includePastThinking, onCheckedChange = vm::setIncludePastThinkingInContext)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Summarize long chats", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Keeps older context in a running summary when a chat gets too long. Uses one extra background response.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoContextSummarization, onCheckedChange = vm::setAutoContextSummarization)
                    }
                }
            }

            SectionLabel("Response style")
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        "Sets the default style for new responses.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Length", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                    Row(Modifier.padding(top = Space.sm).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        listOf("CONCISE", "BALANCED", "DETAILED").forEach { length ->
                            VervanFilterChip(
                                selected = responseLength == length,
                                onClick = { vm.setResponseLength(length) },
                                label = { Text(length.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text("Tone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
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
            SectionLabel("Raw generation parameters")
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    GenerationSlider("Temperature", temperature, "%.2f", 0f..2f, onChange = vm::setTemperature)
                    GenerationSlider("Top-p", topP, "%.2f", 0.1f..1f, onChange = vm::setTopP)
                    GenerationSlider("Top-k", topK.toFloat(), "%.0f", 1f..64f) { vm.setTopK(it.toInt()) }
                    GenerationSlider("Min-p", minP, "%.2f", 0f..1f, onChange = vm::setMinP)
                    GenerationSlider("Repetition penalty", repetitionPenalty, "%.2f", 1f..2f, onChange = vm::setRepetitionPenalty)
                    GenerationSlider("Max output tokens", maxOutputTokens.toFloat(), "%.0f", 64f..4096f) { vm.setMaxOutputTokens(it.toInt()) }
                    GenerationSlider("Max images/prompt", maxNumImages.toFloat(), "%.0f", 1f..4f) { vm.setMaxNumImages(it.toInt()) }

                    Text("Random seed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                    Text(
                        "Leave blank for varied output. Set a number for repeatable output.",
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
                        }) { Text("Randomize") }
                    }
                }
            }
            } else {
                SectionLabel("Response behavior")
                Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text("Creativity", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Choose how closely replies should follow the most likely answer.",
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
                                "CREATIVE" -> "Best for ideas, writing, and varied responses."
                                else -> "A reliable mix of accuracy and variety."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                }
            }

            if (expertMode) {
                SectionLabel("Advanced (llama.cpp)")
                Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(
                            "Defaults for llama.cpp GGUF models. Per-model settings can override them.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GenerationSlider("CPU threads (0 = auto)", cpuThreads.toFloat(), "%.0f", 0f..16f) { vm.setCpuThreads(it.toInt()) }
                        GenerationSlider("Batch size (n_batch)", nBatch.toFloat(), "%.0f", 128f..4096f, steps = 30) { vm.setNBatch(it.toInt()) }
                        GenerationSlider("Physical batch size (n_ubatch)", nUbatch.toFloat(), "%.0f", 32f..2048f, steps = 30) { vm.setNUbatch(it.toInt()) }
                        // Vulkan device index is no longer surfaced — llama.cpp is locked to the
                        // CPU backend in this build, so per-device Vulkan selection doesn't apply.

                        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("Lock model in RAM (mlock)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = useMlock, onCheckedChange = vm::setUseMlock)
                        }

                        Text("Flash attention", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            listOf("AUTO" to "Auto", "ON" to "On", "OFF" to "Off").forEach { (value, label) ->
                                VervanFilterChip(selected = flashAttentionMode == value, onClick = { vm.setFlashAttentionMode(value) }, label = { Text(label) })
                            }
                        }

                        Text("KV cache type", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            listOf("f16", "q8_0", "q4_0").forEach { value ->
                                VervanFilterChip(selected = kvCacheType == value, onClick = { vm.setKvCacheType(value) }, label = { Text(value) })
                            }
                        }
                    }
                }
            }

            SectionLabel("Model engine")
            Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        "Default loading options. Per-model choices take priority.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow low-memory model loads", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Load models that may exceed available memory. This can slow or stop the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = allowLowMemoryModelLoads, onCheckedChange = vm::setAllowLowMemoryModelLoads)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show generation stats", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Show time and tokens per second below replies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showGenerationStats, onCheckedChange = vm::setShowGenerationStats)
                    }
                    Text(if (expertMode) "Backend" else "Performance preference", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.md))
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
