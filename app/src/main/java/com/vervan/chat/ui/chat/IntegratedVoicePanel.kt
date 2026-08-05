package com.vervan.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.voice.VoiceControllerState
import java.util.Locale

/**
 * The compact command surface for a local voice conversation. The live microphone signal is shown
 * only while it represents actual input; idle and processing states use direct status copy instead
 * of decorative visualization. Every horizontal group uses weights or a compact variant so labels
 * cannot force the composer wider than the available phone width.
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
    modifier: Modifier = Modifier,
    pushToTalkEnabled: Boolean = false,
    onTogglePushToTalkMode: () -> Unit = {},
    pushToTalkHeld: Boolean = false,
    onPushToTalkPress: () -> Unit = {},
    onPushToTalkRelease: () -> Unit = {}
) {
    val reducedMotion = rememberReducedMotion()

    // The panel being composed at all means a voice session is live (this composable only
    // exists while handsFreeActive — see ChatScreen), so the screen must not time out and dim
    // mid-conversation the way it would during silent reading. Cleared on dispose so a normal
    // chat screen behind/after it keeps the user's usual screen-timeout setting.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Card(
        modifier = modifier.fillMaxWidth().widthIn(max = 840.dp),
        shape = VervanExtraShapes.hero,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 390.dp
            val horizontalPadding = if (compact) Space.md else Space.lg
            Column(
                Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = Space.md)
            ) {
                VoicePanelHeader(
                    state = state,
                    modelName = modelName,
                    microphoneMuted = microphoneMuted,
                    playbackPaused = playbackPaused,
                    onEnd = onEnd
                )

                if (state == VoiceControllerState.IDLE || state == VoiceControllerState.LISTENING) {
                    ListeningModeToggle(
                        pushToTalkEnabled = pushToTalkEnabled,
                        onToggle = onTogglePushToTalkMode,
                        compact = compact,
                        modifier = Modifier.padding(top = Space.md)
                    )
                }

                attachmentLabel?.let {
                    AttachmentBanner(it, Modifier.padding(top = Space.sm))
                }

                if (errorMessage != null) {
                    VoicePanelError(errorMessage, onRetry)
                } else {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        },
                        label = "voice-console-state"
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
                            pushToTalkEnabled = pushToTalkEnabled,
                            pushToTalkHeld = pushToTalkHeld,
                            compact = compact,
                            onStart = onStart,
                            onFinishUtterance = onFinishUtterance,
                            onCancelUtterance = onCancelUtterance,
                            onInterrupt = onInterrupt,
                            onTogglePlayback = onTogglePlayback,
                            onPushToTalkPress = onPushToTalkPress,
                            onPushToTalkRelease = onPushToTalkRelease
                        )
                    }
                }

                VoiceControlDock(
                    compact = compact,
                    microphoneMuted = microphoneMuted,
                    speechOutputEnabled = speechOutputEnabled,
                    onToggleMute = onToggleMute,
                    onAttach = onAttach,
                    onKeyboard = onKeyboard,
                    onToggleSpeechOutput = onToggleSpeechOutput,
                    onMore = onMore,
                    modifier = Modifier.padding(top = Space.md)
                )
            }
        }
    }
}

@Composable
private fun VoicePanelHeader(
    state: VoiceControllerState,
    modelName: String?,
    microphoneMuted: Boolean,
    playbackPaused: Boolean,
    onEnd: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Waves, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .background(voiceStateColor(state), CircleShape)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            Text("Voice chat", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    voicePanelStateLabel(state, microphoneMuted, playbackPaused),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (!modelName.isNullOrBlank()) {
                    Text("  •  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        modelName,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
        Surface(
            onClick = onEnd,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "End voice session" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ListeningModeToggle(
    pushToTalkEnabled: Boolean,
    onToggle: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), shape = VervanExtraShapes.pill, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            ModeSegment(
                icon = Icons.Filled.GraphicEq,
                label = "Hands-free",
                selected = !pushToTalkEnabled,
                onClick = { if (pushToTalkEnabled) onToggle() },
                modifier = Modifier.weight(1f)
            )
            ModeSegment(
                icon = Icons.Filled.FrontHand,
                label = "Push to talk",
                selected = pushToTalkEnabled,
                onClick = { if (!pushToTalkEnabled) onToggle() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeSegment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp).semantics {
            contentDescription = "$label listening mode${if (selected) ", selected" else ""}"
        },
        shape = VervanExtraShapes.pill,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

@Composable
private fun AttachmentBanner(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(start = Space.sm)) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Added to your next message", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
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
    pushToTalkEnabled: Boolean,
    pushToTalkHeld: Boolean,
    compact: Boolean,
    onStart: () -> Unit,
    onFinishUtterance: () -> Unit,
    onCancelUtterance: () -> Unit,
    onInterrupt: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPushToTalkPress: () -> Unit,
    onPushToTalkRelease: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) Space.md else Space.lg, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                VoiceControllerState.IDLE -> IdleState(microphoneMuted, pushToTalkEnabled, onStart)
                VoiceControllerState.LISTENING -> if (pushToTalkEnabled) {
                    PushToTalkListeningLayout(
                        waveform = waveform,
                        held = pushToTalkHeld,
                        muted = microphoneMuted,
                        onPress = onPushToTalkPress,
                        onRelease = onPushToTalkRelease,
                        onCancelUtterance = onCancelUtterance,
                        liveTranscript = liveTranscript
                    )
                } else {
                    HandsFreeListeningLayout(
                        waveform = waveform,
                        elapsedMs = elapsedMs,
                        microphoneMuted = microphoneMuted,
                        liveTranscript = liveTranscript,
                        onCancelUtterance = onCancelUtterance,
                        onFinishUtterance = onFinishUtterance
                    )
                }
                VoiceControllerState.TRANSCRIBING -> ProcessingState(
                    title = "Turning speech into text",
                    detail = sttLabel,
                    transcript = liveTranscript,
                )
                VoiceControllerState.LOADING_MODEL -> ProcessingState(
                    title = "Preparing your local model",
                    detail = "This stays on your device",
                    transcript = "",
                )
                VoiceControllerState.THINKING -> ProcessingState(
                    title = "Thinking through your request",
                    detail = if (speechOutputEnabled) "Text first, then voice with $ttsLabel" else "Preparing a text response",
                    transcript = "",
                    action = {
                        TextButton(onClick = onInterrupt) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Stop", modifier = Modifier.padding(start = Space.xs))
                        }
                    }
                )
                VoiceControllerState.SPEAKING -> SpeakingState(
                    playbackPaused = playbackPaused,
                    hasEchoCancellation = hasEchoCancellation,
                    onInterrupt = onInterrupt,
                    onTogglePlayback = onTogglePlayback
                )
            }
        }
    }
}

@Composable
private fun IdleState(microphoneMuted: Boolean, pushToTalkEnabled: Boolean, onStart: () -> Unit) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = if (microphoneMuted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (microphoneMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(25.dp)
            )
        }
    }
    Text(
        if (microphoneMuted) "Microphone is muted" else "Ready when you are",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Space.sm)
    )
    Text(
        when {
            microphoneMuted -> "Unmute below to begin"
            pushToTalkEnabled -> "Start, then hold the mic while you speak"
            else -> "Start and speak naturally — silence sends the turn"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Space.xs)
    )
    FilledIconButton(
        onClick = onStart,
        enabled = !microphoneMuted,
        modifier = Modifier.size(56.dp).clip(CircleShape).padding(top = Space.sm),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Start voice session", modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LegacyHandsFreeListening(
    waveform: List<Float>,
    elapsedMs: Int,
    microphoneMuted: Boolean,
    liveTranscript: String,
    onCancelUtterance: () -> Unit,
    onFinishUtterance: () -> Unit
) {
    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            if (microphoneMuted) "MICROPHONE MUTED" else "LISTENING  ${formatVoiceElapsed(elapsedMs)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
            color = if (microphoneMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(20.dp)
        )
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(top = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (waveform.isNotEmpty()) {
                    NormalVoiceVisualizer(
                        waveform = waveform,
                        muted = microphoneMuted,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )
                } else {
                    Text(
                        "Waiting for speech",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedIconButton(onClick = onCancelUtterance, modifier = Modifier.padding(start = Space.sm).size(42.dp).clip(CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = "Discard recording", modifier = Modifier.size(18.dp))
            }
            FilledIconButton(onClick = onFinishUtterance, modifier = Modifier.padding(start = Space.xs).size(48.dp).clip(CircleShape)) {
                Icon(Icons.Filled.Check, contentDescription = "Use speech", modifier = Modifier.size(21.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            VoiceCaption(liveTranscript)
        }
    }
}

@Composable
private fun LegacyPushToTalkListening(
    waveform: List<Float>,
    held: Boolean,
    muted: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancelUtterance: () -> Unit,
    liveTranscript: String
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragUpPx by remember { mutableFloatStateOf(0f) }
    var pastCancelThreshold by remember { mutableStateOf(false) }
    val cancelThresholdPx = with(density) { CancelDragThresholdDp.dp.toPx() }
    val scale by animateFloatAsState(
        if (held) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "push-to-talk-scale"
    )
    val visualOffset = dragUpPx.coerceIn(0f, cancelThresholdPx * 1.15f)

    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            when {
                muted -> "MICROPHONE MUTED"
                pastCancelThreshold -> "RELEASE TO DISCARD"
                held -> "LISTENING"
                else -> "PUSH TO TALK"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
            color = when {
                muted || pastCancelThreshold -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.height(20.dp)
        )

    if (held && waveform.isNotEmpty()) {
        NormalVoiceVisualizer(
            waveform = waveform,
            muted = muted,
            modifier = Modifier.fillMaxWidth().height(40.dp)
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = Space.xs)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .offset { androidx.compose.ui.unit.IntOffset(0, -visualOffset.toInt()) }
                .background(
                    when {
                        pastCancelThreshold -> MaterialTheme.colorScheme.errorContainer
                        held -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    CircleShape
                )
                .pointerInput(muted) {
                    if (muted) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onPress()
                        dragUpPx = 0f
                        pastCancelThreshold = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dragUpPx = (down.position.y - change.position.y).coerceAtLeast(0f)
                            pastCancelThreshold = dragUpPx > cancelThresholdPx
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                        val cancel = pastCancelThreshold
                        dragUpPx = 0f
                        pastCancelThreshold = false
                        onRelease()
                        if (cancel) onCancelUtterance()
                    }
                }
                .semantics { contentDescription = "Hold to talk, slide up to cancel" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (pastCancelThreshold) Icons.Filled.DeleteOutline else Icons.Filled.Mic,
                contentDescription = null,
                tint = when {
                    pastCancelThreshold -> MaterialTheme.colorScheme.onErrorContainer
                    held -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(25.dp)
            )
        }
    }
    Text(
        when {
            muted -> "Microphone muted"
            pastCancelThreshold -> "Release to discard"
            held -> "Listening — slide up to discard"
            else -> "Hold to talk"
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (pastCancelThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
    )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            if (held) {
                VoiceCaption(liveTranscript)
            } else {
                Text(
                    "Slide up to discard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
        }
    }
}

private val VoiceListeningContentHeight = 132.dp

@Composable
private fun HandsFreeListeningLayout(
    waveform: List<Float>,
    elapsedMs: Int,
    microphoneMuted: Boolean,
    liveTranscript: String,
    onCancelUtterance: () -> Unit,
    onFinishUtterance: () -> Unit
) {
    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            if (microphoneMuted) "MICROPHONE MUTED" else "LISTENING  ${formatVoiceElapsed(elapsedMs)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
            color = if (microphoneMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(20.dp)
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = Space.xs),
            contentAlignment = Alignment.Center
        ) {
            if (waveform.isNotEmpty()) {
                NormalVoiceVisualizer(
                    waveform = waveform,
                    muted = microphoneMuted,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                )
            } else {
                Text(
                    "Speak naturally",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                if (liveTranscript.isBlank()) "Silence sends the turn" else liveTranscript,
                style = MaterialTheme.typography.bodySmall,
                color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            OutlinedIconButton(
                onClick = onCancelUtterance,
                modifier = Modifier.size(44.dp).clip(CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Discard recording", modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = onFinishUtterance,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Use speech", modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun PushToTalkListeningLayout(
    waveform: List<Float>,
    held: Boolean,
    muted: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancelUtterance: () -> Unit,
    liveTranscript: String
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragUpPx by remember { mutableFloatStateOf(0f) }
    var pastCancelThreshold by remember { mutableStateOf(false) }
    val cancelThresholdPx = with(density) { CancelDragThresholdDp.dp.toPx() }
    val scale by animateFloatAsState(
        if (held) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "push-to-talk-layout-scale"
    )
    val visualOffset = dragUpPx.coerceIn(0f, cancelThresholdPx * 1.15f)

    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            when {
                muted -> "MICROPHONE MUTED"
                pastCancelThreshold -> "RELEASE TO DISCARD"
                held -> "LISTENING"
                else -> "PUSH TO TALK"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
            color = when {
                muted || pastCancelThreshold -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.height(20.dp)
        )
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = Space.xs),
            contentAlignment = Alignment.Center
        ) {
            if (held && waveform.isNotEmpty()) {
                NormalVoiceVisualizer(
                    waveform = waveform,
                    muted = muted,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale)
                    .offset { androidx.compose.ui.unit.IntOffset(0, -visualOffset.toInt()) }
                    .background(
                        when {
                            pastCancelThreshold -> MaterialTheme.colorScheme.errorContainer
                            held -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape
                    )
                    .pointerInput(muted) {
                        if (muted) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            onPress()
                            dragUpPx = 0f
                            pastCancelThreshold = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                dragUpPx = (down.position.y - change.position.y).coerceAtLeast(0f)
                                pastCancelThreshold = dragUpPx > cancelThresholdPx
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                            }
                            val cancel = pastCancelThreshold
                            dragUpPx = 0f
                            pastCancelThreshold = false
                            onRelease()
                            if (cancel) onCancelUtterance()
                        }
                    }
                    .semantics { contentDescription = "Hold to talk, slide up to cancel" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (pastCancelThreshold) Icons.Filled.DeleteOutline else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = when {
                        pastCancelThreshold -> MaterialTheme.colorScheme.onErrorContainer
                        held -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(27.dp)
                )
            }
        }
        Text(
            if (liveTranscript.isBlank()) "Hold to speak · release when finished" else liveTranscript,
            style = MaterialTheme.typography.bodySmall,
            color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }
}

@Composable
private fun ProcessingState(
    title: String,
    detail: String,
    transcript: String,
    action: (@Composable () -> Unit)? = null
) {
    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(42.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
    Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    )
    action?.invoke()
    VoiceCaption(transcript)
}

@Composable
private fun SpeakingState(
    playbackPaused: Boolean,
    hasEchoCancellation: Boolean,
    onInterrupt: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Text(
        if (playbackPaused) "RESPONSE PAUSED" else "ASSISTANT IS SPEAKING",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
        color = MaterialTheme.colorScheme.primary
    )
    Surface(
        modifier = Modifier.size(54.dp).padding(top = Space.xs),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (playbackPaused) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    Text(
        if (hasEchoCancellation) "You can speak over the reply at any time" else "Interrupt when you want to speak",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Space.xs)
    )
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xl, Alignment.CenterHorizontally)
    ) {
        CircularVoiceAction(Icons.Filled.Mic, "Interrupt", onInterrupt, Modifier.weight(1f))
        CircularVoiceAction(
            if (playbackPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            if (playbackPaused) "Resume" else "Pause",
            onTogglePlayback,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun CircularVoiceAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).clip(CircleShape)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Space.xs))
    }
}

/** Conventional equalizer-style mic visualizer. The fixed-height row keeps its center aligned
 * with the adjacent record controls; bar heights come from the controller's real input levels. */
