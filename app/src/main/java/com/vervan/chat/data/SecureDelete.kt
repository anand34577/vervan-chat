package com.vervan.chat.data

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Best-effort secure delete (Phase C) — overwrites a file's bytes with random data ("rws" mode,
 * which forces each write to sync before returning) before unlinking it, instead of a plain
 * [File.delete] that only removes the directory entry and leaves the underlying blocks readable
 * until the filesystem reuses them.
 *
 * genuinely best-effort, not a guarantee — documented rather than oversold. Flash
 * storage wear-leveling means the physical blocks a rewrite lands on aren't necessarily the
 * same ones that held the original data, so true secure erase isn't achievable at the app layer
 * on modern flash without TRIM/full-disk support this app doesn't have. This raises the bar
 * against casual file-recovery tools reading raw blocks; it isn't forensic-grade erasure.
 */
object SecureDelete {
    private const val CHUNK_BYTES = 1 shl 20 // 1 MB

    /** Returns whether [file] is actually gone afterward (already-absent counts as success) —
     * callers that need to know deletion truly happened (e.g. before dropping the DB row that
     * references it, per Model Loading Strategy §4.4 step 7) must check this instead of assuming
     * success, since [File.delete] can silently fail (locked file, read-only/ejected removable
     * storage) and previously that failure was discarded with no way for a caller to notice. */
    fun overwriteAndDelete(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isFile) {
            runCatching {
                val length = file.length()
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(minOf(length, CHUNK_BYTES.toLong()).toInt().coerceAtLeast(1))
                    val random = SecureRandom()
                    raf.seek(0)
                    var written = 0L
                    while (written < length) {
                        random.nextBytes(buffer)
                        val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                }
            }.onFailure {
                // This used to fail with zero signal anywhere (no log, nothing) then delete the
                // file anyway — silently degrading from "securely wiped" to "sensitive bytes
                // still recoverable on disk" with no way to notice. At minimum, log it.
                Log.w(TAG, "overwriteAndDelete: overwrite failed for ${file.name}, deleting without a secure wipe", it)
            }
        }
        return file.delete()
    }

    private const val TAG = "SecureDelete"
}
