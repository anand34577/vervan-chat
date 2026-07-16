package com.vervan.chat.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays back one finished assistant turn's already-synthesized PCM for the voice chat thread's
 * playback bar — a single static [AudioTrack] (the whole reply's audio, already known and
 * short, unlike [TtsPlaybackQueue]'s streamed-while-synthesizing playback) so [seekTo] can use
 * [AudioTrack.setPlaybackHeadPosition] for real scrubbing instead of re-decoding/re-synthesizing.
 * One instance is reused across turns in [com.vervan.chat.ui.tools.VoiceChatScreen] — loading a
 * new turn stops whatever was playing.
 */
class ReplayAudioPlayer {
    private var track: AudioTrack? = null
    private var totalFrames = 0
    private var sampleRate = 0

    val isPlaying: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun load(samples: ShortArray, sampleRateHz: Int) {
        release()
        if (samples.isEmpty() || sampleRateHz <= 0) return
        sampleRate = sampleRateHz
        totalFrames = samples.size
        track = AudioTrack.Builder()
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
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track?.write(samples, 0, samples.size)
    }

    fun play() { track?.play() }
    fun pause() { track?.pause() }

    /** [AudioTrack.setPlaybackHeadPosition] only takes effect while paused/stopped, so pause
     * first, seek, then resume if it was playing — otherwise the seek is silently ignored. */
    fun seekTo(fraction: Float) {
        val t = track ?: return
        val wasPlaying = isPlaying
        t.pause()
        runCatching { t.setPlaybackHeadPosition((fraction.coerceIn(0f, 1f) * totalFrames).toInt()) }
        if (wasPlaying) t.play()
    }

    fun progressFraction(): Float {
        val t = track ?: return 0f
        if (totalFrames == 0) return 0f
        return (t.playbackHeadPosition.toFloat() / totalFrames).coerceIn(0f, 1f)
    }

    fun positionMs(): Int = if (sampleRate == 0) 0 else ((track?.playbackHeadPosition ?: 0) * 1000L / sampleRate).toInt()

    fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        totalFrames = 0
    }
}
