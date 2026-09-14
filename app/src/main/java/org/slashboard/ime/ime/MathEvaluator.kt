package org.slashboard.ime.ime

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MathEvaluator {

    /**
     * Extracts and evaluates a trailing math expression from text before cursor.
     * E.g. "Total is 1+4" -> result 5.0
     * Returns null if no valid arithmetic expression is found.
     */
    fun evaluateTrailingExpression(text: String): EvaluationResult? {
        val trimmed = text.trimEnd().removeSuffix("=").trimEnd()
        if (trimmed.isEmpty()) return null

        // Scan backwards to find the start of the mathematical expression
        var startIndex = trimmed.length - 1
        var openParens = 0
        var hasOperator = false
        var hasDigit = false

        while (startIndex >= 0) {
            val c = trimmed[startIndex]
            when {
                c.isDigit() -> {
                    hasDigit = true
                    startIndex--
                }
                c == '.' || c == ',' -> {
                    startIndex--
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '×' || c == '÷' || c == '%' || c == '^' -> {
                    hasOperator = true
                    startIndex--
                }
                c == ')' -> {
                    openParens++
                    startIndex--
                }
                c == '(' -> {
                    if (openParens > 0) openParens--
                    startIndex--
                }
                c == ' ' -> {
                    startIndex--
                }
                else -> {
                    // Reached non-math character (letter, punctuation, etc.)
                    break
                }
            }
        }

        val exprCandidate = trimmed.substring(startIndex + 1).trim()
        if (!hasOperator || !hasDigit || exprCandidate.isEmpty()) return null

        // Ensure expression doesn't end with a dangling operator
        val cleanCandidate = exprCandidate.trimEnd('+', '-', '*', '/', '×', '÷', '^', ' ')
        if (cleanCandidate.isEmpty()) return null

        return try {
            val result = evaluate(cleanCandidate)
            if (result.isInfinite() || result.isNaN()) null
            else EvaluationResult(cleanCandidate, result, formatResult(result))
        } catch (_: Exception) {
            null
        }
    }

    data class EvaluationResult(
        val expression: String,
        val result: Double,
        val formattedResult: String
    )

    fun evaluate(expression: String): Double {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        if (sanitized.isEmpty()) throw IllegalArgumentException("Empty expression")

        return Parser(sanitized).parse()
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected char: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                val numStr = str.substring(startPos, pos)
                x = numStr.toDouble()
            } else {
                throw RuntimeException("Unexpected token: " + ch.toChar())
            }

            if (eat('^'.code)) x = Math.pow(x, parseFactor())
            if (eat('%'.code)) x = x / 100.0

            return x
        }
    }

    fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val rounded = Math.round(value)
        return if (Math.abs(value - rounded) < 1e-9) {
            rounded.toString()
        } else {
            val symbols = DecimalFormatSymbols(Locale.US)
            val df = DecimalFormat("#.########", symbols)
            df.format(value)
        }
    }
}
