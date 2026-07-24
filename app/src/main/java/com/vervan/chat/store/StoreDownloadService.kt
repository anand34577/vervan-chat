package com.vervan.chat.store

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.StoreInstallState
import com.vervan.chat.modeldownload.StorageManager
import com.vervan.chat.system.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a Model Store install alive across backgrounding — the store's
 * counterpart to [com.vervan.chat.modeldownload.ModelDownloadService], and it follows that
 * service's structure deliberately so the two behave identically from the user's side.
 *
 * It does not run the install itself: [ModelStoreRepository] already drives that on the
 * application-scoped coroutine. This only holds foreground priority and an ongoing notification
 * for as long as an install is actually active, then stops itself. Without it, a multi-gigabyte
 * download dies the moment Android trims the backgrounded process, and the user comes back to a
 * silently paused install.
 *
 * As with the older service, a foreground service is used on every API level rather than branching
 * to the user-initiated-data-transfer job API on 14+: the foreground service already satisfies the
 * real requirements (survives backgrounding, ongoing notification with actions, reconciles on
 * restart via [com.vervan.chat.store.install.StoreInstallRecovery]).
 */
class StoreDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // If startForeground itself fails (notification channel blocked, background-start
        // restriction), stopping is the only honest option — staying alive without foreground
        // priority would let Android kill the process mid-download anyway, just less predictably.
        runCatching { startForeground(NOTIFICATION_ID, buildNotification(null)) }
            .onFailure { stopSelf(); return }

        val repository = (application as VervanApp).container.modelStoreRepository
        watchJob = scope.launch {
            repository.activeInstall.collect { active ->
                if (active == null || active.state in TERMINAL_STATES) {
                    stopSelf()
                } else if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@StoreDownloadService,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    runCatching {
                        NotificationManagerCompat.from(this@StoreDownloadService)
                            .notify(NOTIFICATION_ID, buildNotification(active))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as VervanApp).container.modelStoreRepository
        when (intent?.action) {
            // Cancelling the install coroutine leaves the staged .part files on disk, so this is a
            // pause in effect: the next attempt resumes by range request rather than restarting.
            ACTION_PAUSE, ACTION_STOP -> repository.cancelActiveInstall()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(active: ModelStoreRepository.ActiveInstall?): Notification {
        NotificationHelper.ensureChannels(this)
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_JOBS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (active == null) {
            return builder.setContentTitle("Preparing model install…").build()
        }

        builder.setContentTitle("Installing ${active.displayName}")

        val progress = active.progress
        val statusLine = when (active.state) {
            StoreInstallState.QUEUED -> "Queued…"
            StoreInstallState.VERIFYING -> "Verifying…"
            StoreInstallState.VALIDATING -> "Validating…"
            StoreInstallState.INSTALLING -> "Installing…"
            StoreInstallState.PAUSED -> "Paused"
            else -> {
                val bytes = if (progress != null && progress.totalBytes > 0) {
                    "${StorageManager.formatBytes(progress.bytesDownloaded)} of " +
                        StorageManager.formatBytes(progress.totalBytes)
                } else {
                    StorageManager.formatBytes(progress?.bytesDownloaded ?: 0L)
                }
                // Multi-artifact models (a projector alongside its weights, or sherpa-onnx's
                // several files) otherwise look stalled between artifacts, since each one's byte
                // counter restarts.
                val filePart = if (progress != null && progress.artifactCount > 1) {
                    "File ${progress.artifactIndex + 1} of ${progress.artifactCount} — "
                } else {
                    ""
                }
                filePart + bytes
            }
        }
        builder.setContentText(statusLine)

        val percent = progress?.takeIf { it.totalBytes > 0 }
            ?.let { ((it.bytesDownloaded.toFloat() / it.totalBytes) * 100).toInt().coerceIn(0, 100) }
        if (percent != null) builder.setProgress(100, percent, false) else builder.setProgress(0, 0, true)

        if (active.state in PAUSABLE_STATES) {
            builder.addAction(0, "Pause", actionIntent(ACTION_PAUSE))
        }
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, StoreDownloadService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    companion object {
        private const val NOTIFICATION_ID = 4202
        const val ACTION_PAUSE = "com.vervan.chat.store.PAUSE"
        const val ACTION_STOP = "com.vervan.chat.store.STOP"

        private val TERMINAL_STATES = setOf(
            StoreInstallState.READY,
            StoreInstallState.FAILED_PERMANENT,
            StoreInstallState.FAILED_RETRYABLE,
            StoreInstallState.CANCELLED,
            StoreInstallState.CORRUPTED
        )
        private val PAUSABLE_STATES = setOf(
            StoreInstallState.DOWNLOADING,
            StoreInstallState.QUEUED
        )

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StoreDownloadService::class.java)
                )
            }
        }
    }
}
