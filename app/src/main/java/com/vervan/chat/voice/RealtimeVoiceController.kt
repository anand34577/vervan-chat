package com.vervan.chat.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.ContinuousAudioCapture
import com.vervan.chat.audio.VoiceActivityDetector
import com.vervan.chat.data.db.entities.ModelRole
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** One turn in the voice thread. [waveform] (bucketed, normalized 0..1 amplitude bars) and
 * [audioSamples] are populated once real audio exists for the turn — immediately for a
 * captured user utterance, asynchronously (after playback finishes) for an assistant reply, so
 * the reply text can render as soon as it's ready without waiting on TTS to catch up. */
data class VoiceTurn(
    val fromUser: Boolean,
    val text: String,
    val waveform: List<Float> = emptyList(),
    val durationMs: Int = 0,
    val audioSamples: ShortArray? = null,
    val sampleRateHz: Int = 0,
    val transcribedOnDevice: Boolean = false,
    val isStreaming: Boolean = false,
    val audioPending: Boolean = false,
    val id: String = UUID.randomUUID().toString()
)

enum class VoiceControllerState { IDLE, LOADING_MODEL, LISTENING, THINKING, SPEAKING }

/**
 * The realtime voice pipeline's glue: continuous mic capture -> VAD endpointing -> STT
 * (existing Gemma audio-direct path when the model supports it, else Android's non-modal
 * [SpeechRecognizer]) -> [com.vervan.chat.llm.LlmEngine] streaming generation ->
 * [SentenceChunker] -> [TtsEngineSelector] -> [TtsPlaybackQueue], with barge-in: while TTS is
 * playing, the same VAD keeps classifying the live (echo-cancelled) capture stream, and
 * sustained speech interrupts playback and starts a new listening cycle.
 *
 * One controller instance = one voice session, started by [start] and torn down by [stop].
 * Not persisted to the Chat database — mirrors the existing [com.vervan.chat.ui.tools.VoiceChatScreen]
 * behavior of an ephemeral, in-memory transcript.
 */
class RealtimeVoiceController(private val app: VervanApp) {
    private val context: Context get() = app

    private val audioCapture = ContinuousAudioCapture()
    private val vad = VoiceActivityDetector(app)
    private val engineSelector = TtsEngineSelector(
        app.container.settingsRepository,
        PiperTtsEngine(app.container.db.ttsVoiceModelDao()),
        AndroidSystemTtsEngine(app),
        KokoroTtsEngine(app.container.db.ttsVoiceModelDao())
    )
    private lateinit var playbackQueue: TtsPlaybackQueue
    private var controllerScope: CoroutineScope? = null
    private var sessionJob: Job? = null
    private var generationJob: Job? = null
    @Volatile private var finishListeningRequested = false
    private var activeSpeechRecognizer: SpeechRecognizer? = null
    private var completedDeviceWaveform: List<Float> = emptyList()

    private val _state = MutableStateFlow(VoiceControllerState.IDLE)
    val state: StateFlow<VoiceControllerState> = _state

    private val _turns = MutableStateFlow<List<VoiceTurn>>(emptyList())
    val turns: StateFlow<List<VoiceTurn>> = _turns

    /** Which STT/TTS path is active, for the UI badges — mirrors the existing
     * "STT: ..." badge pattern in [com.vervan.chat.ui.tools.VoiceChatScreen]. */
    private val _sttLabel = MutableStateFlow("Device (offline)")
    val sttLabel: StateFlow<String> = _sttLabel
    private val _ttsLabel = MutableStateFlow("Android system")
    val ttsLabel: StateFlow<String> = _ttsLabel

    private val _hasEchoCancellation = MutableStateFlow(true)
    val hasEchoCancellation: StateFlow<Boolean> = _hasEchoCancellation

    /** Rolling amplitude bars + elapsed time for the live "Listening…" bubble — updated per
     * frame while [captureUntilSilence] runs, cleared once it returns. */
    private val _liveWaveform = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform
    private val _liveElapsedMs = MutableStateFlow(0)
    val liveElapsedMs: StateFlow<Int> = _liveElapsedMs

    /** Name of the model being loaded, while [state] is [VoiceControllerState.LOADING_MODEL]. */
    private val _loadingModelName = MutableStateFlow<String?>(null)
    val loadingModelName: StateFlow<String?> = _loadingModelName

    /** Set if the preload in [runSession] fails — cleared on the next [start]. Surfaced by the
     * UI as a dismissible/retryable error instead of silently leaving the mic dead. */
    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError
    private val _playbackPaused = MutableStateFlow(false)
    val playbackPaused: StateFlow<Boolean> = _playbackPaused

