package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ResultActions
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.copyText
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese", "Hindi", "Bengali",
    "Chinese (Simplified)", "Japanese", "Korean", "Arabic", "Russian", "Dutch", "Turkish"
)

/** Text translation via the active LLM (a single non-conversational prompt, see [OneShotLlm]) —
 * fully offline, no translation API. Source text can also come from a photo via on-device OCR.
 * The translation streams in and a model-load failure surfaces in-line rather than crashing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var sourceText by rememberSaveable { mutableStateOf("") }
    var translated by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var sourceLang by remember { mutableStateOf("Auto-detect") }
    var targetLang by remember { mutableStateOf("English") }
    var isTranslating by remember { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var targetMenuOpen by remember { mutableStateOf(false) }
    var genJob by remember { mutableStateOf<Job?>(null) }

    fun translate() {
        if (sourceText.isBlank()) return
        genJob?.cancel()
        isTranslating = true
        translated = ""
        errorText = null
        genJob = scope.launch {
            try {
                val langHint = if (sourceLang == "Auto-detect") "" else "The source text is in $sourceLang. "
                val prompt = "Translate the following text to $targetLang. ${langHint}Respond with ONLY the translation, no notes or explanation.\n\nText:\n$sourceText"
                val flow = OneShotLlm.stream(
                    app, prompt,
                    runContext = com.vervan.chat.llm.ToolRunContext("tools/translate", "Translate · $targetLang", sourceText),
                )
                if (flow == null) {
                    errorText = "No model is ready. Open Settings → AI models, load one, then translate again."
                } else {
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    flow.collect {
                        sb.append(it)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60) { translated = sb.toString().trim(); lastEmit = now }
                    }
                    translated = sb.toString().trim()
                    if (translated.isBlank()) errorText = "The model returned an empty translation. Try again."
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isTranslating = false
            }
        }
    }

    fun copyTranslation() {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .copyText(translated, scope, "Translation")
        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_copied)) }
    }

    fun shareTranslation() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Translation to $targetLang")
            putExtra(android.content.Intent.EXTRA_TEXT, translated)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share translation"))
    }

    fun saveTranslation() {
        scope.launch {
            val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                .format(java.util.Date())
            app.container.db.noteDao().upsert(
                Note(title = "Translation · $targetLang · $date", content = translated)
            )
            snackbarHostState.showSnackbar(localResources.getString(R.string.translation_saved_to_notes))
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "translate-${System.currentTimeMillis()}.jpg")
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
                try {
                    if (file.length() > ImportLimits.MAX_IMAGE_SOURCE_BYTES || !ImageUtils.normalizeForModel(file)) {
                        errorText = "The captured image is too large or unreadable."
                        return@launch
                    }
                // Only the extracted text is kept — the copied JPEG has no further use once OCR
                // has read it, and this screen never displays it, so it's deleted immediately
                // instead of leaking into filesDir/images (same pattern as
                // FlashcardsFromPhotoScreen.runOcr).
                    val text = withContext(Dispatchers.IO) {
                        OcrExtractor.extractFromImage(file)
                    }
                    sourceText = text.take(InputLimits.TRANSLATION_TEXT_CHARS)
                    if (text.isNotBlank()) translate()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    errorText = t.toUserMessage()
                } finally {
                    file.delete()
                    isOcrRunning = false
                }
            }
        } else file?.delete()
    }
    val requestCameraPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingFile = file
            takePicture.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_reply_translate)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.Translate,
                eyebrow = "On-device translation",
                // The app bar already names this screen.
                title = null,
                dismissKey = stringResource(R.string.quick_reply_translate),
                body = stringResource(R.string.translation_translate_typed_or_photographed_text)
            )
            // Language selector: each side takes an equal weight so long names like
            // "Chinese (Simplified)" truncate instead of overflowing the row on a narrow phone.
            Row(Modifier.fillMaxWidth().padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { sourceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        // "Auto-detect" is the stored sentinel (also checked when building the
                        // prompt); only its displayed label is translated.
                        Text(if (sourceLang == "Auto-detect") stringResource(R.string.translation_auto_detect) else sourceLang, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        (listOf("Auto-detect") + LANGUAGES).forEach { lang ->
                            DropdownMenuItem(text = { Text(if (lang == "Auto-detect") stringResource(R.string.translation_auto_detect) else lang) }, onClick = { sourceLang = lang; sourceMenuOpen = false })
                        }
                    }
                }
                // Swapping needs a known source language to become the new target.
                IconButton(enabled = sourceLang != "Auto-detect" && !isTranslating, onClick = {
                    if (sourceLang != "Auto-detect") {
                        val prevSource = sourceLang
                        sourceLang = targetLang
                        targetLang = prevSource
                        val prevText = sourceText
                        sourceText = translated
                        translated = prevText
                    }
                }) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.translation_swap_languages)) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { targetMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetLang, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = targetMenuOpen, onDismissRequest = { targetMenuOpen = false }) {
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { targetLang = lang; targetMenuOpen = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it.take(InputLimits.TRANSLATION_TEXT_CHARS) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                minLines = 4,
                enabled = !isTranslating,
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.translation_text_to_translate)) },
                supportingText = { ToolCharCount(sourceText.length, InputLimits.TRANSLATION_TEXT_CHARS) },
                trailingIcon = if (sourceText.isNotEmpty() && !isTranslating) {
                    { IconButton(onClick = { sourceText = ""; translated = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.tool_clear_input)) } }
                } else null
            )
            ToolSourceRow {
                OutlinedButton(
                    onClick = {
                        val clip = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (text.isBlank()) scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.tool_clipboard_empty)) }
                        else sourceText = text.take(InputLimits.TRANSLATION_TEXT_CHARS)
                    },
                    enabled = !isTranslating && !isOcrRunning, modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tool_paste), maxLines = 1, modifier = Modifier.padding(start = Space.xs))
                }
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = !isOcrRunning && !isTranslating, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(
                        if (isOcrRunning) stringResource(R.string.media_reading) else stringResource(R.string.media_from_photo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Space.xs)
                    )
                }
            }
            ToolPrimaryButton(
                label = stringResource(R.string.translation_translate_to, targetLang),
                icon = Icons.Filled.Translate,
                onClick = ::translate,
                blockedReason = when {
                    isOcrRunning -> stringResource(R.string.text_action_recognizing_text)
                    sourceText.isBlank() -> stringResource(R.string.tool_blocked_add_text)
                    sourceLang == targetLang -> stringResource(R.string.translation_same_language)
                    else -> null
                },
                running = isTranslating,
                onStop = { genJob?.cancel(); isTranslating = false }
            )
            when {
                isTranslating && translated.isBlank() -> {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = stringResource(R.string.translation_translating_to_arg1, targetLang),
                        body = stringResource(R.string.translation_translating_with_the_selected_local),
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
                translated.isNotBlank() -> {
                    OutlinedTextField(
                        value = translated,
                        onValueChange = { translated = it.take(InputLimits.TRANSLATION_TEXT_CHARS) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                        minLines = 4,
                        shape = MaterialTheme.shapes.large,
                        label = { Text(stringResource(R.string.translation_translation)) }
                    )
                    if (!isTranslating) {
                        ResultActions(
                            modifier = Modifier.padding(top = Space.sm),
                            onCopy = ::copyTranslation,
                            onShare = ::shareTranslation,
                            onSave = ::saveTranslation,
                            saveLabel = "Save as note"
                        )
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = stringResource(R.string.translation_couldn_t_translate),
                        message = errorText!!,
                        recovery = stringResource(R.string.translation_load_a_model_or_shorten),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = { translate() },
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
            }
        }
    }
}
