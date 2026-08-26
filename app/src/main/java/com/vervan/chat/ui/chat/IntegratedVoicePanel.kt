package com.vervan.chat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.voice.VoiceControllerState
import kotlin.math.PI
import kotlin.math.abs
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
    modelRunsOnDevice: Boolean,
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
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 390.dp
            val horizontalPadding = if (compact) Space.md else Space.lg
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(sessionAccent)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = Space.md)
            ) {
                VoicePanelHeaderV2(
                    state = state,
                    modelName = modelName,
                    modelRunsOnDevice = modelRunsOnDevice,
                    sttLabel = sttLabel,
                    microphoneMuted = microphoneMuted,
                    playbackPaused = playbackPaused,
                    onEnd = onEnd
                )

                if (state == VoiceControllerState.IDLE || state == VoiceControllerState.LISTENING) {
                    ListeningModeToggle(
                        pushToTalkEnabled = pushToTalkEnabled,
                        onToggle = onTogglePushToTalkMode,
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
                        // Keyed on the mode too (not just state) so switching hands-free/push-to-talk
                        // while LISTENING crossfades instead of hard-cutting.
                        targetState = VoiceConsoleKey(state, pushToTalkEnabled),
                        transitionSpec = {
                            if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                            else fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        },
                        label = "voice-console-state"
                    ) { key ->
                        val active = key.state
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
private fun VoicePanelHeaderV2(
    state: VoiceControllerState,
    modelName: String?,
    modelRunsOnDevice: Boolean,
    sttLabel: String,
    microphoneMuted: Boolean,
    playbackPaused: Boolean,
    onEnd: () -> Unit
) {
    val endDescription = stringResource(R.string.voice_end_session)
    val stateLabel = voicePanelStateLabel(state, microphoneMuted, playbackPaused)
    val voiceStatusDescription = stringResource(R.string.voice_status, stateLabel)
    val resolvedModel = modelName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.voice_not_selected)
    val resolvedStt = sttLabel.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.voice_not_available)
    val privacyLabel = stringResource(
        if (modelRunsOnDevice) R.string.voice_local_device else R.string.privacy_remote_title
    )
    val privacyDescription = if (modelRunsOnDevice) {
        privacyLabel
    } else {
        stringResource(R.string.privacy_remote_icon_description)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.voice_chat),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Surface(
                    modifier = Modifier.padding(start = Space.sm).semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = voiceStatusDescription
                    },
                    shape = MaterialTheme.shapes.small,
                    color = voiceStateColor(state).copy(alpha = 0.13f),
                    contentColor = voiceStateColor(state)
                ) {
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = 2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .semantics { contentDescription = privacyDescription },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (modelRunsOnDevice) Icons.Filled.Lock else Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = if (modelRunsOnDevice) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    privacyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.xs)
                )
            }
            Text(
                "$resolvedModel · $resolvedStt",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = VervanMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Surface(
            onClick = onEnd,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = endDescription
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(22.dp))
            }
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
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(3.dp)) {
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
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = tween(220),
        label = "voice-mode-segment"
    )
    val segmentContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "voice-mode-segment-content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).semantics {
            contentDescription = modeDescription
            this.selected = selected
            role = Role.RadioButton
        },
        shape = MaterialTheme.shapes.small,
        color = segmentColor,
        contentColor = segmentContentColor
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

/** Fixed height of the orb visualization region — identical across every state and both
 * listening modes, so nothing (hands-free vs push-to-talk, idle vs speaking) resizes the card
 * by switching what's drawn there. */
private val VoiceOrbStageHeight = 168.dp

/** Height of the optional status line at the top of a footer (HandsFreeFooter's transcript
 * preview; PushToTalkFooter reserves the same space with an empty spacer) and the height of the
 * control row/pill beneath it. Both footers are built from exactly these two pieces so switching
 * hands-free/push-to-talk mode can never change the card's height. */
