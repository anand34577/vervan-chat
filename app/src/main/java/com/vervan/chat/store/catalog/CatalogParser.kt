package com.vervan.chat.store.catalog

import android.util.Log
import com.vervan.chat.store.model.AcceleratorRequirement
import com.vervan.chat.store.model.Artifact
import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.ArtifactSource
import com.vervan.chat.store.model.ModelLicense
import com.vervan.chat.store.model.ModelTask
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.RuntimeId
import com.vervan.chat.store.model.RuntimeSubtype
import com.vervan.chat.store.model.SourceProvider
import com.vervan.chat.store.model.StoreCatalog
import com.vervan.chat.store.model.StoreModel
import com.vervan.chat.store.model.VariantRequirements
import org.json.JSONArray
import org.json.JSONObject

/** A catalogue was rejected outright. The caller keeps its previous good catalogue — the client
 * must never end up with *no* catalogue because one fetch was malformed (spec §4.6). */
class CatalogRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses and validates a signed catalogue document into [StoreCatalog].
 *
 * Two different failure policies live here on purpose, and the distinction matters:
 *
 *  - **Whole-catalogue rejection** (throws [CatalogRejectedException]) for anything that means we
 *    do not understand the document at all: an unrecognised `schemaVersion`, a missing top-level
 *    field, malformed JSON. Spec §2 requires failing *closed* here — a partial parse of an unknown
 *    schema is how you end up installing something the publisher described differently.
 *
 *  - **Per-variant rejection** (variant dropped, catalogue kept) for a variant this build cannot
 *    safely represent: an unknown artifact role, a runtime with no adapter, a required role that
 *    no artifact fills. Dropping just the variant keeps the rest of the store usable on an older
 *    app version, and a dropped variant is strictly safer than a rendered one whose artifact set
 *    we only partly understand — the user can never tap install on it.
 *
 * A variant that survives parsing is structurally installable: every artifact has a 64-hex
 * SHA-256, at least one source, a commit-pinned revision, and a containable install path.
 */
