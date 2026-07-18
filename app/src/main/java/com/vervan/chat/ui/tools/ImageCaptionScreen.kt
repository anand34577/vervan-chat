package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private data class CaptionMode(val label: String, val prompt: String)

private val CAPTION_MODES = listOf(
    CaptionMode("Alt text", "Write concise, accessible alt text describing this image for a screen reader user."),
    CaptionMode("Social caption", "Write a short, engaging social media caption for this image."),
    CaptionMode("Product description", "Write a short product description for what's shown in this image."),
    CaptionMode("Document description", "Describe the content and layout of this document/screen image in a few sentences.")
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

    var imagePath by remember { mutableStateOf<String?>(null) }
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
                val flow = OneShotLlm.stream(app, mode.prompt, imagePath = path)
                if (flow == null) {
                    errorText = "No generation model is active. Load a vision-capable model from Models."
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
        if (success && file != null) { ImageUtils.fixOrientation(file); imagePath = file.absolutePath; output = "" }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { val (file, uri) = newImageFile(); pendingFile = file; takePicture.launch(uri) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (file, _) = newImageFile()
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ImageUtils.fixOrientation(file)
                }
                imagePath = file.absolutePath
                output = ""
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
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.Filled.ImageSearch,
                title = "Describe an image for any audience",
                body = "Create alt text, captions, or detailed image descriptions locally."
            )
            if (visionAvailable == false) {
                Text(
                "Load a vision-capable model to use this tool.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f), enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Text(" Camera")
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.weight(1f), enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Text(" From files")
                }
            }
            imagePath?.let { path ->
                val bitmap = remember(path) { ImageUtils.decodeThumbnail(path, 700)?.asImageBitmap() }
                bitmap?.let { Image(it, "Selected image", Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp), contentScale = ContentScale.Fit) }
                FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CAPTION_MODES.forEach { mode ->
                        FilterChip(selected = activeMode == mode.label, onClick = { runMode(mode) }, label = { Text(mode.label) }, enabled = !isRunning)
                    }
                }
            }
            when {
                isRunning && output.isBlank() -> {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Creating ${activeMode?.lowercase() ?: "description"}",
                        body = "Analyzing the image on this device.",
                        actionLabel = "Stop",
                        onAction = { genJob?.cancel(); isRunning = false }
                    )
                }
                output.isNotBlank() -> {
                    ToolResultHeader(
                        title = activeMode?.takeIf { it.isNotBlank() } ?: "Description ready",
                        supportingText = if (isRunning) "Generating on-device…" else "Ready to copy and use"
                    )
                    Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(output, style = MaterialTheme.typography.bodyMedium)
                            if (!isRunning) {
                                OutlinedButton(
                                    onClick = {
                                        context.getSystemService(android.content.ClipboardManager::class.java)
                                            .setPrimaryClip(android.content.ClipData.newPlainText("Caption", output))
                                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text(" Copy") }
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
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
        }
    }
}
