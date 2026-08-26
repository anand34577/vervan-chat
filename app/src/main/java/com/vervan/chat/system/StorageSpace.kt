package com.vervan.chat.system

import android.annotation.SuppressLint
import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/**
 * Returns the bytes Android says this app can still allocate on the volume containing [directory].
 *
 * `File.usableSpace` reports a filesystem view and can overstate what an app is allowed to use on
 * quota-aware or adoptable storage. Keep the fallback for OEMs that do not expose a usable
 * StorageManager service; callers still fail closed when both probes are unavailable.
 */
object StorageSpace {
    @SuppressLint("UsableSpace")
    fun allocatableBytes(context: Context, directory: File = context.filesDir): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        val quotaAware = runCatching {
            storage?.getAllocatableBytes(storage.getUuidForPath(directory)) ?: -1L
        }.getOrDefault(-1L)
        return if (quotaAware >= 0L) quotaAware else directory.usableSpace
    }
}
