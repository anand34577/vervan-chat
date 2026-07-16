package com.vervan.chat.ui.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
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
fun Clipboard.setText(text: String, scope: CoroutineScope) {
    scope.launch {
        setClipEntry(ClipEntry(ClipData.newPlainText("plain text", text)))
    }
}

fun Clipboard.setSensitiveText(text: String, scope: CoroutineScope, clearAfterMs: Long = 30_000) {
    scope.launch {
        setClipEntry(ClipEntry(ClipData.newPlainText("plain text", text)))
        delay(clearAfterMs)
        val currentText = getClipEntry()?.clipData?.let { clip ->
            if (clip.itemCount == 0) null else clip.getItemAt(0).text?.toString()
        }
        if (currentText == text) setClipEntry(null)
    }
}
