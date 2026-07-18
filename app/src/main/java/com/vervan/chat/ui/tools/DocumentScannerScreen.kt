package com.vervan.chat.ui.tools

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
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
 * Multi-page camera capture -> export as PDF/images (share sheet, all local) or import as a
 * Knowledge document via OCR. ponytail: no auto edge-detection/perspective-crop here — that
 * needs a CV library (e.g. OpenCV) this app doesn't bundle; captures are used as-is. Add real
 * boundary detection if manual framing turns out not to be good enough in practice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(onBack: () -> Unit, onOpenDocument: (String) -> Unit = {}, onProcessAsStudyMaterial: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf(listOf<String>()) }
    var isWorking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "scans").apply { mkdirs() }
        val file = File(dir, "page-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }

    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            ImageUtils.fixOrientation(file)
            pages = pages + file.absolutePath
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingFile = file
            takePicture.launch(uri)
        }
    }

    fun exportPdf() {
        isWorking = true
        statusMessage = null
        scope.launch {
            val pdfFile = withContext(Dispatchers.IO) {
                val doc = PdfDocument()
                pages.forEach { path ->
                    val bitmap = BitmapFactory.decodeFile(path) ?: return@forEach
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, doc.pages.size + 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(page)
                    bitmap.recycle()
                }
                val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                val out = File(dir, "scan-${System.currentTimeMillis()}.pdf")
                out.outputStream().use { doc.writeTo(it) }
                doc.close()
                out
            }
            isWorking = false
            com.vervan.chat.ui.common.openWithExternalApp(context, pdfFile, "application/pdf")
        }
    }

    fun exportImages() {
        pages.forEach { path -> com.vervan.chat.ui.common.openWithExternalApp(context, File(path), "image/jpeg") }
    }

    fun processAsStudyMaterial() {
        isWorking = true
        statusMessage = null
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                pages.joinToString("\n\n") { path -> runCatching { OcrExtractor.extractFromImage(File(path)) }.getOrDefault("") }
            }
            isWorking = false
            onProcessAsStudyMaterial(text)
        }
    }

    fun saveAsDocument() {
        isWorking = true
        statusMessage = null
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                pages.joinToString("\n\n") { path -> runCatching { OcrExtractor.extractFromImage(File(path)) }.getOrDefault("") }
            }
            val kb = KnowledgeBase(name = "Scans")
            app.container.db.knowledgeBaseDao().upsert(kb)
            val name = "Document scan ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            val document = app.container.documentImportManager.importRawText(kb.id, name, text)
            isWorking = false
            statusMessage = "Saved to Knowledge"
            onOpenDocument(document.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document scanner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ToolIntro(
                icon = Icons.Filled.PhotoCamera,
                title = "Scan a complete document",
                body = "Capture pages, review them, then export a PDF or searchable text."
            )
            Text(
                "Capture pages, then export or add them to Knowledge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                Text(" Capture page ${pages.size + 1}")
            }
            if (pages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pages, key = { it }) { path ->
                        Box(Modifier.size(100.dp)) {
                            val bitmap = remember(path) { ImageUtils.decodeThumbnail(path, 200)?.asImageBitmap() }
                            Card(Modifier.fillMaxSize()) {
                                bitmap?.let { Image(it, "Page", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                            IconButton(
                                onClick = { pages = pages.filterNot { it == path } },
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f), CircleShape)
                            ) { Icon(Icons.Filled.Close, "Remove page", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                if (isWorking) {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Processing ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                        body = "Reading and preparing captured pages. Keep this screen open.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ::exportPdf, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
                            Text(" PDF")
                        }
                        OutlinedButton(onClick = ::exportImages, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Text(" Images")
                        }
                    }
                    Button(onClick = ::saveAsDocument, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Filled.Description, null, Modifier.size(18.dp))
                        Text(" Save as document (RAG)")
                    }
                    OutlinedButton(onClick = ::processAsStudyMaterial, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Create study material")
                    }
                }
                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        }
    }
}
