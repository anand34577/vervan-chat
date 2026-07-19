package com.vervan.chat.llm

/**
 * Script-aware token estimate. The old flat chars/4 heuristic assumed English: real tokenizers
 * emit ~1 token per 1–1.5 characters for non-Latin scripts (Devanagari, CJK, Arabic, …), so a
 * chars/4 history budget overshot the model's context ~4x for non-English chats — the exact
 * "works in English, breaks in Hindi" failure mode. ASCII stays at chars/4; everything else
 * counts at 1.5 chars/token. Still an estimate, not a promise.
 */
fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var ascii = 0
    var other = 0
    for (c in text) if (c.code < 128) ascii++ else other++
    return ascii / 4 + (other * 2 + 2) / 3
}

/** Longest prefix of [text] whose [estimateTokens] stays within [maxTokens]. */
fun truncateToTokens(text: String, maxTokens: Int): String {
    if (estimateTokens(text) <= maxTokens) return text
    var ascii = 0
    var other = 0
    for (i in text.indices) {
        if (text[i].code < 128) ascii++ else other++
        if (ascii / 4 + (other * 2 + 2) / 3 > maxTokens) return text.take(i)
    }
    return text
}
