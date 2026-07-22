package com.vervan.chat.data.repo

/**
 * Rule-based "ask before saving" detector (suggestion-inbox capture mode). Looks
 * for explicit, unambiguous statements in a user message — never inferred from tone or
 * repeated behavior, since that needs conflict scoring this project doesn't have (see the
 * implementation note on [com.vervan.chat.data.db.entities.Memory]). A hit only ever produces a
 * [com.vervan.chat.data.db.entities.MemorySuggestion] for the user to accept or reject —
 * nothing here writes to the real memory table directly, so ("no memory is added
 * silently") still holds.
 */
object MemorySuggestionDetector {
    data class Candidate(val text: String, val key: String?)

    private val PATTERNS = listOf(
        Regex("(?i)\\bremember (?:that )?(.+)") to null,
        Regex("(?i)\\bmy name is ([\\w .'-]+)") to "user_name",
        Regex("(?i)\\bcall me ([\\w .'-]+)") to "user_name",
        Regex("(?i)\\bI(?:'m| am) (?:a|an) ([\\w .'-]+ (?:developer|engineer|designer|student|writer|teacher|manager|founder))") to "user_role",
        Regex("(?i)\\bI (?:always |usually |generally )?prefer ([\\w .,'\"-]+)") to "preference",
        Regex("(?i)\\bI (?:always )?use ([\\w .,'\"-]+) (?:for coding|as my language|as my editor)") to "tool_preference",
        Regex("(?i)\\bplease (?:always )?(don't|never) ([\\w .,'\"-]+)") to "avoid_preference",
        Regex("(?i)\\bI work (?:at|for) ([\\w .,'\"&-]+)") to "employer"
    )

    /** Returns at most one candidate per matching pattern, deduplicated by key. */
    fun detect(userText: String): List<Candidate> {
        val results = LinkedHashMap<String, Candidate>()
        for ((pattern, key) in PATTERNS) {
            val match = pattern.find(userText) ?: continue
            val captured = match.groupValues.drop(1).joinToString(" ").trim().trimEnd('.', '!', ',')
            if (captured.isBlank() || captured.length > 200) continue
            val text = if (key == null) captured.replaceFirstChar { it.uppercase() } else "${humanize(key)}: $captured"
            val dedupeKey = key ?: text.lowercase()
            results[dedupeKey] = Candidate(text, key)
        }
        return results.values.toList()
    }

    private fun humanize(key: String) = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
