package com.vervan.chat.ui.tools

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.data.db.entities.TranscriptionProject
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it } }
    }
    return name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(onBack: () -> Unit, onOpenModels: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val vm: TranscriptionViewModel = viewModel(factory = viewModelFactory { initializer { TranscriptionViewModel(app) } })

    val projects by vm.projects.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TranscriptionProject?>(null) }
    pendingDelete?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.txd_delete_title)) },
            text = { Text(stringResource(R.string.txd_delete_body, p.fileName)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.delete(p.id); pendingDelete = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    val current by vm.current.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val installedModels by vm.installedModelVariants.collectAsStateWithLifecycle()
    // whisper.cpp sizes and Shruti languages share one picker; a Shruti choice is keyed
    // "shruti:<lang>" (see TranscriptionViewModel.SHRUTI_PREFIX).
    val installedWhisperVariants = installedModels.filter {
        (it.engine.equals("WHISPER_CPP", ignoreCase = true) ||
            (it.engine.equals(com.vervan.chat.voice.ShrutiSttEngine.ENGINE, ignoreCase = true) && com.vervan.chat.BuildConfig.NEEDLE_AVAILABLE)) &&
            it.isReady
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.importFile(uri, queryDisplayName(context, uri))
        }
    }
    val requestMicPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) vm.startRecording()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (current != null) "Transcript" else "Transcribe") },
            navigationIcon = {
                IconButton(onClick = { if (current != null) vm.closeCurrent() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        val project = current
        if (project == null) {
            PageContainer(Modifier.padding(padding)) {
                Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
                    Text(
                        stringResource(R.string.transcription_import_an_audio_or_video),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Prerequisite first, with the way out: the old order offered Import/Record
                    // (which then failed) above a red card that only said "go to Model Manager".
                    val hasSpeechModel = installedWhisperVariants.isNotEmpty()
                    if (!hasSpeechModel) {
                        com.vervan.chat.ui.common.SystemStatusStrip(
                            title = stringResource(R.string.transcription_no_model),
                            body = stringResource(R.string.transcription_download_one_in_model_manager),
                            tone = com.vervan.chat.ui.common.StatusTone.Warning,
                            actionLabel = stringResource(R.string.transcription_get_model),
                            onAction = onOpenModels,
                            modifier = Modifier.padding(top = Space.md)
                        )
                    }
                    ToolSourceRow(Modifier.padding(top = Space.md)) {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) },
                            enabled = hasSpeechModel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.transcription_import_file))
                        }
                        val recording = phase is TranscriptionViewModel.Phase.Recording
                        OutlinedButton(
                            onClick = {
                                if (recording) vm.stopRecording()
                                else requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = hasSpeechModel || recording,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                if (recording) {
                                    val ms = (phase as TranscriptionViewModel.Phase.Recording).elapsedMs
                                    "Stop (%d:%02d)".format(ms / 60000, (ms / 1000) % 60)
                                } else stringResource(R.string.media_record)
                            )
                        }
                    }
                    (phase as? TranscriptionViewModel.Phase.Failed)?.let {
                        ErrorCard(
                        title = stringResource(R.string.transcription_failed),
                            body = it.message,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }

                Text(stringResource(R.string.transcription_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
                    if (projects.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Mic,
                    title = stringResource(R.string.transcription_empty),
                            body = stringResource(R.string.transcription_import_an_audio_file_or)
                        )
                    } else {
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(projects, key = { it.id }) { p ->
                                Card(
                                    onClick = { vm.open(p.id) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
                                    colors = SurfaceRole.Card.cardColors(),
                                    border = SurfaceRole.Card.border(),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(Space.md), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            OverflowTooltipText(p.fileName, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                statusLabel(p),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { pendingDelete = p }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
          // System back closes the transcript, like the toolbar arrow, instead of leaving the tool.
          androidx.activity.compose.BackHandler { vm.closeCurrent() }
          androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            TranscriptionDetail(
                vm = vm,
                project = project, phase = phase, installedVariants = installedWhisperVariants,
                onTranscribe = { variant -> vm.transcribe(project.id, variant) },
                onCancel = vm::cancelTranscription,
                onEdit = { text -> vm.updateTranscript(project.id, text) },
                onDelete = { vm.delete(project.id); vm.closeCurrent() },
                onExport = { kind ->
                    scope.launch {
                        val file = when (kind) {
                            "txt" -> vm.exportTxt(project)
                            "md" -> vm.exportMarkdown(project)
                            else -> vm.exportPdf(project)
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val mime = when (kind) { "txt" -> "text/plain"; "md" -> "text/markdown"; else -> "application/pdf" }
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, project.fileName)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Export transcript"))
                    }
                }
            )
          }
        }
    }
}

@androidx.compose.runtime.Composable
private fun statusLabel(p: TranscriptionProject): String = when (p.status) {
    "DONE" -> stringResource(R.string.transcription_transcribed_with_arg1, p.modelVariant)
    "TRANSCRIBING" -> stringResource(R.string.transcription_running)
    "FAILED" -> p.errorMessage ?: "Failed"
    "CANCELLED" -> stringResource(R.string.transcription_cancelled)
    else -> stringResource(R.string.transcription_not_transcribed_yet)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TranscriptionDetail(
    vm: TranscriptionViewModel,
    project: TranscriptionProject,
    phase: TranscriptionViewModel.Phase,
    installedVariants: List<com.vervan.chat.data.db.entities.TtsVoiceModel>,
    onTranscribe: (String?) -> Unit,
    onCancel: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val copiedLabel = stringResource(R.string.chat_copied)
    var text by remember(project.id) { mutableStateOf(project.transcript) }
    LaunchedEffect(project.transcript) { if (project.transcript != text) text = project.transcript }
    var variantMenuOpen by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var findOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    val aiActionState by vm.aiActionState.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }

    fun variantKey(m: com.vervan.chat.data.db.entities.TtsVoiceModel) =
        if (m.engine.equals(com.vervan.chat.voice.ShrutiSttEngine.ENGINE, ignoreCase = true)) TranscriptionViewModel.SHRUTI_PREFIX + m.language else m.language
    fun variantLabel(key: String) = if (key.startsWith(TranscriptionViewModel.SHRUTI_PREFIX)) {
        "Shruti " + (com.vervan.chat.voice.ShrutiSttEngine.LANGUAGES[key.removePrefix(TranscriptionViewModel.SHRUTI_PREFIX)] ?: key)
    } else "Whisper $key"
    val installedKeys = installedVariants.map(::variantKey)
    // The project remembers the engine it last used; fall back to an installed one when that is gone.
    var selectedVariant by remember(project.id, installedKeys) {
        mutableStateOf(project.modelVariant.takeIf { it in installedKeys } ?: installedKeys.firstOrNull() ?: project.modelVariant)
    }

    // Snapshot undo before whole-document changes (Replace all, a model rewrite, an added section).
    val undoStack = remember(project.id) { mutableStateListOf<String>() }
    fun applyMutation(newText: String) { undoStack.add(text); text = newText.take(InputLimits.TRANSCRIPT_CHARS); onEdit(text) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var replaceWith by rememberSaveable { mutableStateOf("") }
    val matchCount = if (searchQuery.isEmpty()) 0 else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(text).count()
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    val segments = remember(project.segmentsJson) { vm.parseSegments(project) }
    val transcribing = phase is TranscriptionViewModel.Phase.Transcribing

    fun copy(value: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText(project.fileName, value))
        android.widget.Toast.makeText(context, copiedLabel, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun share(value: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, project.fileName)
            .putExtra(android.content.Intent.EXTRA_TEXT, value)
        context.startActivity(android.content.Intent.createChooser(send, null))
    }

    PageContainer(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = Space.lg)
        ) {
            // The recording
            Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.md)) {
                    Text(project.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(statusLabel(project), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (segments.isNotEmpty()) TimestampedPlaybackCard(audioPath = project.audioPath, segments = segments)
                }
            }

            // 1 · Transcribe
            ToolStep(1, stringResource(R.string.txd_step_transcribe), hint = stringResource(R.string.txd_step_transcribe_hint))
            if (installedVariants.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = variantMenuOpen, onExpandedChange = { if (!transcribing) variantMenuOpen = it }) {
                    OutlinedTextField(
                        value = variantLabel(selectedVariant), onValueChange = {}, readOnly = true, enabled = !transcribing,
                        label = { Text(stringResource(R.string.transcription_model)) },
                        trailingIcon = { if (installedVariants.size > 1) ExposedDropdownMenuDefaults.TrailingIcon(expanded = variantMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    if (installedVariants.size > 1) {
                        androidx.compose.material3.DropdownMenu(expanded = variantMenuOpen, onDismissRequest = { variantMenuOpen = false }) {
                            installedVariants.forEach { m ->
                                DropdownMenuItem(text = { Text(variantLabel(variantKey(m))) }, onClick = { selectedVariant = variantKey(m); variantMenuOpen = false })
                            }
                        }
                    }
                }
            }
            ToolPrimaryButton(
                label = stringResource(if (project.status == "DONE") R.string.txd_retranscribe else R.string.txd_transcribe),
                icon = Icons.Filled.Mic,
                onClick = {
                    if (project.status == "DONE" && text.isNotBlank()) undoStack.add(text)
                    onTranscribe(selectedVariant)
                },
                blockedReason = if (installedVariants.isEmpty()) stringResource(R.string.transcription_no_model) else null,
                running = transcribing,
                onStop = onCancel
            )
            if (transcribing) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.sm))
                Text(stringResource(R.string.transcription_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.xs))
            }
            (phase as? TranscriptionViewModel.Phase.Failed)?.let {
                ErrorCard(title = stringResource(R.string.transcription_failed), body = it.message, modifier = Modifier.padding(top = Space.sm))
            }

            // 2 · Transcript
            ToolStep(2, stringResource(R.string.txd_step_review), hint = stringResource(R.string.txd_step_review_hint))
            OutlinedTextField(
                value = text,
                onValueChange = { val next = it.take(InputLimits.TRANSCRIPT_CHARS); text = next; onEdit(next) },
                enabled = !transcribing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp),
                label = { Text(stringResource(R.string.transcription_transcript)) },
                placeholder = { Text(stringResource(R.string.transcription_placeholder)) },
                supportingText = { Text(stringResource(R.string.txd_counts, text.length, words)) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = { copy(text) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text(stringResource(R.string.action_copy), modifier = Modifier.padding(start = Space.xs))
                }
                OutlinedButton(onClick = { share(text) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.library_share), modifier = Modifier.padding(start = Space.xs))
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { exportMenuOpen = true }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp)); Text(stringResource(R.string.txd_export), modifier = Modifier.padding(start = Space.xs))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_txt)) }, onClick = { exportMenuOpen = false; onExport("txt") })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_markdown)) }, onClick = { exportMenuOpen = false; onExport("md") })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_pdf)) }, onClick = { exportMenuOpen = false; onExport("pdf") })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = { findOpen = !findOpen }, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.FindReplace, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.txd_find_replace), modifier = Modifier.padding(start = Space.xs))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                androidx.compose.material3.TextButton(
                    onClick = { undoStack.removeLastOrNull()?.let { previous -> text = previous; onEdit(previous) } },
                    enabled = undoStack.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.action_undo), modifier = Modifier.padding(start = Space.xs))
                }
            }
            if (findOpen) {
                Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                    Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it.take(InputLimits.SEARCH_QUERY_CHARS) },
                            label = { Text(stringResource(R.string.transcription_find)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = if (searchQuery.isEmpty()) null else {
                                { Text(if (matchCount == 1) stringResource(R.string.txd_one_match) else stringResource(R.string.txd_matches, matchCount)) }
                            }
                        )
                        OutlinedTextField(
                            value = replaceWith, onValueChange = { replaceWith = it.take(InputLimits.SEARCH_QUERY_CHARS) },
                            label = { Text(stringResource(R.string.transcription_replace_with)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                applyMutation(Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).replace(text, Regex.escapeReplacement(replaceWith)))
                            },
                            enabled = matchCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (matchCount > 0) stringResource(R.string.txd_replace_n, matchCount) else stringResource(R.string.transcription_replace_all)) }
                    }
                }
            }

            // 3 · Ask the model
            ToolStep(3, stringResource(R.string.transcription_offline_model), hint = stringResource(R.string.txd_ai_hint))
            val running = (aiActionState as? TranscriptionViewModel.AiActionState.Running)?.label
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                AI_ACTIONS.forEach { (label, template) ->
                    com.vervan.chat.ui.common.VervanFilterChip(
                        selected = running == label,
                        onClick = { vm.runAiAction(project.id, label, template) },
                        enabled = text.isNotBlank() && running == null && !transcribing,
                        label = { Text(if (running == label) "$label…" else label) }
                    )
                }
            }
            if (running != null) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.sm))
            }
            when (val ai = aiActionState) {
                is TranscriptionViewModel.AiActionState.Failed ->
                    ErrorCard(title = stringResource(R.string.transcription_action_failed), body = ai.message, modifier = Modifier.padding(top = Space.sm))
                is TranscriptionViewModel.AiActionState.Done -> Card(
                    Modifier.fillMaxWidth().padding(top = Space.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(Space.md)) {
                        Text(ai.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            com.vervan.chat.ui.common.MarkdownLiteText(ai.text, modifier = Modifier.padding(top = Space.xs))
                        }
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().padding(top = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            if (ai.label == "Improve grammar") {
                                Button(onClick = { applyMutation(ai.text); vm.dismissAiResult() }) { Text(stringResource(R.string.txd_replace_transcript)) }
                            } else {
                                Button(onClick = {
                                    applyMutation(text.trimEnd() + "\n\n## ${ai.label}\n\n${ai.text}\n")
                                    vm.dismissAiResult()
                                }) { Text(stringResource(R.string.txd_add_to_transcript)) }
                            }
                            OutlinedButton(onClick = { copy(ai.text) }) { Text(stringResource(R.string.action_copy)) }
                            androidx.compose.material3.TextButton(onClick = vm::dismissAiResult) { Text(stringResource(R.string.omni_dismiss)) }
                        }
                    }
                }
                else -> {}
            }

            // Keep it
            ToolStep(4, stringResource(R.string.txd_step_keep))
            OutlinedButton(
                onClick = { showSaveDialog = true },
                enabled = text.isNotBlank() && !transcribing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Description, null, Modifier.size(18.dp))
                Text(stringResource(R.string.transcription_save_knowledge), modifier = Modifier.padding(start = Space.sm))
            }
            when (val save = saveState) {
                TranscriptionViewModel.SaveState.Saved -> com.vervan.chat.ui.common.SystemStatusStrip(
                    title = stringResource(R.string.transcription_saved),
                    body = stringResource(R.string.transcription_the_transcript_was_saved_to),
                    tone = com.vervan.chat.ui.common.StatusTone.Ready,
                    modifier = Modifier.padding(top = Space.sm)
                )
                is TranscriptionViewModel.SaveState.Failed -> ErrorCard(
                    title = stringResource(R.string.transcription_save_failed),
                    body = save.message,
                    modifier = Modifier.padding(top = Space.sm)
                )
                else -> {}
            }
            androidx.compose.material3.TextButton(
                onClick = { confirmDelete = true },
                enabled = !transcribing,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) {
                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                Text(stringResource(R.string.txd_delete), modifier = Modifier.padding(start = Space.sm))
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.txd_delete_title)) },
            text = { Text(stringResource(R.string.txd_delete_body, project.fileName)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showSaveDialog) {
        SaveToKnowledgeBaseDialog(
            vm = vm,
            onDismiss = { showSaveDialog = false; vm.resetSaveState() },
            onSave = { kbId, newName -> vm.saveToKnowledgeBase(project.id, kbId, newName); showSaveDialog = false }
        )
    }
}

