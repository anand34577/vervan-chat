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
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vervan.chat.ui.common.collectAsState
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
import androidx.compose.ui.res.stringResource
import com.vervan.chat.VervanApp
import com.vervan.chat.R
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
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.chat_clarification_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.sm))
            }
            Text(request.question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
            if (request.options.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    request.options.forEach { option ->
                        AssistChip(onClick = { onReply(option) }, enabled = enabled, shape = MaterialTheme.shapes.small, label = { Text(option) })
                    }
                }
            }
            Text(
                if (enabled) stringResource(R.string.chat_clarification_choose) else stringResource(R.string.chat_clarification_answered),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm)
            )
        }
    }
}

/**
 * Text for a message-list/home-screen preview line (one truncated line, no formatting) — strips
 * `<tool_call>`/`<thinking>`/`<clarify>` markup the same way the bubble's own visible text does,
 * so a chat whose last reply was a reasoning block or tool call doesn't show raw tags in the
 * preview (see [com.vervan.chat.ui.chat.MessageBubble]'s `quotableText`).
 */
fun chatPreviewText(content: String, isUser: Boolean): String {
    if (content.isBlank()) return ""
    val stripped = com.vervan.chat.tools.ToolCallParser.stripForDisplay(content)
    // User text is not reasoning, but it still passes through the lightweight cleanup so a
    // malformed/partial model tag copied into a draft cannot become a noisy list preview.
    val answer = if (isUser) stripped else com.vervan.chat.llm.ThinkingParser.parse(stripped).answer
    val clarification = com.vervan.chat.llm.ClarificationParser.parse(answer)
    // Fall back to the request's question, never to the pre-clarification `answer` — that still
    // has the <clarify> tag in it whenever the tag's own JSON failed to parse into a question.
    val text = clarification.answer.ifBlank { clarification.request?.question ?: answer }.trim()
    return stripMarkdownForPreview(text)
}

/**
 * Collapses common markdown syntax down to plain text for a one-line, no-formatting preview
 * (chat list / home screen row) — those render a plain `Text()`, never through Markwon, so a
 * heading or bold marker showed up as literal `## `/`**` instead of being rendered. Also sweeps up
 * any leftover angle-bracket tag [com.vervan.chat.llm.ThinkingParser] didn't recognize — a
 * reasoning-marker spelling it doesn't know, or a message still streaming with a not-yet-closed
 * tag — which is what produced a raw `<thinking>` in the preview even though the full message
 * bubble (which has its own dedicated reasoning card) never showed one. Deliberately lossy: this
 * is a best-effort single line, not the full message renderer, which stays exact.
 */
