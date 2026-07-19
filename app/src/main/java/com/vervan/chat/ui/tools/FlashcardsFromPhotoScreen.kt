package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.study.StudyWorkspaceViewModel
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera/gallery photo(s) -> OCR -> LLM-generated flashcard deck, tying the scanner and study
 * systems into one flow. Reuses [OcrExtractor] and [StudyWorkspaceViewModel.generateSet] (the
 * same deck generation the Study workspace uses), so the created deck lands in Study and reviews
 * with the normal flip-card UI. On success it opens the new deck directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsFromPhotoScreen(onBack: () -> Unit, onOpenSet: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: StudyWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { StudyWorkspaceViewModel(app) } })
    val scope = rememberCoroutineScope()
    val generating by vm.generating.collectAsState()
    val generationStage by vm.generationStage.collectAsState()
    val error by vm.error.collectAsState()

    var sourceText by remember { mutableStateOf("") }
    var deckName by remember {
        mutableStateOf("Photo deck " + java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
    }
    var cardCount by remember { mutableFloatStateOf(10f) }
    var ocrRunning by remember { mutableStateOf(false) }

    fun runOcr(files: List<File>) {
        if (files.isEmpty()) return
        ocrRunning = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                files.joinToString("\n\n") { f ->
                    runCatching { OcrExtractor.extractFromImage(f) }.getOrDefault("").also { f.delete() } // only the text is needed
                }
            }
            sourceText = (sourceText + "\n\n" + text).trim()
            ocrRunning = false
        }
    }

    // Gallery: copy each picked image into app storage (OCR reads a real File), then OCR.
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val dir = File(app.filesDir, "scans").apply { mkdirs() }
                            val out = File(dir, "card-${System.currentTimeMillis()}-${uri.hashCode()}.jpg")
                            app.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                            out.takeIf { it.length() > 0 }?.also { ImageUtils.fixOrientation(it) }
                        }.getOrNull()
                    }
                }
                runOcr(files)
            }
        }
    }

    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCamera
        pendingCamera = null
        if (success && file != null) {
            ImageUtils.fixOrientation(file)
            runOcr(listOf(file))
        }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val dir = File(app.filesDir, "scans").apply { mkdirs() }
            val file = File(dir, "card-${System.currentTimeMillis()}.jpg")
            pendingCamera = file
            takePicture.launch(androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards from photo") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 780.dp) {
            Column(Modifier.fillMaxSize().padding(Space.lg).verticalScroll(rememberScrollState())) {
                ToolIntro(
                    icon = Icons.Filled.School,
                    title = "Turn notes into a study deck",
                    body = "Snap or pick photos of textbook pages or handwritten notes. The text is read on-device and turned into review flashcards."
                )
                Row(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(onClick = { requestCamera.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f), enabled = !generating) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                        Text("  Camera")
                    }
                    OutlinedButton(onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), enabled = !generating) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                        Text("  Gallery")
                    }
                }
                if (ocrRunning) {
                    Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Reading the image…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text("Study material (editable)") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                    enabled = !generating
                )
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    enabled = !generating
                )
                Text("${cardCount.toInt()} cards", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.md))
                Slider(value = cardCount, onValueChange = { cardCount = it }, valueRange = 5f..25f, steps = 19, enabled = !generating)

                if (generating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                    ) {
                        Column(Modifier.padding(Space.lg)) {
                            Text(generationStage, style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.sm))
                        }
                    }
                } else {
                    Button(
                        onClick = { vm.generateSet(deckName.trim(), sourceText, cardCount.toInt(), "", "balanced") { onOpenSet(deckName.trim()) } },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                        enabled = sourceText.isNotBlank() && deckName.isNotBlank() && !ocrRunning
                    ) {
                        Icon(Icons.Filled.School, null, Modifier.size(18.dp))
                        Text("  Generate deck")
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm))
                }
            }
        }
    }
}
