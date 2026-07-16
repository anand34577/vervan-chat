package com.vervan.chat.modeldownload

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.vervan.chat.security.createEncryptedPrefs

/**
 * Optional Hugging Face access token for gated model repositories — same Keystore-backed
 * [EncryptedSharedPreferences] pattern as [com.vervan.chat.security.AppLockManager]'s PIN and
 * [com.vervan.chat.server.ApiServerAuth]'s bearer token, never in plain DataStore. Entirely
 * optional: when absent, [HttpRangeDownloader] just makes unauthenticated requests, which is
 * all the two built-in catalogue entries need.
 */
class HuggingFaceTokenStore(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_hf_token")

    fun get(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun set(token: String?) {
        if (token.isNullOrBlank()) prefs.edit().remove(KEY_TOKEN).apply()
        else prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    companion object {
        private const val KEY_TOKEN = "hf_token"
    }
}
