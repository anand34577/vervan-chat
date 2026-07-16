package com.vervan.chat.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.theme.VervanTheme
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class CaptureState {
    data object Capturing : CaptureState()
    data class Selecting(val bitmap: Bitmap) : CaptureState()
    data object Generating : CaptureState()
    data class Done(val explanation: String) : CaptureState()
    data class Failed(val message: String) : CaptureState()
}

/** Gets one user-consented MediaProjection frame. Full-screen requests immediately return to
 * the host app; area requests briefly show the crop UI, then hand generation to BubbleService. */
class ScreenCaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SELECT_AREA = "select_area"
        const val EXTRA_CAPTURE_APP = "capture_app"
        private const val TAG = "ScreenCaptureActivity"
    }

    private val state = MutableStateFlow<CaptureState>(CaptureState.Capturing)

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            state.value = CaptureState.Failed("Screen-capture permission was denied.")
        } else {
            capture(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VervanTheme(darkTheme = isSystemInDarkTheme()) {
                val current by state.collectAsState()
                ScreenExplainScreen(
                    state = current,
                    onDismiss = { finish() },
                    onConfirmSelection = { crop -> saveAndHandOff(crop) }
                )
            }
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching {
            val captureIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val config = if (intent.getBooleanExtra(EXTRA_CAPTURE_APP, false)) {
                    android.media.projection.MediaProjectionConfig.createConfigForUserChoice()
                } else {
                    android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                }
                manager.createScreenCaptureIntent(config)
            } else {
                manager.createScreenCaptureIntent()
            }
            projectionLauncher.launch(captureIntent)
        }
            .onFailure {
                android.util.Log.w(TAG, "Could not launch screen capture consent", it)
                state.value = CaptureState.Failed("Screen capture isn't available on this device.")
            }
    }

    override fun onDestroy() {
        BubbleService.finishCaptureUi()
        super.onDestroy()
    }

    private fun capture(resultCode: Int, data: Intent) {
        // The system consent sheet resumes this transparent activity before delivering its
        // result. Put Vervan's task back behind the app the user was viewing, then allow that
        // window one frame to redraw; otherwise MediaProjection faithfully captures Vervan.
        moveTaskToBack(true)
        lifecycleScope.launch {
            delay(350)
            val bitmap = withContext(Dispatchers.IO) { captureOneFrame(resultCode, data) }
            if (bitmap == null) {
                state.value = CaptureState.Failed("Couldn't capture the screen.")
            } else if (intent.getBooleanExtra(EXTRA_SELECT_AREA, false)) {
                state.value = CaptureState.Selecting(bitmap)
            } else {
                saveAndHandOff(null, bitmap)
            }
        }
    }

    private fun saveAndHandOff(fraction: android.graphics.RectF?, source: Bitmap? = null) {
        val bitmap = source ?: (state.value as? CaptureState.Selecting)?.bitmap ?: return
        state.value = CaptureState.Generating
        lifecycleScope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    val cropped = if (fraction == null) bitmap else {
                        val left = (fraction.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                        val top = (fraction.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                        val width = (fraction.width() * bitmap.width).toInt().coerceIn(1, bitmap.width - left)
                        val height = (fraction.height() * bitmap.height).toInt().coerceIn(1, bitmap.height - top)
                        Bitmap.createBitmap(bitmap, left, top, width, height)
                    }
                    val dir = File(filesDir, "images").apply { mkdirs() }
                    File(dir, "screen_capture_${System.currentTimeMillis()}.png").also { output ->
                        output.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        if (cropped !== bitmap) cropped.recycle()
                        bitmap.recycle()
                        ImageUtils.normalizeForModel(output)
                    }
                }
            } catch (t: Throwable) {
                state.value = CaptureState.Failed("Couldn't save the screenshot: ${t.toUserMessage()}")
                return@launch
            }
            if (BubbleService.explainCapturedScreen(file)) finish()
            else state.value = CaptureState.Failed("The floating assistant stopped before generation could begin.")
        }
    }

    private suspend fun captureOneFrame(resultCode: Int, data: Intent): Bitmap? {
        if (!BubbleService.beginCapture()) return null
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val handler = Handler(Looper.getMainLooper())
        val projection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Could not create MediaProjection", t)
            BubbleService.endCapture()
            return null
        }
        val callback = object : MediaProjection.Callback() {}
        projection.registerCallback(callback, handler)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = projection.createVirtualDisplay(
            "VervanQuickCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
        return try {
            withTimeoutOrNull(3000) {
                suspendCancellableCoroutine { continuation ->
                    reader.setOnImageAvailableListener({ source ->
                        if (!continuation.isActive) return@setOnImageAvailableListener
                        val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val plane = image.planes[0]
                            val rowPadding = plane.rowStride - plane.pixelStride * width
                            val raw = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                            raw.copyPixelsFromBuffer(plane.buffer)
                            val frame = if (rowPadding == 0) raw else Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
                            if (continuation.isActive) continuation.resume(frame)
                        } catch (_: Throwable) {
                            if (continuation.isActive) continuation.resume(null)
                        } finally {
                            image.close()
                        }
                    }, handler)
                }
            }
        } finally {
            display?.release()
            reader.close()
            projection.unregisterCallback(callback)
            projection.stop()
            BubbleService.endCapture()
        }
    }
}
