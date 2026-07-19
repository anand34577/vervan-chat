package com.vervan.chat.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vervan.chat.MainActivity
import com.vervan.chat.VervanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Phase J — foreground service owning the [LocalApiServer] instance, matching
 * [com.vervan.chat.system.GenerationService]'s "visible while running, never silent" shape:
 * a persistent notification the entire time the server is up, started only by an explicit
 * Settings toggle, never on boot.
 */
class ApiServerService : Service() {
    private var server: LocalApiServer? = null
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_STICKY
        val app = applicationContext as VervanApp
        val settings = app.container.settingsRepository
        // Only instance.start() was guarded before — the settings reads and
        // auth.tokenOrGenerate() (which touches the Keystore-backed token store) above it could
        // throw uncaught on the main thread (this whole block runs inside runBlocking), crashing
        // the app the moment the user toggles the local API server on.
        val started = runCatching {
            runBlocking {
                val port = settings.apiServerPort.first()
                val lan = settings.lanApiServerEnabled.first()
                // A LAN-exposed server is never allowed to run unauthenticated, regardless of the
                // auth toggle — otherwise two independent switches could quietly serve an open
                // LLM API to the whole network, which breaks this app's core privacy promise.
                // Localhost-only binding still honors the user's auth choice.
                val requireAuth = settings.apiServerRequireAuth.first() || lan
                val host = if (lan) null else "127.0.0.1" // null hostname = bind all interfaces (NanoHTTPD convention)
                val auth = app.container.apiServerAuth
                if (requireAuth) auth.tokenOrGenerate() // ensure a token exists before anything can connect
                val instance = LocalApiServer(host, port, app, auth, requireAuth, scope)
                runCatching { instance.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
                    .onFailure { return@runBlocking false }
                server = instance
                true
            }
        }.getOrDefault(false)
        if (!started || server == null) { stopSelf(); return START_NOT_STICKY }
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.onFailure { stopSelf() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
        scope.cancel()
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Local API server", NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Local API server is running")
            .setContentText("Turn off in Settings → Security if you're not using it.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vervan_api_server"
        private const val NOTIFICATION_ID = 44

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ApiServerService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ApiServerService::class.java)) }
        }
    }
}
