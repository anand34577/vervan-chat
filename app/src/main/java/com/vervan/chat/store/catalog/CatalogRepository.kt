package com.vervan.chat.store.catalog

import android.content.Context
import android.util.Base64
import android.util.Log
import com.vervan.chat.store.model.StoreCatalog
import com.vervan.chat.system.NetworkAuditLog
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Outcome of one sync attempt. Never fatal to the caller: a failed sync always leaves the
 * previously accepted catalogue in place (spec §4.6). */
sealed interface SyncResult {
    data class Updated(val catalogVersion: Int, val modelCount: Int) : SyncResult
    data object AlreadyCurrent : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Owns the catalogue lifecycle: fetch -> verify -> validate -> rollback-check -> persist, plus the
 * in-APK bootstrap so the store is never empty on first run with no network (spec §3).
 *
 * ### Why `latest.json` is treated as untrusted
 * `latest.json` is **not signed** — only the versioned `catalog.json` is. Everything it says is
 * therefore a hint, not a fact, and an attacker who can control the network can say anything they
 * like in it. Two consequences shape the code below:
 *
 *  1. Its `catalogVersion` is used only as an *early-out optimisation* ("looks like we already
 *     have this, skip the download"). The authoritative rollback check runs against the version
 *     inside the catalogue that actually verified against our embedded key — see [acceptCatalog].
 *     Lying downward in `latest.json` costs an attacker nothing but a skipped update; lying
 *     upward just makes us download a catalogue that then fails the real check.
 *  2. Its `catalogUrl`/`signatureUrl` are constrained to HTTPS on a host allowlist before being
 *     fetched, so a hostile `latest.json` cannot aim the client at an arbitrary endpoint.
 */
class CatalogRepository(
    context: Context,
    private val parser: CatalogParser,
    private val verifier: CatalogSignatureVerifier,
    private val networkAuditLog: NetworkAuditLog,
    private val store: CatalogStore = CatalogStore(context),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
    private val allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS
) {
    private val appContext = context.applicationContext

    private val _catalog = MutableStateFlow<StoreCatalog?>(null)
    /** The catalogue the UI renders. Never null once [initialize] has run — worst case it is the
     * bootstrap snapshot compiled into the APK. */
    val catalog: StateFlow<StoreCatalog?> = _catalog

    private val _lastSyncError = MutableStateFlow<String?>(null)
    /** Surfaced non-blockingly; a sync failure is an advisory, not a dead end. */
    val lastSyncError: StateFlow<String?> = _lastSyncError

    /**
     * Loads the best catalogue available without touching the network: the last accepted one if
     * present, otherwise the bootstrap snapshot from assets. Safe to call on a cold start before
     * any connectivity exists.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val persisted = store.readCatalogJson()?.let { raw ->
            try {
                parser.parse(raw)
            } catch (t: Throwable) {
                // A persisted catalogue that no longer parses means the app was downgraded, or the
                // file is damaged. Fall through to the bootstrap rather than showing nothing.
                Log.w(TAG, "Persisted catalogue no longer parses: ${t.message}")
                null
            }
        }
        _catalog.value = persisted ?: loadBootstrapCatalog()
    }

    /**
     * One full sync attempt. Every network call is recorded to [NetworkAuditLog] first — this app
     * promises the user that all networking is inspectable, and the catalogue sync is the first
     * feature to actually make one.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val pointer = fetchLatestPointer() ?: return@withContext fail("Could not read catalogue index")

            // Early-out only. See the class doc for why this is not the real rollback defence.
            if (pointer.catalogVersion in 1..store.highestAcceptedVersion()) {
                store.recordSyncAttempt()
                _lastSyncError.value = null
                return@withContext SyncResult.AlreadyCurrent
            }

            val catalogUrl = requireAllowed(pointer.catalogUrl)
                ?: return@withContext fail("Catalogue URL is not on the allowed host list")
            val signatureUrl = requireAllowed(pointer.signatureUrl)
                ?: return@withContext fail("Signature URL is not on the allowed host list")

            networkAuditLog.record("Model store: downloading catalogue v${pointer.catalogVersion}")
            val rawCatalog = fetchBytes(catalogUrl, MAX_CATALOG_BYTES)
                ?: return@withContext fail("Could not download catalogue")
            val rawSignature = fetchBytes(signatureUrl, MAX_SIGNATURE_BYTES)
                ?: return@withContext fail("Could not download catalogue signature")

            acceptCatalog(rawCatalog, decodeSignature(rawSignature))
        } catch (t: Throwable) {
            fail(t.message ?: "Catalogue sync failed")
        }
    }

    /**
     * The trust boundary. Order here is not negotiable:
     *
     *   1. **Verify the signature over the raw bytes.** Nothing about the document is believed
     *      before this — not its version, not its schema, not its model list.
     *   2. **Parse and schema-validate**, which fails closed on an unknown `schemaVersion`.
     *   3. **Rollback check against the verified version**, using the value from inside the signed
     *      document rather than the one `latest.json` advertised.
     *   4. **Persist.**
     *
     * Doing (3) before (1) would let an unauthenticated document influence the watermark; doing
     * (4) before (2) would persist a catalogue the app cannot read back.
     */
    private fun acceptCatalog(rawCatalog: ByteArray, signature: ByteArray?): SyncResult {
        if (signature == null || !verifier.verify(rawCatalog, signature)) {
            // Explicitly keep the previous good catalogue — never fall back to the unverified one
            // we just fetched, however plausible it looks (spec §10).
            return fail("Catalogue signature is not valid")
        }

        val json = rawCatalog.toString(Charsets.UTF_8)
        val parsed = try {
            parser.parse(json)
        } catch (e: CatalogRejectedException) {
            return fail(e.message ?: "Catalogue rejected")
        }

        val watermark = store.highestAcceptedVersion()
        if (parsed.catalogVersion < watermark) {
            // A correctly signed but *older* catalogue. This is the replay case: the signature is
            // genuine, which is exactly why the version check has to exist independently of it.
            return fail(
                "Catalogue v${parsed.catalogVersion} is older than the accepted v$watermark; ignoring"
            )
        }
        if (parsed.catalogVersion == watermark) {
            store.recordSyncAttempt()
            _lastSyncError.value = null
            return SyncResult.AlreadyCurrent
        }

        store.commit(json, parsed.catalogVersion)
        _catalog.value = parsed
        _lastSyncError.value = null
        return SyncResult.Updated(parsed.catalogVersion, parsed.models.size)
    }

    /** Walks the endpoint list in priority order (GitHub Pages, then raw.githubusercontent as a
     * fallback) and returns the first pointer that parses. Pages sits behind a CDN with its own
     * TTL, hence the cache-busting query parameter. */
    private fun fetchLatestPointer(): LatestPointer? {
        for (endpoint in endpoints) {
            networkAuditLog.record("Model store: checking catalogue index")
            val bytes = fetchBytes("$endpoint?t=${System.currentTimeMillis()}", MAX_POINTER_BYTES)
                ?: continue
            try {
                val obj = JSONObject(bytes.toString(Charsets.UTF_8))
                return LatestPointer(
                    catalogVersion = obj.optInt("catalogVersion", -1),
                    catalogUrl = obj.optString("catalogUrl"),
                    signatureUrl = obj.optString("signatureUrl"),
                    minimumAppVersion = obj.optInt("minimumAppVersion", 0)
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Malformed latest.json at $endpoint: ${t.message}")
            }
        }
        return null
    }

    /** Signatures are published base64-encoded; tolerate a raw-DER body too so the publishing
     * pipeline can change encoding without stranding installed clients. */
    private fun decodeSignature(raw: ByteArray): ByteArray? = try {
        Base64.decode(raw.toString(Charsets.UTF_8).trim(), Base64.DEFAULT)
    } catch (t: Throwable) {
        raw
    }

    /**
     * Bounded read — [maxBytes] is enforced while streaming, not after, so a hostile or broken
     * endpoint cannot make the client buffer an unbounded response into memory. These are metadata
     * files measured in kilobytes; model weights never come through here.
     */
    private fun fetchBytes(url: String, maxBytes: Int): ByteArray? {
        if (!url.startsWith("https://")) {
            Log.w(TAG, "Refusing non-HTTPS catalogue URL")
            return null
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Catalogue fetch returned HTTP ${connection.responseCode}")
                return null
            }
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (out.size() + read > maxBytes) {
                        Log.w(TAG, "Catalogue response exceeded $maxBytes bytes; aborting")
                        return null
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Catalogue fetch failed for $url: ${t.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Constrains URLs taken from the unsigned pointer to HTTPS on a known host. */
    private fun requireAllowed(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val parsed = URL(url)
            if (parsed.protocol != "https") return null
            if (parsed.host !in allowedHosts) return null
            url
        } catch (t: Throwable) {
            null
        }
    }

    /** The snapshot compiled into the APK. Already signature-verified at build time by virtue of
     * being inside a signed APK, so it is parsed but not re-verified. */
    private fun loadBootstrapCatalog(): StoreCatalog? = try {
        appContext.assets.open(BOOTSTRAP_ASSET).use { input ->
            parser.parse(input.readBytes().toString(Charsets.UTF_8))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "No usable bootstrap catalogue: ${t.message}")
        null
    }

    private fun fail(reason: String): SyncResult.Failed {
        Log.w(TAG, "Catalogue sync failed: $reason")
        _lastSyncError.value = reason
        return SyncResult.Failed(reason)
    }

    private data class LatestPointer(
        val catalogVersion: Int,
        val catalogUrl: String?,
        val signatureUrl: String?,
        val minimumAppVersion: Int
    )

    companion object {
        private const val TAG = "CatalogRepository"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_POINTER_BYTES = 16 * 1024
        private const val MAX_CATALOG_BYTES = 4 * 1024 * 1024
        private const val MAX_SIGNATURE_BYTES = 4 * 1024
        private const val BOOTSTRAP_ASSET = "store/bootstrap-catalog.json"

        // TODO(store): point at the real catalogue repo before shipping.
        private val DEFAULT_ENDPOINTS = listOf(
            "https://your-org.github.io/offline-model-catalog/api/v1/latest.json",
            "https://raw.githubusercontent.com/your-org/offline-model-catalog/main/docs/api/v1/latest.json"
        )
        private val DEFAULT_ALLOWED_HOSTS = setOf(
            "your-org.github.io",
            "raw.githubusercontent.com"
        )
    }
}
