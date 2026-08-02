package com.vervan.chat.retrieval

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.llm.OneShotLlm

/**
 * Query rewriting/expansion — asks the already-resident generation model (never a different one;
 * see [OneShotLlm]'s doc comment on why reusing the caller's model avoids an extra load/swap) to
 * reformulate the user's question into a couple of alternate phrasings before retrieval runs.
 * Cheap recall win in principle (a query using different words than the source document still
 * finds it), but not actually cheap in practice on a phone-class model — it's a whole extra
 * generation round-trip before the real answer even starts, so this is opt-in via
 * [com.vervan.chat.data.settings.SettingsRepository.queryExpansionEnabled] (default off), not
 * silently applied to every grounded turn.
 */
object QueryExpander {
    private const val MAX_VARIANTS = 3
    private const val MAX_OUTPUT_TOKENS = 80

    /** Returns [query] alone if expansion is disabled, unavailable, or the model's output didn't
     * parse into anything usable — retrieval always has at least the original query to run. */
    suspend fun expand(app: VervanApp, model: ModelInfo, query: String): List<String> {
        val prompt = "Rewrite the following search query as up to $MAX_VARIANTS short alternate " +
            "phrasings that describe the same information need using different words. One per " +
            "line, no numbering, no explanation, no quotes.\n\nQuery: $query"
        val raw = runCatching {
            OneShotLlm.run(app, prompt, model = model, maxOutputTokensOverride = MAX_OUTPUT_TOKENS)
        }.getOrNull() ?: return listOf(query)

        val variants = raw.lineSequence()
            .map { it.trim().trim('-', '*', '"', '\'').trim() }
            .filter { it.isNotBlank() && it.length in 3..200 && !it.equals(query, ignoreCase = true) }
            .distinct()
            .take(MAX_VARIANTS)
            .toList()

        return listOf(query) + variants
    }
}
