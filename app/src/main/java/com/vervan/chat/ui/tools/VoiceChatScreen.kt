package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.voice.RealtimeVoiceController
import com.vervan.chat.voice.ReplayAudioPlayer
import com.vervan.chat.voice.VoiceControllerState
import com.vervan.chat.voice.VoiceTurn
import kotlinx.coroutines.delay
import java.util.Locale

/** In-place, on-device voice conversation. The transcript stays visible in every state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(onBack: () -> Unit, onOpenKeyboard: () -> Unit = onBack, onOpenModelManager: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val controller = remember { RealtimeVoiceController(app) }
    val player = remember { ReplayAudioPlayer() }

    val state by controller.state.collectAsState()
    val turns by controller.turns.collectAsState()
    val sttLabel by controller.sttLabel.collectAsState()
    val ttsLabel by controller.ttsLabel.collectAsState()
    val hasEchoCancellation by controller.hasEchoCancellation.collectAsState()
    val liveWaveform by controller.liveWaveform.collectAsState()
    val liveElapsedMs by controller.liveElapsedMs.collectAsState()
    val liveTranscript by controller.liveTranscript.collectAsState()
    val loadingModelName by controller.loadingModelName.collectAsState()
    val modelLoadError by controller.modelLoadError.collectAsState()
    val sttUnavailable by controller.sttUnavailable.collectAsState()
    val playbackPaused by controller.playbackPaused.collectAsState()

    var playingTurnId by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var showEngineMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            controller.stop()
            player.release()
        }
    }

    LaunchedEffect(playingTurnId) {
        while (playingTurnId != null) {
            if (player.isPlaying) {
                playbackProgress = player.progressFraction()
                if (playbackProgress >= 0.999f) {
                    playingTurnId = null
                    break
                }
            }
            delay(80)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length, state) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start(scope)
    }

    fun togglePlayback(turn: VoiceTurn) {
        val samples = turn.audioSamples ?: return
        if (playingTurnId == turn.id) {
            if (player.isPlaying) player.pause() else player.play()
        } else {
            player.load(samples, turn.sampleRateHz)
            playbackProgress = 0f
            playingTurnId = turn.id
            player.play()
        }
    }

    fun seekPlayback(turn: VoiceTurn, fraction: Float) {
        val samples = turn.audioSamples ?: return
        if (playingTurnId != turn.id) {
            player.load(samples, turn.sampleRateHz)
            playingTurnId = turn.id
        }
        player.seekTo(fraction)
        playbackProgress = fraction
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(21.dp))
                            }
                        }
                        Column(Modifier.padding(start = Space.sm)) {
                            Text("Vervan Voice", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On-device · English + Hindi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showEngineMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Voice chat details")
                        }
                        DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                            Column(Modifier.widthIn(min = 240.dp).padding(horizontal = Space.lg, vertical = Space.md)) {
                                Text("On-device voice", style = MaterialTheme.typography.titleSmall)
                                EngineDetail("Speech input", sttLabel)
                                EngineDetail("Speech output", ttsLabel)
                                EngineDetail("Barge-in", if (hasEchoCancellation) "Available" else "Tap the mic to interrupt")
                                if (state != VoiceControllerState.IDLE) {
                                    TextButton(
                                        onClick = { controller.stop(); showEngineMenu = false },
                                        modifier = Modifier.align(Alignment.End).padding(top = Space.sm)
                                    ) { Text("End session") }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            modelLoadError?.let { error ->
                ErrorCard(
                    title = "Couldn't load the model",
                    body = error,
                    actionLabel = "Retry",
                    onAction = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            if (sttUnavailable && modelLoadError == null) {
                ErrorCard(
                    title = "No speech input available",
                    body = "This device has no built-in speech recognizer, and the active model can't hear audio. Download the offline voice model to talk to the assistant.",
                    actionLabel = "Get voice model",
                    onAction = onOpenModelManager,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    if (turns.isEmpty()) {
                        item(key = "voice-empty") { VoiceEmptyState() }
                    }
                    items(turns, key = { it.id }) { turn ->
                        if (turn.fromUser) {
                            UserTurnBubble(turn)
                        } else {
                            AssistantTurnBubble(
                                turn = turn,
                                isActive = playingTurnId == turn.id,
                                isPlaying = playingTurnId == turn.id && player.isPlaying,
                                progress = if (playingTurnId == turn.id) playbackProgress else 0f,
                                onTogglePlay = { togglePlayback(turn) },
                                onSeek = { seekPlayback(turn, it) }
                            )
                        }
                    }
                    if (state == VoiceControllerState.THINKING && turns.lastOrNull()?.isStreaming != true) {
                        item(key = "thinking-thread") { ThinkingThreadIndicator() }
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VoiceControlCluster(
                    state = state,
                    liveWaveform = liveWaveform,
                    liveElapsedMs = liveElapsedMs,
                    liveTranscript = liveTranscript,
                    playbackPaused = playbackPaused,
                    loadingModelName = loadingModelName,
                    onStart = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    onCancel = controller::stop,
                    onFinishListening = controller::finishListening,
                    onBargeIn = controller::manualInterrupt,
                    onPause = controller::togglePlaybackPause,
                    onStop = controller::stop,
                    onKeyboard = {
                        controller.stop()
                        onOpenKeyboard()
                    }
                )
            }
        }
    }
}

@Composable
private fun EngineDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Space.lg))
    }
}

@Composable
private fun VoiceEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Text("A private voice conversation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.md))
            Text(
            "Speak naturally. Vervan listens and replies on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm)
            )
            Text(
                "On-device · Conversation history stays visible",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Space.md)
            )
        }
    }
}

@Composable
private fun UserTurnBubble(turn: VoiceTurn) {
    val bars = turn.waveform.ifEmpty { placeholderWaveform() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(
                Modifier.heightIn(min = 44.dp).padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                WaveformBars(
                    bars = bars,
                    modifier = Modifier.padding(horizontal = Space.sm).width(132.dp).height(24.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(formatDuration(turn.durationMs), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            turn.text,
            modifier = Modifier.padding(top = Space.sm, end = Space.xs).widthIn(max = 320.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
        Text(
            if (turn.transcribedOnDevice) "Transcribed on-device" else "Understood directly from audio",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs, end = Space.xs)
        )
    }
}

@Composable
private fun AssistantTurnBubble(
    turn: VoiceTurn,
    isActive: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "streaming-text").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "streaming-dot"
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Card(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Bottom) {
                // The model streams markdown (same as the regular chat screen) — render it
                // properly here too instead of dumping raw "**"/"#" syntax as plain text.
                MarkdownLiteText(turn.text.ifBlank { " " }, modifier = Modifier.weight(1f, fill = false))
                if (turn.isStreaming) {
                    Text(
                        " •",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primary.copy(alpha = pulse)
                    )
                }
            }
        }
        if (turn.audioSamples != null || turn.audioPending) {
            AssistantPlaybackBar(
                turn = turn,
                isActive = isActive,
                isPlaying = isPlaying,
                progress = progress,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek
            )
        }
    }
}

@Composable
private fun AssistantPlaybackBar(
    turn: VoiceTurn,
    isActive: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val canReplay = turn.audioSamples != null && !turn.audioPending
    Surface(
        modifier = Modifier.padding(top = Space.xs).widthIn(max = 420.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onTogglePlay,
                enabled = canReplay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause response" else "Play response"
                )
            }
            WaveformBars(
                bars = turn.waveform.ifEmpty { placeholderWaveform() },
                modifier = Modifier.weight(1f).height(30.dp).padding(horizontal = Space.sm),
                color = MaterialTheme.colorScheme.primary,
                progress = if (isActive) progress else 0f,
                onSeek = if (canReplay) onSeek else null
            )
            Text(
                if (turn.audioPending) "${formatDuration(turn.durationMs)}+"
                else "${formatDuration(if (isActive) (progress * turn.durationMs).toInt() else 0)} / ${formatDuration(turn.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

@Composable
private fun ThinkingThreadIndicator() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        ThinkingEllipsis(Modifier.padding(start = Space.sm))
    }
}

@Composable
private fun VoiceControlCluster(
    state: VoiceControllerState,
    liveWaveform: List<Float>,
    liveElapsedMs: Int,
    liveTranscript: String,
    playbackPaused: Boolean,
    loadingModelName: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onFinishListening: () -> Unit,
    onBargeIn: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onKeyboard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        AnimatedContent(
            targetState = state,
            // The new state eases in, while the old live waveform stops immediately so the
            // UI never appears to keep listening after capture has ended.
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(0)) },
            label = "voice-control-state"
        ) { activeState ->
            when (activeState) {
                VoiceControllerState.IDLE -> IdleControls(onStart, onKeyboard)
                VoiceControllerState.LISTENING -> ListeningControls(
                    liveWaveform, liveElapsedMs, liveTranscript, onCancel, onFinishListening, onKeyboard
                )
                VoiceControllerState.SPEAKING -> SpeakingControls(playbackPaused, onBargeIn, onPause, onStop)
                VoiceControllerState.THINKING, VoiceControllerState.LOADING_MODEL, VoiceControllerState.TRANSCRIBING -> ProcessingControls(
                    label = voiceStatusLabel(activeState, playbackPaused, loadingModelName),
                    liveTranscript = liveTranscript.takeIf { activeState == VoiceControllerState.TRANSCRIBING }.orEmpty(),
                    onCancel = onCancel,
                    onKeyboard = onKeyboard
                )
            }
        }
    }
}

@Composable
private fun IdleControls(onStart: () -> Unit, onKeyboard: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tap to speak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedIconButton(onClick = onStart, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = "Start voice conversation", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onKeyboard, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Keyboard, contentDescription = "Switch to keyboard")
            }
        }
    }
}

@Composable
private fun ListeningControls(
    waveform: List<Float>,
    elapsedMs: Int,
    liveTranscript: String,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onKeyboard: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WaveformBars(
            bars = waveform.ifEmpty { placeholderWaveform() },
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp).height(44.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Listening… · ${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm)
        )
        LiveTranscriptCaption(liveTranscript)
        Row(
            Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedIconButton(onClick = onCancel, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel recording")
            }
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(76.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Finish recording", modifier = Modifier.size(34.dp))
            }
            OutlinedIconButton(onClick = onKeyboard, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Keyboard, contentDescription = "Switch to keyboard")
            }
        }
    }
}

@Composable
private fun SpeakingControls(paused: Boolean, onBargeIn: () -> Unit, onPause: () -> Unit, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpeakingPulse(paused)
        Text(
            if (paused) "Paused" else "Speaking…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedIconButton(onClick = onBargeIn, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = "Interrupt and speak")
            }
            FilledTonalIconButton(onClick = onPause, modifier = Modifier.size(68.dp)) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume response" else "Pause response",
                    modifier = Modifier.size(30.dp)
                )
            }
            OutlinedIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "End voice session")
            }
        }
    }
}

@Composable
private fun ProcessingControls(label: String, liveTranscript: String, onCancel: () -> Unit, onKeyboard: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        ThinkingEllipsis(Modifier.padding(top = Space.sm))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LiveTranscriptCaption(liveTranscript)
        Row(
            Modifier.fillMaxWidth().padding(top = Space.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel voice session")
            }
            IconButton(onClick = onKeyboard, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Keyboard, contentDescription = "Switch to keyboard")
            }
        }
    }
}

@Composable
private fun LiveTranscriptCaption(text: String) {
    if (text.isBlank()) return
    Surface(
        modifier = Modifier
            .padding(top = Space.sm)
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LIVE TRANSCRIPT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                modifier = Modifier.padding(top = Space.xs).fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpeakingPulse(paused: Boolean) {
    val progress by rememberInfiniteTransition(label = "speaking-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
        label = "speaking-ring"
    )
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        if (!paused) {
            listOf(progress, (progress + 0.5f) % 1f).forEach { phase ->
                Box(
                    Modifier.size(72.dp).graphicsLayer {
                        scaleX = 0.65f + phase * 0.45f
                        scaleY = scaleX
                        alpha = 1f - phase
                    }.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape)
                )
            }
        }
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ThinkingEllipsis(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "thinking-dots"
    )
    Row(modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val distance = kotlin.math.abs(phase - index).coerceAtMost(1f)
            Box(
                Modifier.size(6.dp).graphicsLayer { alpha = 1f - distance * 0.65f }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/** Amplitude bars for live capture, recorded turns, and tap-to-seek playback. */
