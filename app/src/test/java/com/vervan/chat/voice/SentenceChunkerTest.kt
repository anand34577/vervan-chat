package com.vervan.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceChunkerTest {

    @Test
    fun `emits a sentence as soon as its terminal punctuation arrives`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("Hello there. How are")
        assertEquals(listOf("Hello there."), emitted)
        chunker.append(" you?")
        assertEquals(listOf("Hello there.", "How are you?"), emitted)
    }

    @Test
    fun `splits multiple sentences arriving in one chunk`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("First one. Second one! Third one?")
        assertEquals(listOf("First one.", "Second one!", "Third one?"), emitted)
    }

    @Test
    fun `splits on the Hindi danda`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("आप कैसे हैं। मैं ठीक हूँ।")
        assertEquals(listOf("आप कैसे हैं।", "मैं ठीक हूँ।"), emitted)
    }

    @Test
    fun `handles code-mixed Hinglish sentences`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("Aaj mausam accha hai। Let's go outside!")
        assertEquals(listOf("Aaj mausam accha hai।", "Let's go outside!"), emitted)
    }

    @Test
    fun `flush emits trailing text with no terminal punctuation`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("This has no ending")
        assertEquals(emptyList<String>(), emitted)
        chunker.flush()
        assertEquals(listOf("This has no ending"), emitted)
    }

    @Test
    fun `flush is a no-op when the buffer is empty`() {
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("Complete sentence.")
        chunker.flush()
        assertEquals(listOf("Complete sentence."), emitted)
    }

    @Test
    fun `each delimiter in a run is its own emitted fragment`() {
        // the chunker splits on the first delimiter it sees, so "..." after "Wait"
        // yields three short emissions rather than one "Wait..." sentence — an extra couple of
        // near-silent TTS calls, not a correctness bug worth a lookahead parser for.
        val emitted = mutableListOf<String>()
        val chunker = SentenceChunker { emitted.add(it) }
        chunker.append("Wait... really?")
        assertEquals(listOf("Wait.", ".", ".", "really?"), emitted)
    }
}
