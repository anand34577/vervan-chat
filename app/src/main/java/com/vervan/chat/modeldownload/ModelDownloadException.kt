package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelErrorCode

/** Structured failure for every stage of the downloader (network, storage, validation, import)
 * — caught once at the repository/coordinator boundary and turned into a [ModelErrorCode] +
 * message on the package row, instead of each layer inventing its own error shape. */
class ModelDownloadException(val code: ModelErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)