    fun start(scope: CoroutineScope) {
        if (sessionJob?.isActive == true) return
        _modelLoadError.value = null
        controllerScope = scope
        playbackQueue = TtsPlaybackQueue(app, engineSelector, scope)
        sessionJob = scope.launch(Dispatchers.Default) { runSession() }
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        generationJob?.cancel()
        generationJob = null
        controllerScope = null
        if (::playbackQueue.isInitialized) playbackQueue.release()
        audioCapture.stop()
        runCatching { activeSpeechRecognizer?.destroy() }
        activeSpeechRecognizer = null
        vad.release()
        _state.value = VoiceControllerState.IDLE
        _liveWaveform.value = emptyList()
        _liveElapsedMs.value = 0
        _loadingModelName.value = null
        _playbackPaused.value = false
        finishListeningRequested = false
        _turns.update { turns -> turns.map { it.copy(isStreaming = false, audioPending = false) } }
    }

    /** Finishes only the current user turn; [stop] ends the whole voice session. */
    fun finishListening() {
        if (_state.value != VoiceControllerState.LISTENING) return
        finishListeningRequested = true
        runCatching { activeSpeechRecognizer?.stopListening() }
    }

    fun togglePlaybackPause() {
        if (_state.value != VoiceControllerState.SPEAKING || !::playbackQueue.isInitialized) return
        _playbackPaused.value = playbackQueue.togglePause()
    }

    /** Manual escape hatch for devices without hardware echo cancellation, or when the user
     * just wants to cut a reply short without relying on barge-in detection. */
    fun manualInterrupt() {
        if (_state.value != VoiceControllerState.SPEAKING) return
        generationJob?.cancel()
        if (::playbackQueue.isInitialized) playbackQueue.stop()
        _playbackPaused.value = false
    }

    private suspend fun runSession() {
        vad.load()
        audioCapture.start(CAPTURE_FRAME_MS)
        val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
        val modelSupportsAudio = model?.supportsAudio == true
        _sttLabel.value = if (modelSupportsAudio) "Model audio input" else "Device (offline)"

        // Preload the generation model up front, with a visible spinner, instead of loading it
        // lazily on the first reply (where "Thinking…" would silently include a multi-second
        // native model load with no indication that's what's actually happening).
        if (model != null) {
            val alreadyLoaded = app.container.withLlm { it.loadedModelPath == model.filePath }
            if (!alreadyLoaded) {
                _state.value = VoiceControllerState.LOADING_MODEL
                _loadingModelName.value = model.displayName
                try {
                    app.container.withLlm { it.load(model.filePath) }
                } catch (t: Throwable) {
                    _modelLoadError.value = t.message ?: "Could not load ${model.displayName}"
                    _state.value = VoiceControllerState.IDLE
                    audioCapture.stop()
                    vad.release()
                    if (::playbackQueue.isInitialized) playbackQueue.release()
                    return
                } finally {
                    _loadingModelName.value = null
                }
            }
        }

        while (true) {
            _state.value = VoiceControllerState.LISTENING
            val userText: String?
            val audioPathForModel: String?

            if (modelSupportsAudio) {
                val captured = captureUntilSilence()
                if (captured.pcm.isEmpty()) continue
                val wavFile = writePcmToWav(captured.pcm)
                userText = null
                audioPathForModel = wavFile.absolutePath
                _turns.value = _turns.value + VoiceTurn(
                    fromUser = true, text = "Audio sent directly to the model",
                    waveform = buildWaveform(captured.pcm), durationMs = captured.durationMs,
                    audioSamples = captured.pcm, sampleRateHz = VoiceActivityDetector.SAMPLE_RATE_HZ,
                    transcribedOnDevice = false
                )
            } else {
                val sttStart = System.currentTimeMillis()
                val text = listenViaDeviceStt() ?: continue
                if (text.isBlank()) continue
                userText = text
                audioPathForModel = null
                _turns.value = _turns.value + VoiceTurn(
                    fromUser = true, text = text,
                    waveform = completedDeviceWaveform,
                    durationMs = (System.currentTimeMillis() - sttStart).toInt(),
                    transcribedOnDevice = true
                )
            }

            _state.value = VoiceControllerState.THINKING
            respondAndSpeak(userText, audioPathForModel)
        }
    }

