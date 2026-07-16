package com.vervan.chat.ui.tools

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One button in a [TextActionScreen] — [promptFor] turns the current input text into a
 * complete, self-contained one-shot prompt (see [OneShotLlm]). */
data class TextAction(val label: String, val promptFor: (String) -> String)

/**
 * Generic "paste/speak/scan text in, pick a transform, LLM output out" screen — backs Writing
 * Assistant, Smart Notes, Clipboard Assistant, Explain Like I'm, and the text half of Screenshot
 * Intelligence. One implementation instead of five near-identical screens (ponytail: reuse).
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

    var inputText by remember { mutableStateOf(initialText) }
    var outputText by remember { mutableStateOf("") }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    fun runAction(action: TextAction) {
        if (inputText.isBlank() && requireInput) return
        isRunning = true
        activeLabel = action.label
        outputText = ""
        savedMessage = null
        scope.launch {
            outputText = OneShotLlm.run(app, action.promptFor(inputText))?.trim().orEmpty()
            isRunning = false
        }
    }

    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (!text.isNullOrBlank()) inputText = if (inputText.isBlank()) text else "$inputText $text"
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
                ImageUtils.fixOrientation(file)
                val text = withContext(Dispatchers.IO) { runCatching { OcrExtractor.extractFromImage(file) }.getOrDefault("") }
                inputText = if (inputText.isBlank()) text else "$inputText\n$text"
                isOcrRunning = false
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
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
       androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            FeatureHero(
                icon = Icons.Filled.AutoAwesome,
                eyebrow = "On-device assistant",
                title = title,
                body = "Add your material, choose one focused action, then review the result before using it.",
            )
            VervanSectionHeader("1 · Add input")
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text(hint) },
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(title, inputText))
                        }) { Icon(Icons.Filled.ContentCopy, "Copy input text") }
                    }
                }
            )
            if (allowVoice || allowImageOcr) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowVoice) {
                        OutlinedButton(onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Mic, null, Modifier.height(18.dp))
                            Text(" Voice")
                        }
                    }
                    if (allowImageOcr) {
                        OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PhotoCamera, null, Modifier.height(18.dp))
                            Text(" From photo")
                        }
                    }
                }
                if (isOcrRunning) Text("Recognizing text…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
            VervanSectionHeader("2 · Choose an action")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEach { action ->
                    FilterChip(
                        selected = activeLabel == action.label,
                        onClick = { runAction(action) },
                        label = { Text(action.label) },
                        enabled = (inputText.isNotBlank() || !requireInput) && !isRunning
                    )
                }
            }
            VervanSectionHeader("3 · Review result")
            if (isRunning) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                  Row(
                      Modifier.fillMaxWidth().padding(Space.xl),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Generating on this device…")
                  }
                }
            } else if (outputText.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        activeLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        MarkdownLiteText(outputText, modifier = Modifier.padding(top = Space.sm))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(title, outputText))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.height(18.dp))
                        Text(" Copy")
                    }
                    if (allowSaveAsNote) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val note = Note(title = "${activeLabel ?: title} · ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())}", content = outputText)
                                    app.container.db.noteDao().upsert(note)
                                    savedMessage = "Saved to Notes"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, null, Modifier.height(18.dp))
                            Text(" Save as note")
                        }
                    }
                }
                savedMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
            } else if (activeLabel != null) {
                ErrorCard(
                    title = "No response generated",
                    body = "Make sure a compatible generation model is selected and loaded, then try the action again.",
                    modifier = Modifier
                )
            } else {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.fillMaxWidth().padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(24.dp),
                        )
                        Column(Modifier.padding(start = Space.md)) {
                            Text("Ready when you are", style = MaterialTheme.typography.titleSmall)
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
}
