package com.vervan.chat.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Voice message row with an actual waveform visualization (replaces the circa-2015 icon+slider
 * pattern). Matches what WhatsApp/Telegram/iMessage/Slack all do — gives audio messages weight
 * and personality, and makes "where am I in this clip" glanceable.
 *
 * The waveform itself is deterministic from [seedAmplitudes] (or synthesized when null) so it
 * doesn't visibly reflow between recompositions; [progress] 0..1 fills it left-to-right with a
 * primary→onSurface gradient. Live recording mode (passing [isRecording]=true) drives the bars
 * from a low-frequency sine sweep + the deterministic base so they look "alive" without the
 * permissions/safety issues of wiring a real amplitude source into a pure-UI component.
 */
@Composable
fun VoiceWaveform(
    durationSeconds: Int,
    progress: Float,
    onSeek: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isRecording: Boolean = false,
    seedAmplitudes: List<Float>? = null,
    reducedMotion: Boolean = rememberReducedMotion()
) {
    val amps = seedAmplitudes ?: remember { synthesizeAmplitudes(48) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Box(modifier = Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.CenterStart) {
            WaveformBars(
                amplitudes = amps,
                progress = progress,
                isRecording = isRecording,
                reducedMotion = reducedMotion,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
        Text(
            text = formatDuration(if (isPlaying || isRecording) (durationSeconds * progress).toInt() else durationSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WaveformBars(
    amplitudes: List<Float>,
    progress: Float,
    isRecording: Boolean,
    reducedMotion: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveAmpPhase = if (isRecording && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "voiceRec")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 4).toFloat(),
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "voiceRecSweep"
        ).value
    } else 0f

    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = "Audio waveform" }
    ) {
        val totalWidth = size.width
        val barCount = amplitudes.size
        val gap = 2.dp.toPx()
        val barWidth = ((totalWidth - gap * (barCount - 1)) / barCount).coerceAtLeast(1.dp.toPx())
        val centerY = size.height / 2
        val progressX = totalWidth * progress.coerceIn(0f, 1f)

        amplitudes.forEachIndexed { index, amp0 ->
            // Recording animation: bars nearest to a moving "playhead" pulse taller.
            val amp = if (isRecording) {
                val phase = index.toFloat() / barCount
                val liveBoost = if (reducedMotion) 0f else (sin((phase * Math.PI * 2 + liveAmpPhase).toDouble())).toFloat() * 0.35f
                (amp0 + liveBoost).coerceIn(0.15f, 1f)
            } else amp0
            val barHeight = (size.height * amp).coerceAtLeast(2.dp.toPx())
            val x = index * (barWidth + gap)
            val color = if (x <= progressX) playedColor else unplayedColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/** Deterministic pseudo-random amplitudes derived from the index — no noise source needed,
 *  the waveform stays stable across recompositions and the bars look organic rather than uniform. */
private fun synthesizeAmplitudes(count: Int): List<Float> {
    val random = Random(42)
    return (0 until count).map { i ->
        // Mix a base sine with a low-frequency envelope and a random offset for natural look.
        val base = (sin(i * 0.6) * 0.3 + 0.55).toFloat()
        val envelope = (sin(i * 0.13) * 0.2 + 0.85).toFloat()
        val jitter = random.nextFloat() * 0.3f
        (base * envelope + jitter).coerceIn(0.18f, 1f)
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
