package com.vervan.chat.system

/**
 * Logging that is harmless in plain JVM tests as well as on Android.
 *
 * The Android SDK's `Log` methods are throwing stubs in local unit tests. Logging is a diagnostic
 * side effect, not business logic, so a test must not fail while exercising a recovery path just
 * because the platform logger is unavailable. On a device these calls still delegate directly to
 * Android's logger.
 */
object SafeLog {
    fun i(tag: String, message: String): Int = runCatching { android.util.Log.i(tag, message) }.getOrDefault(0)

    fun w(tag: String, message: String): Int = runCatching { android.util.Log.w(tag, message) }.getOrDefault(0)

    fun w(tag: String, message: String, throwable: Throwable): Int =
        runCatching { android.util.Log.w(tag, message, throwable) }.getOrDefault(0)

    fun e(tag: String, message: String): Int = runCatching { android.util.Log.e(tag, message) }.getOrDefault(0)

    fun e(tag: String, message: String, throwable: Throwable): Int =
        runCatching { android.util.Log.e(tag, message, throwable) }.getOrDefault(0)
}
