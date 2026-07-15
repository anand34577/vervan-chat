package com.vervan.chat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runtime permissions can be revoked at any time from outside the app (system Settings, or
 * Android auto-resetting permissions for unused apps) with no callback to tell the app it
 * happened. A screen showing "granted/not granted" needs to re-check on every return to the
 * foreground, not just once at first composition — this bumps an Int each `ON_RESUME` so a
 * `checkSelfPermission` call sitting in the same composable's body re-runs instead of showing
 * a status that was only ever accurate the moment the screen first opened.
 */
@Composable
fun rememberOnResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}
