package com.vervan.chat.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** "Open with…" escape hatch for a file this app can't render natively — same
 * FileProvider/ACTION_VIEW/createChooser pattern already used by ChatInfoScreen and
 * DocumentViewerScreen, factored out here since a third call site needs it too. */
fun openWithExternalApp(context: Context, file: File, mimeType: String) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with…")) }
}
