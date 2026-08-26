package com.vervan.chat.data.repo

/** Returns the first available numbered copy name for reusable library entries. */
suspend fun nextNumberedCopyName(
    sourceName: String,
    exists: suspend (String) -> Boolean
): String {
    val base = sourceName.trim()
        .replace(Regex("\\s+copy\\s+\\d+$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { "Untitled" }
    var index = 1
    while (exists("$base copy $index")) index++
    return "$base copy $index"
}
