package com.vervan.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelAudioTranscriptSanitizerTest {
    @Test
    fun `keeps a labelled transcript`() {
        assertEquals("What can you do here?", ModelAudioTranscriptSanitizer.clean("Transcript: What can you do here?", 3_000))
    }

    @Test
    fun `rejects assistant answer`() {
        assertNull(ModelAudioTranscriptSanitizer.clean("I can help with questions and explanations.", 2_000))
    }

    @Test
    fun `rejects runaway response for a short recording`() {
        assertNull(ModelAudioTranscriptSanitizer.clean("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty", 1_000))
    }

    @Test
    fun `removes echoed transcription instruction`() {
        assertEquals(
            "What can you do?",
            ModelAudioTranscriptSanitizer.clean(
                "What can you do? Apart from the other things listen to the attached audio and transcribe the speaker verbatim.",
                4_000
            )
        )
    }

    @Test
    fun `removes the compact echoed instruction`() {
        assertEquals(
            "What can you do?",
            ModelAudioTranscriptSanitizer.clean(
                "What can you do? Transcribe audio only. Return the spoken words and nothing else.",
                4_000
            )
        )
    }

    @Test
    fun `removes the language preservation instruction when echoed`() {
        assertEquals(
            "Tell me a story in hindi.",
            ModelAudioTranscriptSanitizer.clean(
                "Tell me a story in hindi. Transcribe audio only. Preserve the spoken language; do not translate.",
                4_000
            )
        )
    }
}
