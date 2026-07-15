package com.vervan.chat.ui.common

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** Response segments that need different native renderers. Everything remains on-device. */
sealed class MdSegment {
    data class CodeBlock(val language: String, val code: String) : MdSegment()
    data class Mermaid(val source: String) : MdSegment()
    data class Prose(val text: String) : MdSegment()
}

private val CODE_FENCE_REGEX = Regex("```([\\w+.-]*)[ \\t]*\\r?\\n([\\s\\S]*?)```")

fun splitMarkdownSegments(text: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    var lastEnd = 0
    for (match in CODE_FENCE_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) segments += MdSegment.Prose(text.substring(lastEnd, match.range.first))
        val language = match.groupValues[1]
        val source = match.groupValues[2].trimEnd('\r', '\n')
        segments += if (language.equals("mermaid", ignoreCase = true)) {
            MdSegment.Mermaid(source)
        } else {
            MdSegment.CodeBlock(language, source)
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) segments += MdSegment.Prose(text.substring(lastEnd))
    return segments.ifEmpty { listOf(MdSegment.Prose("")) }
}

data class MermaidEdge(val from: String, val to: String, val label: String? = null)

private val SEQUENCE_EDGE = Regex("^\\s*([\\w .-]+?)\\s*-{1,2}>{1,2}\\s*([\\w .-]+?)\\s*:\\s*(.+?)\\s*$")
private val PIPE_EDGE = Regex("^\\s*(.+?)\\s*--\\s*\\|([^|]+)\\|\\s*-->\\s*(.+?)\\s*$")
private val LABEL_EDGE = Regex("^\\s*(.+?)\\s*--\\s+(.+?)\\s+-->\\s*(.+?)\\s*$")
private val SIMPLE_EDGE = Regex("^\\s*(.+?)\\s*(?:-->|==>|---)\\s*(.+?)\\s*$")

/** A deterministic subset for the flowcharts and sequence diagrams most answers generate. */
fun parseMermaidEdges(source: String): List<MermaidEdge> = source.lineSequence()
    .map(String::trim)
    .filter {
        it.isNotBlank() &&
            !it.startsWith("%%") &&
            !it.startsWith("graph ", ignoreCase = true) &&
            !it.startsWith("flowchart ", ignoreCase = true) &&
            !it.equals("sequenceDiagram", ignoreCase = true)
    }
    .mapNotNull(::parseMermaidEdge)
    .toList()

private fun parseMermaidEdge(line: String): MermaidEdge? {
    SEQUENCE_EDGE.matchEntire(line)?.let { match ->
        return MermaidEdge(
            cleanMermaidNode(match.groupValues[1]),
            cleanMermaidNode(match.groupValues[2]),
            match.groupValues[3].trim()
        )
    }
    PIPE_EDGE.matchEntire(line)?.let { match ->
        return MermaidEdge(
            cleanMermaidNode(match.groupValues[1]),
            cleanMermaidNode(match.groupValues[3]),
            match.groupValues[2].trim()
        )
    }
    LABEL_EDGE.matchEntire(line)?.let { match ->
        return MermaidEdge(
            cleanMermaidNode(match.groupValues[1]),
            cleanMermaidNode(match.groupValues[3]),
            match.groupValues[2].trim()
        )
    }
    return SIMPLE_EDGE.matchEntire(line)?.let { match ->
        MermaidEdge(cleanMermaidNode(match.groupValues[1]), cleanMermaidNode(match.groupValues[2]))
    }
}

private fun cleanMermaidNode(value: String): String {
    val trimmed = value.trim()
    val bracketStart = trimmed.indexOfFirst { it == '[' || it == '(' || it == '{' }
    val withoutId = if (bracketStart > 0) trimmed.substring(bracketStart + 1) else trimmed
    return withoutId.trim().trim('[', ']', '(', ')', '{', '}', '"', '\'', ' ')
}

@Composable
fun MarkdownLiteText(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val segments = remember(text) { splitMarkdownSegments(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        segments.forEach { segment ->
            when (segment) {
                is MdSegment.Prose -> if (segment.text.isNotBlank() || segments.size == 1) {
                    MarkdownProse(segment.text)
                }
                is MdSegment.CodeBlock -> CodeSurface(
                    language = segment.language.ifBlank { "code" },
                    code = segment.code,
                    onCopy = { clipboard.setText(AnnotatedString(segment.code)) }
                )
                is MdSegment.Mermaid -> MermaidDiagram(
                    source = segment.source,
                    onCopy = { clipboard.setText(AnnotatedString(segment.source)) }
                )
            }
        }
    }
}

@Composable
private fun MarkdownProse(markdown: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val textSizePx = with(density) { 16.sp.toPx() }
    val markwon = remember(context, textSizePx) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSizePx) { it.inlinesEnabled(true) })
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setLineSpacing(0f, 1.14f)
                textSize = 16f
            }
        },
        update = { view ->
            view.setTextColor(colors.onSurface.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            markwon.setMarkdown(view, markdown)
        }
    )
}

@Composable
private fun CodeSurface(language: String, code: String, onCopy: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column {
            RendererHeader(label = language, onCopy = onCopy)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = code,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(Space.lg),
                fontFamily = VervanMono,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MermaidDiagram(source: String, onCopy: () -> Unit) {
    val edges = remember(source) { parseMermaidEdges(source) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            RendererHeader(label = "Mermaid · offline", onCopy = onCopy, showDiagramIcon = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (edges.isEmpty()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("Preview unavailable for this diagram type", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "The Mermaid source is preserved and can still be copied.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(source, fontFamily = VervanMono, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    edges.forEach { edge ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            DiagramNode(edge.from, Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                edge.label?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            DiagramNode(edge.to, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagramNode(label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            label,
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun RendererHeader(label: String, onCopy: () -> Unit, showDiagramIcon: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.sm, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (showDiagramIcon) Icon(Icons.Outlined.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = if (showDiagramIcon) null else VervanMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copy") }
    }
}
