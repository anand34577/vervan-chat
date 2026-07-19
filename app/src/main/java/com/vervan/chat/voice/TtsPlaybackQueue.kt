package com.vervan.chat.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Plays synthesized sentences back-to-back via [AudioTrack] in streaming mode, overlapping
 * synthesis of the next sentence with playback of the current one (bounded to 2 concurrent
 * synth jobs so a slow engine can't let synthesis run away ahead of playback). Reuses the
 * exact [AudioFocusRequest]/[AudioAttributes] shape already established for TTS in
 * [com.vervan.chat.ui.chat.ChatScreen] rather than reinventing focus handling a fifth time.
 *
 * One "turn" = one AI reply: [startTurn] opens a fresh sentence queue, [enqueue] feeds
 * sentences as [SentenceChunker] produces them, [endTurn] signals no more are coming for this
 * reply. [stop] is the barge-in cutoff — cancels playback and any in-flight synthesis
 * immediately.
 */
class TtsPlaybackQueue(context: Context, private val engineSelector: TtsEngineSelector, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val synthSemaphore = Semaphore(2)

    private var audioTrack: AudioTrack? = null
    private var trackSampleRate: Int = -1
    private var sentenceChannel: Channel<String> = Channel(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    @Volatile private var paused = false
    // "auto" (not "hi"/"en") so PiperTtsEngine's per-sentence Devanagari-script heuristic runs
    // by default — nothing calls setLanguageHint today, so a hardcoded "en" here previously
    // forced every sentence through the English voice regardless of its actual script.
    private var currentLang = "auto"
    /** Set per-turn via [startTurn] — lets the caller mirror played PCM (for a turn's waveform
     * + replay) without this class needing to know anything about UI or persistence. */
    private var sampleSink: ((ShortArray, Int) -> Unit)? = null

    val isPlaying: Boolean get() = playbackJob?.isActive == true
    val isPaused: Boolean get() = paused

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    fun setLanguageHint(lang: String) { currentLang = lang }

    /** Opens a fresh sentence queue and starts the playback loop for one AI reply. Call once
     * per turn, before the first [enqueue]. */
    fun startTurn(onSample: ((ShortArray, Int) -> Unit)? = null) {
        paused = false
        sampleSink = onSample
        sentenceChannel = Channel(Channel.UNLIMITED)
        val channel = sentenceChannel
        playbackJob = scope.launch(Dispatchers.Default) { runPlaybackLoop(channel) }
    }

    fun enqueue(text: String) {
        sentenceChannel.trySend(text)
    }

    /** Signals this turn has no more sentences coming — the playback loop finishes whatever's
     * queued, then ends on its own rather than waiting forever for a receive that never comes. */
    fun endTurn() {
        sentenceChannel.close()
    }

    /** Waits for the current turn's playback loop to actually finish (all queued sentences
     * played, or [stop] cancelled it) — `join()` on a cancelled job still returns normally
     * rather than throwing, since only the caller's own cancellation would do that. Lets a
     * caller build a turn's final waveform/replay audio only once it's all really played. */
    suspend fun awaitCompletion() {
        playbackJob?.join()
    }

    /** Barge-in cutoff: stops playback and cancels in-flight synthesis instantly. */
    fun stop() {
        paused = false
        playbackJob?.cancel()
        playbackJob = null
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        sentenceChannel.close()
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    fun togglePause(): Boolean {
        paused = !paused
        if (paused) runCatching { audioTrack?.pause() } else runCatching { audioTrack?.play() }
        return paused
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }

    private suspend fun CoroutineScope.runPlaybackLoop(channel: Channel<String>) {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            for (unused in channel) { /* drop — nobody will read this turn's sentences */ }
            return
        }
        try {
            // Blocking receive: waits for the first sentence to actually be enqueued (the LLM
            // hasn't necessarily produced one yet when this loop starts), or for immediate
            // channel closure (an empty turn).
            var currentJob = awaitNextSynthJob(channel)
            while (currentJob != null && isActive) {
                // Opportunistic, non-blocking prefetch of the following sentence so its
                // synthesis overlaps this one's playback — "not available yet" here just
                // means "not ready yet," not "no more sentences," so playback proceeds either way.
                val prefetched = channel.tryReceive().getOrNull()?.let { text -> asyncSynthesize(text) }

                val audio = currentJob.await()
                if (audio != null && isActive) playPcm(audio)

                currentJob = prefetched ?: awaitNextSynthJob(channel)
            }
        } finally {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
    }

    /** Blocks until either the next sentence arrives or the turn's channel is closed with
     * nothing left buffered (end of turn) — the only place that actually decides "no more
     * sentences are coming," as opposed to the non-blocking prefetch check. */
    private suspend fun CoroutineScope.awaitNextSynthJob(channel: Channel<String>): Deferred<TtsAudio?>? {
        val text = channel.receiveCatching().getOrNull() ?: return null
        return asyncSynthesize(text)
    }

    private fun CoroutineScope.asyncSynthesize(text: String): Deferred<TtsAudio?> = async(Dispatchers.Default) {
        synthSemaphore.withPermit {
            runCatching { engineSelector.resolve().synthesize(text, currentLang) }.getOrNull()
        }
    }

    private fun playPcm(audio: TtsAudio) {
        if (audio.samples.isEmpty()) return
        sampleSink?.invoke(audio.samples, audio.sampleRateHz)
        ensureAudioTrack(audio.sampleRateHz)
        val track = audioTrack ?: return
        if (!paused && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
    }

    private fun ensureAudioTrack(sampleRateHz: Int) {
        if (audioTrack != null && trackSampleRate == sampleRateHz) return
        audioTrack?.release()
        val minBufSize = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = if (minBufSize > 0) minBufSize else 4096
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        trackSampleRate = sampleRateHz
    }
}
