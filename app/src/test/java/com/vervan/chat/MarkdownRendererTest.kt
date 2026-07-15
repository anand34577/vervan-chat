package com.vervan.chat

import com.vervan.chat.ui.common.MdSegment
import com.vervan.chat.ui.common.parseMermaidEdges
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

    @Test fun parsesCommonFlowAndSequenceEdges() {
        val flow = parseMermaidEdges("flowchart LR\nA[Draft] --> B{Review}\nB -- approved --> C[Done]")
        assertEquals("Draft", flow[0].from)
        assertEquals("Review", flow[0].to)
        assertEquals("approved", flow[1].label)

        val sequence = parseMermaidEdges("sequenceDiagram\nUser->>App: Open chat")
        assertEquals("User", sequence.single().from)
        assertEquals("App", sequence.single().to)
        assertEquals("Open chat", sequence.single().label)
    }
}
