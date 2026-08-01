package com.vervan.chat.ui.chat

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.voice.VoiceControllerState
import java.util.Locale

/**
 * The normal chat composer's expanded hands-free state. All controls remain in fixed positions
 * across state changes so mute, keyboard, attachments and End never disappear when needed.
 */
@Composable
internal fun IntegratedVoicePanel(
    state: VoiceControllerState,
    waveform: List<Float>,
    elapsedMs: Int,
    liveTranscript: String,
    modelName: String?,
    sttLabel: String,
    ttsLabel: String,
    microphoneMuted: Boolean,
    speechOutputEnabled: Boolean,
    playbackPaused: Boolean,
    hasEchoCancellation: Boolean,
    attachmentLabel: String?,
    errorMessage: String?,
    onStart: () -> Unit,
    onFinishUtterance: () -> Unit,
    onCancelUtterance: () -> Unit,
    onInterrupt: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeechOutput: () -> Unit,
    onAttach: () -> Unit,
    onKeyboard: () -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    Card(
        modifier = modifier.fillMaxWidth().widthIn(max = 840.dp),
        shape = VervanExtraShapes.composer,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            VoicePanelHeader(
                state = state,
                modelName = modelName,
                microphoneMuted = microphoneMuted,
                speechOutputEnabled = speechOutputEnabled,
                playbackPaused = playbackPaused
            )

            attachmentLabel?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(start = Space.sm)) {
                            Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Included with your next spoken message", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                VoicePanelError(errorMessage, onRetry)
            } else {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn() togetherWith fadeOut()
                    },
                    label = "integrated-voice-state"
                ) { active ->
                    VoiceStateBody(
                        state = active,
                        waveform = waveform,
                        elapsedMs = elapsedMs,
                        liveTranscript = liveTranscript,
                        playbackPaused = playbackPaused,
                        microphoneMuted = microphoneMuted,
                        hasEchoCancellation = hasEchoCancellation,
                        speechOutputEnabled = speechOutputEnabled,
                        sttLabel = sttLabel,
                        ttsLabel = ttsLabel,
                        reducedMotion = reducedMotion,
                        onStart = onStart,
                        onFinishUtterance = onFinishUtterance,
                        onCancelUtterance = onCancelUtterance,
                        onInterrupt = onInterrupt,
                        onTogglePlayback = onTogglePlayback
                    )
                }
            }

            ResponsiveActions(modifier = Modifier.padding(top = Space.sm)) {
                VoicePanelAction(
                    icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (microphoneMuted) "Unmute" else "Mute",
                    selected = microphoneMuted,
                    onClick = onToggleMute
                )
                VoicePanelAction(Icons.Filled.AttachFile, "Attach", onClick = onAttach)
                VoicePanelAction(Icons.Filled.Keyboard, "Keyboard", onClick = onKeyboard)
                VoicePanelAction(
                    icon = if (speechOutputEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    label = if (speechOutputEnabled) "Voice on" else "Voice off",
                    selected = speechOutputEnabled,
                    onClick = onToggleSpeechOutput
                )
                VoicePanelAction(Icons.Filled.MoreHoriz, "Options", onClick = onMore)
            }

            Button(
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Space.sm)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("End voice session", modifier = Modifier.padding(start = Space.sm))
            }

            Text(
                "Mic: $sttLabel · Replies: ${if (speechOutputEnabled) ttsLabel else "Off"} · " +
                    if (sttLabel.contains("Android", ignoreCase = true)) "Device service" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun VoicePanelHeader(
    state: VoiceControllerState,
    modelName: String?,
    microphoneMuted: Boolean,
    speechOutputEnabled: Boolean,
    playbackPaused: Boolean
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(
            Modifier.weight(1f).padding(horizontal = Space.md).semantics {
                liveRegion = LiveRegionMode.Polite
            }
        ) {
            Text(voicePanelStateLabel(state, microphoneMuted, playbackPaused), style = MaterialTheme.typography.titleMedium)
            Text(
                modelName ?: "Local model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Text(
                if (speechOutputEnabled) "OFFLINE · VOICE" else "OFFLINE · SILENT",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)
            )
        }
    }
}

