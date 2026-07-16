package com.vervan.chat.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
    } catch (t: Throwable) {
        context.deleteSharedPreferences(name)
        create()
    }
}
