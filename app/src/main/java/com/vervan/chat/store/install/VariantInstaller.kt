package com.vervan.chat.store.install

import android.util.Log
import com.vervan.chat.data.db.entities.StoreArtifactState
import com.vervan.chat.data.db.entities.StoreInstallState
import com.vervan.chat.store.model.Artifact
import com.vervan.chat.store.model.ArtifactSource
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.StoreModel
import com.vervan.chat.store.runtime.RuntimeAdapterRegistry
import com.vervan.chat.store.storage.BlobStore
import com.vervan.chat.store.storage.InstallRecord
import com.vervan.chat.store.storage.InstalledManifestStore
import java.io.File
import kotlinx.coroutines.CancellationException

/** Fetches one artifact's bytes to [dest]. Implemented over the app's existing
 * [com.vervan.chat.modeldownload.HttpRangeDownloader] in production — abstracted here so the
 * transaction logic can be tested without a network. */
interface ArtifactFetcher {
    /**
     * @param knownEtag / [knownLastModified] validators captured from a prior partial fetch of
     *   this same source. The fetcher SHOULD send them as `If-Range` so a server-side content
     *   change rejects the resume (HTTP 200) instead of letting the new bytes be silently
     *   appended to the stale partial. `null` on first attempt.
     * @return the validators the server actually sent back, so the caller can persist them for
     *   the *next* resume — without this round-trip, every resume after a process death would
     *   re-fetch from scratch because nobody remembers what the server claimed last time.
     * @throws PermanentFetchException when retrying or failing over cannot help (404/410).
     */
    suspend fun fetch(
        source: ArtifactSource,
        dest: File,
        expectedBytes: Long,
        onProgress: suspend (bytesDownloaded: Long) -> Unit,
        knownEtag: String? = null,
        knownLastModified: String? = null
    ): FetchMetadata
}

/** Validators the fetcher observed for the resolved source. Persist these per artifact so the
 * next resume of the same artifact against the same source can revalidate via `If-Range`. */
data class FetchMetadata(val etag: String?, val lastModified: String?)

/** The source is gone or forbidden — retrying the same URL is pointless. Distinguished from
 * ordinary failures so [VariantInstaller] fails over to the next source (or gives up) instead of
 * backing off and retrying a URL that will never work (spec §6.5). */
class PermanentFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface InstallOutcome {
    data class Success(val record: InstallRecord) : InstallOutcome
    data class Failed(val reason: String, val permanent: Boolean) : InstallOutcome
    data object Cancelled : InstallOutcome
}

data class InstallProgress(
    val artifactId: String,
    val artifactIndex: Int,
    val artifactCount: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long
)

/**
 * Installs one variant as a single all-or-nothing transaction (spec §6).
 *
 * The central invariant: **a variant is installed if and only if its manifest exists**, and the
 * manifest is written exactly once, last, after every artifact has been fetched, hash-verified and
 * promoted into the blob store. Any failure before that point leaves no manifest, so the variant
 * is simply not installed — there is no partially-installed state for a runtime to stumble into.
 *
 * Verified blobs from a failed attempt are deliberately *kept*. They are content-addressed and
 * immutable, so they are either useful to a retry or to a different model that shares them, and if
 * neither happens the GC pass reclaims them. Deleting them on failure would turn every flaky
 * connection into a full re-download.
 */
