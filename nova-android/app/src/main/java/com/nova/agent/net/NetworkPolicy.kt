package com.nova.agent.net

/**
 * Şifresiz (http) bağlantıya nerede izin verildiğini belirler — SAF, testli.
 *
 * NEDEN MANIFEST'TE DEĞİL: Android'in `network_security_config.xml` dosyası
 * alan adı eşleştirir; `192.168.0.0/16` gibi bir CIDR aralığı İFADE EDEMEZ.
 * Yerel ağdaki gateway'in IP'si her evde farklı olduğundan manifest ya hepsine
 * açık olmak zorunda kalır ya da LAN'ı tamamen kırar. Bu yüzden gerçek kontrol
 * burada, kodda ve birim testiyle kilitli.
 *
 * Kural: **http yalnız özel/yerel adreslere.** Bir QR ya da elle girilen adres
 * genel bir IP'ye şifresiz işaret ediyorsa reddedilir — istem ve API anahtarı
 * açık ağdan geçmez.
 */
object NetworkPolicy {

    /** RFC1918 + loopback + link-local + CGNAT(tailnet) + `.local` mDNS adları. */
    fun isLocalHost(host: String?): Boolean {
        val h = (host ?: "").trim().removeSurrounding("[", "]").removeSuffix(".").lowercase()
        if (h.isEmpty()) return false

        if (h == "localhost") return true
        if (h.endsWith(".local")) return true

        // IPv6 loopback / link-local / unique-local
        if (h.contains(':')) {
            return h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")
        }

        val octets = h.split('.')
        if (octets.size != 4) return false
        val n = octets.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false

        return when {
            n[0] == 127 -> true                                   // loopback
            n[0] == 10 -> true                                    // 10/8
            n[0] == 192 && n[1] == 168 -> true                    // 192.168/16
            n[0] == 172 && n[1] in 16..31 -> true                 // 172.16/12
            n[0] == 169 && n[1] == 254 -> true                    // link-local
            n[0] == 100 && n[1] in 64..127 -> true                // CGNAT / tailnet
            else -> false
        }
    }

    /** Bir Base URL'in taşıma güvenliği açısından kabul edilebilirliği. */
    sealed interface Verdict {
        data object Allowed : Verdict
        /** Reddedildi; [reason] kullanıcıya gösterilecek, [hint] ne yapacağını söyler. */
        data class Blocked(val reason: String, val hint: String) : Verdict
    }

    fun check(baseUrl: String?): Verdict {
        val url = (baseUrl ?: "").trim()
        if (url.isEmpty()) {
            return Verdict.Blocked("Adres boş", "PC'de start-horus çalıştırıp QR'ı okut.")
        }
        val lower = url.lowercase()
        if (lower.startsWith("https://")) return Verdict.Allowed
        if (!lower.startsWith("http://")) {
            return Verdict.Blocked(
                "Desteklenmeyen adres biçimi",
                "Adres http:// veya https:// ile başlamalı.",
            )
        }
        val host = hostOf(url)
        if (host.isEmpty()) {
            return Verdict.Blocked("Adres çözümlenemedi", "Adresi QR'dan tekrar okut.")
        }
        if (isLocalHost(host)) return Verdict.Allowed

        return Verdict.Blocked(
            "Şifresiz bağlantı yalnız yerel ağda kullanılabilir",
            "$host yerel bir adres değil. İstemin ve anahtarın açık ağdan " +
                "şifresiz geçmemesi için bu bağlantı engellendi. Uzaktan " +
                "bağlanmak için https kullan.",
        )
    }

    fun allows(baseUrl: String?): Boolean = check(baseUrl) is Verdict.Allowed

    /**
     * Şema ve **ayrıştırılmış** host üzerinden karar verir (Y1).
     *
     * `check(baseUrl)` dizgeyi kendi ayrıştırıyor; bu aşırı yük ise OkHttp'nin
     * çözdüğü gerçek host'u alır. Aradaki farkı (userinfo, `?@`, kodlanmış
     * karakterler) sömürmek mümkün olmasın diye asıl kapı budur.
     */
    fun allowsHost(scheme: String, host: String): Boolean {
        if (scheme.equals("https", ignoreCase = true)) return true
        if (!scheme.equals("http", ignoreCase = true)) return false
        return isLocalHost(host)
    }

    /** `http://[fe80::1]:8088/v1` → `fe80::1`. Ayrıştırılamazsa boş dizge. */
    fun hostOf(url: String?): String {
        val u = (url ?: "").trim()
        val schemeEnd = u.indexOf("://")
        if (schemeEnd < 0) return ""
        var rest = u.substring(schemeEnd + 3)
        // Yetkili (authority) bölümü ilk '/', '?' ya da '#' ile biter.
        //
        // Eskiden sınır YALNIZ '/' ile aranıyordu; '?' ve '#' hesaba
        // katılmıyordu. Sonuç bir atlatma yoluydu:
        //   hostOf("http://evil.com?@192.168.1.5") -> "192.168.1.5"
        // yani politika "yerel adres" deyip izin veriyor, OkHttp ise gerçekte
        // evil.com'a bağlanıyordu.
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        // kullanıcı bilgisi varsa at (yalnız authority içinde arayarak)
        val at = authority.lastIndexOf('@')
        rest = if (at >= 0) authority.substring(at + 1) else authority
        if (rest.startsWith("[")) {
            val close = rest.indexOf(']')
            return if (close > 0) rest.substring(1, close) else ""
        }
        return rest.substringBefore(':')
    }
}
