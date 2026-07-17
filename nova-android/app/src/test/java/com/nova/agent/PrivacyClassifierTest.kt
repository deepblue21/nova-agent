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
}
