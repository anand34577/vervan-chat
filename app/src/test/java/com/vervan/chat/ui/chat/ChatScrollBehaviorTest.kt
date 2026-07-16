package com.vervan.chat.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollBehaviorTest {
    @Test
    fun nearBottomRequiresTheEndToBeWithinTolerance() {
        assertTrue(isNearConversationBottom(0, -1, 0, 1_000, 48))
        assertTrue(isNearConversationBottom(1, 0, 700, 1_000, 48))
        assertTrue(isNearConversationBottom(5, 4, 1_048, 1_000, 48))
        assertFalse(isNearConversationBottom(5, 4, 1_049, 1_000, 48))
        assertFalse(isNearConversationBottom(5, 3, 900, 1_000, 48))
        assertFalse(isNearConversationBottom(5, 4, 2_000, 1_000, 48))
    }
}
