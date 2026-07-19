package com.vervan.chat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun `parses a well-formed tool call`() {
        val call = ToolCallParser.parse("""Sure, let me check. <tool_call>{"tool": "search_notes", "params": {"query": "groceries"}}</tool_call>""")
        assertEquals("search_notes", call?.name)
        assertEquals("groceries", call?.params?.getString("query"))
    }

    @Test
    fun `tag matching is case and whitespace tolerant`() {
        val call = ToolCallParser.parse("""< Tool_Call >{"tool": "search_notes", "params": {}}< / TOOL_CALL >""")
        assertEquals("search_notes", call?.name)
    }

    @Test
    fun `strips a markdown code fence wrapped around the json body`() {
        val call = ToolCallParser.parse("<tool_call>```json\n{\"tool\": \"search_notes\", \"params\": {}}\n```</tool_call>")
        assertEquals("search_notes", call?.name)
    }

    @Test
    fun `missing tool key is reported as malformed, not silently dropped`() {
        val result = ToolCallParser.parseAll("""<tool_call>{"params": {}}</tool_call>""")
        assertTrue(result.calls.isEmpty())
        assertEquals(1, result.malformed.size)
    }

    @Test
    fun `invalid json is reported as malformed, not silently dropped`() {
        val result = ToolCallParser.parseAll("""<tool_call>{not valid json</tool_call>""")
        assertTrue(result.calls.isEmpty())
        assertEquals(1, result.malformed.size)
    }

    @Test
    fun `no tag at all yields no calls and nothing malformed`() {
        val result = ToolCallParser.parseAll("Just a normal answer, no tools needed.")
        assertTrue(result.calls.isEmpty())
        assertTrue(result.malformed.isEmpty())
        assertNull(ToolCallParser.parse("Just a normal answer, no tools needed."))
    }

    @Test
    fun `parses every well-formed block when the model emits more than one`() {
        val text = """
            <tool_call>{"tool": "search_notes", "params": {"query": "a"}}</tool_call>
            <tool_call>{"tool": "search_notes", "params": {"query": "b"}}</tool_call>
        """.trimIndent()
        val result = ToolCallParser.parseAll(text)
        assertEquals(2, result.calls.size)
        assertEquals("a", result.calls[0].params.getString("query"))
        assertEquals("b", result.calls[1].params.getString("query"))
    }

    @Test
    fun `parse returns only the first well-formed call`() {
        val text = """<tool_call>{"tool": "a", "params": {}}</tool_call><tool_call>{"tool": "b", "params": {}}</tool_call>"""
        assertEquals("a", ToolCallParser.parse(text)?.name)
    }

    @Test
    fun `stripAll removes every matched block, parsed and malformed alike`() {
        val ok = """<tool_call>{"tool": "a", "params": {}}</tool_call>"""
        val bad = """<tool_call>{not valid</tool_call>"""
        val text = "Before. $ok Middle. $bad After."
        val stripped = ToolCallParser.stripAll(text, listOf(ok, bad))
        assertEquals("Before.  Middle.  After.", stripped)
    }

    @Test
    fun `stripForDisplay hides a complete block`() {
        val visible = ToolCallParser.stripForDisplay("""Checking notes now. <tool_call>{"tool": "search_notes", "params": {}}</tool_call>""")
        assertEquals("Checking notes now.", visible)
    }

    @Test
    fun `stripForDisplay hides a still-open tag mid-stream so raw json never flashes`() {
        val visible = ToolCallParser.stripForDisplay("""Checking notes now. <tool_call>{"tool": "search_not""")
        assertEquals("Checking notes now.", visible)
    }

    @Test
    fun `stripForDisplay leaves plain text with no tool_call tag unchanged`() {
        assertEquals("Just a normal answer.", ToolCallParser.stripForDisplay("Just a normal answer."))
    }
}
