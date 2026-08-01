package com.vervan.chat.ui.knowledge

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Jumps straight to one page of a source PDF instead of opening the whole file in an external
 * viewer and making the user hunt for the cited passage themselves — the payoff of tagging
 * chunks with a page number during import (see [com.vervan.chat.model.Chunker.chunkPaginated]).
 * Uses [android.graphics.pdf.PdfRenderer], the platform's own PDF page rasterizer (API 21+,
 * well under this app's minSdk 26) — no new dependency for what's otherwise a whole PDF
 * rendering library's job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageViewerScreen(documentId: String, initialPage: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp

    var document by remember { mutableStateOf<Document?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    // 0-based, matching PdfRenderer's own page indexing — initialPage (from Chunk.pageNumber) is 1-based.
    var pageIndex by remember { mutableIntStateOf((initialPage - 1).coerceAtLeast(0)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    LaunchedEffect(documentId) {
        document = app.container.db.documentDao().get(documentId)
    }

    DisposableEffect(document?.filePath) {
        val doc = document
        if (doc != null) {
            runCatching {
                val opened = ParcelFileDescriptor.open(File(doc.filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                val opendRenderer = PdfRenderer(opened)
                pfd = opened
                renderer = opendRenderer
                pageCount = opendRenderer.pageCount
                pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            }.onFailure { error = "Could not open PDF: ${it.message}" }
        }
        onDispose {
            renderer?.close()
            pfd?.close()
            renderer = null
            pfd = null
        }
    }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        if (pageIndex !in 0 until r.pageCount) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                r.openPage(pageIndex).use { page ->
                    // 2x the page's native point size — sharp enough to read on a phone screen
                    // without producing an unreasonably large bitmap for a typical letter/A4 page.
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = bmp
                }
            }.onFailure { error = "Could not render page ${pageIndex + 1}: ${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.displayName ?: "PDF", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    error != null -> Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                    bitmap != null -> ZoomableBitmap(bitmap!!)
                    else -> CircularProgressIndicator()
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
                }
                Text(if (pageCount > 0) "Page ${pageIndex + 1} of $pageCount" else "…", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { if (pageIndex < pageCount - 1) pageIndex++ }, enabled = pageIndex < pageCount - 1) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
                }
            }
        }
    }
}

/** Pinch-to-zoom + pan for the rendered page bitmap — a citation's whole point is reading the
 * exact source text, which a fit-to-width static image makes too small on most phones. */
@Composable
private fun ZoomableBitmap(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "PDF page",
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    )
}
