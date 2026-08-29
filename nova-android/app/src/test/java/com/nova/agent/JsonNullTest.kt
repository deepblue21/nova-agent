package com.nova.agent

import com.nova.agent.util.str
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * J1 — JSON null sözleşmesi.
 *
 * DÜRÜSTLÜK NOTU: bu test hatayı CİHAZDA yakalayamaz. Birim testi masaüstü
 * `org.json` referans uygulamasıyla koşar ve o uygulama JSON null'da fallback
 * döndürür — yani ham `optString` de bu testi geçerdi. Hata yalnızca Android'in
 * kendi `org.json`'unda var: orada `optString` `"null"` DİZESİNİ döndürüyor.
 *
 * O yüzden bu test sözleşmeyi yazıya döker; gerçek koruma
 * `SourceGuardTest.ham optString kullanilmaz` guard'ıdır — üretim kodunda ham
 * `optString` kalmadığını garanti eder.
 */
class JsonNullTest {

    @Test
    fun `JSON null fallback dondurur`() {
        val o = JSONObject("""{"route": null, "content": null}""")

        assertEquals("", o.str("route"))
        assertEquals("yedek", o.str("content", "yedek"))
    }

    @Test
    fun `olmayan anahtar fallback dondurur`() {
        assertEquals("yedek", JSONObject("{}").str("yok", "yedek"))
    }

    @Test
    fun `gercek deger aynen gelir`() {
        val o = JSONObject("""{"route": "telefon/qwen3", "content": " "}""")

        assertEquals("telefon/qwen3", o.str("route"))
        // Akıştaki tek boşluk anlamlıdır: token ayırıcı. Kırpılmamalı.
        assertEquals(" ", o.str("content"))
    }

    @Test
    fun `null dizesi degeri korunur`() {
        // Kullanıcı gerçekten "null" yazdıysa o bir metindir, kayıp değil.
        assertEquals("null", JSONObject("""{"content": "null"}""").str("content"))
    }
}
