package com.vervan.chat

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

enum class IncomingShareKind { TEXT, IMAGE, DOCUMENT }

data class IncomingShare(
    val kind: IncomingShareKind,
    val text: String? = null,
    val uri: Uri? = null,
)

internal fun classifyIncomingShare(mimeType: String?, fileName: String?): IncomingShareKind {
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val extension = fileName?.substringAfterLast('.', "")?.lowercase()
    return if (normalizedMime?.startsWith("image/") == true || extension in IMAGE_EXTENSIONS) {
        IncomingShareKind.IMAGE
    } else {
        IncomingShareKind.DOCUMENT
    }
}

internal fun mergeSharedText(subject: String?, body: String?): String? {
    val cleanSubject = subject?.trim().orEmpty()
    val cleanBody = body?.trim().orEmpty()
    return when {
        cleanSubject.isEmpty() -> cleanBody.ifEmpty { null }
        cleanBody.isEmpty() || cleanBody == cleanSubject || cleanBody.startsWith("$cleanSubject\n") ->
            cleanBody.ifEmpty { cleanSubject }
        else -> "$cleanSubject\n\n$cleanBody"
    }
}

internal fun Intent.toIncomingShare(contentResolver: ContentResolver): IncomingShare? {
    if (action != Intent.ACTION_SEND) return null

    val uri = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    } ?: clipData?.let { data ->
        (0 until data.itemCount).firstNotNullOfOrNull { data.getItemAt(it).uri }
    }

    val clipText = clipData?.let { data ->
        (0 until data.itemCount).firstNotNullOfOrNull { data.getItemAt(it).text?.toString() }
    }
    val text = mergeSharedText(
        getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: clipText,
    )
    if (uri == null) return text?.let { IncomingShare(IncomingShareKind.TEXT, text = it) }

    val providerMime = runCatching { contentResolver.getType(uri) }.getOrNull()
    val resolvedMime = providerMime?.takeUnless { it == "*/*" || it == "application/octet-stream" } ?: type
    val displayName = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
        }
    }.getOrNull() ?: uri.lastPathSegment

    return IncomingShare(
        kind = classifyIncomingShare(resolvedMime, displayName),
        text = text,
        uri = uri,
    )
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
