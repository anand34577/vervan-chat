package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.runtime.setValue
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

    val ttsEnginePreference by vm.ttsEnginePreference.collectAsStateWithLifecycle()
    val bargeInEnabled by vm.bargeInEnabled.collectAsStateWithLifecycle()
    val inbuiltSttEnabled by vm.inbuiltSttEnabled.collectAsStateWithLifecycle()
    val modelAudioSttEnabled by vm.modelAudioSttEnabled.collectAsStateWithLifecycle()
    val androidSttEnabled by vm.androidSttEnabled.collectAsStateWithLifecycle()
    val sttEnginePreference by vm.sttEnginePreference.collectAsStateWithLifecycle()
    val sttFallbackEnabled by vm.sttFallbackEnabled.collectAsStateWithLifecycle()
    val whisperGpuEnabled by vm.whisperGpuEnabled.collectAsStateWithLifecycle()
    val downloadedVoiceModels by vm.downloadedVoiceModels.collectAsStateWithLifecycle()
    val activeVoiceDownloadJobs by vm.activeVoiceDownloadJobs.collectAsStateWithLifecycle()
    val speechInputEnabled by vm.speechInputEnabled.collectAsStateWithLifecycle()
    val voiceReplyMode by vm.voiceReplyMode.collectAsStateWithLifecycle()
    val voiceInputMethod by vm.voiceInputMethod.collectAsStateWithLifecycle()
    val transcriptReviewEnabled by vm.transcriptReviewEnabled.collectAsStateWithLifecycle()
    val handsFreeAutoSend by vm.handsFreeAutoSend.collectAsStateWithLifecycle()
    val continueListening by vm.continueListening.collectAsStateWithLifecycle()
    val headphonesOnlyPlayback by vm.headphonesOnlyPlayback.collectAsStateWithLifecycle()
    val headphonePrivacyPause by vm.headphonePrivacyPause.collectAsStateWithLifecycle()
    val voiceInputLanguage by vm.voiceInputLanguage.collectAsStateWithLifecycle()
    val vadSensitivity by vm.vadSensitivity.collectAsStateWithLifecycle()
    val voiceSilenceDurationMs by vm.voiceSilenceDurationMs.collectAsStateWithLifecycle()
    val maxUtteranceSeconds by vm.maxUtteranceSeconds.collectAsStateWithLifecycle()
    val storeVoiceRecordings by vm.storeVoiceRecordings.collectAsStateWithLifecycle()
    val voiceSpeechRate by vm.voiceSpeechRate.collectAsStateWithLifecycle()
    val voiceSpeechPitch by vm.voiceSpeechPitch.collectAsStateWithLifecycle()
    val readCodeMode by vm.readCodeMode.collectAsStateWithLifecycle()
    val readTableMode by vm.readTableMode.collectAsStateWithLifecycle()
    val longResponseVoiceMode by vm.longResponseVoiceMode.collectAsStateWithLifecycle()
    val backgroundVoiceEnabled by vm.backgroundVoiceEnabled.collectAsStateWithLifecycle()
    val voiceBatterySaver by vm.voiceBatterySaver.collectAsStateWithLifecycle()
    val transcriptRetentionEnabled by vm.transcriptRetentionEnabled.collectAsStateWithLifecycle()
    val recordingRetentionMode by vm.recordingRetentionMode.collectAsStateWithLifecycle()
    val activeGenerationModel by vm.activeGenerationModel.collectAsStateWithLifecycle()
    val voiceQualityPreset by vm.voiceQualityPreset.collectAsStateWithLifecycle()
    val androidRecognizerAvailable = remember {
        com.vervan.chat.voice.AndroidSystemSttRecognizer.isAvailable(app)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_settings_voice_speech)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.GraphicEq,
                eyebrow = "Natural conversation",
                title = stringResource(R.string.voice_settings_tune_listening_and_speaking),
                body = stringResource(R.string.voice_settings_choose_how_vervan_hears_responds)
            )
            VoiceSettingsHeading("Voice quality")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    VoiceChoiceChips(
                        title = stringResource(R.string.voice_settings_how_should_vervan_sound_and),
                        value = voiceQualityPreset,
                        choices = listOf("FAST" to "Fast", "BALANCED" to "Balanced", "BEST" to "Best"),
                        onSelect = vm::setVoiceQualityPreset
                    )
                    Text(
                        when (voiceQualityPreset) {
                            "FAST" -> stringResource(R.string.voice_settings_quickest_replies_skips_the_slower)
                            "BEST" -> stringResource(R.string.voice_settings_highest_quality_voice_kokoro_or)
                            else -> stringResource(R.string.voice_settings_the_default_balance_of_speed)
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
                        title = stringResource(R.string.voice_settings_input_method),
                        value = voiceInputMethod,
                        choices = listOf("DICTATION" to "Dictation", "PUSH_TO_TALK" to "Push-to-talk", "HANDS_FREE" to "Hands-free"),
                        onSelect = vm::setVoiceInputMethod
                    )
                    VoiceChoiceChips(
                        title = stringResource(R.string.voice_replies),
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
                        title = stringResource(R.string.voice_settings_language),
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
                        stringResource(R.string.voice_settings_active_model_and_whisper_cpp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md)
                    )
                }
            }

            VoiceSettingsHeading("Advanced: engines")
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.voice_settings_realtime_voice_chat_engine), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.voice_settings_auto_uses_piper_choose_kokoro),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val kokoroDownloaded = downloadedVoiceModels.any { it.engine == "KOKORO" && it.language == "multi" && it.isReady }
                    val supertonicDownloaded = downloadedVoiceModels.any { it.engine == "SUPERTONIC" && it.language == "multi" && it.isReady }
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = Space.sm),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
                    ) {
                        com.vervan.chat.ui.common.VervanFilterChip(
                            selected = ttsEnginePreference == "AUTO",
                            onClick = { vm.setTtsEnginePreference("AUTO") },
                            label = { Text(stringResource(R.string.voice_settings_auto_piper)) }
                        )
                        com.vervan.chat.ui.common.VervanFilterChip(
                            selected = ttsEnginePreference == "KOKORO",
                            onClick = { vm.setTtsEnginePreference("KOKORO") },
                            enabled = kokoroDownloaded,
                            label = { Text(if (kokoroDownloaded) stringResource(R.string.voice_settings_kokoro) else stringResource(R.string.voice_settings_kokoro_download_below)) }
                        )
                        com.vervan.chat.ui.common.VervanFilterChip(
                            selected = ttsEnginePreference == "SUPERTONIC",
                            onClick = { vm.setTtsEnginePreference("SUPERTONIC") },
                            enabled = supertonicDownloaded,
                            label = { Text(if (supertonicDownloaded) stringResource(R.string.voice_settings_supertonic) else stringResource(R.string.voice_settings_supertonic_download_in_model_manager)) }
                        )
                        val omniSpeechDownloaded = downloadedVoiceModels.any {
                            it.engine == com.vervan.chat.omni.OmniRuntime.SPEECH_ENGINE && it.isReady
                        }
                        com.vervan.chat.ui.common.VervanFilterChip(
                            selected = ttsEnginePreference == "LFM3_OMNI",
                            onClick = { vm.setTtsEnginePreference("LFM3_OMNI") },
                            enabled = omniSpeechDownloaded,
                            label = { Text(if (omniSpeechDownloaded) "LFM3 Omni voice" else "LFM3 Omni (download speech pack)") }
                        )
                    }
                    if (ttsEnginePreference == "LFM3_OMNI") {
                        val omniVoice by vm.omniTtsVoice.collectAsStateWithLifecycle()
                        Text(
                            "Needs LFM3 Omni 2B too. Slower than Piper and uses about 2 GB more memory while speaking.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                        Row(Modifier.padding(top = Space.xs), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)) {
                            com.vervan.chat.ui.common.VervanFilterChip(selected = omniVoice == "female", onClick = { vm.setOmniTtsVoice("female") }, label = { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.voice_settings_female)) })
                            com.vervan.chat.ui.common.VervanFilterChip(selected = omniVoice == "male", onClick = { vm.setOmniTtsVoice("male") }, label = { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.voice_settings_male)) })
                        }
                    }
                    if (!supertonicDownloaded) {
                        androidx.compose.material3.TextButton(onClick = onOpenModelManager, modifier = Modifier.padding(top = Space.xs)) {
                            Text(stringResource(R.string.voice_settings_open_model_manager_to_download), style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        // Every voice besides "multi" (M1) is a separate ~290 KB download layered
                        // on the shared acoustic model — see SupertonicTtsEngine's class doc. Only
                        // list voices actually installed; the rest are downloadable in Model Manager.
                        val supertonicVoiceVariant by vm.supertonicVoiceVariant.collectAsStateWithLifecycle()
                        val installedVoices = downloadedVoiceModels.filter { it.engine == "SUPERTONIC" && it.isReady }
                        Text(
                            stringResource(R.string.voice_settings_supertonic_voice),
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
                                com.vervan.chat.ui.common.VervanFilterChip(
                                    selected = supertonicVoiceVariant == voice.language,
                                    onClick = { vm.setSupertonicVoiceVariant(voice.language) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = onOpenModelManager, modifier = Modifier.padding(top = Space.xs)) {
                            Text(stringResource(R.string.voice_settings_download_more_supertonic_voices_in), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.voice_settings_speech_to_text), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.voice_settings_choose_an_engine_or_let),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VoiceChoiceChips(
                        title = stringResource(R.string.voice_settings_preferred_engine),
                        value = sttEnginePreference,
                        choices = listOf(
                            "AUTO" to "Automatic",
                            "MODEL_AUDIO" to "Active model",
                            "SHRUTI" to "Shruti",
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
                    val whisperModelVariant by vm.whisperModelVariant.collectAsStateWithLifecycle()
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
                            whisperAvailable -> stringResource(R.string.voice_settings_ready_private_on_device_transcription)
                            // Distinct from "no model yet" — no model download fixes this; the
                            // native library itself wasn't compiled into this build.
                            !com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE -> stringResource(R.string.voice_settings_unavailable_not_built_into_this)
                            else -> stringResource(R.string.voice_settings_unavailable_download_or_import_a)
                        },
                        inbuiltSttEnabled,
                        vm::setInbuiltSttEnabled
                    )
                    val shrutiEnabled by vm.shrutiSttEnabled.collectAsStateWithLifecycle()
                    val shrutiVariant by vm.shrutiModelVariant.collectAsStateWithLifecycle()
                    val installedShrutiModels = downloadedVoiceModels.filter {
                        it.engine.equals(com.vervan.chat.voice.ShrutiSttEngine.ENGINE, ignoreCase = true) && it.isReady
                    }
                    val shrutiRow = installedShrutiModels.firstOrNull { it.language.equals(shrutiVariant, ignoreCase = true) }
                    val shrutiAvailable = com.vervan.chat.BuildConfig.NEEDLE_AVAILABLE &&
                        com.vervan.chat.voice.ShrutiSttEngine.findInstalledModelFile(app, shrutiRow?.filePath, shrutiVariant) != null
                    val shrutiLanguageName = com.vervan.chat.voice.ShrutiSttEngine.LANGUAGES[shrutiVariant] ?: shrutiVariant
                    VoiceToggleRow(
                        "Shruti (Indian languages)",
                        when {
                            !com.vervan.chat.BuildConfig.NEEDLE_AVAILABLE -> "Unavailable · the Shruti engine is not built into this version."
                            shrutiAvailable -> "Ready · $shrutiLanguageName + English, fully on-device."
                            installedShrutiModels.isNotEmpty() -> "Pick one of your downloaded languages below."
                            else -> "Download a Shruti language (Hindi, Marathi, Tamil, Bengali, Telugu) in Models."
                        },
                        shrutiEnabled,
                        { vm.setShrutiSttEnabled(it) }
                    )
                    if (shrutiEnabled && installedShrutiModels.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(top = Space.sm),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
                        ) {
                            installedShrutiModels.forEach { model ->
                                com.vervan.chat.ui.common.VervanFilterChip(
                                    selected = shrutiVariant.equals(model.language, ignoreCase = true),
                                    onClick = { vm.setShrutiModelVariant(model.language) },
                                    label = { Text(com.vervan.chat.voice.ShrutiSttEngine.LANGUAGES[model.language] ?: model.language) }
                                )
                            }
                        }
                        val shrutiEnglishOnly by vm.shrutiEnglishOnly.collectAsStateWithLifecycle()
                        VoiceToggleRow(
                            "English-only speech",
                            "Off detects the language each time (recommended, also for Hinglish).",
                            shrutiEnglishOnly,
                            { vm.setShrutiEnglishOnly(it) }
                        )
                        val savedKeywords by vm.shrutiKeywords.collectAsStateWithLifecycle()
                        var keywordsDraft by remember(savedKeywords) { androidx.compose.runtime.mutableStateOf(savedKeywords) }
                        androidx.compose.material3.OutlinedTextField(
                            value = keywordsDraft,
                            onValueChange = { keywordsDraft = it.take(4_000) },
                            label = { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.voice_settings_terms_label)) },
                            supportingText = { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.voice_settings_terms_help)) },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                        )
                        if (keywordsDraft != savedKeywords) {
                            androidx.compose.material3.TextButton(onClick = { vm.setShrutiKeywords(keywordsDraft) }) { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.voice_settings_terms_save)) }
                        }
                    }
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
                            shrutiEnabled = shrutiEnabled,
                            shrutiAvailable = shrutiAvailable,
                            androidEnabled = androidSttEnabled,
                            androidAvailable = androidRecognizerAvailable
                        )
                    )
                    Text(
                        if (resolution.isAvailable) {
                            "Current route: " + resolution.candidates.joinToString(" → ") { it.label }
                        } else {
                            resolution.unavailableReason ?: stringResource(R.string.voice_settings_speech_input_is_unavailable)
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
                            stringResource(R.string.voice_settings_whisper_cpp_is_unavailable_in),
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
                                stringResource(R.string.voice_settings_model_size),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = Space.md)
                            )
                            Text(
                                stringResource(R.string.voice_settings_tiny_base_fastest_least_accurate),
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
                                    com.vervan.chat.ui.common.VervanFilterChip(
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
                    ) { Text(stringResource(R.string.voice_settings_download_or_import_in_model)) }

                    if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                        val gpuDisabledAfterCrash = remember { vm.whisperGpuDisabledAfterCrash() }
                        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.voice_settings_try_gpu_for_whisper_cpp), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (gpuDisabledAfterCrash) {
                                        "GPU failed on this device and is disabled. Voice recognition now uses the CPU."
                                    } else {
                                        "Experimental. If GPU fails, Vervan switches back to CPU."
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            com.vervan.chat.ui.common.LabeledSwitch(
                                label = stringResource(R.string.voice_settings_try_gpu_for_whisper_cpp),
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
                    Text(stringResource(R.string.voice_settings_voice_models), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.voice_settings_manage_downloaded_hindi_and_english),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onOpenModelManager,
                        modifier = Modifier.padding(top = Space.sm)
                    ) { Text(stringResource(R.string.voice_settings_open_model_manager)) }
                }
            }

            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.voice_settings_higher_quality_voice_optional), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.voice_settings_download_the_optional_kokoro_voice),
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
                                        downloaded -> stringResource(R.string.voice_settings_ready)
                                        activeJob != null -> "Downloading… ${activeJob.progress}%"
                                        else -> stringResource(R.string.voice_settings_not_downloaded)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when {
                                downloaded -> IconButton(onClick = { vm.deleteVoiceModel(entry) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.voice_settings_delete_downloaded_voice))
                                }
                                activeJob != null -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> androidx.compose.material3.TextButton(onClick = { vm.downloadVoiceModel(entry) }) { Text(stringResource(R.string.voice_settings_download)) }
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
        Modifier.fillMaxWidth().toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange).heightIn(min = 48.dp).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
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
                com.vervan.chat.ui.common.VervanFilterChip(
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
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}
