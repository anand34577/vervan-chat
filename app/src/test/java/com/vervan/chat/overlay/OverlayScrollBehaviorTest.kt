package com.vervan.chat.overlay

import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayScrollBehaviorTest {
    @Test
    fun nearBottomRequiresTheEndToBeWithinTolerance() {
        assertTrue(isNearOverlayBottom(0, -1, 0, 1_000, 72))
        assertTrue(isNearOverlayBottom(2, 1, 1_072, 1_000, 72))
        assertFalse(isNearOverlayBottom(2, 1, 1_073, 1_000, 72))
        assertFalse(isNearOverlayBottom(2, 0, 900, 1_000, 72))
    }

    @Test
    fun screenPromptKeepsFollowUpContextAndDropsFailedReplies() {
        val prompt = buildScreenConversationPrompt(
            listOf(
                Message(chatId = "chat", role = MessageRole.USER, content = "Explain this screen"),
                Message(chatId = "chat", role = MessageRole.ASSISTANT, content = "It shows a chart"),
                Message(chatId = "chat", role = MessageRole.ASSISTANT, content = "partial failure", state = MessageState.FAILED),
                Message(chatId = "chat", role = MessageRole.USER, content = "What is the highest value?")
            )
        )

        assertTrue(prompt.indexOf("User: Explain this screen") < prompt.indexOf("Assistant: It shows a chart"))
        assertTrue(prompt.indexOf("Assistant: It shows a chart") < prompt.indexOf("User: What is the highest value?"))
        assertFalse(prompt.contains("partial failure"))
        assertTrue(prompt.endsWith("Assistant: "))
    }
}