private val VoiceFooterTopLineHeight = 40.dp
private val VoiceFooterControlHeight = 56.dp
private val VoiceStageFooterMinHeight = VoiceFooterTopLineHeight + Space.sm + VoiceFooterControlHeight

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
    val accent = voiceStateColor(state)

    // Hoisted here (not inside the push-to-talk orb) so both the orb region and the footer text
    // below it can read the same live drag state.
    var dragUpPx by remember { mutableFloatStateOf(0f) }
    var pastCancelThreshold by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.medium,
        color = stageColor,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) Space.md else Space.lg, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // One centered status pill for every state — the elapsed timer while listening, the
            // state word otherwise — instead of a stray top-left timestamp.
            val topLabel = if (state == VoiceControllerState.LISTENING) formatVoiceElapsed(elapsedMs)
                else voicePanelStateLabel(state, microphoneMuted, playbackPaused)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = accent.copy(alpha = 0.14f),
                contentColor = accent
            ) {
                Text(
                    topLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = VervanMono),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = 4.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(VoiceOrbStageHeight), contentAlignment = Alignment.Center) {
                VoiceSignalField(
                    state = state,
                    waveform = waveform,
                    muted = microphoneMuted,
                    paused = playbackPaused,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .padding(horizontal = if (compact) 0.dp else Space.md)
                )
                when (state) {
                    VoiceControllerState.IDLE -> VoiceOrb(
                        state = VoiceControllerState.IDLE,
                        level = 0f,
                        muted = microphoneMuted,
                        size = 82.dp,
                        icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic
                    )
                    VoiceControllerState.LISTENING -> if (pushToTalkEnabled) {
                        PushToTalkOrb(
                            waveform = waveform,
                            held = pushToTalkHeld,
                            muted = microphoneMuted,
                            dragUpPx = dragUpPx,
                            onDragUpChange = { dragUpPx = it },
                            pastCancelThreshold = pastCancelThreshold,
                            onPastCancelChange = { pastCancelThreshold = it },
                            onPress = onPushToTalkPress,
                            onRelease = onPushToTalkRelease,
                            onCancelUtterance = onCancelUtterance
                        )
                    } else {
                        VoiceOrb(
                            state = VoiceControllerState.LISTENING,
                            level = waveformLevel(waveform),
                            muted = microphoneMuted,
                            size = 82.dp,
                            icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic
                        )
                    }
                    VoiceControllerState.TRANSCRIBING, VoiceControllerState.LOADING_MODEL, VoiceControllerState.THINKING ->
                        VoiceOrb(state = state, level = 0f, muted = false, size = 80.dp, icon = null)
                    VoiceControllerState.SPEAKING -> Box(contentAlignment = Alignment.Center) {
                        VoiceOrb(
                            state = VoiceControllerState.SPEAKING,
                            level = if (playbackPaused) 0f else 1f,
                            muted = false,
                            size = 80.dp,
                            icon = null
                        )
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (playbackPaused) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(min = VoiceStageFooterMinHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    VoiceControllerState.IDLE -> IdleFooter(microphoneMuted, pushToTalkEnabled, onStart)
                    VoiceControllerState.LISTENING -> if (pushToTalkEnabled) {
                        PushToTalkFooter(
                            held = pushToTalkHeld,
                            muted = microphoneMuted,
                            pastCancelThreshold = pastCancelThreshold,
                            liveTranscript = liveTranscript
                        )
                    } else {
                        HandsFreeFooter(liveTranscript, onCancelUtterance, onFinishUtterance)
                    }
                    VoiceControllerState.TRANSCRIBING -> ProcessingFooter(
                        title = stringResource(R.string.voice_turning_speech_to_text),
                        detail = sttLabel,
                        transcript = liveTranscript
                    )
                    VoiceControllerState.LOADING_MODEL -> ProcessingFooter(
                        title = stringResource(R.string.voice_preparing_model),
                        detail = loadingDetail,
                        transcript = ""
                    )
                    VoiceControllerState.THINKING -> ProcessingFooter(
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
                    VoiceControllerState.SPEAKING -> SpeakingFooter(
                        playbackPaused = playbackPaused,
                        hasEchoCancellation = hasEchoCancellation,
                        onInterrupt = onInterrupt,
                        onTogglePlayback = onTogglePlayback
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleFooter(microphoneMuted: Boolean, pushToTalkEnabled: Boolean, onStart: () -> Unit) {
    val detail = when {
        microphoneMuted -> stringResource(R.string.voice_unmute_to_begin)
        pushToTalkEnabled -> stringResource(R.string.voice_start_hold_mic)
        else -> stringResource(R.string.voice_start_speak)
    }
    Text(
        detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = Space.sm)
    )
    Surface(
        onClick = onStart,
        enabled = !microphoneMuted,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.small,
        color = if (microphoneMuted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
        contentColor = if (microphoneMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        border = if (microphoneMuted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_start_session), modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.voice_start_session),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Space.sm)
            )
        }
    }
}

@Composable
private fun HandsFreeFooter(
    liveTranscript: String,
    onCancelUtterance: () -> Unit,
    onFinishUtterance: () -> Unit
) {
    Text(
        if (liveTranscript.isBlank()) stringResource(R.string.voice_silence_sends) else liveTranscript,
        style = MaterialTheme.typography.bodySmall,
        color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(VoiceFooterTopLineHeight).padding(bottom = Space.sm)
    )
    Row(
        Modifier.fillMaxWidth().height(VoiceFooterControlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        VoiceTextAction(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.voice_discard_recording),
            onClick = onCancelUtterance,
            modifier = Modifier.weight(1f),
            selected = false
        )
        VoiceTextAction(
            icon = Icons.Filled.Check,
            label = stringResource(R.string.voice_use_speech),
            onClick = onFinishUtterance,
            modifier = Modifier.weight(1f),
            selected = true
        )
    }
}

/** The push-to-talk hold button, drawn over a backdrop [VoiceOrb] with its solid core hidden
 * ([VoiceOrb.coreVisible] = false) — otherwise the orb's opaque core and the button end up as
 * two barely-distinguishable same-colored discs stacked on each other. Drag state is hoisted to
 * the caller so [PushToTalkFooter] can reflect it too. */
@Composable
private fun PushToTalkOrb(
    waveform: List<Float>,
    held: Boolean,
    muted: Boolean,
    dragUpPx: Float,
    onDragUpChange: (Float) -> Unit,
    pastCancelThreshold: Boolean,
    onPastCancelChange: (Boolean) -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancelUtterance: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cancelThresholdPx = with(density) { CancelDragThresholdDp.dp.toPx() }
    // The drag can travel arbitrarily far past the cancel threshold, but the on-screen nudge that
    // hints at it must stay small and fixed regardless — otherwise it can slide the button clear
    // out of the card and get clipped by the card's own rounded corners.
    val visualCapPx = with(density) { PushToTalkVisualTravelDp.dp.toPx() }
    val visualOffset = (dragUpPx / cancelThresholdPx).coerceIn(0f, 1f) * visualCapPx
    val scale by animateFloatAsState(
        if (held) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "push-to-talk-scale"
    )
    val holdToTalkDescription = stringResource(R.string.voice_hold_cancel)
    val accessibleAction = stringResource(
        if (held) R.string.voice_release_send else R.string.voice_hold_to_talk
    )

    VoiceOrb(
        state = if (held) VoiceControllerState.LISTENING else VoiceControllerState.IDLE,
        level = if (held) waveformLevel(waveform) else 0f,
        muted = muted,
        size = 82.dp,
        icon = null,
        coreVisible = false,
        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, -(visualOffset * 0.4f).toInt()) }
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .offset { androidx.compose.ui.unit.IntOffset(0, -visualOffset.toInt()) }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    when {
                        pastCancelThreshold -> listOf(
                            lerp(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer,
                                0.18f
                            ),
                            MaterialTheme.colorScheme.errorContainer
                        )
                        held -> listOf(
                            lerp(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimary,
                                0.22f
                            ),
                            MaterialTheme.colorScheme.primary
                        )
                        else -> listOf(
                            lerp(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                0.14f
                            ),
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                ),
                CircleShape
            )
            .pointerInput(muted) {
                if (muted) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var cancelGesture = false
                    onPress()
                    onDragUpChange(0f)
                    onPastCancelChange(false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val drag = (down.position.y - change.position.y).coerceAtLeast(0f)
                        cancelGesture = drag > cancelThresholdPx
                        onDragUpChange(drag)
                        onPastCancelChange(cancelGesture)
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    onDragUpChange(0f)
                    onPastCancelChange(false)
                    if (cancelGesture) onCancelUtterance() else onRelease()
                }
            }
            .semantics {
                contentDescription = holdToTalkDescription
                role = Role.Button
                stateDescription = accessibleAction
                if (!muted) {
                    onClick(label = accessibleAction) {
                        if (held) onRelease() else onPress()
                        true
                    }
                }
            },
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
            modifier = Modifier.size(if (held) 34.dp else 28.dp)
        )
    }
}

