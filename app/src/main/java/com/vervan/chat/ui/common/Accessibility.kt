package com.vervan.chat.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced-motion respect — reads the system "remove animations"
 * accessibility setting directly rather than adding a separate in-app toggle; if the user
 * turned this on system-wide, every app should honor it, not just ones that also ask again.
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    val animatorScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    val transitionScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    return animatorScale <= 0f || transitionScale <= 0f
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resumeTick = rememberOnResumeTick()
    return androidx.compose.runtime.remember(context, resumeTick) { isReducedMotionEnabled(context) }
}
