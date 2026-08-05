package com.vervan.chat.ui.common

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** First-page preview data for document attachments. Non-PDF files keep the metadata path but
 * intentionally fall back to the document icon because Android has no safe, universal renderer
 * for every file type a user can pick. */
data class DocumentFirstPagePreview(
    val bitmap: ImageBitmap? = null,
    val pageCount: Int? = null,
)

/** Renders only page one off the main thread so document selection and chat scrolling stay smooth. */
@Composable
fun rememberDocumentFirstPagePreview(
    uri: Uri? = null,
    filePath: String? = null,
    mimeType: String? = null,
    sizePx: Int,
): DocumentFirstPagePreview {
    val resolver = LocalContext.current.contentResolver
    return produceState(
        initialValue = DocumentFirstPagePreview(),
        uri,
        filePath,
        mimeType,
        sizePx
    ) {
        value = withContext(Dispatchers.IO) {
            if (!isPdf(uri, filePath, mimeType)) return@withContext DocumentFirstPagePreview()
            runCatching {
                val descriptor = when {
                    uri != null -> resolver.openFileDescriptor(uri, "r")
                    !filePath.isNullOrBlank() -> ParcelFileDescriptor.open(
                        File(filePath),
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    else -> null
                } ?: return@runCatching DocumentFirstPagePreview()

                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0 || sizePx <= 0) {
                            return@use DocumentFirstPagePreview(pageCount = renderer.pageCount)
                        }
                        renderer.openPage(0).use { page ->
                            val width = sizePx.coerceAtLeast(240)
                            val height = (width.toFloat() * page.height / page.width)
                                .roundToInt()
                                .coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            DocumentFirstPagePreview(bitmap.asImageBitmap(), renderer.pageCount)
                        }
                    }
                }
            }.getOrDefault(DocumentFirstPagePreview())
        }
    }.value
}

private fun isPdf(uri: Uri?, filePath: String?, mimeType: String?): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) ||
        uri?.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true ||
        filePath?.endsWith(".pdf", ignoreCase = true) == true
