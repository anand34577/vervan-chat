package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese", "Hindi", "Bengali",
    "Chinese (Simplified)", "Japanese", "Korean", "Arabic", "Russian", "Dutch", "Turkish"
)

/** Text translation via the active LLM (a single non-conversational prompt, see [OneShotLlm]) —
 * fully offline, no translation API. Source text can also come from a photo via on-device OCR. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var sourceText by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf("") }
    var sourceLang by remember { mutableStateOf("Auto-detect") }
    var targetLang by remember { mutableStateOf("English") }
    var isTranslating by remember { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var targetMenuOpen by remember { mutableStateOf(false) }

    fun translate() {
        if (sourceText.isBlank()) return
        isTranslating = true
        translated = ""
        scope.launch {
            val langHint = if (sourceLang == "Auto-detect") "" else "The source text is in $sourceLang. "
            val prompt = "Translate the following text to $targetLang. ${langHint}Respond with ONLY the translation, no notes or explanation.\n\nText:\n$sourceText"
            translated = OneShotLlm.run(app, prompt)?.trim().orEmpty()
            isTranslating = false
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "translate-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            scope.launch {
                ImageUtils.fixOrientation(file)
                val text = withContext(Dispatchers.IO) { runCatching { OcrExtractor.extractFromImage(file) }.getOrDefault("") }
                sourceText = text
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { sourceMenuOpen = true }) { Text(sourceLang) }
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
                Box {
                    OutlinedButton(onClick = { targetMenuOpen = true }) { Text(targetLang) }
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
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minLines = 4,
                label = { Text("Text to translate") }
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.height(18.dp))
                    Text(" From photo")
                }
                Button(onClick = ::translate, enabled = sourceText.isNotBlank() && !isTranslating, modifier = Modifier.weight(1f)) {
                    Text("Translate")
                }
            }
            if (isTranslating) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Translating…")
                }
            } else if (translated.isNotBlank()) {
                OutlinedTextField(
                    value = translated,
                    onValueChange = { translated = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    minLines = 4,
                    label = { Text("Translation") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Translation", translated))
                        }) { Icon(Icons.Filled.ContentCopy, "Copy translation") }
                    }
                )
            }
        }
    }
}
