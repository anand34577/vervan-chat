package com.vervan.chat.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class RunPreviewTest {
    @Test
    fun `plain text is shown as it is`() {
        assertEquals("Can't make it, sorry.", runPreview("Can't make it, sorry."))
    }

    @Test
    fun `a fenced JSON quiz is summarised by count and first question`() {
        val raw = "```json\n[{\"type\":\"true_false\",\"question\":\"Is the sky blue?\"},{\"question\":\"Why?\"}]\n```"
        assertEquals("2 items · Is the sky blue?", runPreview(raw))
    }

    @Test
    fun `a JSON object shows its first readable field`() {
        assertEquals("Cafe Blue", runPreview("{\"merchant\":\"Cafe Blue\",\"total\":\"4.50\"}"))
    }

    @Test
    fun `broken JSON falls back to the text`() {
        assertEquals("[not json", runPreview("[not json"))
    }
}
