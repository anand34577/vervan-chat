package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.common.copyText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.model.readTextLimited
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.validation.InputLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHUNK_CHARS = 1500

private fun chunks(text: String): List<String> =
    text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        .fold(mutableListOf("")) { acc, para ->
            if (acc.last().length + para.length > CHUNK_CHARS) acc.add(para) else acc[acc.lastIndex] = if (acc.last().isBlank()) para else acc.last() + "\n\n" + para
            acc
        }.filter { it.isNotBlank() }

/**
 * Chunk-based document comparison — paragraph-chunks both versions, pairs them by position, and
 * only sends chunks that actually differ (skipping identical ones) to the model for explanation.
 * Keeps each call small regardless of overall document length instead of needing a huge context
 * window for a single whole-document diff.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentComparisonScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var textA by rememberSaveable { mutableStateOf("") }
    var textB by rememberSaveable { mutableStateOf("") }
    var isComparing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var comparedOnce by remember { mutableStateOf(false) }
    var progress by rememberSaveable { mutableStateOf("") }
    var compareJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val pickA = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { reader ->
                            reader.readTextLimited(InputLimits.DOCUMENT_COMPARISON_SIDE_CHARS)
                        }
                    }
                }.getOrNull()
            }
            if (text != null) textA = text
            else errorText = "Could not read Version A. Choose a text file or paste it."
        }
    }
    val pickB = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { reader ->
                            reader.readTextLimited(InputLimits.DOCUMENT_COMPARISON_SIDE_CHARS)
                        }
                    }
                }.getOrNull()
            }
            if (text != null) textB = text
            else errorText = "Could not read Version B. Choose a text file or paste it."
        }
    }

    fun compare() {
        isComparing = true
        results = emptyList()
        errorText = null
        comparedOnce = false
        progress = ""
        compareJob?.cancel()
        compareJob = scope.launch {
            try {
                val chunksA = chunks(textA)
                val chunksB = chunks(textB)
                val pairCount = maxOf(chunksA.size, chunksB.size)
                val found = mutableListOf<Pair<Int, String>>()
                for (i in 0 until pairCount) {
                    val a = chunksA.getOrNull(i).orEmpty()
                    val b = chunksB.getOrNull(i).orEmpty()
                    if (a.trim() == b.trim()) continue
                    progress = "Comparing section ${i + 1} of $pairCount…"
                    val prompt = "Compare version A and version B of this section and explain: what changed, important additions or removals, " +
                        "conflicting values, and any potentially risky clauses. If one side is empty, describe what was entirely added or removed. " +
                        "Be concise.\n\nVersion A:\n${a.ifBlank { "(missing)" }}\n\nVersion B:\n${b.ifBlank { "(missing)" }}"
                    val explanation = OneShotLlm.run(
                        app, prompt,
                        runContext = com.vervan.chat.llm.ToolRunContext("tools/document-comparison", "Document comparison", "Version A:\n$a\n\nVersion B:\n$b"),
                    )?.trim()
                        ?: throw IllegalStateException("No model is ready. Open Settings → AI models, load one, then compare again.")
                    if (explanation.isNotBlank()) {
                        found += (i + 1) to explanation
                        results = found.toList() // surface each section as it completes, not all at the end
                    }
                }
                comparedOnce = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isComparing = false
                progress = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.document_comparison_document_comparison)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            ToolIntro(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                title = stringResource(R.string.document_comparison_see_exactly_what_changed),
                body = stringResource(R.string.document_comparison_compare_two_versions_for_changes)
            )
            CompareSide(
                step = 1,
                title = stringResource(R.string.document_comparison_original_version_a),
                value = textA,
                onValueChange = { textA = it.take(InputLimits.DOCUMENT_COMPARISON_SIDE_CHARS) },
                placeholder = stringResource(R.string.document_comparison_paste_the_original_version),
                onLoad = { pickA.launch("text/*") },
                enabled = !isComparing
            )
            CompareSide(
                step = 2,
                title = stringResource(R.string.document_comparison_revised_version_b),
                value = textB,
                onValueChange = { textB = it.take(InputLimits.DOCUMENT_COMPARISON_SIDE_CHARS) },
                placeholder = stringResource(R.string.document_comparison_paste_the_revised_version),
                onLoad = { pickB.launch("text/*") },
                enabled = !isComparing
            )
            ToolPrimaryButton(
                label = stringResource(R.string.document_comparison_compare_versions),
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                onClick = ::compare,
                blockedReason = when {
                    textA.isBlank() || textB.isBlank() -> stringResource(R.string.compare_blocked_both)
                    textA.trim() == textB.trim() -> stringResource(R.string.compare_blocked_same)
                    else -> null
                },
                running = isComparing,
                onStop = { compareJob?.cancel(); isComparing = false; progress = "" }
            )

            if (isComparing || errorText != null || comparedOnce || results.isNotEmpty()) {
                ToolStep(3, stringResource(R.string.tool_step_review_result))
            }
            if (isComparing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = progress.ifBlank { "Preparing the comparison" },
                    body = stringResource(R.string.document_comparison_comparing_each_section_locally_results)
                )
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.document_comparison_couldn_t_compare_the_documents),
                    message = it,
                    recovery = stringResource(R.string.document_comparison_check_both_versions_or_shorten),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = { compare() },
                )
            }
            if (comparedOnce && results.isEmpty() && errorText == null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(stringResource(R.string.document_comparison_no_meaningful_differences_found), Modifier.padding(Space.md), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (results.isNotEmpty()) {
                ToolResultHeader(
                    title = stringResource(R.string.document_comparison_comparison_ready),
                    supportingText = "${results.size} changed ${if (results.size == 1) "section" else "sections"} found"
                )
            }
            results.forEach { (section, explanation) ->
                Card(Modifier.fillMaxWidth().padding(top = Space.md), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(Space.md)) {
                        Text(stringResource(R.string.document_comparison_section_arg1, section), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            com.vervan.chat.ui.common.MarkdownLiteText(explanation, modifier = Modifier.padding(top = Space.xs))
                        }
                    }
                }
            }
            if (results.isNotEmpty() && !isComparing) {
                val report = results.joinToString("\n\n") { (section, text) -> "Section $section\n$text" }
                com.vervan.chat.ui.common.ResultActions(
                    modifier = Modifier.padding(top = Space.sm),
                    onCopy = { context.getSystemService(android.content.ClipboardManager::class.java).copyText(report, scope, "Comparison") },
                    onShare = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report)
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    }
                )
            }
        }
    }
}

@Composable
private fun CompareSide(
    step: Int,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onLoad: () -> Unit,
    enabled: Boolean,
) {
    ToolStep(step, title)
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
        minLines = 4, enabled = enabled,
        placeholder = { Text(placeholder) },
        supportingText = { ToolCharCount(value.length, InputLimits.DOCUMENT_COMPARISON_SIDE_CHARS) },
        trailingIcon = if (value.isNotEmpty() && enabled) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.tool_clear_input)) } }
        } else null
    )
    androidx.compose.material3.TextButton(onClick = onLoad, enabled = enabled) {
        Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp))
        Text(stringResource(R.string.compare_load_file), modifier = Modifier.padding(start = Space.sm))
    }
}
