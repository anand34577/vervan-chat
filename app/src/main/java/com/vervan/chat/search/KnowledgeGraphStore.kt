package com.vervan.chat.search

import android.content.Context
import android.content.SharedPreferences
import com.vervan.chat.security.createEncryptedPrefs

/**
 * Holds the user's Google Knowledge Graph Search API key in a Keystore-backed
 * [EncryptedSharedPreferences] file — same reasoning as
 * [com.vervan.chat.modeldownload.HuggingFaceTokenStore]'s HF token,
 * [com.vervan.chat.server.ApiServerAuth]'s bearer token, and
 * [com.vervan.chat.security.AppLockManager]'s PIN: never in plain DataStore alongside
 * the rest of Settings.
 *
 * The Knowledge Graph API is free (100k calls/day) but requires an API key obtained
 * from Google Cloud Console. Without a key, [KnowledgeGraphClient] refuses to call
 * the endpoint rather than making an unauthenticated request that would only 403.
 */
class KnowledgeGraphStore(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_kg_api_key")

    fun get(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun set(apiKey: String?) {
        if (apiKey.isNullOrBlank()) prefs.edit().remove(KEY_API_KEY).apply()
        else prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    companion object {
        private const val KEY_API_KEY = "kg_api_key"
    }
}
