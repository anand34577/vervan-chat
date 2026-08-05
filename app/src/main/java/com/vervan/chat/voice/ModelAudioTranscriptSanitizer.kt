package com.vervan.chat.voice

/** Keeps audio-capable chat models from leaking an assistant answer into a transcript field. */
object ModelAudioTranscriptSanitizer {
    private val assistantLead = Regex(
        "^(?:sure|of course|certainly|i can help|i(?:'|’)m happy to help|here(?:'|’)s|here is|" +
            "the answer is|as an ai|i(?:'|’)m sorry|i cannot|i can’t|you can)\\b[,:.!]?",
        RegexOption.IGNORE_CASE
    )
    private val transcriptLabel = Regex(
        "^(?:transcript|transcription|text)\\s*:\\s*",
        RegexOption.IGNORE_CASE
    )
    private val instructionEcho = Regex(
        "(?is)(?:\\s+(?:apart from the other things\\s+)?listen to the attached audio and " +
            "transcribe the speaker verbatim|\\s+transcribe audio only\\. return the spoken " +
            "words and nothing else\\.|\\s+transcribe audio only\\. preserve the spoken " +
            "language; do not translate\\.).*$"
    )

    /** Returns null for output that looks like an answer rather than speech recognition. */
    fun clean(raw: String, durationMs: Long): String? {
        val cleaned = raw
            .replace(Regex("```(?:text|transcript)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()
            .replace(transcriptLabel, "")
            .replace(instructionEcho, "")
            .trim()
        if (cleaned.isBlank() || assistantLead.containsMatchIn(cleaned)) return null

        // A short recording cannot plausibly produce a long explanatory answer. Legitimate
        // speech stays below this conservative limit; runaway replies fall through to Whisper.
        val wordCount = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
        val seconds = (durationMs / 1_000.0).coerceAtLeast(1.0)
        if (wordCount > (seconds * 5.0 + 12.0).toInt()) return null
        return cleaned.takeIf { it.isNotBlank() }
    }
}
