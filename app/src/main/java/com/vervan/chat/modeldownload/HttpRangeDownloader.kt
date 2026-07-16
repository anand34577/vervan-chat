package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelErrorCode
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Validated resume metadata persisted per file — read back on the next resume attempt instead
 * of trusting the live catalogue (per spec: catalogue entries can change across app releases). */
data class ResumeMetadata(
    val downloadedBytes: Long,
    val expectedBytes: Long?,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean?,
    val resolvedUrl: String?
)

internal fun resumeSourceChanged(
    knownEtag: String?,
    knownLastModified: String?,
    receivedEtag: String?,
    receivedLastModified: String?
): Boolean = (knownEtag != null && receivedEtag != knownEtag) ||
    (knownEtag == null && knownLastModified != null && receivedLastModified != knownLastModified)

/**
 * Streams one file to disk over HTTP(S) with real Range-request resume — never loads a file
 * into memory. Redirects are followed manually (rather than via
 * [HttpURLConnection.setInstanceFollowRedirects]) so the Authorization header can be dropped the
 * moment a redirect leaves a trusted host, instead of blindly forwarding a Hugging Face token to
 * whatever CDN/S3 URL a redirect chain ends up at.
 */
class HttpRangeDownloader {

    /** Resumes/starts a download of [sourceUrl] into [dest], appending after [dest]'s existing
     * length when the server honors the Range request. [onProgress] is throttled internally to
     * roughly [PROGRESS_THROTTLE_MS] between calls, per spec — callers must not be driving a DB
     * write on every chunk. Cancelling the calling coroutine closes the stream promptly (checked
     * every chunk, not just at call boundaries). */
    suspend fun download(
        sourceUrl: String,
        dest: File,
        knownEtag: String?,
        knownLastModified: String?,
        authToken: String?,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): ResumeMetadata = withContext(Dispatchers.IO) {
        var startOffset = if (dest.isFile) dest.length() else 0L
        var currentUrl = sourceUrl
        var redirects = 0
        var restartedFromZero = false

        while (true) {
            val forwardAuth = authToken != null && isTrustedHost(currentUrl)
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (startOffset > 0) setRequestProperty("Range", "bytes=$startOffset-")
                if (startOffset > 0 && (knownEtag != null || knownLastModified != null)) {
                    setRequestProperty("If-Range", knownEtag ?: knownLastModified)
                }
                if (forwardAuth) setRequestProperty("Authorization", "Bearer $authToken")
                setRequestProperty("User-Agent", "VervanChat-ModelDownloader/1.0")
            }

            val code = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                connection.disconnect()
                throw ModelDownloadException(ModelErrorCode.NO_NETWORK, e.message ?: "Could not connect to the download source", e)
            }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                    throw ModelDownloadException(ModelErrorCode.REDIRECT_FAILED, "Too many redirects or missing Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }

            try {
                return@withContext handleResponse(
                    connection, dest, startOffset, currentUrl,
                    knownEtag, knownLastModified, onProgress
                )
            } catch (e: ModelDownloadException) {
                // A stale/invalid partial is recoverable: discard it once and retry the same
                // request from byte zero instead of making the user press Retry.
                if (!restartedFromZero && startOffset > 0L &&
                    e.code in setOf(ModelErrorCode.RANGE_NOT_SUPPORTED, ModelErrorCode.SOURCE_CHANGED)
                ) {
                    dest.delete()
                    startOffset = 0L
                    currentUrl = sourceUrl
                    redirects = 0
                    restartedFromZero = true
                    continue
                }
                throw e
            } finally {
                connection.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun handleResponse(
        connection: HttpURLConnection,
        dest: File,
        startOffset: Long,
        resolvedUrl: String,
        knownEtag: String?,
        knownLastModified: String?,
        onProgress: suspend (Long, Long?) -> Unit
    ): ResumeMetadata {
        val code = connection.responseCode
        val etag = connection.getHeaderField("ETag")
        val lastModified = connection.getHeaderField("Last-Modified")
        val acceptRanges = connection.getHeaderField("Accept-Ranges")?.equals("bytes", ignoreCase = true)

        return when (code) {
            HttpURLConnection.HTTP_PARTIAL -> {
                val contentRange = connection.getHeaderField("Content-Range")
                val rangeStart = contentRange?.substringAfter("bytes ")?.substringBefore("-")?.toLongOrNull()
                if (contentRange == null || rangeStart != startOffset) {
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Server ignored the requested byte range")
                }
                if (resumeSourceChanged(knownEtag, knownLastModified, etag, lastModified)) {
                    throw ModelDownloadException(ModelErrorCode.SOURCE_CHANGED, "The source file changed; restarting safely")
                }
                val totalBytes = contentRange.substringAfter("/", "").toLongOrNull()
                val downloaded = streamToFile(connection, dest, append = true, startOffset = startOffset, totalBytes = totalBytes, onProgress = onProgress)
                ResumeMetadata(downloaded, totalBytes, etag, lastModified, true, resolvedUrl)
            }
            HttpURLConnection.HTTP_OK -> {
                // The server ignored our Range request (or none was sent) — it's serving the
                // whole file from byte 0, so any partial content on disk is now invalid.
                if (dest.isFile) dest.delete()
                val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
                val downloaded = streamToFile(connection, dest, append = false, startOffset = 0, totalBytes = totalBytes, onProgress = onProgress)
                ResumeMetadata(downloaded, totalBytes, etag, lastModified, acceptRanges, resolvedUrl)
            }
            416 -> { // HTTP Range Not Satisfiable — no named constant on HttpURLConnection
                val expectedFromHeader = connection.getHeaderField("Content-Range")?.substringAfter("/", "")?.toLongOrNull()
                if (expectedFromHeader != null && dest.isFile && dest.length() == expectedFromHeader) {
                    ResumeMetadata(dest.length(), expectedFromHeader, etag, lastModified, acceptRanges, resolvedUrl)
                } else {
                    dest.delete()
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Requested range not satisfiable; partial file discarded, restart needed")
                }
            }
            HttpURLConnection.HTTP_NOT_FOUND -> throw ModelDownloadException(ModelErrorCode.HTTP_NOT_FOUND, "File not found (404): ${connection.url}")
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                throw ModelDownloadException(ModelErrorCode.AUTHENTICATION_FAILED, "Authentication failed (HTTP $code)")
            in 500..599 -> throw ModelDownloadException(ModelErrorCode.HTTP_SERVER_ERROR, "Server error (HTTP $code)")
            else -> throw ModelDownloadException(ModelErrorCode.UNKNOWN, "Unexpected HTTP $code")
        }
    }

    private suspend fun streamToFile(
        connection: HttpURLConnection,
        dest: File,
        append: Boolean,
        startOffset: Long,
        totalBytes: Long?,
        onProgress: suspend (Long, Long?) -> Unit
    ): Long {
        dest.parentFile?.mkdirs()
        var downloaded = startOffset
        var lastEmit = 0L
        val buffer = ByteArray(1 shl 16)
        val input = try {
            connection.inputStream
        } catch (e: java.io.IOException) {
            throw ModelDownloadException(ModelErrorCode.NO_NETWORK, e.message ?: "Could not open the download stream", e)
        }
        input.use {
            val raf = try {
                RandomAccessFile(dest, "rw")
            } catch (e: java.io.IOException) {
                throw ModelDownloadException(ModelErrorCode.STORAGE_WRITE_FAILED, e.message ?: "Could not open the partial file", e)
            }
            raf.use {
                try {
                    if (append) raf.seek(startOffset) else raf.setLength(0)
                } catch (e: java.io.IOException) {
                    throw ModelDownloadException(ModelErrorCode.STORAGE_WRITE_FAILED, e.message ?: "Could not prepare the partial file", e)
                }
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = try {
                        input.read(buffer)
                    } catch (e: java.io.IOException) {
                        throw ModelDownloadException(ModelErrorCode.NO_NETWORK, e.message ?: "Download connection interrupted", e)
                    }
                    if (read == -1) break
                    try {
                        raf.write(buffer, 0, read)
                    } catch (e: java.io.IOException) {
                        throw ModelDownloadException(ModelErrorCode.STORAGE_WRITE_FAILED, e.message ?: "Could not write the partial file", e)
                    }
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= PROGRESS_THROTTLE_MS) {
                        lastEmit = now
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
        }
        onProgress(downloaded, totalBytes)
        return downloaded
    }

    private fun isTrustedHost(url: String): Boolean {
        val host = runCatching { URL(url).host }.getOrNull()?.lowercase() ?: return false
        return TRUSTED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val PROGRESS_THROTTLE_MS = 750L
        private val TRUSTED_HOST_SUFFIXES = setOf("huggingface.co", "hf.co")
    }
}
