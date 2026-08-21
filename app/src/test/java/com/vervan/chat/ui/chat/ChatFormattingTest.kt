package com.vervan.chat.ui.chat

import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFormattingTest {

    @Test
    fun `past thinking is excluded by default and included when enabled`() {
        val message = assistant("<think>private reasoning</think>Visible answer")

        assertEquals("Visible answer", ChatFormatting.contextMessageContent(message, false))
        assertEquals(message.content, ChatFormatting.contextMessageContent(message, true))
    }

    @Test
    fun `tool call metadata remains in context when thinking is excluded`() {
        val message = assistant(
            "<think>choose a tool</think>Done",
            toolCallJson = "{\"tool\":\"calculator\",\"params\":{}}"
        )

        val context = ChatFormatting.contextMessageContent(message, false)
        assertTrue(context.contains("Done"))
        assertTrue(context.contains("<tool_call>"))
        assertTrue(context.contains("calculator"))
        assertFalse(context.contains("choose a tool"))
    }

    @Test
    fun `history trimming uses the selected thinking representation`() {
        val history = listOf(
            assistant("<think>${"private ".repeat(80)}</think>short answer"),
            Message(chatId = "chat", role = MessageRole.USER, content = "Keep this recent turn")
        )

        val withoutThinking = ChatFormatting.trimHistoryToBudget(history, contextLimitTokens = 100, includePastThinking = false)
        val withThinking = ChatFormatting.trimHistoryToBudget(history, contextLimitTokens = 100, includePastThinking = true)

        assertEquals(2, withoutThinking.size)
        assertEquals(1, withThinking.size)
        assertEquals("Keep this recent turn", withThinking.single().content)
    }

    @Test
    fun `cancelled and failed assistant output is never context`() {
        val message = assistant(
            "<think>unfinished</think>partial",
            state = MessageState.FAILED
        )

        assertEquals("", ChatFormatting.contextMessageContent(message, false))
        assertEquals("", ChatFormatting.contextMessageContent(message, true))
    }

    private fun assistant(
        content: String,
        toolCallJson: String? = null,
        state: MessageState = MessageState.COMPLETE
    ) = Message(
        chatId = "chat",
        role = MessageRole.ASSISTANT,
        content = content,
        state = state,
        toolCallJson = toolCallJson
    )
}
