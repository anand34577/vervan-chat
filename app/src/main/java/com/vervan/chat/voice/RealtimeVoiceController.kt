package com.vervan.chat.voice

import android.util.Log
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.ContinuousAudioCapture
import com.vervan.chat.audio.VoiceActivityDetector
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.modelload.LoadTrigger
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Connects the realtime audio session to the ordinary chat pipeline.
 *
 * The bridge deliberately sits above every engine: capture, VAD, STT and TTS remain owned by
 * [RealtimeVoiceController], while the normal chat ViewModel owns message persistence, context
 * assembly, retrieval, attachments, tools, branching and LLM selection. This is what lets voice
 * remain a modality of an existing conversation instead of becoming a second chat system.
 */
interface VoiceConversationBridge {
    suspend fun respond(input: VoiceInputTurn, onAssistantUpdate: (String) -> Unit): String
    fun cancelResponse()
}

data class VoiceInputTurn(
    val text: String,
    val recordingPath: String?,
    val sttLabel: String,
    val durationMs: Int
)

/**
 * The realtime voice pipeline's glue: continuous mic capture -> VAD endpointing -> STT
 * (existing Gemma audio-direct path when the model supports it, else the downloaded whisper.cpp
 * model) -> [com.vervan.chat.llm.LlmEngine] streaming generation ->
 * [SentenceChunker] -> [TtsEngineSelector] -> [TtsPlaybackQueue], with barge-in: while TTS is
 * playing, the same VAD keeps classifying the live (echo-cancelled) capture stream, and
 * sustained speech interrupts playback and starts a new listening cycle.
 *
 * One controller instance = one voice session, started by [start] and torn down by [stop].
 * Not persisted to the Chat database — the turn list is an ephemeral, in-memory transcript.
 */
