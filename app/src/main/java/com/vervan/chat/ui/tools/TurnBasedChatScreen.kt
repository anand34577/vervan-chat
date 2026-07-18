package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.OneShotLlm
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Generic multi-turn "AI stays in character, one message at a time" screen — the transcript is
 * re-sent as plain text on every turn (same non-conversational [OneShotLlm] used elsewhere; this
 * app has no persistent multi-turn native session outside the main Chat feature). Backs Socratic
 * Tutor, Interview Practice, and Language Practice Mode — the only difference between them is
 * the system instruction and the setup question.
 *
 * The reply streams into a growing bubble, the transcript follows the newest turn, and a load
 * failure surfaces in-line instead of crashing the coroutine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnBasedChatScreen(title: String, systemInstruction: String, setupHint: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var setup by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf(listOf<ChatTurn>()) }
    var draft by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var genJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    fun transcriptText() = turns.joinToString("\n") { (if (it.fromUser) "User: " else "Assistant: ") + it.text }

    fun send(userText: String?) {
        isThinking = true
        if (userText != null) turns = turns + ChatTurn(true, userText)
        genJob = scope.launch {
            try {
                val prompt = buildString {
                    appendLine(systemInstruction)
                    appendLine("Context: $setup")
                    if (turns.isNotEmpty()) {
                        appendLine()
                        appendLine("Conversation so far:")
                        appendLine(transcriptText())
                    }
                    appendLine()
                    append("Respond with ONLY your next message, staying in character and addressing one point at a time. Keep it to 2-4 sentences.")
                }
                val flow = OneShotLlm.stream(app, prompt)
                if (flow == null) {
                    turns = turns + ChatTurn(false, "⚠️ No generation model is active. Load one from Models, then continue.")
                } else {
                    // Grow one assistant bubble as tokens arrive; throttle the state write so the
                    // markdown isn't re-parsed on every token during a fast stream (ponytail).
                    turns = turns + ChatTurn(false, "")
                    val idx = turns.lastIndex
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    flow.collect {
                        sb.append(it)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60) {
                            turns = turns.toMutableList().also { l -> l[idx] = ChatTurn(false, sb.toString()) }
                            lastEmit = now
                        }
                    }
                    val finalText = sb.toString().ifBlank { "(no response — try again)" }
                    turns = turns.toMutableList().also { l -> l[idx] = ChatTurn(false, finalText) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                turns = turns + ChatTurn(false, "⚠️ ${t.toUserMessage()}")
            } finally {
                isThinking = false
            }
        }
    }

    // Keep the newest turn in view as the transcript grows and as the reply streams in.
    LaunchedEffect(turns.size, isThinking) {
        if (turns.isNotEmpty()) runCatching { listState.animateScrollToItem(turns.lastIndex) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (!started) {
            PageContainer(Modifier.padding(padding)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(vertical = Space.lg)
                    ) {
                        FeatureHero(
                            icon = Icons.Filled.Forum,
                            eyebrow = "Guided practice",
                            title = title,
                            body = "Set a focus and work through it one response at a time."
                        )
                        VervanSectionHeader("1 · Set the focus")
                        OutlinedTextField(
                            value = setup,
                            onValueChange = { setup = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = MaterialTheme.shapes.large,
                            label = { Text("Session context") },
                            placeholder = { Text(setupHint) }
                        )
                        VervanSectionHeader("2 · Begin the session")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                        "The assistant starts with one question. Your practice stays in this transcript.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Space.lg)
                            )
                        }
                        Button(
                            onClick = { started = true; send(null) },
                            enabled = setup.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                        ) { Text("Start guided session") }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
              Column(Modifier.fillMaxSize().widthIn(max = 840.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)
                ) {
                    Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
                        Text("Session focus", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(setup, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    itemsIndexed(turns) { index, turn ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start) {
                            Card(
                                modifier = Modifier.widthIn(max = 560.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (turn.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Box(Modifier.padding(Space.md)) {
                                    // Assistant turns may contain lists/emphasis; user turns are their own literal text.
                                    if (turn.fromUser) Text(turn.text) else MarkdownLiteText(turn.text)
                                }
                            }
                        }
                    }
                    if (isThinking && turns.lastOrNull()?.text.isNullOrBlank()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                                Text("Thinking…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Card(
                    Modifier.fillMaxWidth().padding(Space.md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            modifier = Modifier.weight(1f), placeholder = { Text("Your response") }, enabled = !isThinking,
                            shape = MaterialTheme.shapes.large, maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent, disabledBorderColor = Color.Transparent
                            )
                        )
                        // While a reply streams, this same slot becomes a Stop control.
                        Surface(
                            modifier = Modifier.padding(start = Space.sm).size(48.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (isThinking || draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            if (isThinking) {
                                IconButton(onClick = { genJob?.cancel(); isThinking = false }) {
                                    Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            } else {
                                IconButton(
                                    enabled = draft.isNotBlank(),
                                    onClick = { val text = draft; draft = ""; send(text) }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send, "Send",
                                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
              }
            }
        }
    }
}