private val AI_ACTIONS: List<Pair<String, (String) -> String>> = listOf(
    "Summarize" to { t -> "Summarize the following transcript in a short paragraph:\n\n$t" },
    "Action items" to { t -> "Extract the action items from this transcript as a bullet list. If there are none, say so.\n\n$t" },
    "Title" to { t -> "Suggest one short, specific title (under 10 words) for this transcript. Reply with only the title.\n\n$t" },
    "Improve grammar" to { t -> "Rewrite the following transcript fixing grammar and filler words, but keep the meaning and speaker's voice unchanged:\n\n$t" }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToKnowledgeBaseDialog(vm: TranscriptionViewModel, onDismiss: () -> Unit, onSave: (kbId: String?, newName: String?) -> Unit) {
    val knowledgeBases by vm.knowledgeBases.collectAsStateWithLifecycle()
    var selectedKbId by remember { mutableStateOf<String?>(knowledgeBases.firstOrNull()?.id) }
    var newName by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val defaultTitle = stringResource(R.string.transcription_default_title)
    val newKnowledgeBaseLabel = stringResource(R.string.transcription_new_knowledge_base)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transcription_save_title)) },
        text = {
            Column {
                if (knowledgeBases.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                        OutlinedTextField(
                            value = knowledgeBases.find { it.id == selectedKbId }?.name ?: newKnowledgeBaseLabel,
                            onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.transcription_knowledge_base)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            knowledgeBases.forEach { kb ->
                                DropdownMenuItem(text = { Text(kb.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = { selectedKbId = kb.id; menuOpen = false })
                            }
            DropdownMenuItem(text = { Text(stringResource(R.string.transcription_new_knowledge_base)) }, onClick = { selectedKbId = null; menuOpen = false })
                        }
                    }
                }
                if (selectedKbId == null) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.take(80) },
                label = { Text(stringResource(R.string.transcription_new_knowledge_base_name)) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedKbId, newName.ifBlank { defaultTitle }) }) { Text(stringResource(R.string.action_save)) }
        },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Plays the source recording and lets tapping a segment jump straight to that point — whisper.cpp
 * segment timestamps (see [com.vervan.chat.voice.WhisperCppSttEngine.transcribeWithTimestamps])
 * are a fixed side list, independent of any edits made to the transcript text field above. */
