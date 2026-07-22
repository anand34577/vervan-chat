package com.vervan.chat.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vervan.chat.R

/**
 * Posts user-relevant notifications for background work completion/failure.
 * Deliberately *not* used for engagement prompts — only operational status the user would
 * want to know while the app is backgrounded (import done, indexing complete, job failed,
 * thermal/storage warnings). If POST_NOTIFICATIONS is unavailable on Android 13+, posting
 * safely no-ops; the in-app job queue remains the source of truth.
 */
object NotificationHelper {
    const val CHANNEL_JOBS = "vervan_jobs"
    const val CHANNEL_IMPORT = "vervan_import"
    private var channelsCreated = false

    fun ensureChannels(context: Context) {
        if (channelsCreated) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_JOBS, context.getString(R.string.channel_jobs), NotificationManager.IMPORTANCE_LOW)
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_IMPORT, context.getString(R.string.channel_import), NotificationManager.IMPORTANCE_DEFAULT)
        )
        channelsCreated = true
    }

    fun post(context: Context, id: Int, title: String, text: String, channel: String = CHANNEL_JOBS) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
        // runCatching: POST_NOTIFICATIONS may be denied on API 33+; we don't crash, the
        // in-app job queue is the source of truth regardless.
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
