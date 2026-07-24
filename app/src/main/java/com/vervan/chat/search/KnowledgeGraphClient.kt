package com.vervan.chat.search

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Result of a single Knowledge Graph entity lookup — what the tool layer turns into the
 * model-facing string. [articleExcerpt] is the first paragraph or so of the linked
 * Wikipedia/Wikidata article body, if Google returned one.
 */
data class KnowledgeGraphEntity(
    val name: String,
    val types: List<String>,
    val description: String?,
    val articleExcerpt: String?,
    val url: String?
)

/**
 * Google Knowledge Graph Search API client. Pure Kotlin so it can be unit-tested on the
 * JVM (the JSON parsing is the only non-trivial logic — the HTTP plumbing itself is
 * exercised end-to-end on-device, same as [com.vervan.chat.modeldownload.HttpRangeDownloader]).
 *
 * The KG endpoint returns entities (people/places/things) — not arbitrary web pages — so
 * it's well-suited for "who/what is X" lookups the local model's training data is weak on,
 * and unsuitable for "find me a recent article about Y". The caller's tool description
 * is written so the model knows that boundary.
 *
 * Why KG over Custom Search JSON here: KG needs no CSE ID, has a 100k/day free tier
 * (vs. CSE's 100/day), and returns clean structured facts instead of a list of page
 * snippets the model would have to re-rank.
 */
class KnowledgeGraphClient(
    private val apiKeyProvider: () -> String?,
    private val onNetworkCall: (String) -> Unit = {}
) {
    /** Searches [query] and returns up to [limit] entities (max 10 enforced by the API).
     * @throws IllegalArgumentException if no API key has been configured.
     * @throws java.io.IOException on network/HTTP failure. */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<KnowledgeGraphEntity> {
        require(limit >= 1 && limit <= MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("No Google Knowledge Graph API key configured")

        val url = buildUrl(query, apiKey, limit.coerceAtMost(MAX_LIMIT))
        onNetworkCall("Google Knowledge Graph search: \"$query\"")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw java.io.IOException("Knowledge Graph HTTP $code: ${error?.take(500).orEmpty()}")
            }
            return parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Pulled out so the parsing path is JVM-testable without any network. Public only
     * so the test in app/src/test/.../search/ can call it directly. */
    fun parseResponse(json: String): List<KnowledgeGraphEntity> {
        val root = JSONObject(json)
        val items = root.optJSONArray("itemListElement") ?: return emptyList()
        val parsed = mutableListOf<KnowledgeGraphEntity>()
        for (i in 0 until items.length()) {
            val element = items.optJSONObject(i) ?: continue
            val result = element.optJSONObject("result") ?: continue
            val name = result.optString("name").trim()
            if (name.isEmpty()) continue
            val types = result.optJSONArray("@type")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() && it != "Thing" } }
            } ?: emptyList()
            val description = result.optString("description").takeIf { it.isNotBlank() }
            val detailed = result.optJSONObject("detailedDescription")
            val articleExcerpt = detailed?.optString("articleBody")?.takeIf { it.isNotBlank() }?.let { truncate(it, ARTICLE_EXCERPT_CHARS) }
            val url = detailed?.optString("url")?.takeIf { it.isNotBlank() }
                ?: result.optString("url").takeIf { it.isNotBlank() }
            parsed += KnowledgeGraphEntity(
                name = name,
                types = types,
                description = description,
                articleExcerpt = articleExcerpt,
                url = url
            )
            if (parsed.size >= MAX_LIMIT) break
        }
        return parsed
    }

    /** Formats [entities] as the model-facing tool result — names, types, descriptions,
     * and one excerpt per entity. Pulled out so the formatting is also testable. */
    fun formatResult(query: String, entities: List<KnowledgeGraphEntity>): String {
        if (entities.isEmpty()) return "No entities matched \"$query\"."
        return buildString {
            entities.forEachIndexed { index, entity ->
                if (index > 0) append("\n")
                append("${index + 1}. ${entity.name}")
                if (entity.types.isNotEmpty()) append(" (${entity.types.joinToString("/")})")
                entity.description?.let { append(": $it") }
                entity.articleExcerpt?.let { append(" $it") }
                entity.url?.let { append("\n   Source: $it") }
            }
        }
    }

    private fun buildUrl(query: String, apiKey: String, limit: Int): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        return "$ENDPOINT?query=$encoded&key=$encodedKey&limit=$limit&languages=en&indent=false"
    }

    private fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val cut = text.lastIndexOfAny(charArrayOf(' ', '.', ',', ';', '\n'), maxChars)
        val end = if (cut >= maxChars / 2) cut else maxChars
        return text.substring(0, end).trimEnd() + "…"
    }

    companion object {
        private const val ENDPOINT = "https://kgsearch.googleapis.com/v1/entities:search"
        private const val DEFAULT_LIMIT = 5
        internal const val MAX_LIMIT = 10
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "VervanChat-KnowledgeGraph/1.0"
        // KG articleBody entries can run into thousands of chars; truncating here keeps
        // the tool result inside the local model's small context window without losing
        // the first paragraph, which is what the model actually needs.
        private const val ARTICLE_EXCERPT_CHARS = 280
    }
}
