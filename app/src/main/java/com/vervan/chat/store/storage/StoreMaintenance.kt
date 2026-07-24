package com.vervan.chat.store.storage

import android.util.Log

/**
 * Periodic upkeep for the blob store (spec §7). Intended to be driven by WorkManager — this is
 * exactly the deferrable background work WorkManager is good at, as distinct from the large
 * user-facing download, which must not be a WorkManager job because Android 16's job quotas can
 * stall long-running workers.
 *
 * Two independent passes, run on different cadences:
 *
 *  - [reconcileAndCollect] recomputes what is referenced from the installed manifests and deletes
 *    everything else. Recomputation rather than an incremental refcount is the whole point: an
 *    incremental counter that misses a decrement leaks storage invisibly, and one that misses an
 *    increment deletes a blob a working model needs. Deriving it from manifests each time makes
 *    those bugs impossible by construction.
 *  - [spotCheckIntegrity] re-hashes a small sample of installed blobs to catch on-disk corruption
 *    from unclean shutdowns, so it surfaces as "model needs re-downloading" rather than as an
 *    unexplained native crash at load.
 */
class StoreMaintenance(
    private val blobStore: BlobStore,
    private val manifestStore: InstalledManifestStore
) {

    data class GcResult(val reclaimedBytes: Long, val stagingReclaimedBytes: Long)

    fun reconcileAndCollect(activeInstallVariantIds: Set<String> = emptySet()): GcResult {
        val referenced = manifestStore.referencedHashes()
        val reclaimed = blobStore.collectGarbage(referenced)
        // Staging dirs for variants that are neither installed nor currently installing are
        // abandoned partial downloads.
        val live = activeInstallVariantIds + manifestStore.readAll().map { it.variantId }
        val stagingReclaimed = blobStore.clearStaging(keepVariantIds = live)
        if (reclaimed > 0 || stagingReclaimed > 0) {
            Log.i(TAG, "GC reclaimed $reclaimed bytes of blobs, $stagingReclaimed bytes of staging")
        }
        return GcResult(reclaimed, stagingReclaimed)
    }

    /**
     * Re-hashes up to [sampleSize] installed blobs and reports which variants are now broken.
     *
     * Sampled rather than exhaustive because re-hashing every blob means reading several gigabytes
     * — acceptable occasionally, not on a schedule, and definitely not on battery. Over enough
     * runs the sample covers everything.
     *
     * @return variant ids whose blobs failed verification; callers should mark these NOT_READY and
     *   offer a re-download rather than deleting them out from under the user.
     */
    fun spotCheckIntegrity(sampleSize: Int = 2): Set<String> {
        val records = manifestStore.readAll()
        if (records.isEmpty()) return emptySet()

        val hashToVariants = HashMap<String, MutableSet<String>>()
        records.forEach { record ->
            record.roleToHash.values.forEach { hash ->
                hashToVariants.getOrPut(hash.lowercase()) { mutableSetOf() }.add(record.variantId)
            }
        }

        val broken = mutableSetOf<String>()
        hashToVariants.keys.shuffled().take(sampleSize).forEach { hash ->
            if (!blobStore.verifyBlob(hash)) {
                // verifyBlob has already deleted the corrupt blob; every variant referencing it is
                // now incomplete.
                broken += hashToVariants[hash].orEmpty()
                Log.w(TAG, "Blob $hash failed spot-check; affects ${hashToVariants[hash]}")
            }
        }
        return broken
    }

    /** Per-variant disk usage for the storage-management UI. Shared blobs are attributed to every
     * variant that references them, so the numbers intentionally sum to more than
     * [BlobStore.totalBlobBytes] — presenting a share of a shared file would be more misleading
     * than an honest "this model needs these bytes present". */
    fun usageByVariant(): Map<String, Long> =
        manifestStore.readAll().associate { record ->
            record.variantId to record.roleToHash.values.sumOf { blobStore.sizeOf(it) }
        }

    companion object {
        private const val TAG = "StoreMaintenance"
    }
}
