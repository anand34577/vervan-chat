package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.validation.InputLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TextToSpeechScreen(onBack: () -> Unit, onOpenModels: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val vm: TextToSpeechViewModel = viewModel(factory = viewModelFactory { initializer { TextToSpeechViewModel(app) } })

    val phase by vm.phase.collectAsStateWithLifecycle()
    val sentenceResults by vm.sentenceResults.collectAsStateWithLifecycle()
    val installedModels by vm.installedVoiceModels.collectAsStateWithLifecycle()
    val defaultEnginePref by vm.ttsEnginePreference.collectAsStateWithLifecycle()
    val defaultSupertonicVoice by vm.supertonicVoiceVariant.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
    var engine by remember(defaultEnginePref) { mutableStateOf(if (defaultEnginePref == "AUTO") "PIPER" else defaultEnginePref) }
    var supertonicVoice by remember(defaultSupertonicVoice) { mutableStateOf(defaultSupertonicVoice) }
    var pauseMs by remember { mutableIntStateOf(250) }

    val piperReady = installedModels.any { it.engine == "PIPER" && it.isReady }
    val kokoroReady = installedModels.any { it.engine == "KOKORO" && it.language == "multi" && it.isReady }
    val supertonicVoices = installedModels.filter { it.engine == "SUPERTONIC" && it.isReady }
    val omniReady = installedModels.any { it.engine == com.vervan.chat.omni.OmniRuntime.SPEECH_ENGINE && it.isReady }
    // Only installed voices are offered. Listing four "(not downloaded)" chips, one of them
    // pre-selected but disabled, gave no way forward; an empty list gets one clear action instead.
    val readyEngines = buildList {
        if (piperReady) add("PIPER" to "Piper")
        if (kokoroReady) add("KOKORO" to "Kokoro")
        if (supertonicVoices.isNotEmpty()) add("SUPERTONIC" to "Supertonic")
        if (omniReady) add("LFM3_OMNI" to "LFM3 Omni")
    }
    LaunchedEffect(readyEngines.map { it.first }) {
        if (readyEngines.isNotEmpty() && readyEngines.none { it.first == engine }) engine = readyEngines.first().first
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.text_to_speech_text_to_speech)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
        )
    }) { padding ->
        PageContainer(Modifier.padding(padding)) {
            // One scrolling column. It used to be a fixed column where the text box only got
            // weight(1f) — the leftover space — and the history empty state below it filled the
            // screen, so the text box collapsed to nothing and there was nowhere to type.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(InputLimits.TTS_TEXT_CHARS) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 320.dp),
                    label = { Text(stringResource(R.string.text_to_speech_text)) },
                    placeholder = { Text(stringResource(R.string.text_to_speech_type_or_paste_text_to)) },
                    supportingText = { Text("${text.length} / ${InputLimits.TTS_TEXT_CHARS}") }
                )

                Text(stringResource(R.string.text_to_speech_voice), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.md))
                if (readyEngines.isEmpty()) {
                    com.vervan.chat.ui.common.SystemStatusStrip(
                        title = stringResource(R.string.tts_no_voice_title),
                        body = stringResource(R.string.tts_no_voice_body),
                        tone = com.vervan.chat.ui.common.StatusTone.Warning,
                        actionLabel = stringResource(R.string.tts_get_voice),
                        onAction = onOpenModels,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        readyEngines.forEach { (id, label) ->
                            com.vervan.chat.ui.common.VervanFilterChip(selected = engine == id, onClick = { engine = id }, label = { Text(label) })
                        }
                    }
                }
                if (engine == "SUPERTONIC" && supertonicVoices.size > 1) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        supertonicVoices.forEach { voice ->
                            com.vervan.chat.ui.common.VervanFilterChip(selected = supertonicVoice == voice.language, onClick = { supertonicVoice = voice.language }, label = { Text(voice.language) })
                        }
                    }
                }

                Text(stringResource(R.string.text_to_speech_pause_between_sentences_arg1_ms, pauseMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Space.sm))
                Slider(value = pauseMs.toFloat(), onValueChange = { pauseMs = it.toInt() }, valueRange = 0f..1000f)

                when (val p = phase) {
                    TextToSpeechViewModel.Phase.LoadingEngine -> {
                        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = Space.sm), strokeWidth = 2.dp)
                            Text(stringResource(R.string.text_to_speech_loading_voice_engine_this_can), style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                    is TextToSpeechViewModel.Phase.Generating -> {
                        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = Space.sm), strokeWidth = 2.dp)
                            Text(if (p.total > 0) stringResource(R.string.text_to_speech_generating_arg1_arg2, p.sentenceIndex, p.total) else stringResource(R.string.text_to_speech_generating), style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                    TextToSpeechViewModel.Phase.ReviewingResults -> {
                        val failedCount = sentenceResults.count { it.audio == null }
                        Text(
                            "$failedCount sentence${if (failedCount == 1) "" else "s"} failed to synthesize. Retry them, or finish without them.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Space.md)
                        )
                        LazyColumn(Modifier.height(200.dp).padding(top = Space.sm)) {
                            items(sentenceResults.size) { index ->
                                val r = sentenceResults[index]
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        r.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                        color = if (r.audio == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (r.audio == null) {
                                        IconButton(onClick = { vm.retrySentence(index) }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry))
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Button(
                                onClick = { vm.finishAnyway(text, pauseMs) },
                                enabled = sentenceResults.any { it.audio != null },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.text_to_speech_finish_anyway)) }
                            OutlinedButton(onClick = vm::cancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.unsaved_changes_discard)) }
                        }
                    }
                    else -> {
                        Button(
                            onClick = { vm.generate(text, engine, "auto", supertonicVoice.takeIf { engine == "SUPERTONIC" }, pauseMs) },
                            enabled = text.isNotBlank() && readyEngines.any { it.first == engine },
                            modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                        ) { Text(stringResource(R.string.text_to_speech_generate_audio)) }
                        if (readyEngines.isNotEmpty() && text.isBlank()) {
                            Text(
                                stringResource(R.string.tts_type_to_enable),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                        }
                    }
                }
                (phase as? TextToSpeechViewModel.Phase.Failed)?.let {
                    ErrorCard(title = stringResource(R.string.text_to_speech_couldn_t_generate_audio), body = it.message, modifier = Modifier.padding(top = Space.sm))
                }
                (phase as? TextToSpeechViewModel.Phase.Done)?.let { done ->
                    AudioPlaybackCard(
                        file = done.file,
                        onShare = { file, mime -> shareAudio(context, file, mime) },
                        onExportM4a = {
                            scope.launch {
                                val m4a = vm.exportM4a(done.file)
                                shareAudio(context, m4a, "audio/mp4")
                            }
                        }
                    )
                }

                Text(stringResource(R.string.transcription_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
                if (projects.isEmpty()) {
                    // Inline note, not the full-screen EmptyState (which takes all remaining height).
                    Text(
                        stringResource(R.string.text_to_speech_audio_you_generate_above_will),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        projects.forEach { p ->
                            Card(
                                onClick = {
                                    text = p.sourceText
                                    engine = p.engine
                                    if (p.engine == "SUPERTONIC") supertonicVoice = p.voiceVariant
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
                                colors = SurfaceRole.Card.cardColors(),
                                border = SurfaceRole.Card.border(),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(Space.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        OverflowTooltipText(p.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${p.engine} · ${(p.durationMs / 1000)}s",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.deleteProject(p.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.library_delete)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareAudio(context: android.content.Context, file: java.io.File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mime
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share audio"))
}

@Composable
private fun AudioPlaybackCard(file: java.io.File, onShare: (java.io.File, String) -> Unit, onExportM4a: () -> Unit) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(file) { mutableStateOf(false) }
    var positionMs by remember(file) { mutableStateOf(0) }
    var durationMs by remember(file) { mutableStateOf(0) }
    var speed by remember(file) { mutableFloatStateOf(1f) }

    DisposableEffect(file) {
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener { isPlaying = false; positionMs = 0 }
        }
        player = mp
        durationMs = mp.duration
        onDispose { mp.release(); player = null }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying && player != null) {
            positionMs = player?.currentPosition ?: 0
            kotlinx.coroutines.delay(200)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = {
                val mp = player ?: return@IconButton
                if (isPlaying) mp.pause() else mp.start()
                isPlaying = !isPlaying
            }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.text_to_speech_play))
            }
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { fraction ->
                    val target = (fraction * durationMs).toInt()
                    player?.seekTo(target)
                    positionMs = target
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onShare(file, "audio/wav") }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.text_to_speech_share_wav)) }
        }
        Text(
            "%d:%02d / %d:%02d".format(positionMs / 60000, (positionMs / 1000) % 60, durationMs / 60000, (durationMs / 1000) % 60),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.tts_speed_value, "%.1f".format(speed)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = Space.sm))
            Slider(
                value = speed, onValueChange = { newSpeed ->
                    speed = newSpeed
                    val mp = player ?: return@Slider
                    runCatching {
                        val wasPlaying = mp.isPlaying
                        mp.playbackParams = PlaybackParams().setSpeed(newSpeed)
                        if (wasPlaying && !mp.isPlaying) mp.start()
                    }
                },
                valueRange = 0.5f..2f, modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(onClick = onExportM4a, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) { Text(stringResource(R.string.text_to_speech_export_as_m4a)) }
    }
}
