package com.vervan.chat

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.update
import com.vervan.chat.data.settings.ThemeMode
import com.vervan.chat.security.AppLockMethod
import com.vervan.chat.ui.lock.LockScreen
import com.vervan.chat.ui.nav.VervanNavGraph
import com.vervan.chat.ui.theme.VervanTheme

class MainActivity : FragmentActivity() {
    private var intentVersion by mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VervanApp
        setContent {
            val themeMode by app.container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val fontScale by app.container.settingsRepository.fontScale.collectAsState(initial = 1.0f)
            val oledTrueBlack by app.container.settingsRepository.oledTrueBlack.collectAsState(initial = false)
            val dynamicColor by app.container.settingsRepository.dynamicColor.collectAsState(initial = false)
            val highContrast by app.container.settingsRepository.highContrast.collectAsState(initial = false)
            val largeTouchTargets by app.container.settingsRepository.largeTouchTargets.collectAsState(initial = false)
            val accentTheme by app.container.settingsRepository.accentTheme.collectAsState(initial = com.vervan.chat.data.settings.AccentTheme.AMBER)
            val appLockEnabled by app.container.settingsRepository.appLockEnabled.collectAsState(initial = false)
            val appLockMethodName by app.container.settingsRepository.appLockMethod.collectAsState(initial = "BIOMETRIC")
            val isLocked by app.container.appLockManager.isLocked.collectAsState()
            val screenshotBlockingEnabled by app.container.settingsRepository.screenshotBlockingEnabled.collectAsState(initial = false)
            // App lock and the app-wide screenshot block each contribute their own reason to the
            // shared set, independently, so neither clears a flag the other still needs.
            LaunchedEffect(appLockEnabled) {
                app.container.secureWindowReasons.update { reasons -> if (appLockEnabled) reasons + "app_lock" else reasons - "app_lock" }
            }
            LaunchedEffect(screenshotBlockingEnabled) {
                app.container.secureWindowReasons.update { reasons -> if (screenshotBlockingEnabled) reasons + "screenshot_block" else reasons - "screenshot_block" }
            }
            val secureReasons by app.container.secureWindowReasons.collectAsState()
            // FLAG_SECURE blocks screenshots/screen recording, and blanks the recent-apps
            // thumbnail "for free" — Android does that automatically for a FLAG_SECURE window.
            LaunchedEffect(secureReasons) {
                if (secureReasons.isNotEmpty()) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            val baseDensity = LocalDensity.current
            val windowSizeClass = calculateWindowSizeClass(this)
            VervanTheme(
                darkTheme = darkTheme,
                oledTrueBlack = oledTrueBlack,
                dynamicColor = dynamicColor,
                highContrast = highContrast,
                accent = accentTheme
            ) {
                CompositionLocalProvider(
                    // App text sizing augments Android's accessibility font scale instead of
                    // replacing it. The previous value silently reset a user's system Large
                    // text setting whenever the in-app slider was left at its 1.0 default.
                    LocalDensity provides androidx.compose.ui.unit.Density(
                        baseDensity.density,
                        baseDensity.fontScale * fontScale
                    ),
                    LocalMinimumInteractiveComponentSize provides if (largeTouchTargets) 56.dp else 48.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        VervanNavGraph(
                            app = app,
                            sharedText = extractSharedText(intent),
                            sharedImageUri = extractSharedImageUri(intent),
                            shortcut = extractShortcut(intent),
                            intentVersion = intentVersion,
                            windowSizeClass = windowSizeClass
                        )
                        if (appLockEnabled && isLocked) {
                            LockScreen(
                                activity = this@MainActivity,
                                appLockManager = app.container.appLockManager,
                                method = runCatching { AppLockMethod.valueOf(appLockMethodName) }.getOrDefault(AppLockMethod.BIOMETRIC)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentVersion++
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
    }

    private fun extractSharedImageUri(intent: Intent?): android.net.Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    /** Resolves a launcher-shortcut extra (spec §37.3) into a deep navigation target. */
    private fun extractShortcut(intent: Intent?): String? =
        intent?.getStringExtra("vervan_shortcut")
}
