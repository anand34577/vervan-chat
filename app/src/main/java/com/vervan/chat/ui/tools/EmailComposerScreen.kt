package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val TONES = listOf("Friendly", "Formal", "Assertive", "Apologetic", "Enthusiastic", "Neutral")
private val LENGTHS = listOf("Short", "Medium", "Long")

/** Structured reply drafting — no email account access needed, works entirely from pasted/typed
 * text (spec: "no email access required initially"). The draft streams in and a model-load
 * failure surfaces in-line instead of crashing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var originalMessage by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var keyPoints by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("Friendly") }
    var length by remember { mutableStateOf("Medium") }
    var toneMenuOpen by remember { mutableStateOf(false) }
    var lengthMenuOpen by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var genJob by remember { mutableStateOf<Job?>(null) }

    fun generate() {
        genJob?.cancel()
        isGenerating = true
        output = ""
        errorText = null
        genJob = scope.launch {
            try {
                val prompt = buildString {
                    appendLine("Draft a reply email/message with these parameters:")
                    appendLine("Tone: $tone")
                    appendLine("Length: $length")
                    if (relationship.isNotBlank()) appendLine("Relationship to recipient: $relationship")
                    if (keyPoints.isNotBlank()) appendLine("Key points to include: $keyPoints")
                    if (originalMessage.isNotBlank()) { appendLine(); appendLine("Original message being replied to:"); appendLine(originalMessage) }
                    appendLine()
                    append("Respond with ONLY the drafted reply, no preamble.")
                }
                val flow = OneShotLlm.stream(app, prompt)
                if (flow == null) {
                    errorText = "No generation model is active. Load one from Models, then draft again."
                } else {
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    flow.collect {
                        sb.append(it)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60) { output = sb.toString().trim(); lastEmit = now }
                    }
                    output = sb.toString().trim()
                    if (output.isBlank()) errorText = "The model returned an empty draft. Try again."
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorText = t.toUserMessage()
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email & message composer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Column(
              Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg)
          ) {
            FeatureHero(
                icon = Icons.Filled.Mail,
                eyebrow = "On-device assistant",
                title = "Email & message composer",
                body = "Draft an email from a few key points. No account access needed."
            )
            OutlinedTextField(
                value = originalMessage, onValueChange = { originalMessage = it },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg), minLines = 3,
                shape = MaterialTheme.shapes.large,
                label = { Text("Original message (optional)") }
            )
            OutlinedTextField(
                value = keyPoints, onValueChange = { keyPoints = it },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm), minLines = 2,
                shape = MaterialTheme.shapes.large,
                label = { Text("Key points to include") }
            )
            OutlinedTextField(
                value = relationship, onValueChange = { relationship = it },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                shape = MaterialTheme.shapes.large,
                label = { Text("Relationship to recipient (optional)") }
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { toneMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tone: $tone", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = toneMenuOpen, onDismissRequest = { toneMenuOpen = false }) {
                        TONES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tone = t; toneMenuOpen = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { lengthMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Length: $length", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = lengthMenuOpen, onDismissRequest = { lengthMenuOpen = false }) {
                        LENGTHS.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { length = l; lengthMenuOpen = false }) }
                    }
                }
            }
            if (isGenerating) {
                OutlinedButton(onClick = { genJob?.cancel(); isGenerating = false }, modifier = Modifier.fillMaxWidth().padding(top = Space.md)) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Text(" Stop")
                }
            } else {
                Button(
                    onClick = ::generate,
                    enabled = keyPoints.isNotBlank() || originalMessage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                ) { Text("Draft reply") }
            }

            when {
                isGenerating && output.isBlank() -> {
                    Row(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Drafting…", modifier = Modifier.padding(start = Space.md))
                    }
                }
                output.isNotBlank() -> {
                    Card(Modifier.fillMaxWidth().padding(top = Space.lg), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(Space.lg)) {
                            MarkdownLiteText(output)
                            if (!isGenerating) {
                                ResponsiveActions(Modifier.padding(top = Space.md)) {
                                    OutlinedButton(onClick = {
                                        context.getSystemService(android.content.ClipboardManager::class.java)
                                            .setPrimaryClip(android.content.ClipData.newPlainText("Draft", output))
                                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text(" Copy") }
                                    OutlinedButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, output) }
                                        context.startActivity(Intent.createChooser(send, "Share draft"))
                                    }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(" Share") }
                                }
                            }
                        }
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = "Couldn't draft a reply",
                        message = errorText!!,
                        recovery = "Your notes are safe. Check the model or shorten the input, then try again.",
                        actionLabel = "Try again",
                        onAction = { generate() },
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
            }
          }
        }
      }
    }
}
