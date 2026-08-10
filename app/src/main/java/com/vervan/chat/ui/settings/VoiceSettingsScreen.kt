package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.GraphicEq
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
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit = {}, onOpenModelManager: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val ttsEnginePreference by vm.ttsEnginePreference.collectAsState()
    val bargeInEnabled by vm.bargeInEnabled.collectAsState()
    val inbuiltSttEnabled by vm.inbuiltSttEnabled.collectAsState()
    val modelAudioSttEnabled by vm.modelAudioSttEnabled.collectAsState()
    val androidSttEnabled by vm.androidSttEnabled.collectAsState()
    val sttEnginePreference by vm.sttEnginePreference.collectAsState()
    val sttFallbackEnabled by vm.sttFallbackEnabled.collectAsState()
    val whisperGpuEnabled by vm.whisperGpuEnabled.collectAsState()
    val downloadedVoiceModels by vm.downloadedVoiceModels.collectAsState()
    val activeVoiceDownloadJobs by vm.activeVoiceDownloadJobs.collectAsState()
    val speechInputEnabled by vm.speechInputEnabled.collectAsState()
    val voiceReplyMode by vm.voiceReplyMode.collectAsState()
    val voiceInputMethod by vm.voiceInputMethod.collectAsState()
    val transcriptReviewEnabled by vm.transcriptReviewEnabled.collectAsState()
    val handsFreeAutoSend by vm.handsFreeAutoSend.collectAsState()
    val continueListening by vm.continueListening.collectAsState()
    val headphonesOnlyPlayback by vm.headphonesOnlyPlayback.collectAsState()
    val headphonePrivacyPause by vm.headphonePrivacyPause.collectAsState()
    val voiceInputLanguage by vm.voiceInputLanguage.collectAsState()
    val vadSensitivity by vm.vadSensitivity.collectAsState()
    val voiceSilenceDurationMs by vm.voiceSilenceDurationMs.collectAsState()
    val maxUtteranceSeconds by vm.maxUtteranceSeconds.collectAsState()
    val storeVoiceRecordings by vm.storeVoiceRecordings.collectAsState()
    val voiceSpeechRate by vm.voiceSpeechRate.collectAsState()
    val voiceSpeechPitch by vm.voiceSpeechPitch.collectAsState()
    val readCodeMode by vm.readCodeMode.collectAsState()
    val readTableMode by vm.readTableMode.collectAsState()
    val longResponseVoiceMode by vm.longResponseVoiceMode.collectAsState()
    val backgroundVoiceEnabled by vm.backgroundVoiceEnabled.collectAsState()
    val voiceBatterySaver by vm.voiceBatterySaver.collectAsState()
    val transcriptRetentionEnabled by vm.transcriptRetentionEnabled.collectAsState()
    val recordingRetentionMode by vm.recordingRetentionMode.collectAsState()
    val activeGenerationModel by vm.activeGenerationModel.collectAsState()
    val voiceQualityPreset by vm.voiceQualityPreset.collectAsState()
    val androidRecognizerAvailable = remember {
        com.vervan.chat.voice.AndroidSystemSttRecognizer.isAvailable(app)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice & speech") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.GraphicEq,
                eyebrow = "Natural conversation",
                title = "Tune listening and speaking",
                body = "Choose how Vervan hears, responds, plays speech, and protects your voice data."
            )
            VoiceSettingsHeading("Voice quality")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    VoiceChoiceChips(
                        title = "How should Vervan sound and listen?",
                        value = voiceQualityPreset,
                        choices = listOf("FAST" to "Fast", "BALANCED" to "Balanced", "BEST" to "Best"),
                        onSelect = vm::setVoiceQualityPreset
                    )
                    Text(
                        when (voiceQualityPreset) {
                            "FAST" -> "Quickest replies. Skips the slower whisper.cpp transcriber; uses the default Piper voice."
                            "BEST" -> "Highest quality voice (Kokoro or Supertonic, once downloaded) and drops the lower-accuracy Android fallback for speech input."
                            else -> "The default balance of speed and accuracy for both listening and replying."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }

            VoiceSettingsHeading("Basic")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
            VoiceToggleRow("Speech input", "Use dictation, push-to-talk, and hands-free listening.", speechInputEnabled, vm::setSpeechInputEnabled)
                    VoiceChoiceChips(
                        title = "Input method",
                        value = voiceInputMethod,
                        choices = listOf("DICTATION" to "Dictation", "PUSH_TO_TALK" to "Push-to-talk", "HANDS_FREE" to "Hands-free"),
                        onSelect = vm::setVoiceInputMethod
                    )
                    VoiceChoiceChips(
                        title = "Voice replies",
                        value = voiceReplyMode,
                        choices = listOf("NEVER" to "Off", "MANUAL" to "Manual", "AUTOMATIC" to "Automatic", "HANDS_FREE" to "Hands-free"),
                        onSelect = vm::setVoiceReplyMode
                    )
                    VoiceToggleRow("Review before sending", "Keep dictation editable until you approve it.", transcriptReviewEnabled, vm::setTranscriptReviewEnabled)
                    VoiceToggleRow("Auto-send in hands-free", "Send only finalized hands-free transcripts automatically.", handsFreeAutoSend, vm::setHandsFreeAutoSend)
                }
            }

            VoiceSettingsHeading("Speech input")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    VoiceChoiceChips(
                        title = "Language",
                        value = voiceInputLanguage,
                        choices = listOf("AUTO" to "Auto", "EN" to "English", "HI" to "Hindi", "MULTI" to "Multilingual"),
                        onSelect = vm::setVoiceInputLanguage
                    )
                    VoiceSliderRow("VAD sensitivity", "How readily hands-free listening detects speech.", vadSensitivity, 0f..1f, vm::setVadSensitivity)
                    VoiceSliderRow(
                        "Silence before sending",
                        "${voiceSilenceDurationMs} ms",
                        voiceSilenceDurationMs.toFloat(),
                        300f..3000f,
                        { vm.setVoiceSilenceDurationMs(it.toInt()) }
                    )
                    VoiceSliderRow(
                        "Maximum utterance",
                        "$maxUtteranceSeconds seconds",
                        maxUtteranceSeconds.toFloat(),
                        10f..180f,
                        { vm.setMaxUtteranceSeconds(it.toInt()) }
                    )
                    VoiceToggleRow(
                        "Save my voice with messages",
                        "Save recorded requests with playback. Android speech recognition cannot save the original audio.",
                        storeVoiceRecordings,
                        vm::setStoreVoiceRecordings
                    )
                }
            }

            VoiceSettingsHeading("Speech output")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    VoiceToggleRow("Continue listening", "Reopen listening after a spoken reply finishes.", continueListening, vm::setContinueListening)
                    VoiceToggleRow("Headphones-only playback", "Never route automatic replies through the device speaker.", headphonesOnlyPlayback, vm::setHeadphonesOnlyPlayback)
                    VoiceToggleRow("Pause when headphones disconnect", "Stop playback before audio can move to the speaker.", headphonePrivacyPause, vm::setHeadphonePrivacyPause)
                    VoiceSliderRow("Speech rate", String.format("%.1fx", voiceSpeechRate), voiceSpeechRate, 0.6f..1.6f, vm::setVoiceSpeechRate)
                    VoiceSliderRow("Speech pitch", String.format("%.1fx", voiceSpeechPitch), voiceSpeechPitch, 0.7f..1.3f, vm::setVoiceSpeechPitch)
                    VoiceChoiceChips("Read code", readCodeMode, listOf("BRIEF" to "Brief", "SUMMARY" to "Summary", "LITERAL" to "Literal"), vm::setReadCodeMode)
                    VoiceChoiceChips("Read tables", readTableMode, listOf("SKIP" to "Skip", "SUMMARY" to "Summary", "LITERAL" to "Literal"), vm::setReadTableMode)
                    VoiceChoiceChips("Long responses", longResponseVoiceMode, listOf("ASK" to "Ask", "SUMMARY" to "Summarize", "FULL" to "Read all"), vm::setLongResponseVoiceMode)
                }
            }

            VoiceSettingsHeading("Audio and performance")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
            VoiceToggleRow("Interrupt by speaking", "Uses echo cancellation when available; otherwise tap to interrupt.", bargeInEnabled, vm::setBargeInEnabled)
            VoiceToggleRow("Background voice session", "Keep voice controls available outside Vervan.", backgroundVoiceEnabled, vm::setBackgroundVoiceEnabled)
            VoiceToggleRow("Battery-saving voice mode", "Use less power and avoid overlapping speech.", voiceBatterySaver, vm::setVoiceBatterySaver)
                }
            }

            VoiceSettingsHeading("Privacy")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    VoiceToggleRow("Keep transcripts", "Store finalized speech as ordinary chat messages.", transcriptRetentionEnabled, vm::setTranscriptRetentionEnabled)
                    VoiceChoiceChips(
                        "Recording retention",
                        recordingRetentionMode,
                        listOf("NONE" to "Never", "TEMPORARY" to "Temporary", "KEEP" to "Keep"),
                        vm::setRecordingRetentionMode
                    )
                    Text(
                        "Active-model and whisper.cpp recognition stay on-device. Android speech may use the device's installed service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md)
                    )
                }
            }

            VoiceSettingsHeading("Advanced: engines")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Realtime voice chat engine", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Auto uses Piper. Choose Kokoro for higher quality with slower playback, or Supertonic for a 31-language voice (largest download, slowest).",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val kokoroDownloaded = downloadedVoiceModels.any { it.engine == "KOKORO" && it.language == "multi" && it.isReady }
                    val supertonicDownloaded = downloadedVoiceModels.any { it.engine == "SUPERTONIC" && it.language == "multi" && it.isReady }
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = Space.sm),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
                    ) {
                        FilterChip(
                            selected = ttsEnginePreference == "AUTO",
                            onClick = { vm.setTtsEnginePreference("AUTO") },
                            label = { Text("Auto (Piper)") }
                        )
                        FilterChip(
                            selected = ttsEnginePreference == "KOKORO",
                            onClick = { vm.setTtsEnginePreference("KOKORO") },
                            enabled = kokoroDownloaded,
                            label = { Text(if (kokoroDownloaded) "Kokoro" else "Kokoro (download below)") }
                        )
                        FilterChip(
                            selected = ttsEnginePreference == "SUPERTONIC",
                            onClick = { vm.setTtsEnginePreference("SUPERTONIC") },
                            enabled = supertonicDownloaded,
                            label = { Text(if (supertonicDownloaded) "Supertonic" else "Supertonic (download in Model Manager)") }
                        )
                    }
                    if (!supertonicDownloaded) {
                        androidx.compose.material3.TextButton(onClick = onOpenModelManager, modifier = Modifier.padding(top = Space.xs)) {
                            Text("Open Model Manager to download Supertonic", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        // Every voice besides "multi" (M1) is a separate ~290 KB download layered
                        // on the shared acoustic model — see SupertonicTtsEngine's class doc. Only
                        // list voices actually installed; the rest are downloadable in Model Manager.
                        val supertonicVoiceVariant by vm.supertonicVoiceVariant.collectAsState()
                        val installedVoices = downloadedVoiceModels.filter { it.engine == "SUPERTONIC" && it.isReady }
                        Text(
                            "Supertonic voice",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Space.md)
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(top = Space.sm),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
                        ) {
                            installedVoices.forEach { voice ->
                                val label = com.vervan.chat.modeldownload.ModelCatalog.all
                                    .find { it.ttsEngine == "SUPERTONIC" && it.ttsLanguage == voice.language }
                                    ?.displayName?.substringAfter("— ") ?: voice.language
                                FilterChip(
                                    selected = supertonicVoiceVariant == voice.language,
                                    onClick = { vm.setSupertonicVoiceVariant(voice.language) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = onOpenModelManager, modifier = Modifier.padding(top = Space.xs)) {
                            Text("Download more Supertonic voices in Model Manager", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Speech-to-text", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Choose an engine, or let Vervan use the first one ready.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VoiceChoiceChips(
                        title = "Preferred engine",
                        value = sttEnginePreference,
                        choices = listOf(
                            "AUTO" to "Automatic",
                            "MODEL_AUDIO" to "Active model",
                            "WHISPER_CPP" to "whisper.cpp",
                            "ANDROID" to "Android"
                        ),
                        onSelect = vm::setSttEnginePreference
                    )
                    VoiceToggleRow(
                        "Automatic fallback",
                        "Automatic skips unavailable engines. A manual choice uses only that engine.",
                        sttFallbackEnabled,
                        vm::setSttFallbackEnabled
                    )
                    val modelAudioAvailable = activeGenerationModel?.supportsAudio == true
                    VoiceToggleRow(
                        "Active model audio",
                        when {
                            activeGenerationModel == null -> "Unavailable · no active chat model."
                            modelAudioAvailable -> "Ready · ${activeGenerationModel?.displayName} can transcribe up to 30 seconds."
                            else -> "Unavailable · ${activeGenerationModel?.displayName} does not support audio input."
                        },
                        modelAudioSttEnabled,
                        vm::setModelAudioSttEnabled
                    )
                    val whisperModelVariant by vm.whisperModelVariant.collectAsState()
                    val whisperModel = downloadedVoiceModels.firstOrNull {
                        it.engine.equals("WHISPER_CPP", ignoreCase = true) &&
                            it.language.equals(whisperModelVariant, ignoreCase = true) && it.isReady
                    }
                    val whisperAvailable = com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE &&
                        com.vervan.chat.voice.WhisperCppSttEngine.findInstalledModelFile(
                            app, whisperModel?.filePath, whisperModelVariant
                        ) != null
                    VoiceToggleRow(
                        "whisper.cpp",
                        when {
                            whisperAvailable -> "Ready · private, on-device transcription."
                            // Distinct from "no model yet" — no model download fixes this; the
                            // native library itself wasn't compiled into this build.
                            !com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE -> "Unavailable · not built into this app."
                            else -> "Unavailable · download or import a whisper.cpp model."
                        },
                        inbuiltSttEnabled,
                        vm::setInbuiltSttEnabled
                    )
                    VoiceToggleRow(
                        "Android speech service",
                        if (androidRecognizerAvailable) {
                            "Ready · prefers the device's on-device recognizer. Original audio cannot be saved."
                        } else {
                            "Unavailable · this device has no speech recognition service."
                        },
                        androidSttEnabled,
                        vm::setAndroidSttEnabled
                    )
                    val resolution = com.vervan.chat.voice.SttEnginePolicy.resolve(
                        com.vervan.chat.voice.SttAvailability(
                            speechInputEnabled = speechInputEnabled,
                            preference = sttEnginePreference,
                            fallbackEnabled = sttFallbackEnabled,
                            modelEnabled = modelAudioSttEnabled,
                            modelAvailable = modelAudioAvailable,
                            whisperEnabled = inbuiltSttEnabled,
                            whisperAvailable = whisperAvailable,
                            androidEnabled = androidSttEnabled,
                            androidAvailable = androidRecognizerAvailable
                        )
                    )
                    Text(
                        if (resolution.isAvailable) {
                            "Current route: " + resolution.candidates.joinToString(" → ") { it.label }
                        } else {
                            resolution.unavailableReason ?: "Speech input is unavailable"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (resolution.isAvailable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Space.md)
                    )
                    // Detail block for the whisper.cpp row above — only worth showing (including
                    // the "not built into this build" case) when the user actually has that
                    // engine turned on. It used to render unconditionally, so anyone using Android
                    // speech service or active-model audio successfully still saw a red
                    // "whisper.cpp unavailable" banner that had nothing to do with their setup.
                    if (inbuiltSttEnabled && !com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                        Text(
                            "whisper.cpp is unavailable in this build. Use another speech engine.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    } else if (inbuiltSttEnabled) {
                        val whisperFile = com.vervan.chat.voice.WhisperCppSttEngine
                            .findInstalledModelFile(app, whisperModel?.filePath, whisperModelVariant)
                        val lastBackend = remember { vm.whisperLastKnownBackend() }
                        Text(
                            when {
                                whisperFile == null -> "whisper.cpp model: not downloaded"
                                lastBackend != null -> "whisper.cpp model: ready — last ran on $lastBackend"
                                else -> "whisper.cpp model: ready"
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                        val installedWhisperModels = downloadedVoiceModels.filter {
                            it.engine.equals("WHISPER_CPP", ignoreCase = true) && it.isReady
                        }
                        if (installedWhisperModels.size > 1) {
                            Text(
                                "Model size",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = Space.md)
                            )
                            Text(
                                "Tiny/Base: fastest, least accurate. Small: balanced. Larger models are slower and use more memory.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.padding(top = Space.sm),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
                            ) {
                                installedWhisperModels.forEach { model ->
                                    val label = com.vervan.chat.modeldownload.ModelCatalog.all
                                        .find { it.ttsEngine == "WHISPER_CPP" && it.ttsLanguage == model.language }
                                        ?.displayName?.substringBefore(" —") ?: model.language
                                    FilterChip(
                                        selected = whisperModelVariant == model.language,
                                        onClick = { vm.setWhisperModelVariant(model.language) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = Space.xs)
                    ) { Text("Download or import in Model Manager") }

                    if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                        val gpuDisabledAfterCrash = remember { vm.whisperGpuDisabledAfterCrash() }
                        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Try GPU for whisper.cpp (experimental)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (gpuDisabledAfterCrash) {
                                        "GPU failed on this device and is disabled. Voice recognition now uses the CPU."
                                    } else {
                                        "Experimental. If GPU fails, Vervan switches back to CPU."
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = whisperGpuEnabled,
                                onCheckedChange = { vm.setWhisperGpuEnabled(it) },
                                enabled = !gpuDisabledAfterCrash
                            )
                        }
                    }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Voice models", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Manage downloaded Hindi and English voices in Model Manager.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = Space.sm)
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
                            Modifier.fillMaxWidth().padding(top = Space.sm),
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

@Composable
private fun VoiceSettingsHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = Space.xs, top = Space.md, bottom = Space.xs)
    )
}

@Composable
private fun VoiceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VoiceChoiceChips(
    title: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            choices.forEach { (key, label) ->
                FilterChip(
                    selected = value == key,
                    onClick = { onSelect(key) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun VoiceSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OverflowTooltipText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}
