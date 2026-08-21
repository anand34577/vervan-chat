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

    @Test
    fun `channel reasoning is hidden from previews`() {
        assertEquals(
            "Actual response",
            chatPreviewText(
                "<|channel|>thought\nThinking process<|channel|>final\nActual response",
                isUser = false
            )
        )
    }

    @Test
    fun `unclosed channel reasoning is hidden from previews`() {
        assertEquals(
            "Before",
            chatPreviewText("Before<|channel|>thought\nprivate work", isUser = false)
        )
    }

    @Test
    fun `full text destination keeps answer markdown but removes internal blocks`() {
        assertEquals(
            "**Answer**",
            visibleMessageText("<think>private</think>**Answer**<tool_call>{}</tool_call>", isUser = false)
        )
    }
}
