package com.vervan.chat.ui.tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.theme.Space
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.validation.InputLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Capture via Google ML Kit's own full-screen Document Scanner UI ([GmsDocumentScanning], see
 * [DocumentScannerScreen.startScan]) -> export as PDF/images (share sheet, all local) or import as
 * a Knowledge document via OCR. ML Kit's capture/crop/deskew is trained specifically for this job
 * and returns already-finished page images directly into `pages`, skipping any manual crop step —
 * [PageCropDialog] only comes up if the user taps an already-imported page to re-adjust it.
 *
 * Requires Google Play Services (it dynamically downloads the scanner module/UI on first use);
 * the capture button is disabled with an explanation when that's unavailable.
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

    // Path currently open in the crop editor, for re-adjusting an already-imported page.
    // thumbVersion invalidates remembered thumbnails after an in-place re-crop, since the path
    // itself doesn't change.
    var cropTarget by remember { mutableStateOf<String?>(null) }
    var thumbVersion by remember { mutableIntStateOf(0) }
    var isImportingScan by remember { mutableStateOf(false) }

    // ML Kit's own scanner already crops/deskews each page, so its results land straight in
    // `pages`, skipping [PageCropDialog] entirely.
    fun importGmsScanResult(scanResult: GmsDocumentScanningResult) {
        val allUris = scanResult.pages?.map { it.imageUri } ?: return
        val uris = allUris.take(InputLimits.MAX_DOCUMENT_SCAN_PAGES)
        if (uris.isEmpty()) return
        isImportingScan = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "scans").apply { mkdirs() }
                var failures = 0
                uris.mapNotNull { uri ->
                    val file = File(dir, "page-${System.currentTimeMillis()}-${uri.hashCode()}.jpg")
                    val ok = runCatching {
                        (context.contentResolver.openInputStream(uri) ?: error("Couldn't open scan page")).use { input ->
                            file.outputStream().use { output -> input.copyToLimited(output, ImportLimits.MAX_IMAGE_SOURCE_BYTES) }
                        }
                        require(file.length() > 0) { "Scan page is empty" }
                        require(ImageUtils.normalizeForModel(file)) { "Scan page could not be decoded" }
                    }.isSuccess
                    if (!ok) { failures++; file.delete(); null } else file.absolutePath
                } to failures
            }
            isImportingScan = false
            pages = pages + result.first
            statusMessage = when {
                result.first.isEmpty() -> "Couldn't import the scan pages. Try scanning again."
                result.second > 0 -> "Imported ${result.first.size} page(s); ${result.second} page(s) were rejected because they could not be read or were too large."
                allUris.size > uris.size -> "Imported the first ${InputLimits.MAX_DOCUMENT_SCAN_PAGES} pages."
                else -> null
            }
        }
    }

    val gmsScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let(::importGmsScanResult)
        }
    }

    // Google Play services is required for GmsDocumentScanning (it dynamically downloads the
    // scanner module/UI from Play services on first use) — absent on GMS-less devices/ROMs, the
    // capture button below is disabled with an explanation instead of silently failing.
    val gmsAvailable = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    fun startScan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                gmsScanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // Play services reported available but the scanner module itself failed to start
                // (e.g. first-run module download failed offline).
                statusMessage = "Couldn't start the scanner — check your connection and try again."
            }
    }

    // cropTarget is only ever set from an already-imported page's thumbnail (see the Card
    // onClick below) — every target here is always already in `pages`, so re-adjusting a crop
    // never needs to add or delete a page, only re-save the file in place.
    cropTarget?.let { target ->
        PageCropDialog(
            imagePath = target,
            onDone = {
                thumbVersion++
                cropTarget = null
            },
            onCancel = { cropTarget = null }
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
                com.vervan.chat.system.pruneOldExports(dir)
                out
            }
            isWorking = false
            com.vervan.chat.ui.common.shareWithExternalApps(context, listOf(pdfFile), "application/pdf")
        }
    }

    fun exportImages() {
        com.vervan.chat.ui.common.shareWithExternalApps(context, pages.map(::File), "image/jpeg")
    }

    // Extracts OCR text for every captured page, joined for downstream use. Returns null (with
    // statusMessage set to an explanation) when text recognition found nothing on any page —
    // callers must not silently save/process an empty result as if it succeeded.
    suspend fun extractPagesTextOrNull(): String? {
        val text = try {
            withContext(Dispatchers.IO) {
                pages.map { path -> OcrExtractor.extractFromImage(File(path)) }.joinToString("\n\n")
            }
        } catch (t: Throwable) {
            statusMessage = "Couldn't read scan text: ${t.toUserMessage()}"
            return null
        }
        if (text.length > com.vervan.chat.ui.common.ValidationLimits.OCR_TEXT) {
            statusMessage = "OCR text is too long to process (maximum ${com.vervan.chat.ui.common.ValidationLimits.OCR_TEXT} characters)."
            return null
        }
        if (text.isBlank()) {
            statusMessage = "No text could be recognized in these pages. Try clearer, evenly lit captures."
            return null
        }
        return text
    }

    fun processAsStudyMaterial() {
        isWorking = true
        statusMessage = null
        scope.launch {
            val text = extractPagesTextOrNull()
            isWorking = false
            if (text != null) onProcessAsStudyMaterial(text)
        }
    }

    fun saveAsDocument() {
        isWorking = true
        statusMessage = null
        scope.launch {
            val text = extractPagesTextOrNull()
            if (text == null) {
                isWorking = false
                return@launch
            }
            val kb = app.container.db.knowledgeBaseDao().get(KnowledgeBase.SCANS_KNOWLEDGE_BASE_ID)
                ?: KnowledgeBase(id = KnowledgeBase.SCANS_KNOWLEDGE_BASE_ID, name = "Scans").also {
                    app.container.db.knowledgeBaseDao().upsert(it)
                }
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
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            ToolIntro(
                icon = Icons.Filled.PhotoCamera,
                title = "Scan a complete document",
                body = "Capture pages, review them, then export a PDF or searchable text."
            )
            Text(
                "Capture pages, then export or add them to Knowledge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm)
            )
            OutlinedButton(
                onClick = ::startScan,
                enabled = gmsAvailable && !isImportingScan,
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
            ) {
                if (isImportingScan) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Importing…", modifier = Modifier.padding(start = Space.sm))
                } else {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(
                        if (pages.isEmpty()) "Capture first page" else "Capture page ${pages.size + 1}",
                        modifier = Modifier.padding(start = Space.sm)
                    )
                }
            }
            if (!gmsAvailable) {
                Text(
                    "Document scanning needs Google Play services, which isn't available on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            if (pages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(pages, key = { it }) { path ->
                        Box(Modifier.size(100.dp)) {
                            val bitmap = rememberThumbnail(path, 200, invalidationKey = thumbVersion)
                            Card(onClick = { cropTarget = path }, modifier = Modifier.fillMaxSize()) {
                                bitmap?.let { Image(it, "Page — tap to adjust crop", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                            IconButton(
                                onClick = {
                                    pages = pages.filterNot { it == path }
                                    // Pages only ever get here via a copy into filesDir/scans (see
                                    // importGmsScanResult) — nothing else in the app references this
                                    // path (OCR/export read it in place, they don't persist it), so a
                                    // discarded page is safe to delete outright instead of leaking it.
                                    runCatching { File(path).delete() }
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f), MaterialTheme.shapes.small)
                            ) { Icon(Icons.Filled.Close, "Remove page", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                if (isWorking) {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Processing ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                        body = "Reading and preparing captured pages. Keep this screen open.",
                        modifier = Modifier.padding(top = Space.lg)
                    )
                } else {
                    ResponsiveActions(Modifier.padding(top = Space.lg)) {
                        OutlinedButton(onClick = ::exportPdf) {
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
                            Text("PDF", modifier = Modifier.padding(start = Space.sm))
                        }
                        OutlinedButton(onClick = ::exportImages) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Text("Images", modifier = Modifier.padding(start = Space.sm))
                        }
                    }
                    Button(onClick = ::saveAsDocument, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                        Icon(Icons.Filled.Description, null, Modifier.size(18.dp))
                        Text("Save as document (RAG)", modifier = Modifier.padding(start = Space.sm))
                    }
                    OutlinedButton(onClick = ::processAsStudyMaterial, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                        Text("Create study material")
                    }
                }
                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.sm))
                }
            }
        }
    }
}

