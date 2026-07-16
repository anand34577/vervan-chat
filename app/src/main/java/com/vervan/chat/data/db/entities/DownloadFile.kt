package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelFileRole { MODEL, TOKENIZER, VOCABULARY, CONFIG, METADATA, ADAPTER, AUXILIARY }

enum class FileDownloadStatus { NOT_STARTED, DOWNLOADING, PAUSED, WAITING_FOR_NETWORK, COMPLETED, FAILED }

/** One file within a [DownloadPackage]. Resume metadata ([etag], [lastModified],
 * [acceptRanges]) is validated against the server on every resume attempt (see
 * com.vervan.chat.modeldownload.HttpRangeDownloader) — a changed validator means the remote
 * artifact moved and the partial file must be discarded, not silently appended to. */
@Entity(tableName = "download_files")
data class DownloadFile(
    @PrimaryKey val id: String, // "packageId:fileId"
    val packageId: String,
    val fileId: String,
    val fileName: String,
    val role: ModelFileRole,
    val sourceUrl: String,
    val resolvedUrl: String? = null,
    val tempPath: String,
    val finalPath: String,
    val expectedBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val sha256: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val acceptRanges: Boolean? = null,
    val status: FileDownloadStatus = FileDownloadStatus.NOT_STARTED,
    val retryCount: Int = 0,
    val errorMessage: String? = null
)
