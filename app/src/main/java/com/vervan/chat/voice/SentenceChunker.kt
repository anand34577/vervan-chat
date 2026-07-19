package com.vervan.chat.voice

/**
 * Buffers streamed LLM token text and emits complete sentences as soon as they're formed, so
 * TTS can start on sentence 1 while the model is still generating sentence 2 — the thing that
 * makes the realtime voice pipeline feel conversational instead of request/response.
 *
 * Splits on `.`, `!`, `?`, and the Hindi danda `।` (Hindi + English + Hinglish all use these).
 * a plain first-delimiter scan, not decimal-number-aware ("3.14" splits into two
 * "sentences") — an extra short TTS utterance is harmless, so this isn't worth a smarter
 * tokenizer. Pure Kotlin, no Android dependency, so it's unit-testable without a device.
 */
class SentenceChunker(private val onSentence: (String) -> Unit) {
    private val buffer = StringBuilder()

    fun append(textChunk: String) {
        buffer.append(textChunk)
        drain()
    }

    /** Call when the LLM stream ends — emits whatever's left even without terminal
     * punctuation, so the last clause of a reply doesn't get silently dropped. */
    fun flush() {
        val remainder = buffer.toString().trim()
        buffer.clear()
        if (remainder.isNotEmpty()) onSentence(remainder)
    }

    private fun drain() {
        while (true) {
            val idx = buffer.indexOfFirst { it in DELIMITERS }
            if (idx < 0) return
            val sentence = buffer.substring(0, idx + 1).trim()
            buffer.delete(0, idx + 1)
            if (sentence.isNotEmpty()) onSentence(sentence)
        }
    }

    companion object {
        private val DELIMITERS = charArrayOf('.', '!', '?', '।')
    }
}
