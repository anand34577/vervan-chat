package com.vervan.chat.ui.tools

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.TtsProject
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.voice.KokoroTtsEngine
import com.vervan.chat.voice.Mp4aEncoder
import com.vervan.chat.system.pruneOldExports
import com.vervan.chat.voice.PiperTtsEngine
import com.vervan.chat.voice.SupertonicTtsEngine
import com.vervan.chat.voice.TtsEngine
import com.vervan.chat.voice.TtsFileGenerator
import com.vervan.chat.voice.WavPcmDecoder
import com.vervan.chat.model.readBytesLimited
import com.vervan.chat.validation.InputLimits
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backs the Text-to-Speech screen (acceptance criteria §6/§14/§15): pick an engine + voice
 * (defaulting to the app-wide TTS default from Voice Settings, overridable per conversion
 * without changing that default — same one-time-override pattern as
 * [TranscriptionViewModel.transcribe]), convert text to audio sentence by sentence (so a failed
 * sentence can be retried without redoing the whole document), play it back, export/share, and
 * keep a history of past conversions.
 *
 * Deferred from the full spec for this first pass: pitch shifting and true playback-speed-
 * independent-of-pitch (needs real audio DSP — a naive resample changes pitch and speed
 * together, which isn't what "speed control" or "pitch control" mean), and MP3/FLAC export (no
 * license-free encoder ships with Android; M4A/AAC covers the same need with the built-in one).
 */
class TextToSpeechViewModel(private val app: VervanApp) : ViewModel() {
    private val voiceModelDao = app.container.db.ttsVoiceModelDao()
    private val settings = app.container.settingsRepository
    private val projectDao = app.container.db.ttsProjectDao()
    private val piper = PiperTtsEngine(voiceModelDao)
    private val kokoro = KokoroTtsEngine(voiceModelDao)
    private val supertonic = SupertonicTtsEngine(voiceModelDao, settings)

