package com.vervan.chat.store.license

import android.content.Context
import android.util.Log
import com.vervan.chat.store.model.ModelLicense
import com.vervan.chat.store.model.StoreModel
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable record of every licence the user has tapped to accept (spec §11).
 *
 * Acceptance is keyed on `modelId` **plus the licence's [ModelLicense.acceptanceHash]**, not on
 * modelId alone. That is what makes the re-prompt rule work: if a later catalogue version changes
 * a model's licence text or URL, its hash changes, the previous acceptance no longer matches, and
 * the user is asked again rather than being silently held to terms they never saw.
 *
 * Each record keeps the timestamp and the catalogue version it was accepted under, so a
 * rightsholder query about a specific model can be answered precisely rather than approximately.
 */
class LicenseAcceptanceStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "store/license-acceptances.json"))

    data class Acceptance(
        val modelId: String,
        val licenseHash: String,
        val licenseName: String,
        val acceptedAt: Long,
        val catalogVersion: Int
    )

    /** True when this exact licence text has been accepted for this model. A changed hash reads as
     * "not accepted", which is the re-prompt. */
    fun isAccepted(modelId: String, licenseHash: String): Boolean =
        readAll().any { it.modelId == modelId && it.licenseHash == licenseHash }

    fun isAccepted(model: StoreModel): Boolean =
        isAccepted(model.modelId, model.license.acceptanceHash)

    fun accept(model: StoreModel, catalogVersion: Int) {
        val existing = readAll().filterNot {
            it.modelId == model.modelId && it.licenseHash == model.license.acceptanceHash
        }
        val updated = existing + Acceptance(
            modelId = model.modelId,
            licenseHash = model.license.acceptanceHash,
            licenseName = model.license.name,
            acceptedAt = System.currentTimeMillis(),
            catalogVersion = catalogVersion
        )
        write(updated)
    }

    /** Full history, for an "About this model" / compliance view. Superseded acceptances for
     * earlier licence versions are deliberately retained — the fact that the user once agreed to
     * an older licence is part of the record, not something to overwrite. */
    fun readAll(): List<Acceptance> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Acceptance(
                    modelId = obj.optString("modelId"),
                    licenseHash = obj.optString("licenseHash"),
                    licenseName = obj.optString("licenseName"),
                    acceptedAt = obj.optLong("acceptedAt"),
                    catalogVersion = obj.optInt("catalogVersion")
                )
            }
        } catch (t: Throwable) {
            // A damaged file must not read as "everything is accepted" — an unreadable record is
            // treated as no record, so the user is asked again.
            Log.w(TAG, "Licence acceptance file unreadable, treating as empty: ${t.message}")
            emptyList()
        }
    }

    private fun write(acceptances: List<Acceptance>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        acceptances.forEach {
            array.put(
                JSONObject()
                    .put("modelId", it.modelId)
                    .put("licenseHash", it.licenseHash)
                    .put("licenseName", it.licenseName)
                    .put("acceptedAt", it.acceptedAt)
                    .put("catalogVersion", it.catalogVersion)
            )
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }

    companion object {
        private const val TAG = "LicenseAcceptanceStore"
    }
}
