package com.vervan.chat.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMono
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Response segments that need different native renderers. Everything remains on-device. */
sealed class MdSegment {
    data class CodeBlock(val language: String, val code: String) : MdSegment()
    data class Mermaid(val source: String) : MdSegment()
    data class Prose(val text: String) : MdSegment()
}

private val CODE_FENCE_REGEX = Regex("```([\\w+.-]*)[ \\t]*\\r?\\n([\\s\\S]*?)```")
private val OPEN_FENCE_REGEX = Regex("```([\\w+.-]*)[ \\t]*\\r?\\n")

private fun toCodeSegment(language: String, source: String): MdSegment =
    if (language.equals("mermaid", ignoreCase = true)) MdSegment.Mermaid(source) else MdSegment.CodeBlock(language, source)

fun splitMarkdownSegments(text: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    var lastEnd = 0
    for (match in CODE_FENCE_REGEX.findAll(text)) {
        if (match.range.first > lastEnd) segments += MdSegment.Prose(text.substring(lastEnd, match.range.first))
        segments += toCodeSegment(match.groupValues[1], match.groupValues[2].trimEnd('\r', '\n'))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        val remainder = text.substring(lastEnd)
        // A fence opened but not yet closed — mid-stream, the model hasn't emitted the
        // closing ``` yet. Render what's arrived so far as a live-updating code/mermaid
        // block instead of dumping the raw ``` marker and code as plain prose until it closes.
        val openMatch = OPEN_FENCE_REGEX.find(remainder)
        if (openMatch != null) {
            if (openMatch.range.first > 0) segments += MdSegment.Prose(remainder.substring(0, openMatch.range.first))
            segments += toCodeSegment(openMatch.groupValues[1], remainder.substring(openMatch.range.last + 1))
        } else {
            segments += MdSegment.Prose(remainder)
        }
    }
    return segments.ifEmpty { listOf(MdSegment.Prose("")) }
}

/** Markwon uses `$$...$$` for inline math. Models commonly emit `$...$`, `\(...\)`, and
 * `\[...\]`, so normalize those delimiters while leaving inline-code spans untouched. */
internal fun normalizeLatexDelimiters(markdown: String): String {
    val out = StringBuilder(markdown.length)
    var index = 0
    var codeFenceLength = 0
    while (index < markdown.length) {
        if (markdown[index] == '`') {
            val end = markdown.indexOfFirstFrom(index) { it != '`' }
            val runLength = end - index
            if (codeFenceLength == 0) codeFenceLength = runLength
            else if (runLength == codeFenceLength) codeFenceLength = 0
            out.append(markdown, index, end)
            index = end
            continue
        }
        if (codeFenceLength == 0 && markdown.startsWith("\\[", index)) {
            out.append("$$\n")
            index += 2
            continue
        }
        if (codeFenceLength == 0 && markdown.startsWith("\\]", index)) {
            out.append("\n$$")
            index += 2
            continue
        }
        if (codeFenceLength == 0 && (markdown.startsWith("\\(", index) || markdown.startsWith("\\)", index))) {
            out.append("$$")
            index += 2
            continue
        }
        if (
            codeFenceLength == 0 && markdown[index] == '$' &&
            markdown.getOrNull(index - 1) != '\\' &&
            markdown.getOrNull(index - 1) != '$' && markdown.getOrNull(index + 1) != '$'
        ) {
            out.append("$$")
        } else {
            out.append(markdown[index])
        }
        index++
    }
    return out.toString()
}

private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    for (index in start until length) if (predicate(this[index])) return index
    return length
}

@Composable
fun MarkdownLiteText(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
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
                    onCopy = { clipboard.setText(segment.code, scope) }
                )
                is MdSegment.Mermaid -> MermaidDiagram(
                    source = segment.source,
                    onCopy = { clipboard.setText(segment.source, scope) }
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
    val markwon = remember(context, textSizePx, colors.onSurface) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSizePx) {
                it.inlinesEnabled(true)
                it.theme().textColor(colors.onSurface.toArgb())
            })
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
            markwon.setMarkdown(view, normalizeLatexDelimiters(markdown))
        }
    )
}

@Composable
private fun CodeSurface(language: String, code: String, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val highlightColors = remember(scheme) {
        CodeHighlightColors(
            keyword = scheme.primary,
            string = scheme.tertiary,
            comment = scheme.onSurfaceVariant,
            number = scheme.secondary,
            type = scheme.primary,
            default = scheme.onSurface
        )
    }
    val highlighted = remember(code, language, highlightColors) { highlightCode(code, language, highlightColors) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column {
            RendererHeader(label = language, onCopy = onCopy)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = highlighted,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(Space.lg),
                fontFamily = VervanMono,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MermaidDiagram(source: String, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(with(density) { 160.dp.roundToPx() }) }
    val currentHeightCallback by rememberUpdatedState<(Int) -> Unit>(newValue = { measured ->
        heightPx = measured.coerceAtLeast(with(density) { 80.dp.roundToPx() })
    })
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            RendererHeader(label = "Mermaid · offline", onCopy = onCopy, showDiagramIcon = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() }),
                factory = { context -> OfflineMermaidView(context) { currentHeightCallback(it) } },
                onRelease = WebView::destroy,
                update = { view ->
                    view.render(
                        source = source,
                        background = scheme.surfaceContainerLow.cssHex(),
                        foreground = scheme.onSurface.cssHex(),
                        primary = scheme.primaryContainer.cssHex(),
                        primaryText = scheme.onPrimaryContainer.cssHex(),
                        secondary = scheme.secondaryContainer.cssHex(),
                        outline = scheme.outline.cssHex()
                    )
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class OfflineMermaidView(context: Context, private val onHeightChanged: (Int) -> Unit) : WebView(context) {
    private var ready = false
    private var pendingScript: String? = null
    private var renderedKey: String? = null

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
        }
        addJavascriptInterface(HeightBridge(this, onHeightChanged), "AndroidBridge")
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                // Mermaid is fully vendored in APK assets. Reject every other request instead
                // of relying only on blockNetworkLoads, so diagrams can never fetch fonts,
                // images, scripts, or styles from the internet.
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"

            override fun onPageFinished(view: WebView, url: String) {
                ready = true
                pendingScript?.also {
                    pendingScript = null
                    evaluateJavascript(it, null)
                }
            }
        }
        loadUrl(MERMAID_PAGE)
    }

    fun render(source: String, background: String, foreground: String, primary: String, primaryText: String, secondary: String, outline: String) {
        val key = listOf(source, background, foreground, primary, primaryText, secondary, outline).joinToString("\u0000")
        if (key == renderedKey) return
        renderedKey = key
        val script = "window.renderDiagram(${listOf(source, background, foreground, primary, primaryText, secondary, outline).joinToString(",") { JSONObject.quote(it) }})"
        if (ready) evaluateJavascript(script, null) else pendingScript = script
    }

    private class HeightBridge(private val view: WebView, private val callback: (Int) -> Unit) {
        @JavascriptInterface
        fun setHeight(heightPx: Int) {
            view.post { callback(heightPx) }
        }
    }

    companion object {
        private const val MERMAID_PAGE = "https://appassets.androidplatform.net/assets/mermaid/index.html"
    }
}

private fun androidx.compose.ui.graphics.Color.cssHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())

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