@Composable
private fun WaveformBars(
    bars: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    progress: Float = 0f,
    onSeek: ((Float) -> Unit)? = null
) {
    val trackColor = color.copy(alpha = 0.30f)
    Row(
        modifier = modifier
            .semantics {
                contentDescription = if (onSeek == null) "Audio waveform" else "Audio waveform. Tap to seek."
                if (onSeek != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                    stateDescription = "${(progress.coerceIn(0f, 1f) * 100).toInt()} percent"
                }
            }
            .then(
                if (onSeek != null) Modifier.pointerInput(onSeek) {
                    detectTapGestures { offset -> onSeek((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        bars.forEachIndexed { index, amplitude ->
            val played = onSeek != null && bars.isNotEmpty() && index.toFloat() / bars.size <= progress
            Box(
                Modifier.weight(1f).height((6 + amplitude.coerceIn(0f, 1f) * 24).dp)
                    .background(if (played) color else trackColor, RoundedCornerShape(1.dp))
            )
        }
    }
}

private fun placeholderWaveform(): List<Float> =
    listOf(0.22f, 0.45f, 0.72f, 0.38f, 0.88f, 0.54f, 0.30f, 0.68f, 0.42f, 0.80f, 0.35f, 0.58f)

private fun formatDuration(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
}

internal fun voiceStatusLabel(state: VoiceControllerState, paused: Boolean, loadingModelName: String?): String = when (state) {
    VoiceControllerState.IDLE -> "Ready"
    VoiceControllerState.LOADING_MODEL -> "Preparing ${loadingModelName ?: "local model"}…"
    VoiceControllerState.LISTENING -> "Listening…"
    VoiceControllerState.TRANSCRIBING -> "Transcribing…"
    VoiceControllerState.THINKING -> "Thinking…"
    VoiceControllerState.SPEAKING -> if (paused) "Paused" else "Speaking…"
}