    private suspend fun respondAndSpeak(userText: String?, audioPath: String?) {
        val prompt = userText
            ?: "Respond to the user's spoken message conversationally and concisely, in the same language they used."

        val turnId = UUID.randomUUID().toString()
        val turnSamples = ArrayList<Short>()
        var turnSampleRate = 0
        var enteredSpeaking = false
        playbackQueue.startTurn { samples, sampleRateHz ->
            if (!enteredSpeaking) {
                enteredSpeaking = true
                _state.value = VoiceControllerState.SPEAKING
            }
            turnSampleRate = sampleRateHz
            synchronized(turnSamples) { for (s in samples) turnSamples.add(s) }
            val snapshot = synchronized(turnSamples) { turnSamples.toShortArray() }
            _turns.update { turns -> turns.map { turn ->
                if (turn.id == turnId) turn.copy(
                    waveform = buildWaveform(snapshot),
                    durationMs = (snapshot.size * 1000L / sampleRateHz).toInt()
                ) else turn
            } }
        }
        _ttsLabel.value = engineSelector.resolve().engineName
        var replyText = ""
        val chunker = SentenceChunker(playbackQueue::enqueue)

        val scope = controllerScope
        val bargeInWatcher = maybeStartBargeInWatcher()
        val job = scope?.launch(Dispatchers.Default) {
            app.container.withLlm { engine ->
                val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
                if (model != null && engine.loadedModelPath != model.filePath) engine.load(model.filePath)
                engine.generate(prompt, audioPath = audioPath).collect { token ->
                    replyText += token
                    _turns.update { current ->
                        if (current.any { it.id == turnId }) {
                            current.map { turn -> if (turn.id == turnId) turn.copy(text = replyText) else turn }
                        } else {
                            current + VoiceTurn(
                                fromUser = false,
                                text = replyText,
                                isStreaming = true,
                                audioPending = true,
                                id = turnId
                            )
                        }
                    }
                    chunker.append(token)
                }
            }
        }
        generationJob = job
        // Deliberately NOT wrapped in runCatching: Job.join() only throws if THIS coroutine
        // (sessionJob, via runSession's while loop) is itself being cancelled — e.g. the user
        // called RealtimeVoiceController.stop(). Swallowing that would let this function keep
        // running as a "zombie" after stop() was supposed to end everything. When barge-in
        // cancels `job` specifically (not this coroutine), join() returns normally, which is
        // exactly when the flush/cleanup below is meant to run.
        job?.join()
        chunker.flush()
        playbackQueue.endTurn()
        generationJob = null

        // Append the text immediately — readable before TTS finishes, per the spec's "read
        // ahead of the audio" requirement. The playback bar's audio/waveform is patched onto
        // this same turn (by id) once playback actually finishes, below.
        _turns.update { turns -> turns.map { turn ->
            if (turn.id == turnId) turn.copy(text = replyText.trim(), isStreaming = false) else turn
        } }

        playbackQueue.awaitCompletion()
        bargeInWatcher?.cancel()
        _playbackPaused.value = false
        val samples = synchronized(turnSamples) { turnSamples.toShortArray() }
        _turns.update { turns -> turns.map { turn ->
            if (turn.id != turnId) turn
            else if (samples.isEmpty() || turnSampleRate == 0) turn.copy(audioPending = false)
            else turn.copy(
                waveform = buildWaveform(samples),
                durationMs = (samples.size * 1000L / turnSampleRate).toInt(),
                audioSamples = samples,
                sampleRateHz = turnSampleRate,
                audioPending = false
            )
        } }
    }

    /** Continuous, echo-cancelled listening while TTS plays: sustained speech interrupts
     * playback and cancels the in-flight reply. Only runs when barge-in is enabled AND this
     * device actually has hardware echo cancellation — otherwise the mic simply stays off
     * while TTS talks, and [manualInterrupt] is the only way to cut a reply short. */
    private fun maybeStartBargeInWatcher(): Job? {
        val scope = controllerScope ?: return null
        return scope.launch(Dispatchers.Default) {
            if (!app.container.settingsRepository.bargeInEnabled.first()) return@launch
            var speechFrames = 0
            audioCapture.frames.takeWhile { frame ->
                _hasEchoCancellation.value = audioCapture.hasEchoCancellation
                if (!audioCapture.hasEchoCancellation) return@takeWhile false
                val speaking = vad.isSpeech(frame)
                speechFrames = if (speaking) speechFrames + 1 else 0
                val triggered = speechFrames * CAPTURE_FRAME_MS >= BARGE_IN_TRIGGER_MS
                if (triggered) {
                    generationJob?.cancel()
                    playbackQueue.stop()
                }
                !triggered
            }.collect { }
        }
    }

    private data class CapturedUtterance(val pcm: ShortArray, val durationMs: Int)

