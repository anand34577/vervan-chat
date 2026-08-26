package com.vervan.chat.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.vervan.chat.security.createEncryptedPrefs
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * holds the local API server's bearer token in a Keystore-backed
 * [EncryptedSharedPreferences] file, same reasoning as [com.vervan.chat.security.AppLockManager]'s
 * PIN storage: never in plain DataStore alongside the rest of Settings.
 *
 * The decoded token is cached in memory after first read so that the per-request auth check
 * doesn't decrypt through the Keystore daemon on every HTTP request (which serializes all
 * concurrent API requests through a single daemon). [verify] uses [MessageDigest.isEqual] for a
 * constant-time comparison so a LAN attacker can't recover the token byte-by-byte via 401
 * response latency.
 */
class ApiServerAuth(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_api_server")

    // @Volatile: read on the API server worker threads, mutated only under synchronized(this).
    @Volatile
    private var cached: ByteArray? = null

    /** Returns the existing token, or generates and persists a new one on first call. */
    fun tokenOrGenerate(): String = synchronized(this) {
        val current = cached ?: readStoredToken()
        if (current != null) {
            cached = current
            String(current, Charsets.UTF_8)
        } else {
            regenerate()
        }
    }

    fun regenerate(): String = synchronized(this) {
        val random = ByteArray(TOKEN_RANDOM_BYTES).also { SecureRandom().nextBytes(it) }
        try {
            val token = Base64.encodeToString(
                random,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            prefs.edit().putString(KEY_TOKEN, token).apply()
            cached?.fill(0)
            cached = token.toByteArray(Charsets.UTF_8)
            token
        } finally {
            random.fill(0)
        }
    }

    fun verify(candidate: String): Boolean {
        val expected = cached ?: synchronized(this) {
            cached ?: readStoredToken()?.also { cached = it }
        } ?: return false
        if (!isValidToken(candidate)) return false
        val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
        return try {
            MessageDigest.isEqual(candidateBytes, expected)
        } finally {
            candidateBytes.fill(0)
        }
    }

    private fun readStoredToken(): ByteArray? {
        val token = try {
            prefs.getString(KEY_TOKEN, null)
        } catch (_: Exception) {
            null
        }
        return token?.takeIf(::isValidToken)?.toByteArray(Charsets.UTF_8)
    }

    private fun isValidToken(token: String): Boolean =
        token.length == TOKEN_LENGTH && token.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        }

    companion object {
        private const val KEY_TOKEN = "api_token"
        private const val TOKEN_RANDOM_BYTES = 24
        private const val TOKEN_LENGTH = 32
    }
}
