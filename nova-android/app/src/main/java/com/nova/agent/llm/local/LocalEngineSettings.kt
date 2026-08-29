package com.nova.agent.llm.local

/**
 * Cihaz-üstü motorun ayarlanabilir kısmı. Bu dosyada Android ya da LiteRT-LM
 * tipi YOKTUR: tamamı saf Kotlin'dir ve JVM birim testleriyle kilitlenir.
 * Native API'ye çeviri yalnız [OnDeviceEngine] içinde yapılır.
 */

/**
 * Hızlandırma tercihi.
 *
 * Sessiz devir yasağı burada da geçerlidir: kullanıcı **açıkça** GPU ya da NPU
 * seçtiyse, o backend yüklenemeyince CPU'ya gizlice düşülmez — hata dürüstçe
 * gösterilir. Yalnız [AUTO] denemeye izin verir, çünkü "otomatik" seçmek
 * denemeye verilmiş açık rızadır ve sonuç arayüzde ölçülen değerle yazılır.
 */
enum class BackendPreference(val id: String, val label: String, val note: String) {
    AUTO(
        id = "auto",
        label = "Otomatik",
        note = "Önce GPU denenir; cihaz desteklemiyorsa CPU'ya düşülür ve hangisinin " +
            "kullanıldığı ekranda yazar.",
    ),
    CPU(
        id = "cpu",
        label = "CPU",
        note = "Her ARM64 cihazda çalışır. En uyumlu, en yavaş yol.",
    ),
    GPU(
        id = "gpu",
        label = "GPU",
        note = "Destekleyen cihazlarda belirgin hızlanma. Yüklenemezse CPU'ya düşülmez, " +
            "hata gösterilir.",
    ),
    NPU(
        id = "npu",
        label = "NPU (deneysel)",
        note = "Cihaz üreticisinin NPU kütüphanelerini gerektirir. Bu yapı o kütüphaneleri " +
            "paketlemez; çoğu cihazda yüklenmez.",
    ),
    ;