@Composable
private fun PushToTalkFooter(
    held: Boolean,
    muted: Boolean,
    pastCancelThreshold: Boolean,
    liveTranscript: String
) {
    val instruction = when {
        muted -> stringResource(R.string.voice_microphone_muted_short)
        pastCancelThreshold -> stringResource(R.string.voice_release_discard)
        held -> stringResource(R.string.voice_release_send)
        liveTranscript.isBlank() -> stringResource(R.string.voice_hold_to_talk)
        else -> liveTranscript
    }
    val instructionColor = when {
        pastCancelThreshold -> MaterialTheme.colorScheme.onErrorContainer
        held -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // No separate status line above the pill here (the instruction text already carries that
    // role) — an empty spacer of the same height keeps this footer exactly as tall as
    // HandsFreeFooter's, instead of the mode toggle nudging the card's height around.
    Spacer(Modifier.height(VoiceFooterTopLineHeight + Space.sm))
    Surface(
        modifier = Modifier.fillMaxWidth().height(VoiceFooterControlHeight),
        shape = MaterialTheme.shapes.small,
        color = when {
            pastCancelThreshold -> MaterialTheme.colorScheme.errorContainer
            held -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = instructionColor
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    pastCancelThreshold -> Icons.Filled.DeleteOutline
                    held -> Icons.Filled.Check
                    else -> Icons.Filled.Mic
                },
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Text(
                instruction,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Space.sm)
            )
        }
    }
}

