package com.vervan.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.vervan.chat.VervanApp
import com.vervan.chat.data.settings.ThemeMode

/**
 * Theme bridge for entry points that do not run inside MainActivity's lifecycle composition:
 * process-text actions, the screen assistant, and the floating bubble. Keeping this in one place
 * prevents those surfaces from silently reverting to the system theme while the main app uses a
 * saved accent, OLED mode, dynamic color, or contrast preference.
 */
@Composable
fun VervanThemeFromPreferences(
    app: VervanApp,
    content: @Composable () -> Unit,
) {
    val preferences by app.container.settingsRepository.themePreferences.collectAsState(initial = null)
    val resolved = preferences ?: return
    val dark = when (resolved.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    VervanTheme(
        darkTheme = dark,
        oledTrueBlack = resolved.oledTrueBlack,
        dynamicColor = resolved.dynamicColor,
        highContrast = resolved.highContrast,
        accent = resolved.accentTheme,
        content = content,
    )
}
