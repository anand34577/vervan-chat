package com.vervan.chat.validation

/**
 * Runtime input guardrails shared by UI, import, API, and persistence boundaries.
 *
 * These are deliberately conservative product limits, not database limits. A caller must reject
 * or clearly report input that exceeds them; silently truncating persisted or submitted content
 * makes it impossible for the user to know what was lost.
 */
object InputLimits {
    const val SEARCH_QUERY_CHARS = 200
    const val CHAT_TEXT_CHARS = 12_000
    const val GENERAL_TOOL_INPUT_CHARS = 50_000
    const val OCR_TEXT_CHARS = 100_000
    const val TRANSLATION_TEXT_CHARS = 100_000
    const val DOCUMENT_COMPARISON_SIDE_CHARS = 100_000
    const val DOCUMENT_COMPARISON_TOTAL_CHARS = 200_000
    const val TRANSCRIPT_CHARS = 250_000
    const val TTS_TEXT_CHARS = 8_000
    const val PRONUNCIATION_TEXT_CHARS = 500
    const val TTS_MAX_SENTENCES = 500
    const val TURN_MAX_TURNS = 50
    const val TURN_TRANSCRIPT_CHARS = 100_000
    const val BACKUP_PASSWORD_CHARS = 128
    const val API_MAX_STOP_SEQUENCES = 8
    const val API_MAX_STOP_SEQUENCE_CHARS = 256
    const val API_MAX_TOOLS = 32
    const val API_MAX_TOOL_IDS = 64
    const val API_MAX_TOOL_PARAMETER_CHARS = 8_000
    const val API_MAX_LIST_ITEMS = 1_000
    const val API_MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    const val STRUCTURED_FIELD_COUNT = 20
    const val STRUCTURED_FIELD_CHARS = 64

    const val MAX_IMAGE_BATCH = 8
    const val MAX_DOCUMENT_SCAN_PAGES = 100
    const val MAX_IMAGE_BATCH_BYTES = 256L * 1024 * 1024
    const val MAX_NORMALIZED_IMAGE_BYTES = 10L * 1024 * 1024
    const val MAX_AUDIO_DURATION_MS = 30 * 60 * 1000L
    const val MAX_DECODED_AUDIO_BYTES = 256L * 1024 * 1024
    const val MAX_CHAT_TEMPLATE_CHARS = 128_000
    const val MAX_ADAPTER_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_TTS_ARCHIVE_BYTES = 1L * 1024 * 1024 * 1024
    const val MAX_TTS_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_TTS_ARCHIVE_ENTRIES = 4_096

    fun requireText(value: String, field: String, maxChars: Int, allowBlank: Boolean = true): String {
        val normalized = value.trim()
        require(allowBlank || normalized.isNotBlank()) { "$field is required" }
        require(normalized.length <= maxChars) {
            "$field is too long (maximum $maxChars characters)"
        }
        return normalized
    }

    fun requireFinite(value: Double, field: String, range: ClosedFloatingPointRange<Double>): Double {
        require(value.isFinite()) { "$field must be a finite number" }
        require(value in range) { "$field must be between ${range.start} and ${range.endInclusive}" }
        return value
    }
}
