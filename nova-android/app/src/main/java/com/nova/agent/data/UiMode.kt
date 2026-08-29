package com.nova.agent.data

/**
 * Arayüz yoğunluğu. **Yalnız görünürlüğü değiştirir, davranışı değiştirmez.**
 *
 * Basit modda gizlenen bir ayarın değeri silinmez, sıfırlanmaz ve varsayılana
 * çekilmez; kayıtlı değeriyle çalışmaya devam eder. Gelişmiş'e geçildiğinde
 * kullanıcı onu bıraktığı yerde bulur. Bu değişmez, `SettingsSection`
 * tablosunun tek doğruluk kaynağı olmasıyla korunur: hiçbir ekran "bu modda
 * şunu da kapat" mantığını kendi içinde yeniden yazmaz.
 *
 * Göç kuralı (`SettingsStore.load`): temiz kurulum Basit'te başlar, mevcut
 * kurulum Gelişmiş'te kalır. Zaten ayar yapmış bir kullanıcının kontrolleri
 * güncelleme sonrası kaybolmaz.
 */
enum class UiMode(val id: String, val label: String, val summary: String) {
    SIMPLE(
        id = "simple",
        label = "Basit",
        summary = "Günlük kullanım için gereken ayarlar. Gizlenen ayarlar kayıtlı " +
            "değerleriyle çalışmaya devam eder.",
    ),
    ADVANCED(
        id = "advanced",
        label = "Gelişmiş",
        summary = "Motor hızlandırma, örnekleme, kişilik ve elle bağlantı dahil tüm ayarlar.",
    ),
    ;

    val isAdvanced: Boolean get() = this == ADVANCED

    /** Bu modda [section] görünür mü. Tek karar noktası. */
    fun shows(section: SettingsSection): Boolean = isAdvanced || !section.advancedOnly

    companion object {
        /** Bilinmeyen/boş kimlik Basit'e düşer; kayıtlı değer bozuksa kilitlenme olmaz. */
        fun fromId(id: String?): UiMode = entries.firstOrNull { it.id == id } ?: SIMPLE
    }
}

/**
 * Moda göre gösterilip gizlenen ayar bölümleri.
 *
 * [advancedOnly] true olan bölüm Basit modda ÇİZİLMEZ. Bir bölümün buraya
 * eklenmesi, ilgili ekranda `mode.shows(...)` ile sarmalanmasını gerektirir;
 * `UiModeTest` bu tablonun beklenen şekli koruduğunu doğrular.
 */
enum class SettingsSection(val advancedOnly: Boolean) {
    /** PC'yi bul + eşle. Bağlantı kurmanın kolay yolu — her modda görünür. */
    CONNECTION(advancedOnly = false),

    /** Base URL + belirteci elle yazma. Basit modda gereksiz, hata kaynağı. */
    CONNECTION_MANUAL(advancedOnly = true),

    /** Gateway model seçici + Yenile. */
    GATEWAY_MODEL(advancedOnly = true),

    /** Çaba düzeyi + akıl yürütme anahtarı (yalnız Gateway yolunu etkiler). */
    GATEWAY_TUNING(advancedOnly = true),

    /** Renk teması. */
    APPEARANCE(advancedOnly = false),

    /** Cihaz-üstü motor: hızlandırma (backend) + örnekleme ayarları. */
    LOCAL_ENGINE(advancedOnly = true),

    /** Yerel model için sistem talimatı. */
    PERSONA(advancedOnly = true),

    /** Kapılı model indirmeleri için Hugging Face erişim belirteci. */
    HF_TOKEN(advancedOnly = true),

    /** Yerel veriyi temizle. Gizlilik kontrolü — her modda erişilebilir kalır. */
    DATA(advancedOnly = false),

    /** Sürüm bilgisi. */
    ABOUT(advancedOnly = false),
    ;
}

/**
 * Basit/Gelişmiş göçünün kararı.
 *
 * Bu dosyada durur çünkü saftır (Android bağımlılığı yok) ve JVM birim
 * testinden `SettingsStore`un DataStore delegesini yüklemeden çağrılabilir.
 *
 * Temiz kurulum Basit'te başlar. **Zaten ayar yapmış** bir kurulum Gelişmiş'te
 * kalır: güncelleme sonrası kullandığı kontrollerin kaybolması bir
 * regresyondur, "sadeleştirme" değil.
 */
fun initialUiModeFor(freshInstall: Boolean): UiMode =
    if (freshInstall) UiMode.SIMPLE else UiMode.ADVANCED
