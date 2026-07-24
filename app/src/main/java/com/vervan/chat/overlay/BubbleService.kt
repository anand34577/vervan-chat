package com.vervan.chat.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vervan.chat.MainActivity
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ValidationLimits
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * draws the persistent draggable quick-action bubble via [WindowManager]. Runs as a
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
    private var menuView: ComposeView? = null
    private var menuOwner: OverlayLifecycleOwner? = null
    // The result overlay is a Compose panel (markdown rendering) hosted in a WindowManager view,
    // which needs its own view-tree owners since it lives outside any Activity (see
    // OverlayLifecycleOwner). State is hoisted here so the streaming generation loop can update
    // it from BubbleService without recreating the view.
    private var resultView: ComposeView? = null
    private var resultOwner: OverlayLifecycleOwner? = null
    private val resultTextState = mutableStateOf("")
    private val resultBusyState = mutableStateOf(false)
    private val resultChatIdState = mutableStateOf<String?>(null)
    private var resultImagePath: String? = null
    private var continueConversationAfterCapture = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val showBubble = intent?.getBooleanExtra(EXTRA_SHOW_BUBBLE, true) ?: true
        if (bubbleView != null) {
            setBubbleVisible(showBubble)
            return START_STICKY
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        if (started.isFailure) { stopSelf(); return START_NOT_STICKY }
        if (!addBubble()) return START_NOT_STICKY
        setBubbleVisible(showBubble)
        instance = this
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        serviceScope.cancel()
        removeBubble()
    }

    private fun addBubble(): Boolean {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density

        val bubble = AccessibleBubbleImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Open Vervan Quick actions"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF161B29.toInt())
                setStroke((2 * density).toInt(), 0xFFF6B24E.toInt())
            }
            elevation = 10 * density
            isClickable = true
            isFocusable = true
            val paddingPx = (9 * density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
        val sizePx = (56 * density).toInt()
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
        bubble.setOnClickListener { toggleMenu(params) }

        // Use the platform gesture threshold so a slightly shaky tap is not mistaken for a drag.
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    bubble.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.86f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        bubble.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                    }
                    params.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - sizePx).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (resources.displayMetrics.heightPixels - sizePx).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(bubble, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    bubble.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start()
                    if (!moved) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    bubble.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(bubble, params) }.onFailure { stopSelf(); return false }
        bubbleView = bubble
        return true
    }

    /** Tapping the bubble no longer jumps straight into a capture — a floating button with no
     * confirm step ("did I just start recording my screen?") is exactly the kind of surprise a
     * privacy-focused app shouldn't spring on someone. It expands a menu instead: the capture
     * actions, plus two distinct dismiss options — "Hide" removes the bubble for now but leaves
     * the feature enabled (it returns the next time you open Vervan), while "Turn off" disables
     * the whole feature (persists the Settings toggle to off). */
    private fun toggleMenu(bubbleParams: WindowManager.LayoutParams) {
        if (menuView != null) { hideMenu(); return }
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        fun act(action: () -> Unit) {
            hideMenu()
            action()
        }
        val menu = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                com.vervan.chat.ui.theme.VervanTheme(darkTheme = night) {
                    QuickBubbleMenu(
                        captureAppAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                        onResume = if (resultChatIdState.value != null && resultView == null) ({
                            act { showResult(resultTextState.value, resultBusyState.value) }
                        }) else null,
                        onExplainScreen = { act { launchCapture(selectArea = false) } },
                        onCaptureArea = { act { launchCapture(selectArea = true) } },
                        onCaptureApp = { act { launchCapture(selectArea = false, captureApp = true) } },
                        onHide = { act { hideForNow() } },
                        onTurnOff = { act { closeFeature() } },
                        onDismiss = { hideMenu() }
                    )
                }
            }
        }

        val menuWidth = (300 * density).toInt().coerceAtMost(resources.displayMetrics.widthPixels - (16 * density).toInt())
        val menuParams = WindowManager.LayoutParams(
            menuWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            bubbleParams.type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = bubbleParams.y + (64 * density).toInt()
        }
        runCatching { wm.addView(menu, menuParams) }.onFailure { owner.onDestroy(); return }
        menuView = menu
        menuOwner = owner
        bubbleView?.contentDescription = "Close Vervan Quick actions"
        menu.post {
            menuParams.x = bubbleParams.x.coerceIn(0, (resources.displayMetrics.widthPixels - menu.width).coerceAtLeast(0))
            val below = bubbleParams.y + (64 * density).toInt()
            menuParams.y = if (below + menu.height <= resources.displayMetrics.heightPixels) {
                below
            } else {
                (bubbleParams.y - menu.height).coerceAtLeast(0)
            }
            runCatching { wm.updateViewLayout(menu, menuParams) }
        }
    }

    private fun hideMenu() {
        menuView?.let { runCatching { windowManager?.removeView(it) } }
        menuView = null
        menuOwner?.onDestroy()
        menuOwner = null
        bubbleView?.contentDescription = "Open Vervan Quick actions"
    }

    private fun launchCapture(
        selectArea: Boolean,
        captureApp: Boolean = false,
        continueConversation: Boolean = false,
    ) {
        captureUiActive = true
        continueConversationAfterCapture = continueConversation && resultChatIdState.value != null
        hideResult(clearConversation = !continueConversationAfterCapture)
        bubbleView?.visibility = View.INVISIBLE
        runCatching {
            startActivity(
                Intent(this, ScreenCaptureActivity::class.java)
                    .putExtra(ScreenCaptureActivity.EXTRA_SELECT_AREA, selectArea)
                    .putExtra(ScreenCaptureActivity.EXTRA_CAPTURE_APP, captureApp)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            captureUiActive = false
            continueConversationAfterCapture = false
            bubbleView?.visibility = View.VISIBLE
            showResult("Screen capture could not be opened.")
        }
    }

    /** Owns generation after the permission/selection activity closes, so the answer can keep
     * streaming over the user's current app and the entire exchange is durable in Room. */
    private fun explainCapturedScreen(file: File) {
        val continueExistingConversation = continueConversationAfterCapture
        continueConversationAfterCapture = false
        serviceScope.launch {
            val app = applicationContext as VervanApp
            val db = app.container.db
            try {
                showResult("", busy = true)
                val existingChat = resultChatIdState.value
                    ?.takeIf { continueExistingConversation }
                    ?.let { db.chatDao().getChat(it) }
                val chat = existingChat ?: Chat(
                    title = "Screen explanation · " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date()),
                    workspaceId = app.container.settingsRepository.activeWorkspaceId.first(),
                ).also { db.chatDao().upsert(it) }
                val prompt = screenCapturePrompt(existingChat != null)
                resultImagePath = file.path
                resultChatIdState.value = chat.id
                val user = Message(
                    chatId = chat.id,
                    parentId = chat.activeLeafId,
                    role = MessageRole.USER,
                    content = prompt,
                    imagePath = file.path,
                )
                db.messageDao().upsert(user)
                generateScreenReply(app, chat, user, file.path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val message = "Couldn't explain the screen: ${t.toUserMessage()}"
                showResult(message)
            }
        }
    }

    private fun askFollowUp(question: String) {
        val chatId = resultChatIdState.value ?: return
        val imagePath = resultImagePath ?: return
        val prompt = question.trim().take(ValidationLimits.CHAT_COMPOSER).takeIf { it.isNotEmpty() } ?: return
        if (resultBusyState.value) return
        resultBusyState.value = true
        serviceScope.launch {
            try {
                val app = applicationContext as VervanApp
                val db = app.container.db
                val chat = db.chatDao().getChat(chatId) ?: run {
                    showResult("This screen conversation is no longer available.")
                    return@launch
                }
                val user = Message(chatId = chatId, parentId = chat.activeLeafId, role = MessageRole.USER, content = prompt)
                db.messageDao().upsert(user)
                generateScreenReply(app, chat, user, imagePath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                showResult("Couldn't send that question: ${t.toUserMessage()}")
            }
        }
    }

    /** Generates both the first explanation and later questions through one durable chat path. */
    private suspend fun generateScreenReply(app: VervanApp, chat: Chat, user: Message, imagePath: String) {
        val db = app.container.db
        val reply = Message(
            chatId = chat.id,
            parentId = user.id,
            role = MessageRole.ASSISTANT,
            content = "",
            state = MessageState.STREAMING
        )
        try {
            showResult("", busy = true)
            db.messageDao().upsert(reply)
            db.chatDao().update(chat.copy(activeLeafId = reply.id, updatedAt = System.currentTimeMillis()))
            val prompt = buildScreenConversationPrompt(db.messageDao().getMessages(chat.id).filter { it.id != reply.id })

            // Prefer the model already resident in memory; a screen follow-up should not reload
            // another model just because Model Manager's active flag differs.
            val loadedId = app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId
            val model = loadedId?.let { db.modelDao().get(it) }
                ?: db.modelDao().getActiveModel(ModelRole.GENERATION)
                ?: error("No chat model selected. Import or activate one in Models.")
            val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, com.vervan.chat.modelload.LoadTrigger.CHAT_SEND)
            check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
            check(app.container.visionEnabled(model)) { "The active model (${model.displayName}) doesn't support image understanding." }

            val answer = StringBuilder()
            var lastPersistAt = 0L
            val params = com.vervan.chat.llm.resolveGenerationParams(model, app.container.settingsRepository)
            app.container.generate(
                model, prompt, imagePath, null,
                params.temperature, params.topP, params.topK, params.seed,
                params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
            ).collect { chunk ->
                answer.append(chunk)
                showResult(answer.toString(), busy = true)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastPersistAt >= 100L) {
                    db.messageDao().update(reply.copy(content = answer.toString(), state = MessageState.STREAMING))
                    lastPersistAt = now
                }
            }
            val finalAnswer = answer.toString().ifBlank { "(No response)" }
            db.messageDao().update(reply.copy(content = finalAnswer, state = MessageState.COMPLETE))
            showResult(finalAnswer)
        } catch (cancelled: CancellationException) {
            db.messageDao().update(reply.copy(state = MessageState.CANCELLED))
            throw cancelled
        } catch (t: Throwable) {
            val message = "Couldn't answer that: ${t.toUserMessage()}"
            db.messageDao().update(reply.copy(content = message, state = MessageState.FAILED))
            showResult(message)
        }
    }

    private fun showResult(text: String, busy: Boolean = false) {
        resultTextState.value = text
        resultBusyState.value = busy
        val wm = windowManager ?: return
        if (resultView == null) {
            val owner = OverlayLifecycleOwner().apply { onCreate() }
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val panel = ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    com.vervan.chat.ui.theme.VervanTheme(darkTheme = night) {
                        val chatId = resultChatIdState.value
                        OverlayResultPanel(
                            text = resultTextState.value,
                            busy = resultBusyState.value,
                            onCopy = { copyResultToClipboard() },
                            onOpen = if (chatId != null) ({ openChatInApp(chatId) }) else null,
                            onAsk = if (chatId != null && resultImagePath != null) ({ askFollowUp(it) }) else null,
                            onShowNextScreen = if (chatId != null) ({
                                launchCapture(selectArea = false, continueConversation = true)
                            }) else null,
                            onClose = { hideResult(clearConversation = false) },
                        )
                    }
                }
            }
            val density = resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels - (24 * density).toInt()).coerceAtMost((420 * density).toInt()),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutParams?.type ?: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (48 * density).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            runCatching { wm.addView(panel, params) }.onFailure { owner.onDestroy(); return }
            resultView = panel
            resultOwner = owner
        }
    }

    private fun hideResult(clearConversation: Boolean = true) {
        resultView?.let { runCatching { windowManager?.removeView(it) } }
        resultView = null
        resultOwner?.onDestroy()
        resultOwner = null
        if (clearConversation) {
            resultChatIdState.value = null
            resultImagePath = null
        }
    }

    private fun copyResultToClipboard() {
        val text = resultTextState.value.takeIf { it.isNotBlank() } ?: return
        runCatching {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Screen explanation", text))
        }
    }

    /** Opens the saved explanation chat inside the full app — reuses the launcher-shortcut deep
     * link path (see VervanNavGraph's `open_chat:` handling) rather than a bespoke intent. */
    private fun openChatInApp(chatId: String) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("vervan_shortcut", "open_chat:$chatId")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        hideResult()
    }

    private fun setBubbleVisible(visible: Boolean) {
        bubbleView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (!visible) hideMenu()
    }

    private fun showCaptureFailure(message: String) {
        captureUiActive = false
        continueConversationAfterCapture = false
        setBubbleVisible(true)
        showResult(message)
    }

    /** "Hide until next open" dismisses the bubble for now WITHOUT disabling the feature: the
     * Settings toggle stays on, so VervanApp's lifecycle observer re-creates the service the next
     * time the app is opened (and shows the bubble again when it's next backgrounded). This is the
     * lightweight "get it off my screen" the user asked for, distinct from "Turn off". */
    private fun hideForNow() {
        stopSelf()
    }

    /** "Turn off" disables the feature outright (persists the Settings toggle to off), not just
     * this instance of the bubble — otherwise it would silently reappear the next time the app
     * is backgrounded (see VervanApp's lifecycle observer), which isn't what "off" means to
     * the user who just tapped it. */
    private fun closeFeature() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // A bare, unguarded launch here used to crash the app if the settings DataStore
            // write threw (disk pressure, a concurrent corrupt-file recovery) — tapping "Close"
            // on the bubble menu is exactly the kind of routine action that shouldn't be able
            // to bring down the whole process.
            runCatching {
                (applicationContext as com.vervan.chat.VervanApp).container.settingsRepository.setQuickActionBubbleEnabled(false)
            }.onFailure { android.util.Log.e("BubbleService", "closeFeature settings write failed", it) }
        }
        stopSelf()
    }

    private fun removeBubble() {
        hideMenu()
        hideResult()
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
        private const val TAG = "BubbleService"
        private const val CHANNEL_ID = "vervan_bubble"
        private const val NOTIFICATION_ID = 43
        private const val EXTRA_SHOW_BUBBLE = "show_bubble"

        @Volatile private var instance: BubbleService? = null
        @Volatile private var captureUiActive = false

        fun start(context: Context, showBubble: Boolean = true) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BubbleService::class.java).putExtra(EXTRA_SHOW_BUBBLE, showBubble)
                )
            }.onFailure { android.util.Log.w(TAG, "Could not start quick-action bubble", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BubbleService::class.java)) }
        }

        fun shouldRemainRunningInForeground(): Boolean = captureUiActive

        /** Returns false when the process/service was killed, so the foreground lifecycle can
         * start it again while Android still permits foreground-service launches. */
        fun setVisible(visible: Boolean): Boolean {
            val service = instance ?: return false
            service.setBubbleVisible(visible)
            return true
        }

        fun finishCaptureUi() {
            captureUiActive = false
            instance?.setBubbleVisible(true)
        }

        fun captureFailed(message: String) {
            instance?.showCaptureFailure(message)
        }

        fun explainCapturedScreen(file: File): Boolean {
            val service = instance ?: return false
            service.explainCapturedScreen(file)
            return true
        }

        /** Upgrades the already-running bubble service to hold the `mediaProjection` FGS type
         * — must succeed before [android.media.projection.MediaProjectionManager.getMediaProjection]
         * is called, or that call throws. Returns false if the bubble isn't running (feature was
         * turned off between the tap and this call) so the caller can fail cleanly instead of
         * crashing on a doomed `getMediaProjection()`. */
        fun beginCapture(): Boolean {
            val svc = instance ?: run {
                android.util.Log.w(TAG, "beginCapture() failed: BubbleService isn't running")
                return false
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            return runCatching {
                svc.startForeground(NOTIFICATION_ID, svc.buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            }.onFailure { android.util.Log.w(TAG, "beginCapture() startForeground(mediaProjection) threw", it) }.isSuccess
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

/** Makes the draggable overlay operable through accessibility services as well as touch. */
private class AccessibleBubbleImageView(context: Context) : AppCompatImageView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

internal fun screenCapturePrompt(continuing: Boolean): String = if (continuing) {
    "I've followed the previous step. This is the screen I see now. Tell me what to tap next."
} else {
    "Explain what's shown in this screenshot, plainly and concisely."
}

/** Rebuilds the small Screen Assist conversation because each native generation starts fresh. */
internal fun buildScreenConversationPrompt(messages: List<Message>): String = buildString {
    appendLine("You are Screen Assist. Answer the latest user question about the attached screenshot.")
    appendLine("Use both the screenshot and the conversation below. Be direct and concise.")
    appendLine()
    // Screen Assist sends its short full history; add token-budget trimming only if
    // real-world overlay conversations become long enough to approach model context limits.
    messages.forEach { message ->
        if (message.role == MessageRole.ASSISTANT && message.state in setOf(
                MessageState.CANCELLED,
                MessageState.INTERRUPTED,
                MessageState.FAILED
            )
        ) return@forEach
        val content = if (message.role == MessageRole.ASSISTANT) {
            com.vervan.chat.llm.ThinkingParser.parse(message.content).answer
        } else {
            message.content
        }.trim()
        if (content.isEmpty()) return@forEach
        val role = when (message.role) {
            MessageRole.USER -> "User"
            MessageRole.ASSISTANT -> "Assistant"
            MessageRole.SYSTEM -> "Context"
        }
        appendLine("$role: $content")
    }
    append("Assistant: ")
}
