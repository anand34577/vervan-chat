package com.vervan.chat.system

import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/** Converts technical failures into short guidance a non-technical user can act on. */
fun Throwable.toUserMessage(): String {
    val root = rootCause()
    val detail = root.message.orEmpty().lowercase()
    return when {
        root is CancellationException -> "Cancelled. No changes were made."
        root is OutOfMemoryError -> "Not enough memory. Close other apps or use a smaller model."
        root is StackOverflowError -> "This request is too complex. Try a shorter version."
        root is UnsatisfiedLinkError -> "The AI engine could not start. Try another engine or check Diagnostics."
        root is SecurityException -> "Permission is required. Allow it, then try again."
        root is java.io.FileNotFoundException -> "File unavailable. Choose it again or check that it still exists."
        root is TimeoutException || "timed out" in detail || "timeout" in detail ->
            "This took too long. Try again with a smaller model or shorter input."
        "no space" in detail || "disk full" in detail || "sqlite_full" in detail ->
            "Not enough storage. Free some space, then try again."
        "no generation model" in detail || "no active model" in detail || "model is not loaded" in detail ->
            "No model is ready. Open Models and load one."
        "context" in detail && ("length" in detail || "window" in detail || "token" in detail || "too long" in detail) ->
            "Input is too long. Shorten it or use a model with more context."
        "unsupported" in detail || "not supported" in detail ->
            "This model or file type is not supported. Choose another option."
        "permission" in detail || "access denied" in detail || "eacces" in detail ->
            "Access was blocked. Check permissions, then try again."
        "model" in detail && ("load" in detail || "backend" in detail || "runtime" in detail) ->
            "The model could not load. Retry, switch runtime, or use a smaller model."
        root is IOException -> "Could not read or save the data. Check the file and free storage."
        else -> "Could not finish. Try again or check Settings > Diagnostics."
    }
}

private fun Throwable.rootCause(): Throwable {
    var current = this
    val seen = mutableSetOf<Throwable>()
    while (current.cause != null && seen.add(current)) current = current.cause!!
    return current
}

/** Sanitizes an engine or library error string before it reaches the interface. */
fun String?.toUserMessage(): String = if (this.isNullOrBlank()) {
    "Could not finish. Try again."
} else {
    IllegalStateException(this).toUserMessage()
}
