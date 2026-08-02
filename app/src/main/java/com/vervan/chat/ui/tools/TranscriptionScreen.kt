package com.vervan.chat.ui.tools

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.TranscriptionProject
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch

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
fun TranscriptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val vm: TranscriptionViewModel = viewModel(factory = viewModelFactory { initializer { TranscriptionViewModel(app) } })

    val projects by vm.projects.collectAsState()
    val current by vm.current.collectAsState()
    val phase by vm.phase.collectAsState()
    val installedModels by vm.installedModelVariants.collectAsState()
    val installedWhisperVariants = installedModels.filter { it.engine.equals("WHISPER_CPP", ignoreCase = true) && it.isReady }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.importFile(uri, queryDisplayName(context, uri))
        }
    }
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (current != null) "Transcript" else "Transcribe") },
            navigationIcon = {
                IconButton(onClick = { if (current != null) vm.closeCurrent() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        val project = current
        if (project == null) {
            PageContainer(Modifier.padding(padding)) {
                Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
                    Text(
                        "Import an audio or video file, or record directly. Transcription runs fully offline with whisper.cpp.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ResponsiveActions(Modifier.padding(top = Space.md)) {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) }
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Import file")
                        }
                        val recording = phase is TranscriptionViewModel.Phase.Recording
                        OutlinedButton(
                            onClick = {
                                if (recording) vm.stopRecording()
                                else requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                if (recording) {
                                    val ms = (phase as TranscriptionViewModel.Phase.Recording).elapsedMs
                                    "Stop (%d:%02d)".format(ms / 60000, (ms / 1000) % 60)
                                } else "Record"
                            )
                        }
                    }
                    if (installedWhisperVariants.isEmpty()) {
                        Text(
                            "No whisper.cpp model is downloaded yet — download one in Model Manager before transcribing.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    }
                    (phase as? TranscriptionViewModel.Phase.Failed)?.let {
                        Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm))
                    }

                    Text("History", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
                    if (projects.isEmpty()) {
                        Text("No transcriptions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        IconButton(onClick = { vm.delete(p.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
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

private fun statusLabel(p: TranscriptionProject): String = when (p.status) {
    "DONE" -> "Transcribed with ${p.modelVariant}"
    "TRANSCRIBING" -> "Transcribing…"
    "FAILED" -> p.errorMessage ?: "Failed"
    "CANCELLED" -> "Cancelled"
    else -> "Not transcribed yet"
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var text by remember(project.id) { mutableStateOf(project.transcript) }
    LaunchedEffect(project.transcript) { if (project.transcript != text) text = project.transcript }
    var variantMenuOpen by remember { mutableStateOf(false) }
    var selectedVariant by remember(project.id) { mutableStateOf(project.modelVariant) }
    val aiActionState by vm.aiActionState.collectAsState()
    val saveState by vm.saveState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    // Snapshot-based undo: not every keystroke (BasicTextField gives no cheap per-keystroke undo
    // stack of its own), just before a whole-document mutation — Replace All or an AI action —
    // since those are the edits large enough that losing them by accident actually hurts.
    val undoStack = remember(project.id) { mutableStateListOf<String>() }
    fun pushUndo() { undoStack.add(text) }
    fun applyMutation(newText: String) { pushUndo(); text = newText; onEdit(newText) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    val matchCount = if (searchQuery.isEmpty()) 0 else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(text).count()

    val segments = remember(project.segmentsJson) { vm.parseSegments(project) }

    PageContainer(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
            Text(project.fileName, style = MaterialTheme.typography.titleMedium)
            Text(statusLabel(project), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (installedVariants.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = variantMenuOpen, onExpandedChange = { variantMenuOpen = it },
                    modifier = Modifier.padding(top = Space.sm)
                ) {
                    OutlinedTextField(
                        value = selectedVariant, onValueChange = {}, readOnly = true,
                        label = { Text("whisper.cpp model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = variantMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    androidx.compose.material3.DropdownMenu(expanded = variantMenuOpen, onDismissRequest = { variantMenuOpen = false }) {
                        installedVariants.forEach { m ->
                            DropdownMenuItem(text = { Text(m.language) }, onClick = { selectedVariant = m.language; variantMenuOpen = false })
                        }
                    }
                }
            }

            when (phase) {
                is TranscriptionViewModel.Phase.Transcribing -> {
                    Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = Space.sm), strokeWidth = 2.dp)
                        Text("Transcribing…", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                else -> {
                    Button(
                        onClick = { onTranscribe(selectedVariant) },
                        enabled = installedVariants.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                    ) { Text(if (project.status == "DONE") "Re-transcribe" else "Transcribe") }
                }
            }
            (phase as? TranscriptionViewModel.Phase.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm))
            }

            if (segments.isNotEmpty()) {
                TimestampedPlaybackCard(audioPath = project.audioPath, segments = segments)
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onEdit(it) },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Space.md),
                label = { Text("Transcript") },
                placeholder = { Text("Transcribe the audio, or type/paste text here.") }
            )

            Text(
                "${text.length} characters · ${text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size} words",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    label = { Text("Find") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceWith, onValueChange = { replaceWith = it },
                    label = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    if (searchQuery.isEmpty()) "" else "$matchCount match${if (matchCount == 1) "" else "es"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val replaced = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
                            .replace(text, Regex.escapeReplacement(replaceWith))
                        applyMutation(replaced)
                    },
                    enabled = matchCount > 0
                ) { Text("Replace all") }
                OutlinedButton(
                    onClick = {
                        val previous = undoStack.removeLastOrNull() ?: return@OutlinedButton
                        text = previous
                        onEdit(previous)
                    },
                    enabled = undoStack.isNotEmpty(),
                    modifier = Modifier.padding(start = Space.sm)
                ) { Text("Undo") }
            }

            ResponsiveActions(Modifier.padding(top = Space.md)) {
                OutlinedButton(onClick = { onExport("txt") }, enabled = text.isNotBlank()) { Text("TXT") }
                OutlinedButton(onClick = { onExport("md") }, enabled = text.isNotBlank()) { Text("Markdown") }
                OutlinedButton(onClick = { onExport("pdf") }, enabled = text.isNotBlank()) { Text("PDF") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }

            Text("Ask the offline model", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.md))
            val running = (aiActionState as? TranscriptionViewModel.AiActionState.Running)?.label
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AI_ACTIONS.forEach { (label, template) ->
                    OutlinedButton(
                        onClick = { pushUndo(); vm.runAiAction(project.id, label, template) },
                        enabled = text.isNotBlank() && running == null
                    ) { Text(if (running == label) "$label…" else label) }
                }
            }
            (aiActionState as? TranscriptionViewModel.AiActionState.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }

            OutlinedButton(
                onClick = { showSaveDialog = true },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) { Text("Save to Knowledge Base") }
            when (saveState) {
                TranscriptionViewModel.SaveState.Saved -> Text("Saved.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                is TranscriptionViewModel.SaveState.Failed -> Text((saveState as TranscriptionViewModel.SaveState.Failed).message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                else -> {}
            }
        }
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
    val knowledgeBases by vm.knowledgeBases.collectAsState()
    var selectedKbId by remember { mutableStateOf<String?>(knowledgeBases.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Knowledge Base") },
        text = {
            Column {
                if (knowledgeBases.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                        OutlinedTextField(
                            value = knowledgeBases.find { it.id == selectedKbId }?.name ?: "New Knowledge Base",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Knowledge Base") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            knowledgeBases.forEach { kb ->
                                DropdownMenuItem(text = { Text(kb.name) }, onClick = { selectedKbId = kb.id; menuOpen = false })
                            }
                            DropdownMenuItem(text = { Text("New Knowledge Base…") }, onClick = { selectedKbId = null; menuOpen = false })
                        }
                    }
                }
                if (selectedKbId == null) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("New Knowledge Base name") },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedKbId, newName.ifBlank { "Transcripts" }) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
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
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                Text("Timestamps", style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(160.dp)) {
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
