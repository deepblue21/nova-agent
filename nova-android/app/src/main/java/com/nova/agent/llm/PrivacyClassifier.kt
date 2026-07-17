package com.nova.agent.llm

/**
 * İstemin gizli/hassas veri içerip içermediğine dair hafif, saf bir sezgi.
 * Amaç: Hibrit modda hassas görünen istemleri — telefonda model varsa —
 * PC'ye otomatik devretmeden cihazda tutmak. Kesin bir sınıflandırıcı
 * değildir; yalnız açık işaretleri yakalar ve JVM'de test edilebilir.
 *
 * Not: PC kullanıcının kendi makinesidir (bulut değil); bu yüzden gizlilik
 * override'ı yalnız otomatik devri engeller, kullanıcının elle "PC ajanına
 * devret" tercihini kısıtlamaz.
 */
object PrivacyClassifier {

    private val keywords = listOf(
        "şifre", "sifre", "parola", "password", "passwd",
        "gizli", "mahrem", "özel anahtar", "private key", "api key", "api anahtar",
        "token", "cvv", "cvc", "pin kod", "pin kodu",
        "tckn", "tc kimlik", "kimlik no", "kimlik numaram",
        "iban", "kart numaram", "kredi kart",
    )

    // 13-19 haneli olası kart numarası (aralarında boşluk/tire olabilir).
    private val cardLike = Regex("(?:\\d[ -]?){13,19}")

    // Türkiye IBAN'ı: TR + 24 rakam (araya boşluk girebilir).
    private val ibanLike = Regex("TR\\d{2}(?:[ ]?\\d){20,22}", RegexOption.IGNORE_CASE)

    // 11 haneli TCKN adayı (kelime sınırıyla).
    private val tcknLike = Regex("(?<!\\d)\\d{11}(?!\\d)")

    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        if (keywords.any { lower.contains(it) }) return true
        if (ibanLike.containsMatchIn(text)) return true
        if (tcknLike.containsMatchIn(text)) return true
        // Kart benzeri: yalnız yeterli rakam varsa (boşluk/tire çıkarınca ≥13).
        cardLike.findAll(text).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 13..19) return true
        }
        return false
    }
}
