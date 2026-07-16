package com.vervan.chat.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import com.vervan.chat.security.createEncryptedPrefs
import java.security.SecureRandom

/**
 * Phase J — holds the local API server's bearer token in a Keystore-backed
 * [EncryptedSharedPreferences] file, same reasoning as [com.vervan.chat.security.AppLockManager]'s
 * PIN storage: never in plain DataStore alongside the rest of Settings.
 */
class ApiServerAuth(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_api_server")

    /** Returns the existing token, or generates and persists a new one on first call. */
    fun tokenOrGenerate(): String = prefs.getString(KEY_TOKEN, null) ?: regenerate()

    fun regenerate(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun verify(candidate: String): Boolean = candidate.isNotBlank() && candidate == prefs.getString(KEY_TOKEN, null)

    companion object {
        private const val KEY_TOKEN = "api_token"
    }
}