/**
 * Corner-adjust crop editor with perspective correction, like dedicated scanner apps: drag the
 * four handles onto the document's corners and the page is de-skewed to a flat rectangle via
 * [android.graphics.Matrix.setPolyToPoly] (pure platform API). Corners live in normalized [0,1]
 * image coordinates so screen size/rotation never invalidates them; the warp itself runs on the
 * full-resolution bitmap at confirm time and overwrites [imagePath] in place. Only reachable by
 * tapping an already-imported page to re-adjust it — ML Kit itself already crops/deskews on
 * capture, so this always starts from a fixed inset rather than a detected outline.
 */
@Composable
private fun PageCropDialog(imagePath: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    // Display copy only — bounded decode keeps an 8MP+ capture from costing 30MB+ here. Decoded
    // on Dispatchers.IO via produceState, not inline in remember{} — a synchronous decode here
    // (the previous pattern) runs on the main thread during composition, i.e. exactly when this
    // dialog is opening, which is precisely the moment jank is most visible to the user.
    val displayBitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) > 1600 || bounds.outHeight / (sample * 2) > 1600) sample *= 2
            BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    // An 8% inset — visibly "a crop" so the affordance is obvious, and close enough to full-page
    // that Full page/small adjustments are both one gesture away.
    val corners = remember(imagePath) {
        mutableStateListOf(Offset(0.08f, 0.08f), Offset(0.92f, 0.08f), Offset(0.92f, 0.92f), Offset(0.08f, 0.92f))
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
                    // The page-list thumbnail is keyed on this same path — without this it would
                    // keep rendering the pre-crop image after saving (see ImageUtils.invalidateThumbnail).
                    ImageUtils.invalidateThumbnail(imagePath)
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
                    Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel crop", tint = Color.White) }
                    Text(
                        "Adjust corners",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    com.vervan.chat.ui.common.VervanTextButton(onClick = {
                        corners[0] = Offset(0f, 0f); corners[1] = Offset(1f, 0f)
                        corners[2] = Offset(1f, 1f); corners[3] = Offset(0f, 1f)
                    }) { Text("Full page") }
                }

                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.lg)) {
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
                    Modifier.fillMaxWidth().padding(Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) { Text("Cancel") }
                    Button(onClick = ::confirmCrop, modifier = Modifier.weight(1f), enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Saving…", modifier = Modifier.padding(start = Space.sm))
                        } else Text("Use this crop")
                    }
                }
            }
        }
    }
}
