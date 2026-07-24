package com.vervan.chat.store.install

import com.vervan.chat.data.db.entities.ModelErrorCode
import com.vervan.chat.modeldownload.HttpRangeDownloader
import com.vervan.chat.modeldownload.ModelDownloadException
import com.vervan.chat.store.model.ArtifactSource
import com.vervan.chat.store.model.SourceProvider
import com.vervan.chat.system.NetworkAuditLog
import java.io.File

/**
 * Production [ArtifactFetcher], built on the app's existing [HttpRangeDownloader].
 *
 * Reusing that downloader rather than writing a second one is the whole point: it already handles
 * the hard parts correctly — manual redirect following that drops the Authorization header the
 * moment a redirect leaves a trusted host, `If-Range` revalidation so a changed remote file
 * discards the partial instead of appending to it, and a one-shot restart-from-zero when a server
 * ignores a range request. A parallel implementation would be a second place for those to be got
 * wrong.
 *
 * This class contributes exactly two things on top: the error classification the store's failover
 * logic needs, and the auth policy.
 */
class HttpArtifactFetcher(
    private val downloader: HttpRangeDownloader,
    private val networkAuditLog: NetworkAuditLog,
    /** Resolves the user's own Hugging Face token, when they have supplied one. Never an
     * app-owned/shared token — spec §9 and §14 forbid embedding one, because it would let this app
     * act as a shared credential for gated repos and bypass the rightsholder's approval. */
    private val userHuggingFaceToken: suspend () -> String? = { null }
) : ArtifactFetcher {

    override suspend fun fetch(
        source: ArtifactSource,
        dest: File,
        expectedBytes: Long,
        onProgress: suspend (Long) -> Unit,
        knownEtag: String?,
        knownLastModified: String?
    ): FetchMetadata {
        val url = source.toUrl()
        networkAuditLog.record("Model store: downloading ${source.path} from ${source.repository}")

        // A token is only ever attached to Hugging Face itself. The downloader additionally drops
        // it across host-changing redirects, so a mirror or CDN never sees the user's credential.
        val token = if (source.provider == SourceProvider.HUGGING_FACE) userHuggingFaceToken() else null

        val meta = try {
            downloader.download(
                sourceUrl = url,
                dest = dest,
                // Forward the previously-observed validators so the downloader can send an
                // If-Range header — without it, a server that silently swapped the file under a
                // pinned revision would happily 206 a different version's bytes onto the end of
                // the stale partial, and only the post-hoc SHA-256 check would notice.
                knownEtag = knownEtag,
                knownLastModified = knownLastModified,
                authToken = token,
                onProgress = { downloaded, _ -> onProgress(downloaded) }
            )
        } catch (e: ModelDownloadException) {
            throw e.toFetchFailure(url)
        }
        return FetchMetadata(etag = meta.etag, lastModified = meta.lastModified)
    }

    /**
     * Splits the downloader's error codes into "retrying could work" and "retrying is pointless".
     *
     * Getting this boundary right is what keeps the store from hammering a dead URL with
     * exponential backoff, and equally from giving up on a transient 503. The permanent set is
     * deliberately narrow — anything genuinely ambiguous is treated as retryable, because a
     * needless retry costs a little bandwidth while a wrong permanent failure costs the user their
     * model.
     */
    private fun ModelDownloadException.toFetchFailure(url: String): Exception = when (code) {
        // The pinned revision or path is gone. For a commit-pinned catalogue this is a catalogue
        // bug, not a network condition, and no amount of retrying fixes it.
        ModelErrorCode.HTTP_NOT_FOUND -> PermanentFetchException(
            "Source no longer available (this is a catalogue error): $url", this
        )
        // Gated repo, or a bad/expired user token. Retrying without new credentials cannot help;
        // the user has to authorise with the rightsholder.
        ModelErrorCode.AUTHENTICATION_REQUIRED, ModelErrorCode.AUTHENTICATION_FAILED ->
            PermanentFetchException("Access denied — this model may require your own Hugging Face authorisation", this)
        // The remote bytes changed under a pinned revision, which should be impossible and means
        // the catalogue's hash can no longer be trusted for this source.
        ModelErrorCode.SOURCE_CHANGED -> PermanentFetchException(
            "The remote file changed unexpectedly for a pinned revision", this
        )
        // Everything else — timeouts, 5xx, range weirdness, storage hiccups — can plausibly
        // succeed on another attempt or another source.
        else -> this
    }
}
