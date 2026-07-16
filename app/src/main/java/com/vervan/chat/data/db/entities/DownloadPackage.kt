package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Explicit download/install state machine for the model downloader (see
 * com.vervan.chat.modeldownload). The normal path is
 * NOT_DOWNLOADED -> QUEUED -> PREPARING -> DOWNLOADING -> DOWNLOADED -> VERIFYING -> IMPORTING
 * -> READY, with PAUSING/PAUSED, WAITING_FOR_NETWORK/WAITING_FOR_WIFI, and
 * CANCELLING/CANCELLED/FAILED as interruption branches. A package must never jump straight from
 * DOWNLOADING to READY — verification and import are always separate, observable steps. */
enum class ModelStatus {
    NOT_DOWNLOADED,
    QUEUED, PREPARING, WAITING_FOR_NETWORK, WAITING_FOR_WIFI, WAITING_FOR_STORAGE,
    DOWNLOADING, PAUSING, PAUSED,
    DOWNLOADED, VERIFYING, IMPORTING,
    READY,
    CANCELLING, CANCELLED, FAILED, DELETING
}

/** Why a download stopped — inspected by the downloader before it writes a package's final
 * state, so a plain worker/coroutine cancellation (process death, app kill) is never confused
 * with an explicit user pause/cancel/delete request. */
enum class StopReason { NONE, PAUSE_REQUESTED, CANCEL_REQUESTED, DELETE_REQUESTED }

enum class ModelErrorCode {
    NO_NETWORK, WIFI_REQUIRED, MOBILE_DATA_CONFIRMATION_REQUIRED,
    AUTHENTICATION_REQUIRED, AUTHENTICATION_FAILED,
    LICENSE_REQUIRED,
    HTTP_NOT_FOUND, HTTP_SERVER_ERROR, REDIRECT_FAILED, RANGE_NOT_SUPPORTED, SOURCE_CHANGED,
    INSUFFICIENT_STORAGE, STORAGE_UNAVAILABLE, STORAGE_WRITE_FAILED,
    CHECKSUM_MISMATCH, INVALID_MODEL_FILE, UNSUPPORTED_MODEL, TOKENIZER_MISSING,
    IMPORT_FAILED, DUPLICATE_MODEL, USER_CANCELLED, UNKNOWN
}

/** One model-package install (a catalogue model + version being downloaded). Files belonging to
 * it live in [com.vervan.chat.data.db.dao.DownloadFileDao]. Package identity is
 * `modelId:version` (see [id]'s construction at the call site), so a future catalogue update
 * that ships a new version of an already-installed model downloads as a distinct package rather
 * than clobbering state for the old one. */
@Entity(tableName = "download_packages")
data class DownloadPackage(
    @PrimaryKey val id: String, // "modelId:version"
    val modelId: String,
    val version: String,
    val displayName: String,
    val role: ModelRole,
    val status: ModelStatus = ModelStatus.QUEUED,
    val stopReason: StopReason = StopReason.NONE,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val currentFileId: String? = null,
    val errorCode: ModelErrorCode? = null,
    val errorMessage: String? = null,
    val authRequired: Boolean = false,
    val licenseAccepted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
