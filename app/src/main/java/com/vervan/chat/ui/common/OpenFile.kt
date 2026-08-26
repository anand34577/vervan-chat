package com.vervan.chat.ui.common

import android.content.Context
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Opens a private app file externally and always explains why that could not happen. */
fun openWithExternalApp(context: Context, file: File, mimeType: String) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "The original file is no longer available on this device.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "This private file cannot be shared.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    com.vervan.chat.system.runCatchingPreservingCancellation {
        context.startActivity(Intent.createChooser(intent, "Open with…"))
    }
        .onFailure {
            android.widget.Toast.makeText(context, "No installed app can open this file type.", android.widget.Toast.LENGTH_LONG).show()
        }
}

/** Shares one or more private files through the platform share sheet. */
fun shareWithExternalApps(context: Context, files: List<File>, mimeType: String) {
    val existing = files.filter { it.exists() }
    if (existing.isEmpty()) {
        android.widget.Toast.makeText(context, "The exported file is no longer available on this device.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val uris = try {
        existing.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "One or more private files cannot be shared.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
        .setType(mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply {
            clipData = ClipData.newRawUri("Vervan export", uris.first())
            uris.drop(1).forEach { clipData?.addItem(ClipData.Item(it)) }
        }
    com.vervan.chat.system.runCatchingPreservingCancellation {
        context.startActivity(Intent.createChooser(intent, "Share with…"))
    }
        .onFailure {
            android.widget.Toast.makeText(context, "No installed app can share this file type.", android.widget.Toast.LENGTH_LONG).show()
        }
}
