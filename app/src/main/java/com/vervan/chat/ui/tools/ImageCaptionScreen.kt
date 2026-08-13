package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberManagedImagePath
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private data class CaptionMode(val label: String, val prompt: String)

private val CAPTION_MODES = listOf(
    CaptionMode("Alt text", "Write concise alt text for screen readers."),
    CaptionMode("Social caption", "Write a short, engaging social media caption for this image."),
    CaptionMode("Product description", "Write a short product description for what's shown in this image."),
    CaptionMode("Document description", "Describe this document or screen and its layout.")
)

/** Vision-model image captioning — requires the active model to declare vision support; shows a
 * clear message instead of silently failing when it doesn't (same guard [ChatViewModel] uses). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageCaptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var visionAvailable by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visionAvailable = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)?.supportsVision == true
    }

    val managedImagePath = rememberManagedImagePath()
    val imagePath = managedImagePath.path
    var output by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var activeMode by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var genJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastMode by remember { mutableStateOf<CaptionMode?>(null) }

    fun runMode(mode: CaptionMode) {
        val path = imagePath ?: return
        genJob?.cancel()
        isRunning = true
        activeMode = mode.label
        lastMode = mode
        output = ""
        errorText = null
        genJob = scope.launch {
            try {
                val flow = OneShotLlm.stream(
                    app, mode.prompt, imagePath = path,
                    runContext = com.vervan.chat.llm.ToolRunContext("tools/image-caption", "Image caption · ${mode.label}", "Image: ${java.io.File(path).name}"),
                )
                if (flow == null) {
                    errorText = "No vision model is ready. Open Settings → AI models and load one."
                } else {
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    flow.collect {
                        sb.append(it)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60) { output = sb.toString().trim(); lastEmit = now }
                    }
                    output = sb.toString().trim()
                    if (output.isBlank()) errorText = "The model returned an empty response. Try again."
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isRunning = false
            }
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "caption-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            val valid = runCatching {
                require(file.length() <= ImportLimits.MAX_IMAGE_SOURCE_BYTES)
                require(ImageUtils.normalizeForModel(file)) { "The captured image could not be decoded" }
            }.isSuccess
            if (valid) { managedImagePath.set(file.absolutePath); output = "" }
            else { errorText = "The captured image is too large or could not be read. Please use a smaller image."; file.delete() }
        } else file?.delete()
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { val (file, uri) = newImageFile(); pendingFile = file; takePicture.launch(uri) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (file, _) = newImageFile()
                val copied = runCatching {
                    (context.contentResolver.openInputStream(uri) ?: error("Couldn't open selected image")).use { input ->
                        file.outputStream().use { input.copyToLimited(it, ImportLimits.MAX_IMAGE_SOURCE_BYTES) }
                    }
                    require(ImageUtils.normalizeForModel(file)) { "The selected image could not be decoded" }
                }.isSuccess
                if (copied) { managedImagePath.set(file.absolutePath); output = ""; errorText = null }
                else { file.delete(); errorText = "The selected image is too large or could not be read." }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image caption & alt text") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.Filled.ImageSearch,
                title = "Describe an image for any audience",
                body = "Create alt text, captions, or image descriptions on-device."
            )
            if (visionAvailable == false) {
                Text(
                    "Load a vision-capable model to use this tool.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                )
            }
            ResponsiveActions {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Text("Camera", modifier = Modifier.padding(start = Space.sm))
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Text("From files", modifier = Modifier.padding(start = Space.sm))
                }
            }
            imagePath?.let { path ->
                val bitmap = rememberThumbnail(path, 700)
                bitmap?.let { Image(it, "Selected image", Modifier.fillMaxWidth().height(200.dp).padding(top = Space.md), contentScale = ContentScale.Fit) }
                FlowRow(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CAPTION_MODES.forEach { mode ->
                        VervanFilterChip(selected = activeMode == mode.label, onClick = { runMode(mode) }, label = { Text(mode.label) }, enabled = !isRunning)
                    }
                }
            }
            when {
                isRunning && output.isBlank() -> {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Creating ${activeMode?.lowercase() ?: "description"}",
                        body = "Analyzing the image with the active model; review the privacy status before sending sensitive images.",
                        actionLabel = "Stop",
                        onAction = { genJob?.cancel(); isRunning = false }
                    )
                }
                output.isNotBlank() -> {
                    ToolResultHeader(
                        title = activeMode?.takeIf { it.isNotBlank() } ?: "Description ready",
                        supportingText = if (isRunning) "Generating with the active model…" else "Ready to copy and use"
                    )
                    Card(Modifier.fillMaxWidth().padding(top = Space.lg), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(Space.md)) {
                            Text(output, style = MaterialTheme.typography.bodyMedium)
                            if (!isRunning) {
                                OutlinedButton(
                                    onClick = {
                                        context.getSystemService(android.content.ClipboardManager::class.java)
                                            .setSensitiveText(output, scope, "Caption")
                                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                                    },
                                    modifier = Modifier.padding(top = Space.sm)
                                ) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text("Copy", modifier = Modifier.padding(start = Space.sm)) }
                            }
                        }
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = "Couldn't generate a caption",
                        message = errorText!!,
                        recovery = "Load a vision model or choose a clearer image, then try again.",
                        actionLabel = lastMode?.let { "Try again" },
                        onAction = lastMode?.let { mode -> { runMode(mode) } },
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
            }
        }
        }
    }
}
