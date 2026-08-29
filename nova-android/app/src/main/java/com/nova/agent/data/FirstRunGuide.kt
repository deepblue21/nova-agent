package com.nova.agent.data

/**
 * İlk açılış yönlendirmesi — **SAF**, JVM'de test edilebilir.
 *
 * Çözdüğü sorun: uygulamanın manşet özelliği telefonda çevrimdışı LLM
 * çalıştırmak, ama yeni bir kullanıcı Kontrol ekranında bundan **hiç haberdar
 * olmuyor**: Modeller sekmesi var, kimse oraya yönlendirmiyor. (Bu kart
 * yazıldığında varsayılan politika `GATEWAY_ONLY` idi; B5 ile varsayılan
 * `LOCAL_FIRST` oldu, kart artık gerçekten eksik olan tek şeyi — kurulu
 * modeli — işaret ediyor.)
 *
 * Off Grid bunu kurulumdan hemen sonra zorunlu bir model indirme ekranıyla
 * çözüyor. Burada daha hafif bir yol seçildi: Kontrol ekranında **kapatılabilir
 * bir kart**. Gerekçe — zorunlu bir ekran, yalnız PC'ye bağlanmak isteyen
 * kullanıcıyı ilgilenmediği bir indirmeyle karşılar; kart ise görünür olur
 * ama yolu tıkamaz.
 */
object FirstRunGuide {

    /**
     * Kart gösterilsin mi.
     *
     * Üç koşul birden: hiç model kurulu değil, kullanıcı kartı kapatmamış ve
     * indirme sürmüyor. Kurulu model varsa kartın işi bitmiştir; kapatıldıysa
     * bir daha çıkmaz (kalıcı ayar).
     */
    fun shouldShow(
        anyModelInstalled: Boolean,
        dismissed: Boolean,
        downloadInProgress: Boolean = false,
    ): Boolean = !anyModelInstalled && !dismissed && !downloadInProgress

    const val TITLE: String = "Telefonda çevrimdışı çalıştır"

    /**
     * Kart metni. Önerilen model adı ve boyutu **çağıran taraftan** gelir;
     * burada uydurulmaz — cihaz RAM'ine göre değişir.
     */
    fun body(recommendedName: String, recommendedSize: String): String =
        "İnternet olmadan da yanıt alabilirsin: bir model indirdiğinde istemler " +
            "telefonundan hiç çıkmaz. Cihazın için önerilen: $recommendedName " +
            "($recommendedSize). PC'ye bağlanmak istiyorsan bu adım gerekmez."

    const val PRIMARY_ACTION: String = "Modelleri aç"
    const val DISMISS_ACTION: String = "Şimdilik atla"
}
