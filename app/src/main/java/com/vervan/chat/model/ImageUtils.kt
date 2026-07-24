package com.vervan.chat.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.min

/** Keeps camera/gallery images correctly oriented and bounded for on-device inference. */
object ImageUtils {
    private const val MODEL_MAX_DIMENSION = 2048

    /**
     * Decodes with sampling before applying EXIF rotation, then stores a normalized JPEG.
     * This avoids decoding 12–50 MP camera images at full size in both Compose and LiteRT.
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
        var saved = runCatching {
            temp.outputStream().use { normalized.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.getOrDefault(false)
        if (saved) {
            if (!temp.renameTo(file)) {
                // copyTo() used to run unguarded — a storage-full mid-copy threw straight out
                // of this function, and temp.delete() below never ran, leaving a stray .tmp file.
                saved = runCatching { temp.copyTo(file, overwrite = true) }.isSuccess
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

    /**
     * Decodes an arbitrary content [uri] (a gallery/camera pick), applies EXIF orientation, scales
     * the longest edge down to [maxDimension], and writes a PNG to [dest]. The same orient-then-
     * bound treatment as [normalizeForModel], but reading from a Uri and writing PNG into a caller-
     * chosen destination — used by the persona avatar picker. Returns false if the Uri isn't a
     * decodable image so the caller surfaces a friendly error instead of a half-written file.
     */
    fun copyNormalizedPng(context: Context, uri: Uri, dest: File, maxDimension: Int = MODEL_MAX_DIMENSION): Boolean {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return false

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return false

        val orientation = runCatching {
            ByteArrayInputStream(bytes).use { ins ->
                ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
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

        dest.parentFile?.mkdirs()
        val ok = runCatching {
            dest.outputStream().use { normalized.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        if (normalized !== oriented) normalized.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return ok
    }

    /** Small preview for the composer; never decodes the inference-sized image on the UI thread.
     *  Results are memoized in a small process-wide LRU cache keyed by (path, sizePx) so that
     *  navigating away from and back into a chat (or opening the same image from ChatInfo after
     *  viewing it inline) doesn't re-decode the same file. The cache is sized by bitmap byte
     *  count, not entry count, so a few large previews naturally evict smaller ones. */
    fun decodeThumbnail(path: String, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val cacheKey = "$path@$sizePx"
        thumbnailCache.get(cacheKey)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > sizePx * 2 || bounds.outHeight / sampleSize > sizePx * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
        val scale = min(1f, sizePx.toFloat() / maxOf(decoded.width, decoded.height))
        val result = if (scale >= 1f) decoded else {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt(),
                (decoded.height * scale).toInt(),
                true
            ).also { decoded.recycle() }
        }
        thumbnailCache.put(cacheKey, result)
        return result
    }

    private val thumbnailCache: android.util.LruCache<String, Bitmap> =
        // Cap at ~12MB — enough for a handful of 200–1200px ARGB_8888 previews without
        // pressuring a low-end device. Bitmap.getByteCount() drives eviction.
        object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
}