@Composable
private fun ProcessingFooter(
    title: String,
    detail: String,
    transcript: String,
    action: (@Composable () -> Unit)? = null
) {
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
private fun SpeakingFooter(
    playbackPaused: Boolean,
    hasEchoCancellation: Boolean,
    onInterrupt: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Text(
        if (hasEchoCancellation) stringResource(R.string.voice_speak_over_reply)
        else stringResource(R.string.voice_interrupt_prompt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = Space.sm)
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xl, Alignment.CenterHorizontally)
    ) {
        VoiceTextAction(Icons.Filled.Mic, stringResource(R.string.voice_interrupt), onInterrupt, Modifier.weight(1f), selected = false)
        VoiceTextAction(
            if (playbackPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            if (playbackPaused) stringResource(R.string.voice_resume)
            else stringResource(R.string.voice_pause),
            onTogglePlayback,
            Modifier.weight(1f),
            selected = true
        )
    }
}

@Composable
private fun VoiceTextAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
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

/**
 * A real signal field rather than a decorative equalizer. During capture it renders the newest
 * microphone samples; during model and playback states it moves with a restrained synthetic rhythm
 * so the state remains legible without pretending that microphone input is being recorded.
 */
@Composable
private fun VoiceSignalField(
    state: VoiceControllerState,
    waveform: List<Float>,
    muted: Boolean,
    paused: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else voiceStateColor(state)
    val samples = remember(waveform) { waveform.takeLast(48).map { it.coerceIn(0f, 1f) } }
    val transition = rememberInfiniteTransition(label = "voice-signal-field")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice-signal-phase"
    )
    val signalDescription = stringResource(R.string.voice_live_levels)
    val signalModifier = if (state == VoiceControllerState.LISTENING) {
        modifier.semantics { contentDescription = signalDescription }
    } else {
        modifier
    }

    Canvas(signalModifier) {
        val barCount = 30
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount)
            .coerceAtLeast(2.dp.toPx())
        val minHeight = 5.dp.toPx()
        val maxHeight = size.height * 0.72f
        val center = (barCount - 1) / 2f

        repeat(barCount) { index ->
            val sample = if (samples.isEmpty()) {
                0f
            } else {
                val sourceIndex = ((index.toFloat() / (barCount - 1)) * samples.lastIndex)
                    .toInt()
                    .coerceIn(0, samples.lastIndex)
                samples[sourceIndex]
            }
            val synthetic = (sin(phase + index * 0.58f) * 0.5f + 0.5f)
            val activity = when (state) {
                VoiceControllerState.IDLE -> 0.04f
                VoiceControllerState.LISTENING -> if (muted) 0.03f else sample
                VoiceControllerState.SPEAKING -> if (paused) 0.04f else 0.16f + synthetic * 0.42f
                VoiceControllerState.TRANSCRIBING,
                VoiceControllerState.LOADING_MODEL,
                VoiceControllerState.THINKING -> 0.10f + synthetic * 0.24f
            }.coerceIn(0f, 1f)
            val centerFocus = 1f - (abs(index - center) / center)
            val barHeight = (minHeight + maxHeight * activity * (0.68f + centerFocus * 0.32f))
                .coerceAtMost(size.height)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = accent.copy(
                    alpha = when (state) {
                        VoiceControllerState.IDLE -> 0.18f
                        VoiceControllerState.LISTENING -> 0.50f
                        else -> 0.34f
                    }
                ),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/** Average recent mic energy into a single 0..1 level for [VoiceOrb] to react to. */
private fun waveformLevel(waveform: List<Float>): Float {
    val recent = waveform.takeLast(8)
    if (recent.isEmpty()) return 0f
    return recent.map { it.coerceIn(0f, 1f) }.average().toFloat()
}

/**
 * The single reactive visual anchor for every voice state: a soft radial-gradient orb with an
 * ambient glow, breathing or pulsing per [state]. [level] (0..1) drives real-time reactivity
 * during LISTENING (mic energy); other states animate on their own internal clock so the orb
 * always reads as "alive" without needing real audio data.
 */
@Composable
private fun VoiceOrb(
    state: VoiceControllerState,
    level: Float,
    muted: Boolean,
    size: Dp,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    // Set false when the orb is only a backdrop glow behind another pressable control (push-to-talk):
    // drawing the near-opaque core there reads as a second flat button sitting behind the real one.
    coreVisible: Boolean = true
) {
    val reducedMotion = rememberReducedMotion()
    val accent = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else voiceStateColor(state)
    val accentContent = if (muted) MaterialTheme.colorScheme.surface else voiceStateContentColor(state)
    val period = when (state) {
        VoiceControllerState.IDLE -> 3200
        VoiceControllerState.THINKING -> 1500
        VoiceControllerState.TRANSCRIBING, VoiceControllerState.LOADING_MODEL -> 1800
        VoiceControllerState.LISTENING -> 1200
        VoiceControllerState.SPEAKING -> 900
    }
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "voice-orb-phase"
    )
    val ringProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period * 2, easing = LinearEasing), RepeatMode.Restart),
        label = "voice-orb-ring"
    )
    val synthEnergy = (sin(phase) * 0.5f + 0.5f) * 0.6f + (sin(phase * 2.3f + 1f) * 0.5f + 0.5f) * 0.4f
    val rawEnergy = when (state) {
        VoiceControllerState.IDLE -> 0.12f + synthEnergy * 0.10f
        VoiceControllerState.LISTENING -> if (muted) 0.08f else level.coerceIn(0f, 1f)
        VoiceControllerState.SPEAKING -> if (level <= 0f) 0.1f else 0.35f + synthEnergy * 0.5f
        else -> 0.3f + synthEnergy * 0.35f
    }
    val energy by animateFloatAsState(
        targetValue = rawEnergy,
        animationSpec = if (reducedMotion) tween(1) else spring(dampingRatio = 0.7f, stiffness = 220f),
        label = "voice-orb-energy"
    )
    val rotation = if (state == VoiceControllerState.THINKING && !reducedMotion) phase * (180f / PI.toFloat()) else 0f
    val coreScale = 0.82f + energy * 0.34f
    val glowScale = 1f + energy * 0.55f
    val glowAlpha = 0.22f + energy * 0.30f
    // A slow outward-expanding, fading ring reads as a radar ping — cheap extra "alive" motion
    // that doesn't compete with the core's own breathing. Idle stays still and calm on purpose.
    val showRing = !reducedMotion && !muted && state != VoiceControllerState.IDLE

    Box(modifier.size(size * 1.9f), contentAlignment = Alignment.Center) {
        if (showRing) {
            Box(
                Modifier
                    .size(size * 1.1f)
                    .scale(1f + ringProgress * 0.9f)
                    .alpha((1f - ringProgress) * 0.45f)
                    .border(1.dp, accent, CircleShape)
            )
        }
        Box(
            Modifier
                .size(size * 1.7f)
                .scale(glowScale)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = glowAlpha), Color.Transparent)),
                    CircleShape
                )
        )
        if (coreVisible) {
            Box(
                Modifier
                    .size(size)
                    .scale(coreScale)
                    .rotate(rotation)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                lerp(accent, accentContent, 0.22f),
                                accent,
                                lerp(accent, MaterialTheme.colorScheme.onSurface, 0.10f)
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .size(size * 0.5f)
                    .offset(x = -size * 0.14f, y = -size * 0.16f)
                    .alpha(0.22f)
                    .background(
                        Brush.radialGradient(listOf(accentContent, Color.Transparent)),
                        CircleShape
                    )
            )
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentContent,
                modifier = Modifier.size(size * 0.46f)
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
        shape = MaterialTheme.shapes.medium,
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
            Surface(
                onClick = onRetry,
                modifier = Modifier
                    .padding(start = Space.md)
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Space.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceControlDock(
    microphoneMuted: Boolean,
    speechOutputEnabled: Boolean,
    onToggleMute: () -> Unit,
    onAttach: () -> Unit,
    onKeyboard: () -> Unit,
    onToggleSpeechOutput: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        BoxWithConstraints {
            val showLabels = maxWidth >= 440.dp
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoicePanelAction(
                    Icons.Filled.AttachFile,
                    stringResource(R.string.voice_attach),
                    showLabel = showLabels,
                    onClick = onAttach,
                    modifier = Modifier.weight(1f)
                )
                VoicePanelAction(
                    icon = if (speechOutputEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    label = stringResource(if (speechOutputEnabled) R.string.voice_on else R.string.voice_off),
                    selected = !speechOutputEnabled,
                    showLabel = showLabels,
                    onClick = onToggleSpeechOutput,
                    modifier = Modifier.weight(1f)
                )
                VoicePanelAction(
                    icon = if (microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = stringResource(if (microphoneMuted) R.string.voice_unmute else R.string.voice_mute),
                    selected = microphoneMuted,
                    prominent = true,
                    showLabel = showLabels,
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1.16f)
                )
                VoicePanelAction(
                    Icons.Filled.Keyboard,
                    stringResource(R.string.voice_type),
                    showLabel = showLabels,
                    onClick = onKeyboard,
                    modifier = Modifier.weight(1f)
                )
                VoicePanelAction(
                    Icons.Filled.MoreHoriz,
                    stringResource(R.string.voice_options),
                    showLabel = showLabels,
                    onClick = onMore,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VoicePanelAction(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    prominent: Boolean = false
) {
    val actionContainer by animateColorAsState(
        targetValue = when {
            prominent && selected -> MaterialTheme.colorScheme.errorContainer
            prominent -> MaterialTheme.colorScheme.primaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "voice-control-container"
    )
    val actionContent by animateColorAsState(
        targetValue = when {
            prominent && selected -> MaterialTheme.colorScheme.onErrorContainer
            prominent -> MaterialTheme.colorScheme.onPrimaryContainer
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "voice-control-content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .heightIn(min = if (prominent) 60.dp else 56.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        shape = MaterialTheme.shapes.small,
        color = actionContainer,
        contentColor = actionContent
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(if (prominent) 44.dp else 40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (prominent) 22.dp else 19.dp))
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
}

@Composable
private fun voiceStateColor(state: VoiceControllerState): Color = when (state) {
    VoiceControllerState.IDLE -> MaterialTheme.colorScheme.outline
    VoiceControllerState.LISTENING -> MaterialTheme.colorScheme.primary
    VoiceControllerState.TRANSCRIBING, VoiceControllerState.LOADING_MODEL, VoiceControllerState.THINKING -> MaterialTheme.colorScheme.tertiary
    VoiceControllerState.SPEAKING -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun voiceStateContentColor(state: VoiceControllerState): Color = when (state) {
    VoiceControllerState.IDLE -> MaterialTheme.colorScheme.surface
    VoiceControllerState.LISTENING -> MaterialTheme.colorScheme.onPrimary
    VoiceControllerState.TRANSCRIBING,
    VoiceControllerState.LOADING_MODEL,
    VoiceControllerState.THINKING -> MaterialTheme.colorScheme.onTertiary
    VoiceControllerState.SPEAKING -> MaterialTheme.colorScheme.onSecondary
}

@Composable
private fun voicePanelStateLabel(state: VoiceControllerState, muted: Boolean, paused: Boolean): String = when {
    muted -> stringResource(R.string.voice_microphone_muted_short)
    state == VoiceControllerState.IDLE -> stringResource(R.string.voice_not_listening)
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
private const val PushToTalkVisualTravelDp = 20f

/** AnimatedContent key for the voice console: crossfades on either a state change or a
 * hands-free/push-to-talk mode switch, instead of only reacting to [VoiceControllerState]. */
private data class VoiceConsoleKey(val state: VoiceControllerState, val pushToTalk: Boolean)
