package com.vervan.chat.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppLockMethod { BIOMETRIC, PIN, BOTH }

/**
 * Privacy hardening (Phase A) — owns the app-lock's locked/unlocked state and PIN storage.
 * Mirrors [com.vervan.chat.model.WorkspaceManager]'s role as a small orchestrator over a single
 * concern, not a general repository.
 *
 * The PIN is never stored in [com.vervan.chat.data.settings.SettingsRepository]'s plain
 * DataStore — it lives in a Keystore-backed [EncryptedSharedPreferences] file here, as a
 * PBKDF2 hash + random salt (stdlib javax.crypto, no new crypto dependency), never in plaintext.
 */
class AppLockManager(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "vervan_lock", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Starts locked — if app lock is enabled, cold start should show the gate; if it's
    // disabled, this flag is simply never consulted (see LockScreen's gating), so defaulting
    // to locked costs nothing and avoids a race with reading the enabled flag from DataStore.
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked

    @Volatile private var backgroundedAt: Long? = null

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun clearPin() { prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply() }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val candidate = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP)
        // Constant-time-ish compare isn't critical here — this gates local UI access behind a
        // 4+ digit PIN with device-level rate limiting via the lock screen itself, not a
        // network-facing auth endpoint where timing attacks are a real concern.
        return candidate == hashB64
    }

    /** Locks immediately — cold start with lock enabled, or a manual "Lock now" action. */
    fun lockNow() { _isLocked.value = true }

    fun unlock() { _isLocked.value = false; backgroundedAt = null }

    fun onAppBackgrounded() { backgroundedAt = System.currentTimeMillis() }

    /** Re-locks if the app was backgrounded for at least [timeoutSeconds]. Called from
     * ProcessLifecycleOwner's ON_START, only meaningful when app lock is enabled. */
    fun onAppForegrounded(timeoutSeconds: Int) {
        val since = backgroundedAt
        if (since != null && System.currentTimeMillis() - since >= timeoutSeconds * 1000L) {
            _isLocked.value = true
        }
        backgroundedAt = null
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val PBKDF2_ITERATIONS = 120_000
    }
}
