package com.vervan.chat.data.repo

import com.vervan.chat.data.db.entities.Persona

/**
 * Turns a [Persona]'s declarative behavior dials into one extra prompt
 * line layered after its free-text systemInstruction — every dial defaults to neutral/blank,
 * so a persona nobody has tuned pays zero extra prompt cost.
 */
object PersonaTraits {
    fun instructionFor(persona: Persona): String {
        val parts = mutableListOf<String>()
        when (persona.tone) {
            "WARM" -> parts += "warm and encouraging"
            "DIRECT" -> parts += "direct and matter-of-fact"
            "PLAYFUL" -> parts += "light and playful"
        }
        when (persona.formality) {
            "CASUAL" -> parts += "casual"
            "FORMAL" -> parts += "formal and professional"
        }
        when (persona.conciseness) {
            "TERSE" -> parts += "terse"
            "ELABORATE" -> parts += "willing to elaborate at length"
        }
        when (persona.responseLength) {
            "SHORT" -> parts += "keep responses short"
            "LONG" -> parts += "give thorough, longer responses"
        }
        val toneLine = if (parts.isNotEmpty()) "Tone: be ${parts.joinToString(", ")}." else ""
        val languageLine = if (persona.language.isNotBlank()) "Reply in ${persona.language} unless asked otherwise." else ""
        return listOf(toneLine, languageLine).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Creativity dial (0..1) maps onto generation temperature (0.2..1.2) — a persona set to
     * "precise" (low creativity) shouldn't ride the same default temperature as one set to
     * "imaginative" (high creativity). */
    fun temperatureFor(persona: Persona?, fallback: Float): Float =
        if (persona == null) fallback else 0.2f + persona.creativity.coerceIn(0f, 1f) * 1.0f
}
