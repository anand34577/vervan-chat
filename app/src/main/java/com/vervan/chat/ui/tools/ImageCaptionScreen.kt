package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vervan.chat.ui.common.copyText
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
fun ImageCaptionScreen(onBack: () -> Unit, onOpenModels: (() -> Unit)? = null) {
    val context = LocalContext.current
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var visionAvailable by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visionAvailable = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)?.supportsVision == true
    }

    val managedImagePath = rememberManagedImagePath()
    val imagePath = managedImagePath.path
    var output by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var activeMode by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var genJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastMode by remember { mutableStateOf<CaptionMode?>(null) }
    var selectedMode by rememberSaveable { mutableStateOf(CAPTION_MODES.first().label) }

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
    val requestCameraPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) { val (file, uri) = newImageFile(); pendingFile = file; takePicture.launch(uri) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
                title = { Text(stringResource(R.string.image_caption_image_caption_alt_text)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            ToolIntro(
                icon = Icons.Filled.ImageSearch,
                title = stringResource(R.string.image_caption_describe_an_image_for_any),
                body = stringResource(R.string.image_caption_create_alt_text_captions_or)
            )
            if (visionAvailable == false) {
                com.vervan.chat.ui.common.SystemStatusStrip(
                    title = stringResource(R.string.tool_needs_vision_title),
                    body = stringResource(R.string.image_caption_load_a_vision_capable_model),
                    tone = com.vervan.chat.ui.common.StatusTone.Warning,
                    actionLabel = onOpenModels?.let { stringResource(R.string.tool_choose_model) },
                    onAction = onOpenModels,
                    modifier = Modifier.padding(top = Space.md)
                )
            }

            ToolStep(1, stringResource(R.string.tool_step_choose_image))
            ToolImageSource(
                imagePath = imagePath,
                onCamera = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) },
                onGallery = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isRunning
            )

            ToolStep(2, stringResource(R.string.caption_step_what))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                CAPTION_MODES.forEach { mode ->
                    VervanFilterChip(selected = selectedMode == mode.label, onClick = { selectedMode = mode.label }, label = { Text(mode.label) }, enabled = !isRunning)
                }
            }
            val mode = CAPTION_MODES.firstOrNull { it.label == selectedMode } ?: CAPTION_MODES.first()
            ToolPrimaryButton(
                label = stringResource(R.string.caption_write, mode.label.lowercase()),
                icon = Icons.Filled.ImageSearch,
                onClick = { runMode(mode) },
                blockedReason = when {
                    visionAvailable == false -> stringResource(R.string.tool_needs_vision_title)
                    imagePath == null -> stringResource(R.string.tool_blocked_add_image)
                    else -> null
                },
                running = isRunning,
                onStop = { genJob?.cancel(); isRunning = false }
            )

            if (isRunning || output.isNotBlank() || errorText != null) {
                ToolStep(3, stringResource(R.string.tool_step_review_result))
            }
            when {
                isRunning && output.isBlank() -> {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = "Creating ${activeMode?.lowercase() ?: "description"}",
                        body = stringResource(R.string.image_caption_analyzing_the_image_with_the),
                    )
                }
                output.isNotBlank() -> {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(Space.lg)) {
                            activeMode?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(output, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs))
                            }
                        }
                    }
                    if (!isRunning) {
                        com.vervan.chat.ui.common.ResultActions(
                            modifier = Modifier.padding(top = Space.sm),
                            onCopy = {
                                context.getSystemService(android.content.ClipboardManager::class.java).copyText(output, scope, "Caption")
                                scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_copied)) }
                            },
                            onShare = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, output)
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            },
                            onRegenerate = lastMode?.let { m -> { runMode(m) } }
                        )
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = stringResource(R.string.image_caption_couldn_t_generate_a_caption),
                        message = errorText!!,
                        recovery = stringResource(R.string.image_caption_load_a_vision_model_or),
                        actionLabel = lastMode?.let { "Try again" },
                        onAction = lastMode?.let { m -> { runMode(m) } },
                    )
                }
            }
        }
    }
}
