package com.vervan.chat.store.catalog

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * On-disk home for the last catalogue the client accepted, plus the rollback-protection watermark.
 *
 * Writes are temp-file-plus-rename so a process death mid-write can never leave a truncated
 * catalogue behind — the app would then have neither the old nor the new one, which §4.6 forbids.
 *
 * [highestAcceptedVersion] is stored *separately from* the catalogue file and is never lowered.
 * Keeping it separate matters: if the catalogue file is ever deleted (storage pressure, a user
 * clearing data partially, a bug), the watermark must survive so a replayed old catalogue still
 * gets rejected. Deleting app data resets both, but that is a user-initiated act on a device they
 * control, not something a network attacker can reach.
 */
class CatalogStore(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, "store"))

    init {
        root.mkdirs()
    }

    private val catalogFile = File(root, "catalog.json")
    private val metaFile = File(root, "catalog-meta.json")

    fun readCatalogJson(): String? = catalogFile.takeIf { it.isFile }?.readText()

    /** The highest catalogue version ever *successfully verified and accepted*. Zero when the
     * client has never accepted one, which makes any signed catalogue acceptable on first run. */
    fun highestAcceptedVersion(): Int = try {
        metaFile.takeIf { it.isFile }
            ?.let { JSONObject(it.readText()).optInt("highestAcceptedVersion", 0) }
            ?: 0
    } catch (t: Throwable) {
        Log.w(TAG, "Catalogue meta unreadable, treating as never-accepted: ${t.message}")
        0
    }

    fun lastSyncAt(): Long = try {
        metaFile.takeIf { it.isFile }
            ?.let { JSONObject(it.readText()).optLong("lastSyncAt", 0L) } ?: 0L
    } catch (t: Throwable) {
        0L
    }

    /**
     * Commits a verified catalogue. Order is deliberate: the catalogue lands first, the watermark
     * second. A crash between them leaves the new catalogue with an old watermark, which at worst
     * re-accepts the same version — harmless. The reverse order could leave a raised watermark
     * with the *old* catalogue still on disk, permanently refusing the update that was supposed to
     * replace it.
     */
    fun commit(rawCatalogJson: String, catalogVersion: Int) {
        writeAtomically(catalogFile, rawCatalogJson)
        val meta = JSONObject()
            .put("highestAcceptedVersion", maxOf(catalogVersion, highestAcceptedVersion()))
            .put("lastSyncAt", System.currentTimeMillis())
        writeAtomically(metaFile, meta.toString())
    }

    /** Records that a sync ran without a new catalogue being accepted, so the UI can distinguish
     * "never synced" from "synced, already current". */
    fun recordSyncAttempt() {
        val meta = JSONObject()
            .put("highestAcceptedVersion", highestAcceptedVersion())
            .put("lastSyncAt", System.currentTimeMillis())
        writeAtomically(metaFile, meta.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            // Same-directory rename should not fail, but a copy fallback is better than losing the
            // write entirely on a filesystem that refuses it.
            target.writeText(content)
            temp.delete()
        }
    }

    companion object {
        private const val TAG = "CatalogStore"
    }
}
