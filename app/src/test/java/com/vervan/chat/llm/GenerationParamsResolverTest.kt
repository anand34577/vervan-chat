package com.vervan.chat.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StoppingAtTest {
    private fun collect(chunks: List<String>, stops: List<String>): String = runBlocking {
        flowOf(*chunks.toTypedArray()).stoppingAt(stops).toList().joinToString("")
    }

    @Test
    fun noStopSequencesPassesEverythingThrough() {
        assertEquals("hello world", collect(listOf("hello ", "world"), emptyList()))
    }

    @Test
    fun noMatchEmitsEverything() {
        assertEquals("hello world", collect(listOf("hello ", "world"), listOf("STOP")))
    }

    @Test
    fun matchWithinASingleChunkTruncates() {
        assertEquals("The answer is 42", collect(listOf("The answer is 42<end>"), listOf("<end>")))
    }

    @Test
    fun matchSplitAcrossTwoChunksStillTruncates() {
        assertEquals("The answer is 42", collect(listOf("The answer is 42<e", "nd>ignored"), listOf("<end>")))
    }

    @Test
    fun earliestOccurringStopSequenceWinsRegardlessOfListOrder() {
        assertEquals("keep ", collect(listOf("keep STOP1 rest STOP2"), listOf("STOP2", "STOP1")))
    }
}
