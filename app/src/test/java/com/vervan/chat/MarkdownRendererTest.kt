package com.vervan.chat

import androidx.compose.ui.graphics.Color
import com.vervan.chat.ui.common.CodeHighlightColors
import com.vervan.chat.ui.common.MdSegment
import com.vervan.chat.ui.common.highlightCode
import com.vervan.chat.ui.common.normalizeLatexDelimiters
import com.vervan.chat.ui.common.splitMarkdownSegments
import com.vervan.chat.ui.common.repairMarkdownTables
import com.vervan.chat.ui.common.stabilizeStreamingMarkdown
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

    @Test fun leavesPricesAsTextInsteadOfMath() {
        val price = "Was \$12.90/mo and now \$10.99/mo."
        assertEquals(price, normalizeLatexDelimiters(price))
        assertEquals("Total: \$155.62", normalizeLatexDelimiters("Total: \$155.62"))
        assertEquals("Area \$\$\\pi r^2\$\$ costs \$5", normalizeLatexDelimiters("Area \$\\pi r^2\$ costs \$5"))
    }

    @Test fun holdsBackMarkupThatWouldRenderAsAnEmptyLine() {
        assertEquals("- one", stabilizeStreamingMarkdown("- one\n*"))
        assertEquals("Intro", stabilizeStreamingMarkdown("Intro\n\n##"))
        assertEquals("Intro", stabilizeStreamingMarkdown("Intro\n1."))
        assertEquals("| a | b |", stabilizeStreamingMarkdown("| a | b |\n|---|"))
        assertEquals("- **Gr**", stabilizeStreamingMarkdown("- **Gr"))
        assertEquals("Use `npm`", stabilizeStreamingMarkdown("Use `npm"))
        assertEquals("```kotlin\nval a = \"**\"", stabilizeStreamingMarkdown("```kotlin\nval a = \"**\"\n"))
        assertEquals("plain text", stabilizeStreamingMarkdown("plain text\n\n"))
    }

    @Test fun repairsTableSeparatorsThatDoNotMatchTheHeader() {
        val broken = "## Fixes\n| Problem | Cause | Solution |\n| :--- | :--- |\n| A | B | C |"
        assertEquals(
            "## Fixes\n\n| Problem | Cause | Solution |\n| --- | --- | --- |\n| A | B | C |",
            repairMarkdownTables(broken)
        )
        val valid = "| a | b |\n|---|---|\n| 1 | 2 |"
        assertEquals(valid, repairMarkdownTables(valid))
        val fenced = "```\n| a | b | c |\n| - |\n```"
        assertEquals(fenced, repairMarkdownTables(fenced))
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
