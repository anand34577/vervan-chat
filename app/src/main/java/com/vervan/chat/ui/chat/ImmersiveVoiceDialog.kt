package com.vervan.chat.ui.chat

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.voice.VoiceTurn

/**
 * The voice chat screen: a full-screen, chat-bubble presentation of the same
 * controller-backed session [ChatScreen] already drives — voice is a modality of the ordinary
 * conversation (see [com.vervan.chat.voice.RealtimeVoiceController]'s `respond`/`cancelResponse`
 * callbacks, wired in ChatScreen), not a
 * separate chat system, so this is deliberately styled like the normal message list rather than
 * inventing a different visual language: user turns right-aligned/filled, assistant turns
 * left-aligned/surfaced, both showing a tap-to-replay recorded-audio player
 * ([VoiceTurnAudioPlayer]) the instant real PCM audio exists for that turn.
 */
@Composable
internal fun ImmersiveVoiceDialog(
    conversationTitle: String,
    modelName: String?,
    turns: List<VoiceTurn>,
    liveTranscript: String,
    onExitImmersive: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onExitImmersive,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                VoiceChatHeader(conversationTitle, modelName, onExitImmersive)

                val listState = rememberLazyListState()
                LaunchedEffect(turns.size, turns.lastOrNull()?.text) {
                    if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    if (turns.isEmpty()) {
                        item {
                            VoiceChatEmptyState(liveTranscript)
                        }
                    }
                    items(turns, key = { it.id }) { turn ->
                        VoiceTurnBubble(turn)
                    }
                    if (liveTranscript.isNotBlank() && turns.lastOrNull()?.text != liveTranscript) {
                        item(key = "live-caption") {
                            LiveCaptionBubble(liveTranscript)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                ) {
                    content()
                }
            }
        }
    }
}
@Composable
private fun VoiceChatHeader(conversationTitle: String, modelName: String?, onExitImmersive: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 390.dp
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = Space.md, end = Space.sm)) {
                    Text(
                        conversationTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        modelName ?: "Local model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!compact) LiveVoicePill() else LiveStatusDot()
                IconButton(onClick = onExitImmersive, modifier = Modifier.padding(start = Space.xs)) {
                    Icon(Icons.Filled.CloseFullscreen, contentDescription = stringResource(R.string.ui_immersivevoicedialog_162_exit_full_screen))
                }
            }
        }
    }
}
@Composable
private fun LiveStatusDot() {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.onTertiaryContainer, CircleShape))
        }
    }
}
@Composable
private fun LiveVoicePill() {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "live-pill")
    val pulse = if (reducedMotion) 1f else transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "live-pill-alpha"
    ).value
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(pulse)
                    .background(MaterialTheme.colorScheme.onTertiaryContainer, CircleShape)
            )
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}
@Composable
private fun VoiceChatEmptyState(liveTranscript: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            liveTranscript.ifBlank { "Ask anything when you're ready." },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Space.lg)
        )
    }
}
@Composable
private fun VoiceTurnBubble(turn: VoiceTurn) {
    val isUser = turn.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            SpeakerAvatar(isUser = false, modifier = Modifier.padding(end = Space.sm))
        }
        Surface(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 520.dp),
            shape = if (isUser) VervanExtraShapes.userBubble else VervanExtraShapes.assistantBubble,
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ) {
            Column(Modifier.padding(Space.md)) {
                Text(
                    if (isUser) "YOU" else "ASSISTANT",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary)
                        .let { if (isUser) it.copy(alpha = 0.75f) else it }
                )
                when {
                    turn.audioPending -> VoiceTurnAudioPending(modifier = Modifier.padding(top = Space.xs))
                    turn.audioSamples != null && turn.audioSamples.isNotEmpty() -> VoiceTurnAudioPlayer(
                        samples = turn.audioSamples,
                        sampleRateHz = turn.sampleRateHz,
                        waveform = turn.waveform,
                        durationMs = turn.durationMs,
                        modifier = Modifier.padding(top = Space.xs),
                        accent = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                    else -> {}
                }
                val displayText = turn.text.ifBlank { if (turn.isStreaming) null else "" }
                if (displayText == null) {
                    ThinkingDots(modifier = Modifier.padding(top = Space.sm))
                } else if (displayText.isNotEmpty()) {
                    Text(
                        displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            }
        }
        if (isUser) {
            SpeakerAvatar(isUser = true, modifier = Modifier.padding(start = Space.sm))
        }
    }
}
@Composable
private fun LiveCaptionBubble(liveTranscript: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Surface(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 520.dp).alpha(0.78f),
            shape = VervanExtraShapes.userBubble,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(Modifier.padding(Space.md)) {
                Text(stringResource(R.string.ui_immersivevoicedialog_302_you_live), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(liveTranscript, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs))
            }
        }
        SpeakerAvatar(isUser = true, modifier = Modifier.padding(start = Space.sm))
    }
}
@Composable
private fun SpeakerAvatar(isUser: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isUser) Icons.Filled.Mic else Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ThinkingDots(modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "thinking-dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha = if (reducedMotion) 0.6f else transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = index * 150, easing = LinearEasing),
                    RepeatMode.Reverse
                ),
                label = "dot-$index"
            ).value
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}
