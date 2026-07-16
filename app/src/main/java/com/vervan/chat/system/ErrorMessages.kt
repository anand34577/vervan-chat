package com.vervan.chat.system

import java.io.IOException

/** Turns a caught [Throwable] into a message a non-technical user can actually act on,
 * instead of a raw exception class name / native-library message leaking into the UI
 * (chat error banner, workflow error, tool-result text, API server responses). */
fun Throwable.toUserMessage(): String = when (this) {
    is OutOfMemoryError -> "Ran out of memory. Try closing other apps, or switch to a smaller model in Model Manager."
    is StackOverflowError -> "That request was too complex to process."
    is UnsatisfiedLinkError -> "The on-device model failed to load on this device (missing native library)."
    is SecurityException -> "Permission was denied for this action."
    is java.io.FileNotFoundException -> "The file couldn't be found — it may have been moved, deleted, or its access permission expired."
    is IOException -> "A storage or file error occurred. Check available storage and try again."
    else -> message?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred (${this::class.simpleName})."
}
