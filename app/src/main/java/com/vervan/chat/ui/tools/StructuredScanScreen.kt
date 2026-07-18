package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPage
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.model.ImageUtils
import com.vervan.chat.model.OcrExtractor
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ScanKind(val title: String, val fields: List<Pair<String, String>>) {
    BUSINESS_CARD(
        "Business card scanner",
        listOf("name" to "Name", "company" to "Company", "jobTitle" to "Job title", "phone" to "Phone", "email" to "Email", "website" to "Website", "address" to "Address")
    ),
    RECEIPT(
        "Receipt scanner",
        listOf("merchant" to "Merchant", "date" to "Date", "total" to "Total", "tax" to "Tax", "currency" to "Currency", "paymentMethod" to "Payment method")
    ),
    TABLE("Table scanner", emptyList()),
    CUSTOM("Smart form filler", emptyList())
}

/**
 * Camera/file image -> OCR -> LLM extracts structured fields (JSON for business
 * card/receipt, a Markdown table for the table kind) -> editable fields + export.
 * One screen parameterized by [ScanKind] instead of three near-identical ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredScanScreen(kind: ScanKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var imagePath by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var fields by remember { mutableStateOf(mapOf<String, String>()) }
    var lineItems by remember { mutableStateOf("") }
    var markdownTable by remember { mutableStateOf("") }
    var customFieldsInput by remember { mutableStateOf("") }
    var lastFile by remember { mutableStateOf<File?>(null) }
    val activeFields = remember(kind, customFieldsInput) {
        if (kind == ScanKind.CUSTOM) customFieldsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { it to it }
        else kind.fields
    }

    fun runExtraction(file: File) {
        imagePath = file.absolutePath
        lastFile = file
        isProcessing = true
        errorText = null
        statusMessage = null
        fields = emptyMap()
        markdownTable = ""
        lineItems = ""
        scope.launch {
            try {
                val ocrText = withContext(Dispatchers.IO) { runCatching { OcrExtractor.extractFromImage(file) }.getOrDefault("") }
                if (ocrText.isBlank()) {
                    errorText = "No readable text was found in that image. Try a clearer, closer photo."
                    return@launch
                }
                if (kind == ScanKind.TABLE) {
                    val prompt = "Convert the table found in the following OCR text into a Markdown table (pipe-separated, with a header row). " +
                        "Respond with ONLY the Markdown table, no explanation.\n\nOCR text:\n$ocrText"
                    markdownTable = OneShotLlm.run(app, prompt)?.trim()
                        ?: throw IllegalStateException("No generation model is active. Load one from Models, then scan again.")
                    if (markdownTable.isBlank()) errorText = "The model couldn't build a table from that image. Try again."
                } else {
                    val keys = activeFields.joinToString(", ") { it.first }
                    val prompt = "From the following OCR text" +
                        (if (kind == ScanKind.BUSINESS_CARD) " (from a business card)" else if (kind == ScanKind.RECEIPT) " (from a receipt)" else " (from a document/ID/invoice/business card)") +
                        ", extract a JSON object with exactly these keys: $keys" +
                        (if (kind == ScanKind.RECEIPT) ", lineItems (an array of short strings like \"Item name - price\")" else "") +
                        ". Use an empty string (or empty array) for anything not found. Respond with ONLY the JSON object, no markdown fences, no explanation.\n\nOCR text:\n$ocrText"
                    val raw = OneShotLlm.run(app, prompt)?.trim()
                        ?: throw IllegalStateException("No generation model is active. Load one from Models, then scan again.")
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
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                        ImageUtils.fixOrientation(file)
                        file
                    }.getOrNull()
                }
                if (dest != null) runExtraction(dest)
            }
        }
    }

    fun copyText(text: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(kind.title, text))
    }

    fun exportFile(name: String, content: String, mime: String) {
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(content)
        com.vervan.chat.ui.common.openWithExternalApp(context, file, mime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
       Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)) {
            FeatureHero(
                icon = Icons.Filled.PhotoCamera,
                eyebrow = "Scan and extract",
                title = kind.title,
                body = "Capture a clear image, review the extracted fields, then export."
            )
            VervanSectionHeader("1 · Choose what to capture")
            if (kind == ScanKind.CUSTOM) {
                OutlinedTextField(
                    value = customFieldsInput, onValueChange = { customFieldsInput = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    label = { Text("Fields to extract") },
                    placeholder = { Text("e.g. name, ID number, date of birth, expiry date") }
                )
            }
            val captureEnabled = kind != ScanKind.CUSTOM || activeFields.isNotEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestCameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = captureEnabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(" Camera")
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = captureEnabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                    Text(" From files")
                }
            }
            VervanSectionHeader("2 · Check the source")
            imagePath?.let { path ->
                val bitmap = remember(path) { ImageUtils.decodeThumbnail(path, 700)?.asImageBitmap() }
                bitmap?.let { Image(it, "Scanned image", Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp), contentScale = ContentScale.Fit) }
            } ?: Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(
                "Take a photo or choose an image to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.lg)
                )
            }
            VervanSectionHeader("3 · Review extracted data")
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Couldn't extract data",
                    message = it,
                    recovery = "Use a sharper, well-lit image, then try again.",
                    actionLabel = lastFile?.let { "Try again" },
                    onAction = lastFile?.let { file -> { runExtraction(file) } },
                    modifier = Modifier.padding(bottom = Space.md)
                )
            }
            if (isProcessing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Extracting ${kind.title.lowercase()}",
                    body = "Reading the image and organizing its contents locally."
                )
            } else if (kind == ScanKind.TABLE) {
                if (markdownTable.isNotBlank()) {
                    OutlinedTextField(
                        value = markdownTable, onValueChange = { markdownTable = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp), minLines = 6, label = { Text("Markdown table") }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyText(markdownTable) }, modifier = Modifier.weight(1f)) { Text("Copy Markdown") }
                        OutlinedButton(onClick = { copyText(markdownToCsv(markdownTable)) }, modifier = Modifier.weight(1f)) { Text("Copy CSV") }
                        OutlinedButton(onClick = { copyText(markdownToJson(markdownTable)) }, modifier = Modifier.weight(1f)) { Text("Copy JSON") }
                    }
                }
            } else if (fields.isNotEmpty()) {
                Column(Modifier.padding(top = 16.dp)) {
                    activeFields.forEach { (key, label) ->
                        OutlinedTextField(
                            value = fields[key].orEmpty(),
                            onValueChange = { fields = fields + (key to it) },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    if (kind == ScanKind.RECEIPT) {
                        OutlinedTextField(
                            value = lineItems, onValueChange = { lineItems = it },
                            label = { Text("Line items") }, minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    when (kind) {
                        ScanKind.BUSINESS_CARD -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val insertContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
                            OutlinedButton(
                                onClick = {
                                    insertContact.launch(
                                        android.content.Intent(android.provider.ContactsContract.Intents.Insert.ACTION).apply {
                                            type = android.provider.ContactsContract.RawContacts.CONTENT_TYPE
                                            putExtra(android.provider.ContactsContract.Intents.Insert.NAME, fields["name"])
                                            putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, fields["company"])
                                            putExtra(android.provider.ContactsContract.Intents.Insert.JOB_TITLE, fields["jobTitle"])
                                            putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, fields["phone"])
                                            putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, fields["email"])
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.ContactPage, null, Modifier.size(18.dp)); Text(" Save contact") }
                            Button(
                                onClick = { exportFile("contact-${System.currentTimeMillis()}.vcf", buildVCard(fields), "text/x-vcard") },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(" Export .vcf") }
                        }
                        ScanKind.RECEIPT -> Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val header = activeFields.joinToString(",") { it.first }
                                        val row = activeFields.joinToString(",") { "\"${fields[it.first].orEmpty().replace("\"", "'")}\"" }
                                        exportFile("receipt-${System.currentTimeMillis()}.csv", "$header\n$row\n", "text/csv")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(" Export CSV") }
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
                                ) { Text("Log as expense") }
                            }
                            statusMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                        }
                        ScanKind.CUSTOM -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val json = JSONObject().apply { activeFields.forEach { (key, _) -> put(key, fields[key].orEmpty()) } }
                                    copyText(json.toString(2))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Copy JSON") }
                            Button(
                                onClick = {
                                    val header = activeFields.joinToString(",") { it.first }
                                    val row = activeFields.joinToString(",") { "\"${fields[it.first].orEmpty().replace("\"", "'")}\"" }
                                    exportFile("form-${System.currentTimeMillis()}.csv", "$header\n$row\n", "text/csv")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(" Export CSV") }
                        }
                        else -> {}
                    }
                }
            }
        }
       }
      }
    }
}

private fun buildVCard(fields: Map<String, String>): String = buildString {
    appendLine("BEGIN:VCARD")
    appendLine("VERSION:3.0")
    appendLine("FN:${fields["name"].orEmpty()}")
    appendLine("ORG:${fields["company"].orEmpty()}")
    appendLine("TITLE:${fields["jobTitle"].orEmpty()}")
    appendLine("TEL:${fields["phone"].orEmpty()}")
    appendLine("EMAIL:${fields["email"].orEmpty()}")
    appendLine("URL:${fields["website"].orEmpty()}")
    appendLine("ADR:;;${fields["address"].orEmpty()};;;;")
    appendLine("END:VCARD")
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
