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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GenerationRetrievalSettingsScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val retrievalMode by vm.defaultRetrievalMode.collectAsState()
    val contextLimit by vm.contextTokenLimit.collectAsState()
    val responseLength by vm.responseLength.collectAsState()
    val responseTone by vm.responseTone.collectAsState()
    val temperature by vm.temperature.collectAsState()
    val topP by vm.topP.collectAsState()
    val topK by vm.topK.collectAsState()
    val preferredBackend by vm.preferredBackend.collectAsState()
    val autoLoadDefaultModel by vm.autoLoadDefaultModel.collectAsState()
    val showGenerationStats by vm.showGenerationStats.collectAsState()
    val maxNumImages by vm.maxNumImages.collectAsState()
    val randomSeed by vm.randomSeed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generation & retrieval") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel("Chat & retrieval")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Default retrieval mode", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("KEYWORD", "SEMANTIC", "HYBRID", "EXACT_PHRASE").forEach { mode ->
                            FilterChip(
                                selected = retrievalMode == mode,
                                onClick = { vm.setDefaultRetrievalMode(mode) },
                                label = { Text(mode.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text(
                        "Used when an embedding model is active; source-grounded chats fall back to keyword search otherwise.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text("Context budget", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = contextLimit.toFloat(), onValueChange = { vm.setContextTokenLimit(it.toInt()) },
                            valueRange = 1024f..16384f, steps = 14, modifier = Modifier.weight(1f)
                        )
                        Text("$contextLimit tok", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(
                        "The context inspector flags this as the target — a manual setting, not a device-calibrated limit.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("Response style")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Applies to every chat as a stated preference — never inferred from what you've written.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Length", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CONCISE", "BALANCED", "DETAILED").forEach { length ->
                            FilterChip(
                                selected = responseLength == length,
                                onClick = { vm.setResponseLength(length) },
                                label = { Text(length.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text("Tone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
                    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NEUTRAL", "CASUAL", "FORMAL").forEach { tone ->
                            FilterChip(
                                selected = responseTone == tone,
                                onClick = { vm.setResponseTone(tone) },
                                label = { Text(tone.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            SectionLabel("Advanced generation")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    GenerationSlider("Temperature", temperature, "%.2f", 0f..2f, vm::setTemperature)
                    GenerationSlider("Top-p", topP, "%.2f", 0.1f..1f, vm::setTopP)
                    GenerationSlider("Top-k", topK.toFloat(), "%.0f", 1f..64f) { vm.setTopK(it.toInt()) }
                    GenerationSlider("Max images/prompt", maxNumImages.toFloat(), "%.0f", 1f..4f) { vm.setMaxNumImages(it.toInt()) }

                    Text("Random seed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
                    Text(
                        "Blank = a fresh sample every time. Set a number to make output reproducible.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var seedText by remember(randomSeed) { mutableStateOf(if (randomSeed < 0) "" else randomSeed.toString()) }
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { input ->
                            seedText = input.filter { it.isDigit() }
                            vm.setRandomSeed(seedText.toIntOrNull() ?: -1)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }

            SectionLabel("Model engine")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Load model when chat opens", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Prepares the selected local model before the composer is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoLoadDefaultModel, onCheckedChange = vm::setAutoLoadDefaultModel)
                    }
                    Text(
                        "Applies to any model left on \"Auto\" in Model manager — a model with its own explicit " +
                            "GPU/CPU/NPU choice there always uses that instead, strictly (no fallback).",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show generation stats", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Time taken and tokens/sec under a reply when you tap to expand it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showGenerationStats, onCheckedChange = vm::setShowGenerationStats)
                    }
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("AUTO" to "Auto", "GPU" to "GPU only", "CPU" to "CPU only", "NPU" to "NPU only").forEach { (value, label) ->
                            FilterChip(
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
