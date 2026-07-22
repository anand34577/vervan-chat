package com.vervan.chat.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced-motion respect — reads the system "remove animations"
 * accessibility setting directly rather than adding a separate in-app toggle; if the user
 * turned this on system-wide, every app should honor it, not just ones that also ask again.
 */
fun isReducedMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { isReducedMotionEnabled(context) }
}
