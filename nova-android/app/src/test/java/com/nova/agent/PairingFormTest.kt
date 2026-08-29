package com.nova.agent

import com.nova.agent.feature.pairing.PairingForm
import com.nova.agent.feature.pairing.PairingPhase
import com.nova.agent.feature.pairing.PairingUiState
import com.nova.agent.net.DiscoveredGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eşleme formunun kuralları. Kodlar **tek kullanımlık** olduğu için buradaki
 * hatalar kullanıcıyı doğrudan çıkmaza sokar: iki kez gönderilen bir kod
 * ikinci seferde 410 döner ve kullanıcı başarılı denemesinin başarısız
 * olduğunu sanır. O yüzden gönderim koşulları burada kilitli.
 */
class PairingFormTest {

    private fun gateway(host: String, name: String = "PC") = DiscoveredGateway(
        displayName = name,
        host = host,
        port = 8088,
        baseUrl = "http://$host:8088/v1",
    )

    private fun state(
        found: List<DiscoveredGateway> = emptyList(),
        selected: String? = null,
        code: String = "",
        phase: PairingPhase = PairingPhase.Idle,
        scanning: Boolean = false,
    ) = PairingUiState(
        scanning = scanning,
        found = found,
        selectedBaseUrl = selected,
        code = code,
        phase = phase,
    )

    // ---------- gönderim koşulları ----------

