package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera/file image in, on-device ML Kit text out (same recognizer [OcrExtractor] already
 * uses for scanned-PDF import) — a standalone utility, not tied to any chat. Result can be
 * copied or imported into Knowledge as a new document for RAG.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(onBack: () -> Unit, onOpenDocument: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var imagePath by remember { mutableStateOf<String?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    fun runOcr(file: File) {
        imagePath = file.absolutePath
        savedMessage = null
        isProcessing = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { OcrExtractor.extractFromImage(file) }.getOrDefault("")
            }
            extractedText = text
            isProcessing = false
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "ocr-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            ImageUtils.fixOrientation(file)
            runOcr(file)
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingCameraFile = file
            takePicture.launch(uri)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val dest = withContext(Dispatchers.IO) {
                    val (file, _) = newImageFile()
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                        ImageUtils.fixOrientation(file)
                        file
                    }.getOrNull()
                }
                if (dest != null) runOcr(dest)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR scanner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                "Extract text from a photo or an image file — runs fully on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.height(18.dp))
                    Text(" Camera")
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.height(18.dp))
                    Text(" From files")
                }
            }
            imagePath?.let { path ->
                val bitmap = remember(path) { ImageUtils.decodeThumbnail(path, 800)?.asImageBitmap() }
                bitmap?.let {
                    Image(
                        it, contentDescription = "Scanned image",
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (isProcessing) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("Recognizing text…")
                }
            } else if (imagePath != null) {
                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    minLines = 6,
                    label = { Text("Recognized text") },
                    placeholder = { Text("No text found") }
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OCR text", extractedText))
                        },
                        enabled = extractedText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.height(18.dp))
                        Text(" Copy")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val kb = KnowledgeBase(name = "Scans")
                                app.container.db.knowledgeBaseDao().upsert(kb)
                                val name = "Scan ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                                val document = app.container.documentImportManager.importRawText(kb.id, name, extractedText)
                                savedMessage = "Saved to Knowledge"
                                onOpenDocument(document.id)
                            }
                        },
                        enabled = extractedText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Description, null, Modifier.height(18.dp))
                        Text(" Save as document")
                    }
                }
                savedMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
