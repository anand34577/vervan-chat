package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable state for the Model Store's install pipeline (com.vervan.chat.store).
 *
 * Deliberately separate tables from [DownloadPackage]/[DownloadFile], which serve the older
 * in-APK catalogue. Reusing those would mean forcing two different data models through one schema:
 * the old one keys on `modelId:version` with [ModelFileRole], the new one keys on a variant id
 * with the store's much larger [com.vervan.chat.store.model.ArtifactRole] and needs multi-source
 * failover state per artifact. Two clean tables cost one migration; one overloaded table would
 * cost correctness in both pipelines.
 */

/** The store's install state machine (spec §6). Mirrors the shape of [ModelStatus] but is a
 * distinct type because the stages differ — the store has an explicit VALIDATING step (runtime
 * adapter role-completeness) that the old pipeline has no equivalent for, and splits failure into
 * retryable and permanent so the UI can offer "Retry" only when retrying could actually work. */
enum class StoreInstallState {
    QUEUED, DOWNLOADING, VERIFYING, VALIDATING, INSTALLING, READY,
    PAUSED, FAILED_RETRYABLE, FAILED_PERMANENT, CANCELLED, CORRUPTED
}

enum class StoreArtifactState { PENDING, DOWNLOADING, VERIFYING, COMPLETED, FAILED, SKIPPED_DEDUPED }

/**
 * One install transaction for one variant.
 *
 * Written **before any network call** (spec §6, Android execution details) so that a process death
 * mid-download is recoverable: on restart the app finds the session, reconciles each artifact's
 * `downloadedBytes` against the real `.part` file on disk, and resumes rather than restarting a
 * multi-gigabyte transfer.
 *
 * [catalogVersion] and [acceptedLicenseHash] are captured here rather than looked up later because
 * the catalogue can be updated mid-download — the install must record the terms the user actually
 * accepted at the moment they accepted them (spec §11/§12).
 */
@Entity(tableName = "store_install_sessions")
data class StoreInstallSession(
    @PrimaryKey val variantId: String,
    val modelId: String,
    val displayName: String,
    val version: String,
    val runtime: String,
    val state: StoreInstallState = StoreInstallState.QUEUED,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val currentArtifactId: String? = null,
    val errorMessage: String? = null,
    val catalogVersion: Int = 0,
    val acceptedLicenseHash: String? = null,
    /** Set when the user explicitly asked to stop, so an ordinary coroutine cancellation from
     * process death is never mistaken for a deliberate pause/cancel. */
    val userRequestedStop: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One artifact within a session.
 *
 * [sourceIndex] records which of the artifact's sources is currently being used, so a resume after
 * a failover continues against the mirror it had already switched to rather than starting over at
 * the primary. [resolvedUrl] holds the post-redirect CDN URL; Hugging Face `/resolve/` endpoints
 * 302 to an expiring S3/CloudFront URL, so a resume re-validates against the *original* source URL
 * and lets the redirect be followed afresh (spec §6.5).
 */
@Entity(tableName = "store_install_artifacts", indices = [Index("variantId")])
data class StoreInstallArtifact(
    @PrimaryKey val id: String, // "variantId:artifactId"
    val variantId: String,
    val artifactId: String,
    val role: String,
    val sourceIndex: Int = 0,
    val sourceUrl: String,
    val resolvedUrl: String? = null,
    val tempPath: String,
    val expectedBytes: Long,
    val downloadedBytes: Long = 0,
    val expectedSha256: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val state: StoreArtifactState = StoreArtifactState.PENDING,
    val retryCount: Int = 0,
    val errorMessage: String? = null
)
