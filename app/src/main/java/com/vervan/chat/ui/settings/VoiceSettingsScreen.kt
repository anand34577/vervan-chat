package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit = {}, onOpenModelManager: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val ttsRate by vm.ttsRate.collectAsState()
    val autoReadAloud by vm.autoReadAloud.collectAsState()
    val ttsEnginePreference by vm.ttsEnginePreference.collectAsState()
    val kokoroQualityEnabled by vm.kokoroQualityEnabled.collectAsState()
    val bargeInEnabled by vm.bargeInEnabled.collectAsState()
    val inbuiltSttEnabled by vm.inbuiltSttEnabled.collectAsState()
    val downloadedVoiceModels by vm.downloadedVoiceModels.collectAsState()
    val activeVoiceDownloadJobs by vm.activeVoiceDownloadJobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Read-aloud speed", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = ttsRate, onValueChange = { vm.setTtsRate(it) },
                            valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f)
                        )
                        Text("${String.format("%.1f", ttsRate)}x", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-read replies", style = MaterialTheme.typography.bodyMedium)
                            Text("Speak each finished assistant reply automatically", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoReadAloud, onCheckedChange = { vm.setAutoReadAloud(it) })
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Realtime voice chat engine", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Which text-to-speech engine the realtime voice chat pipeline uses. Auto tries Piper, then the device's built-in engine.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO" to "Auto", "PIPER" to "Piper", "SYSTEM" to "Device").forEach { (value, label) ->
                            FilterChip(
                                selected = ttsEnginePreference == value,
                                onClick = { vm.setTtsEnginePreference(value) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Higher quality voice (slower)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Kokoro sounds noticeably better than Piper but can take 2-3 minutes of processing per minute of speech on budget devices — never used automatically, only when explicitly selected above.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = kokoroQualityEnabled, onCheckedChange = { vm.setKokoroQualityEnabled(it) })
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Interrupt by speaking (barge-in)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Talk over a reply to cut it off, like a phone call. Needs hardware echo cancellation — falls back to a tap-to-interrupt button on devices without it.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = bargeInEnabled, onCheckedChange = { vm.setBargeInEnabled(it) })
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Speech-to-text", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Realtime voice chat tries the loaded chat model's own transcription first (when it supports audio input, e.g. Gemma 4 E2B). This controls the fallback used when that model can't transcribe, or does it poorly:",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use offline speech-to-text model", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "On: use the downloaded Whisper model (Model Manager) when available. Off: fall back straight to the device's built-in speech recognizer.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = inbuiltSttEnabled, onCheckedChange = { vm.setInbuiltSttEnabled(it) })
                    }
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Download Whisper in Model Manager") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Voice models", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Hindi and English voices are downloaded and managed from Model Manager — same pause/resume/cancel/delete controls as any other model. Without one downloaded, realtime voice chat falls back to the device's built-in text-to-speech.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Open Model Manager") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Higher quality voice (optional)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Kokoro isn't in Model Manager yet (it needs an extra step Model Manager's downloader doesn't do) — download it here instead.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.vervan.chat.voice.TtsVoiceCatalog.entries.forEach { entry ->
                        val downloaded = downloadedVoiceModels.any { it.engine == entry.engine && it.language == entry.language && it.isReady }
                        val activeJob = activeVoiceDownloadJobs.firstOrNull { it.label == entry.label }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    when {
                                        downloaded -> "Ready"
                                        activeJob != null -> "Downloading… ${activeJob.progress}%"
                                        else -> "Not downloaded"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when {
                                downloaded -> IconButton(onClick = { vm.deleteVoiceModel(entry) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete downloaded voice")
                                }
                                activeJob != null -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> androidx.compose.material3.TextButton(onClick = { vm.downloadVoiceModel(entry) }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
    }
}
