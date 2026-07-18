package com.vervan.chat.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.UploadFile
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
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space
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

    var textA by remember { mutableStateOf("") }
    var textB by remember { mutableStateOf("") }
    var isComparing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var comparedOnce by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    val pickA = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull() }
            if (text != null) textA = text
            else errorText = "Could not read Version A. Choose a text file or paste it."
        }
    }
    val pickB = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull() }
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
        scope.launch {
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
                    val explanation = OneShotLlm.run(app, prompt)?.trim()
                        ?: throw IllegalStateException("No generation model is active. Load one from Models, then compare again.")
                    if (explanation.isNotBlank()) {
                        found += (i + 1) to explanation
                        results = found.toList() // surface each section as it completes, not all at the end
                    }
                }
                comparedOnce = true
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
                title = { Text("Document comparison") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            ToolIntro(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                title = "See exactly what changed",
                body = "Compare two versions for changes, conflicts, and risky clauses."
            )
            ToolSection(
                title = "Choose both versions",
                description = "Paste text or load a text file. Content stays local.",
                icon = Icons.Filled.UploadFile
            ) {
            Text("Original · Version A", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(value = textA, onValueChange = { textA = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Paste the original version") })
            OutlinedButton(onClick = { pickA.launch("text/*") }, modifier = Modifier.fillMaxWidth()) { Text("Load Version A") }

            Text("Revised · Version B", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.sm))
            OutlinedTextField(value = textB, onValueChange = { textB = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("Paste the revised version") })
            OutlinedButton(onClick = { pickB.launch("text/*") }, modifier = Modifier.fillMaxWidth()) { Text("Load Version B") }

            Button(
                onClick = ::compare,
                enabled = textA.isNotBlank() && textB.isNotBlank() && !isComparing,
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
            ) { Text("Compare versions") }
            }

            if (isComparing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = progress.ifBlank { "Preparing the comparison" },
                    body = "Comparing each section locally. Results appear as they finish."
                )
            }
            errorText?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Couldn't compare the documents",
                    message = it,
                    recovery = "Check both versions or shorten large sections, then try again.",
                    actionLabel = "Try again",
                    onAction = { compare() },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            if (comparedOnce && results.isEmpty() && errorText == null) {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text("No meaningful differences found.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (results.isNotEmpty()) {
                ToolResultHeader(
                    title = "Comparison ready",
                    supportingText = "${results.size} changed ${if (results.size == 1) "section" else "sections"} found"
                )
            }
            results.forEach { (section, explanation) ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Section $section", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        }
    }
}
