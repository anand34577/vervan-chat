package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberManagedImagePath
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.common.copyText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.system.toUserMessage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.validation.InputLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera/file image in, on-device ML Kit text out (same recognizer [OcrExtractor] already
 * uses for scanned-PDF import) — a standalone utility, not tied to any chat. Result can be
 * copied or imported into Knowledge as a new document for RAG.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(onBack: () -> Unit, onOpenDocument: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    val managedImagePath = rememberManagedImagePath()
    val imagePath = managedImagePath.path
    var extractedText by rememberSaveable { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun runOcr(file: File) {
        managedImagePath.set(file.absolutePath)
        savedMessage = null
        errorText = null
        isProcessing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { OcrExtractor.extractFromImage(file) } }
            result.onSuccess {
                if (it.length > InputLimits.OCR_TEXT_CHARS) errorText = "OCR returned too much text (maximum ${InputLimits.OCR_TEXT_CHARS} characters)."
                else extractedText = it
            }
                .onFailure { errorText = it.toUserMessage(); extractedText = "" }
            isProcessing = false
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "ocr-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            if (file.length() > ImportLimits.MAX_IMAGE_SOURCE_BYTES || !ImageUtils.normalizeForModel(file)) {
                errorText = "The captured image is too large or unreadable. Choose a smaller JPG or PNG."
                file.delete()
            } else runOcr(file)
        } else file?.delete()
    }
    val requestCameraPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingCameraFile = file
            takePicture.launch(uri)
            } else errorText = "Camera access is off. Choose an image, or allow it in Android Settings → Apps → Vervan → Permissions."
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val dest = withContext(Dispatchers.IO) {
                    val (file, _) = newImageFile()
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyToLimited(it, ImportLimits.MAX_IMAGE_SOURCE_BYTES) } }
                        check(ImageUtils.normalizeForModel(file)) { "The selected image is not readable" }
                        file
                    }.getOrElse { file.delete(); null }
                }
                if (dest != null) runOcr(dest)
            else errorText = "Could not open the image. Choose another one."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_scanner_ocr_scanner)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.ocr_scanner_turn_a_photo_into_editable),
                body = stringResource(R.string.ocr_scanner_choose_an_image_review_its)
            )
            ResponsiveActions {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.media_camera), modifier = Modifier.padding(start = Space.sm))
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.ocr_scanner_from_files), modifier = Modifier.padding(start = Space.sm))
                }
            }
            imagePath?.let { path ->
                val bitmap = rememberThumbnail(path, 800)
                bitmap?.let {
                    Image(
                        it, contentDescription = stringResource(R.string.ocr_scanner_scanned_image),
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = Space.md),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (isProcessing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.ocr_scanner_recognizing_text),
                    body = stringResource(R.string.ocr_scanner_reading_text_from_the_image)
                )
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.ocr_scanner_text_recognition_failed),
                    message = it,
                    recovery = stringResource(R.string.ocr_scanner_use_a_clear_jpg_or)
                )
            }
            if (!isProcessing && imagePath != null && errorText == null) {
                ToolResultHeader(
                    title = if (extractedText.isBlank()) stringResource(R.string.ocr_scanner_no_text_detected) else stringResource(R.string.ocr_scanner_text_recognized),
                    supportingText = if (extractedText.isBlank()) stringResource(R.string.ocr_scanner_try_a_sharper_evenly_lit) else stringResource(R.string.ocr_scanner_review_and_correct_the_result)
                )
                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it.take(InputLimits.OCR_TEXT_CHARS) },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                    minLines = 6,
                    label = { Text(stringResource(R.string.ocr_scanner_recognized_text)) },
                    placeholder = { Text(stringResource(R.string.ocr_scanner_no_text_found)) }
                )
                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.copyText(extractedText, scope, "OCR text")
                        },
                        enabled = extractedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.library_copy), modifier = Modifier.padding(start = Space.sm))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val kb = app.container.db.knowledgeBaseDao().get(KnowledgeBase.SCANS_KNOWLEDGE_BASE_ID)
                                    ?: KnowledgeBase(id = KnowledgeBase.SCANS_KNOWLEDGE_BASE_ID, name = "Scans").also {
                                        app.container.db.knowledgeBaseDao().upsert(it)
                                    }
                                val name = "Scan ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                                val document = app.container.documentImportManager.importRawText(kb.id, name, extractedText)
                                if (document.status == com.vervan.chat.data.db.entities.DocumentStatus.READY) {
                                    savedMessage = "Saved to Knowledge"
                                    onOpenDocument(document.id)
                                } else {
                                    errorText = document.failureReason ?: "The scan could not be indexed (${document.status.name.lowercase()})."
                                }
                            }
                        },
                        enabled = extractedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Description, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.ocr_scanner_save_as_document), modifier = Modifier.padding(start = Space.sm))
                    }
                }
                savedMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.sm))
                }
            }
        }
        }
    }
}
