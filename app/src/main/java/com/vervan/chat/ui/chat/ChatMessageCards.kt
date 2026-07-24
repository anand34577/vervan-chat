package com.vervan.chat.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.vervanWarning
import org.json.JSONArray

// Leaf message sub-card composables extracted out of ChatScreen.kt (which stays the screen
// scaffold + MessageBubble). These render persisted per-message JSON — retrieved sources,
// memory activity, tool results/confirmations, clarification requests — and are called from
// MessageBubble, so they're `internal` rather than `private`.

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ClarificationCard(
    request: com.vervan.chat.llm.ClarificationParser.Request,
    enabled: Boolean,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("One detail before I continue", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
            Text(request.question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (request.options.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    request.options.forEach { option ->
                        AssistChip(onClick = { onReply(option) }, enabled = enabled, label = { Text(option) })
                    }
                }
            }
            Text(
                if (enabled) "Choose an answer or type your own below." else "Answered in the conversation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

internal fun assistantSpokenText(content: String): String {
    val answer = com.vervan.chat.llm.ThinkingParser.parse(content).answer
    val parsed = com.vervan.chat.llm.ClarificationParser.parse(answer)
    return listOfNotNull(
        parsed.answer.takeIf { it.isNotBlank() },
        parsed.request?.question,
        parsed.request?.options?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Options: ")
    ).joinToString("\n")
}

@Composable
internal fun rememberBatchedStreamingText(text: String, isStreaming: Boolean): String {
    val latestText by rememberUpdatedState(text)
    var displayedText by remember { mutableStateOf(text) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            displayedText = latestText
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(50)
            if (displayedText != latestText) displayedText = latestText
        }
    }

    return if (isStreaming) displayedText else text
}

/** Standard mode translates the raw retrieval score into a plain-language match
 * strength instead of a bare number; Expert mode shows the exact score (see call site). */
private fun matchStrength(score: Double): String = when {
    score >= 0.75 -> "Strong"
    score >= 0.5 -> "Moderate"
    else -> "Weak"
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SourceCards(
    sourcesJson: String,
    onOpenPassage: (String) -> Unit = {},
    // Small-model recovery (P1): shown only in the "grounding was attempted, found nothing"
    // empty state below — a plain missing-KB-selection case has nothing to recover from here.
    onRetryWithQuality: () -> Unit = {},
    betterModelName: String? = null
) {
    val array = remember(sourcesJson) { runCatching { JSONArray(sourcesJson) }.getOrNull() } ?: return
    var selected by remember(sourcesJson) { mutableStateOf<org.json.JSONObject?>(null) }
    // Mark-irrelevant is a client-side hide, not persisted or fed back into retrieval —
    // a real "don't retrieve this chunk again" would need a per-chat exclusion set
    // threaded through RetrievalEngine; this covers the common "get this off my screen" need.
    val hiddenIndices = remember(sourcesJson) { mutableStateListOf<Int>() }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as VervanApp
    val expertMode by app.container.settingsRepository.expertMode.collectAsState(initial = false)
    if (array.length() == 0) {
        Column(Modifier.padding(top = 8.dp)) {
            Text(
                "Not grounded — no matching sources found in the selected knowledge bases",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = onRetryWithQuality) { Text("Try Quality mode", style = MaterialTheme.typography.labelSmall) }
            }
            betterModelName?.let {
                Text(
                    "$it is also installed and may do better with this question — switch it from Mode & model.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
        return
    }
    com.vervan.chat.ui.common.AssistantSubCard(
        kind = com.vervan.chat.ui.common.SubCardKind.Sources,
        title = "Sources (${array.length()})",
        collapsible = false,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until array.length()) {
                if (i in hiddenIndices) continue
                val obj = array.getJSONObject(i)
                AssistChip(
                    onClick = { selected = obj },
                    label = {
                        Text(
                            "[${i + 1}] ${obj.optString("documentName")}${obj.optString("sectionPath").let { if (it.isNotBlank()) " — $it" else "" }}",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
    selected?.let { source ->
        val index = (0 until array.length()).first { array.getJSONObject(it) === source }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(source.optString("documentName")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    source.optString("sectionPath").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(source.optString("excerpt"), modifier = Modifier.padding(top = 8.dp))
                    Text(
                        if (expertMode) {
                            "Retrieval score ${String.format("%.2f", source.optDouble("score"))} · rank ${index + 1} · ranking signal, not confidence"
                        } else {
                            "${matchStrength(source.optDouble("score"))} match"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { clipboard.setText(source.optString("excerpt"), scope) }) {
                            Text("Copy excerpt", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            val citation = "[${index + 1}] ${source.optString("documentName")}" +
                                source.optString("sectionPath").let { if (it.isNotBlank()) " — $it" else "" }
                            clipboard.setText(citation, scope)
                        }) { Text("Copy citation", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { hiddenIndices.add(index); selected = null }) {
                            Text("Mark irrelevant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                val chunkId = source.optString("chunkId")
                if (chunkId.isNotBlank()) {
                    TextButton(onClick = { selected = null; onOpenPassage(chunkId) }) { Text("Open in context") }
                } else {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            },
            dismissButton = {
                if (source.optString("chunkId").isNotBlank()) {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
internal fun MemoryActivityCard(memoryActivityJson: String) {
    val obj = remember(memoryActivityJson) { runCatching { org.json.JSONObject(memoryActivityJson) }.getOrNull() } ?: return
    val recalled = remember(memoryActivityJson) {
        obj.optJSONArray("recalled")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        }.orEmpty()
    }
    val saved = remember(memoryActivityJson) {
        obj.optJSONArray("saved")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        }.orEmpty()
    }
    if (recalled.isEmpty() && saved.isEmpty()) return
    val title = when {
        saved.isNotEmpty() && recalled.isNotEmpty() -> "Memory · ${saved.size} saved, ${recalled.size} recalled"
        saved.isNotEmpty() -> if (saved.size == 1) "Saved to memory" else "Saved ${saved.size} memories"
        recalled.size == 1 -> "Recalled 1 memory"
        else -> "Recalled ${recalled.size} memories"
    }
    com.vervan.chat.ui.common.AssistantSubCard(
        kind = com.vervan.chat.ui.common.SubCardKind.Memory,
        title = title,
        modifier = Modifier.padding(top = Space.sm),
        initiallyExpanded = saved.isNotEmpty()
    ) {
        if (saved.isNotEmpty()) {
            Text("SAVED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            saved.forEach { item ->
                val indexLabel = if (item.optBoolean("indexed")) " · semantic ready" else ""
                Text(
                    "• ${item.optString("text")}$indexLabel",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
        if (recalled.isNotEmpty()) {
            val mode = if (obj.optString("mode") == "semantic") "SEMANTIC RECALL" else "TEXT MATCH FALLBACK"
            Text(
                mode,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = if (saved.isEmpty()) 0.dp else Space.sm)
            )
            recalled.forEach { item ->
                Text(
                    "• ${item.optString("text")}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}

@Composable
internal fun ToolResultCard(toolResultJson: String, toolCallJson: String?) {
    val obj = remember(toolResultJson) { runCatching { org.json.JSONObject(toolResultJson) }.getOrNull() } ?: return
    val callObj = remember(toolCallJson) { toolCallJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } }
    val success = obj.optBoolean("success", true)
    val toolName = obj.optString("tool")
    if (success && toolName in setOf("remember", "search_memories")) {
        com.vervan.chat.ui.common.AssistantSubCard(
            kind = com.vervan.chat.ui.common.SubCardKind.Memory,
            title = if (toolName == "remember") "Saved to memory" else "Searched memory",
            modifier = Modifier.padding(top = Space.sm),
            initiallyExpanded = true
        ) {
            Text(obj.optString("summary"), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (success) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.error
                )
                Text(
                    "${obj.optString("tool")}${if (!success) " failed" else " done"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    color = if (success) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show request and response",
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(obj.optString("summary"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            // Full request (tool + params) and raw response JSON — collapsed by default so the
            // normal chat flow stays uncluttered, but always available per tool call, including
            // when scrolling back through history later (this is the persisted message, not a
            // transient in-session view).
            if (expanded) {
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Request", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    callObj?.optJSONObject("params")?.toString(2) ?: "(no parameters)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                Text("Response", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    obj.toString(2),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
internal fun ToolConfirmationCard(toolCallJson: String?, onConfirm: (Boolean) -> Unit) {
    val obj = remember(toolCallJson) { toolCallJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } } ?: return
    val params = obj.optJSONObject("params")
    // EXTERNAL_ACTION (leaves the app / can't be undone from in-app history, e.g. sending a
    // message) gets a required acknowledgment checkbox before Allow is enabled — REVERSIBLE_WRITE
    // (undoable in-app, e.g. via recycle bin) keeps the single-tap flow (B4).
    val isExternal = obj.optString("risk") == "EXTERNAL_ACTION"
    // web_search is the one EXTERNAL_ACTION tool that doesn't launch an Intent — it's a silent
    // background network call, so "leaves the app" would be a factually wrong warning for it.
    val leavesApp = obj.optString("tool") != "web_search"
    var acknowledged by remember(toolCallJson) { mutableStateOf(!isExternal) }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.vervanWarning.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.vervanWarning)
                Text(
                    (if (isExternal) " Proposed external action · " else " Proposed action · ") + obj.optString("tool"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.vervanWarning
                )
            }
            if (params != null) {
                Text(
                    params.toString(), style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono, modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (isExternal) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        if (leavesApp) "This leaves the app and can't be undone from here"
                        else "This sends your query to Google's servers over the network",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { onConfirm(true) }, enabled = acknowledged) { Text("Allow") }
                TextButton(onClick = { onConfirm(false) }) { Text("Deny") }
            }
        }
    }
}

/** Prompted right after a fresh 👎 reaction (see ChatScreen's onReaction wiring) — a small fixed
 * set of reasons, not free text, so this stays a "spot the pattern" signal
 * (ChatViewModel.setFeedbackReason) rather than something this offline app needs to interpret. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackReasonDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What was wrong with this answer?") },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                listOf("Repetitive", "Factually wrong", "Off-topic", "Too short", "Too long", "Other").forEach { reason ->
                    AssistChip(onClick = { onSelect(reason) }, label = { Text(reason) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Skip") } }
    )
}
