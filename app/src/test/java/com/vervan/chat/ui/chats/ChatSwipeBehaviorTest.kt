package com.vervan.chat.ui.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSwipeBehaviorTest {
    @Test
    fun `swipe is capped at the reveal distance in both directions`() {
        assertEquals(104f, clampedChatSwipeOffset(90f, 40f, 104f), 0f)
        assertEquals(-104f, clampedChatSwipeOffset(-90f, -40f, 104f), 0f)
    }

    @Test
    fun `swipe can reverse smoothly after reaching the cap`() {
        assertEquals(74f, clampedChatSwipeOffset(104f, -30f, 104f), 0f)
        assertEquals(-74f, clampedChatSwipeOffset(-104f, 30f, 104f), 0f)
    }
}
