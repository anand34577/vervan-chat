package com.vervan.chat.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Opens a private app file externally and always explains why that could not happen. */
fun openWithExternalApp(context: Context, file: File, mimeType: String) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "The original file is no longer available on this device.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with…")) }
        .onFailure {
            android.widget.Toast.makeText(context, "No installed app can open this file type.", android.widget.Toast.LENGTH_LONG).show()
        }
}
