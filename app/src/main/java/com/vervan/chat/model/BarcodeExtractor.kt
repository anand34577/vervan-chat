package com.vervan.chat.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.io.File

/**
 * Decodes QR codes and common 1D/2D barcodes from a still image, and generates them, all
 * on-device via ZXing's dependency-free "core" artifact — the barcode counterpart to
 * [OcrExtractor]. Deliberately not ML Kit: ZXing core is pure Java with no Play Services
 * requirement and no dynamic model download, and the same library covers both decode and
 * encode. A local model has no way to read (or draw) a QR code itself; this is how the chat
 * composer's "Scan QR" attach and the `scan_qr_code`/`generate_barcode` tools give it one.
 */
object BarcodeExtractor {
    private const val MAX_BITMAP_DIMENSION = 2500

    /** Returns every decoded value found in [file], one per line, prefixed with its format
     * (e.g. "QR: https://example.com"). Empty string if nothing was decoded. */
    fun extractFromImage(file: File): String {
        val bitmap = decodeBounded(file) ?: return ""
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        val binaryBitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val reader = GenericMultipleBarcodeReader(MultiFormatReader().apply { setHints(hints) })
        val results = try {
            reader.decodeMultiple(binaryBitmap, hints)
        } catch (e: NotFoundException) {
            return ""
        }
        return results.mapNotNull { result -> result.text?.takeIf(String::isNotBlank)?.let { text -> "${result.barcodeFormat.label()}: $text" } }
            .distinct()
            .joinToString("\n")
    }

    /** Renders [text] as a barcode of [format] into a square-ish PNG at [file]. */
    fun generate(text: String, format: BarcodeFormat, file: File, size: Int = 800) {
        val matrix = MultiFormatWriter().encode(text, format, size, size)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** Human-readable format name — shared by [extractFromImage]'s per-code prefix and the
     * `generate_barcode` tool's result summary. */
    fun BarcodeFormat.label(): String = when (this) {
        BarcodeFormat.QR_CODE -> "QR"
        BarcodeFormat.AZTEC -> "Aztec"
        BarcodeFormat.DATA_MATRIX -> "Data Matrix"
        BarcodeFormat.PDF_417 -> "PDF417"
        BarcodeFormat.CODE_128 -> "Code 128"
        BarcodeFormat.CODE_39 -> "Code 39"
        BarcodeFormat.CODE_93 -> "Code 93"
        BarcodeFormat.CODABAR -> "Codabar"
        BarcodeFormat.EAN_13 -> "EAN-13"
        BarcodeFormat.EAN_8 -> "EAN-8"
        BarcodeFormat.ITF -> "ITF"
        BarcodeFormat.UPC_A -> "UPC-A"
        BarcodeFormat.UPC_E -> "UPC-E"
        else -> "Barcode"
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
