package com.vervan.chat.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vervan.chat.R

/**
 * Foreground service backing active generation (B16) — the actual generation
 * coroutine still runs in ChatViewModel's viewModelScope; this service's only job is to hold
 * a foreground-priority process state (+ visible notification) for as long as generation is
 * in flight, so the OS doesn't kill the process just because the app was backgrounded mid-
 * response. doesn't itself resume generation after a real process death (that would
 * need the prompt/state persisted and replayed via WorkManager) — it prevents the common case
 * (user backgrounds the app) from ever reaching process death in the first place.
 */
class GenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Generating response…")
            .setContentText("Vervan Chat is running a model response")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Generation", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val CHANNEL_ID = "vervan_generation"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, GenerationService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, GenerationService::class.java))
            }
        }
    }
}
