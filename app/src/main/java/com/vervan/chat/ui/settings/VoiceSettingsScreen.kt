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
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.theme.Space
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
    val sttEnginePreference by vm.sttEnginePreference.collectAsState()
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
        ScrollablePage(padding) {
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
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

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Realtime voice chat engine", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Choose the voice engine. Auto tries Piper, then the device voice.",
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
                                "Kokoro sounds better but can be much slower on budget devices.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = kokoroQualityEnabled, onCheckedChange = { vm.setKokoroQualityEnabled(it) })
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Interrupt by speaking (barge-in)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Speak to interrupt a reply. Unsupported devices use a Stop button.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = bargeInEnabled, onCheckedChange = { vm.setBargeInEnabled(it) })
                    }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Speech-to-text", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Uses the active model first, then the fallback selected below.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use offline speech-to-text model", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Use downloaded Whisper when available; otherwise use device speech recognition.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = inbuiltSttEnabled, onCheckedChange = { vm.setInbuiltSttEnabled(it) })
                    }
                    Text(
                        "Offline engine",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "Choose the downloaded Whisper runtime. Auto prefers whisper.cpp.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO" to "Auto", "WHISPER_CPP" to "whisper.cpp", "WHISPER_ONNX" to "Whisper (ONNX)").forEach { (value, label) ->
                            FilterChip(
                                selected = sttEnginePreference == value,
                                onClick = { vm.setSttEnginePreference(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("Download Whisper in Model Manager") }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Voice models", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Manage downloaded Hindi and English voices in Model Manager. Device speech is the fallback.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Open Model Manager") }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Higher quality voice (optional)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Download the optional Kokoro voice here.",
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
