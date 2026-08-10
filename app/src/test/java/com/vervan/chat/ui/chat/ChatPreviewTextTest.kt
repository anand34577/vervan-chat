package com.vervan.chat.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPreviewTextTest {

    @Test
    fun `removes reasoning markup from assistant previews`() {
        assertEquals(
            "Visible answer",
            chatPreviewText("<thinks>private reasoning</thinks>Visible answer", isUser = false)
        )
    }

    @Test
    fun `removes tool calls from previews`() {
        assertEquals(
            "Visible answer",
            chatPreviewText(
                "<tool_call>{\"tool\":\"calculator\",\"params\":{}}</tool_call>Visible answer",
                isUser = false
            )
        )
    }

    @Test
    fun `reasoning-only messages produce an empty preview`() {
        assertEquals("", chatPreviewText("<think>still working</think>", isUser = false))
    }
}
