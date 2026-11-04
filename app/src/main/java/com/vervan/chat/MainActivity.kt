package com.vervan.chat

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.update
import com.vervan.chat.data.settings.ThemeMode
import com.vervan.chat.security.AppLockMethod
import com.vervan.chat.ui.lock.LockScreen
import com.vervan.chat.ui.nav.VervanNavGraph
import com.vervan.chat.ui.theme.VervanTheme
import java.util.UUID

class MainActivity : FragmentActivity() {
    private var intentVersion by mutableIntStateOf(0)
    private var incomingShare by mutableStateOf<IncomingShare?>(null)
    // The shortcut waiting to be handled. Cleared (and stripped from the intent) once the nav graph
    // acts on it, so a configuration change or process restore can't replay it and create a
    // second chat/note.
    private var pendingShortcut by mutableStateOf<String?>(null)
    private var shareIntentConsumed = false
    private var shortcutConsumed = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Holds the system splash until Compose has drawn its first frame, then hands over to
        // Theme.Vervan (see Theme.Vervan.Starting) without a blank window in between.
        val splash = installSplashScreen()
        var firstFrameReady = false
        splash.setKeepOnScreenCondition { !firstFrameReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareIntentConsumed = savedInstanceState?.getBoolean(STATE_SHARE_CONSUMED) == true
        incomingShare = if (shareIntentConsumed) null else intent.toIncomingShare(contentResolver)
        shortcutConsumed = savedInstanceState?.getBoolean(STATE_SHORTCUT_CONSUMED) == true
        pendingShortcut = if (shortcutConsumed) null else extractShortcut(intent)
        val app = application as VervanApp
        // DataStore is async, so the first frames used hard-coded defaults: a dark flash for
        // light-theme users and, worse, the unlocked UI for a frame before app lock kicked in.
        // A tiny synchronous mirror of those few values gives the first frame the right answer.
        val mirror = getSharedPreferences(UI_MIRROR_PREFS, MODE_PRIVATE)
        val initialTheme = runCatching { ThemeMode.valueOf(mirror.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        val initialLock = mirror.getBoolean(KEY_APP_LOCK, false)
        if (initialLock) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val themeMode by app.container.settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = initialTheme)
            val fontScale by app.container.settingsRepository.fontScale.collectAsStateWithLifecycle(initialValue = 1.0f)
            val oledTrueBlack by app.container.settingsRepository.oledTrueBlack.collectAsStateWithLifecycle(initialValue = false)
            val dynamicColor by app.container.settingsRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
            val highContrast by app.container.settingsRepository.highContrast.collectAsStateWithLifecycle(initialValue = false)
            val largeTouchTargets by app.container.settingsRepository.largeTouchTargets.collectAsStateWithLifecycle(initialValue = false)
            val accentTheme by app.container.settingsRepository.accentTheme.collectAsStateWithLifecycle(initialValue = com.vervan.chat.data.settings.AccentTheme.GREEN)
            val appLockEnabled by app.container.settingsRepository.appLockEnabled.collectAsStateWithLifecycle(initialValue = initialLock)
            LaunchedEffect(themeMode, appLockEnabled) {
                mirror.edit().putString(KEY_THEME, themeMode.name).putBoolean(KEY_APP_LOCK, appLockEnabled).apply()
            }
            val appLockMethodName by app.container.settingsRepository.appLockMethod.collectAsStateWithLifecycle(initialValue = "BIOMETRIC")
            val isLocked by app.container.appLockManager.isLocked.collectAsStateWithLifecycle()
            val screenshotBlockingEnabled by app.container.settingsRepository.screenshotBlockingEnabled.collectAsStateWithLifecycle(initialValue = false)
            // App lock and the app-wide screenshot block each contribute their own reason to the
            // shared set, independently, so neither clears a flag the other still needs.
            LaunchedEffect(appLockEnabled) {
                app.container.secureWindowReasons.update { reasons -> if (appLockEnabled) reasons + "app_lock" else reasons - "app_lock" }
            }
            LaunchedEffect(screenshotBlockingEnabled) {
                app.container.secureWindowReasons.update { reasons -> if (screenshotBlockingEnabled) reasons + "screenshot_block" else reasons - "screenshot_block" }
            }
            val secureReasons by app.container.secureWindowReasons.collectAsStateWithLifecycle()
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
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            androidx.compose.runtime.SideEffect { firstFrameReady = true }
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
                        Box(
                            if (appLockEnabled && isLocked) {
                                Modifier.fillMaxSize().clearAndSetSemantics { }
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                            VervanNavGraph(
                                app = app,
                                incomingShare = incomingShare.takeUnless { appLockEnabled && isLocked },
                                onShareConsumed = ::consumeShare,
                                shortcut = pendingShortcut.takeUnless { appLockEnabled && isLocked },
                                onShortcutConsumed = ::consumeShortcut,
                                intentVersion = intentVersion,
                                windowSizeClass = windowSizeClass
                            )
                        }
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
        shareIntentConsumed = false
        shortcutConsumed = false
        pendingShortcut = extractShortcut(intent)
        incomingShare = intent.toIncomingShare(contentResolver)
        intentVersion++
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((event.isCtrlPressed || event.isMetaPressed) && event.repeatCount == 0) {
            val shortcut = when (keyCode) {
                KeyEvent.KEYCODE_N -> "new_chat"
                KeyEvent.KEYCODE_K -> "search"
                KeyEvent.KEYCODE_COMMA -> "settings"
                else -> null
            }
            if (shortcut != null) {
                pendingShortcut = shortcut
                intentVersion++
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SHARE_CONSUMED, shareIntentConsumed)
        outState.putBoolean(STATE_SHORTCUT_CONSUMED, shortcutConsumed)
        super.onSaveInstanceState(outState)
    }

    private fun consumeShortcut() {
        shortcutConsumed = true
        pendingShortcut = null
        intent?.removeExtra("vervan_shortcut")
    }

    private fun consumeShare() {
        shareIntentConsumed = true
        incomingShare = null
        setIntent(Intent(intent).apply {
            action = null
            type = null
            clipData = null
            removeExtra(Intent.EXTRA_STREAM)
            removeExtra(Intent.EXTRA_TEXT)
            removeExtra(Intent.EXTRA_HTML_TEXT)
            removeExtra(Intent.EXTRA_SUBJECT)
        })
    }

    /**
     * Resolves a launcher-shortcut extra into a deep navigation target.
     *
     * MainActivity is exported, so any third-party app can send `vervan_shortcut`. Validate the
     * value here at the trust boundary rather than letting an untrusted string reach
     * [VervanNavGraph], where it would be interpolated into a NavController route (an unparseable
     * route throws IllegalArgumentException and crashes the app from outside). Allowed shapes:
     *  - the static keywords new_chat / voice / capture / search / settings
     *  - open_chat:<UUID> (the shape used by Chat's generated primary key)
     */
    private fun extractShortcut(intent: Intent?): String? {
        val raw = intent?.getStringExtra("vervan_shortcut") ?: return null
        if (raw in STATIC_SHORTCUTS) return raw
        if (raw.startsWith("open_chat:")) {
            val id = raw.removePrefix("open_chat:")
            if (runCatching { UUID.fromString(id) }.isSuccess) return "open_chat:$id"
        }
        return null
    }

    private companion object {
        const val STATE_SHARE_CONSUMED = "share_intent_consumed"
        const val STATE_SHORTCUT_CONSUMED = "shortcut_consumed"
        const val UI_MIRROR_PREFS = "vervan_ui_mirror"
        const val KEY_THEME = "theme_mode"
        const val KEY_APP_LOCK = "app_lock_enabled"
        val STATIC_SHORTCUTS = setOf("new_chat", "voice", "capture", "search", "settings")
    }
}
