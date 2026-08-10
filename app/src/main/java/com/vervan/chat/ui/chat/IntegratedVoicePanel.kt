package com.vervan.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.voice.VoiceControllerState
import kotlin.math.PI
import kotlin.math.sin
import java.util.Locale

/**
 * The compact command surface for a local voice conversation. Listening uses a tonal waveform stage
 * that reflects real microphone levels and gently breathes while the session is waiting for speech;
 * idle and processing states use direct status copy instead of decorative visualization. Every
 * horizontal group uses weights or a compact variant so labels cannot force the composer wider than
 * the available phone width.
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

    val sessionAccent by animateColorAsState(
        targetValue = voiceStateColor(state),
        animationSpec = tween(if (reducedMotion) 1 else 420),
        label = "voice-session-accent"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .animateContentSize(animationSpec = tween(if (reducedMotion) 1 else 320)),
        shape = VervanExtraShapes.hero,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            sessionAccent.copy(alpha = if (state == VoiceControllerState.IDLE) 0.42f else 0.68f)
        )
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
    val voiceEndDescription = stringResource(R.string.voice_end_session)
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
            VoiceActivityIndicator(
                state = state,
                modifier = Modifier.align(Alignment.BottomEnd).size(18.dp)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            Text(stringResource(R.string.voice_chat), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val stateLabel = voicePanelStateLabel(state, microphoneMuted, playbackPaused)
                val voiceStatusDescription = stringResource(R.string.voice_status, stateLabel)
                Surface(
                    shape = VervanExtraShapes.pill,
                    color = voiceStateColor(state).copy(alpha = 0.13f),
                    contentColor = voiceStateColor(state),
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = voiceStatusDescription
                    }
                ) {
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
                if (!modelName.isNullOrBlank()) {
                    Text("  •  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        modelName,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        OutlinedIconButton(
            onClick = onEnd,
            modifier = Modifier.size(40.dp).semantics { contentDescription = voiceEndDescription }
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VoiceActivityIndicator(state: VoiceControllerState, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val active = state == VoiceControllerState.LISTENING || state == VoiceControllerState.SPEAKING
    val transition = rememberInfiniteTransition(label = "voice-activity-pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "voice-activity-progress"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        if (active && !reducedMotion) {
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(0.62f + progress * 0.38f)
                    .alpha((1f - progress) * 0.7f)
                    .border(1.dp, voiceStateColor(state), CircleShape)
            )
        }
        Box(
            Modifier
                .size(if (active) 9.dp else 7.dp)
                .background(voiceStateColor(state), CircleShape)
        )
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
        Row(Modifier.fillMaxWidth().padding(if (compact) 2.dp else 4.dp)) {
            ModeSegment(
                icon = Icons.Filled.GraphicEq,
                label = stringResource(R.string.voice_hands_free),
                selected = !pushToTalkEnabled,
                onClick = { if (pushToTalkEnabled) onToggle() },
                modifier = Modifier.weight(1f)
            )
            ModeSegment(
                icon = Icons.Filled.FrontHand,
                label = stringResource(R.string.voice_push_to_talk),
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
    val selectionSuffix = if (selected) stringResource(R.string.voice_selected_suffix) else ""
    val modeDescription = stringResource(R.string.voice_mode_status, label, selectionSuffix)
    val segmentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = tween(220),
        label = "voice-mode-segment"
    )
    val segmentContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "voice-mode-segment-content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp).semantics { contentDescription = modeDescription },
        shape = VervanExtraShapes.pill,
        color = segmentColor,
        contentColor = segmentContentColor,
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
            Text(stringResource(R.string.voice_added_next_message), style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
    val loadingDetail = stringResource(R.string.voice_local_device)
    val thinkingDetail = if (speechOutputEnabled) {
        stringResource(R.string.voice_text_then_voice, ttsLabel)
    } else {
        stringResource(R.string.voice_text_response)
    }
    val stageColor by animateColorAsState(
        targetValue = when (state) {
            VoiceControllerState.IDLE -> MaterialTheme.colorScheme.surfaceContainer
            VoiceControllerState.LISTENING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            VoiceControllerState.TRANSCRIBING,
            VoiceControllerState.LOADING_MODEL,
            VoiceControllerState.THINKING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
            VoiceControllerState.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f)
        },
        animationSpec = tween(if (reducedMotion) 1 else 360),
        label = "voice-stage-surface"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.extraLarge,
        color = stageColor,
        border = BorderStroke(1.dp, voiceStateColor(state).copy(alpha = 0.32f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) Space.md else Space.lg, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceActivityIndicator(state, Modifier.size(18.dp))
                Text(
                    voicePanelStateLabel(state, microphoneMuted, playbackPaused).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
                    color = voiceStateColor(state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.sm).weight(1f)
                )
                if (state == VoiceControllerState.LISTENING) {
                    Text(
                        formatVoiceElapsed(elapsedMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                        microphoneMuted = microphoneMuted,
                        liveTranscript = liveTranscript,
                        onCancelUtterance = onCancelUtterance,
                        onFinishUtterance = onFinishUtterance
                    )
                }
                VoiceControllerState.TRANSCRIBING -> ProcessingState(
            title = stringResource(R.string.voice_turning_speech_to_text),
                    detail = sttLabel,
                    transcript = liveTranscript,
                )
                VoiceControllerState.LOADING_MODEL -> ProcessingState(
            title = stringResource(R.string.voice_preparing_model),
                    detail = loadingDetail,
                    transcript = "",
                )
                VoiceControllerState.THINKING -> ProcessingState(
            title = stringResource(R.string.voice_thinking),
                    detail = thinkingDetail,
                    transcript = "",
                    action = {
                        TextButton(onClick = onInterrupt) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.xs))
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
    val title = if (microphoneMuted) stringResource(R.string.voice_microphone_muted) else stringResource(R.string.voice_ready)
    val detail = when {
        microphoneMuted -> stringResource(R.string.voice_unmute_to_begin)
        pushToTalkEnabled -> stringResource(R.string.voice_start_hold_mic)
        else -> stringResource(R.string.voice_start_speak)
    }
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
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Space.sm)
    )
    Text(
        detail,
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
        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_start_session), modifier = Modifier.size(24.dp))
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
            if (microphoneMuted) stringResource(R.string.voice_muted_caps)
            else stringResource(R.string.voice_listening_elapsed, formatVoiceElapsed(elapsedMs)),
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
                        stringResource(R.string.voice_waiting_speech),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedIconButton(onClick = onCancelUtterance, modifier = Modifier.padding(start = Space.sm).size(48.dp).clip(CircleShape)) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.voice_discard_recording), modifier = Modifier.size(18.dp))
            }
            FilledIconButton(onClick = onFinishUtterance, modifier = Modifier.padding(start = Space.xs).size(48.dp).clip(CircleShape)) {
        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.voice_use_speech), modifier = Modifier.size(21.dp))
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
    val holdToTalkDescription = stringResource(R.string.voice_hold_cancel)

    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            when {
                muted -> stringResource(R.string.voice_muted_caps)
                pastCancelThreshold -> stringResource(R.string.voice_release_discard_caps)
                held -> stringResource(R.string.voice_listening_caps)
                else -> stringResource(R.string.voice_push_to_talk_caps)
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
        .semantics { contentDescription = holdToTalkDescription },
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
            muted -> stringResource(R.string.voice_microphone_muted_short)
            pastCancelThreshold -> stringResource(R.string.voice_release_discard)
            held -> stringResource(R.string.voice_listening_discard)
            else -> stringResource(R.string.voice_hold_to_talk)
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
                    stringResource(R.string.voice_slide_discard),
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
    microphoneMuted: Boolean,
    liveTranscript: String,
    onCancelUtterance: () -> Unit,
    onFinishUtterance: () -> Unit
) {
    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = Space.xs),
            contentAlignment = Alignment.Center
        ) {
            NormalVoiceVisualizer(
                waveform = waveform,
                muted = microphoneMuted,
                modifier = Modifier.fillMaxWidth().height(58.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
    Text(
                if (liveTranscript.isBlank()) stringResource(R.string.voice_silence_sends) else liveTranscript,
                style = MaterialTheme.typography.bodySmall,
                color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            OutlinedIconButton(
                onClick = onCancelUtterance,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            ) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.voice_discard_recording), modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = onFinishUtterance,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            ) {
        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.voice_use_speech), modifier = Modifier.size(21.dp))
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
    val holdToTalkDescription = stringResource(R.string.voice_hold_cancel)

    Column(Modifier.fillMaxWidth().height(VoiceListeningContentHeight)) {
        Text(
            when {
                muted -> stringResource(R.string.voice_muted_caps)
                pastCancelThreshold -> stringResource(R.string.voice_release_discard_caps)
                held -> stringResource(R.string.voice_listening_caps)
                else -> stringResource(R.string.voice_push_to_talk_caps)
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
            if (held) {
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
        .semantics { contentDescription = holdToTalkDescription },
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
            if (liveTranscript.isBlank()) stringResource(R.string.voice_hold_speak) else liveTranscript,
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
        if (playbackPaused) stringResource(R.string.voice_response_paused)
        else stringResource(R.string.voice_assistant_speaking),
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
        if (hasEchoCancellation) stringResource(R.string.voice_speak_over_reply)
        else stringResource(R.string.voice_interrupt_prompt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Space.xs)
    )
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xl, Alignment.CenterHorizontally)
    ) {
        CircularVoiceAction(Icons.Filled.Mic, stringResource(R.string.voice_interrupt), onInterrupt, Modifier.weight(1f))
        CircularVoiceAction(
            if (playbackPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            if (playbackPaused) stringResource(R.string.voice_resume)
            else stringResource(R.string.voice_pause),
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
    val reducedMotion = rememberReducedMotion()
    val barCount = 32
    val samples = waveform.takeLast(barCount)
    val ambientTransition = rememberInfiniteTransition(label = "voice-waveform-ambient")
    val ambientPhase by ambientTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "voice-waveform-ambient-phase"
    )
    val accent = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val liveLevelsDescription = stringResource(R.string.voice_live_levels)

    Box(
        modifier = modifier
            .clip(VervanExtraShapes.hero)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.48f))
            .border(1.dp, accent.copy(alpha = if (muted) 0.12f else 0.22f), VervanExtraShapes.hero)
            .semantics { contentDescription = liveLevelsDescription },
        contentAlignment = Alignment.Center
    ) {
        // A quiet centerline makes low-volume speech legible without making silence look broken.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accent.copy(alpha = if (muted) 0.10f else 0.18f))
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.md),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(barCount) { index ->
                val position = index / (barCount - 1f)
                val envelope = (0.72f + 0.28f * sin(position * PI).toFloat()).coerceIn(0.72f, 1f)
                val sample = samples.getOrNull(index - (barCount - samples.size))?.coerceIn(0f, 1f)
                val ambient = 0.12f + 0.10f * ((sin(ambientPhase + index * 0.42f) + 1f) / 2f)
                val targetLevel = if (sample == null) {
                    if (muted) 0.06f else ambient * envelope
                } else {
                    (0.06f + sample * (0.78f + envelope * 0.22f)).coerceIn(0.06f, 1f)
                }
                val animatedLevel by animateFloatAsState(
                    targetValue = targetLevel,
                    animationSpec = if (reducedMotion) tween(1) else spring(dampingRatio = 0.72f, stiffness = 850f),
                    label = "voice-waveform-bar-$index"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height((5 + animatedLevel * 38).dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    accent.copy(alpha = if (muted) 0.24f else 0.52f),
                                    accent.copy(alpha = if (muted) 0.42f else 0.96f),
                                    accent.copy(alpha = if (muted) 0.20f else 0.58f)
                                )
                            ),
                            CircleShape
                        )
                )
            }
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
            Text(stringResource(R.string.voice_live_transcript), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                        message.contains("clear speech", ignoreCase = true) -> stringResource(R.string.voice_error_no_speech)
                        message.contains("unavailable", ignoreCase = true) -> stringResource(R.string.voice_error_unavailable)
                        else -> stringResource(R.string.voice_error_attention)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.voice_retry_session))
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
    // Was VervanExtraShapes.pill (100dp, fully stadium) — nested inside the panel's own
    // 20dp-radius `hero` Card, a full pill read as visually mismatched against the card
    // it sits in rather than as one coherent surface.
    Surface(modifier.fillMaxWidth(), shape = VervanExtraShapes.hero, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.Top
        ) {
            VoicePanelAction(
                icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = stringResource(if (microphoneMuted) R.string.voice_unmute else R.string.voice_mute),
                selected = microphoneMuted,
                showLabel = !compact,
                onClick = onToggleMute,
                modifier = Modifier.weight(1f)
            )
            VoicePanelAction(Icons.Filled.AttachFile, stringResource(R.string.voice_attach), showLabel = !compact, onClick = onAttach, modifier = Modifier.weight(1f))
            VoicePanelAction(Icons.Filled.Keyboard, stringResource(R.string.voice_keyboard), showLabel = !compact, onClick = onKeyboard, modifier = Modifier.weight(1f))
            VoicePanelAction(
                icon = if (speechOutputEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = stringResource(if (speechOutputEnabled) R.string.voice_on else R.string.voice_off),
                selected = !speechOutputEnabled,
                showLabel = !compact,
                onClick = onToggleSpeechOutput,
                modifier = Modifier.weight(1f)
            )
            VoicePanelAction(Icons.Filled.MoreHoriz, stringResource(R.string.voice_options), showLabel = !compact, onClick = onMore, modifier = Modifier.weight(1f))
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
    val actionContainer by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(220),
        label = "voice-control-container"
    )
    val actionContent by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "voice-control-content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = actionContainer,
        contentColor = actionContent
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
            }
            if (showLabel) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs)
                )
            }
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

@Composable
private fun voicePanelStateLabel(state: VoiceControllerState, muted: Boolean, paused: Boolean): String = when {
    muted -> stringResource(R.string.voice_microphone_muted_short)
    state == VoiceControllerState.IDLE -> stringResource(R.string.voice_ready_device)
    state == VoiceControllerState.LOADING_MODEL -> stringResource(R.string.voice_preparing_model)
    state == VoiceControllerState.LISTENING -> stringResource(R.string.voice_listening_status)
    state == VoiceControllerState.TRANSCRIBING -> stringResource(R.string.voice_transcribing_status)
    state == VoiceControllerState.THINKING -> stringResource(R.string.voice_generating_status)
    state == VoiceControllerState.SPEAKING && paused -> stringResource(R.string.voice_response_paused)
    else -> stringResource(R.string.voice_speaking_status)
}

private fun formatVoiceElapsed(ms: Int): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}

private const val CancelDragThresholdDp = 64f
