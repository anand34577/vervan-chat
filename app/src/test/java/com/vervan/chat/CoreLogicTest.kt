package com.vervan.chat

import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.llm.ThinkingParser
import com.vervan.chat.model.Chunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test fun branchPathAndCycleSafety() {
        val root = Message(id = "a", chatId = "c", role = MessageRole.USER, content = "root", createdAt = 1)
        val left = Message(id = "b", chatId = "c", parentId = "a", role = MessageRole.ASSISTANT, content = "left", createdAt = 2)
        val right = Message(id = "d", chatId = "c", parentId = "a", role = MessageRole.ASSISTANT, content = "right", createdAt = 3)
        assertEquals(listOf("a", "d"), BranchUtil.pathTo(listOf(root, left, right), "d").map { it.id })
        assertEquals("d", BranchUtil.deepestTip(listOf(root, left, right), "a"))
        val cycle = root.copy(parentId = "b")
        assertEquals(2, BranchUtil.pathTo(listOf(cycle, left), "b").size)
    }

    @Test fun thinkingParserPreservesAnswer() {
        val parsed = ThinkingParser.parse("<thinking>check facts</thinking>Final answer")
        assertEquals("check facts", parsed.reasoning)
        assertEquals("Final answer", parsed.answer)
        assertEquals("plain", ThinkingParser.parse("plain").answer)
    }

    @Test fun chunkerRejectsEmptyDocuments() {
        assertTrue(Chunker.chunk("   \n").isEmpty())
        assertEquals("Section", Chunker.chunk("# Section\n\nUseful text").single().sectionPath)
    }
}