class RealtimeVoiceController(
    private val app: VervanApp,
    private val conversationBridge: VoiceConversationBridge? = null,
    private val generationModelId: String? = null,
) {
    private val audioCapture = ContinuousAudioCapture()
    private val vad = VoiceActivityDetector(app)
    private val whisperCppStt: SttEngine = WhisperCppSttEngine(app, app.container.db.ttsVoiceModelDao(), app.container.settingsRepository)
    private val engineSelector = TtsEngineSelector(
        app.container.settingsRepository,
        PiperTtsEngine(app.container.db.ttsVoiceModelDao()),
        KokoroTtsEngine(app.container.db.ttsVoiceModelDao()),
        SupertonicTtsEngine(app.container.db.ttsVoiceModelDao(), app.container.settingsRepository)
    )
    private lateinit var playbackQueue: TtsPlaybackQueue
    private var controllerScope: CoroutineScope? = null
    private var sessionJob: Job? = null
    private var generationJob: Job? = null
    // Tracked as a field (not a local in respondAndSpeak) so stop() can cancel-and-join it too —
    // it used to be a plain local that stop() never touched at all, so calling it while the
    // watcher was still mid-frame raced vad.release() below into a native use-after-free crash.
    private var bargeInWatcher: Job? = null
    private var persistenceJob: Job? = null
    private val historyRun = ToolRun(toolRoute = "tools/voice-chat", toolName = "Voice chat", input = "")
    @Volatile private var finishListeningRequested = false
    @Volatile private var cancelListeningRequested = false
    @Volatile private var responseInterrupted = false

    private val _state = MutableStateFlow(VoiceControllerState.IDLE)
    val state: StateFlow<VoiceControllerState> = _state

    private val _turns = MutableStateFlow<List<VoiceTurn>>(emptyList())
    val turns: StateFlow<List<VoiceTurn>> = _turns

    /** Which STT/TTS path is active, for the "STT: ..."/"TTS: ..." UI badges. */
    private val _sttLabel = MutableStateFlow("whisper.cpp")
    val sttLabel: StateFlow<String> = _sttLabel
    private val _ttsLabel = MutableStateFlow("Piper")
    val ttsLabel: StateFlow<String> = _ttsLabel

    private val _hasEchoCancellation = MutableStateFlow(true)
    val hasEchoCancellation: StateFlow<Boolean> = _hasEchoCancellation

    /** Rolling amplitude bars + elapsed time for the live "Listening…" bubble — updated per
     * frame while [captureUntilSilence] runs, cleared once it returns. */
    private val _liveWaveform = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform
    private val _liveElapsedMs = MutableStateFlow(0)
    val liveElapsedMs: StateFlow<Int> = _liveElapsedMs

    /** Partial words produced by the active STT path, exposed while Gemma decodes the captured
     * utterance. */
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript

    /** Name of the model being loaded, while [state] is [VoiceControllerState.LOADING_MODEL]. */
    private val _loadingModelName = MutableStateFlow<String?>(null)
    val loadingModelName: StateFlow<String?> = _loadingModelName

    /** Set if the preload in [runSession] fails — cleared on the next [start]. Surfaced by the
     * UI as a dismissible/retryable error instead of silently leaving the mic dead. */
    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError

    /** True when no STT tier is available at all — the active model can't hear audio, and the
     * inbuilt whisper.cpp model isn't downloaded/enabled (there is no device-recognizer fallback
     * tier). Surfaced by the UI so a silent, do-nothing mic becomes an actionable "download the
     * offline voice model" prompt instead. Cleared as soon as any STT path works. */
    private val _sttUnavailable = MutableStateFlow(false)
    val sttUnavailable: StateFlow<Boolean> = _sttUnavailable

    private val _playbackPaused = MutableStateFlow(false)
    val playbackPaused: StateFlow<Boolean> = _playbackPaused
    private val _microphoneMuted = MutableStateFlow(false)
    val microphoneMuted: StateFlow<Boolean> = _microphoneMuted
    private val _speechOutputEnabled = MutableStateFlow(true)
    val speechOutputEnabled: StateFlow<Boolean> = _speechOutputEnabled

    /** True while the UI's push-to-talk button is physically held down. Only consulted by
     * [captureUntilSilence] when [SettingsRepository.voicePushToTalkEnabled] is on for the
     * current utterance — see [pushToTalkPress]/[pushToTalkRelease]. */
    private val _pushToTalkHeld = MutableStateFlow(false)
    val pushToTalkHeld: StateFlow<Boolean> = _pushToTalkHeld

    /** Call from the UI's press gesture on the mic button. A no-op outside push-to-talk mode or
     * outside [VoiceControllerState.LISTENING] — harmless if a stray press lands during another
     * state (e.g. the tail end of a gesture after the turn already finished). */
    fun pushToTalkPress() {
        if (_state.value == VoiceControllerState.LISTENING) _pushToTalkHeld.value = true
    }

    /** Call from the UI's release gesture. Ends the held utterance immediately — release IS the
     * endpoint signal in push-to-talk mode, same role [finishListening] plays for the tap-based
     * flow. Safe to call even if nothing was actually captured yet (a tap-and-immediately-release
     * before any audio came in): [captureUntilSilence] already handles an empty capture as a
     * silent no-op that just re-enters LISTENING. */
    fun pushToTalkRelease() {
        _pushToTalkHeld.value = false
    }

    fun start(scope: CoroutineScope) {
        if (sessionJob?.isActive == true) return
        _modelLoadError.value = null
        _sttUnavailable.value = false
        controllerScope = scope
        playbackQueue = TtsPlaybackQueue(app, engineSelector, scope)
        if (persistenceJob?.isActive != true) {
            persistenceJob = scope.launch(Dispatchers.IO) {
                combine(_turns, _state) { turns, state -> turns to state }.collectLatest { (turns, state) ->
                    if (turns.isEmpty()) return@collectLatest
                    // Coalesce streaming token updates; the quiet point after each turn writes the
                    // complete session snapshot without hammering Room for every token.
                    delay(350)
                    persistHistory(turns, if (state == VoiceControllerState.IDLE) ToolRunState.COMPLETED else ToolRunState.RUNNING)
                }
            }
        }
        sessionJob = scope.launch(Dispatchers.Default) { runSession() }
    }

    fun stop() {
        // A controller that never had start() called (e.g. one that's remembered anew and torn
        // down before hands-free was ever toggled on — see ChatScreen's
        // DisposableEffect(voiceController)/onDispose) has nothing of its own to tear down. Doing
        // the full teardown anyway used to call conversationBridge?.cancelResponse() unconditionally,
        // which cancels whatever the chat is currently generating (voice or plain text) even though
        // this controller instance was never the one generating it — e.g. it fired mid-send right
        // after Home's "Ask anything" quick-ask, cancelling the reply before it started.
        if (sessionJob == null && _state.value == VoiceControllerState.IDLE) return
        val beforeStop = _turns.value
        val interrupted = beforeStop.any { it.isStreaming } || _state.value in setOf(
            VoiceControllerState.THINKING,
            VoiceControllerState.TRANSCRIBING,
            VoiceControllerState.LOADING_MODEL,
        )
        // cancel() only *requests* cancellation and returns immediately — it does not wait for a
        // coroutine currently blocked inside a native call (e.g. bargeInWatcher mid-frame inside
        // Vad.acceptWaveform) to actually stop. Releasing vad/the STT engines right after a bare
        // cancel() used to race that in-flight native call into a use-after-free crash. Every job
        // that might still be touching native state is joined below, on a background coroutine,
        // before any release() runs.
        val jobsToJoin = listOfNotNull(sessionJob, generationJob, bargeInWatcher)
        sessionJob = null
        generationJob = null
        bargeInWatcher = null
        controllerScope = null
        _state.value = VoiceControllerState.IDLE
        _liveWaveform.value = emptyList()
        _liveElapsedMs.value = 0
        _liveTranscript.value = ""
        _loadingModelName.value = null
        _playbackPaused.value = false
        _microphoneMuted.value = false
        _sttUnavailable.value = false
        _pushToTalkHeld.value = false
        finishListeningRequested = false
        cancelListeningRequested = false
        responseInterrupted = true
        AndroidSystemSttRecognizer.cancelActiveRecognition()
        conversationBridge?.cancelResponse()
        _turns.update { turns -> turns.map { it.copy(isStreaming = false, audioPending = false) } }
        CoroutineScope(Dispatchers.Default).launch {
            jobsToJoin.forEach { runCatching { it.cancelAndJoin() } }
            if (::playbackQueue.isInitialized) playbackQueue.release()
            audioCapture.stop()
            vad.release()
            whisperCppStt.release()
            engineSelector.releaseAll()
            if (beforeStop.isNotEmpty()) {
                persistHistory(_turns.value, if (interrupted) ToolRunState.INTERRUPTED else ToolRunState.COMPLETED)
            }
        }
    }

    private suspend fun persistHistory(turns: List<VoiceTurn>, state: ToolRunState) {
        val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
        val userInput = turns.filter { it.fromUser }.joinToString("\n") { it.text }
        val transcript = turns.joinToString("\n\n") { turn ->
            (if (turn.fromUser) "You" else "Vervan") + ": " + turn.text
        }
        app.container.db.toolRunDao().upsert(
            historyRun.copy(
                input = userInput,
                output = transcript,
                state = state,
                modelId = model?.id,
                modelName = model?.displayName,
                backend = model?.lastWorkingBackend?.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Finishes only the current user turn; [stop] ends the whole voice session. */
    fun finishListening() {
        if (_state.value != VoiceControllerState.LISTENING) return
        finishListeningRequested = true
        AndroidSystemSttRecognizer.finishActiveRecognition()
    }

    fun togglePlaybackPause() {
        if (_state.value != VoiceControllerState.SPEAKING || !::playbackQueue.isInitialized) return
        _playbackPaused.value = playbackQueue.togglePause()
    }

    /** Discards only the utterance currently being captured and returns to listening. */
    fun cancelCurrentUtterance() {
        if (_state.value != VoiceControllerState.LISTENING) return
        cancelListeningRequested = true
        finishListeningRequested = true
        AndroidSystemSttRecognizer.cancelActiveRecognition()
    }

    fun toggleMicrophoneMute() {
        _microphoneMuted.value = !_microphoneMuted.value
    }

    fun toggleSpeechOutput() {
        val enabled = !_speechOutputEnabled.value
        _speechOutputEnabled.value = enabled
        if (!enabled && ::playbackQueue.isInitialized) {
            playbackQueue.stop()
            _playbackPaused.value = false
        }
    }

    fun setSpeechOutputEnabled(enabled: Boolean) {
        if (_speechOutputEnabled.value == enabled) return
        _speechOutputEnabled.value = enabled
        if (!enabled && ::playbackQueue.isInitialized) {
            playbackQueue.stop()
            _playbackPaused.value = false
        }
    }

    /** Manual escape hatch for devices without hardware echo cancellation, or when the user
     * just wants to cut a reply short without relying on barge-in detection. */
    fun manualInterrupt() {
        if (_state.value !in setOf(VoiceControllerState.THINKING, VoiceControllerState.SPEAKING)) return
        responseInterrupted = true
        conversationBridge?.cancelResponse()
        generationJob?.cancel()
        if (::playbackQueue.isInitialized) playbackQueue.stop()
        _playbackPaused.value = false
        _state.value = VoiceControllerState.LISTENING
    }

    /** Resolves the offline STT engine (whisper.cpp — the only one; Android's system speech
     *  recognizer is deliberately never used) for the current turn, based on the
     *  `inbuiltSttEnabled` master toggle: null when it's off, or when whisper.cpp's model isn't
     *  actually downloaded/ready. Calling isReady() here is what lazily loads the model; idempotent
     *  under the engine's own mutex, so the background warm-up in [runSession] racing this call is
     *  safe.
     *
     *  Never throws. A native model load is the one step here that can fail in ways the engine
     *  can't fully anticipate (a missing/mismatched .so on this ABI, a corrupt model file), and
     *  this runs on the session's main loop — an escaping exception would tear down the whole
     *  voice session rather than degrading gracefully. A failed engine is treated as "not
     *  available for this turn". [CancellationException] is deliberately re-thrown — [stop]
     *  cancels the session through it, and swallowing it here would leave the loop running after
     *  teardown. */
    private suspend fun pickInbuiltStt(): SttEngine? {
        return try {
            whisperCppStt.takeIf { it.isReady() }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "Inbuilt STT unavailable this turn", t)
            null
        }
    }

    private suspend fun runSession() {
        vad.load()
        audioCapture.start(CAPTURE_FRAME_MS)
        val model = generationModelId?.let { app.container.db.modelDao().get(it) }
            ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)

        // Preload the generation model up front, with a visible spinner, instead of loading it
        // lazily on the first reply (where "Thinking…" would silently include a multi-second
        // native model load with no indication that's what's actually happening).
        if (model != null) {
            val alreadyLoaded = app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId == model.id
            if (!alreadyLoaded) {
                _state.value = VoiceControllerState.LOADING_MODEL
                _loadingModelName.value = model.displayName
            }
            val result = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
            _loadingModelName.value = null
            if (!result.success) {
                _modelLoadError.value = result.errorMessage ?: "Could not load ${model.displayName}"
                _state.value = VoiceControllerState.IDLE
                audioCapture.stop()
                vad.release()
                if (::playbackQueue.isInitialized) playbackQueue.release()
                return
            }
            // Re-read after the load: for a never-before-loaded model, supportsAudio was still
            // null above — the load itself just proved (and persisted, via reconcileCapabilities)
            // whether audio input actually works, so trust that over the stale pre-load row.
        }

        // Warm the inbuilt STT model up in the background while the session settles, so the first
        // turn that needs it doesn't pay the multi-second native model load synchronously inside
        // the LISTENING state (where the user is already talking with no feedback). isReady() is
        // idempotent and mutex-guarded, so this racing with the first real pickInbuiltStt() below
        // is safe — whichever runs first does the load, the other is a no-op. pickInbuiltStt()
        // resolves the user's chosen engine AND its isReady() in one call, so the warm-up loads
        // exactly the model the loop will end up using.
        if (app.container.settingsRepository.inbuiltSttEnabled.first()) {
            controllerScope?.launch(Dispatchers.Default) { runCatching { pickInbuiltStt() } }
        }

        while (true) {
            _liveTranscript.value = ""
            // Reset before publishing LISTENING. Resetting later inside a recognizer created a
            // race where a fast tap on X/Stop was accepted by the UI and then silently erased.
            finishListeningRequested = false
            cancelListeningRequested = false
            _state.value = VoiceControllerState.LISTENING
            val currentModel = model?.id?.let { app.container.db.modelDao().get(it) }
            val modelCanHear = currentModel?.let {
                it.supportsAudio ?: app.container.audioEnabled(it)
            } == true
            val resolution = SttEnginePolicy.resolve(app, modelCanHear)
            if (!resolution.isAvailable) {
                _sttLabel.value = resolution.unavailableReason ?: "No STT available"
                _sttUnavailable.value = true
                delay(NO_STT_RETRY_DELAY_MS)
                continue
            }
            _sttUnavailable.value = false
            val firstEngine = resolution.candidates.first()
            val userInput: VoiceInputTurn

            // 2-tier STT policy (no device-recognizer fallback — Android's system STT is
            // deliberately never used):
            //  1. Active generation model transcribes its own audio-direct capture (e.g. Gemma 4
            //     E2B) when it supports audio input.
            //  2. If that comes back blank/failed (model "not working well" for STT), or the
            //     model doesn't support audio input at all, fall back to the downloaded on-device
            //     whisper.cpp model — only if it's actually downloaded AND the user has it enabled.
            if (firstEngine == SttEngineChoice.ANDROID) {
                _sttLabel.value = SttEngineChoice.ANDROID.label
                audioCapture.stop()
                if (finishListeningRequested || cancelListeningRequested) {
                    cancelListeningRequested = false
                    finishListeningRequested = false
                    audioCapture.start(CAPTURE_FRAME_MS)
                    continue
                }
                val maxSeconds = app.container.settingsRepository.maxUtteranceSeconds.first()
                val language = app.container.settingsRepository.voiceInputLanguage.first()
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val result = AndroidSystemSttRecognizer.recognizeOnce(app, language, maxSeconds)
                audioCapture.start(CAPTURE_FRAME_MS)
                if (cancelListeningRequested) {
                    cancelListeningRequested = false
                    finishListeningRequested = false
                    continue
                }
                val transcript = result.getOrNull()?.trim()
                if (transcript.isNullOrBlank()) {
                    _modelLoadError.value = result.exceptionOrNull()?.message ?: "No speech was recognized"
                    continue
                }
                val durationMs = (android.os.SystemClock.elapsedRealtime() - startedAt).toInt()
                _turns.value = _turns.value + VoiceTurn(
                    fromUser = true,
                    text = transcript,
                    durationMs = durationMs,
                    transcribedOnDevice = true
                )
                userInput = VoiceInputTurn(
                    text = transcript,
                    recordingPath = null,
                    sttLabel = SttEngineChoice.ANDROID.label,
                    durationMs = durationMs
                )
            } else {
                val configuredMax = app.container.settingsRepository.maxUtteranceSeconds.first() * 1_000
                val maxDurationMs = if (firstEngine == SttEngineChoice.MODEL_AUDIO) {
                    configuredMax.coerceAtMost(MODEL_AUDIO_MAX_UTTERANCE_MS)
                } else {
                    configuredMax
                }
                val pushToTalk = app.container.settingsRepository.voicePushToTalkEnabled.first()
                val captured = captureUntilSilence(maxDurationMs, pushToTalk)
                if (cancelListeningRequested) {
                    cancelListeningRequested = false
                    continue
                }
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
                var usedEngine: SttEngineChoice? = null
                for (candidate in resolution.candidates) {
                    if (candidate == SttEngineChoice.ANDROID) continue
                    _sttLabel.value = candidate.label
                    _liveTranscript.value = ""
                    transcript = when (candidate) {
                        SttEngineChoice.MODEL_AUDIO -> transcribeAudio(wavFile.absolutePath)
                        SttEngineChoice.WHISPER_CPP -> pickInbuiltStt()
                            ?.transcribe(captured.pcm, VoiceActivityDetector.SAMPLE_RATE_HZ)
                        SttEngineChoice.ANDROID -> null
                    }
                    if (!transcript.isNullOrBlank()) {
                        usedEngine = candidate
                        break
                    }
                }
                if (transcript.isNullOrBlank()) {
                    wavFile.delete()
                    _liveTranscript.value = ""
                    _turns.update { turns -> turns.filterNot { it.id == turnId } }
                    _modelLoadError.value = "Speech could not be transcribed. Try again or choose another STT engine."
                    continue
                }
                val keepRecording = app.container.settingsRepository.storeVoiceRecordings.first()
                val recordingPath = wavFile.absolutePath.takeIf { keepRecording }
                if (!keepRecording) wavFile.delete()
                _turns.update { turns -> turns.map { if (it.id == turnId) it.copy(text = transcript) else it } }
                userInput = VoiceInputTurn(
                    text = transcript,
                    recordingPath = recordingPath,
                    sttLabel = usedEngine?.label ?: _sttLabel.value,
                    durationMs = captured.durationMs
                )
            }

            _liveTranscript.value = ""
            _sttUnavailable.value = false
            _modelLoadError.value = null
            _state.value = VoiceControllerState.THINKING
            respondAndSpeak(userInput)
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
        val model = generationModelId?.let { app.container.db.modelDao().get(it) }
            ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
            ?: return@runCatching null
        val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
        if (!loaded.success || !app.container.audioEnabled(model)) return@runCatching null
        val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
        val builder = StringBuilder()
        app.container.generate(
            model, TRANSCRIBE_PROMPT, null, audioPath,
            params.temperature, params.topP, params.topK, params.seed,
            params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
        ).collect { token ->
            builder.append(token)
            _liveTranscript.value = builder.toString().trimStart()
        }
        builder.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun respondAndSpeak(userInput: VoiceInputTurn) {
        responseInterrupted = false
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
        _ttsLabel.value = if (_speechOutputEnabled.value) {
            engineSelector.resolve()?.engineName ?: "No TTS available"
        } else {
            "Voice replies off"
        }
        var replyText = ""
        // The model streams markdown (for the transcript/UI to render), but TTS should never
        // read "asterisk asterisk" aloud — strip markdown syntax per-sentence, after the chunker
        // has already assembled a complete sentence, so the whole-syntax regexes in
        // markdownToSpeechText (e.g. matching a full "**bold**" pair) never see a token stream
        // split mid-marker.
        val chunker = SentenceChunker { sentence ->
            if (_speechOutputEnabled.value) playbackQueue.enqueue(markdownToSpeechText(sentence))
        }

        val scope = controllerScope
        bargeInWatcher = maybeStartBargeInWatcher()
        val job = scope?.launch(Dispatchers.Default) {
            fun acceptAssistantUpdate(updatedText: String) {
                val delta = if (updatedText.startsWith(replyText)) updatedText.removePrefix(replyText) else updatedText
                replyText = updatedText
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
                if (delta.isNotEmpty()) chunker.append(delta)
            }

            if (conversationBridge != null) {
                val finalText = conversationBridge.respond(userInput, ::acceptAssistantUpdate)
                if (finalText != replyText) acceptAssistantUpdate(finalText)
            } else {
                val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
                    ?: throw IllegalStateException("The active generation model was removed during the voice session")
                val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
                check(loaded.success) { loaded.errorMessage ?: "Could not load the voice model" }
                val genParams = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
                val replyFlow = app.container.generate(
                    model, userInput.text, null, null,
                    genParams.temperature, genParams.topP, genParams.topK, genParams.seed,
                    genParams.minP, genParams.repetitionPenalty, genParams.maxOutputTokens, genParams.stopSequences
                )
                replyFlow.collect { token -> acceptAssistantUpdate(replyText + token) }
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
        if (responseInterrupted) {
            playbackQueue.stop()
        } else {
            chunker.flush()
            playbackQueue.endTurn()
        }
        generationJob = null

        // Append the text immediately — readable before TTS finishes, per the spec's "read
        // ahead of the audio" requirement. The playback bar's audio/waveform is patched onto
        // this same turn (by id) once playback actually finishes, below.
        _turns.update { turns -> turns.map { turn ->
            if (turn.id == turnId) turn.copy(
                text = replyText.trim(),
                isStreaming = false,
                audioPending = !responseInterrupted && turn.audioPending
            ) else turn
        } }

        playbackQueue.awaitCompletion()
        // Joined, not just cancelled — the next loop iteration calls captureUntilSilence(), which
        // immediately does vad.reset()/vad.isSpeech() from a different coroutine. A bare cancel()
        // only requests cancellation; without waiting for it to actually land, that reset/capture
        // could start while this watcher was still mid-frame inside the same native Vad object,
        // which is exactly the concurrent-native-call crash this used to hit.
        bargeInWatcher?.cancelAndJoin()
        bargeInWatcher = null
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
            // Start this barge-in watch from clean VAD state: the detector accumulates internal
            // speech-segment state across acceptWaveform calls, so carrying the just-finished
            // user utterance's tail into barge-in detection would bias the very first frames here.
            vad.reset()
            var speechFrames = 0
            audioCapture.frames.takeWhile { frame ->
                _hasEchoCancellation.value = audioCapture.hasEchoCancellation
                if (!audioCapture.hasEchoCancellation) return@takeWhile false
                val speaking = vad.isSpeech(frame)
                speechFrames = if (speaking) speechFrames + 1 else 0
                val triggered = speechFrames * CAPTURE_FRAME_MS >= BARGE_IN_TRIGGER_MS
                if (triggered) {
                    responseInterrupted = true
                    conversationBridge?.cancelResponse()
                    generationJob?.cancel()
                    playbackQueue.stop()
                }
                !triggered
            }.collect { }
        }
    }

    private data class CapturedUtterance(val pcm: ShortArray, val durationMs: Int)

    /** Endpoints one utterance two different ways depending on [pushToTalk]:
     *  - off (default, hands-free): VAD decides both when speech starts and — via
     *    [TRAILING_SILENCE_MS] of quiet after it — when it ends. This is the original behavior,
     *    unchanged for anyone who never turns push-to-talk on.
     *  - on: the UI's hold gesture ([pushToTalkPress]/[pushToTalkRelease], reflected in
     *    [_pushToTalkHeld]) decides both edges instead of the VAD — recording only accumulates
     *    while held, and release ends the utterance immediately rather than waiting out a
     *    trailing-silence timer. The VAD is not consulted at all in this mode; a deliberately
     *    held-then-silent button press should still count as intentional speech, not get
     *    endpointed early just because the room is quiet.
     */
    private suspend fun captureUntilSilence(maxDurationMs: Int, pushToTalk: Boolean = false): CapturedUtterance {
        // Clear any VAD state left over from the previous turn (or the barge-in watcher) before
        // endpointing a fresh utterance. This also bounds the detector's internal speech-segment
        // buffer, which this pipeline polls via isSpeechDetected() but never drains, so it would
        // otherwise grow for the whole session.
        vad.reset()
        val collected = ArrayList<Short>()
        // The VAD needs a few frames of context before it confidently flags speech as started —
        // normal detector latency, not a bug in it — so frames strictly before that flip would
        // otherwise be dropped entirely, cutting off the attack of the first word (exactly what
        // was reported as "misses my first word"). Keep a short rolling pre-roll of frames seen
        // while not-yet-speaking and splice it in the instant speech actually triggers, so the
        // true onset survives even though the VAD only recognized it a few frames late. Push-to-
        // talk uses the same pre-roll for the same reason: the button press event and the audio
        // frame that actually contains the attack of the first word are not perfectly aligned.
        val preRoll = ArrayDeque<ShortArray>()
        val preRollMaxFrames = PRE_SPEECH_BUFFER_MS / CAPTURE_FRAME_MS
        var sawSpeech = false
        var silenceMs = 0
        var elapsedMs = 0
        _liveWaveform.value = emptyList()
        _liveElapsedMs.value = 0
        if (pushToTalk) _pushToTalkHeld.value = false
        audioCapture.frames.takeWhile { frame ->
            if (finishListeningRequested) return@takeWhile false
            if (_microphoneMuted.value) {
                _liveWaveform.value = emptyList()
                delay(CAPTURE_FRAME_MS.toLong())
                return@takeWhile true
            }
            val held = _pushToTalkHeld.value
            if (pushToTalk && !held && !sawSpeech) {
                // Waiting for the button to be pressed — don't accumulate audio or touch the VAD
                // yet, just keep the pre-roll warm so the moment press lands doesn't clip.
                preRoll.addLast(frame)
                if (preRoll.size > preRollMaxFrames) preRoll.removeFirst()
                elapsedMs += CAPTURE_FRAME_MS
                _liveWaveform.value = emptyList()
                _liveElapsedMs.value = elapsedMs
                return@takeWhile elapsedMs < maxDurationMs
            }
            val speaking = if (pushToTalk) held else vad.isSpeech(frame)
            if (speaking) {
                if (!sawSpeech) {
                    preRoll.forEach { collected.addAll(it.toList()) }
                    preRoll.clear()
                }
                sawSpeech = true
                silenceMs = 0
                collected.addAll(frame.toList())
            } else if (sawSpeech) {
                silenceMs += CAPTURE_FRAME_MS
                collected.addAll(frame.toList())
            } else {
                preRoll.addLast(frame)
                if (preRoll.size > preRollMaxFrames) preRoll.removeFirst()
            }
            elapsedMs += CAPTURE_FRAME_MS
            _liveWaveform.value = (_liveWaveform.value + frameLevel(frame)).takeLast(LIVE_WAVEFORM_BARS)
            _liveElapsedMs.value = elapsedMs
            val done = finishListeningRequested ||
                (!pushToTalk && sawSpeech && silenceMs >= TRAILING_SILENCE_MS) ||
                (pushToTalk && sawSpeech && !held) ||
                elapsedMs >= maxDurationMs
            !done
        }.collect { }
        finishListeningRequested = false
        _pushToTalkHeld.value = false
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

    companion object {
        private const val TAG = "RealtimeVoiceController"

        /** Frame duration for the one shared [ContinuousAudioCapture] stream — both STT
         * endpointing and barge-in detection read frames at this size. */
        private const val CAPTURE_FRAME_MS = 20
        // See captureUntilSilence's pre-roll buffer — long enough to cover the VAD's onset
        // detection lag on a fast consonant, short enough not to drag in the tail of whatever
        // background noise preceded speech.
        private const val PRE_SPEECH_BUFFER_MS = 300
        private const val TRAILING_SILENCE_MS = 600
        private const val MODEL_AUDIO_MAX_UTTERANCE_MS = 30_000
        private const val BARGE_IN_TRIGGER_MS = 300
        private const val NO_STT_RETRY_DELAY_MS = 400L
        private const val LIVE_WAVEFORM_BARS = 40
        private const val TRANSCRIBE_PROMPT =
            "Transcribe exactly what was said in this audio. Output only the raw transcript, nothing else — no commentary, no translation."
    }
}
