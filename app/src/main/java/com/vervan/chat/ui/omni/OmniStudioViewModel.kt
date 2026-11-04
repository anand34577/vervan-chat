package com.vervan.chat.ui.omni

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.omni.OmniRuntime
import com.vervan.chat.omni.OmniTtsEngine
import com.vervan.chat.omni.runtime.AudioIO
import com.vervan.chat.omni.runtime.ChatPrompt
import com.vervan.chat.omni.runtime.Clip
import com.vervan.chat.omni.runtime.Conversation
import com.vervan.chat.omni.runtime.Lfm3OmniEngine
import com.vervan.chat.omni.runtime.Photos
import com.vervan.chat.omni.runtime.Videos
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives Omni Studio: every LFM3 Omni capability the regular chat cannot reach through LiteRT-LM —
 * spoken replies (text and speech interleaved), multi-turn voice chat, questions about a photo or a
 * video (its pictures and its sound), text-to-speech in a male or female voice, and speech recognition.
 * All model work goes through [OmniRuntime.use], one call at a time on the runtime's own thread.
 */
class OmniStudioViewModel(private val app: VervanApp) : ViewModel() {

    enum class Mode(val label: String) { CHAT("Voice chat"), PHOTO("Photo"), VIDEO("Video"), SPEAK("Speak"), TRANSCRIBE("Transcribe") }

    data class Turn(
        val id: String = UUID.randomUUID().toString(),
        val fromUser: Boolean,
        val text: String,
        val streaming: Boolean = false,
        /** The spoken answer (24 kHz) for replay, or a recorded question (16 kHz). */
        val pcm: FloatArray? = null,
        val sampleRate: Int = Lfm3OmniEngine.SAMPLE_RATE,
    )

    sealed interface Phase {
        data object Idle : Phase
        data class Working(val message: String) : Phase
        data class Recording(val seconds: Double) : Phase
        data object Answering : Phase
    }

    val runtimeState: StateFlow<OmniRuntime.State> = OmniRuntime.state

    private val _status = MutableStateFlow<OmniRuntime.Status?>(null)
    val status: StateFlow<OmniRuntime.Status?> = _status.asStateFlow()

    private val _mode = MutableStateFlow(Mode.CHAT)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** "voice" = spoken + written answers, "text" = written only. */
    private val _answerForm = MutableStateFlow("voice")
    val answerForm: StateFlow<String> = _answerForm.asStateFlow()

    /** What the current conversation is about: the photo / video name, or null. */
    private val _mediaLabel = MutableStateFlow<String?>(null)
    val mediaLabel: StateFlow<String?> = _mediaLabel.asStateFlow()
    private val _photoPreview = MutableStateFlow<android.graphics.Bitmap?>(null)
    val photoPreview: StateFlow<android.graphics.Bitmap?> = _photoPreview.asStateFlow()

