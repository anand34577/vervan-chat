package com.vervan.chat.llm

import android.content.Context
import android.content.SharedPreferences
import com.vervan.chat.security.createEncryptedPrefs

/**
 * Holds bearer API keys for external OpenAI-compatible [com.vervan.chat.data.db.entities.
 * ModelInfo] rows (`engine == REMOTE_API`), one per model id, in a Keystore-backed
 * [EncryptedSharedPreferences] file — same reasoning as [com.vervan.chat.security.AppLockManager]'s
 * PIN and [com.vervan.chat.server.ApiServerAuth]'s bearer token: never in the plain Room row or
 * plain DataStore alongside the rest of Settings/model config.
 *
 * Keyed by [com.vervan.chat.data.db.entities.ModelInfo.id] (this app's own local id, stable for
 * the model row's lifetime), not by provider/endpoint — two remote model rows pointing at the
 * same provider with different keys (e.g. two OpenAI accounts) are stored independently.
 */
class RemoteApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_remote_api_keys")

    fun get(modelId: String): String? = prefs.getString(keyFor(modelId), null)

    fun set(modelId: String, apiKey: String) {
        prefs.edit().putString(keyFor(modelId), apiKey).apply()
    }

    /** Called when a REMOTE_API model row is deleted — an orphaned key left behind would be a
     * silent, unbounded leak of every API key the user ever removed. */
    fun remove(modelId: String) {
        prefs.edit().remove(keyFor(modelId)).apply()
    }

    private fun keyFor(modelId: String) = "key_$modelId"
}
