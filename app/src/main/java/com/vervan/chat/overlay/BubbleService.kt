package com.vervan.chat.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vervan.chat.MainActivity
import com.vervan.chat.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Phase I — draws the persistent draggable quick-action bubble via [WindowManager]. Runs as a
 * "specialUse" foreground service while idle (just showing an overlay) and briefly upgrades to
 * the `mediaProjection` type — via a second [startForeground] call with an explicit type, which
 * Android allows on an already-running service — only around an actual capture. Android 14+
 * rejects starting a `mediaProjection`-typed FGS before the user has granted screen-capture
 * consent, so the type can't be held for the service's whole (mostly consent-less) lifetime;
 * see [beginCapture]/[endCapture], called from [ScreenCaptureActivity].
 *
 * Deliberately plain View/WindowManager, not Compose — hosting a `ComposeView` outside any
 * Activity/Fragment needs manually wiring `ViewTreeLifecycleOwner`/`ViewTreeViewModelStoreOwner`/
 * `ViewTreeSavedStateRegistryOwner`, real complexity for one small draggable circle. The actual
 * result UI (after a capture) runs in a normal Activity ([ScreenCaptureActivity]), which gets
 * Compose for free.
 */
class BubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var menuView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubbleView != null) return START_STICKY // already showing
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        if (started.isFailure) { stopSelf(); return START_NOT_STICKY }
        addBubble()
        instance = this
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        removeBubble()
    }

    private fun addBubble() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val bubble = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF6750A4.toInt())
            }
            val paddingPx = (8 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            sizePx, sizePx, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
        layoutParams = params

        // Minimal drag-to-move + tap-to-capture — a real touch slop constant isn't critical
        // here (56dp target, coarse gesture), just distinguishing "dragged" from "tapped".
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { wm.updateViewLayout(bubble, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu(params)
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(bubble, params) }.onFailure { stopSelf(); return }
        bubbleView = bubble
    }

    /** Tapping the bubble no longer jumps straight into a capture — a floating button with no
     * confirm step ("did I just start recording my screen?") is exactly the kind of surprise a
     * privacy-focused app shouldn't spring on someone. It now expands a two-item menu instead:
     * Explain (starts the capture) or Close (turns the whole feature off, not just this instance
     * — reappearing right after the user explicitly closed it would be its own kind of surprise). */
    private fun toggleMenu(bubbleParams: WindowManager.LayoutParams) {
        if (menuView != null) { hideMenu(); return }
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(0xFF2B2B2B.toInt())
            }
        }
        fun menuButton(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            setOnClickListener { hideMenu(); onClick() }
        }
        menu.addView(menuButton("Explain") { launchCapture() })
        menu.addView(menuButton("Close") { closeFeature() })

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            bubbleParams.type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = bubbleParams.y + (64 * density).toInt()
        }
        runCatching { wm.addView(menu, menuParams) }.onFailure { return }
        menuView = menu
    }

    private fun hideMenu() {
        menuView?.let { runCatching { windowManager?.removeView(it) } }
        menuView = null
    }

    private fun launchCapture() {
        startActivity(
            Intent(this, ScreenCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** "Close" turns the feature off outright (persists the Settings toggle to off), not just
     * this instance of the bubble — otherwise it would silently reappear the next time the app
     * is backgrounded (see VervanApp's lifecycle observer), which isn't what "closed" means to
     * the user who just tapped it. */
    private fun closeFeature() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            (applicationContext as com.vervan.chat.VervanApp).container.settingsRepository.setQuickActionBubbleEnabled(false)
        }
        stopSelf()
    }

    private fun removeBubble() {
        hideMenu()
        bubbleView?.let { runCatching { windowManager?.removeView(it) } }
        bubbleView = null
        windowManager = null
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Quick-action bubble", NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Quick-action bubble is on")
            .setContentText("Tap to explain what's on screen. Turn off in Settings → Security.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vervan_bubble"
        private const val NOTIFICATION_ID = 43

        @Volatile private var instance: BubbleService? = null

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BubbleService::class.java)) }
        }

        /** Upgrades the already-running bubble service to hold the `mediaProjection` FGS type
         * — must succeed before [android.media.projection.MediaProjectionManager.getMediaProjection]
         * is called, or that call throws. Returns false if the bubble isn't running (feature was
         * turned off between the tap and this call) so the caller can fail cleanly instead of
         * crashing on a doomed `getMediaProjection()`. */
        fun beginCapture(): Boolean {
            val svc = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            return runCatching {
                svc.startForeground(NOTIFICATION_ID, svc.buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            }.isSuccess
        }

        /** Reverts to the plain "specialUse" type once the capture is done — matches the intent
         * of holding `mediaProjection` only around the capture it's actually needed for. */
        fun endCapture() {
            val svc = instance ?: return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            runCatching { svc.startForeground(NOTIFICATION_ID, svc.buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        }
    }
}
