package com.vervan.chat.ui.quickaction

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.vervan.chat.ui.theme.VervanTheme

/**
 * Phase I — handles `ACTION_PROCESS_TEXT` (the "select text in any app → Vervan Chat" entry
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

        val selectedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val readonly = intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true) ?: true

        setContent {
            VervanTheme(darkTheme = isSystemInDarkTheme()) {
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
