package com.nova.agent

import com.nova.agent.llm.PrivacyClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyClassifierTest {

    @Test
    fun `sifre parola gibi anahtarlar hassas sayilir`() {
        assertTrue(PrivacyClassifier.isSensitive("wifi şifremi hatırlat"))
        assertTrue(PrivacyClassifier.isSensitive("banka parolam nedir"))
        assertTrue(PrivacyClassifier.isSensitive("my account password is"))
        assertTrue(PrivacyClassifier.isSensitive("API key üret"))
    }

    @Test
    fun `tckn iban ve kart numarasi yakalanir`() {
        assertTrue(PrivacyClassifier.isSensitive("kimlik 12345678901 olarak kaydet"))
        assertTrue(PrivacyClassifier.isSensitive("IBAN TR33 0006 1005 1978 6457 8413 26"))
        assertTrue(PrivacyClassifier.isSensitive("kart 4242 4242 4242 4242 son kullanma"))
    }

    @Test
    fun `siradan istemler hassas degildir`() {
        assertFalse(PrivacyClassifier.isSensitive("bugün hava nasıl"))
        assertFalse(PrivacyClassifier.isSensitive("23 * 7 kaç eder"))
        assertFalse(PrivacyClassifier.isSensitive("kısa bir şiir yaz"))
        assertFalse(PrivacyClassifier.isSensitive(""))
        // Kısa sayı dizisi kart/TCKN sanılmamalı.
        assertFalse(PrivacyClassifier.isSensitive("saat 12 34 buluşalım"))
    }

    /**
     * G2 regresyonu. Gateway'e giden istek TEK bir istemi değil, mesaj
     * listesinin tamamını taşır. Yalnız son mesaja bakan eski kural şu
     * sızıntıya izin veriyordu: kart numarası 1. turda yazılır ve telefonda
     * kalır; 3. turdaki masum ama UZUN istem uzunluk kuralıyla PC'ye gider
     * ve kart numarasını içeren 1. tur da onunla birlikte çıkar.
     */
    @Test
    fun `gecmisteki sir sonraki turda da hassas sayilir`() {
        val konusma = listOf(
            "kart numaram 4242 4242 4242 4242",
            "tesekkurler",
            "bana uzun bir makale ozeti yaz, konusu bahar aylarinda bahce bakimi",
        )
        assertTrue(
            "gecmisteki kart numarasi tum konusmayi hassas yapmali",
            PrivacyClassifier.isAnySensitive(konusma),
        )
        assertFalse(
            "eski kural yalniz SON mesaja bakiyordu - sizinti tam buradaydi",
            PrivacyClassifier.isSensitive(konusma.last()),
        )
    }

    @Test
    fun `tamamen masum konusma hassas degildir`() {
        assertFalse(
            PrivacyClassifier.isAnySensitive(
                listOf("merhaba", "hava nasil", "yarin ne yapsam"),
            ),
        )
    }

    @Test
    fun `bos liste hassas degildir`() {
        assertFalse(PrivacyClassifier.isAnySensitive(emptyList()))
    }
}