@Composable
private fun VoiceStateBody(
    state: VoiceControllerState,
    waveform: List<Float>,
    elapsedMs: Int,
    liveTranscript: String,
    playbackPaused: Boolean,
    microphoneMuted: Boolean,
    hasEchoCancellation: Boolean,
    speechOutputEnabled: Boolean,
    sttLabel: String,
    ttsLabel: String,
    reducedMotion: Boolean,
    onStart: () -> Unit,
    onFinishUtterance: () -> Unit,
    onCancelUtterance: () -> Unit,
    onInterrupt: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            VoiceControllerState.IDLE -> {
                FilledIconButton(
                    onClick = onStart,
                    enabled = !microphoneMuted,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Start listening", modifier = Modifier.size(30.dp))
                }
                Text("Tap to start listening", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
            }

            VoiceControllerState.LISTENING -> {
                VoiceLevelBars(waveform, muted = microphoneMuted)
                Text(
                    if (microphoneMuted) "Microphone muted" else formatVoiceElapsed(elapsedMs),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Space.sm)
                )
                VoiceCaption(liveTranscript, provisional = true)
                Row(
                    Modifier.padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedIconButton(onClick = onCancelUtterance, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Discard this recording")
                        }
                        Text(
                            "Discard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = onFinishUtterance, modifier = Modifier.size(68.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = "Finish and transcribe", modifier = Modifier.size(28.dp))
                        }
                        Text(
                            "Use speech",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs)
                        )
                    }
                }
            }

            VoiceControllerState.TRANSCRIBING -> {
                ProcessingGlyph(Icons.Filled.GraphicEq, reducedMotion)
                Text(
                    "Converting speech to text with $sttLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
                VoiceCaption(liveTranscript, provisional = true)
            }

            VoiceControllerState.THINKING, VoiceControllerState.LOADING_MODEL -> {
                ProcessingGlyph(Icons.Filled.GraphicEq, reducedMotion)
                Text(
                    when {
                        state == VoiceControllerState.LOADING_MODEL ->
                            "Loading the selected model on this device"
                        speechOutputEnabled ->
                            "Generating text · preparing speech with $ttsLabel"
                        else ->
                            "Generating a text-only response"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
                if (state == VoiceControllerState.THINKING) {
                    TextButton(
                        onClick = onInterrupt,
                        modifier = Modifier.padding(top = Space.sm)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Stop response", modifier = Modifier.padding(start = Space.xs))
                    }
                }
            }

            VoiceControllerState.SPEAKING -> {
                StateGlyph(if (playbackPaused) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp)
                Text(
                    if (hasEchoCancellation) "Start speaking or tap Interrupt"
                    else "Tap Interrupt to speak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
                Row(
                    Modifier.padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    FilledTonalIconButton(onClick = onInterrupt, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = "Interrupt and speak")
                    }
                    FilledTonalIconButton(onClick = onTogglePlayback, modifier = Modifier.size(56.dp)) {
                        Icon(
                            if (playbackPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (playbackPaused) "Resume speaking" else "Pause speaking"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCaption(text: String, provisional: Boolean) {
    if (text.isBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(
                if (provisional) "LIVE CAPTION · NOT SENT" else "TRANSCRIPT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs))
        }
    }
}

@Composable
private fun VoiceLevelBars(waveform: List<Float>, muted: Boolean) {
    val bars = waveform.takeLast(28).ifEmpty { List(28) { 0.08f } }
    Row(
        Modifier.fillMaxWidth().height(42.dp).alpha(if (muted) 0.35f else 1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { level ->
            Box(
                Modifier.weight(1f).height((4 + level.coerceIn(0f, 1f) * 34).dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun StateGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ProcessingGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    reducedMotion: Boolean
) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (!reducedMotion) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
        StateGlyph(icon)
    }
}

@Composable
private fun VoicePanelError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Voice session needs attention", style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.xs))
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Replay, contentDescription = "Retry voice session")
            }
        }
    }
}

@Composable
private fun VoicePanelAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 54.dp)) {
        if (selected) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = label)
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp).semantics { contentDescription = label }
            ) {
                Icon(icon, contentDescription = null)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun voicePanelStateLabel(state: VoiceControllerState, muted: Boolean, paused: Boolean): String = when {
    muted -> "Microphone muted"
    state == VoiceControllerState.IDLE -> "Voice session ready"
    state == VoiceControllerState.LOADING_MODEL -> "Preparing local model"
    state == VoiceControllerState.LISTENING -> "Listening"
    state == VoiceControllerState.TRANSCRIBING -> "Transcribing speech"
    state == VoiceControllerState.THINKING -> "Generating response"
    state == VoiceControllerState.SPEAKING && paused -> "Response paused"
    else -> "Speaking response"
}

private fun formatVoiceElapsed(ms: Int): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
