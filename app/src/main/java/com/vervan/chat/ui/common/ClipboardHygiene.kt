package com.vervan.chat.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clipboard hygiene — copies [text] normally, then after [clearAfterMs] clears the
 * clipboard only if it still holds exactly what was just copied, so a later, unrelated copy the
 * user made in the meantime isn't wiped out from under them.
 *
 * This can only clear this app's own copy. Android 13+'s OS-level clipboard
 * preview/history is entirely outside app control — nothing at the app layer reaches it.
 */
fun Clipboard.setText(text: String, scope: CoroutineScope) {
    setSensitiveText(text, scope)
}

fun Clipboard.setSensitiveText(text: String, scope: CoroutineScope, clearAfterMs: Long = 30_000) {
    scope.launch {
        setClipEntry(ClipEntry(sensitiveClip("plain text", text)))
        delay(clearAfterMs)
        val currentText = getClipEntry()?.clipData?.let { clip ->
            if (clip.itemCount == 0) null else clip.getItemAt(0).text?.toString()
        }
        if (currentText == text) setClipEntry(null)
    }
}

fun ClipboardManager.setSensitiveText(
    text: String,
    scope: CoroutineScope,
    label: String = "Vervan content",
    clearAfterMs: Long = 30_000
) {
    setSensitiveText(text, label)
    scope.launch {
        delay(clearAfterMs)
        val currentText = primaryClip?.let { current ->
            if (current.itemCount == 0) null else current.getItemAt(0).text?.toString()
        }
        if (currentText == text) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clearPrimaryClip()
            } else {
                setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

fun ClipboardManager.setSensitiveText(text: String, label: String = "Vervan content") {
    setPrimaryClip(sensitiveClip(label, text))
}

private fun sensitiveClip(label: String, text: String): ClipData =
    ClipData.newPlainText(label, text).also { clip ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
    }
