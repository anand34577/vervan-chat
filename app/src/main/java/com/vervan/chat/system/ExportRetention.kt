package com.vervan.chat.system

import java.io.File

/**
 * Age-based cleanup for one-off share/export artifacts (a scanned PDF, a merged TTS export, a
 * transcript) written into a scratch directory (typically under `cacheDir`) that nothing else in
 * the app is responsible for pruning. Call this after writing a new file into [dir] — cheap
 * enough not to need WorkManager or a cold-start pass, same reasoning as
 * [com.vervan.chat.VervanApp]'s own cold-start recycle-bin purge.
 *
 * Not a substitute for Settings' manual "Clear cache" (which still wipes the whole `cacheDir`
 * immediately) — this just keeps that directory from growing without bound between the times a
 * user thinks to do that.
 */
fun pruneOldExports(dir: File, retentionMs: Long = DEFAULT_EXPORT_RETENTION_MS) {
    val cutoff = System.currentTimeMillis() - retentionMs
    dir.listFiles()?.forEach { file -> if (file.isFile && file.lastModified() < cutoff) file.delete() }
}

const val DEFAULT_EXPORT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
