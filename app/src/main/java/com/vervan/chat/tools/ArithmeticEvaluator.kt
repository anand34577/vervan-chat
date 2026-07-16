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
        // A `calculate` call is a string the model constructs (or echoes back from user input);
        // thousands of nested '(' — or unary +/- — recursed parseFactor()/parseExpression() with
        // no limit, throwing StackOverflowError (an Error, not an Exception) up through
        // ToolRegistry's catch(Exception) and relying entirely on ChatViewModel's outer
        // catch(Throwable) to avoid crashing. Failing closed here with a normal exception is the
        // more robust fix, in case this evaluator is ever called from a path that only catches
        // Exception (as it already is in one ChatViewModel tool-execution site before this
        // review's Throwable-widening fix).
        private var depth = 0

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
                '-' -> { pos++; return -withDepth { parseFactor() } }
                '+' -> { pos++; return withDepth { parseFactor() } }
                '(' -> {
                    pos++
                    val value = withDepth { parseExpression() }
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

        private inline fun withDepth(block: () -> Double): Double {
            require(depth < MAX_DEPTH) { "expression is too deeply nested" }
            depth++
            try {
                return block()
            } finally {
                depth--
            }
        }

        private fun skipWhitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(): Char? = input.getOrNull(pos)
        fun expectEnd() { skipWhitespace(); require(pos == input.length) { "unexpected trailing input" } }

        companion object {
            private const val MAX_DEPTH = 200
        }
    }
}