@Composable
private fun NormalVoiceVisualizer(
    waveform: List<Float>,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val samples = waveform.takeLast(24)
    val bars = List(24) { index ->
        samples.getOrNull(index - (24 - samples.size)) ?: 0.08f
    }
    Row(
        modifier = modifier
            .semantics { contentDescription = "Live microphone levels" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { level ->
            Box(
                Modifier
                    .weight(1f)
                    .height((4 + level.coerceIn(0.08f, 1f) * 30).dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (muted) 0.28f else 0.86f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun VoiceCaption(text: String) {
    if (text.isBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            Text("LIVE TRANSCRIPT  •  NOT SENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun VoicePanelError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        message.contains("clear speech", ignoreCase = true) -> "No clear speech detected"
                        message.contains("unavailable", ignoreCase = true) -> "Voice input unavailable"
                        else -> "Voice input needs attention"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Replay, contentDescription = "Retry voice session")
            }
        }
    }
}

@Composable
private fun VoiceControlDock(
    compact: Boolean,
    microphoneMuted: Boolean,
    speechOutputEnabled: Boolean,
    onToggleMute: () -> Unit,
    onAttach: () -> Unit,
    onKeyboard: () -> Unit,
    onToggleSpeechOutput: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), shape = VervanExtraShapes.pill, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.Top
        ) {
            VoicePanelAction(
                icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (microphoneMuted) "Unmute" else "Mute",
                selected = microphoneMuted,
                showLabel = !compact,
                onClick = onToggleMute,
                modifier = Modifier.weight(1f)
            )
            VoicePanelAction(Icons.Filled.AttachFile, "Attach", showLabel = !compact, onClick = onAttach, modifier = Modifier.weight(1f))
            VoicePanelAction(Icons.Filled.Keyboard, "Keyboard", showLabel = !compact, onClick = onKeyboard, modifier = Modifier.weight(1f))
            VoicePanelAction(
                icon = if (speechOutputEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = if (speechOutputEnabled) "Voice on" else "Voice off",
                selected = !speechOutputEnabled,
                showLabel = !compact,
                onClick = onToggleSpeechOutput,
                modifier = Modifier.weight(1f)
            )
            VoicePanelAction(Icons.Filled.MoreHoriz, "Options", showLabel = !compact, onClick = onMore, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun VoicePanelAction(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (selected) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(42.dp).clip(CircleShape)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
            }
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(42.dp).clip(CircleShape)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
            }
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun voiceStateColor(state: VoiceControllerState): Color = when (state) {
    VoiceControllerState.IDLE -> MaterialTheme.colorScheme.outline
    VoiceControllerState.LISTENING -> MaterialTheme.colorScheme.primary
    VoiceControllerState.TRANSCRIBING, VoiceControllerState.LOADING_MODEL, VoiceControllerState.THINKING -> MaterialTheme.colorScheme.tertiary
    VoiceControllerState.SPEAKING -> MaterialTheme.colorScheme.secondary
}

private fun voicePanelStateLabel(state: VoiceControllerState, muted: Boolean, paused: Boolean): String = when {
    muted -> "Microphone muted"
    state == VoiceControllerState.IDLE -> "Ready on device"
    state == VoiceControllerState.LOADING_MODEL -> "Preparing model"
    state == VoiceControllerState.LISTENING -> "Listening"
    state == VoiceControllerState.TRANSCRIBING -> "Transcribing"
    state == VoiceControllerState.THINKING -> "Generating response"
    state == VoiceControllerState.SPEAKING && paused -> "Response paused"
    else -> "Speaking"
}

private fun formatVoiceElapsed(ms: Int): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}

private const val CancelDragThresholdDp = 64f