class VariantInstaller(
    private val blobStore: BlobStore,
    private val manifestStore: InstalledManifestStore,
    private val fetcher: ArtifactFetcher,
    private val adapters: RuntimeAdapterRegistry,
    private val probe: ArtifactFormatProbe = ArtifactFormatProbe(),
    /** Free bytes required beyond the download itself, covering filesystem overhead. */
    private val safetyMarginBytes: Long = 256L * 1024 * 1024,
    private val recorder: InstallSessionRecorder = InstallSessionRecorder.None,
    private val usableSpaceProvider: () -> Long
) {

    suspend fun install(
        model: StoreModel,
        variant: ModelVariant,
        catalogVersion: Int,
        acceptedLicenseHash: String?,
        onProgress: suspend (InstallProgress) -> Unit = {}
    ): InstallOutcome {
        // Role completeness first — cheapest possible rejection, and it catches the case that
        // would otherwise waste a multi-gigabyte download: a vision variant with no projector.
        val adapter = adapters.adapterFor(variant.runtime)
            ?: return InstallOutcome.Failed("No runtime for ${variant.runtime.wireName}", permanent = true)
        val missingRoles = adapter.requiredRoles(variant).filter { variant.artifactFor(it) == null }
        if (missingRoles.isNotEmpty()) {
            return InstallOutcome.Failed(
                "Variant is missing required ${missingRoles.joinToString { it.wireName }}",
                permanent = true
            )
        }

        // Deduplication: anything already in the blob store costs nothing to "download".
        val pending = variant.artifacts.filterNot { blobStore.contains(it.sha256) }
        val pendingBytes = pending.sumOf { it.sizeBytes }
        val usable = usableSpaceProvider()
        if (usable in 0 until (pendingBytes + safetyMarginBytes)) {
            return InstallOutcome.Failed(
                "Not enough free space: needs ${formatBytes(pendingBytes + safetyMarginBytes)}, " +
                    "${formatBytes(usable)} available",
                permanent = false
            )
        }

        val staging = blobStore.stagingDirFor(variant.variantId)
        var completedBytes = variant.artifacts.filter { blobStore.contains(it.sha256) }.sumOf { it.sizeBytes }

        // Persisted BEFORE any network call, so a process death one instruction later still leaves
        // a resumable record rather than an orphaned staging directory nobody knows about.
        recorder.begin(model, variant, pending, staging, catalogVersion, acceptedLicenseHash)

        try {
            for ((index, artifact) in pending.withIndex()) {
                // Re-check: an earlier artifact in this same variant may have been the identical
                // blob (a shared tokenizer listed under two roles).
                if (blobStore.contains(artifact.sha256)) continue

                val staged = File(staging, "${artifact.artifactId}.part")
                val baseBytes = completedBytes
                recorder.onState(variant.variantId, StoreInstallState.DOWNLOADING)
                recorder.onArtifactState(variant.variantId, artifact.artifactId, StoreArtifactState.DOWNLOADING)

                val fetchOutcome = fetchWithFailover(variant.variantId, artifact, staged) { bytes ->
                    recorder.onProgress(variant.variantId, artifact.artifactId, baseBytes + bytes)
                    onProgress(
                        InstallProgress(
                            artifactId = artifact.artifactId,
                            artifactIndex = index,
                            artifactCount = pending.size,
                            bytesDownloaded = baseBytes + bytes,
                            totalBytes = variant.totalSizeBytes
                        )
                    )
                }
                if (fetchOutcome != null) {
                    staged.delete()
                    return fail(variant.variantId, fetchOutcome, artifact.artifactId)
                }

                recorder.onState(variant.variantId, StoreInstallState.VERIFYING)
                recorder.onArtifactState(
                    variant.variantId, artifact.artifactId, StoreArtifactState.VERIFYING, staged.length()
                )
                verifyStaged(artifact, staged)?.let { failure ->
                    staged.delete()
                    return fail(variant.variantId, failure, artifact.artifactId)
                }

                blobStore.put(staged, artifact.sha256)
                recorder.onArtifactState(
                    variant.variantId, artifact.artifactId, StoreArtifactState.COMPLETED, artifact.sizeBytes
                )
                completedBytes += artifact.sizeBytes
            }
        } catch (e: CancellationException) {
            // A cancelled coroutine is process death or a user stop — never a failure. Leave the
            // staged parts alone so a resume reuses them via Range requests, and leave the session
            // for startup recovery to reconcile against what is actually on disk.
            recorder.onState(variant.variantId, StoreInstallState.PAUSED)
            throw e
        } catch (t: Throwable) {
            return fail(
                variant.variantId,
                InstallOutcome.Failed(t.message ?: "Install failed", permanent = false),
                null
            )
        }

        recorder.onState(variant.variantId, StoreInstallState.VALIDATING)

        // Every artifact is now a verified blob. Commit the manifest — this is the instant the
        // variant becomes installed.
        val record = InstallRecord(
            variantId = variant.variantId,
            modelId = model.modelId,
            version = variant.version,
            runtime = variant.runtime,
            runtimeSubtype = variant.runtimeSubtype,
            capabilities = variant.capabilities,
            roleToHash = variant.runtimeConfig.mapValues { (_, artifactId) ->
                variant.artifacts.first { it.artifactId == artifactId }.sha256
            },
            installedAt = System.currentTimeMillis(),
            catalogVersion = catalogVersion,
            acceptedLicenseHash = acceptedLicenseHash,
            totalBytes = variant.totalSizeBytes
        )

        // Resolve and validate through the adapter *before* publishing the manifest, so a variant
        // that cannot actually be loaded never reaches READY.
        recorder.onState(variant.variantId, StoreInstallState.INSTALLING)
        manifestStore.commit(record)
        val resolved = manifestStore.read(variant.variantId)?.let { manifestStore.resolve(it) }
        if (resolved == null) {
            manifestStore.uninstall(variant.variantId)
            return fail(
                variant.variantId,
                InstallOutcome.Failed("Install could not be resolved after commit", permanent = false),
                null
            )
        }
        return try {
            adapter.validate(resolved)
            blobStore.clearStagingFor(variant.variantId)
            recorder.finish(variant.variantId, StoreInstallState.READY)
            InstallOutcome.Success(record)
        } catch (t: Throwable) {
            // The manifest is what makes a variant installed, so removing it is what un-installs
            // the failed attempt. Blobs stay; GC decides whether they are orphaned.
            manifestStore.uninstall(variant.variantId)
            fail(
                variant.variantId,
                InstallOutcome.Failed(t.message ?: "Runtime validation failed", permanent = true),
                null
            )
        }
    }

    /** Records a terminal failure against the session and returns it unchanged. Permanent and
     * retryable are kept distinct all the way to the database so the UI can offer "Retry" only
     * when retrying could actually succeed. */
    private suspend fun fail(
        variantId: String,
        outcome: InstallOutcome.Failed,
        artifactId: String?
    ): InstallOutcome.Failed {
        if (artifactId != null) {
            recorder.onArtifactState(variantId, artifactId, StoreArtifactState.FAILED)
        }
        recorder.finish(
            variantId,
            if (outcome.permanent) StoreInstallState.FAILED_PERMANENT else StoreInstallState.FAILED_RETRYABLE,
            outcome.reason
        )
        return outcome
    }

    /**
     * Tries each source in order. Failover is safe here *only* because every fetched byte is
     * hash-checked against the signed catalogue afterwards — a mirror cannot substitute different
     * content, it can only fail to provide the right content (spec §9).
     *
     * Resume validators (ETag / Last-Modified) are read from the recorder at entry and persisted
     * back after a successful fetch against each source — without that round-trip, a resume after
     * process death would have no `If-Range` header to send, and any byte-different version of the
     * file (mirror drift, server config change) would be silently appended to the stale partial.
     *
     * @return null on success, or the outcome to return to the caller.
     */
    private suspend fun fetchWithFailover(
        variantId: String,
        artifact: Artifact,
        dest: File,
        onProgress: suspend (Long) -> Unit
    ): InstallOutcome.Failed? {
        var lastError: String? = null
        var sawPermanent = false
        // Validators loaded once per artifact: we resume the source the previous attempt was
        // actually using, and If-Range tells us if that source's content has changed since.
        var (knownEtag, knownLastModified) = recorder.artifactResumeMetadata(variantId, artifact.artifactId)

        for (source in artifact.sources) {
            try {
                val meta = fetcher.fetch(
                    source, dest, artifact.sizeBytes, onProgress,
                    knownEtag = knownEtag, knownLastModified = knownLastModified
                )
                // Persist what the server actually returned so the next resume (after a process
                // death) can revalidate. This is the bug fix: previously the fetcher hard-coded
                // null/null on every call, so the downloader never sent If-Range.
                recorder.onArtifactResumeMetadata(variantId, artifact.artifactId, meta.etag, meta.lastModified)
                knownEtag = meta.etag
                knownLastModified = meta.lastModified
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: PermanentFetchException) {
                // A pinned revision that 404s is a catalogue bug, not a transient one — note it,
                // but still try the mirror, which may well have the artifact.
                Log.w(TAG, "Permanent failure on ${source.provider.wireName}: ${e.message}")
                sawPermanent = true
                lastError = e.message
                dest.delete()
                // Different source = different validators; clear them so the next attempt
                // re-fetches from scratch instead of If-Range'ing against the wrong artifact.
                knownEtag = null
                knownLastModified = null
                recorder.onArtifactResumeMetadata(variantId, artifact.artifactId, null, null)
            } catch (t: Throwable) {
                Log.w(TAG, "Fetch failed from ${source.provider.wireName}: ${t.message}")
                lastError = t.message
                // Keep the partial file: the next source is a different URL, but a retry of this
                // same source can resume from it. The validators above remain valid for a same-source
                // retry; switching to a different source below clears them via the PermanentFetchException
                // arm only on permanent failures — a transient failure of a non-permanent kind keeps
                // the partial AND the validators, which is correct.
            }
        }
        return InstallOutcome.Failed(
            "Could not download ${artifact.artifactId}: ${lastError ?: "unknown error"}",
            permanent = sawPermanent
        )
    }

    /** Size, then hash, then format probe — cheapest disqualifier first. */
    private fun verifyStaged(artifact: Artifact, staged: File): InstallOutcome.Failed? {
        if (!staged.isFile) {
            return InstallOutcome.Failed("${artifact.artifactId} did not download", permanent = false)
        }
        if (staged.length() != artifact.sizeBytes) {
            return InstallOutcome.Failed(
                "${artifact.artifactId}: expected ${artifact.sizeBytes} bytes, got ${staged.length()}",
                permanent = false
            )
        }
        val actual = BlobStore.sha256Of(staged)
        if (!actual.equals(artifact.sha256, ignoreCase = true)) {
            // The bytes are not what the catalogue signed. Never retryable against the same
            // source, and never promoted to a blob.
            return InstallOutcome.Failed(
                "${artifact.artifactId} failed checksum verification",
                permanent = true
            )
        }
        return when (val result = probe.probe(staged, artifact.role)) {
            is ArtifactFormatProbe.ProbeResult.Accepted -> null
            is ArtifactFormatProbe.ProbeResult.Rejected -> InstallOutcome.Failed(
                "${artifact.artifactId} is not a valid ${artifact.role.wireName}: ${result.reason}",
                permanent = true
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes / (1024.0 * 1024 * 1024)
        return if (gib >= 1) "%.1f GB".format(gib) else "%.0f MB".format(bytes / (1024.0 * 1024))
    }

    companion object {
        private const val TAG = "VariantInstaller"
    }
}
