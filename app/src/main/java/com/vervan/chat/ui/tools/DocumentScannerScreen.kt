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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
 * Multi-page camera capture -> corner-adjust crop with perspective correction -> export as
 * PDF/images (share sheet, all local) or import as a Knowledge document via OCR.
 * manual draggable corners, no auto edge-detection — that needs a CV library (OpenCV/
 * MLKit) this offline app doesn't bundle. The perspective warp itself is Matrix.setPolyToPoly,
 * pure platform API. Add auto boundary detection if manual corners prove too slow in practice.
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
    // Path currently open in the crop editor: a fresh capture (not yet in `pages`) or an existing
    // page being re-adjusted. thumbVersion invalidates remembered thumbnails after an in-place
    // re-crop, since the path itself doesn't change.
    var cropTarget by remember { mutableStateOf<String?>(null) }
    var thumbVersion by remember { mutableIntStateOf(0) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            ImageUtils.fixOrientation(file)
            cropTarget = file.absolutePath
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingFile = file
            takePicture.launch(uri)
        }
    }

    cropTarget?.let { target ->
        PageCropDialog(
            imagePath = target,
            onDone = {
                if (target !in pages) pages = pages + target
                thumbVersion++
                cropTarget = null
            },
            onCancel = {
                // A fresh capture the user backed out of shouldn't linger on disk.
                if (target !in pages) File(target).delete()
                cropTarget = null
            }
        )
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
                            val bitmap = remember(path, thumbVersion) { ImageUtils.decodeThumbnail(path, 200)?.asImageBitmap() }
                            Card(onClick = { cropTarget = path }, modifier = Modifier.fillMaxSize()) {
                                bitmap?.let { Image(it, "Page — tap to adjust crop", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
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

/**
 * Corner-adjust crop editor with perspective correction, like dedicated scanner apps: drag the
 * four handles onto the document's corners and the page is de-skewed to a flat rectangle via
 * [android.graphics.Matrix.setPolyToPoly] (no CV dependency). Corners live in normalized [0,1]
 * image coordinates so screen size/rotation never invalidates them; the warp itself runs on the
 * full-resolution bitmap at confirm time and overwrites [imagePath] in place.
 */
@Composable
private fun PageCropDialog(imagePath: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    // Display copy only — bounded decode keeps an 8MP+ capture from costing 30MB+ here.
    val displayBitmap = remember(imagePath) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) > 1600 || bounds.outHeight / (sample * 2) > 1600) sample *= 2
        BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    // Default corners at an 8% inset — visibly "a crop" so the affordance is obvious, and close
    // enough to full-page that Full page/small adjustments are both one gesture away.
    val corners = remember(imagePath) {
        mutableStateListOf(
            Offset(0.08f, 0.08f), Offset(0.92f, 0.08f), Offset(0.92f, 0.92f), Offset(0.08f, 0.92f),
        )
    }

    fun confirmCrop() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val full = BitmapFactory.decodeFile(imagePath) ?: return@withContext
                val src = floatArrayOf(
                    corners[0].x * full.width, corners[0].y * full.height,
                    corners[1].x * full.width, corners[1].y * full.height,
                    corners[2].x * full.width, corners[2].y * full.height,
                    corners[3].x * full.width, corners[3].y * full.height,
                )
                fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
                    kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
                val outW = maxOf(dist(src[0], src[1], src[2], src[3]), dist(src[6], src[7], src[4], src[5])).toInt().coerceAtLeast(64)
                val outH = maxOf(dist(src[0], src[1], src[6], src[7]), dist(src[2], src[3], src[4], src[5])).toInt().coerceAtLeast(64)
                val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
                val matrix = android.graphics.Matrix()
                if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                    val out = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(out).drawBitmap(full, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    File(imagePath).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                    out.recycle()
                }
                full.recycle()
            }
            isSaving = false
            onDone()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel crop", tint = Color.White) }
                    Text(
                        "Adjust corners",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        corners[0] = Offset(0f, 0f); corners[1] = Offset(1f, 0f)
                        corners[2] = Offset(1f, 1f); corners[3] = Offset(0f, 1f)
                    }) { Text("Full page") }
                }

                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
                    // The displayed image rect inside this Box (ContentScale.Fit letterboxing) —
                    // needed to map normalized corners <-> screen px and back.
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    val bmpW = displayBitmap?.width?.toFloat() ?: 1f
                    val bmpH = displayBitmap?.height?.toFloat() ?: 1f
                    val scale = minOf(boxSize.width / bmpW, boxSize.height / bmpH).takeIf { it.isFinite() && it > 0 } ?: 1f
                    val drawW = bmpW * scale
                    val drawH = bmpH * scale
                    val offX = (boxSize.width - drawW) / 2f
                    val offY = (boxSize.height - drawH) / 2f
                    fun toScreen(n: Offset) = Offset(offX + n.x * drawW, offY + n.y * drawH)

                    displayBitmap?.let {
                        Image(
                            it.asImageBitmap(), "Captured page",
                            modifier = Modifier.fillMaxSize().onSizeChanged { s -> boxSize = s },
                            contentScale = ContentScale.Fit
                        )
                    }
                    var dragCorner by remember { mutableStateOf(-1) }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(drawW, drawH) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    // Grab the nearest corner within a generous 48dp touch slop.
                                    val slop = 48.dp.toPx()
                                    dragCorner = corners.indices
                                        .minByOrNull { (toScreen(corners[it]) - pos).getDistance() }
                                        ?.takeIf { (toScreen(corners[it]) - pos).getDistance() < slop } ?: -1
                                },
                                onDragEnd = { dragCorner = -1 },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val i = dragCorner
                                    if (i >= 0 && drawW > 0 && drawH > 0) {
                                        corners[i] = Offset(
                                            (corners[i].x + amount.x / drawW).coerceIn(0f, 1f),
                                            (corners[i].y + amount.y / drawH).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            )
                        }
                    ) {
                        val quad = corners.map { toScreen(it) }
                        // Dim everything outside the selection (even-odd: full rect minus quad).
                        val dim = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            moveTo(quad[0].x, quad[0].y)
                            lineTo(quad[1].x, quad[1].y)
                            lineTo(quad[2].x, quad[2].y)
                            lineTo(quad[3].x, quad[3].y)
                            close()
                        }
                        drawPath(dim, Color.Black.copy(alpha = 0.55f))
                        val edge = Path().apply {
                            moveTo(quad[0].x, quad[0].y)
                            lineTo(quad[1].x, quad[1].y)
                            lineTo(quad[2].x, quad[2].y)
                            lineTo(quad[3].x, quad[3].y)
                            close()
                        }
                        drawPath(edge, Color.White, style = Stroke(width = 2.dp.toPx()))
                        quad.forEachIndexed { i, p ->
                            drawCircle(Color.White, radius = if (i == dragCorner) 14.dp.toPx() else 10.dp.toPx(), center = p)
                            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 4.dp.toPx(), center = p)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) { Text("Cancel") }
                    Button(onClick = ::confirmCrop, modifier = Modifier.weight(1f), enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(" Saving…")
                        } else Text("Use this crop")
                    }
                }
            }
        }
    }
}
