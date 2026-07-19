package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.FileDownloadStatus
import com.vervan.chat.data.db.entities.ModelErrorCode
import com.vervan.chat.data.db.entities.ModelOrigin
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ModelStatus

enum class ModelAction { DOWNLOAD, PAUSE, RESUME, CANCEL, DELETE, LOAD, UNLOAD, DETAILS }

internal fun downloadActionsFor(status: ModelStatus): Set<ModelAction> = when (status) {
    ModelStatus.NOT_DOWNLOADED -> setOf(ModelAction.DOWNLOAD, ModelAction.DETAILS)
    ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.WAITING_FOR_NETWORK,
    ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE, ModelStatus.DOWNLOADING ->
        setOf(ModelAction.PAUSE, ModelAction.CANCEL, ModelAction.DETAILS)
    ModelStatus.PAUSED, ModelStatus.FAILED -> setOf(ModelAction.RESUME, ModelAction.DELETE, ModelAction.DETAILS)
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING -> setOf(ModelAction.DETAILS)
    ModelStatus.PAUSING, ModelStatus.CANCELLING, ModelStatus.DELETING,
    ModelStatus.READY, ModelStatus.CANCELLED -> emptySet()
}

data class ModelError(val code: ModelErrorCode, val message: String)

data class ModelFileUiState(
    val fileId: String,
    val fileName: String,
    val status: FileDownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val errorMessage: String?
)

/** One row for the Models screen — either a catalogue entry not yet downloaded, an in-progress
 * download/install, or an installed model. [allowedActions] is the single source of truth for
 * which buttons a card shows; Compose never re-derives state-transition rules itself (spec
 * requirement — see ModelInstallationRepository.allowedActionsFor). */
data class ModelUiState(
    val modelId: String,
    val version: String,
    val displayName: String,
    val description: String,
    val category: ModelRole,
    val status: ModelStatus,
    val files: List<ModelFileUiState> = emptyList(),
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val estimatedRemainingSeconds: Long? = null,
    val currentFileName: String? = null,
    val completedFileCount: Int = 0,
    val totalFileCount: Int = 0,
    val error: ModelError? = null,
    val origin: ModelOrigin? = null,
    val isLoaded: Boolean = false,
    val installedSizeBytes: Long? = null,
    val installedAt: Long? = null,
    val requiresAuthToken: Boolean = false,
    val requiresLicenseAcceptance: Boolean = false,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val precision: String? = null,
    val minimumRamBytes: Long? = null,
    val capabilities: Set<String> = emptySet(),
    val sourceName: String = "",
    val allowedActions: Set<ModelAction> = emptySet()
) {
    val packageId: String get() = DownloadIds.packageId(modelId, version)
}
