package com.vervan.chat.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.min

/** Keeps camera/gallery images correctly oriented and bounded for on-device inference. */
object ImageUtils {
    private const val MODEL_MAX_DIMENSION = 2048

    /**
     * Decodes with sampling before applying EXIF rotation, then stores a normalized JPEG.
     * This avoids decoding 12â€“50 MP camera images at full size in both Compose and LiteRT.
     */
    fun fixOrientation(file: File) {
        normalizeForModel(file)
    }

    fun normalizeForModel(file: File, maxDimension: Int = MODEL_MAX_DIMENSION): Boolean {
        if (!file.isFile || maxDimension <= 0) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return false

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val oriented = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        val scale = min(1f, maxDimension.toFloat() / maxOf(oriented.width, oriented.height))
        val normalized = if (scale < 1f) {
            Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt(), (oriented.height * scale).toInt(), true)
        } else oriented

        val temp = File(file.parentFile, "${file.name}.tmp")
        val saved = runCatching {
            temp.outputStream().use { normalized.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.getOrDefault(false)
        if (saved) {
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } else {
            temp.delete()
        }

        if (normalized !== oriented) normalized.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return saved
    }

    /** Small preview for the composer; never decodes the inference-sized image on the UI thread. */
    fun decodeThumbnail(path: String, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > sizePx * 2 || bounds.outHeight / sampleSize > sizePx * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
        val scale = min(1f, sizePx.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt(),
            (decoded.height * scale).toInt(),
            true
        ).also { decoded.recycle() }
    }
}
