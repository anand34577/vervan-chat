package com.vervan.chat.ui.omni

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import androidx.compose.ui.res.stringResource
import com.vervan.chat.omni.OmniRuntime
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.theme.Space

/**
 * Omni Studio: LFM3 Omni's full feature set in one place — voice chat with spoken replies, questions
 * about a photo or a video, text-to-speech, and transcription. The plain chat screen keeps using the
 * same model through LiteRT-LM for text, photo and audio input.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OmniStudioScreen(onBack: () -> Unit, onOpenModels: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: OmniStudioViewModel = viewModel(factory = viewModelFactory { initializer { OmniStudioViewModel(app) } })

    val status by vm.status.collectAsStateWithLifecycle()
    val runtime by vm.runtimeState.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.omni_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = Space.lg)) {
            StatusCard(status, runtime, phase, onOpenModels, vm)
            ScrollableTabRow(selectedTabIndex = mode.ordinal, edgePadding = 16.dp, modifier = Modifier.padding(top = Space.sm)) {
                OmniStudioViewModel.Mode.entries.forEach { m ->
                    Tab(selected = mode == m, onClick = { vm.setMode(m) }, text = { Text(m.label) })
                }
            }
            error?.let {
                ErrorCard(
                    title = "LFM3 Omni", body = it, modifier = Modifier.padding(top = Space.sm),
                    actionLabel = stringResource(R.string.omni_dismiss), onAction = vm::dismissError
                )
            }
            val ready = status?.installed == true
            when (mode) {
                OmniStudioViewModel.Mode.SPEAK -> SpeakPane(vm, ready, phase)
                OmniStudioViewModel.Mode.TRANSCRIBE -> TranscribePane(vm, ready, phase)
                else -> ConversationPane(vm, mode, ready, phase)
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: OmniRuntime.Status?,
    runtime: OmniRuntime.State,
    phase: OmniStudioViewModel.Phase,
    onOpenModels: () -> Unit,
    vm: OmniStudioViewModel,
) {
    Card(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(Space.md)) {
            val s = status
            when {
                s == null -> Text(stringResource(R.string.omni_checking), style = MaterialTheme.typography.bodyMedium)
                !s.installed -> OmniSetup(s, onInstalled = vm::refreshStatus, onOpenModels = onOpenModels)
                else -> {
                    val line = when (runtime) {
                        is OmniRuntime.State.Ready -> stringResource(R.string.omni_loaded, runtime.placement)
                        is OmniRuntime.State.Loading -> runtime.step
                        is OmniRuntime.State.Failed -> stringResource(R.string.omni_not_loaded, runtime.message)
                        OmniRuntime.State.Closed -> if (s.prepared) stringResource(R.string.omni_ready_to_load) else
                            stringResource(R.string.omni_first_load, s.prepareNeedsBytes / 1e9, s.freeBytes / 1e9)
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    val busy = phase !is OmniStudioViewModel.Phase.Idle || runtime is OmniRuntime.State.Loading
                    Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (runtime is OmniRuntime.State.Ready) {
                            OutlinedButton(onClick = vm::unload, enabled = !busy) { Text(stringResource(R.string.omni_unload)) }
                        } else {
                            Button(onClick = vm::load, enabled = !busy) { Text(stringResource(R.string.omni_load)) }
                        }
                        if (s.preparedBytes > 0) {
                            TextButton(onClick = vm::freeSpace, enabled = !busy) {
                                Text(stringResource(R.string.omni_free_gb, s.preparedBytes / 1e9))
                            }
                        }
                    }
                    if (runtime is OmniRuntime.State.Loading) {
                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.xs))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConversationPane(
    vm: OmniStudioViewModel,
    mode: OmniStudioViewModel.Mode,
    ready: Boolean,
    phase: OmniStudioViewModel.Phase,
) {
    val context = LocalContext.current
    val turns by vm.turns.collectAsStateWithLifecycle()
    val answerForm by vm.answerForm.collectAsStateWithLifecycle()
    val mediaLabel by vm.mediaLabel.collectAsStateWithLifecycle()
    val photo by vm.photoPreview.collectAsStateWithLifecycle()
    var typed by rememberSaveable { mutableStateOf("") }
    val idle = phase is OmniStudioViewModel.Phase.Idle
    val recording = phase is OmniStudioViewModel.Phase.Recording

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri, displayName(context, uri) ?: "photo")
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickVideo(uri, displayName(context, uri) ?: "video")
    }
    val requestMic = com.vervan.chat.ui.common.rememberPermissionRequester { granted -> if (granted) vm.startRecording() }

    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (mode != OmniStudioViewModel.Mode.PHOTO) {
                com.vervan.chat.ui.common.VervanFilterChip(selected = answerForm == "voice", onClick = { vm.setAnswerForm("voice") }, label = { Text(stringResource(R.string.omni_spoken_answers)) })
                com.vervan.chat.ui.common.VervanFilterChip(selected = answerForm == "text", onClick = { vm.setAnswerForm("text") }, label = { Text(stringResource(R.string.omni_text_only)) })
            }
            when (mode) {
                else -> Unit
            }
            if (turns.isNotEmpty()) TextButton(onClick = vm::newConversation, enabled = idle) { Text(stringResource(R.string.omni_new_conversation)) }
        }
        // Choosing the photo or video is the first step of these modes: a full-width button.
        when (mode) {
            OmniStudioViewModel.Mode.PHOTO -> Button(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = ready && idle,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) { Text(stringResource(if (mediaLabel == null) R.string.omni_choose_photo else R.string.omni_change_photo)) }
            OmniStudioViewModel.Mode.VIDEO -> Button(
                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                enabled = ready && idle,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) { Text(stringResource(if (mediaLabel == null) R.string.omni_choose_video else R.string.omni_change_video)) }
            else -> Unit
        }
        when (mode) {
            OmniStudioViewModel.Mode.PHOTO -> Text(
                stringResource(R.string.omni_hint_photo),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OmniStudioViewModel.Mode.VIDEO -> Text(
                stringResource(R.string.omni_hint_video),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                stringResource(R.string.omni_hint_chat),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
            photo?.let {
                Image(it.asImageBitmap(), contentDescription = mediaLabel, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).padding(end = Space.sm))
            }
            mediaLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(turns.size, turns.lastOrNull()?.text) {
            if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
        }
        if (turns.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                com.vervan.chat.ui.common.EmptyState(
                    icon = if (mode == OmniStudioViewModel.Mode.CHAT) Icons.Filled.Mic else Icons.Filled.PlayArrow,
                    title = stringResource(if (ready) R.string.omni_empty_ready_title else R.string.omni_empty_not_ready_title),
                    body = stringResource(if (ready) R.string.omni_empty_ready_body else R.string.omni_empty_not_ready_body)
                )
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = Space.sm), state = listState,
            verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            items(turns, key = { it.id }) { turn -> TurnBubble(turn, onReplay = { vm.replay(turn) }) }
        }

        PhaseLine(phase, onStop = vm::stop)
        // Same shape as the chat composer: one rounded container, text on top, actions below,
        // with the send button filled only when there is something to send.
        val canSend = ready && idle && typed.isNotBlank()
        val sendLabel = stringResource(R.string.chat_send)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.sm)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = Space.lg, end = Space.xs, top = Space.sm, bottom = Space.xs)
        ) {
            val inputEnabled = ready && idle
            androidx.compose.foundation.text.BasicTextField(
                value = typed,
                onValueChange = { typed = it.take(2_000) },
                enabled = inputEnabled,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (inputEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = Space.sm, end = Space.sm),
                decorationBox = { inner ->
                    Box {
                        if (typed.isEmpty()) {
                            Text(
                                stringResource(R.string.omni_ask_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { if (recording) vm.stopRecording() else requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                    enabled = ready && (idle || recording)
                ) {
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (recording) stringResource(R.string.omni_stop_and_send) else stringResource(R.string.omni_record_question),
                        tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        .clickable(enabled = canSend, role = androidx.compose.ui.semantics.Role.Button) { vm.askTyped(typed); typed = "" }
                        .semantics { contentDescription = sendLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (canSend) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnBubble(turn: OmniStudioViewModel.Turn, onReplay: () -> Unit) {
    val container = if (turn.fromUser) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = container), shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(0.88f)) {
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    turn.text.ifBlank { if (turn.streaming) "…" else "" },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                )
                if (turn.pcm != null && !turn.streaming) {
                    IconButton(onClick = onReplay) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.omni_play)) }
                }
            }
        }
    }
}

@Composable
private fun PhaseLine(phase: OmniStudioViewModel.Phase, onStop: () -> Unit) {
    val text = when (phase) {
        is OmniStudioViewModel.Phase.Working -> phase.message
        is OmniStudioViewModel.Phase.Recording -> stringResource(R.string.omni_listening, phase.seconds)
        OmniStudioViewModel.Phase.Answering -> stringResource(R.string.omni_answering)
        OmniStudioViewModel.Phase.Idle -> return
    }
    Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        if (phase !is OmniStudioViewModel.Phase.Recording) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        if (phase !is OmniStudioViewModel.Phase.Recording) TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpeakPane(vm: OmniStudioViewModel, ready: Boolean, phase: OmniStudioViewModel.Phase) {
    val voice by vm.ttsVoice.collectAsStateWithLifecycle()
    val language by vm.ttsLanguage.collectAsStateWithLifecycle()
    val output by vm.ttsOutput.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val idle = phase is OmniStudioViewModel.Phase.Idle
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null) vm.saveSpeech(uri)
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Space.sm)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(1_500) },
            label = { Text(stringResource(R.string.omni_text_to_speak)) }, minLines = 4, maxLines = 10,
            supportingText = { Text(stringResource(R.string.omni_chars_count, text.length, 1_500)) },
            enabled = idle,
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.omni_voice_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Space.sm))
        FlowRow(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            com.vervan.chat.ui.common.VervanFilterChip(selected = voice == "female", onClick = { vm.setTtsVoice("female") }, label = { Text(stringResource(R.string.omni_voice_female)) })
            com.vervan.chat.ui.common.VervanFilterChip(selected = voice == "male", onClick = { vm.setTtsVoice("male") }, label = { Text(stringResource(R.string.omni_voice_male)) })
        }
        Text(stringResource(R.string.omni_accent_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Space.sm))
        FlowRow(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf("American English", "British English").forEach { l ->
                com.vervan.chat.ui.common.VervanFilterChip(selected = language == l, onClick = { vm.setTtsLanguage(l) }, label = { Text(l) })
            }
        }
        PhaseLine(phase, onStop = vm::stop)
        Button(
            onClick = { vm.speak(text) }, enabled = ready && idle && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = Space.md)
        ) { Text(stringResource(R.string.omni_speak_action)) }
        if (output != null) {
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = vm::replaySpeech, enabled = idle, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.omni_play_again)) }
                OutlinedButton(onClick = { saver.launch("omni-speech.wav") }, enabled = idle, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.omni_save_wav)) }
            }
        }
        Text(
            stringResource(R.string.omni_speak_hint),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm)
        )
    }
}

@Composable
private fun TranscribePane(vm: OmniStudioViewModel, ready: Boolean, phase: OmniStudioViewModel.Phase) {
    val context = LocalContext.current
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val idle = phase is OmniStudioViewModel.Phase.Idle
    val recording = phase is OmniStudioViewModel.Phase.Recording
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.transcribeFile(uri, displayName(context, uri) ?: "audio")
    }
    val requestMic = com.vervan.chat.ui.common.rememberPermissionRequester { granted -> if (granted) vm.startRecording() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Space.sm)) {
        Text(stringResource(R.string.omni_transcribe_hint),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Button(
                onClick = { if (recording) vm.stopRecording() else requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                enabled = ready && (idle || recording),
                modifier = Modifier.weight(1f)
            ) {
                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (recording) R.string.action_stop else R.string.omni_record), modifier = Modifier.padding(start = Space.xs))
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, enabled = ready && idle, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.omni_open_file))
            }
        }
        PhaseLine(phase, onStop = vm::stop)
        if (transcript.isNotBlank()) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Card(Modifier.fillMaxWidth().padding(top = Space.md)) {
                    Text(transcript, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.md))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(transcript)) }) { Text(stringResource(R.string.action_copy)) }
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, transcript)
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                }) { Text(stringResource(R.string.action_share)) }
            }
        }
        Spacer(Modifier.height(Space.lg))
    }
}

private fun displayName(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
