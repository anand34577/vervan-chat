package com.vervan.chat.system

import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import android.content.Context
import androidx.annotation.StringRes
import com.vervan.chat.R

/**
 * Where [toUserMessage] reads its translated text. Set once from VervanApp.onCreate; the
 * application context follows locale changes. Until it is set (plain JVM unit tests, very early
 * startup) the English text written next to each id is used, so messages are never empty.
 */
object UserMessageText {
    @Volatile
    var context: Context? = null
}

private fun localized(@StringRes id: Int, english: String): String =
    UserMessageText.context?.let { runCatching { it.getString(id) }.getOrNull() } ?: english

/** Converts technical failures into short guidance a non-technical user can act on. */
fun Throwable.toUserMessage(): String {
    val root = rootCause()
    val detail = root.message.orEmpty().lowercase()
    return when {
        root is CancellationException -> localized(R.string.error_cancelled, "Cancelled. No changes were made.")
        root is OutOfMemoryError -> localized(R.string.error_out_of_memory, "Not enough memory. Close other apps or use a smaller model.")
        root is StackOverflowError -> localized(R.string.error_too_complex, "This request is too complex. Try a shorter version.")
        root is UnsatisfiedLinkError -> localized(R.string.error_engine_start, "The AI engine could not start. Try another engine or check Diagnostics.")
        root is SecurityException -> localized(R.string.error_permission_required, "Permission is required. Allow it, then try again.")
        // LlmEngine already words this one for the user, and the generic "could not load" below
        // would hide the fix (pick an NPU build, or Auto).
        "not built for the npu" in detail -> localized(R.string.error_not_npu_build, "This model isn't built for the NPU. Download an NPU build of it, or set its backend to Auto to run it on the GPU.")
        root is java.io.FileNotFoundException -> localized(R.string.error_file_unavailable, "File unavailable. Choose it again or check that it still exists.")
        root is TimeoutException || "timed out" in detail || "timeout" in detail ->
            localized(R.string.error_timeout, "This took too long. Try again with a smaller model or shorter input.")
        "no space" in detail || "disk full" in detail || "sqlite_full" in detail ->
            localized(R.string.error_no_storage, "Not enough storage. Free some space, then try again.")
        "no generation model" in detail || "no active model" in detail || "model is not loaded" in detail ->
            localized(R.string.error_no_model, "No model is ready. Open Settings → AI models and load one.")
        "context" in detail && ("length" in detail || "window" in detail || "token" in detail || "too long" in detail) ->
            localized(R.string.error_input_too_long, "Input is too long. Shorten it or use a model with more context.")
        "unsupported" in detail || "not supported" in detail ->
            localized(R.string.error_unsupported, "This model or file type is not supported. Choose another option.")
        "permission" in detail || "access denied" in detail || "eacces" in detail ->
            localized(R.string.error_access_blocked, "Access was blocked. Check permissions, then try again.")
        "model" in detail && ("load" in detail || "backend" in detail || "runtime" in detail) ->
            localized(R.string.error_model_load, "The model could not load. Retry, switch runtime, or use a smaller model.")
        root is IOException -> localized(R.string.error_io, "Could not read or save the data. Check the file and free storage.")
        else -> localized(R.string.error_generic, "Could not finish. Try again or check Settings → Storage & backup → Diagnostics.")
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
    localized(R.string.error_generic_short, "Could not finish. Try again.")
} else {
    IllegalStateException(this).toUserMessage()
}
