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

internal fun exceedsDownloadLimit(downloadedBytes: Long, incomingBytes: Int, maxBytes: Long?): Boolean =
    maxBytes != null && (downloadedBytes > maxBytes || incomingBytes.toLong() > maxBytes - downloadedBytes)

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
        maxBytes: Long? = null,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): ResumeMetadata = withContext(Dispatchers.IO) {
        var startOffset = if (dest.isFile) dest.length() else 0L
        if (maxBytes != null && startOffset > maxBytes) {
            dest.delete()
            throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "Partial file exceeds the expected size")
        }
        var currentUrl = sourceUrl
        var redirects = 0
        var restartedFromZero = false

        while (true) {
            val parsedUrl = try {
                URL(currentUrl)
            } catch (e: java.net.MalformedURLException) {
                throw ModelDownloadException(ModelErrorCode.REDIRECT_FAILED, "Download URL is invalid", e)
            }
            if (parsedUrl.protocol.lowercase(java.util.Locale.ROOT) !in setOf("http", "https")) {
                throw ModelDownloadException(ModelErrorCode.REDIRECT_FAILED, "Download URL must use HTTP or HTTPS")
            }
            val forwardAuth = authToken != null && isTrustedHost(currentUrl)
            val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (startOffset > 0) setRequestProperty("Range", "bytes=$startOffset-")
                if (startOffset > 0 && (knownEtag != null || knownLastModified != null)) {
                    setRequestProperty("If-Range", knownEtag ?: knownLastModified)
                }
                // Byte offsets are defined over the stored representation. Transparent content
                // encoding makes Range resume ambiguous and can append decompressed bytes at a
                // compressed offset, corrupting the file without an obvious transport error.
                setRequestProperty("Accept-Encoding", "identity")
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
                currentUrl = URL(parsedUrl, location).toString()
                redirects++
                continue
            }

            try {
                return@withContext handleResponse(
                    connection, dest, startOffset, currentUrl,
                    knownEtag, knownLastModified, maxBytes, onProgress
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
        maxBytes: Long?,
        onProgress: suspend (Long, Long?) -> Unit
    ): ResumeMetadata {
        val code = connection.responseCode
        val etag = connection.getHeaderField("ETag")
        val lastModified = connection.getHeaderField("Last-Modified")
        val acceptRanges = connection.getHeaderField("Accept-Ranges")?.equals("bytes", ignoreCase = true)

        return when (code) {
            HttpURLConnection.HTTP_PARTIAL -> {
                val contentRange = parseContentRange(connection.getHeaderField("Content-Range"))
                if (contentRange == null || contentRange.start != startOffset) {
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Server ignored the requested byte range")
                }
                if (contentRange.end < contentRange.start || contentRange.end == Long.MAX_VALUE ||
                    (contentRange.total != null && contentRange.end >= contentRange.total)
                ) {
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Server returned an invalid Content-Range")
                }
                val responseBytes = contentRange.end - contentRange.start + 1
                val declaredResponseBytes = connection.contentLengthLong.takeIf { it >= 0 }
                if (declaredResponseBytes != null && declaredResponseBytes != responseBytes) {
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Server returned an inconsistent byte range")
                }
                if (resumeSourceChanged(knownEtag, knownLastModified, etag, lastModified)) {
                    throw ModelDownloadException(ModelErrorCode.SOURCE_CHANGED, "The source file changed; restarting safely")
                }
                val totalBytes = contentRange.total
                rejectOversizedResponse(totalBytes, maxBytes)
                val downloaded = streamToFile(
                    connection, dest, append = true, startOffset = startOffset,
                    totalBytes = totalBytes, maxBytes = tighterLimit(maxBytes, totalBytes),
                    onProgress = onProgress
                )
                if (downloaded != startOffset + responseBytes ||
                    (totalBytes != null && downloaded != totalBytes)
                ) {
                    throw ModelDownloadException(ModelErrorCode.NO_NETWORK, "Download ended before the complete file was received")
                }
                ResumeMetadata(downloaded, totalBytes, etag, lastModified, true, resolvedUrl)
            }
            HttpURLConnection.HTTP_OK -> {
                // The server ignored our Range request (or none was sent) — it's serving the
                // whole file from byte 0, so any partial content on disk is now invalid.
                if (dest.isFile) dest.delete()
                val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
                rejectOversizedResponse(totalBytes, maxBytes)
                val downloaded = streamToFile(
                    connection, dest, append = false, startOffset = 0, totalBytes = totalBytes,
                    maxBytes = tighterLimit(maxBytes, totalBytes), onProgress = onProgress
                )
                if (totalBytes != null && downloaded != totalBytes) {
                    throw ModelDownloadException(ModelErrorCode.NO_NETWORK, "Download ended before the complete file was received")
                }
                ResumeMetadata(downloaded, totalBytes, etag, lastModified, acceptRanges, resolvedUrl)
            }
            416 -> { // HTTP Range Not Satisfiable — no named constant on HttpURLConnection
                val expectedFromHeader = parseUnsatisfiedContentRange(connection.getHeaderField("Content-Range"))
                if (resumeSourceChanged(knownEtag, knownLastModified, etag, lastModified)) {
                    throw ModelDownloadException(ModelErrorCode.SOURCE_CHANGED, "The source file changed; restarting safely")
                }
                rejectOversizedResponse(expectedFromHeader, maxBytes)
                if (expectedFromHeader != null && dest.isFile && dest.length() == expectedFromHeader) {
                    ResumeMetadata(dest.length(), expectedFromHeader, etag, lastModified, acceptRanges, resolvedUrl)
                } else {
                    dest.delete()
                    throw ModelDownloadException(ModelErrorCode.RANGE_NOT_SUPPORTED, "Requested range not satisfiable; partial file discarded, restart needed")
                }
            }
            // 410 Gone is grouped with 404 on purpose: for a catalogue pinned to an immutable
            // commit SHA it means the revision was withdrawn, which is exactly as permanent as a
            // 404 and must not be retried with backoff. Left in the generic `else` branch it would
            // surface as UNKNOWN and be treated as transient.
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE ->
                throw ModelDownloadException(ModelErrorCode.HTTP_NOT_FOUND, "File not found (HTTP $code): ${connection.url}")
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
        maxBytes: Long?,
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
                    if (exceedsDownloadLimit(downloaded, read, maxBytes)) {
                        raf.setLength(downloaded)
                        throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "Download exceeds the expected size")
                    }
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

    private fun rejectOversizedResponse(totalBytes: Long?, maxBytes: Long?) {
        if (totalBytes != null && maxBytes != null && totalBytes > maxBytes) {
            throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "Remote file exceeds the expected size")
        }
    }

    private fun tighterLimit(configured: Long?, advertised: Long?): Long? = when {
        configured == null -> advertised
        advertised == null -> configured
        else -> minOf(configured, advertised)
    }

    private data class ContentRange(val start: Long, val end: Long, val total: Long?)

    private fun parseContentRange(value: String?): ContentRange? {
        val match = value?.trim()?.let(CONTENT_RANGE::matchEntire) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        return ContentRange(start, end, total)
    }

    private fun parseUnsatisfiedContentRange(value: String?): Long? =
        value?.trim()?.let(UNSATISFIED_CONTENT_RANGE::matchEntire)
            ?.groupValues?.get(1)?.toLongOrNull()

    private fun isTrustedHost(url: String): Boolean {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return false
        // Host allowlisting alone is insufficient: forwarding a bearer token to an HTTP URL on
        // an otherwise trusted host would expose it to the network in plaintext after a downgrade.
        if (!parsed.protocol.equals("https", ignoreCase = true)) return false
        val host = parsed.host.lowercase(java.util.Locale.ROOT)
        return host in TRUSTED_AUTH_HOSTS
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val PROGRESS_THROTTLE_MS = 750L
        private val CONTENT_RANGE = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
        private val UNSATISFIED_CONTENT_RANGE = Regex("bytes\\s+\\*/(\\d+)", RegexOption.IGNORE_CASE)
        private val TRUSTED_AUTH_HOSTS = setOf("huggingface.co", "hf.co")
    }
}
