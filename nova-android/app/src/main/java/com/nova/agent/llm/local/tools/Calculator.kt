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
            } else if (value.isNaN()) {
                // Sıfıra bölme AYRICA ve daha önce yakalanıyor (parseTerm), yani
                // buraya asla ulaşamaz: eski "(sıfıra bölme olabilir)" açıklaması
                // hiçbir koşulda doğru olamayacak bir tahmindi. Buraya düşen
                // gerçek durumlar farklı — örn. negatif sayının kesirli kuvveti.
                Outcome.Error("Tanımsız sonuç (geçersiz işlem)")
            } else if (value.isInfinite()) {
                Outcome.Error("Sonuç sayı aralığının dışında (taşma)")
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

        /**
         * Özyineleme derinliği.
         *
         * `((((…))))` gibi derin iç içe ifade `parsePrimary → parseExpression →
         * … → parsePrimary` zincirini yığın taşana kadar sürüyordu.
         * `StackOverflowError` bir `Exception` DEĞİL, `Error`'dır: aşağıdaki
         * `catch (e: CalcException)` onu yakalamaz ve **uygulama çöker**.
         * "Araç hataları uygulamayı düşüremez" garantisi tam burada kırılıyordu.
         *
         * Yığın taşmasını yakalamaya çalışmak güvenilmez (yakalandığı anda yığın
         * zaten tükenmiştir); doğrusu oraya hiç varmamak. Sınır cömert: elle
         * yazılabilecek her makul ifadenin çok üstünde.
         */
        private var depth = 0

        private inline fun <T> nested(block: () -> T): T {
            if (++depth > MAX_DEPTH) throw CalcException("İfade fazla iç içe")
            try {
                return block()
            } finally {
                depth--
            }
        }

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
            // "-----…-1" de aynı zinciri kurar; o da sayılır.
            if (!atEnd() && peek() == '-') {
                i++
                return nested { -parseUnary() }
            }
            if (!atEnd() && peek() == '+') {
                i++
                return nested { parseUnary() }
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (atEnd()) throw CalcException("İfade eksik")
            if (peek() == '(') {
                i++
                val value = nested { parseExpression() }
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

        private companion object {
            const val MAX_DEPTH = 128
        }
    }
}
