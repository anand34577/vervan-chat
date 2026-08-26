@file:Suppress("LocalContextGetResourceValueCall")

package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResultActions
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One button in a [TextActionScreen] — [promptFor] turns the current input text into a
 * complete, self-contained one-shot prompt (see [OneShotLlm]). */
data class TextAction(val label: String, val promptFor: (String) -> String)

/**
 * Generic "paste/speak/scan text in, pick a transform, LLM output out" screen — backs Writing
 * Assistant, Smart Notes, Clipboard Assistant, Explain Like I'm, and the text half of Screenshot
 * Intelligence. One implementation instead of five near-identical screens (reuse).
 *
 * Output streams token-by-token with a stop control, so the user sees progress immediately and
 * can keep a partial result — same interaction model as the main chat, not a blocking spinner.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextActionScreen(
    title: String,
    actions: List<TextAction>,
    onBack: () -> Unit,
    hint: String = "Paste or type text",
    initialText: String = "",
    allowVoice: Boolean = false,
    allowImageOcr: Boolean = false,
    allowSaveAsNote: Boolean = false,
    requireInput: Boolean = true
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf(initialText) }
    var outputText by remember { mutableStateOf("") }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var lastAction by remember { mutableStateOf<TextAction?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var genJob by remember { mutableStateOf<Job?>(null) }

    fun runAction(action: TextAction) {
        if (inputText.isBlank() && requireInput) return
        genJob?.cancel()
        isRunning = true
        activeLabel = action.label
        lastAction = action
        outputText = ""
        errorText = null
        genJob = scope.launch {
            try {
                val route = searchableTools.firstOrNull { it.label.equals(title, ignoreCase = true) }?.route ?: "tools"
                val flow = OneShotLlm.stream(
                    app,
                    action.promptFor(inputText),
                    runContext = com.vervan.chat.llm.ToolRunContext(route, "$title · ${action.label}", inputText),
                )
                if (flow == null) {
                    errorText = "No model is ready. Open Settings → AI models, load one, then try again."
                } else {
                    // throttle the state write (not the collection) to ~16 fps so
                    // MarkdownLiteText isn't re-parsed on every single token during a fast stream.
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    flow.collect {
                        sb.append(it)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60) { outputText = sb.toString(); lastEmit = now }
                    }
                    outputText = sb.toString()
                    if (outputText.isBlank()) errorText = "The model returned an empty response. Try again."
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                // stream() throws on load/capability failures — surface it instead of crashing
                // the coroutine (the old blocking run() left this uncaught).
                errorText = t.toUserMessage()
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        genJob?.cancel()
        isRunning = false
    }

    fun copy(text: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setSensitiveText(text, scope, title)
        scope.launch { snackbarHostState.showSnackbar("Copied") }
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, activeLabel ?: title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share result"))
    }

    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (!text.isNullOrBlank()) inputText = (if (inputText.isBlank()) text else "$inputText $text").take(InputLimits.GENERAL_TOOL_INPUT_CHARS)
    }
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "textaction-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var isOcrRunning by remember { mutableStateOf(false) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            isOcrRunning = true
            scope.launch {
                if (file.length() > ImportLimits.MAX_IMAGE_SOURCE_BYTES || !ImageUtils.normalizeForModel(file)) {
                    errorText = "The captured image is too large or unreadable."
                    file.delete()
                    isOcrRunning = false
                    return@launch
                }
                // Only the extracted text is kept — the copied JPEG has no further use once OCR
                // has read it, and this screen never displays it, so it's deleted immediately
                // instead of leaking into filesDir/images (same pattern as
                // FlashcardsFromPhotoScreen.runOcr).
                val text = withContext(Dispatchers.IO) {
                    runCatching { OcrExtractor.extractFromImage(file) }.getOrElse { throw IllegalStateException("Could not read OCR text: ${it.message}") }.also { file.delete() }
                }
                inputText = (if (inputText.isBlank()) text else "$inputText\n$text").take(InputLimits.GENERAL_TOOL_INPUT_CHARS)
                isOcrRunning = false
            }
        } else file?.delete()
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingFile = file
            takePicture.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
      ScrollablePage(contentPadding = padding) {
            FeatureHero(
                icon = Icons.Filled.AutoAwesome,
                eyebrow = stringResource(R.string.email_composer_eyebrow),
                title = title,
                body = stringResource(R.string.ui_textactionscreen_244_add_text_choose_an_action_then_review_the_re),
            )

            VervanSectionHeader("1 · Add input")
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it.take(InputLimits.GENERAL_TOOL_INPUT_CHARS) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = MaterialTheme.shapes.medium,
                placeholder = { Text(hint) },
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(onClick = { copy(inputText) }) { Icon(Icons.Filled.ContentCopy, "Copy input text") }
                    }
                }
            )
            if (allowVoice || allowImageOcr) {
                Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (allowVoice) {
                        OutlinedButton(onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Mic, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.tts_voice), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Space.sm))
                        }
                    }
                    if (allowImageOcr) {
                        OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.media_from_photo), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Space.sm))
                        }
                    }
                }
                AnimatedVisibility(visible = isOcrRunning) {
                    Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.ui_textactionscreen_279_recognizing_text), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = Space.sm))
                    }
                }
            }

            VervanSectionHeader("2 · Choose an action")
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                actions.forEach { action ->
                    VervanFilterChip(
                        selected = activeLabel == action.label,
                        onClick = { runAction(action) },
                        label = { Text(action.label) },
                        enabled = (inputText.isNotBlank() || !requireInput) && !isRunning
                    )
                }
            }

            VervanSectionHeader("3 · Review result")
            when {
                // Pre-first-token: a spinner is all we can show, but keep the stop control present.
                isRunning && outputText.isBlank() -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(Space.xl),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.ui_textactionscreen_314_generating_with_the_active_model), modifier = Modifier.padding(start = Space.md).weight(1f))
                            OutlinedButton(onClick = { stop() }) {
                                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.sm))
                            }
                        }
                    }
                }
                outputText.isNotBlank() -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.padding(Space.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                activeLabel?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                }
                                if (isRunning) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                }
                            }
                            MarkdownLiteText(outputText, modifier = Modifier.padding(top = Space.sm))
                        }
                    }
                    if (isRunning) {
                        Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { stop() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.sm))
                            }
                        }
                    } else {
                        ResultActions(
                            modifier = Modifier.padding(top = Space.sm),
                            onCopy = { copy(outputText) },
                            onShare = { share(outputText) },
                            onRegenerate = lastAction?.let { action -> { runAction(action) } },
                            onSave = if (allowSaveAsNote) {
                                {
                                    scope.launch {
                                        val note = Note(
                                            title = context.getString(R.string.ui_textactionscreen_saved_title, activeLabel ?: title, java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())),
                                            content = outputText
                                        )
                                        app.container.db.noteDao().upsert(note)
                                        snackbarHostState.showSnackbar("Saved to Notes")
                                    }
                                }
                            } else null,
                            saveLabel = "Save as note"
                        )
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = stringResource(R.string.ui_textactionscreen_370_couldn_t_generate_a_result),
                        message = errorText!!,
                        recovery = stringResource(R.string.ui_textaction_input_recovery),
                        actionLabel = lastAction?.let { "Try again" },
                        onAction = lastAction?.let { action -> { runAction(action) } }
                    )
                }
                else -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = Space.md)) {
                                Text(stringResource(R.string.voice_ready), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Your local result will appear here without replacing the original input.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
      }
    }
}