    companion object {
        /** Kayıtlı değer bozuksa güvenli tarafa (Otomatik) düşer. */
        fun fromId(id: String?): BackendPreference = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Motorun GERÇEKTEN yüklendiği backend. Tercihten ayrı tutulur: arayüz
 * tahmini değil, ölçülen sonucu gösterir ("Otomatik" seçiliyken kullanıcı
 * GPU mu CPU mu çalıştığını görebilmelidir).
 */
enum class ActiveBackend(val label: String) {
    NONE("—"),
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU"),
    ;
}

/**
 * Bir tercihin hangi backend'leri, hangi sırayla deneyeceği. Saf liste —
 * [OnDeviceEngine] bunu yukarıdan aşağı dener, ilk başarılıda durur.
 */
object BackendPlan {
    fun attempts(preference: BackendPreference): List<ActiveBackend> = when (preference) {
        BackendPreference.AUTO -> listOf(ActiveBackend.GPU, ActiveBackend.CPU)
        BackendPreference.CPU -> listOf(ActiveBackend.CPU)
        BackendPreference.GPU -> listOf(ActiveBackend.GPU)
        BackendPreference.NPU -> listOf(ActiveBackend.NPU)
    }

    /** Tercih birden fazla backend deneyebiliyor mu (yalnız Otomatik). */
    fun allowsFallback(preference: BackendPreference): Boolean =
        attempts(preference).size > 1

    /**
     * Tüm denemeler başarısız olduğunda gösterilecek mesaj. Ham native hata
     * [cause] içinde korunur; üstüne ne yapılacağı yazılır.
     */
    fun failureMessage(preference: BackendPreference, cause: String): String = when (preference) {
        BackendPreference.AUTO ->
            "Model hiçbir hızlandırmayla yüklenemedi (GPU ve CPU denendi). $cause"
        BackendPreference.CPU ->
            "Model CPU ile yüklenemedi. $cause"
        BackendPreference.GPU ->
            "GPU hızlandırma bu cihazda yüklenemedi. Ayarlar > Cihaz motoru'ndan " +
                "\"Otomatik\" ya da \"CPU\" seçin. $cause"
        BackendPreference.NPU ->
            "NPU hızlandırma yüklenemedi; bu yapı üretici NPU kütüphanelerini paketlemez. " +
                "Ayarlar > Cihaz motoru'ndan \"Otomatik\" seçin. $cause"
    }
}

/**
 * Örnekleme (sampling) parametreleri.
 *
 * Varsayılan [SamplerPreset.MODEL_DEFAULT]'tur ve o durumda motora HİÇ
 * samplerConfig verilmez — yani bugünkü davranış birebir korunur. Kullanıcı
 * bilinçli bir hazır ayar ya da elle değer seçmedikçe üretim değişmez.
 */
data class SamplerSettings(
    val topK: Int,
    // topP/temperature Double: LiteRT-LM'in SamplerConfig'i Double alır
    // (dokümandaki `topP = 0.95` literali Kotlin'de Double'dır). Dönüşüm
    // yalnız arayüz kaydırıcısında (Float) yapılır, native sınırında değil.
    val topP: Double,
    val temperature: Double,
) {
    /** Aralık dışı değerleri native tarafa hiç göndermeden düzeltir. */
    fun clamped(): SamplerSettings = SamplerSettings(
        topK = topK.coerceIn(TOP_K_MIN, TOP_K_MAX),
        topP = topP.coerceIn(TOP_P_MIN, TOP_P_MAX),
        temperature = temperature.coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX),
    )

    companion object {
        const val TOP_K_MIN = 1
        const val TOP_K_MAX = 128
        const val TOP_P_MIN = 0.05
        const val TOP_P_MAX = 1.0
        const val TEMPERATURE_MIN = 0.0
        const val TEMPERATURE_MAX = 2.0
    }
}

/**
 * Hazır örnekleme ayarları.
 *
 * Değerler model ailesinden bağımsız, muhafazakâr seçildi; tek bir modele göre
 * ayarlanmış "sihirli" sayı iddiası yok. Kesin ucunda topK=1 gerçek greedy
 * çözümlemedir, dolayısıyla sıcaklık orada anlamsızdır ve düşük tutulur.
 */
enum class SamplerPreset(
    val id: String,
    val label: String,
    val note: String,
    /** null = motora samplerConfig verilme, modelin kendi varsayılanı kullanılsın. */
    val settings: SamplerSettings?,
) {
    MODEL_DEFAULT(
        id = "model_default",
        label = "Model varsayılanı",
        note = "Örnekleme ayarı gönderilmez; model kendi varsayılanıyla çalışır.",
        settings = null,
    ),
    PRECISE(
        id = "precise",
        label = "Kesin",
        note = "Tekrarlanabilir, kısa ve kuru yanıtlar. Araç kullanımı ve hesap için uygun.",
        settings = SamplerSettings(topK = 1, topP = 1.0, temperature = 0.1),
    ),
    BALANCED(
        id = "balanced",
        label = "Dengeli",
        note = "Günlük sohbet için orta yol.",
        settings = SamplerSettings(topK = 40, topP = 0.95, temperature = 0.7),
    ),
    CREATIVE(
        id = "creative",
        label = "Yaratıcı",
        note = "Daha çeşitli ama daha savruk yanıtlar.",
        settings = SamplerSettings(topK = 64, topP = 0.97, temperature = 1.0),
    ),
    CUSTOM(
        id = "custom",
        label = "Elle",
        note = "topK / topP / sıcaklık değerlerini kendin belirlersin.",
        settings = null,
    ),
    ;

    val isCustom: Boolean get() = this == CUSTOM

    companion object {
        fun fromId(id: String?): SamplerPreset = entries.firstOrNull { it.id == id } ?: MODEL_DEFAULT

        /**
         * Elle ayar başlatılırken gösterilecek makul başlangıç noktası:
         * Dengeli ile aynı değerler.
         *
         * Bilerek `BALANCED.settings` yerine düz değer yazıldı — enum girdileri
         * ile companion arasındaki ilklendirme sırasına bel bağlamamak için.
         * İkisinin eşitliğini `LocalEngineSettingsTest` doğrular, yani kopya
         * sessizce ayrışamaz.
         */
        val CUSTOM_SEED: SamplerSettings =
            SamplerSettings(topK = 40, topP = 0.95, temperature = 0.7)

        /**
         * Motora gönderilecek nihai değer. null dönerse çağıran taraf
         * samplerConfig'i HİÇ set etmemelidir (model varsayılanı korunur).
         */
        fun resolve(preset: SamplerPreset, custom: SamplerSettings): SamplerSettings? =
            if (preset.isCustom) custom.clamped() else preset.settings?.clamped()
    }
}
