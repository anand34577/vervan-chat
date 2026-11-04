package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.copyText
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
    val localResources = androidx.compose.ui.platform.LocalResources.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var originalMessage by rememberSaveable { mutableStateOf("") }
    var relationship by rememberSaveable { mutableStateOf("") }
    var keyPoints by rememberSaveable { mutableStateOf("") }
    var tone by remember { mutableStateOf("Friendly") }
    var length by remember { mutableStateOf("Medium") }
    var toneMenuOpen by remember { mutableStateOf(false) }
    var lengthMenuOpen by remember { mutableStateOf(false) }
    var output by rememberSaveable { mutableStateOf("") }
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
                val flow = OneShotLlm.stream(
                    app, prompt,
                    runContext = com.vervan.chat.llm.ToolRunContext("tools/email-composer", "Email composer", listOf(originalMessage, keyPoints).filter { it.isNotBlank() }.joinToString("\n\n")),
                )
                if (flow == null) {
                    errorText = "No model is ready. Open Settings → AI models, load one, then draft again."
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
                title = { Text(stringResource(R.string.email_composer_email_message_composer)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
      ScrollablePage(contentPadding = padding, maxContentWidth = 840.dp) {
            FeatureHero(
                icon = Icons.Filled.Mail,
                eyebrow = "On-device assistant",
                // The app bar already names this screen.
                title = null,
                dismissKey = stringResource(R.string.email_composer_email_message_composer),
                body = stringResource(R.string.email_composer_draft_an_email_from_a)
            )
            OutlinedTextField(
                value = originalMessage, onValueChange = { originalMessage = it.take(20_000) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg), minLines = 3,
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.email_composer_original_message_optional)) }
            )
            OutlinedTextField(
                value = keyPoints, onValueChange = { keyPoints = it.take(12_000) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm), minLines = 2,
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.email_composer_key_points_to_include)) }
            )
            OutlinedTextField(
                value = relationship, onValueChange = { relationship = it.take(200) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.email_composer_relationship_to_recipient_optional)) }
            )
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { toneMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.email_composer_tone_arg1, tone), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = toneMenuOpen, onDismissRequest = { toneMenuOpen = false }) {
                        TONES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tone = t; toneMenuOpen = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { lengthMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.email_composer_length_arg1, length), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = lengthMenuOpen, onDismissRequest = { lengthMenuOpen = false }) {
                        LENGTHS.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { length = l; lengthMenuOpen = false }) }
                    }
                }
            }
            if (isGenerating) {
                OutlinedButton(onClick = { genJob?.cancel(); isGenerating = false }, modifier = Modifier.fillMaxWidth().padding(top = Space.md)) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.sm))
                }
            } else {
                Button(
                    onClick = ::generate,
                    enabled = keyPoints.isNotBlank() || originalMessage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                ) { Text(stringResource(R.string.email_composer_draft_reply)) }
            }

            when {
                isGenerating && output.isBlank() -> {
                    Row(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.email_composer_drafting), modifier = Modifier.padding(start = Space.md))
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
                                            .copyText(output, scope, "Draft")
                                        scope.launch { snackbarHostState.showSnackbar(localResources.getString(R.string.chat_copied)) }
                                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text(stringResource(R.string.library_copy), modifier = Modifier.padding(start = Space.sm)) }
                                    OutlinedButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, output) }
                                        context.startActivity(Intent.createChooser(send, "Share draft"))
                                    }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.library_share), modifier = Modifier.padding(start = Space.sm)) }
                                }
                            }
                        }
                    }
                }
                errorText != null -> {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = stringResource(R.string.email_composer_couldn_t_draft_a_reply),
                        message = errorText!!,
                        recovery = stringResource(R.string.email_composer_your_notes_are_safe_check),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = { generate() },
                        modifier = Modifier.padding(top = Space.lg)
                    )
                }
            }
      }
    }
}