    private val _ttsOutput = MutableStateFlow<FloatArray?>(null)
    val ttsOutput: StateFlow<FloatArray?> = _ttsOutput.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    val ttsVoice: StateFlow<String> = app.container.settingsRepository.omniTtsVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "female")
    val ttsLanguage: StateFlow<String> = app.container.settingsRepository.omniTtsLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "American English")

    private var conversation: Conversation? = null
    private var conversationEngine: Lfm3OmniEngine? = null
    private var recorder: AudioIO.Recorder? = null
    private var player: AudioIO.Player? = null
    private var job: Job? = null
    @Volatile private var stopRequested = false

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch { _status.value = runCatching { OmniRuntime.status(app) }.getOrNull() }
    }

    fun dismissError() { _error.value = null }

    fun setMode(m: Mode) {
        if (_mode.value == m) return
        stop()
        _mode.value = m
        resetConversationState()
    }

    fun setAnswerForm(form: String) {
        if (_answerForm.value == form) return
        _answerForm.value = form
        // The system turn differs between the two forms, so a new conversation starts.
        if (_mode.value == Mode.CHAT || _mode.value == Mode.VIDEO) resetConversationState()
    }

    fun setTtsVoice(v: String) = viewModelScope.launch { app.container.settingsRepository.setOmniTtsVoice(v) }
    fun setTtsLanguage(v: String) = viewModelScope.launch { app.container.settingsRepository.setOmniTtsLanguage(v) }

    private fun resetConversationState() {
        conversation = null
        _turns.value = emptyList()
        _mediaLabel.value = null
        _photoPreview.value = null
    }

    fun load() = launchWork("Loading LFM3 Omni") {
        OmniRuntime.open(app, exclusive = true)
    }

    fun unload() {
        stop()
        viewModelScope.launch {
            OmniRuntime.close()
            conversation = null
            refreshStatus()
        }
    }

    fun freeSpace() {
        stop()
        viewModelScope.launch {
            OmniRuntime.deletePrepared(app)
            conversation = null
            refreshStatus()
        }
    }

    /** Starts the conversation the current mode needs, on the runtime thread. */
    private fun conversationFor(engine: Lfm3OmniEngine): Conversation {
        val existing = conversation
        if (existing != null && conversationEngine === engine) return existing
        return Conversation(engine).also {
            conversation = it
            conversationEngine = engine
        }
    }

    fun newConversation() {
        stop()
        _turns.value = emptyList()
        val hadMedia = _mediaLabel.value != null
        launchWork("Starting over") {
            OmniRuntime.use(app, exclusive = true) { e ->
                val c = conversationFor(e)
                if (hadMedia || c.plainSystemPrompt != null) c.restart()
            }
        }
    }

    fun pickPhoto(uri: Uri, name: String) = launchWork("Reading the photo") {
        val photo = withContext(Dispatchers.IO) { Photos.fromUri(app, uri, name) }
        OmniRuntime.use(app, exclusive = true) { e -> conversationFor(e).setPhoto(photo) }
        _photoPreview.value = photo.square
        _mediaLabel.value = name
        _turns.value = emptyList()
    }

    fun pickVideo(uri: Uri, name: String) = launchWork("Reading the video") {
        val video = withContext(Dispatchers.IO) {
            Videos.read(app, uri, name) { step -> _phase.value = Phase.Working(step) }
        }
        OmniRuntime.use(app, exclusive = true) { e ->
            conversationFor(e).setVideo(video, _answerForm.value) { step -> _phase.value = Phase.Working(step) }
        }
        _photoPreview.value = null
        _mediaLabel.value = if (video.cut) "$name (first %.0f s)".format(video.readSeconds) else name
        _turns.value = emptyList()
    }

    fun startRecording() {
        if (recorder != null || job?.isActive == true) return
        _error.value = null
        val rec = AudioIO.Recorder { seconds, reachedLimit ->
            _phase.value = Phase.Recording(seconds)
            if (reachedLimit) viewModelScope.launch { stopRecording() }
        }
        try {
            rec.start()
            recorder = rec
            _phase.value = Phase.Recording(0.0)
        } catch (t: Throwable) {
            _error.value = t.message ?: "The microphone could not be opened."
        }
    }

    fun cancelRecording() {
        val rec = recorder ?: return
        recorder = null
        runCatching { rec.stop() }
        _phase.value = Phase.Idle
    }

    /** Stops the recording and sends it: a spoken question, or (Transcribe) audio to write down. */
    fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        val pcm = rec.stop()
        _phase.value = Phase.Idle
        when (_mode.value) {
            Mode.TRANSCRIBE -> transcribePcm(pcm, "Recording")
            Mode.SPEAK -> Unit
            else -> {
                val clip = try {
                    AudioIO.check("your question", pcm)
                } catch (t: Throwable) {
                    _error.value = t.message
                    return
                }
                ask(clip, "")
            }
        }
    }

    fun askTyped(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) ask(null, t)
    }

    private fun ask(clip: Clip?, typed: String) {
        val mode = _mode.value
        if (mode == Mode.PHOTO && _mediaLabel.value == null) {
            _error.value = "Choose a photo first."
            return
        }
        if (mode == Mode.VIDEO && _mediaLabel.value == null) {
            _error.value = "Choose a video first."
            return
        }
        val voice = _answerForm.value == "voice" || mode == Mode.PHOTO
        val userTurn = Turn(fromUser = true, text = if (clip != null) "🎤 %.1f s".format(clip.seconds) else typed,
            pcm = clip?.pcm16k, sampleRate = 16_000)
        val answerTurn = Turn(fromUser = false, text = "", streaming = true)
        _turns.update { it + userTurn + answerTurn }
        val p = if (voice) AudioIO.Player(Lfm3OmniEngine.SAMPLE_RATE) else null
        player = p
        stopRequested = false
        job = viewModelScope.launch {
            _phase.value = Phase.Answering
            try {
                val out = OmniRuntime.use(app, exclusive = true) { e ->
                    val c = conversationFor(e)
                    if (mode == Mode.CHAT && c.plainSystemPrompt == null) {
                        c.startPlain(if (voice) ChatPrompt.VOICE_CHAT_SYSTEM else TEXT_CHAT_SYSTEM, if (voice) "voice" else "text")
                    }
                    c.ask(
                        Conversation.Question(clip, typed, if (clip != null) "recording" else "typed"),
                        instruction = "",
                        listener = object : Conversation.Listener {
                            override fun onAudio(pcm: FloatArray) { p?.play(pcm) }
                            override fun onText(text: String) { updateTurn(answerTurn.id) { it.copy(text = text) } }
                        },
                        stop = { stopRequested },
                    )
                }
                updateTurn(answerTurn.id) {
                    it.copy(text = out.text.ifBlank { it.text }, streaming = false, pcm = out.pcm.takeIf { pcm -> pcm.isNotEmpty() })
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _turns.update { list -> list.filterNot { it.id == answerTurn.id && it.text.isBlank() } }
                updateTurn(answerTurn.id) { it.copy(streaming = false) }
                _error.value = t.message ?: "LFM3 Omni could not answer."
                if (t is Conversation.Full) conversation = null
            } finally {
                p?.close()
                _phase.value = Phase.Idle
                refreshStatus()
            }
        }
    }

    private fun updateTurn(id: String, change: (Turn) -> Turn) {
        _turns.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    /** Plays a turn's audio again. */
    fun replay(turn: Turn) {
        val pcm = turn.pcm ?: return
        player?.stopNow()
        player = AudioIO.Player(turn.sampleRate).also {
            it.play(pcm)
            it.close()
        }
    }

    fun speak(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val p = AudioIO.Player(Lfm3OmniEngine.SAMPLE_RATE)
        player = p
        stopRequested = false
        _ttsOutput.value = null
        job = viewModelScope.launch {
            _phase.value = Phase.Answering
            try {
                val gender = ttsVoice.value
                val language = ttsLanguage.value
                val pcm = OmniRuntime.use(app, exclusive = true) { e ->
                    val ids = e.tokenizer.encode(ChatPrompt.tts(t, language, gender))
                    Conversation(e).answerIds(
                        ids, maxNewTokens = OmniTtsEngine.maxFramesFor(t),
                        listener = object : Conversation.Listener {
                            override fun onAudio(pcm: FloatArray) { p.play(pcm) }
                        },
                        stop = { stopRequested },
                    ).pcm
                }
                _ttsOutput.value = OmniTtsEngine.trimLeadingSilence(pcm)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _error.value = t.message ?: "LFM3 Omni could not speak that."
            } finally {
                p.close()
                _phase.value = Phase.Idle
                refreshStatus()
            }
        }
    }

    fun replaySpeech() {
        val pcm = _ttsOutput.value ?: return
        player?.stopNow()
        player = AudioIO.Player(Lfm3OmniEngine.SAMPLE_RATE).also { it.play(pcm); it.close() }
    }

    /** Writes the last spoken text as a 24 kHz WAV to [uri] (from a Save dialog). */
    fun saveSpeech(uri: Uri) {
        val pcm = _ttsOutput.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(app.cacheDir, "omni-speech-${System.currentTimeMillis()}.wav")
                AudioIO.writeWav(tmp, pcm, Lfm3OmniEngine.SAMPLE_RATE)
                app.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                tmp.delete()
            }.onFailure { _error.value = "Could not save the audio: ${it.message}" }
        }
    }

    /** Transcribes an audio or video file (any length; read in 30 s windows). */
    fun transcribeFile(uri: Uri, name: String) = launchWork("Reading $name") {
        val pcm = withContext(Dispatchers.IO) {
            val tmp = File(app.cacheDir, "omni-asr-${System.currentTimeMillis()}")
            try {
                app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: error("Could not read $name.")
                val decoded = com.vervan.chat.voice.AudioDecoder.decodeToPcm16k(tmp).first
                FloatArray(decoded.size) { decoded[it] / 32768f }
            } finally {
                tmp.delete()
            }
        }
        transcribePcmNow(pcm)
    }

    private fun transcribePcm(pcm: FloatArray, label: String) = launchWork("Transcribing $label") { transcribePcmNow(pcm) }

    private suspend fun transcribePcmNow(pcm: FloatArray) {
        val window = Clip.MAX_SECONDS * 16_000
        if (pcm.size < AudioIO.MIN_SECONDS * 16_000) throw IllegalStateException("The recording is too short.")
        val total = (pcm.size + window - 1) / window
        val sb = StringBuilder()
        stopRequested = false
        for (i in 0 until total) {
            if (stopRequested) break
            _phase.value = Phase.Working(if (total > 1) "Transcribing part ${i + 1} of $total" else "Transcribing")
            val part = pcm.copyOfRange(i * window, minOf(pcm.size, (i + 1) * window))
            if (part.size < AudioIO.MIN_SECONDS * 16_000) continue
            val text = OmniRuntime.use(app, exclusive = true) { e ->
                // One question per conversation: each window is written down on its own.
                val c = Conversation(e)
                c.startPlain(ChatPrompt.ASR_SYSTEM, "text")
                c.ask(Conversation.Question(Clip("audio", part), "", "recording"), instruction = "",
                    stop = { stopRequested }).text
            }
            if (text.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(text.trim())
                _transcript.value = sb.toString()
            }
        }
        _transcript.value = sb.toString()
        if (sb.isBlank()) throw IllegalStateException("No speech was recognized.")
    }

    fun stop() {
        stopRequested = true
        player?.stopNow()
        player = null
        cancelRecording()
    }

    private fun launchWork(message: String, block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _error.value = null
        job = viewModelScope.launch {
            _phase.value = Phase.Working(message)
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _error.value = t.message ?: "Something went wrong."
            } finally {
                _phase.value = Phase.Idle
                refreshStatus()
            }
        }
    }

    override fun onCleared() {
        stop()
        // Leaving Omni Studio gives the memory back to the chat model.
        app.applicationScope.launch { OmniRuntime.close() }
    }

    companion object {
        const val TEXT_CHAT_SYSTEM = "You are a helpful assistant."
    }
}
