package com.vervan.chat.ui.common

import com.vervan.chat.validation.InputLimits

/**
 * Central length-limit matrix — product guardrails, not database limits. Every persisted
 * or submitted text field should reference a constant here (via [BoundedTextField]) instead of
 * an unbounded `OutlinedTextField`, so limits stay consistent and are defined in exactly one place.
 */
object ValidationLimits {
    const val SEARCH_QUERY = InputLimits.SEARCH_QUERY_CHARS
    const val CHAT_TITLE = 120
    const val CHAT_COMPOSER = InputLimits.CHAT_TEXT_CHARS
    const val FIND_IN_CONVERSATION = 200
    const val TEMPLATE_COMMAND_NAME = 32
    const val MEMORY_TEXT = 1_000
    const val MEMORY_KEY = 64
    const val KNOWLEDGE_BASE_NAME = 80
    const val KNOWLEDGE_BASE_DESCRIPTION = 500
    const val DOCUMENT_DISPLAY_NAME = 180
    const val MODEL_DISPLAY_NAME = 100
    const val MODEL_NOTE = 500
    const val PERSONA_NAME = 60
    const val PERSONA_ROLE = 200
    const val PERSONA_SYSTEM_INSTRUCTION = 16_000
    const val PERSONA_TEST_PROMPT = 8_000
    const val TEMPLATE_TITLE = 80
    const val TEMPLATE_DESCRIPTION = 240
    const val TEMPLATE_BODY = 24_000
    const val WORKFLOW_NAME = 80
    const val WORKFLOW_DESCRIPTION = 500
    const val WORKFLOW_STEP = 12_000
    const val WORKFLOW_STEP_COUNT = 20
    const val WORKFLOW_RUN_INPUT = 50_000
    const val NOTE_TITLE = 120
    const val NOTE_TAG = 32
    const val NOTE_TAG_COUNT = 20
    const val NOTE_CONTENT = 100_000
    const val PROJECT_NAME = 80
    const val PROJECT_DESCRIPTION = 1_000
    const val PROJECT_INSTRUCTIONS = 12_000
    const val FOLDER_NAME = 60
    const val WORKSPACE_NAME = 60
    const val WORKSPACE_DESCRIPTION = 1_000
    const val USER_PREFERRED_NAME = 80
    const val USER_OCCUPATION = 120
    const val USER_EXPERTISE = 100
    const val USER_INTEREST_ITEM = 40
    const val USER_INTEREST_COUNT = 20
    const val USER_AVOID_TOPIC_ITEM = 60
    const val USER_AVOID_TOPIC_COUNT = 20
    const val USER_GOALS = 2_000
    const val WRITING_INPUT = 50_000
    const val WRITING_CUSTOM_INSTRUCTION = 2_000
    const val DEVELOPER_INPUT = 80_000
    const val DEVELOPER_CUSTOM_INSTRUCTION = 2_000
    const val STUDY_SOURCE = 100_000
    const val STUDY_SET_NAME = 100
    const val FLASHCARD_QUESTION = 1_000
    const val FLASHCARD_ANSWER = 4_000
    const val BACKUP_LABEL = 100

    const val GENERAL_TOOL_INPUT = InputLimits.GENERAL_TOOL_INPUT_CHARS
    const val OCR_TEXT = InputLimits.OCR_TEXT_CHARS
    const val TRANSLATION_TEXT = InputLimits.TRANSLATION_TEXT_CHARS
    const val TRANSCRIPT = InputLimits.TRANSCRIPT_CHARS
    const val TTS_TEXT = InputLimits.TTS_TEXT_CHARS
}
