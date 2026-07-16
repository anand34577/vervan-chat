package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
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
import kotlinx.coroutines.launch

private val TONES = listOf("Friendly", "Formal", "Assertive", "Apologetic", "Enthusiastic", "Neutral")
private val LENGTHS = listOf("Short", "Medium", "Long")

/** Structured reply drafting — no email account access needed, works entirely from pasted/typed
 * text (spec: "no email access required initially"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()

    var originalMessage by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var keyPoints by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf("Friendly") }
    var length by remember { mutableStateOf("Medium") }
    var toneMenuOpen by remember { mutableStateOf(false) }
    var lengthMenuOpen by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    fun generate() {
        isGenerating = true
        output = ""
        scope.launch {
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
            output = OneShotLlm.run(app, prompt)?.trim().orEmpty()
            isGenerating = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email & message composer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = originalMessage, onValueChange = { originalMessage = it },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
                label = { Text("Original message (optional)") }
            )
            OutlinedTextField(
                value = keyPoints, onValueChange = { keyPoints = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2,
                label = { Text("Key points to include") }
            )
            OutlinedTextField(
                value = relationship, onValueChange = { relationship = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Relationship to recipient (optional)") }
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { toneMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Tone: $tone") }
                    DropdownMenu(expanded = toneMenuOpen, onDismissRequest = { toneMenuOpen = false }) {
                        TONES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tone = t; toneMenuOpen = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { lengthMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Length: $length") }
                    DropdownMenu(expanded = lengthMenuOpen, onDismissRequest = { lengthMenuOpen = false }) {
                        LENGTHS.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { length = l; lengthMenuOpen = false }) }
                    }
                }
            }
            Button(
                onClick = ::generate,
                enabled = (keyPoints.isNotBlank() || originalMessage.isNotBlank()) && !isGenerating,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Draft reply") }

            if (isGenerating) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text("Drafting…")
                }
            } else if (output.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(output, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Draft", output))
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Copy") }
                    }
                }
            }
        }
    }
}