@Composable
private fun TimestampedPlaybackCard(
    audioPath: String,
    segments: List<com.vervan.chat.voice.WhisperCppSttEngine.TranscriptSegment>
) {
    var player by remember(audioPath) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember(audioPath) { mutableStateOf(false) }
    var positionMs by remember(audioPath) { mutableStateOf(0L) }

    androidx.compose.runtime.DisposableEffect(audioPath) {
        val mp = runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                setOnCompletionListener { isPlaying = false }
            }
        }.getOrNull()
        player = mp
        onDispose { mp?.release(); player = null }
    }
    LaunchedEffect(isPlaying, player) {
        while (isPlaying && player != null) {
            positionMs = player?.currentPosition?.toLong() ?: 0L
            kotlinx.coroutines.delay(200)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = {
                    val mp = player ?: return@IconButton
                    if (isPlaying) mp.pause() else mp.start()
                    isPlaying = !isPlaying
                }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.transcription_play)
                    )
                }
            Text(stringResource(R.string.transcription_timestamps), style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 160.dp)) {
                items(segments) { seg ->
                    val active = positionMs in seg.startMs..(seg.endMs.coerceAtLeast(seg.startMs + 1))
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                player?.seekTo(seg.startMs.toInt())
                                player?.start()
                                isPlaying = true
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            "%d:%02d".format(seg.startMs / 60000, (seg.startMs / 1000) % 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Space.sm)
                        )
                        Text(
                            seg.text, style = MaterialTheme.typography.bodySmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
