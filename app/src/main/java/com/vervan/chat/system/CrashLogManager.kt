package com.vervan.chat.system

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash capture for a fully-offline app: with no remote crash reporter available
 * (and none wanted — see the network trust dashboard in DiagnosticsScreen), field failures
 * are invisible unless the app records them itself. Two sources feed the same log directory:
 *
 *  1. Java/Kotlin uncaught exceptions — [install] wraps the default handler, writes the
 *     trace, then delegates so the normal crash dialog/process death still happens.
 *  2. Deaths the process can never observe from inside — native crashes, ANRs, and the
 *     low-memory killer. [recordSystemExits] reads [ApplicationExitInfo] (API 30+) on the
 *     next launch and writes one entry per new exit. LMK kills matter most here: on 6–8 GB
 *     devices a too-large model/context dies silently and just "restarts", so that entry
 *     carries an actionable hint instead of a bare reason code.
 *
 * Logs are plain text under filesDir/crashlogs, newest-first, pruned to [MAX_LOGS]. Surfaced
 * (view/share/clear) in DiagnosticsScreen.
 */
class CrashLogManager(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "crashlogs")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        writeLog(
            kind = "crash",
            title = "Uncaught exception on thread ${thread.name}",
            body = trace
        )
    }

    /**
     * Records system-initiated process deaths since the last check. Call once per cold start,
     * off the main thread. Dedupes across launches by persisting the newest exit timestamp
     * already recorded — getHistoricalProcessExitReasons returns the same history every time.
     */
    fun recordSystemExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_TS, 0L)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 10) }
            .getOrElse { return }
        var newest = lastSeen
        // Oldest-first so log file timestamps line up with actual exit order.
        for (exit in exits.sortedBy { it.timestamp }) {
            if (exit.timestamp <= lastSeen) continue
            newest = maxOf(newest, exit.timestamp)
            val entry = describe(exit) ?: continue
            writeLog(kind = "exit", title = entry.first, body = entry.second, timestamp = exit.timestamp)
        }
        if (newest != lastSeen) prefs.edit().putLong(KEY_LAST_EXIT_TS, newest).apply()
    }

    /** Reason → (title, body with guidance); null for exits not worth a log entry (normal
     * user/system lifecycle: swipe-away, permission change, app update, ...). */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(exit: ApplicationExitInfo): Pair<String, String>? {
        val detail = exit.description?.takeIf { it.isNotBlank() }?.let { "\nSystem detail: $it" } ?: ""
        return when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed by the system: low memory" to
                "Android's low-memory killer stopped the app to reclaim RAM.$detail\n\n" +
                "If this happened while a model was loaded or generating, the model plus its " +
                "context likely exceeded what this device can hold alongside other apps. " +
                "Try a smaller model or quantization, reduce the context size in " +
                "Settings → Generation, or close other apps before long generations."
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash" to
                "The app's native code (model inference runtime) crashed.$detail\n\n" +
                "This usually points at a model file/runtime incompatibility or running out of " +
                "memory inside the inference engine. If it repeats with one model, try " +
                "re-downloading it or using a different quantization."
            ApplicationExitInfo.REASON_ANR -> "App not responding (ANR)" to
                "The system stopped the app because the UI was blocked too long.$detail"
            ApplicationExitInfo.REASON_CRASH -> "Crash" to
                "The app crashed with an unhandled error.$detail" +
                "\n\n(If a matching 'crash' log with a stack trace exists at the same time, that one has the details.)"
            else -> null
        }
    }

    private fun writeLog(kind: String, title: String, body: String, timestamp: Long = System.currentTimeMillis()) {
        try {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestamp))
            val file = File(dir, "$kind-$stamp-${timestamp % 1000}.txt")
            file.writeText(buildString {
                appendLine(title)
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))}")
                appendLine("App version: ${appVersion()}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine()
                appendLine(body.trimEnd())
            })
            prune()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write crash log", t)
        }
    }

    private fun prune() {
        listLogs().drop(MAX_LOGS).forEach { it.delete() }
    }

    /** Newest first. */
    fun listLogs(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "txt" }?.sortedByDescending { it.name.substringAfter('-') }
            ?: emptyList()

    fun clear() {
        listLogs().forEach { it.delete() }
    }

    private fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode})"
    }.getOrDefault("unknown")

    companion object {
        private const val TAG = "CrashLogManager"
        private const val PREFS = "crashlog"
        private const val KEY_LAST_EXIT_TS = "last_exit_timestamp"
        private const val MAX_LOGS = 10
    }
}
