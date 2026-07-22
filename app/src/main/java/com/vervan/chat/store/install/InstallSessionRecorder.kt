package com.vervan.chat.store.install

import com.vervan.chat.data.db.dao.StoreInstallArtifactDao
import com.vervan.chat.data.db.dao.StoreInstallSessionDao
import com.vervan.chat.data.db.entities.StoreArtifactState
import com.vervan.chat.data.db.entities.StoreInstallArtifact
import com.vervan.chat.data.db.entities.StoreInstallSession
import com.vervan.chat.data.db.entities.StoreInstallState
import com.vervan.chat.store.model.Artifact
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.StoreModel
import com.vervan.chat.store.storage.BlobStore
import java.io.File

/**
 * Durable record of an install in flight, so a process death is recoverable rather than a restart
 * (spec §6, Android execution details).
 *
 * Separated behind an interface for two reasons: [VariantInstaller]'s transaction logic stays
 * testable without a database, and the recording is genuinely optional — a no-op recorder produces
 * a correct install that simply cannot be resumed, which is the right degradation if persistence
 * ever fails.
 */
interface InstallSessionRecorder {

    /** Called **before the first network request**, so a crash a millisecond later still leaves a
     * resumable record. */
    suspend fun begin(
        model: StoreModel,
        variant: ModelVariant,
        pending: List<Artifact>,
        stagingDir: File,
        catalogVersion: Int,
        acceptedLicenseHash: String?
    )

    suspend fun onState(variantId: String, state: StoreInstallState)

    suspend fun onArtifactState(
        variantId: String,
        artifactId: String,
        state: StoreArtifactState,
        downloadedBytes: Long = 0
    )

    suspend fun onProgress(variantId: String, artifactId: String, totalDownloadedBytes: Long)

    /** Persist the resume validators (ETag / Last-Modified) the fetcher observed for this
     * artifact's resolved source, so the next resume can revalidate via If-Range instead of
     * blindly appending to whatever's on disk. */
    suspend fun onArtifactResumeMetadata(variantId: String, artifactId: String, etag: String?, lastModified: String?)

    /** Reads back the resume validators persisted by [onArtifactResumeMetadata]. Null on first
     * attempt (or when the recorder is the no-op [None]). */
    suspend fun artifactResumeMetadata(variantId: String, artifactId: String): Pair<String?, String?>

    suspend fun finish(variantId: String, state: StoreInstallState, errorMessage: String? = null)

    /** No-op implementation — a correct but unresumable install. */
    object None : InstallSessionRecorder {
        override suspend fun begin(
            model: StoreModel, variant: ModelVariant, pending: List<Artifact>,
            stagingDir: File, catalogVersion: Int, acceptedLicenseHash: String?
        ) = Unit
        override suspend fun onState(variantId: String, state: StoreInstallState) = Unit
        override suspend fun onArtifactState(
            variantId: String, artifactId: String, state: StoreArtifactState, downloadedBytes: Long
        ) = Unit
        override suspend fun onProgress(variantId: String, artifactId: String, totalDownloadedBytes: Long) = Unit
        override suspend fun onArtifactResumeMetadata(
            variantId: String, artifactId: String, etag: String?, lastModified: String?
        ) = Unit
        override suspend fun artifactResumeMetadata(variantId: String, artifactId: String): Pair<String?, String?> = null to null
        override suspend fun finish(variantId: String, state: StoreInstallState, errorMessage: String?) = Unit
    }
}

