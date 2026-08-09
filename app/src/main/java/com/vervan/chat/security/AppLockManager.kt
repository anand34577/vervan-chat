package com.vervan.chat.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppLockMethod { BIOMETRIC, PIN, BOTH }

/**
 * Privacy hardening — owns the app-lock's locked/unlocked state and PIN storage.
 * Mirrors [com.vervan.chat.model.WorkspaceManager]'s role as a small orchestrator over a single
 * concern, not a general repository.
 *
 * The PIN is never stored in [com.vervan.chat.data.settings.SettingsRepository]'s plain
 * DataStore — it lives in a Keystore-backed [EncryptedSharedPreferences] file here, as a
 * PBKDF2 hash + random salt (stdlib javax.crypto, no new crypto dependency), never in plaintext.
 */
class AppLockManager(context: Context) {
    // A corrupted/invalidated Keystore key (OS upgrade, cloud restore, FRP re-provision) makes
    // EncryptedSharedPreferences.create throw instead of silently recovering — since this runs
    // from Application.onCreate(), an uncaught throw here crashes every single app launch.
    // Android's own documented recovery is to delete the corrupted prefs file so a fresh master
    // key gets created; that costs the user their PIN (they re-set it), not their whole app.
    private val prefs: SharedPreferences = createEncryptedPrefs(context, "vervan_lock")

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
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (pinLockoutRemainingMs() > 0) return false
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val correct = MessageDigest.isEqual(hash(pin, salt), expected)
        if (correct) {
            prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKED_UNTIL).apply()
        } else {
            val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            if (failedAttempts >= MAX_PIN_ATTEMPTS) {
                prefs.edit()
                    .remove(KEY_FAILED_ATTEMPTS)
                    .putLong(KEY_LOCKED_UNTIL, SystemClock.elapsedRealtime() + PIN_LOCKOUT_MS)
                    .apply()
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts).apply()
            }
        }
        return correct
    }

    /** [SystemClock.elapsedRealtime], not [System.currentTimeMillis] — the lockout used to be
     * measured against the wall clock, which anyone with the device unlocked can wind backward
     * from Settings to erase the wait and keep brute-forcing the PIN with no lockout at all.
     * elapsedRealtime can't be set by the user, but it does reset to ~0 on reboot while
     * [KEY_LOCKED_UNTIL] stays on disk as an absolute elapsedRealtime value from before the
     * reboot — so `stored - new elapsedRealtime` stays large instead of going negative, and an
     * uncapped remaining time could then read as "locked out for hours" (however long the device
     * had been up when the lockout was set) instead of clearing. Capping at [PIN_LOCKOUT_MS]
     * bounds the worst case to the intended lockout length regardless of what elapsedRealtime did
     * across a reboot. */
    fun pinLockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCKED_UNTIL, 0) - SystemClock.elapsedRealtime()).coerceIn(0, PIN_LOCKOUT_MS)

    /** Locks immediately — cold start with lock enabled, or a manual "Lock now" action. */
    fun lockNow() { _isLocked.value = true }

    fun unlock() { _isLocked.value = false; backgroundedAt = null }

    fun onAppBackgrounded() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Re-locks if the app was backgrounded for at least [timeoutSeconds]. Called from
     * ProcessLifecycleOwner's ON_START, only meaningful when app lock is enabled.
     * [SystemClock.elapsedRealtime], not the wall clock — same reasoning as
     * [pinLockoutRemainingMs]: winding the date forward used to let anyone skip straight past
     * this timeout instead of actually waiting it out. */
    fun onAppForegrounded(timeoutSeconds: Int) {
        val since = backgroundedAt
        if (since != null && SystemClock.elapsedRealtime() - since >= timeoutSeconds * 1000L) {
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
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val MAX_PIN_ATTEMPTS = 5
        private const val PIN_LOCKOUT_MS = 30_000L
    }
}
