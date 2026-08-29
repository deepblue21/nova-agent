package com.nova.agent.feature.pairing

import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.Pairing

/**
 * Eşleme panelinin durumu ve kuralları — **SAF**, JVM'de test edilebilir.
 *
 * Android ve ağ tipleri burada yok; `NsdManager` ve OkHttp kabuğu
 * [PairingController] içinde. Böylece "ne zaman gönderilebilir", "hiçbir şey
 * bulunamayınca ne yazar" gibi kararlar birim testiyle kilitlenir.
 */

/** Eşleme akışının bulunduğu aşama. */
sealed interface PairingPhase {
    /** Henüz bir şey denenmedi; liste taranıyor ya da boş. */
    data object Idle : PairingPhase

    /** Kod takas ediliyor. Bu sırada tekrar gönderim engellenir. */
    data object Claiming : PairingPhase

    /** Başarısız — [message] ne olduğunu, [hint] ne yapılacağını söyler. */
    data class Failed(val message: String, val hint: String = "") : PairingPhase

    /** Anahtar alındı ve kaydedildi. Anahtarın KENDİSİ burada tutulmaz. */
    data class Paired(val label: String, val baseUrl: String) : PairingPhase
}

/**
 * Panelin tam durumu.
 *
 * [code] kullanıcının yazdığı ham metindir; normalleştirme gönderim anında
 * [Pairing.normalizeCode] ile yapılır (0/O, 1/I/L karışıklığı orada düzelir).
 */
data class PairingUiState(
    val scanning: Boolean = false,
    val found: List<DiscoveredGateway> = emptyList(),
    val selectedBaseUrl: String? = null,
    val code: String = "",
    val phase: PairingPhase = PairingPhase.Idle,
) {
    val selected: DiscoveredGateway?
        get() = found.firstOrNull { it.baseUrl == selectedBaseUrl }

    val busy: Boolean get() = phase is PairingPhase.Claiming
}

/**
 * Kod alanına yazılan/yapıştırılanın ne olduğu.
 *
 * Alan iki şeyi birden kabul eder: PC ekranındaki 8 karakterlik kod ya da
 * QR'ın içerdiği tam `horus://pair?...` bağlantısı. İkincisi adresi de
 * taşıdığı için keşif başarısız olsa bile (AP izolasyonu, farklı ağ) tek
 * hamlede bağlanmayı mümkün kılar — Basit modda elle adres alanı yok.
 */
sealed interface PairingInput {
    /** Düz kod. Hedef, listeden seçilmiş PC olmalı. */
    data class Code(val code: String) : PairingInput

    /** Tam eşleme bağlantısı: adres + kod birlikte geldi, listeye gerek yok. */
    data class Uri(val baseUrl: String, val code: String, val name: String) : PairingInput

    /** Henüz gönderilebilir bir şey yok. */
    data object Incomplete : PairingInput
}

/** Takasın gideceği yer. */
data class PairingTarget(val baseUrl: String, val code: String, val label: String)

/** Eşleme formunun kuralları. Arayüz bunları yeniden yazmaz, buradan sorar. */
object PairingForm {

    /** Kodun normalleşmiş uzunluğu; `Pairing` ile aynı sözleşme. */
    const val CODE_LENGTH: Int = 8

    /** Girdi tam eşleme bağlantısı gibi mi görünüyor (geçerli olması şart değil). */
    fun looksLikeUri(raw: String): Boolean =
        raw.trim().startsWith(Pairing.SCHEME_PREFIX.substringBefore('?'), ignoreCase = true)

    /**
     * Kod alanındaki metni yorumlar. Sıra önemli: URI kontrolü önce yapılır,
     * çünkü tam bir bağlantı [Pairing.normalizeCode] için "geçersiz kod"tur
     * (uzunluk ve alfabe tutmaz) ve düz kod olarak yorumlanırsa sessizce
     * yok sayılırdı.
     */
    fun parseInput(raw: String): PairingInput {
        if (looksLikeUri(raw)) {
            val parsed = Pairing.parsePairUri(raw.trim()) ?: return PairingInput.Incomplete
            return PairingInput.Uri(parsed.baseUrl, parsed.code, parsed.name)
        }
        val code = Pairing.normalizeCode(raw)
        return if (code.length == CODE_LENGTH) PairingInput.Code(code) else PairingInput.Incomplete
    }

