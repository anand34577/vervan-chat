package com.vervan.chat.store.storage

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Content-addressed storage for verified model artifacts.
 *
 * ```
 * models/
 *   blobs/sha256/<hash>          # immutable once written
 *   installs/<variantId>/manifest.json
 *   staging/<variantId>/          # in-progress, never visible to a runtime
 * ```
 *
 * Addressing by hash rather than by name buys three things that matter here: the espeak-ng data
 * directory shared by four TTS voices is stored once; a model update re-downloads only the blobs
 * whose hashes actually changed; and a blob is immutable by construction, so nothing can be
 * modified in place behind a runtime that has it mmapped.
 *
 * A blob only ever enters via [put], and only after its hash has been verified against the signed
 * catalogue — so the filename *is* the integrity claim, and [verifyBlob] can re-check it later
 * with no external metadata.
 */
class BlobStore(private val root: File) {

    private val blobsDir = File(root, "blobs/sha256")
    private val installsDir = File(root, "installs")
    private val stagingDir = File(root, "staging")

    init {
        blobsDir.mkdirs()
        installsDir.mkdirs()
        stagingDir.mkdirs()
    }

    val installsRoot: File get() = installsDir

    fun stagingDirFor(variantId: String): File =
        File(stagingDir, variantId.sanitized()).apply { mkdirs() }

    fun contains(sha256: String): Boolean = blobFile(sha256).isFile

    /** Absolute path a runtime will be handed, or null when the blob is absent. Callers must treat
     * null as NOT_READY rather than assuming the file is there — an external volume can disappear
     * between install and load. */
    fun pathFor(sha256: String): String? = blobFile(sha256).takeIf { it.isFile }?.absolutePath

    fun sizeOf(sha256: String): Long = blobFile(sha256).takeIf { it.isFile }?.length() ?: 0L

    /**
     * Moves an already-verified staged file into the blob store and returns its final location.
     *
     * If the blob already exists this discards the incoming copy rather than overwriting: the
     * existing file has the same hash by definition, and overwriting risks corrupting a file some
     * other installed variant currently has open. That "already present" path is also the
     * deduplication win — a second model needing the same tokenizer downloads nothing.
     */
    fun put(staged: File, sha256: String): File {
        val target = blobFile(sha256)
        if (target.isFile) {
            staged.delete()
            return target
        }
        target.parentFile?.mkdirs()
        if (!staged.renameTo(target)) {
            // Rename fails across filesystems (staging on internal, blobs on an SD card). Copy
            // then delete, but never write to the final target in place: a process death mid-copy
            // would leave `target` present on disk with the correct SHA-256 filename but truncated
            // contents, silently breaking the class's "filename == integrity claim" contract. Copy
            // into a sibling temp file in the same directory and rename atomically — a same-dir
            // rename is atomic even across filesystems at the directory level, so the target is
            // either fully present or fully absent.
            val temp = File(target.parentFile, "${sha256}.tmp.${System.nanoTime()}")
            try {
                staged.copyTo(temp, overwrite = false)
                if (!temp.renameTo(target)) {
                    // Last-resort: a same-directory rename should not fail, but if it does (SELinux
                    // policy, readonly mount) we must not leave the temp file lying around claiming
                    // to be a complete blob.
                    temp.delete()
                    throw java.io.IOException("Could not commit blob $sha256 (rename failed)")
                }
                staged.delete()
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
        }
        return target
    }

    /**
     * Recomputes a blob's hash and deletes it if it no longer matches. Drives the periodic
     * integrity spot-check that catches on-disk corruption from unclean shutdowns —
     * without it, a corrupted weights file surfaces as an unexplained native crash at load time
     * rather than as a re-downloadable model.
     */
    fun verifyBlob(sha256: String): Boolean {
        val file = blobFile(sha256)
        if (!file.isFile) return false
        val actual = sha256Of(file)
        if (!actual.equals(sha256, ignoreCase = true)) {
            Log.w(TAG, "Blob $sha256 failed integrity check (got $actual); deleting")
            file.delete()
            return false
        }
        return true
    }

    /**
     * Deletes every blob not named by [referencedHashes].
     *
     * The caller derives that set by reading all installed manifests — deliberately *not* from an
     * incrementally maintained refcount. An incremental count is one missed decrement away from
     * leaking gigabytes silently, and one missed increment away from deleting a blob a working
     * model depends on; recomputing from the manifests makes the manifests the single source of
     * truth and is cheap at this scale (tens of models, not millions of objects).
     *
     * @return bytes reclaimed.
     */
    fun collectGarbage(referencedHashes: Set<String>): Long {
        var reclaimed = 0L
        blobsDir.listFiles()?.forEach { blob ->
            if (blob.name !in referencedHashes) {
                val size = blob.length()
                if (blob.delete()) {
                    reclaimed += size
                    Log.i(TAG, "Reclaimed orphan blob ${blob.name} ($size bytes)")
                }
            }
        }
        return reclaimed
    }

    /** Drops one variant's staging directory — used after that variant's install commits. Scoped
     * to a single variant on purpose: a store-wide sweep here would delete partial downloads
     * belonging to other installs that are still running. */
    fun clearStagingFor(variantId: String): Long {
        val dir = File(stagingDir, variantId.sanitized())
        if (!dir.isDirectory) return 0L
        val size = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        return size
    }

    /** Clears abandoned staging directories — partial downloads for variants nobody is installing
     * any more. Only safe from the periodic GC job, which knows the full set of live installs;
     * never call it from an install path (see [clearStagingFor]). Never touches blobs. */
    fun clearStaging(keepVariantIds: Set<String> = emptySet()): Long {
        var reclaimed = 0L
        val keep = keepVariantIds.map { it.sanitized() }.toSet()
        stagingDir.listFiles()?.forEach { dir ->
            if (dir.name !in keep) {
                reclaimed += dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
            }
        }
        return reclaimed
    }

    fun totalBlobBytes(): Long = blobsDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun blobFile(sha256: String) = File(blobsDir, sha256.lowercase())

    /** Variant ids come from a signed catalogue, but they end up as directory names — keep them
     * to characters that cannot traverse or escape regardless. */
    private fun String.sanitized() = replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val TAG = "BlobStore"

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
