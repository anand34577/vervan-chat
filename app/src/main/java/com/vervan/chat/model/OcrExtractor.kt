package com.vervan.chat.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Renders each page of a scanned (text-layer-less) PDF to a bitmap and runs on-device ML Kit
 * text recognition. Uses the bundled `com.google.mlkit:text-recognition`
 * model, which ships inside the APK — no network fetch, unlike the Play-Services-backed
 * variant of the same API. Only called from [DocumentImportManager] when PDFBox's text layer
 * comes back empty.
 */
object OcrExtractor {
    private const val RENDER_DPI = 200
    private const val PDF_POINTS_PER_INCH = 72f
    private const val MAX_BITMAP_DIMENSION = 2500

    fun extractFromPdf(file: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pages = StringBuilder()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val targetScale = RENDER_DPI / PDF_POINTS_PER_INCH
                            val maxScale = minOf(
                                MAX_BITMAP_DIMENSION.toFloat() / page.width.coerceAtLeast(1),
                                MAX_BITMAP_DIMENSION.toFloat() / page.height.coerceAtLeast(1)
                            )
                            val scale = minOf(targetScale, maxScale).coerceAtLeast(0.1f)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                            if (result.text.isNotBlank()) pages.append(result.text).append("\n\n")
                            bitmap.recycle()
                        }
                    }
                    return pages.toString().trim()
                }
            }
        } finally {
            recognizer.close()
        }
    }

    /** Runs on-device OCR over a single image file —
     * the same recognizer as [extractFromPdf], no page-rendering step needed. */
    fun extractFromImage(file: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val bitmap = decodeBounded(file) ?: return ""
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            bitmap.recycle()
            return result.text.trim()
        } finally {
            recognizer.close()
        }
    }

    private fun decodeBounded(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_BITMAP_DIMENSION || bounds.outHeight / sample > MAX_BITMAP_DIMENSION) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
