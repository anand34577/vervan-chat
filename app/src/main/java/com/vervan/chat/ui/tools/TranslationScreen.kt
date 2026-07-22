package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
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
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
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
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var sourceText by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
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
                    errorText = "No generation model is active. Load one from Models, then translate again."
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
                ImageUtils.fixOrientation(file)
                val text = withContext(Dispatchers.IO) { runCatching { OcrExtractor.extractFromImage(file) }.getOrDefault("") }
                sourceText = text
                isOcrRunning = false
                if (text.isNotBlank()) translate()
            }
        }
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
                title = { Text("Translate") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Column(
              Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)
          ) {
            FeatureHero(
                icon = Icons.Filled.Translate,
                eyebrow = "On-device translation",
                title = "Translate",
                body = "Translate typed or photographed text entirely on your device."
            )
            // Language selector: each side takes an equal weight so long names like
            // "Chinese (Simplified)" truncate instead of overflowing the row on a narrow phone.
            Row(Modifier.fillMaxWidth().padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { sourceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(sourceLang, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        (listOf("Auto-detect") + LANGUAGES).forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { sourceLang = lang; sourceMenuOpen = false })
                        }
                    }
                }
                IconButton(onClick = {
                    if (sourceLang != "Auto-detect") {
                        val prevSource = sourceLang
                        sourceLang = targetLang
                        targetLang = prevSource
                        val prevText = sourceText
                        sourceText = translated
                        translated = prevText
                    }
                }) { Icon(Icons.Filled.SwapHoriz, "Swap languages") }
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
                onValueChange = { sourceText = it },
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                minLines = 4,
                shape = MaterialTheme.shapes.large,
                label = { Text("Text to translate") }
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = !isOcrRunning, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(if (isOcrRunning) " Reading…" else " From photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isTranslating) {
                    OutlinedButton(onClick = { genJob?.cancel(); isTranslating = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Text(" Stop", maxLines = 1)
                    }
                } else {
                    Button(onClick = ::translate, enabled = sourceText.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text("Translate", maxLines = 1)
                    }
                }
            }
            when {
                isTranslating && translated.isBlank() -> {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Translating to $targetLang",
                        body = "Translating with the selected local model.",
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
                translated.isNotBlank() -> {
                    OutlinedTextField(
                        value = translated,
                        onValueChange = { translated = it },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                        minLines = 4,
                        shape = MaterialTheme.shapes.large,
                        label = { Text("Translation") },
                        trailingIcon = {
                            IconButton(onClick = {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Translation", translated))
                                scope.launch { snackbarHostState.showSnackbar("Copied") }
                            }) { Icon(Icons.Filled.ContentCopy, "Copy translation") }
                        }
                    )
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = "Couldn't translate",
                        message = errorText!!,
                        recovery = "Load a model or shorten the text, then try again.",
                        actionLabel = "Try again",
                        onAction = { translate() },
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
            }
          }
        }
      }
    }
}
