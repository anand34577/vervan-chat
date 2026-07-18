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
import com.vervan.chat.modelload.LoadTrigger
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

enum class VoiceControllerState { IDLE, LOADING_MODEL, LISTENING, TRANSCRIBING, THINKING, SPEAKING }

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
    private val whisperStt = WhisperSttEngine(app.container.db.ttsVoiceModelDao())
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
        whisperStt.release()
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

        // Preload the generation model up front, with a visible spinner, instead of loading it
        // lazily on the first reply (where "Thinking…" would silently include a multi-second
        // native model load with no indication that's what's actually happening).
        if (model != null) {
            val alreadyLoaded = app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId == model.id
            if (!alreadyLoaded) {
                _state.value = VoiceControllerState.LOADING_MODEL
                _loadingModelName.value = model.displayName
            }
            val result = app.container.modelLoadCoordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.VOICE_SESSION)
            _loadingModelName.value = null
            if (!result.success) {
                _modelLoadError.value = result.errorMessage ?: "Could not load ${model.displayName}"
                _state.value = VoiceControllerState.IDLE
                audioCapture.stop()
                vad.release()
                if (::playbackQueue.isInitialized) playbackQueue.release()
                return
            }
        }

        while (true) {
            _state.value = VoiceControllerState.LISTENING
            val userText: String

            // 3-tier STT policy:
            //  1. Active generation model transcribes its own audio-direct capture (e.g. Gemma 4
            //     E2B) when it supports audio input.
            //  2. If that comes back blank/failed (model "not working well" for STT), or the
            //     model doesn't support audio input at all, fall back to the downloaded on-device
            //     Whisper model — only if it's actually downloaded AND the user has it enabled.
            //  3. Otherwise fall back to Android's system SpeechRecognizer (live capture, no VAD
            //     buffer needed since it does its own endpointing).
            val inbuiltSttReady = app.container.settingsRepository.inbuiltSttEnabled.first() && whisperStt.isReady()
            if (modelSupportsAudio || inbuiltSttReady) {
                val captured = captureUntilSilence()
                if (captured.pcm.isEmpty()) continue
                val wavFile = writePcmToWav(captured.pcm)
                val turnId = UUID.randomUUID().toString()
                _turns.value = _turns.value + VoiceTurn(
                    fromUser = true, text = "Transcribing…",
                    waveform = buildWaveform(captured.pcm), durationMs = captured.durationMs,
                    audioSamples = captured.pcm, sampleRateHz = VoiceActivityDetector.SAMPLE_RATE_HZ,
                    transcribedOnDevice = false, id = turnId
                )
                _state.value = VoiceControllerState.TRANSCRIBING

                var transcript: String? = null
                if (modelSupportsAudio) {
                    _sttLabel.value = "Model audio input"
                    transcript = transcribeAudio(wavFile.absolutePath)
                }
                if (transcript.isNullOrBlank() && inbuiltSttReady) {
                    _sttLabel.value = "Whisper (offline)"
                    transcript = whisperStt.transcribe(captured.pcm, VoiceActivityDetector.SAMPLE_RATE_HZ)
                }
                if (transcript.isNullOrBlank()) {
                    _turns.update { turns -> turns.filterNot { it.id == turnId } }
                    continue
                }
                _turns.update { turns -> turns.map { if (it.id == turnId) it.copy(text = transcript) else it } }
                userText = transcript
            } else {
                _sttLabel.value = "Device (offline)"
                val sttStart = System.currentTimeMillis()
                val text = listenViaDeviceStt() ?: continue
                if (text.isBlank()) continue
                userText = text
                _turns.value = _turns.value + VoiceTurn(
                    fromUser = true, text = text,
                    waveform = completedDeviceWaveform,
                    durationMs = (System.currentTimeMillis() - sttStart).toInt(),
                    transcribedOnDevice = true
                )
            }

            _state.value = VoiceControllerState.THINKING
            respondAndSpeak(userText)
        }
    }

    /** First hop of the audio-capable-model path: asks the model to transcribe the just-captured
     * utterance verbatim (a plain, unstreamed call — the output is a transcript to display, not
     * a spoken reply to chunk/speak) so the second hop ([respondAndSpeak]) always generates from
     * known-language text. This also keeps TTS language routing correct downstream: replying
     * from an audio blob directly gives no language signal, whereas replying to transcript text
     * lets [PiperTtsEngine]'s per-sentence script detection work as intended. Returns null on
     * any failure (model error, empty output) so the caller can just re-listen. */
    private suspend fun transcribeAudio(audioPath: String): String? = runCatching {
        val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
            ?: return@runCatching null
        val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
        if (!loaded.success || !app.container.audioEnabled(model)) return@runCatching null
        val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
        val builder = StringBuilder()
        app.container.generate(
            model, TRANSCRIBE_PROMPT, null, audioPath,
            params.temperature, params.topP, params.topK, params.seed,
            params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
        ).collect { token -> builder.append(token) }
        builder.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun respondAndSpeak(userText: String) {
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
        // The model streams markdown (for the transcript/UI to render), but TTS should never
        // read "asterisk asterisk" aloud — strip markdown syntax per-sentence, after the chunker
        // has already assembled a complete sentence, so the whole-syntax regexes in
        // markdownToSpeechText (e.g. matching a full "**bold**" pair) never see a token stream
        // split mid-marker.
        val chunker = SentenceChunker { sentence -> playbackQueue.enqueue(markdownToSpeechText(sentence)) }

        val scope = controllerScope
        val bargeInWatcher = maybeStartBargeInWatcher()
        val job = scope?.launch(Dispatchers.Default) {
            val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
                ?: throw IllegalStateException("The active generation model was removed during the voice session")
            val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
            check(loaded.success) { loaded.errorMessage ?: "Could not load the voice model" }
            val genParams = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
            val replyFlow = app.container.generate(
                model, userText, null, null,
                genParams.temperature, genParams.topP, genParams.topK, genParams.seed,
                genParams.minP, genParams.repetitionPenalty, genParams.maxOutputTokens, genParams.stopSequences
            )
            replyFlow.collect { token ->
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
        private const val TRANSCRIBE_PROMPT =
            "Transcribe exactly what was said in this audio. Output only the raw transcript, nothing else — no commentary, no translation."
    }
}
