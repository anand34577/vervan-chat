package com.vervan.chat.llm

/** Turns an optional model-emitted clarification tag into native quick replies. */
object ClarificationParser {
    data class Request(val question: String, val options: List<String>)
    data class Parsed(val answer: String, val request: Request?)

    private val COMPLETE = Regex("<\\s*clarify\\s*>([\\s\\S]*?)<\\s*/\\s*clarify\\s*>", RegexOption.IGNORE_CASE)
    private val STREAMING = Regex("<\\s*clarify(?:\\s*>[\\s\\S]*)?$", RegexOption.IGNORE_CASE)
    private val QUESTION = Regex("\"question\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.IGNORE_CASE)
    private val OPTIONS = Regex("\"options\"\\s*:\\s*\\[([\\s\\S]*?)]", RegexOption.IGNORE_CASE)
    private val JSON_STRING = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")

    fun parse(content: String): Parsed {
        val match = COMPLETE.find(content)
        if (match != null) {
            val request = runCatching {
                val json = match.groupValues[1]
                val question = QUESTION.find(json)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()
                val options = OPTIONS.find(json)?.groupValues?.get(1)?.let { body ->
                    JSON_STRING.findAll(body)
                    .map { unescape(it.groupValues[1]).trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)
                    .toList()
                }.orEmpty()
                Request(question, options).takeIf { question.isNotBlank() }
            }.getOrNull()
            if (request != null) return Parsed(content.removeRange(match.range).trim(), request)
        }
        val streaming = STREAMING.find(content)
        return if (streaming != null) Parsed(content.substring(0, streaming.range.first).trim(), null)
        else Parsed(content, null)
    }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\' || index >= value.length) {
                result.append(char)
                continue
            }
            when (val escaped = value[index++]) {
                '"', '\\', '/' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    val end = (index + 4).coerceAtMost(value.length)
                    val code = value.substring(index, end).takeIf { it.length == 4 }?.toIntOrNull(16)
                    if (code != null) { result.append(code.toChar()); index = end } else result.append("\\u")
                }
                else -> result.append(escaped)
            }
        }
        return result.toString()
    }
}
