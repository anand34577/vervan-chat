package com.vervan.chat.voice

/**
 * Reduces one complete sentence (as emitted by [SentenceChunker], not a raw mid-token fragment —
 * that's what makes whole-syntax regexes like `**text**` safe here) to plain, speakable text.
 * The LLM replies in markdown for on-screen rendering, but a TTS engine has no business reading
 * "asterisk asterisk bold asterisk asterisk" aloud — this strips the syntax while keeping the
 * wrapped words. Not a full markdown parser: a handful of regex passes cover what LLM replies
 * actually use (headings, emphasis, links, code spans/fences, lists, blockquotes, tables,
 * horizontal rules) — good enough for speech, not for re-rendering.
 */
fun markdownToSpeechText(markdown: String): String {
    var text = markdown

    // Fenced code blocks: speak this as one short phrase, not the fence markers + raw source.
    text = text.replace(Regex("```[\\w+.-]*\\r?\\n?([\\s\\S]*?)```"), " code block ")
    // Inline code: speak the content, drop the backticks.
    text = text.replace(Regex("`([^`]+)`"), "$1")
    // Images: speak the alt text if there is one, otherwise drop it entirely.
    text = text.replace(Regex("!\\[([^]]*)]\\([^)]*\\)")) { it.groupValues[1] }
    // Links: speak the link text, drop the URL.
    text = text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    // Bold/italic/strikethrough: keep the wrapped text, drop the markers.
    text = text.replace(Regex("(\\*\\*\\*|___)(.+?)\\1"), "$2")
    text = text.replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
    text = text.replace(Regex("(?<!\\w)(\\*|_)(.+?)\\1(?!\\w)"), "$2")
    text = text.replace(Regex("~~(.+?)~~"), "$1")
    // Headings, blockquotes, list markers — all line-leading syntax.
    text = text.lineSequence().joinToString("\n") { line ->
        line.replace(Regex("^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+[.)]\\s+)"), "")
    }
    // Horizontal rules (a line of only -/*/_ characters).
    text = text.replace(Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$", RegexOption.MULTILINE), "")
    // Table pipes read poorly aloud — collapse to spaces.
    text = text.replace('|', ' ')
    // Escaped markdown characters (\* \_ etc.) become the literal character.
    text = text.replace(Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!])"), "$1")

    return text.replace(Regex("\\s+"), " ").trim()
}