class CatalogParser(
    /** Schema versions this build knows how to read in full. Anything else fails closed. */
    private val supportedSchemaVersions: Set<Int> = setOf(1),
    private val appVersionCode: Int,
    /** Runtimes this build actually has an adapter and native library for. A catalogue may
     * legitimately describe more than the installed app can run. */
    private val availableRuntimes: Set<RuntimeId>
) {

    fun parse(json: String): StoreCatalog {
        val root = try {
            JSONObject(json)
        } catch (t: Throwable) {
            throw CatalogRejectedException("Catalogue is not valid JSON", t)
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion !in supportedSchemaVersions) {
            // Fail closed, loudly. This is the branch that fires when the catalogue has moved on
            // and the app has not — the fix is an app update, not a lenient parse.
            throw CatalogRejectedException(
                "Unsupported schemaVersion $schemaVersion (this build reads $supportedSchemaVersions)"
            )
        }

        val catalogVersion = root.optInt("catalogVersion", -1)
        if (catalogVersion < 0) throw CatalogRejectedException("Missing or invalid catalogVersion")

        val generatedAt = root.optLong("generatedAt", 0L)
        val modelsArray = root.optJSONArray("models")
            ?: throw CatalogRejectedException("Missing models[]")

        val models = ArrayList<StoreModel>(modelsArray.length())
        for (i in 0 until modelsArray.length()) {
            val obj = modelsArray.optJSONObject(i) ?: continue
            val model = try {
                parseModel(obj)
            } catch (e: CatalogRejectedException) {
                throw e
            } catch (t: Throwable) {
                // A single malformed entry must not cost the user the whole store.
                Log.w(TAG, "Dropping malformed model at index $i: ${t.message}")
                null
            }
            // A model whose every variant was dropped has nothing installable left to show.
            if (model != null && model.variants.isNotEmpty()) models.add(model)
        }

        return StoreCatalog(schemaVersion, catalogVersion, generatedAt, models)
    }

    private fun parseModel(obj: JSONObject): StoreModel {
        val modelId = obj.requireString("modelId")
        val variantsArray = obj.optJSONArray("variants") ?: JSONArray()
        val variants = ArrayList<ModelVariant>(variantsArray.length())
        for (i in 0 until variantsArray.length()) {
            val vObj = variantsArray.optJSONObject(i) ?: continue
            val variant = try {
                parseVariant(vObj)
            } catch (t: EntryUnusable) {
                Log.i(TAG, "Skipping variant in $modelId: ${t.message}")
                null
            } catch (t: Throwable) {
                Log.w(TAG, "Dropping malformed variant in $modelId: ${t.message}")
                null
            }
            if (variant != null) variants.add(variant)
        }

        return StoreModel(
            modelId = modelId,
            displayName = obj.requireString("displayName"),
            publisher = obj.requireString("publisher"),
            description = obj.optString("description", ""),
            tasks = obj.optJSONArray("tasks").toStringList().mapNotNull(ModelTask::fromWire).toSet(),
            languages = obj.optJSONArray("languages").toStringList(),
            license = parseLicense(obj.optJSONObject("license") ?: JSONObject()),
            modelCardUrl = obj.optString("modelCardUrl", ""),
            variants = variants
        )
    }

    private fun parseLicense(obj: JSONObject) = ModelLicense(
        name = obj.optString("name", "Unknown"),
        url = obj.optString("url", ""),
        // Absent means "not reviewed", and an unreviewed entry must not read as permitted.
        redistributionPermitted = obj.optBoolean("redistributionPermitted", false),
        gated = obj.optBoolean("gated", false),
        commercialUseAllowed = obj.optBoolean("commercialUseAllowed", false),
        acceptableUseRestrictions = obj.optJSONArray("acceptableUseRestrictions").toStringList(),
        attributionRequired = obj.optBoolean("attributionRequired", true),
        usageThresholdClause = obj.optString("usageThresholdClause").ifBlank { null },
        acceptanceHash = obj.optString("acceptanceHash", "")
    )

    private fun parseVariant(obj: JSONObject): ModelVariant {
        val variantId = obj.requireString("variantId")

        val runtime = RuntimeId.fromWire(obj.requireString("runtime"))
            ?: throw EntryUnusable("unknown runtime '${obj.optString("runtime")}'")
        if (runtime !in availableRuntimes) {
            throw EntryUnusable("runtime ${runtime.wireName} is not built into this app")
        }

        val subtypeWire = obj.optString("runtimeSubtype").ifBlank { null }
        val subtype = subtypeWire?.let {
            RuntimeSubtype.fromWire(it) ?: throw EntryUnusable("unknown runtimeSubtype '$it'")
        }
        // sherpa-onnx's artifact composition is entirely determined by subtype; without one there
        // is no way to know which roles are mandatory, so the variant is not safely installable.
        if (runtime == RuntimeId.SHERPA_ONNX && subtype == null) {
            throw EntryUnusable("sherpa-onnx variant declares no runtimeSubtype")
        }

        val requirements = parseRequirements(
            obj.optJSONObject("requirements") ?: throw EntryUnusable("no requirements block")
        )
        if (requirements.minAppVersionCode > appVersionCode) {
            throw EntryUnusable("needs app version ${requirements.minAppVersionCode}, this is $appVersionCode")
        }

        val artifactsArray = obj.optJSONArray("artifacts") ?: JSONArray()
        val artifacts = ArrayList<Artifact>(artifactsArray.length())
        for (i in 0 until artifactsArray.length()) {
            val aObj = artifactsArray.optJSONObject(i) ?: continue
            artifacts.add(parseArtifact(aObj))
        }
        if (artifacts.isEmpty()) throw EntryUnusable("no artifacts")

        // runtimeConfig is the only sanctioned way a runtime finds a file, so every mapping must
        // point at an artifact that actually exists in this variant.
        val runtimeConfigObj = obj.optJSONObject("runtimeConfig") ?: JSONObject()
        val runtimeConfig = HashMap<ArtifactRole, String>()
        for (key in runtimeConfigObj.keys()) {
            val role = ArtifactRole.fromWire(key)
                ?: throw EntryUnusable("runtimeConfig names unknown role '$key'")
            val artifactId = runtimeConfigObj.getString(key)
            if (artifacts.none { it.artifactId == artifactId }) {
                throw EntryUnusable("runtimeConfig role '$key' points at missing artifact '$artifactId'")
            }
            runtimeConfig[role] = artifactId
        }
        if (runtimeConfig.isEmpty()) throw EntryUnusable("empty runtimeConfig")

        return ModelVariant(
            variantId = variantId,
            version = obj.requireString("version"),
            runtime = runtime,
            runtimeSubtype = subtype,
            format = obj.optString("format", ""),
            quantization = obj.optString("quantization").ifBlank { null },
            capabilities = obj.optJSONArray("capabilities").toStringList()
                .mapNotNull(ModelTask::fromWire).toSet(),
            totalSizeBytes = obj.optLong("totalSizeBytes", artifacts.sumOf { it.sizeBytes }),
            requirements = requirements,
            artifacts = artifacts,
            runtimeConfig = runtimeConfig,
            defaultContextTokens = obj.optInt("defaultContextTokens", 0).takeIf { it > 0 }
        )
    }

    private fun parseRequirements(obj: JSONObject) = VariantRequirements(
        minAppVersionCode = obj.optInt("minAppVersion", 0),
        minRuntimeVersion = obj.optString("minRuntimeVersion").ifBlank { null },
        supportedAbis = obj.optJSONArray("supportedAbis").toStringList().toSet(),
        estimatedMinRamBytes = obj.optLong("estimatedMinRamBytes", 0L),
        acceleratorRequirement = AcceleratorRequirement.fromWire(
            obj.optString("acceleratorRequirement", "none")
        ) ?: AcceleratorRequirement.NONE
    )

    private fun parseArtifact(obj: JSONObject): Artifact {
        val artifactId = obj.requireString("artifactId")
        val roleWire = obj.requireString("role")
        val role = ArtifactRole.fromWire(roleWire)
            ?: throw EntryUnusable("artifact '$artifactId' has unknown role '$roleWire'")

        val sha256 = obj.requireString("sha256").lowercase()
        if (!SHA256_PATTERN.matches(sha256)) {
            throw EntryUnusable("artifact '$artifactId' has a malformed sha256")
        }

        val sizeBytes = obj.optLong("sizeBytes", -1L)
        if (sizeBytes <= 0L) throw EntryUnusable("artifact '$artifactId' has no sizeBytes")

        val installPath = obj.requireString("installPath")
        validateInstallPath(artifactId, installPath)

        val sourcesArray = obj.optJSONArray("sources") ?: JSONArray()
        val sources = ArrayList<ArtifactSource>(sourcesArray.length())
        for (i in 0 until sourcesArray.length()) {
            val sObj = sourcesArray.optJSONObject(i) ?: continue
            sources.add(parseSource(artifactId, sObj))
        }
        if (sources.isEmpty()) throw EntryUnusable("artifact '$artifactId' has no sources")

        return Artifact(artifactId, role, installPath, sizeBytes, sha256, sources)
    }

    private fun parseSource(artifactId: String, obj: JSONObject): ArtifactSource {
        val providerWire = obj.optString("provider", SourceProvider.HUGGING_FACE.wireName)
        val provider = SourceProvider.fromWire(providerWire)
            ?: throw EntryUnusable("artifact '$artifactId' has unknown provider '$providerWire'")

        val revision = obj.requireString("revision")
        // Spec §14, non-negotiable: a moving ref means the bytes behind the signed hash can change
        // under us. Only a full 40-hex commit SHA is accepted — not `main`, not a tag, not a short
        // SHA (which is ambiguous and can become ambiguous later as the repo grows).
        if (!COMMIT_SHA_PATTERN.matches(revision)) {
            throw EntryUnusable(
                "artifact '$artifactId' pins revision '$revision', which is not an immutable commit SHA"
            )
        }

        val source = ArtifactSource(
            provider = provider,
            repository = obj.requireString("repository"),
            revision = revision,
            path = obj.requireString("path")
        )
        if (!source.toUrl().startsWith("https://")) {
            throw EntryUnusable("artifact '$artifactId' resolves to a non-HTTPS URL")
        }
        return source
    }

    /**
     * Install paths come from a signed catalogue, but signing proves authorship, not correctness —
     * a mistake in the build pipeline should not be able to write outside the variant's own
     * directory. Rejects absolute paths, parent traversal, and anything the store is forbidden to
     * install as an artifact regardless of declared role (spec §10): native libraries, dex, APKs
     * and shell scripts are code, and this pipeline installs data.
     */
    private fun validateInstallPath(artifactId: String, path: String) {
        if (path.isBlank()) throw EntryUnusable("artifact '$artifactId' has a blank installPath")
        if (path.startsWith("/") || path.matches(WINDOWS_ABSOLUTE)) {
            throw EntryUnusable("artifact '$artifactId' has an absolute installPath")
        }
        val segments = path.split('/', '\\')
        if (segments.any { it == ".." }) {
            throw EntryUnusable("artifact '$artifactId' installPath escapes its directory")
        }
        val name = segments.last().lowercase()
        if (FORBIDDEN_EXTENSIONS.any { name.endsWith(it) }) {
            throw EntryUnusable("artifact '$artifactId' has a forbidden executable extension")
        }
    }

    /** Thrown for a model or variant this build cannot safely install. Always caught at the
     * enclosing entry boundary — a bad entry costs that entry, never the catalogue. */
    private class EntryUnusable(message: String) : Exception(message)

    /** Deliberately throws [EntryUnusable], not [CatalogRejectedException]: a field missing from
     * one entry says nothing about whether the rest of the document is trustworthy, and the
     * signature has already established that the whole document came from us. Only genuinely
     * document-level problems (§2's unknown schema, absent `models[]`) reject the catalogue. */
    private fun JSONObject.requireString(key: String): String {
        val value = optString(key)
        if (value.isBlank()) throw EntryUnusable("missing required field '$key'")
        return value
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).ifBlank { null } }
    }

    companion object {
        private const val TAG = "CatalogParser"
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
        private val COMMIT_SHA_PATTERN = Regex("^[0-9a-f]{40}$")
        private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:.*$")
        private val FORBIDDEN_EXTENSIONS = listOf(
            ".so", ".dex", ".apk", ".jar", ".aar", ".sh", ".bash", ".exe", ".dll", ".dylib", ".bin.sh"
        )
    }
}
