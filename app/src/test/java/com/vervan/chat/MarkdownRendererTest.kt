package com.vervan.chat

import androidx.compose.ui.graphics.Color
import com.vervan.chat.ui.common.CodeHighlightColors
import com.vervan.chat.ui.common.MdSegment
import com.vervan.chat.ui.common.highlightCode
import com.vervan.chat.ui.common.normalizeLatexDelimiters
import com.vervan.chat.ui.common.splitMarkdownSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test fun separatesCodeAndMermaidFences() {
        val segments = splitMarkdownSegments("Intro\n```kotlin\nval ok = true\n```\n```mermaid\ngraph TD\nA-->B\n```")
        assertTrue(segments.any { it is MdSegment.CodeBlock && it.language == "kotlin" })
        assertTrue(segments.any { it is MdSegment.Mermaid })
    }

    @Test fun keepsOpenFenceRenderableWhileStreaming() {
        val segments = splitMarkdownSegments("Before\n```mermaid\nflowchart LR\nA --> B")
        assertTrue(segments.last() is MdSegment.Mermaid)
        assertEquals("flowchart LR\nA --> B", (segments.last() as MdSegment.Mermaid).source)
    }

    @Test fun normalizesCommonLatexWithoutChangingInlineCode() {
        assertEquals("Inline \$\$x^2\$\$ and `price \$5`", normalizeLatexDelimiters("Inline \$x^2\$ and `price \$5`"))
        assertEquals("\$\$E=mc^2\$\$", normalizeLatexDelimiters("\\(E=mc^2\\)"))
        assertEquals("\$\$\na+b\n\$\$", normalizeLatexDelimiters("\\[a+b\\]"))
    }

    @Test fun highlightsEveryOptionalGrammarWithoutMissingGroupCrashes() {
        val colors = CodeHighlightColors(Color.Red, Color.Green, Color.Gray, Color.Blue, Color.Magenta, Color.Black)
        mapOf(
            "kotlin" to "fun main() { // hi\n val n = 1 }",
            "python" to "# hi\ndef main(): return 1",
            "yaml" to "# hi\nkey: value",
            "json" to "{\"ok\": true}",
            "css" to "/* hi */ .x { color: inherit; }"
        ).forEach { (language, code) ->
            assertEquals(code, highlightCode(code, language, colors).text)
        }
    }
}