/** Room-backed [InstallSessionRecorder]. */
class RoomInstallSessionRecorder(
    private val sessionDao: StoreInstallSessionDao,
    private val artifactDao: StoreInstallArtifactDao
) : InstallSessionRecorder {

    override suspend fun begin(
        model: StoreModel,
        variant: ModelVariant,
        pending: List<Artifact>,
        stagingDir: File,
        catalogVersion: Int,
        acceptedLicenseHash: String?
    ) {
        sessionDao.upsert(
            StoreInstallSession(
                variantId = variant.variantId,
                modelId = model.modelId,
                displayName = model.displayName,
                version = variant.version,
                runtime = variant.runtime.wireName,
                state = StoreInstallState.QUEUED,
                totalBytes = variant.totalSizeBytes,
                // Artifacts already in the blob store count as downloaded from the start, so a
                // deduplicated install does not appear to stall at 0% before jumping to done.
                downloadedBytes = variant.artifacts.filterNot { a -> pending.any { it.artifactId == a.artifactId } }
                    .sumOf { it.sizeBytes },
                catalogVersion = catalogVersion,
                acceptedLicenseHash = acceptedLicenseHash
            )
        )
        // Replace any rows from a previous attempt so stale artifact state cannot leak into this
        // one — the staged .part files on disk are what carries resume progress forward.
        artifactDao.deleteForVariant(variant.variantId)
        pending.forEach { artifact ->
            artifactDao.upsert(
                StoreInstallArtifact(
                    id = "${variant.variantId}:${artifact.artifactId}",
                    variantId = variant.variantId,
                    artifactId = artifact.artifactId,
                    role = artifact.role.wireName,
                    sourceUrl = artifact.sources.first().toUrl(),
                    tempPath = File(stagingDir, "${artifact.artifactId}.part").absolutePath,
                    expectedBytes = artifact.sizeBytes,
                    expectedSha256 = artifact.sha256,
                    downloadedBytes = File(stagingDir, "${artifact.artifactId}.part")
                        .takeIf { it.isFile }?.length() ?: 0L
                )
            )
        }
    }

    override suspend fun onState(variantId: String, state: StoreInstallState) {
        val session = sessionDao.get(variantId) ?: return
        sessionDao.upsert(session.copy(state = state, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun onArtifactState(
        variantId: String,
        artifactId: String,
        state: StoreArtifactState,
        downloadedBytes: Long
    ) {
        val row = artifactDao.getForVariant(variantId).find { it.artifactId == artifactId } ?: return
        artifactDao.upsert(
            row.copy(
                state = state,
                downloadedBytes = if (downloadedBytes > 0) downloadedBytes else row.downloadedBytes
            )
        )
    }

    override suspend fun onProgress(variantId: String, artifactId: String, totalDownloadedBytes: Long) {
        val session = sessionDao.get(variantId) ?: return
        sessionDao.upsert(
            session.copy(
                downloadedBytes = totalDownloadedBytes,
                currentArtifactId = artifactId,
                state = StoreInstallState.DOWNLOADING,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun onArtifactResumeMetadata(
        variantId: String, artifactId: String, etag: String?, lastModified: String?
    ) {
        val row = artifactDao.getForVariant(variantId).find { it.artifactId == artifactId } ?: return
        // Don't clobber the validators with nulls if the server happened to omit them this time —
        // a slightly older validator is a better revalidation hint than none at all.
        if (etag == null && lastModified == null) return
        artifactDao.upsert(row.copy(etag = etag ?: row.etag, lastModified = lastModified ?: row.lastModified))
    }

    override suspend fun artifactResumeMetadata(variantId: String, artifactId: String): Pair<String?, String?> {
        val row = artifactDao.getForVariant(variantId).find { it.artifactId == artifactId } ?: return null to null
        return row.etag to row.lastModified
    }

    override suspend fun finish(variantId: String, state: StoreInstallState, errorMessage: String?) {
        val session = sessionDao.get(variantId) ?: return
        sessionDao.upsert(
            session.copy(state = state, errorMessage = errorMessage, updatedAt = System.currentTimeMillis())
        )
        if (state == StoreInstallState.READY) {
            // The installed manifest is now the record of truth; per-artifact rows would only be a
            // second, divergent copy of it.
            artifactDao.deleteForVariant(variantId)
        }
    }
}

/**
 * Startup reconciliation for sessions a process death left mid-flight (spec §6/§29 precedent set
 * by the older pipeline's `recoverOnStartup`).
 *
 * The rule that matters: **a killed process is never itself treated as a failure.** An interrupted
 * session becomes [StoreInstallState.PAUSED], not FAILED, and its recorded byte counts are
 * reconciled against the real `.part` files on disk — the last progress write can lag the actual
 * bytes on disk by up to one throttle interval, so trusting the database over the filesystem here
 * would corrupt a resume.
 */
class StoreInstallRecovery(
    private val sessionDao: StoreInstallSessionDao,
    private val artifactDao: StoreInstallArtifactDao,
    private val blobStore: BlobStore
) {
    suspend fun recoverOnStartup() {
        for (session in sessionDao.getUnfinished()) {
            val artifacts = artifactDao.getForVariant(session.variantId)
            var reconciledBytes = 0L
            for (artifact in artifacts) {
                val onDisk = File(artifact.tempPath).takeIf { it.isFile }?.length() ?: 0L
                // A completed artifact was already promoted to a blob; its .part is gone, so fall
                // back to its expected size rather than counting it as zero.
                val counted = when {
                    blobStore.contains(artifact.expectedSha256) -> artifact.expectedBytes
                    else -> onDisk
                }
                reconciledBytes += counted
                if (counted != artifact.downloadedBytes) {
                    artifactDao.upsert(
                        artifact.copy(
                            downloadedBytes = counted,
                            state = if (blobStore.contains(artifact.expectedSha256)) {
                                StoreArtifactState.COMPLETED
                            } else {
                                StoreArtifactState.PENDING
                            }
                        )
                    )
                }
            }
            sessionDao.upsert(
                session.copy(
                    state = StoreInstallState.PAUSED,
                    downloadedBytes = reconciledBytes,
                    userRequestedStop = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
