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
                    // A sticky service can be recreated after the user disabled it. DataStore is
                    // the source of truth; never reopen a listening socket when its toggle is off.
                    if (!settings.apiServerEnabled.first()) return@runCatching false
                    if (restart) {
                        server?.stop()
                        server = null
                    }
                    if (server != null) return@runCatching true

                    val port = settings.apiServerPort.first()
                    // Always binds every interface (0.0.0.0) — null is NanoHTTPD's bind-all
                    // convention — user ask, not gated by the old "Allow other devices on this
                    // Wi-Fi" toggle anymore (that flag now only affects the exposure copy shown in
                    // Settings/Privacy Dashboard, not the actual bind address). requireAuth is
                    // therefore the ONLY thing standing between "on" and "reachable by anyone who
                    // can route to this device with no key" — it follows the user's own "Require
                    // an API key" choice directly, with no automatic force-on.
                    val host: String? = null
                    val fullMode = settings.apiServerFullMode.first()
                    // The API key is optional and applies only when the user turns it on — including
                    // in full web app mode. That mode does expose /api/* (chats, messages,
                    // attachments, knowledge bases) rather than only inference, so running it
                    // without a key on an untrusted network is a real exposure; the Settings screen
                    // and Privacy Dashboard both call that out in red rather than the server
                    // overriding the choice.
                    val requireAuth = settings.apiServerRequireAuth.first()
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
            NotificationChannel(CHANNEL_ID, "Local API server", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Local API server is running")
            .setContentText("Turn off in Settings > Privacy & security when unused.")
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
