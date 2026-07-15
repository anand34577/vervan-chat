package com.vervan.chat.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.ui.theme.VervanTheme
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class CaptureState {
    data object Capturing : CaptureState()
    /** Full-frame captured — Android's own screen-share consent dialog (unavoidable on every
     * capture; there's no API to skip or "remember" it) already happened once for this whole
     * frame. Picking a region here is a client-side crop of that one frame, a snipping-tool-like
     * step that needs no second MediaProjection round-trip. */
    data class Selecting(val bitmap: Bitmap) : CaptureState()
    data object Generating : CaptureState()
    data class Done(val explanation: String) : CaptureState()
    data class Failed(val message: String) : CaptureState()
}

/**
 * Phase I — launched by [BubbleService] on tap. Runs the `MediaProjection` consent flow, grabs
 * exactly one frame (not a continuous recording — minimizes how long the capture surface is
 * live), then feeds it to the loaded model's vision path exactly like a photo attachment does
 * ([com.vervan.chat.llm.LlmEngine.generate]'s `imagePath` parameter). Relies on
 * [BubbleService] already running as the active `mediaProjection`-typed foreground service —
 * see that class's doc comment for why capture would otherwise fail on Android 14+.
 */
class ScreenCaptureActivity : ComponentActivity() {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Capturing)
    private val state: StateFlow<CaptureState> = _state
    private var capturedFile: File? = null

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            _state.value = CaptureState.Failed("Screen-capture permission was denied.")
            return@registerForActivityResult
        }
        captureAndExplain(result.resultCode, result.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VervanTheme(darkTheme = isSystemInDarkTheme()) {
                val s by state.collectAsState()
                ScreenExplainScreen(
                    state = s,
                    onDismiss = { finish() },
                    onConfirmSelection = { fraction -> explain(fraction) }
                )
            }
        }
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        capturedFile?.delete() // single-use screenshot; nothing lingers on disk after this screen closes
    }

    private fun captureAndExplain(resultCode: Int, data: Intent) {
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = withContext(Dispatchers.IO) { captureOneFrame(resultCode, data) }
            if (bitmap == null) {
                _state.value = CaptureState.Failed("Couldn't capture the screen.")
                return@launch
            }
            _state.value = CaptureState.Selecting(bitmap)
        }
    }

    /** [fraction] is the user's chosen crop, in 0..1 of the captured bitmap — null means "the
     * whole screen", from the "explain whole screen" quick action instead of a hand-drawn box. */
    private fun explain(fraction: android.graphics.RectF?) {
        val app = applicationContext as VervanApp
        val bitmap = (state.value as? CaptureState.Selecting)?.bitmap ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val file = withContext(Dispatchers.IO) {
                val cropped = if (fraction == null) bitmap else {
                    val left = (fraction.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val top = (fraction.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val w = (fraction.width() * bitmap.width).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
                    val h = (fraction.height() * bitmap.height).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
                    Bitmap.createBitmap(bitmap, left, top, w, h)
                }
                File(cacheDir, "screen_capture_${System.currentTimeMillis()}.png").also { f ->
                    f.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (cropped !== bitmap) cropped.recycle()
                    bitmap.recycle()
                    ImageUtils.normalizeForModel(f)
                }
            }
            capturedFile = file

            val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _state.value = CaptureState.Failed("No chat model selected. Import or activate one in Models.")
                return@launch
            }
            _state.value = CaptureState.Generating
            try {
                app.container.withLlm { engine ->
                    // engine.load() is a blocking native call (can take 10s+) — must not run on
                    // Main or it ANRs this activity exactly like the chat screen's auto-load did.
                    if (engine.loadedModelPath != model.filePath) withContext(Dispatchers.IO) { engine.load(model.filePath) }
                    if (!engine.visionEnabled) {
                        _state.value = CaptureState.Failed("The active model (${model.displayName}) doesn't support image understanding.")
                        return@withLlm
                    }
                    val explanation = StringBuilder()
                    engine.generate("Explain what's shown in this screenshot, plainly and concisely.", imagePath = file.path)
                        .collect { chunk -> explanation.append(chunk) }
                    _state.value = CaptureState.Done(explanation.toString())
                }
            } catch (e: Exception) {
                _state.value = CaptureState.Failed("Generation failed: ${e.message}")
            }
        }
    }

    /** One frame only: creates the virtual display, waits for the first available image (with
     * a timeout so a stalled callback can't hang the activity forever), then tears everything
     * down immediately — [MediaProjection.stop] included — rather than leaving the capture
     * surface live any longer than it takes to grab a single screenshot. */
    private suspend fun captureOneFrame(resultCode: Int, data: Intent): Bitmap? {
        // Android 14+ throws from getMediaProjection() unless a foreground service already
        // holds the mediaProjection FGS type — BubbleService can't hold that type all the time
        // (it's rejected before consent is granted, which is most of its lifetime), so it's
        // upgraded into that type right here, for just the duration of this one capture.
        if (!BubbleService.beginCapture()) return null
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // A null Handler means "the calling thread's Looper" — this runs on a Dispatchers.IO
        // thread with no Looper at all, so both callbacks below need an explicit main-thread
        // Handler or delivery silently never happens.
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (e: SecurityException) {
            BubbleService.endCapture()
            return null
        }
        val callback = object : MediaProjection.Callback() {}
        projection.registerCallback(callback, mainHandler)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "VervanQuickCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        val bitmap = try {
            withTimeoutOrNull(3000) {
                suspendCancellableCoroutine { cont ->
                    imageReader.setOnImageAvailableListener({ reader ->
                        // The reader keeps delivering frames (maxImages=2, AUTO_MIRROR) right up
                        // until teardown — once the first frame already resumed the coroutine,
                        // ignore anything after it instead of touching a possibly-closing image.
                        if (!cont.isActive) return@setOnImageAvailableListener
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * width
                            val raw = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                            raw.copyPixelsFromBuffer(buffer)
                            val cropped = if (rowPadding == 0) raw else Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
                            if (cont.isActive) cont.resume(cropped)
                        } catch (e: Exception) {
                            // A frame that races with the projection stopping can arrive with an
                            // already-invalid buffer — treat it as "no frame this time" rather
                            // than crashing; withTimeoutOrNull's caller already handles null.
                            if (cont.isActive) cont.resume(null)
                        } finally {
                            image.close()
                        }
                    }, mainHandler)
                }
            }
        } finally {
            virtualDisplay?.release()
            imageReader.close()
            projection.unregisterCallback(callback)
            projection.stop()
            BubbleService.endCapture()
        }
        return bitmap
    }
}