    /**
     * Takasın gerçekten gideceği hedef; gönderilemez durumda `null`.
     *
     * Yapıştırılan bağlantı kendi adresini taşır ve **listeden seçim
     * gerektirmez**; düz kod ise seçili PC'ye gider.
     */
    fun resolveTarget(state: PairingUiState): PairingTarget? {
        return when (val input = parseInput(state.code)) {
            is PairingInput.Uri -> PairingTarget(
                baseUrl = input.baseUrl,
                code = input.code,
                label = input.name.ifBlank { input.baseUrl },
            )

            is PairingInput.Code -> {
                val target = state.selected ?: return null
                PairingTarget(target.baseUrl, input.code, target.displayName)
            }

            PairingInput.Incomplete -> null
        }
    }

    /**
     * Gönderilebilir mi.
     *
     * Çözülebilir bir hedef var ve şu an bir takas sürmüyor. Sürerken izin
     * vermek çift takas denemesi demektir — kodlar tek kullanımlık olduğu için
     * ikincisi 410 ile döner ve kullanıcı kendi başarılı denemesinin başarısız
     * olduğunu sanır.
     */
    fun canSubmit(state: PairingUiState): Boolean =
        !state.busy && resolveTarget(state) != null

    /**
     * Liste boşken gösterilecek metin. Hiçbir koşulda "aranıyor…" diye
     * sonsuza kadar dönmez: tarama bittiğinde neden bulunamadığını ve elle
     * yolun var olduğunu söyler.
     */
    fun emptyMessage(scanning: Boolean): String = if (scanning) {
        "Yerel ağ taranıyor…"
    } else {
        "Yakınlarda PC bulunamadı. PC'de start-horus.ps1 -Lan çalışıyor olmalı ve " +
            "telefon aynı Wi-Fi ağında olmalı. Bazı yönlendiriciler cihazlar arası " +
            "yayını engeller.\n\n" +
            "Liste boş kalsa bile bağlanabilirsin: PC ekranındaki QR'ın altındaki " +
            "horus://pair… bağlantısını aşağıdaki alana yapıştır — adres ve kod " +
            "birlikte gelir."
    }

    /**
     * Ayraçlar atıldıktan sonra yazılmış karakter sayısı.
     *
     * [Pairing.normalizeCode] ilerleme ölçmek için kullanılamaz: tam 8 geçerli
     * karakter yoksa **boş dizge** döner, yani "4/8" gibi bir sayaç ondan
     * türetilemez. Ayraç kuralları oradakiyle birebir aynı tutulur.
     */
    fun typedLength(code: String): Int =
        code.count { !it.isWhitespace() && it != '-' && it != '_' && it != '.' }

    /**
     * Kod alanının altındaki yardım metni: yazdıkça ilerleme, tamamlanınca
     * onay, geçersizse **nedeni**.
     *
     * Sebebi yazmak önemli: alfabede olmayan bir harf ya da bozuk bir
     * bağlantı yüzünden buton pasif kalırsa, kullanıcı neden ilerleyemediğini
     * göremeden takılır.
     */
    fun codeHelp(code: String): String {
        if (looksLikeUri(code)) {
            return when (val input = parseInput(code)) {
                is PairingInput.Uri ->
                    "Bağlantı okundu: ${input.name.ifBlank { input.baseUrl }}"
                else ->
                    "Bu eşleme bağlantısı okunamadı. QR'ı yeniden okut ya da " +
                        "altındaki 8 karakterlik kodu gir."
            }
        }
        val typed = typedLength(code)
        return when {
            typed == 0 -> "PC ekranındaki 8 karakterlik kod (ya da QR bağlantısını yapıştır)"
            typed < CODE_LENGTH ->
                "$typed/$CODE_LENGTH — O/0 ve I/1 karışıklığı otomatik düzeltilir"
            typed > CODE_LENGTH -> "Kod $CODE_LENGTH karakter olmalı ($typed yazıldı)"
            Pairing.normalizeCode(code).isEmpty() ->
                "Kodda bu alfabede olmayan bir karakter var (U harfi kullanılmaz)"
            else -> "Kod tamam"
        }
    }

    /**
     * Yeni keşif listesi geldiğinde seçim ne olmalı.
     *
     * Kurallar: seçili PC listede duruyorsa seçim korunur (liste her mDNS
     * olayında yeniden yayınlanır; seçimin altından kayması sinir bozucu olur).
     * Seçim yoksa ve TEK bir PC varsa o seçilir — tek seçenekte kullanıcıya
     * dokundurmanın bir değeri yok. Birden fazlaysa seçim kullanıcıya bırakılır.
     */
    fun reconcileSelection(
        previousBaseUrl: String?,
        found: List<DiscoveredGateway>,
    ): String? {
        if (previousBaseUrl != null && found.any { it.baseUrl == previousBaseUrl }) {
            return previousBaseUrl
        }
        return found.singleOrNull()?.baseUrl
    }
}
