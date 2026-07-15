package com.vervan.chat.data

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Best-effort secure delete (Phase C) — overwrites a file's bytes with random data ("rws" mode,
 * which forces each write to sync before returning) before unlinking it, instead of a plain
 * [File.delete] that only removes the directory entry and leaves the underlying blocks readable
 * until the filesystem reuses them.
 *
 * ponytail: genuinely best-effort, not a guarantee — documented rather than oversold. Flash
 * storage wear-leveling means the physical blocks a rewrite lands on aren't necessarily the
 * same ones that held the original data, so true secure erase isn't achievable at the app layer
 * on modern flash without TRIM/full-disk support this app doesn't have. This raises the bar
 * against casual file-recovery tools reading raw blocks; it isn't forensic-grade erasure.
 */
object SecureDelete {
    private const val CHUNK_BYTES = 1 shl 20 // 1 MB

    fun overwriteAndDelete(file: File) {
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
            }
        }
        file.delete()
    }
}