    private suspend fun captureUntilSilence(): CapturedUtterance {
        finishListeningRequested = false
        val collected = ArrayList<Short>()
        var sawSpeech = false
        var silenceMs = 0
        var elapsedMs = 0
        _liveWaveform.value = emptyList()
        _liveElapsedMs.value = 0
        audioCapture.frames.takeWhile { frame ->
            val speaking = vad.isSpeech(frame)
            if (speaking) {
                sawSpeech = true
                silenceMs = 0
                collected.addAll(frame.toList())
            } else if (sawSpeech) {
                silenceMs += CAPTURE_FRAME_MS
                collected.addAll(frame.toList())
            }
            elapsedMs += CAPTURE_FRAME_MS
            _liveWaveform.value = (_liveWaveform.value + frameLevel(frame)).takeLast(LIVE_WAVEFORM_BARS)
            _liveElapsedMs.value = elapsedMs
            val done = finishListeningRequested || (sawSpeech && silenceMs >= TRAILING_SILENCE_MS) || elapsedMs >= MAX_UTTERANCE_MS
            !done
        }.collect { }
        finishListeningRequested = false
        _liveWaveform.value = emptyList()
        return CapturedUtterance(collected.toShortArray(), elapsedMs)
    }

    /** Normalized 0..1 loudness for one frame, for the live "Listening…" waveform — 6000 is an
     * empirical reference level for comfortable speaking volume at 16kHz/PCM16, not a hard limit
     * (louder frames just clip to 1f). */
    private fun frameLevel(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sumSq = 0.0
        for (s in frame) sumSq += s.toDouble() * s
        val rms = kotlin.math.sqrt(sumSq / frame.size)
        return (rms / 6000.0).toFloat().coerceIn(0.05f, 1f)
    }

    /** Buckets [samples] into [barCount] normalized 0..1 amplitude bars for a finished
     * turn's waveform display (recording or playback). */
    private fun buildWaveform(samples: ShortArray, barCount: Int = 32): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val bucketSize = (samples.size / barCount).coerceAtLeast(1)
        val bars = ArrayList<Float>(barCount)
        var i = 0
        while (i < samples.size && bars.size < barCount) {
            val end = (i + bucketSize).coerceAtMost(samples.size)
            var sumSq = 0.0
            for (j in i until end) sumSq += samples[j].toDouble() * samples[j]
            val rms = kotlin.math.sqrt(sumSq / (end - i).coerceAtLeast(1))
            bars.add((rms / 32768.0).toFloat().coerceIn(0f, 1f))
            i = end
        }
        val max = bars.maxOrNull()?.takeIf { it > 0.0001f } ?: 1f
        return bars.map { (it / max).coerceIn(0.05f, 1f) }
    }

    private fun writePcmToWav(samples: ShortArray): File {
        val dir = File(app.filesDir, "audio").apply { mkdirs() }
        val file = File(dir, "realtime-${UUID.randomUUID()}.wav")
        file.writeBytes(WavPcmDecoder.encode(samples, VoiceActivityDetector.SAMPLE_RATE_HZ))
        return file
    }

    /** Android's non-modal [SpeechRecognizer]/[RecognitionListener] API — unlike the modal
     * [RecognizerIntent] dialog the composer's dictate button uses, this runs in the
     * background so the continuous session loop doesn't need to show a system UI every turn. */
    private suspend fun listenViaDeviceStt(): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            activeSpeechRecognizer = recognizer
            finishListeningRequested = false
            val startedAt = System.currentTimeMillis()
            _liveWaveform.value = emptyList()
            _liveElapsedMs.value = 0
            completedDeviceWaveform = emptyList()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            fun finish(text: String?) {
                if (cont.isActive) cont.resume(text)
                if (activeSpeechRecognizer === recognizer) activeSpeechRecognizer = null
                completedDeviceWaveform = _liveWaveform.value
                _liveWaveform.value = emptyList()
                recognizer.destroy()
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    finish(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                }
                override fun onError(error: Int) = finish(null)
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _liveWaveform.value = (_liveWaveform.value + level).takeLast(LIVE_WAVEFORM_BARS)
                    _liveElapsedMs.value = (System.currentTimeMillis() - startedAt).toInt()
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            cont.invokeOnCancellation {
                if (activeSpeechRecognizer === recognizer) activeSpeechRecognizer = null
                _liveWaveform.value = emptyList()
                recognizer.destroy()
            }
            recognizer.startListening(intent)
        }
    }

    companion object {
        /** Frame duration for the one shared [ContinuousAudioCapture] stream — both STT
         * endpointing and barge-in detection read frames at this size. */
        private const val CAPTURE_FRAME_MS = 20
        private const val TRAILING_SILENCE_MS = 600
        private const val MAX_UTTERANCE_MS = 30_000
        private const val BARGE_IN_TRIGGER_MS = 300
        private const val LIVE_WAVEFORM_BARS = 40
    }
}