    @Test
    fun `pc secili ve kod tamsa gonderilebilir`() {
        val pc = gateway("192.168.1.5")
        assertTrue(
            PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, "ABCD2345")),
        )
    }

    @Test
    fun `pc secilmeden gonderilemez`() {
        assertFalse(PairingForm.canSubmit(state(code = "ABCD2345")))
    }

    @Test
    fun `eksik kod gonderilemez`() {
        val pc = gateway("192.168.1.5")
        assertFalse(PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, "ABCD")))
        assertFalse(PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, "")))
    }

    /** Sürerken ikinci gönderim, tek kullanımlık kodu boşa yakar. */
    @Test
    fun `takas surerken tekrar gonderilemez`() {
        val pc = gateway("192.168.1.5")
        assertFalse(
            PairingForm.canSubmit(
                state(listOf(pc), pc.baseUrl, "ABCD2345", phase = PairingPhase.Claiming),
            ),
        )
    }

    /** Ayırıcılar ve küçük harf normalleştirmede düzelir; form kabul etmeli. */
    @Test
    fun `ayiricili ve kucuk harfli kod kabul edilir`() {
        val pc = gateway("192.168.1.5")
        assertTrue(PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, "abcd-2345")))
        assertTrue(PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, " ABCD 2345 ")))
    }

    @Test
    fun `hatadan sonra yeniden gonderilebilir`() {
        val pc = gateway("192.168.1.5")
        assertTrue(
            PairingForm.canSubmit(
                state(listOf(pc), pc.baseUrl, "ABCD2345", phase = PairingPhase.Failed("hata")),
            ),
        )
    }

    // ---------- seçim uzlaştırma ----------

    /** Liste her mDNS olayında yeniden yayınlanır; seçim altından kaymamalı. */
    @Test
    fun `mevcut secim liste yenilenince korunur`() {
        val a = gateway("192.168.1.5", "Masaüstü")
        val b = gateway("192.168.1.9", "Dizüstü")
        assertEquals(
            a.baseUrl,
            PairingForm.reconcileSelection(a.baseUrl, listOf(b, a)),
        )
    }

    @Test
    fun `tek pc varsa kendiliginden secilir`() {
        val a = gateway("192.168.1.5")
        assertEquals(a.baseUrl, PairingForm.reconcileSelection(null, listOf(a)))
    }

    /** Birden fazla seçenek varsa kullanıcı adına seçim yapılmaz. */
    @Test
    fun `birden fazla pc varsa secim kullaniciya birakilir`() {
        val a = gateway("192.168.1.5")
        val b = gateway("192.168.1.9")
        assertNull(PairingForm.reconcileSelection(null, listOf(a, b)))
    }

    @Test
    fun `secili pc kaybolursa secim dusurulur`() {
        val a = gateway("192.168.1.5")
        val b = gateway("192.168.1.9")
        val c = gateway("192.168.1.11")
        // Tek aday kaldı → o seçilir.
        assertEquals(b.baseUrl, PairingForm.reconcileSelection(a.baseUrl, listOf(b)))
        // Belirsiz kaldı → seçim yok.
        assertNull(PairingForm.reconcileSelection(a.baseUrl, listOf(b, c)))
    }

    @Test
    fun `bos listede secim yok`() {
        assertNull(PairingForm.reconcileSelection("http://192.168.1.5:8088/v1", emptyList()))
    }

    // ---------- mesajlar ----------

    /**
     * Tarama bitip hiçbir şey bulunamadıysa kullanıcı sonsuza kadar
     * "aranıyor…" görmemeli; neden ve çıkış yolu yazmalı.
     */
    @Test
    fun `bos liste mesaji tarama bitince cikis yolu gosterir`() {
        val scanning = PairingForm.emptyMessage(scanning = true)
        val done = PairingForm.emptyMessage(scanning = false)

        assertTrue(scanning.contains("taranıyor"))
        assertFalse("Tarama bittiğinde hâlâ 'taranıyor' denemez", done.contains("taranıyor"))
        assertTrue("Neden söylenmeli", done.contains("Wi-Fi"))
        assertTrue("Elle yol hatırlatılmalı", done.contains("elle"))
    }

    @Test
    fun `kod yardimi ilerlemeyi gosterir`() {
        assertTrue(PairingForm.codeHelp("").contains("8"))
        assertTrue(PairingForm.codeHelp("ABCD").contains("4/8"))
        assertEquals("Kod tamam", PairingForm.codeHelp("ABCD2345"))
        // Ayırıcı sayılmaz: 8 gerçek karakter tamamdır.
        assertEquals("Kod tamam", PairingForm.codeHelp("ABCD-2345"))
        // Küçük harf ve görsel ikizler normalleşir.
        assertEquals("Kod tamam", PairingForm.codeHelp("abod-234i"))
    }

    /**
     * En sinsi durum: 8 karakter yazılmış ama alfabede olmayan bir harf var
     * (Crockford Base32'de U yoktur). Buton pasif kalır — sebebi yazmazsak
     * kullanıcı neden ilerleyemediğini anlayamaz.
     */
    @Test
    fun `gecersiz karakterli tam uzunluktaki kod nedenini soyler`() {
        val help = PairingForm.codeHelp("ABCD234U")
        assertTrue("Sebep yazılmalı", help.contains("U"))
        // Ve gerçekten gönderilemez olmalı.
        val pc = gateway("192.168.1.5")
        assertFalse(PairingForm.canSubmit(state(listOf(pc), pc.baseUrl, "ABCD234U")))
    }

    @Test
    fun `fazla uzun kod uyarilir`() {
        assertTrue(PairingForm.codeHelp("ABCD23456").contains("8"))
    }

    @Test
    fun `yazilan uzunluk ayraclari saymaz`() {
        assertEquals(0, PairingForm.typedLength(""))
        assertEquals(8, PairingForm.typedLength("ABCD-2345"))
        assertEquals(8, PairingForm.typedLength(" ABCD 2345 "))
        assertEquals(8, PairingForm.typedLength("ABCD_2345"))
    }

    // ---------- yapıştırılan eşleme bağlantısı ----------

    private val pairLink =
        "horus://pair?v=1&code=ABCD2345&host=192.168.1.5&port=8088&name=SALIH-PC"

    /**
     * Faz A'nın çıkmazını kapatan yol: keşif hiçbir şey bulamasa bile
     * (AP izolasyonu, farklı ağ) yapıştırılan bağlantı adresi de taşıdığı
     * için Basit modda tek hamlede bağlanılır.
     */
    @Test
    fun yapistirilan_baglanti_liste_bos_olsa_da_gonderilebilir() {
        val s = state(found = emptyList(), selected = null, code = pairLink)

        assertTrue(PairingForm.canSubmit(s))
        val target = PairingForm.resolveTarget(s)
        assertEquals("http://192.168.1.5:8088/v1", target?.baseUrl)
        assertEquals("ABCD2345", target?.code)
        assertEquals("SALIH-PC", target?.label)
    }

    /** Bağlantı kendi adresini taşır; listedeki seçim onu ezmemeli. */
    @Test
    fun yapistirilan_baglanti_listedeki_secimi_ezer() {
        val other = gateway("192.168.1.99", "Başka PC")
        val s = state(found = listOf(other), selected = other.baseUrl, code = pairLink)

        assertEquals("http://192.168.1.5:8088/v1", PairingForm.resolveTarget(s)?.baseUrl)
    }

    @Test
    fun bozuk_baglanti_gonderilemez_ve_nedeni_yazar() {
        val broken = "horus://pair?v=1&code=ABCD2345"  // host/port yok
        val s = state(code = broken)

        assertFalse(PairingForm.canSubmit(s))
        assertTrue(PairingForm.codeHelp(broken).contains("okunamadı"))
    }

    /** Desteklenmeyen sürüm sessizce kabul edilmez. */
    @Test
    fun bilinmeyen_surumlu_baglanti_reddedilir() {
        val v2 = "horus://pair?v=2&code=ABCD2345&host=192.168.1.5&port=8088"
        assertFalse(PairingForm.canSubmit(state(code = v2)))
    }

    /** Bizim şemamız olmayan bir metin URI sanılmamalı; düz kod yolu işler. */
    @Test
    fun baska_sema_uri_sayilmaz() {
        assertFalse(PairingForm.looksLikeUri("ABCD2345"))
        assertFalse(PairingForm.looksLikeUri("https://example.com/pair?code=ABCD2345"))
        assertTrue(PairingForm.looksLikeUri(pairLink))
        assertTrue("Boşluklu yapıştırma da tanınmalı", PairingForm.looksLikeUri("  $pairLink"))
    }

    @Test
    fun baglanti_okundugunda_yardim_metni_hedefi_yazar() {
        assertTrue(PairingForm.codeHelp(pairLink).contains("SALIH-PC"))
    }

    /** Takas sürerken yapıştırılan bağlantı da ikinci kez gönderilemez. */
    @Test
    fun baglanti_ile_de_cift_gonderim_engellenir() {
        assertFalse(
            PairingForm.canSubmit(state(code = pairLink, phase = PairingPhase.Claiming)),
        )
    }

    /** Düz kod yolunda hedef hâlâ listeden seçilen PC olmalı. */
    @Test
    fun duz_kod_secili_pcye_gider() {
        val pc = gateway("192.168.1.5", "Masaüstü")
        val target = PairingForm.resolveTarget(state(listOf(pc), pc.baseUrl, "abcd-2345"))

        assertEquals(pc.baseUrl, target?.baseUrl)
        assertEquals("ABCD2345", target?.code)
        assertEquals("Masaüstü", target?.label)
    }

    @Test
    fun duz_kod_secim_yokken_hedef_uretmez() {
        assertNull(PairingForm.resolveTarget(state(code = "ABCD2345")))
    }

    // ---------- durum türevleri ----------

    @Test
    fun `secili gateway baseUrl uzerinden bulunur`() {
        val a = gateway("192.168.1.5", "Masaüstü")
        val b = gateway("192.168.1.9", "Dizüstü")
        assertEquals(b, state(listOf(a, b), b.baseUrl).selected)
        assertNull(state(listOf(a, b), null).selected)
        // Listede olmayan bir seçim uydurulmaz.
        assertNull(state(listOf(a), b.baseUrl).selected)
    }

    @Test
    fun `busy yalniz takas surerken dogrudur`() {
        assertTrue(state(phase = PairingPhase.Claiming).busy)
        assertFalse(state(phase = PairingPhase.Idle).busy)
        assertFalse(state(phase = PairingPhase.Failed("x")).busy)
        assertFalse(state(phase = PairingPhase.Paired("telefon", "http://x/v1")).busy)
    }
}
