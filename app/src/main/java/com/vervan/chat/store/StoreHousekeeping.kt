package com.vervan.chat.store

import android.content.Context
import android.util.Log
import com.vervan.chat.store.catalog.CatalogRepository
import com.vervan.chat.store.storage.InstalledManifestStore
import com.vervan.chat.store.storage.StoreMaintenance

/**
 * Periodic upkeep for the Model Store: catalogue refresh, blob garbage collection and a rolling
 * integrity spot-check (spec §7).
 *
 * Deliberately **not** a WorkManager job. None of this work has a deadline — a catalogue that
 * refreshes on the next launch instead of overnight is indistinguishable to the user, and blobs
 * orphaned by an uninstall stay orphaned harmlessly until someone opens the app again. Adding
 * WorkManager as a dependency (it is not currently in the build) to schedule work whose only
 * requirement is "eventually" would buy exact timing this feature has no use for, so instead this
 * runs on the cold-start housekeeping pass the app already performs, throttled by a persisted
 * timestamp so a user who launches ten times a day is not re-syncing ten times.
 */
class StoreHousekeeping(
    context: Context,
    private val catalogRepository: CatalogRepository,
    private val maintenance: StoreMaintenance,
    private val manifestStore: InstalledManifestStore,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Runs whichever passes are due. Each pass is independently throttled and independently
     * failure-isolated: a catalogue sync that fails because the device is offline must not stop
     * the GC pass, which needs no network at all.
     *
     * @param activeInstallVariantIds variants with an install in flight. Their staging directories
     *   and blobs are off-limits to the collector — a GC that ran mid-install would delete the
     *   partial downloads out from under it.
     */
    suspend fun runIfDue(activeInstallVariantIds: Set<String> = emptySet()) {
        if (isDue(KEY_LAST_SYNC, SYNC_INTERVAL_MS)) {
            runCatching { catalogRepository.sync() }
                .onSuccess { mark(KEY_LAST_SYNC) }
                .onFailure { Log.w(TAG, "Scheduled catalogue sync failed", it) }
        }

        if (isDue(KEY_LAST_GC, GC_INTERVAL_MS)) {
            runCatching { maintenance.reconcileAndCollect(activeInstallVariantIds) }
                .onSuccess { result ->
                    mark(KEY_LAST_GC)
                    if (result.reclaimedBytes > 0 || result.stagingReclaimedBytes > 0) {
                        Log.i(
                            TAG,
                            "Store GC reclaimed ${result.reclaimedBytes} bytes of blobs and " +
                                "${result.stagingReclaimedBytes} bytes of staging"
                        )
                    }
                }
                .onFailure { Log.w(TAG, "Store GC failed", it) }
        }

        if (isDue(KEY_LAST_INTEGRITY, INTEGRITY_INTERVAL_MS)) {
            runCatching { maintenance.spotCheckIntegrity(INTEGRITY_SAMPLE_SIZE) }
                .onSuccess { broken ->
                    mark(KEY_LAST_INTEGRITY)
                    // Manifests for broken variants are left in place on purpose: deleting them
                    // would silently make the model vanish from the user's installed list with no
                    // explanation. Flagging them lets the UI offer a re-download instead.
                    if (broken.isNotEmpty()) {
                        Log.w(TAG, "Integrity spot-check found corrupt variants: $broken")
                        prefs.edit().putStringSet(KEY_CORRUPT_VARIANTS, broken).apply()
                    } else {
                        prefs.edit().remove(KEY_CORRUPT_VARIANTS).apply()
                    }
                }
                .onFailure { Log.w(TAG, "Integrity spot-check failed", it) }
        }
    }

    /** Variants the last integrity pass found to have a missing or hash-mismatched blob. */
    fun corruptVariantIds(): Set<String> =
        prefs.getStringSet(KEY_CORRUPT_VARIANTS, emptySet()).orEmpty()

    /** Forces the next [runIfDue] to perform every pass — used after an uninstall, where waiting
     * a day to reclaim several gigabytes would look like the space was never freed. */
    fun invalidate() {
        prefs.edit().remove(KEY_LAST_SYNC).remove(KEY_LAST_GC).remove(KEY_LAST_INTEGRITY).apply()
    }

    /** True when nothing is installed and no catalogue has ever synced, so a first-run cold start
     * can skip the whole pass rather than reaching the network for a store the user never opened. */
    fun isDormant(): Boolean =
        manifestStore.readAll().isEmpty() && prefs.getLong(KEY_LAST_SYNC, 0L) == 0L

    private fun isDue(key: String, intervalMs: Long): Boolean {
        val last = prefs.getLong(key, 0L)
        val elapsed = now() - last
        // A clock that moved backwards (timezone edit, NTP correction) yields a negative elapsed
        // time. Treating that as "not due" would stall housekeeping until the clock caught up, so
        // it counts as due and re-marks the timestamp to the corrected clock.
        return last == 0L || elapsed < 0 || elapsed >= intervalMs
    }

    private fun mark(key: String) {
        prefs.edit().putLong(key, now()).apply()
    }

    companion object {
        private const val TAG = "StoreHousekeeping"
        private const val PREFS = "store_housekeeping"
        private const val KEY_LAST_SYNC = "lastSyncAt"
        private const val KEY_LAST_GC = "lastGcAt"
        private const val KEY_LAST_INTEGRITY = "lastIntegrityAt"
        private const val KEY_CORRUPT_VARIANTS = "corruptVariantIds"

        private const val SYNC_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val GC_INTERVAL_MS = 24L * 60 * 60 * 1000
        // Hashing multi-gigabyte blobs is expensive, so this runs weekly and only over a small
        // sample — the point is to notice silent filesystem corruption eventually, not promptly.
        private const val INTEGRITY_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val INTEGRITY_SAMPLE_SIZE = 2
    }
}
