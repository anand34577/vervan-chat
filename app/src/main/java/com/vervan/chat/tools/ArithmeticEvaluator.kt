package com.vervan.chat.tools

/** Tiny recursive-descent evaluator for the `calculate` tool — deliberately not `javax.script`
 * (unavailable on Android) or a general expression library; this only ever needs
 * +, -, *, /, unary minus, parentheses, and decimals over doubles. */
object ArithmeticEvaluator {
    fun evaluate(expression: String): Double {
        val parser = Parser(expression)
        val result = parser.parseExpression()
        parser.expectEnd()
        return result
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> { pos++; value *= parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "division by zero" }
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            when (peek()) {
                '-' -> { pos++; return -parseFactor() }
                '+' -> { pos++; return parseFactor() }
                '(' -> {
                    pos++
                    val value = parseExpression()
                    skipWhitespace()
                    require(peek() == ')') { "missing closing parenthesis" }
                    pos++
                    return value
                }
            }
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            require(pos > start) { "expected a number at position $pos" }
            return input.substring(start, pos).toDouble()
        }

        private fun skipWhitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(): Char? = input.getOrNull(pos)
        fun expectEnd() { skipWhitespace(); require(pos == input.length) { "unexpected trailing input" } }
    }
}
