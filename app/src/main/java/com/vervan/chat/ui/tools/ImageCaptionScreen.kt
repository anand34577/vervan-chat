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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
    var activeMode by remember { mutableStateOf<String?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    fun runMode(mode: CaptionMode) {
        val path = imagePath ?: return
        isRunning = true
        activeMode = mode.label
        output = ""
        scope.launch {
            output = OneShotLlm.run(app, mode.prompt, imagePath = path)?.trim().orEmpty()
            isRunning = false
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
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (visionAvailable == false) {
                Text(
                    "The active model doesn't declare vision support — switch to a vision-capable model in Model Manager to use this tool.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f), enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.height(18.dp)); Text(" Camera")
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.weight(1f), enabled = visionAvailable != false) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.height(18.dp)); Text(" From files")
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
            if (isRunning) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text("Generating…")
                }
            } else if (output.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(output, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Caption", output))
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Icon(Icons.Filled.ContentCopy, null, Modifier.height(18.dp)); Text(" Copy") }
                    }
                }
            }
        }
    }
}
