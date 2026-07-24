package com.vervan.chat.model

/**
 * Guesses whether two imported model files are "the same model, different version" (spec
 *) from their display names alone — there's no explicit family/version field on
 * [com.vervan.chat.data.db.entities.ModelInfo], so this strips common version/date suffixes
 * and compares what's left.
 * name-based heuristic, not a real family id. Good enough to prompt a relink
 * question at import time; false positives/negatives just mean the user sees (or doesn't
 * see) an optional dialog, never data loss. Upgrade to a persisted `family` field if this
 * misfires often in practice.
 */
object ModelFamily {
    private val VERSION_SUFFIX = Regex("(?i)[-_ ]?v?\\d+(\\.\\d+)*$")
    private val DATE_SUFFIX = Regex("(?i)[-_ ]?\\(?\\d{4}(-\\d{2}-\\d{2})?\\)?$")

    fun normalize(displayName: String): String {
        var n = displayName.lowercase().trim()
        repeat(3) {
            n = n.replace(VERSION_SUFFIX, "").replace(DATE_SUFFIX, "").trim()
        }
        return n
    }

    fun sameFamily(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotBlank() && na == nb
    }
}
