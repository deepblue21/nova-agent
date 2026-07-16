package com.nova.agent.llm.local.tools

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Çevrimdışı hesap makinesi: + - * / % ^ ve parantez destekli, güvenli
 * (kod çalıştırmayan) özyinelemeli ayrıştırıcı. Saf/JVM-testli.
 */
object Calculator {

    sealed interface Outcome {
        data class Ok(val value: Double, val formatted: String) : Outcome
        data class Error(val message: String) : Outcome
    }

    fun evaluate(rawExpression: String): Outcome {
        val expr = rawExpression
            .replace(',', '.')
            .replace(Regex("\\s+"), "")
        if (expr.isEmpty()) return Outcome.Error("Boş ifade")
        return try {
            val parser = Parser(expr)
            val value = parser.parseExpression()
            if (!parser.atEnd()) {
                Outcome.Error("Beklenmeyen karakter: '${parser.peek()}'")
            } else if (value.isNaN() || value.isInfinite()) {
                Outcome.Error("Tanımsız sonuç (sıfıra bölme olabilir)")
            } else {
                Outcome.Ok(value, format(value))
            }
        } catch (e: CalcException) {
            Outcome.Error(e.message ?: "Geçersiz ifade")
        }
    }

    /** 8.0 → "8", 8.5 → "8.5" */
    fun format(value: Double): String =
        if (value == floor(value) && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private class CalcException(message: String) : Exception(message)

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length
        fun peek(): Char = if (atEnd()) ' ' else s[i]

        // expr := term (('+'|'-') term)*
        fun parseExpression(): Double {
            var left = parseTerm()
            while (!atEnd() && (peek() == '+' || peek() == '-')) {
                val op = s[i]
                i++
                val right = parseTerm()
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        // term := factor (('*'|'/'|'%') factor)*
        private fun parseTerm(): Double {
            var left = parseFactor()
            while (!atEnd() && (peek() == '*' || peek() == '/' || peek() == '%')) {
                val op = s[i]
                i++
                val right = parseFactor()
                left = when (op) {
                    '*' -> left * right
                    '/' -> {
                        if (right == 0.0) throw CalcException("Sıfıra bölme")
                        left / right
                    }
                    else -> {
                        if (right == 0.0) throw CalcException("Sıfıra bölme (mod)")
                        left % right
                    }
                }
            }
            return left
        }

        // factor := unary ('^' factor)?  — üs sağdan bağlar
        private fun parseFactor(): Double {
            val base = parseUnary()
            if (!atEnd() && peek() == '^') {
                i++
                val exponent = parseFactor()
                return base.pow(exponent)
            }
            return base
        }

        private fun parseUnary(): Double {
            if (!atEnd() && peek() == '-') {
                i++
                return -parseUnary()
            }
            if (!atEnd() && peek() == '+') {
                i++
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (atEnd()) throw CalcException("İfade eksik")
            if (peek() == '(') {
                i++
                val value = parseExpression()
                if (atEnd() || s[i] != ')') throw CalcException("Kapanmayan parantez")
                i++
                return value
            }
            val start = i
            while (!atEnd() && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw CalcException("Sayı bekleniyordu: '${peek()}'")
            val token = s.substring(start, i)
            return token.toDoubleOrNull() ?: throw CalcException("Geçersiz sayı: $token")
        }
    }
}
