package com.vervan.chat.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/** Theme-derived colors for [highlightCode] — resolved from MaterialTheme at the call site so
 * highlighting follows light/dark theme instead of hardcoded hex values. */
data class CodeHighlightColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val default: Color
)

private data class LangSpec(
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
    val hashComment: Boolean = false
)

// no prism4j/markwon-syntax-highlight artifact is available in the offline Gradle
// cache (this build has no network access), so this is a hand-rolled regex tokenizer instead
// of a real grammar-based highlighter. Covers keywords/types/strings/numbers/comments for the
// languages models commonly emit — good enough for readability, not a full lexer. Add real
// grammars if prism4j ever becomes available offline.
private val C_FAMILY_KEYWORDS = setOf(
    "abstract", "as", "assert", "break", "case", "catch", "class", "const", "continue", "default",
    "defer", "delete", "do", "else", "enum", "export", "extends", "false", "final", "finally", "for",
    "fun", "func", "goto", "if", "implements", "import", "in", "inline", "interface", "internal", "is",
    "let", "module", "mut", "namespace", "new", "null", "object", "operator", "override", "package",
    "private", "protected", "public", "readonly", "return", "sealed", "signed", "sizeof", "static",
    "struct", "super", "switch", "synchronized", "template", "this", "throw", "throws", "transient",
    "true", "try", "typedef", "typeof", "unsafe", "unsigned", "using", "val", "var", "virtual", "void",
    "volatile", "when", "where", "while", "yield"
)
private val C_FAMILY_TYPES = setOf(
    "int", "long", "short", "byte", "char", "float", "double", "bool", "boolean", "string", "String",
    "Int", "Long", "Short", "Byte", "Char", "Float", "Double", "Boolean", "Unit", "Any", "Nothing",
    "List", "Map", "Set", "Array", "number", "object"
)
private val PYTHON_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
    "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None",
    "nonlocal", "not", "or", "pass", "raise", "return", "True", "False", "try", "while", "with",
    "yield", "self"
)
private val SQL_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE",
    "ALTER", "DROP", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING",
    "LIMIT", "OFFSET", "AND", "OR", "NOT", "NULL", "AS", "DISTINCT", "UNION", "ALL", "IN", "LIKE",
    "BETWEEN", "EXISTS", "CASE", "WHEN", "THEN", "END", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
    "DEFAULT", "INDEX", "VIEW", "WITH"
)
private val BASH_KEYWORDS = setOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac",
    "function", "return", "exit", "export", "local", "echo", "read", "shift", "break", "continue",
    "true", "false"
)
private val CSS_KEYWORDS = setOf("important", "inherit", "initial", "unset", "none", "auto")

private fun langSpec(language: String): LangSpec = when (language.trim().lowercase()) {
    "kotlin", "kt", "kts", "java", "javascript", "js", "jsx", "typescript", "ts", "tsx", "c", "h",
    "cpp", "c++", "cc", "hpp", "cs", "csharp", "c#", "go", "golang", "rust", "rs", "swift", "dart",
    "php", "scala", "groovy" ->
        LangSpec(C_FAMILY_KEYWORDS, C_FAMILY_TYPES, lineComment = "//", blockComment = "/*" to "*/")
    "python", "py" -> LangSpec(PYTHON_KEYWORDS, hashComment = true)
    "bash", "sh", "shell", "zsh" -> LangSpec(BASH_KEYWORDS, hashComment = true)
    "sql" -> LangSpec(SQL_KEYWORDS, lineComment = "--", blockComment = "/*" to "*/")
    "yaml", "yml" -> LangSpec(hashComment = true)
    "css", "scss", "less" -> LangSpec(CSS_KEYWORDS, blockComment = "/*" to "*/")
    "json" -> LangSpec(setOf("true", "false", "null"))
    "xml", "html", "htm" -> LangSpec(blockComment = "<!--" to "-->")
    else -> LangSpec(hashComment = true, lineComment = "//", blockComment = "/*" to "*/")
}

private val STRING_PATTERN = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
private val NUMBER_PATTERN = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLdD]?\\b"

private fun buildMasterRegex(spec: LangSpec): Regex {
    val parts = mutableListOf<String>()
    spec.blockComment?.let { (open, close) -> parts += "(?<BLOCK>${Regex.escape(open)}[\\s\\S]*?${Regex.escape(close)})" }
    spec.lineComment?.let { parts += "(?<LINE>${Regex.escape(it)}.*)" }
    if (spec.hashComment) parts += "(?<HASH>#.*)"
    parts += "(?<STR>$STRING_PATTERN)"
    parts += "(?<NUM>$NUMBER_PATTERN)"
    if (spec.keywords.isNotEmpty()) parts += "(?<KW>\\b(?:${spec.keywords.joinToString("|") { Regex.escape(it) }})\\b)"
    if (spec.types.isNotEmpty()) parts += "(?<TY>\\b(?:${spec.types.joinToString("|") { Regex.escape(it) }})\\b)"
    return Regex(parts.joinToString("|"))
}

fun highlightCode(code: String, language: String, colors: CodeHighlightColors): AnnotatedString {
    val spec = langSpec(language)
    val master = buildMasterRegex(spec)
    return buildAnnotatedString {
        var last = 0
        for (match in master.findAll(code)) {
            if (match.range.first > last) append(code.substring(last, match.range.first))
            val style = when {
                (spec.blockComment != null && match.groups["BLOCK"] != null) ||
                    (spec.lineComment != null && match.groups["LINE"] != null) ||
                    (spec.hashComment && match.groups["HASH"] != null) ->
                    SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                match.groups["STR"] != null -> SpanStyle(color = colors.string)
                match.groups["NUM"] != null -> SpanStyle(color = colors.number)
                spec.keywords.isNotEmpty() && match.groups["KW"] != null -> SpanStyle(color = colors.keyword)
                spec.types.isNotEmpty() && match.groups["TY"] != null -> SpanStyle(color = colors.type)
                else -> SpanStyle(color = colors.default)
            }
            withStyle(style) { append(match.value) }
            last = match.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }
}