    val installedVoiceModels: StateFlow<List<TtsVoiceModel>> =
        voiceModelDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ttsEnginePreference: StateFlow<String> = settings.ttsEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val supertonicVoiceVariant: StateFlow<String> = settings.supertonicVoiceVariant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "multi")

    val projects: StateFlow<List<TtsProject>> =
        projectDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    sealed interface Phase {
        data object Idle : Phase
        /** The chosen engine's first-use native model load (e.g. Supertonic's 4 ONNX sessions +
         * self-test) — distinct from [Generating] so a slow warm-up doesn't look identical to
         * "generating sentence 0 of 0", which read as a frozen/stuck app rather than a one-time
         * load in progress. */
        data object LoadingEngine : Phase
        data class Generating(val sentenceIndex: Int, val total: Int) : Phase
        /** At least one sentence still needs [retrySentence] before [finishAnyway]/auto-merge —
         * see [sentenceResults] for which. */
        data object ReviewingResults : Phase
        data class Done(val file: File) : Phase
        data class Failed(val message: String) : Phase
    }
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase
    private val _sentenceResults = MutableStateFlow<List<TtsFileGenerator.SentenceResult>>(emptyList())
    val sentenceResults: StateFlow<List<TtsFileGenerator.SentenceResult>> = _sentenceResults

    private var job: Job? = null
    // Session state kept across a generate() -> possibly several retrySentence() calls -> finish,
    // since the SUPERTONIC-voice-override must stay in effect for the whole session, not just
    // the initial synthesis pass (a retry can happen long after generate()'s own coroutine ends).
    private var sessionEngine: TtsEngine? = null
    private var sessionEngineName: String = ""
    private var sessionVoiceVariant: String = ""
    private var sessionLang: String = "auto"
    private var sessionTitle: String = ""
    private var sessionOverriding = false
    private var sessionPreviousVoice: String? = null

    private fun engineFor(name: String): TtsEngine = when (name) {
        "KOKORO" -> kokoro
        "SUPERTONIC" -> supertonic
        else -> piper
    }

    /** [engineName] is "PIPER"/"KOKORO"/"SUPERTONIC" (the screen's own explicit choice — unlike
     * [com.vervan.chat.voice.TtsEngineSelector], this never falls back automatically, since a
     * one-shot conversion should use exactly the voice the user picked). [supertonicVoice], if
     * set, is a one-time override of [SettingsRepository.supertonicVoiceVariant] — restored once
     * the whole session (including any retries) finishes, so this never changes the global
     * default. Sentences that fail to synthesize leave [phase] at [Phase.ReviewingResults]
     * instead of failing the whole conversion — see [sentenceResults]/[retrySentence]. */
    fun generate(text: String, engineName: String, lang: String, supertonicVoice: String?, pauseMs: Int) {
        if (text.isBlank()) return
        if (text.length > InputLimits.TTS_TEXT_CHARS) {
            _phase.value = Phase.Failed("Text is too long for speech (maximum ${InputLimits.TTS_TEXT_CHARS} characters).")
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _phase.value = Phase.LoadingEngine
            _sentenceResults.value = emptyList()
            val previousVoice = settings.supertonicVoiceVariant.first()
            val overriding = engineName == "SUPERTONIC" && supertonicVoice != null && supertonicVoice != previousVoice
            if (overriding) settings.setSupertonicVoiceVariant(supertonicVoice!!)
            sessionOverriding = overriding
            sessionPreviousVoice = previousVoice
            sessionEngineName = engineName
            sessionVoiceVariant = supertonicVoice ?: previousVoice
            sessionLang = lang
            sessionTitle = text.trim().take(60).ifBlank { "Untitled" }
            try {
                val engine = engineFor(engineName)
                sessionEngine = engine
                if (!engine.isReady()) {
                    _phase.value = Phase.Failed("The selected voice isn't downloaded yet. Download it in Model Manager first.")
                    restoreOverride()
                    return@launch
                }
                val sentences = TtsFileGenerator.splitSentences(text)
                if (sentences.isEmpty()) {
                    _phase.value = Phase.Failed("No text to convert")
                    restoreOverride()
                    return@launch
                }
                _phase.value = Phase.Generating(0, sentences.size)
                val results = TtsFileGenerator.synthesizeSentences(sentences, engine, lang) { p ->
                    _phase.value = Phase.Generating(p.sentenceIndex, p.totalSentences)
                }
                _sentenceResults.value = results
                if (results.all { it.audio != null }) finishMerge(text, pauseMs) else _phase.value = Phase.ReviewingResults
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                _phase.value = Phase.Failed(t.message ?: "Could not generate audio.")
                restoreOverride()
            }
        }
    }

    /** Re-synthesizes just [index] — the same engine/voice/language the rest of the session
     * used, so a retry can't silently produce a sentence in a different voice from its
     * neighbors. */
    fun retrySentence(index: Int) {
        val engine = sessionEngine ?: return
        viewModelScope.launch {
            val current = _sentenceResults.value
            val target = current.getOrNull(index) ?: return@launch
            val audio = TtsFileGenerator.retrySentence(target.text, engine, sessionLang)
            _sentenceResults.value = current.toMutableList().also { it[index] = target.copy(audio = audio) }
        }
    }

    /** Merges whatever sentences currently have audio, silently skipping any that still don't —
     * for a user who'd rather ship a document with one dropped sentence than keep retrying. */
    fun finishAnyway(sourceText: String, pauseMs: Int) {
        job?.cancel()
        job = viewModelScope.launch { finishMerge(sourceText, pauseMs) }
    }

    private suspend fun finishMerge(sourceText: String, pauseMs: Int) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val outFile = File(File(app.filesDir, "tts_output").apply { mkdirs() }, "tts-$stamp.wav")
            withContext(Dispatchers.IO) { TtsFileGenerator.mergeToFile(_sentenceResults.value, outFile, pauseMs) }
            val totalSamples = _sentenceResults.value.sumOf { it.audio?.samples?.size ?: 0 }
            val sampleRate = _sentenceResults.value.firstNotNullOfOrNull { it.audio?.sampleRateHz } ?: 22050
            projectDao.upsert(
                TtsProject(
                    title = sessionTitle, sourceText = sourceText, engine = sessionEngineName,
                    voiceVariant = sessionVoiceVariant, language = sessionLang,
                    audioPath = outFile.absolutePath, durationMs = totalSamples * 1000L / sampleRate
                )
            )
            _phase.value = Phase.Done(outFile)
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            Log.e(TAG, "finishMerge failed", t)
            _phase.value = Phase.Failed(t.message ?: "Could not save audio.")
        } finally {
            restoreOverride()
        }
    }

    private suspend fun restoreOverride() {
        if (sessionOverriding) settings.setSupertonicVoiceVariant(sessionPreviousVoice ?: "multi")
        sessionOverriding = false
    }

    fun cancel() {
        job?.cancel()
        job = null
        viewModelScope.launch { restoreOverride() }
        _phase.value = Phase.Idle
        _sentenceResults.value = emptyList()
    }

    // A share-only byproduct, not tracked by any TtsProject row the way the source .wav is (see
    // deleteProject below) — writing it into the same tts_output directory as the tracked .wav
    // files with no owner responsible for cleanup left one orphaned .m4a per share forever. Kept
    // in its own subdirectory and self-pruned by age on every write instead, same pattern as
    // TranscriptionViewModel.pruneOldExports uses for its own one-off export artifacts.
    suspend fun exportM4a(wavFile: File): File = withContext(Dispatchers.IO) {
        require(wavFile.isFile) { "Audio export file is missing" }
        require(wavFile.length() <= InputLimits.MAX_DECODED_AUDIO_BYTES) { "Audio export is too large" }
        val audio = wavFile.inputStream().use { WavPcmDecoder.decode(it.readBytesLimited(InputLimits.MAX_DECODED_AUDIO_BYTES)) }
        val dir = File(app.filesDir, "tts_output/exports").apply { mkdirs() }
        val outFile = File(dir, wavFile.nameWithoutExtension + ".m4a")
        Mp4aEncoder.encode(audio.samples, audio.sampleRateHz, outFile)
        pruneOldExports(dir)
        outFile
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            val project = projectDao.get(id)
            projectDao.deleteById(id)
            project?.let { File(it.audioPath).delete() }
        }
    }

    override fun onCleared() {
        piper.release()
        kokoro.release()
        supertonic.release()
    }

    companion object {
        private const val TAG = "TextToSpeechViewModel"
    }
}
