package com.vervan.chat.ui.quickaction

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.theme.VervanThemeFromPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * handles `ACTION_PROCESS_TEXT` (the "select text in any app → Vervan Chat" entry
 * point declared in AndroidManifest.xml). Deliberately a standalone [ComponentActivity], not
 * routed through [com.vervan.chat.ui.nav.VervanNavGraph] — it's launched directly by another
 * app with no relationship to this app's own back stack, and is themed as a compact floating
 * window (`Theme.Vervan.Dialog`) rather than a full screen.
 *
 * Uses [isSystemInDarkTheme] directly rather than threading the full
 * [com.vervan.chat.data.settings.SettingsRepository] theme/accent/OLED preferences through like
 * [com.vervan.chat.MainActivity] does — a transient popup doesn't need full theme parity with
 * the main app; add it if that turns out to matter in practice.
 */
class QuickActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // This exported activity can display selected text from another app. Keep the window
        // secure until the complete privacy snapshot is known so startup cannot briefly expose
        // that text in screenshots or the Recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val selectedText = com.vervan.chat.system.runCatchingPreservingCancellation {
            intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        }.getOrDefault("")
        val readonly = com.vervan.chat.system.runCatchingPreservingCancellation {
            intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true) ?: true
        }.getOrDefault(true)
        val app = application as VervanApp

        // Process-text is an exported activity and can write notes/library output. It must obey
        // the same app-lock boundary as MainActivity instead of becoming a private-data side
        // door while the main task is locked.
        lifecycleScope.launch {
            val security = com.vervan.chat.system.runCatchingPreservingCancellation {
                app.container.settingsRepository.securityPreferences.first()
            }.getOrNull()
            val lockEnabled = security?.appLockEnabled ?: true
            if (lockEnabled && app.container.appLockManager.isLocked.value) {
                startActivity(
                    Intent(this@QuickActionActivity, com.vervan.chat.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
                return@launch
            }
            if (security != null && !security.appLockEnabled && !security.screenshotBlockingEnabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            setContent {
                VervanThemeFromPreferences(app) {
                    QuickActionScreen(
                        originalText = selectedText,
                        canInsertBack = !readonly,
                        onInsertBack = { result ->
                            setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}
