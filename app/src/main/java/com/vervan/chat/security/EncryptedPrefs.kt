package com.vervan.chat.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

private const val TAG = "EncryptedPrefs"

/** Creates a Keystore-backed [EncryptedSharedPreferences] file, self-healing once if the
 * backing Keystore key is corrupted/invalidated (OS upgrade, cloud restore, FRP re-provision) —
 * a real, recurring failure mode on some OEMs. Without this, callers on Application.onCreate()
 * (see [com.vervan.chat.security.AppLockManager], [com.vervan.chat.server.ApiServerAuth]) would
 * crash on every single launch until the user manually cleared app data. */
fun createEncryptedPrefs(context: Context, name: String): SharedPreferences {
    fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, name, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    return try {
        create()
    } catch (failure: Exception) {
        if (!failure.isEncryptedPrefsKeyFailure()) throw failure
        Log.w(TAG, "Encrypted prefs '$name' Keystore key invalid, recreating (values will be lost)", failure)
        context.deleteSharedPreferences(name)
        create()
    }
}

/** Only confirmed key invalidation/ciphertext-authentication failures justify destructive
 * recovery. I/O, permission, programming, and VM failures must be surfaced without deleting the
 * last recoverable copy of the user's secrets. */
internal fun Throwable.isEncryptedPrefsKeyFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is android.security.keystore.KeyPermanentlyInvalidatedException ||
            current is UnrecoverableKeyException ||
            current is InvalidKeyException ||
            current is AEADBadTagException ||
            current is BadPaddingException
        ) return true
        current = current.cause.takeUnless { it === current }
    }
    return false
}
