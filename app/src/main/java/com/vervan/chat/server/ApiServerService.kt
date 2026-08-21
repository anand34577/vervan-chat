package com.vervan.chat.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vervan.chat.MainActivity
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the local API server and keeps it visible through a foreground notification. */
class ApiServerService : Service() {
    @Volatile
    private var server: LocalApiServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private val lifecycleMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires foreground promotion promptly after startForegroundService(). Never
        // block the service main thread on DataStore, Keystore, or socket setup first.
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.onFailure {
            Log.e(TAG, "startForeground failed, stopping ApiServerService", it)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val app = applicationContext as VervanApp
        val settings = app.container.settingsRepository
        val restart = intent?.action == ACTION_RESTART
        startJob = scope.launch {
            lifecycleMutex.withLock {
                val started = runCatching {
                    // Older installs used an unauthenticated default. Apply the one-time
                    // migration before reading the server flags so a restart can never reopen a
                    // LAN-facing socket with that legacy default.
                    settings.applyApiServerSecurityDefaults()
                    // A sticky service can be recreated after the user disabled it. DataStore is
                    // the source of truth; never reopen a listening socket when its toggle is off.
                    if (!settings.apiServerEnabled.first()) return@runCatching false
                    // The API is an external data surface. A service recreated while the app is
                    // locked must stop instead of exposing chats, documents, or inference.
                    if (settings.appLockEnabled.first() && app.container.appLockManager.isLocked.value) {
                        return@runCatching false
                    }
                    if (restart) {
                        server?.stop()
                        server = null
                    }
                    if (server != null) return@runCatching true

                    val port = settings.apiServerPort.first()
                    val allowLan = settings.apiServerAllowLan.first()
                    // Bind localhost unless the user explicitly opts into LAN access. NanoHTTPD's
                    // null host means 0.0.0.0, so it is never used as the default.
                    val host: String? = if (allowLan) null else "127.0.0.1"
                    val fullMode = settings.apiServerFullMode.first()
                    // A localhost-only server may be used without a key, but LAN access is an
                    // externally reachable data surface. Enforce authentication here as the
                    // final gate even if a legacy preference or concurrent settings update
                    // briefly presents an unsafe combination to the service.
                    val requireAuth = settings.apiServerRequireAuth.first() || allowLan
                    val auth = app.container.apiServerAuth
                    if (requireAuth) auth.tokenOrGenerate()

                    LocalApiServer(host, port, app, auth, requireAuth, fullMode, scope).also { instance ->
                        instance.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        server = instance
                    }
                    true
                }.getOrElse { failure ->
                    if (failure is CancellationException) throw failure
                    Log.e(TAG, "API server failed to start", failure)
                    false
                }

                if (!started || server == null) stopSelfResult(startId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        startJob?.cancel()
        startJob = null
        server?.stop()
        server = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_api_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.notification_api_title))
            .setContentText(getString(R.string.notification_api_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "ApiServerService"
        private const val CHANNEL_ID = "vervan_api_server"
        private const val NOTIFICATION_ID = 44
        private const val ACTION_RESTART = "com.vervan.chat.server.RESTART"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ApiServerService::class.java))
            }.onFailure { Log.e(TAG, "Failed to start ApiServerService", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ApiServerService::class.java)) }
                .onFailure { Log.e(TAG, "Failed to stop ApiServerService", it) }
        }

        fun restart(context: Context) {
            val intent = Intent(context, ApiServerService::class.java).setAction(ACTION_RESTART)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.e(TAG, "Failed to restart ApiServerService", it) }
        }
    }
}
