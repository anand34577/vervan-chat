package com.vervan.chat.modeldownload

import android.Manifest
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
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.system.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [ModelDownloadRepository]'s work alive across backgrounding —
 * same role as [com.vervan.chat.system.GenerationService] for active generation, applied to
 * model downloads. It does not run the download loop itself (that's already a plain coroutine
 * in the repository, scoped to [com.vervan.chat.VervanApp]'s application-level scope); its only
 * job is to hold foreground priority + an ongoing notification for as long as something is
 * actually active, and to run [ModelDownloadRepository.recoverOnStartup] once per process start.
 *
 * ponytail: this is the one mechanism for background execution on every Android version here,
 * rather than branching to a separate user-initiated-data-transfer job API on 14+ — a foreground
 * service already satisfies the real requirements (survives backgrounding, ongoing notification
 * with actions, reconciles on restart); the newer API mainly trades a little battery-attribution
 * nicety for meaningfully more code, not more correctness.
 */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { startForeground(NOTIFICATION_ID, buildNotification(null)) }.onFailure { stopSelf() }
        val repository = (application as VervanApp).container.modelDownloadRepository
        watchJob = scope.launch {
            repository.recoverOnStartup()
            repository.uiStates.collect { states ->
                val active = states.firstOrNull { it.status in NOTIFY_STATUSES }
                if (active == null) {
                    stopSelf()
                } else {
                    val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this@ModelDownloadService,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    if (canPostNotifications) runCatching {
                        NotificationManagerCompat.from(this@ModelDownloadService).notify(NOTIFICATION_ID, buildNotification(active))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as VervanApp).container.modelDownloadRepository
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val version = intent?.getStringExtra(EXTRA_VERSION)
        if (modelId != null && version != null) {
            when (intent.action) {
                ACTION_PAUSE -> scope.launch { repository.pauseDownload(modelId, version) }
                ACTION_STOP -> scope.launch { repository.cancelDownload(modelId, version, keepPartial = false) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(active: ModelUiState?): android.app.Notification {
        NotificationHelper.ensureChannels(this)
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_JOBS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (active == null) {
            return builder.setContentTitle("Preparing model download…").build()
        }

        val progress = if (active.totalBytes != null && active.totalBytes > 0) {
            ((active.downloadedBytes.toFloat() / active.totalBytes) * 100).toInt().coerceIn(0, 100)
        } else null
        val statusLine = when (active.status) {
            ModelStatus.WAITING_FOR_NETWORK -> "Waiting for network…"
            ModelStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi…"
            ModelStatus.VERIFYING -> "Verifying…"
            ModelStatus.IMPORTING -> "Importing…"
            else -> {
                val fileNote = if (active.totalFileCount > 1) "File ${active.completedFileCount + 1} of ${active.totalFileCount} — " else ""
                val bytesNote = if (active.totalBytes != null) "${StorageManager.formatBytes(active.downloadedBytes)} of ${StorageManager.formatBytes(active.totalBytes)}" else StorageManager.formatBytes(active.downloadedBytes)
                val speedNote = active.speedBytesPerSecond?.takeIf { it > 0 }
                    ?.let { " · ${StorageManager.formatBytes(it)}/s" }.orEmpty()
                fileNote + bytesNote + speedNote
            }
        }
        builder.setContentTitle("Downloading ${active.displayName}").setContentText(statusLine)
        if (progress != null) builder.setProgress(100, progress, false) else builder.setProgress(0, 0, true)

        if (active.status in PAUSABLE_NOTIFY_STATUSES) {
            builder.addAction(0, "Pause", actionIntent(ACTION_PAUSE, active))
            builder.addAction(0, "Stop", actionIntent(ACTION_STOP, active))
        }
        return builder.build()
    }

    private fun actionIntent(action: String, model: ModelUiState): PendingIntent {
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_MODEL_ID, model.modelId)
            putExtra(EXTRA_VERSION, model.version)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_MODEL_ID = "modelId"
        private const val EXTRA_VERSION = "version"
        const val ACTION_PAUSE = "com.vervan.chat.modeldownload.PAUSE"
        const val ACTION_STOP = "com.vervan.chat.modeldownload.STOP"

        private val NOTIFY_STATUSES = setOf(
            ModelStatus.QUEUED, ModelStatus.PREPARING, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI,
            ModelStatus.DOWNLOADING, ModelStatus.PAUSING, ModelStatus.DOWNLOADED, ModelStatus.VERIFYING,
            ModelStatus.IMPORTING, ModelStatus.CANCELLING
        )
        private val PAUSABLE_NOTIFY_STATUSES = setOf(
            ModelStatus.DOWNLOADING, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI
        )

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java)) }
        }
    }
}
