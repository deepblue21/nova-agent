package com.nova.agent.net

/**
 * Yerel ağda bulunan bir Horus Gateway'i.
 *
 * [baseUrl] doğrudan ayarlara yazılabilecek biçimdedir (`.../v1`).
 */
data class DiscoveredGateway(
    val displayName: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
)

/**
 * mDNS/NSD keşif çözümlemesi — SAF, JVM'de test edilebilir.
 *
 * Android'in `NsdManager`'ı bize (servis adı, host, port, TXT) verir; bu nesne
 * bunu kullanılabilir bir [DiscoveredGateway]'e çevirir. Ağ/Android API'si
 * burada YOK, bu yüzden davranış birim testiyle kilitlenebilir.
 *
 * TXT sözleşmesi `scripts/announce-mdns.mjs` ile aynıdır:
 *   v=1  ·  path=/v1  ·  tls=0|1  ·  name=<görünen ad>
 */
object GatewayDiscovery {

    const val SERVICE_TYPE: String = "_horus._tcp"

    /** NsdManager `serviceType` alanı sondaki noktayla gelebilir. */
    const val NSD_SERVICE_TYPE: String = "_horus._tcp."

    /** Desteklenen tek TXT sürümü. Gelecekte v=2 çıkarsa sessizce bağlanmayız. */
    const val SUPPORTED_VERSION: String = "1"

    /**
     * @return kullanılabilir bir gateway ya da `null` (bizim servisimiz değil /
     *         desteklenmeyen sürüm / bozuk adres). Kısmi veri uydurulmaz.
     */
    fun fromService(
        serviceName: String?,
        host: String?,
        port: Int,
        txt: Map<String, String> = emptyMap(),
    ): DiscoveredGateway? {
        val version = txt["v"]?.trim().orEmpty().ifEmpty { SUPPORTED_VERSION }
        if (version != SUPPORTED_VERSION) return null

        val h = (host ?: "").trim().removeSuffix(".")
        if (h.isEmpty()) return null

        val tls = txt["tls"]?.trim() == "1"

        // NOVA yalnız `/v1` konuşur — DİKİŞ HATASI buradaydı.
        //
        // Keşif özel bir TXT `path` değerini (örn. `/api/v1`) kabul ediyordu ve
        // bir test bunu doğruluyordu. Ama Y1'den beri `canonicalBaseUrl` yolu
        // `/v1` dışında olan her adresi REDDEDİYOR. İki bileşen ayrı ayrı
        // testliydi, aralarındaki dikiş değildi: böyle bir gateway listede
        // çıkıyor, eşleme "başarılı" diyor, sonra her istek sessizce düşüyordu.
        //
        // Doğru davranış onu hiç önermemek. Kullanılamayacak bir PC'yi listeye
        // koymak, kullanıcıya eşleme kodunu boşuna yaktırmaktan başka bir şey
        // yapmıyor.
        val path = txt["path"]?.trim().orEmpty()
        if (path.isNotEmpty() && path.trim('/') != "v1") return null

        val base = Pairing.baseUrl(h, port, tls, path.ifEmpty { "/v1" })
        if (base.isEmpty()) return null

        val name = (txt["name"]?.trim().orEmpty())
            .ifEmpty { (serviceName ?: "").trim() }
            .ifEmpty { h }

        return DiscoveredGateway(
            displayName = name.take(64),
            host = h,
            port = port,
            baseUrl = base,
        )
    }

    /**
     * Keşif listesini kararlı biçimde sıralar ve yinelenenleri (aynı baseUrl)
     * teke indirir. NSD aynı servisi birden çok arayüzde bildirebilir.
     */
    fun merge(found: List<DiscoveredGateway>): List<DiscoveredGateway> =
        found.distinctBy { it.baseUrl }
            .sortedWith(compareBy({ it.displayName.lowercase() }, { it.host }))
}
