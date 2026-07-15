package com.vervan.chat.ui.common

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clipboard hygiene (Phase H) — copies [text] normally, then after [clearAfterMs] clears the
 * clipboard only if it still holds exactly what was just copied, so a later, unrelated copy the
 * user made in the meantime isn't wiped out from under them.
 *
 * ponytail: this can only clear this app's own copy. Android 13+'s OS-level clipboard
 * preview/history is entirely outside app control — nothing at the app layer reaches it.
 */
fun ClipboardManager.setSensitiveText(text: String, scope: CoroutineScope, clearAfterMs: Long = 30_000) {
    setText(AnnotatedString(text))
    scope.launch {
        delay(clearAfterMs)
        if (getText()?.text == text) setText(AnnotatedString(""))
    }
}
