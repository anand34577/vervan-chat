package com.vervan.chat.ui.chat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Replays a voice turn's captured PCM audio straight from memory — no file involved, unlike
 * [VoiceMessageRow] (which plays a saved file via [android.media.MediaPlayer]). [VoiceTurn]
 * already carries [samples]/[sampleRateHz] for both the user's just-recorded utterance and,
 * once playback finishes, the assistant's spoken reply — this is what turns that otherwise-
 * discarded in-memory audio into an actual "recorded audio preview" the user can tap and
 * re-listen to, matching the tap-to-play-tap-to-seek convention of WhatsApp/Telegram voice
 * bubbles. [android.media.AudioTrack] in MODE_STATIC (not [TtsPlaybackQueue]'s streaming mode)
 * because the full clip is already known upfront — one write, then play/pause/seek against it.
 */
@Composable
internal fun VoiceTurnAudioPlayer(
    samples: ShortArray,
    sampleRateHz: Int,
    waveform: List<Float>,
    durationMs: Int,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    if (samples.isEmpty() || sampleRateHz <= 0) return

    var track by remember(samples) { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember(samples) { mutableStateOf(false) }
    var progress by remember(samples) { mutableFloatStateOf(0f) } // 0..1
    var loadFailed by remember(samples) { mutableStateOf(false) }

    DisposableEffect(samples) {
        onDispose {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            track = null
        }
    }

    fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        return runCatching {
            val bytes = samples.size * 2
            val minBuf = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufBytes = maxOf(bytes, if (minBuf > 0) minBuf else bytes)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .apply { write(samples, 0, samples.size) }
        }.onSuccess {
            track = it
            loadFailed = false
        }.onFailure {
            loadFailed = true
        }.getOrNull()
    }

    fun seekTo(fraction: Float) {
        val t = ensureTrack() ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        val framePos = (clamped * samples.size).toInt().coerceIn(0, samples.size)
        val wasPlaying = isPlaying
        runCatching {
            t.pause()
            t.stop()
            t.reloadStaticData()
            t.setPlaybackHeadPosition(framePos)
            if (wasPlaying) t.play()
        }
        progress = clamped
    }

    // Polls playback position instead of a completion callback — AudioTrack has no
    // position-changed listener, and 80ms is smooth enough for a voice-note scrubber while
    // being cheap enough to run for the lifetime of a visible bubble.
    LaunchedEffect(isPlaying, track) {
        val t = track ?: return@LaunchedEffect
        while (isPlaying) {
            val posFrames = runCatching { t.playbackHeadPosition }.getOrDefault(0)
            progress = if (samples.isNotEmpty()) (posFrames.toFloat() / samples.size).coerceIn(0f, 1f) else 0f
            if (posFrames >= samples.size) {
                isPlaying = false
                progress = 0f
                runCatching { t.stop(); t.reloadStaticData() }
            }
            delay(80)
        }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val t = ensureTrack() ?: return@IconButton
                if (isPlaying) {
                    runCatching { t.pause() }
                    isPlaying = false
                } else {
                    if (progress >= 1f) {
                        runCatching { t.stop(); t.reloadStaticData(); t.setPlaybackHeadPosition(0) }
                        progress = 0f
                    }
                    runCatching { t.play() }
                    isPlaying = true
                }
            },
            modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.15f), MaterialTheme.shapes.small)
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause recording" else "Play recording",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.sm)
                .height(32.dp)
                .pointerInput(samples) {
                    detectTapGestures { offset -> seekTo((offset.x / size.width.toFloat())) }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            VoiceWaveformBars(waveform, progress, accent)
        }
        Text(
            formatDurationMs(if (isPlaying || progress > 0f) (progress * durationMs).toInt() else durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceWaveformBars(waveform: List<Float>, progress: Float, accent: androidx.compose.ui.graphics.Color) {
    val bars = waveform.ifEmpty { List(24) { 0.15f } }
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { index, level ->
            val played = index.toFloat() / bars.size < progress
            Box(
                Modifier
                    .weight(1f)
                    .height((3 + level.coerceIn(0f, 1f) * 24).dp)
                    .background(
                        if (played) accent else accent.copy(alpha = 0.28f),
                        CircleShape
                    )
            )
        }
    }
}

/** Compact "not ready yet" placeholder for a turn whose audio is still being assembled
 * ([com.vervan.chat.voice.VoiceTurn.audioPending]) — keeps the row's height stable instead of
 * the player popping in once playback finishes. */
@Composable
internal fun VoiceTurnAudioPending(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp))
        Text(
            "Preparing audio…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.sm)
        )
    }
}

private fun formatDurationMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
