@file:Suppress("LocalContextGetResourceValueCall")

package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberManagedImagePath
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.common.setSensitiveText
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.model.BarcodeExtractor
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera/file image in, on-device ZXing decode out — the QR/barcode counterpart of
 * [OcrScannerScreen], same shape and reasoning: a standalone utility, not tied to any chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    val managedImagePath = rememberManagedImagePath()
    val imagePath = managedImagePath.path
    var decodedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val capturedImageInvalid = stringResource(com.vervan.chat.R.string.media_image_capture_invalid)
    val cameraAccessOff = stringResource(com.vervan.chat.R.string.media_camera_access_off)
    val selectedImageUnreadable = stringResource(com.vervan.chat.R.string.media_image_unreadable)
    val imageOpenFailed = stringResource(com.vervan.chat.R.string.media_image_open_failed)

    fun runScan(file: File) {
        managedImagePath.set(file.absolutePath)
        errorText = null
        isProcessing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { BarcodeExtractor.extractFromImage(file) } }
            result.onSuccess { decodedText = it }
                .onFailure { errorText = it.toUserMessage(); decodedText = "" }
            isProcessing = false
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "qr-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            if (file.length() > ImportLimits.MAX_IMAGE_SOURCE_BYTES || !ImageUtils.normalizeForModel(file)) {
                errorText = capturedImageInvalid
                file.delete()
            } else runScan(file)
        } else file?.delete()
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingCameraFile = file
            takePicture.launch(uri)
        } else errorText = cameraAccessOff
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val dest = withContext(Dispatchers.IO) {
                    val (file, _) = newImageFile()
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyToLimited(it, ImportLimits.MAX_IMAGE_SOURCE_BYTES) } }
                        check(ImageUtils.normalizeForModel(file)) { selectedImageUnreadable }
                        file
                    }.getOrElse { file.delete(); null }
                }
                if (dest != null) runScan(dest)
                else errorText = imageOpenFailed
            }
        }
    }

    // A decoded value that's just a URL is the common case (menus, tickets, wifi/contact
    // payloads are less so) — offering "Open" only then avoids a dead button the rest of the time.
    val firstUrl = remember(decodedText) {
        decodedText.lineSequence()
            .map { it.substringAfter(": ", it) }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.vervan.chat.R.string.media_qr_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.Filled.QrCodeScanner,
                title = stringResource(com.vervan.chat.R.string.media_qr_intro_title),
                body = stringResource(com.vervan.chat.R.string.media_qr_intro_body)
            )
            Text(
                stringResource(com.vervan.chat.R.string.media_qr_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ResponsiveActions(Modifier.padding(top = Space.md)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(stringResource(com.vervan.chat.R.string.media_camera), modifier = Modifier.padding(start = Space.sm))
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                    Text(stringResource(com.vervan.chat.R.string.media_from_photo), modifier = Modifier.padding(start = Space.sm))
                }
            }
            imagePath?.let { path ->
                val bitmap = rememberThumbnail(path, 800)
                bitmap?.let {
                    Image(
                        it, contentDescription = stringResource(com.vervan.chat.R.string.media_scanned_photo),
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = Space.md),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (isProcessing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(com.vervan.chat.R.string.media_qr_decoding),
                    body = stringResource(com.vervan.chat.R.string.media_qr_reading_body)
                )
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(com.vervan.chat.R.string.media_qr_decoding_failed),
                    message = it,
                    recovery = stringResource(com.vervan.chat.R.string.media_clear_image_recovery)
                )
            }
            if (!isProcessing && imagePath != null && errorText == null) {
                ToolResultHeader(
                    title = if (decodedText.isBlank()) stringResource(com.vervan.chat.R.string.media_qr_no_code) else stringResource(com.vervan.chat.R.string.media_qr_decoded),
                    supportingText = if (decodedText.isBlank()) stringResource(com.vervan.chat.R.string.media_qr_no_code_hint) else stringResource(com.vervan.chat.R.string.media_qr_found)
                )
                OutlinedTextField(
                    value = decodedText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                    minLines = 3,
                    label = { Text(stringResource(com.vervan.chat.R.string.media_qr_value)) },
                    placeholder = { Text(stringResource(com.vervan.chat.R.string.media_qr_no_code_found)) }
                )
                ResponsiveActions(Modifier.padding(top = Space.sm)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setSensitiveText(decodedText, scope, context.getString(com.vervan.chat.R.string.media_qr_value))
                        },
                        enabled = decodedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                        Text(stringResource(com.vervan.chat.R.string.action_copy), modifier = Modifier.padding(start = Space.sm))
                    }
                    if (firstUrl != null) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(firstUrl))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                            Text(stringResource(com.vervan.chat.R.string.media_open_link), modifier = Modifier.padding(start = Space.sm))
                        }
                    }
                }
            }
        }
        }
    }
}
