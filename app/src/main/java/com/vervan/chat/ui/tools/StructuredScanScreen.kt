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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.rememberManagedImagePath
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.copyText
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ScanKind(val title: String, val fields: List<Pair<String, String>>) {
    RECEIPT(
        "Receipt scanner",
        listOf("merchant" to "Merchant", "date" to "Date", "total" to "Total", "tax" to "Tax", "currency" to "Currency", "paymentMethod" to "Payment method")
    ),
    TABLE("Table scanner", emptyList()),
    CUSTOM("Smart form filler", emptyList())
}

/**
 * Camera/file image -> OCR -> LLM extracts structured fields (JSON for receipts/forms,
 * a Markdown table for the table kind) -> editable fields + export.
 * One screen parameterized by [ScanKind] instead of three near-identical ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredScanScreen(kind: ScanKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    val managedImagePath = rememberManagedImagePath()
    val imagePath = managedImagePath.path
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var fields by remember { mutableStateOf(mapOf<String, String>()) }
    var lineItems by rememberSaveable { mutableStateOf("") }
    var markdownTable by rememberSaveable { mutableStateOf("") }
    var customFieldsInput by rememberSaveable { mutableStateOf("") }
    var lastFile by remember { mutableStateOf<File?>(null) }
    val activeFields = remember(kind, customFieldsInput) {
        if (kind == ScanKind.CUSTOM) customFieldsInput.split(",").take(InputLimits.STRUCTURED_FIELD_COUNT)
            .map { it.trim().take(InputLimits.STRUCTURED_FIELD_CHARS) }.filter { it.isNotBlank() }.map { it to it }
        else kind.fields
    }

    fun runExtraction(file: File) {
        managedImagePath.set(file.absolutePath)
        lastFile = file
        isProcessing = true
        errorText = null
        statusMessage = null
        fields = emptyMap()
        markdownTable = ""
        lineItems = ""
        scope.launch {
            try {
                val ocrText = withContext(Dispatchers.IO) {
                    runCatching { OcrExtractor.extractFromImage(file) }
                        .getOrElse { throw IllegalStateException("Could not read OCR text: ${it.message}") }
                }
                if (ocrText.length > InputLimits.OCR_TEXT_CHARS) throw IllegalStateException("OCR text exceeds the safe limit")
                if (ocrText.isBlank()) {
                    errorText = "No readable text was found in that image. Try a clearer, closer photo."
                    return@launch
                }
                if (kind == ScanKind.TABLE) {
                    val prompt = "Convert the table found in the following OCR text into a Markdown table (pipe-separated, with a header row). " +
                        "Respond with ONLY the Markdown table, no explanation.\n\nOCR text:\n$ocrText"
                    markdownTable = OneShotLlm.run(
                        app, prompt,
                        runContext = com.vervan.chat.llm.ToolRunContext("tools/table-scanner", "Table scanner", ocrText),
                    )?.trim()
                        ?: throw IllegalStateException("No model is ready. Open Settings → AI models, load one, then scan again.")
                    if (markdownTable.isBlank()) errorText = "The model couldn't build a table from that image. Try again."
                } else {
                    val keys = activeFields.joinToString(", ") { it.first }
                    val prompt = "From the following OCR text" +
                        (if (kind == ScanKind.RECEIPT) " (from a receipt)" else " (from a document or image)") +
                        ", extract a JSON object with exactly these keys: $keys" +
                        (if (kind == ScanKind.RECEIPT) ", lineItems (an array of short strings like \"Item name - price\")" else "") +
                        ". Use an empty string (or empty array) for anything not found. Respond with ONLY the JSON object, no markdown fences, no explanation.\n\nOCR text:\n$ocrText"
                    val route = if (kind == ScanKind.RECEIPT) "tools/receipt-scanner" else "tools/smart-form-filler"
                    val raw = OneShotLlm.run(
                        app, prompt,
                        runContext = com.vervan.chat.llm.ToolRunContext(route, kind.title, ocrText),
                    )?.trim()
                        ?: throw IllegalStateException("No model is ready. Open Settings → AI models, load one, then scan again.")
                    val json = runCatching { JSONObject(raw.substringAfter("{").let { "{$it" }.substringBeforeLast("}").let { "$it}" }) }.getOrNull()
                    fields = activeFields.associate { (key, _) -> key to (json?.optString(key).orEmpty()) }
                    lineItems = json?.optJSONArray("lineItems")?.let { arr -> (0 until arr.length()).joinToString("\n") { arr.optString(it) } }.orEmpty()
                    if (fields.values.all { it.isBlank() }) errorText = "Couldn't extract those fields. You can edit the source photo and try again."
                }
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isProcessing = false
            }
        }
    }

    fun newImageFile(): Pair<File, android.net.Uri> {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "scan-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            ImageUtils.fixOrientation(file)
            runExtraction(file)
        } else file?.delete()
    }
    val requestCameraPermission = com.vervan.chat.ui.common.rememberPermissionRequester { granted ->
        if (granted) {
            val (file, uri) = newImageFile()
            pendingFile = file
            takePicture.launch(uri)
        }
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
                if (dest != null) runExtraction(dest)
            }
        }
    }

    fun copyText(text: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.copyText(text, scope, kind.title)
    }

    fun exportFile(name: String, content: String, mime: String) {
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(content)
        com.vervan.chat.system.pruneOldExports(dir)
        com.vervan.chat.ui.common.openWithExternalApp(context, file, mime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(kind.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding) {
            FeatureHero(
                icon = Icons.Filled.PhotoCamera,
                eyebrow = "Scan and extract",
                // The app bar already names this screen.
                title = null,
                dismissKey = kind.title,
                body = stringResource(R.string.structured_scan_capture_a_clear_image_review)
            )
            VervanSectionHeader(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_structured_scan_1_choose_what_to_capture))
            if (kind == ScanKind.CUSTOM) {
                OutlinedTextField(
                    value = customFieldsInput, onValueChange = { customFieldsInput = it.take(InputLimits.STRUCTURED_FIELD_COUNT * (InputLimits.STRUCTURED_FIELD_CHARS + 1)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.md),
                    label = { Text(stringResource(R.string.structured_scan_fields_to_extract)) },
                    placeholder = { Text(stringResource(R.string.structured_scan_e_g_name_id_number)) },
                    // Camera and From files stay disabled until this has a field; say so.
                    supportingText = { Text(stringResource(R.string.structured_scan_fields_required)) }
                )
            }
            val captureEnabled = kind != ScanKind.CUSTOM || activeFields.isNotEmpty()
            ResponsiveActions {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = captureEnabled) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.media_camera), modifier = Modifier.padding(start = Space.sm))
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = captureEnabled) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.structured_scan_from_files), modifier = Modifier.padding(start = Space.sm))
                }
            }
            VervanSectionHeader(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_structured_scan_2_check_the_source))
            imagePath?.let { path ->
                val bitmap = rememberThumbnail(path, 700)
                bitmap?.let { Image(it, "Scanned image", Modifier.fillMaxWidth().height(180.dp).padding(top = Space.md), contentScale = ContentScale.Fit) }
            } ?: Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(
                    stringResource(R.string.structured_scan_take_a_photo_or_choose),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.lg)
                )
            }
            // A step heading with nothing under it read as a broken section.
            if (errorText != null || isProcessing || markdownTable.isNotBlank() || fields.isNotEmpty()) {
                VervanSectionHeader(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_structured_scan_3_review_extracted_data))
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.structured_scan_couldn_t_extract_data),
                    message = it,
                    recovery = stringResource(R.string.structured_scan_use_a_sharper_well_lit),
                    actionLabel = lastFile?.let { "Try again" },
                    onAction = lastFile?.let { file -> { runExtraction(file) } },
                    modifier = Modifier.padding(bottom = Space.md)
                )
            }
            if (isProcessing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Extracting ${kind.title.lowercase()}",
                    body = stringResource(R.string.structured_scan_reading_the_image_and_organizing)
                )
            } else if (kind == ScanKind.TABLE) {
                if (markdownTable.isNotBlank()) {
                    OutlinedTextField(
                        value = markdownTable, onValueChange = { markdownTable = it.take(InputLimits.OCR_TEXT_CHARS) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg), minLines = 6, label = { Text(stringResource(R.string.structured_scan_markdown_table)) }
                    )
                    ResponsiveActions(Modifier.padding(top = Space.sm)) {
                        OutlinedButton(onClick = { copyText(markdownTable) }) { Text(stringResource(R.string.structured_scan_copy_markdown)) }
                        OutlinedButton(onClick = { copyText(markdownToCsv(markdownTable)) }) { Text(stringResource(R.string.structured_scan_copy_csv)) }
                        OutlinedButton(onClick = { copyText(markdownToJson(markdownTable)) }) { Text(stringResource(R.string.structured_scan_copy_json)) }
                    }
                }
            } else if (fields.isNotEmpty()) {
                Column(Modifier.padding(top = Space.lg)) {
                    activeFields.forEach { (key, label) ->
                        OutlinedTextField(
                            value = fields[key].orEmpty(),
                            onValueChange = { fields = fields + (key to it.take(4_000)) },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm)
                        )
                    }
                    if (kind == ScanKind.RECEIPT) {
                        OutlinedTextField(
                            value = lineItems, onValueChange = { lineItems = it.take(20_000) },
                            label = { Text(stringResource(R.string.structured_scan_line_items)) }, minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm)
                        )
                    }
                    when (kind) {
                        ScanKind.RECEIPT -> Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                OutlinedButton(
                                    onClick = {
                                        val header = activeFields.joinToString(",") { it.first }
                                        val row = activeFields.joinToString(",") { "\"${fields[it.first].orEmpty().replace("\"", "'")}\"" }
                                        exportFile("receipt-${System.currentTimeMillis()}.csv", "$header\n$row\n", "text/csv")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.structured_scan_export_csv), modifier = Modifier.padding(start = Space.sm)) }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            app.container.db.expenseDao().upsert(
                                                com.vervan.chat.data.db.entities.Expense(
                                                    merchant = fields["merchant"].orEmpty().ifBlank { "Unknown" },
                                                    amount = fields["total"]?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: 0.0,
                                                    currency = fields["currency"].orEmpty(),
                                                    paymentMethod = fields["paymentMethod"].orEmpty()
                                                )
                                            )
                                            statusMessage = "Logged to expense ledger"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.structured_scan_log_as_expense)) }
                            }
                            statusMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.xs)) }
                        }
                        ScanKind.CUSTOM -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            OutlinedButton(
                                onClick = {
                                    val json = JSONObject().apply { activeFields.forEach { (key, _) -> put(key, fields[key].orEmpty()) } }
                                    copyText(json.toString(2))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.structured_scan_copy_json)) }
                            Button(
                                onClick = {
                                    val header = activeFields.joinToString(",") { it.first }
                                    val row = activeFields.joinToString(",") { "\"${fields[it.first].orEmpty().replace("\"", "'")}\"" }
                                    exportFile("form-${System.currentTimeMillis()}.csv", "$header\n$row\n", "text/csv")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.structured_scan_export_csv), modifier = Modifier.padding(start = Space.sm)) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

private fun parseMarkdownRows(markdown: String): List<List<String>> =
    markdown.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("|") }
        .filterNot { row -> row.replace("|", "").replace("-", "").replace(":", "").isBlank() } // separator row
        .map { row -> row.trim('|').split("|").map { it.trim() } }
        .toList()

private fun markdownToCsv(markdown: String): String =
    parseMarkdownRows(markdown).joinToString("\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "'")}\"" } }

private fun markdownToJson(markdown: String): String {
    val rows = parseMarkdownRows(markdown)
    if (rows.isEmpty()) return "[]"
    val header = rows.first()
    val body = rows.drop(1)
    val arr = org.json.JSONArray()
    body.forEach { row ->
        val obj = JSONObject()
        header.forEachIndexed { i, key -> obj.put(key.ifBlank { "col$i" }, row.getOrNull(i).orEmpty()) }
        arr.put(obj)
    }
    return arr.toString(2)
}