private fun stripMarkdownForPreview(text: String): String {
    var s = text
    // Fenced code blocks — drop the fence/language marker, keep the code text itself.
    s = s.replace(Regex("```[a-zA-Z0-9_+-]*\\n?"), "")
    // Headings, blockquotes, list bullets/numbers at the start of a line.
    s = s.replace(Regex("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+"), "")
    // Emphasis/inline-code/strikethrough markers — keep their contents.
    s = s.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "$1")
    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    s = s.replace(Regex("__(.+?)__"), "$1")
    s = s.replace(Regex("\\*(.+?)\\*"), "$1")
    s = s.replace(Regex("(?<![A-Za-z0-9])_(.+?)_(?![A-Za-z0-9])"), "$1")
    s = s.replace(Regex("`([^`]+)`"), "$1")
    s = s.replace(Regex("~~(.+?)~~"), "$1")
    // Links/images — show their visible text, not the markup.
    s = s.replace(Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
    // Any remaining tag-shaped construct — a reasoning/tool marker ThinkingParser's own tag list
    // doesn't cover, or one still open mid-stream. A defensive net for a lossy one-liner, not a
    // correctness guarantee (the real message bubble parses these properly).
    s = s.replace(Regex("<\\|[^|>]*\\|?>"), "")
    s = s.replace(Regex("</?[a-zA-Z_][\\w:-]*(?:\\s[^<>]*)?>"), "")
    // Streaming can leave the final tag fragment without a closing angle bracket. It is not
    // meaningful preview content, so hide only that dangling suffix instead of leaking `<thi`.
    s = s.replace(Regex("<\\|[^|>]*$"), "")
    s = s.replace(Regex("</?[a-zA-Z_][\\w:-]*(?:\\s[^<>]*)?$"), "")
    return s.replace(Regex("\\s+"), " ").trim()
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
    // A citation whose chunk carries a pageNumber came from a PDF with a real text layer (see
    // Chunk.pageNumber's doc comment) — for those, jump straight into the PDF page viewer
    // instead of forcing a stop at Source Passage first just to tap its own "view PDF page"
    // button. Source Passage (onOpenPassage) stays available for the neighbors/context view,
    // and remains the only option for a non-PDF source, which has no page to jump to.
    onOpenPdfPage: (documentId: String, page: Int) -> Unit = { _, _ -> },
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
        Column(Modifier.padding(top = Space.sm)) {
            Text(
                "Not grounded — no matching sources found in the selected knowledge bases",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = onRetryWithQuality) { Text(stringResource(R.string.chat_try_quality), style = MaterialTheme.typography.labelSmall) }
            }
            betterModelName?.let {
                Text(
                    "$it may work better for this question. Switch models in Mode & model.",
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
        title = stringResource(R.string.chat_sources_count, array.length()),
        collapsible = false,
        modifier = Modifier.padding(top = Space.sm).fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            for (i in 0 until array.length()) {
                if (i in hiddenIndices) continue
                val obj = array.getJSONObject(i)
                AssistChip(
                    onClick = { selected = obj },
                    shape = MaterialTheme.shapes.small,
                    label = {
                        val page = obj.optInt("pageNumber", -1)
                        Text(
                            "[${i + 1}] ${obj.optString("documentName")}${obj.optString("sectionPath").let { if (it.isNotBlank()) " — $it" else "" }}" +
                                (if (page > 0) " (p. $page)" else ""),
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
                    val sectionLabel = source.optString("sectionPath").takeIf { it.isNotBlank() }
                    val pageLabel = source.optInt("pageNumber", -1).takeIf { it > 0 }?.let { "Page $it" }
                    listOfNotNull(sectionLabel, pageLabel).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(source.optString("excerpt"), modifier = Modifier.padding(top = Space.sm))
                    Text(
                        if (expertMode) {
                            "Retrieval score ${String.format("%.2f", source.optDouble("score"))} · rank ${index + 1} · ranking signal, not confidence"
                        } else {
                            "${matchStrength(source.optDouble("score"))} match"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md)
                    )
                    // FlowRow, not Row — three buttons plus "Mark irrelevant" don't reliably fit
                    // one line at dialog width, and a plain Row squeezed each button into
                    // whatever sliver was left instead of wrapping, breaking "Mark irrelevant"
                    // across several lines of single characters. Same fix as the Sources chips
                    // FlowRow above.
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        TextButton(onClick = { clipboard.setText(source.optString("excerpt"), scope) }) {
                            Text(stringResource(R.string.chat_copy_excerpt), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            val citation = "[${index + 1}] ${source.optString("documentName")}" +
                                source.optString("sectionPath").let { if (it.isNotBlank()) " — $it" else "" }
                            clipboard.setText(citation, scope)
                        }) { Text(stringResource(R.string.chat_copy_citation), style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { hiddenIndices.add(index); selected = null }) {
                            Text(stringResource(R.string.chat_mark_irrelevant), style = MaterialTheme.typography.labelSmall)
                        }
                        val page = source.optInt("pageNumber", -1)
                        val documentId = source.optString("documentId").takeIf { it.isNotBlank() }
                        if (page > 0 && documentId != null) {
                            TextButton(onClick = { selected = null; onOpenPdfPage(documentId, page) }) {
                                Text(stringResource(R.string.chat_open_pdf_page, page), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val chunkId = source.optString("chunkId")
                if (chunkId.isNotBlank()) {
                    TextButton(onClick = { selected = null; onOpenPassage(chunkId) }) { Text(stringResource(R.string.chat_open_context)) }
                } else {
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_close)) }
                }
            },
            dismissButton = {
                if (source.optString("chunkId").isNotBlank()) {
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_close)) }
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
            Text(stringResource(R.string.chat_saved_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
        Modifier.fillMaxWidth().padding(top = Space.sm).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (success) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.error
                )
                // One weighted child, not two: this was `weight(1f, fill = false)` on the label plus
                // a `Spacer(weight(1f))`, and those two split the free space 50/50 — which parked the
                // expand chevron mid-row instead of at the trailing edge. Letting the label consume
                // the remaining width (it already ellipsizes) pushes the chevron to the real end.
                Text(
                    "${obj.optString("tool")}${if (!success) " failed" else " done"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    color = if (success) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.xs).weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show request and response",
                    modifier = Modifier.size(18.dp)
                )
            }
            // Summary, request params, and raw response are all tool *output* — collapsed shows
            // only the name/status row above (user ask: don't leak the response before the card is
            // opened). Expanded reveals everything, still persisted per call so it's available when
            // scrolling back through history later, not just in the live session.
            if (expanded) {
                Text(obj.optString("summary"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.xs))
                HorizontalDivider(Modifier.padding(top = Space.sm, bottom = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.chat_request), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    callObj?.optJSONObject("params")?.toString(2) ?: "(no parameters)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    modifier = Modifier.padding(top = Space.xs, bottom = Space.sm)
                )
                Text(stringResource(R.string.chat_response), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    obj.toString(2),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    modifier = Modifier.padding(top = Space.xs)
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
    var acknowledged by remember(toolCallJson) { mutableStateOf(!isExternal) }
    Card(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.vervanWarning.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(Space.md)) {
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
                    fontFamily = com.vervan.chat.ui.theme.VervanMono, modifier = Modifier.padding(top = Space.sm)
                )
            }
            if (isExternal) {
                Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        "This leaves the app and can't be undone from here",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Space.xs)
                    )
                }
            }
            Row(Modifier.padding(top = Space.sm)) {
                TextButton(onClick = { onConfirm(true) }, enabled = acknowledged) { Text(stringResource(R.string.action_allow)) }
                TextButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.action_deny)) }
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
        title = { Text(stringResource(R.string.chat_feedback_title)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                listOf("Repetitive", "Factually wrong", "Off-topic", "Too short", "Too long", "Other").forEach { reason ->
                    AssistChip(onClick = { onSelect(reason) }, shape = MaterialTheme.shapes.small, label = { Text(reason) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_skip)) } }
    )
}
