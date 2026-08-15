package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.School
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
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
    var ocrError by remember { mutableStateOf<String?>(null) }

    fun runOcr(files: List<File>) {
        if (files.isEmpty()) return
        ocrRunning = true
        ocrError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var failures = 0
                val text = files.mapNotNull { f ->
                    runCatching { OcrExtractor.extractFromImage(f) }
                        .onFailure { failures++ }
                        .getOrNull()
                        .also { f.delete() }
                }.joinToString("\n\n")
                text to failures
            }
            if (result.second > 0) {
                ocrError = "Could not read ${result.second} selected image(s). They were skipped; try clearer or smaller images."
            }
            if (result.first.isBlank() && result.second > 0) {
                ocrRunning = false
                return@launch
            }
            sourceText = (sourceText + "\n\n" + result.first).trim().take(ValidationLimits.STUDY_SOURCE)
            ocrRunning = false
        }
    }

    // Gallery: copy each picked image into app storage (OCR reads a real File), then OCR.
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val dir = File(app.filesDir, "scans").apply { mkdirs() }
                            val out = File(dir, "card-${System.currentTimeMillis()}-${uri.hashCode()}.jpg")
                            app.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyToLimited(it, ImportLimits.MAX_IMAGE_SOURCE_BYTES) } }
                            require(out.length() > 0) { "Selected image is empty" }
                            require(ImageUtils.normalizeForModel(out)) { "Selected image could not be decoded" }
                            out
                        }.getOrNull()
                    }
                }
                if (result.size < uris.size) {
                    ocrError = "${uris.size - result.size} selected image(s) were rejected because they could not be read or were too large."
                }
                runOcr(result)
            }
        }
    }

    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCamera
        pendingCamera = null
        if (success && file != null) {
            if (runCatching { require(ImageUtils.normalizeForModel(file)) }.isSuccess) runOcr(listOf(file))
            else { file.delete(); ocrError = "The captured image could not be read. Please try again." }
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
                title = { Text(stringResource(R.string.ui_flashcardsfromphotoscreen_158_flashcards_from_photo)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 780.dp) {
                ToolIntro(
                    icon = Icons.Filled.School,
                    title = stringResource(R.string.ui_flashcardsfromphotoscreen_166_turn_notes_into_a_study_deck),
                    body = stringResource(R.string.ui_flashcardsfromphotoscreen_167_photograph_pages_or_notes_to_create_flashcar)
                )
                ResponsiveActions(Modifier.padding(top = Space.lg)) {
                    OutlinedButton(onClick = { requestCamera.launch(android.Manifest.permission.CAMERA) }, enabled = !generating) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.media_camera), modifier = Modifier.padding(start = Space.sm))
                    }
                    OutlinedButton(onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !generating) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.media_gallery), modifier = Modifier.padding(start = Space.sm))
                    }
                }
                if (ocrRunning) {
                    Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.ui_flashcardsfromphotoscreen_182_reading_the_image), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.sm))
                    }
                }
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it.take(ValidationLimits.STUDY_SOURCE) },
                    label = { Text(stringResource(R.string.ui_flashcardsfromphotoscreen_188_study_material_editable)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                    enabled = !generating
                )
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it.take(ValidationLimits.STUDY_SET_NAME) },
                    label = { Text(stringResource(R.string.study_deck_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    enabled = !generating
                )
                Text(stringResource(R.string.ui_flashcardsfromphotoscreen_card_count, cardCount.toInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.md))
                Slider(value = cardCount, onValueChange = { cardCount = it }, valueRange = 5f..25f, steps = 19, enabled = !generating)

                if (generating) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                        shape = MaterialTheme.shapes.medium,
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
                        Text(stringResource(R.string.study_generate), modifier = Modifier.padding(start = Space.sm))
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm))
                }
                ocrError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm))
                }
        }
    }
}
