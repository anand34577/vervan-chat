package com.vervan.chat.store.storage

import android.util.Log
import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.ModelTask
import com.vervan.chat.store.model.RuntimeId
import com.vervan.chat.store.model.RuntimeSubtype
import com.vervan.chat.store.runtime.InstalledManifest
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * A record of one completed variant install: which blob fills which semantic role.
 *
 * This file is the authoritative statement that a variant is installed. Its existence is what
 * makes a model loadable, which is why [InstalledManifestStore.commit] writes it *last* and
 * atomically — an all-or-nothing install is enforced by the fact that a variant with
 * no manifest simply is not installed, however many of its blobs happen to be on disk.
 *
 * It is also the input to garbage collection: a blob is referenced if and only if some manifest
 * names it, so a manifest and the blobs it points at can never disagree.
 */
data class InstallRecord(
    val variantId: String,
    val modelId: String,
    val version: String,
    val runtime: RuntimeId,
    val runtimeSubtype: RuntimeSubtype?,
    val capabilities: Set<ModelTask>,
    /** role -> blob SHA-256. Paths are resolved at read time via [BlobStore.pathFor] rather than
     * stored, so moving the blob root (an SD card remount) does not invalidate every manifest. */
    val roleToHash: Map<ArtifactRole, String>,
    val installedAt: Long,
    /** Which catalogue version this install came from, and which licence text was accepted.
     * Both are needed to answer a rightsholder complaint precisely. */
    val catalogVersion: Int,
    val acceptedLicenseHash: String?,
    val totalBytes: Long
)

class InstalledManifestStore(private val blobStore: BlobStore) {

    /**
     * Writes the manifest atomically into the variant's install directory. Temp-plus-rename means
     * a process death mid-write leaves either the old manifest or none — never a half-written one
     * that would resolve some roles and not others.
     */
    fun commit(record: InstallRecord) {
        val dir = installDir(record.variantId).apply { mkdirs() }
        val target = File(dir, MANIFEST_NAME)
        val temp = File(dir, "$MANIFEST_NAME.tmp")
        temp.writeText(record.toJson().toString())
        if (!temp.renameTo(target)) {
            target.writeText(record.toJson().toString())
            temp.delete()
        }
    }

    fun read(variantId: String): InstallRecord? {
        val file = File(installDir(variantId), MANIFEST_NAME)
        if (!file.isFile) return null
        return try {
            parse(JSONObject(file.readText()))
        } catch (t: Throwable) {
            Log.w(TAG, "Unreadable manifest for $variantId: ${t.message}")
            null
        }
    }

    fun readAll(): List<InstallRecord> =
        blobStore.installsRoot.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val file = File(dir, MANIFEST_NAME)
            if (!file.isFile) return@mapNotNull null
            try {
                parse(JSONObject(file.readText()))
            } catch (t: Throwable) {
                Log.w(TAG, "Skipping unreadable manifest in ${dir.name}: ${t.message}")
                null
            }
        }.orEmpty()

    /**
     * Every blob hash referenced by any installed variant — the input to
     * [BlobStore.collectGarbage].
     *
     * Note this deliberately reads *all* manifests every time rather than caching. If reading one
     * manifest fails, that manifest's blobs would drop out of the referenced set and be collected,
     * silently breaking an installed model — so [uninstall] removes the manifest only after a
     * successful parse, and a manifest that cannot be parsed is left in place and logged rather
     * than treated as absent.
     */
    fun referencedHashes(): Set<String> =
        readAll().flatMap { it.roleToHash.values }.map { it.lowercase() }.toSet()

    /**
     * Resolves an install record into the role -> absolute path form runtimes consume. Returns
     * null when any referenced blob is missing from disk, because a partially resolvable model is
     * exactly what must never reach a runtime adapter — the adapter would load what it found and
     * behave as if the rest were optional.
     */
    fun resolve(record: InstallRecord): InstalledManifest? {
        val paths = HashMap<ArtifactRole, String>(record.roleToHash.size)
        for ((role, hash) in record.roleToHash) {
            val path = blobStore.pathFor(hash) ?: run {
                Log.w(TAG, "${record.variantId}: blob $hash for ${role.wireName} is missing")
                return null
            }
            paths[role] = path
        }
        return InstalledManifest(
            variantId = record.variantId,
            runtime = record.runtime,
            runtimeSubtype = record.runtimeSubtype,
            capabilities = record.capabilities,
            roleToPath = paths
        )
    }

    /** Removes the install record. Blobs are not touched here — they may be shared, and the GC
     * pass decides what is genuinely orphaned. */
    fun uninstall(variantId: String): Boolean =
        installDir(variantId).deleteRecursively()

    private fun installDir(variantId: String) =
        File(blobStore.installsRoot, variantId.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun InstallRecord.toJson(): JSONObject {
        val roles = JSONObject()
        roleToHash.forEach { (role, hash) -> roles.put(role.wireName, hash) }
        return JSONObject()
            .put("manifestVersion", MANIFEST_VERSION)
            .put("variantId", variantId)
            .put("modelId", modelId)
            .put("version", version)
            .put("runtime", runtime.wireName)
            .put("runtimeSubtype", runtimeSubtype?.wireName)
            .put("capabilities", JSONArray(capabilities.map { it.wireName }))
            .put("roles", roles)
            .put("installedAt", installedAt)
            .put("catalogVersion", catalogVersion)
            .put("acceptedLicenseHash", acceptedLicenseHash)
            .put("totalBytes", totalBytes)
    }

    private fun parse(obj: JSONObject): InstallRecord? {
        val manifestVersion = obj.optInt("manifestVersion", 0)
        if (manifestVersion > MANIFEST_VERSION) {
            // Written by a newer app that was then downgraded. Refuse to interpret it rather than
            // guess — a misread role map loads the wrong file into a native runtime.
            Log.w(TAG, "Manifest version $manifestVersion is newer than this build understands")
            return null
        }
        val runtime = RuntimeId.fromWire(obj.optString("runtime")) ?: return null
        val rolesObj = obj.optJSONObject("roles") ?: return null
        val roles = HashMap<ArtifactRole, String>()
        for (key in rolesObj.keys()) {
            val role = ArtifactRole.fromWire(key) ?: return null
            roles[role] = rolesObj.getString(key)
        }
        if (roles.isEmpty()) return null

        val capabilitiesArray = obj.optJSONArray("capabilities") ?: JSONArray()
        return InstallRecord(
            variantId = obj.optString("variantId").ifBlank { return null },
            modelId = obj.optString("modelId"),
            version = obj.optString("version"),
            runtime = runtime,
            runtimeSubtype = obj.optString("runtimeSubtype").ifBlank { null }
                ?.let { RuntimeSubtype.fromWire(it) },
            capabilities = (0 until capabilitiesArray.length())
                .mapNotNull { ModelTask.fromWire(capabilitiesArray.optString(it)) }.toSet(),
            roleToHash = roles,
            installedAt = obj.optLong("installedAt", 0L),
            catalogVersion = obj.optInt("catalogVersion", 0),
            acceptedLicenseHash = obj.optString("acceptedLicenseHash").ifBlank { null },
            totalBytes = obj.optLong("totalBytes", 0L)
        )
    }

    companion object {
        private const val TAG = "InstalledManifestStore"
        private const val MANIFEST_NAME = "installed-manifest.json"
        private const val MANIFEST_VERSION = 1
    }
}
