package com.nova.agent.llm.local

/**
 * Telefona indirilebilir model tanımı. Katalog sabittir:
 * indirme URL'si belirli bir depo revizyonuna kilitlidir ve dosya
 * kurulmadan önce SHA-256 özeti buradaki değerle doğrulanır.
 */
data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val family: String,
    val quantization: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val licenseName: String,
    val licenseUrl: String,
    /** Rahat çalışma için önerilen toplam cihaz RAM'i (GB). */
    val recommendedRamGb: Int,
    /** Qwen3 şablonundaki gerçek enable_thinking değişkenini destekliyor mu. */
    val supportsThinkingToggle: Boolean,
) {
    val sizeLabel: String
        get() = "%.1f GB".format(sizeBytes / 1_073_741_824.0).replace('.', ',')
}

/**
 * Faz 1 kataloğu — yalnız apache-2.0 lisanslı, kapısız (lisans onayı
 * gerektirmeyen) Qwen3 artifact'leri. Boyut ve SHA-256 değerleri
 * 2026-07-16'da HuggingFace API'sinden doğrulandı.
 * Lisans onayı gerektiren modeller (ör. Gemma) Faz 2'de eklenecek.
 */
object LocalModelCatalog {

    const val REVISION = "3adacb36657dbe0119addf143782ed973c680716"
    private const val BASE =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/$REVISION/"

    val entries: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "qwen3-0.6b-int4",
            displayName = "Qwen3 0.6B (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            downloadUrl = BASE + "qwen3_0_6b_mixed_int4.litertlm",
            sizeBytes = 497_664_000L,
            sha256 = "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE",
            recommendedRamGb = 3,
            supportsThinkingToggle = true,
        ),
        LocalModelSpec(
            id = "qwen3-0.6b",
            displayName = "Qwen3 0.6B (tam)",
            family = "Qwen3",
            quantization = "standart",
            fileName = "Qwen3-0.6B.litertlm",
            downloadUrl = BASE + "Qwen3-0.6B.litertlm",
            sizeBytes = 614_236_160L,
            sha256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE",
            recommendedRamGb = 4,
            supportsThinkingToggle = true,
        ),
    )

    val default: LocalModelSpec = entries.first()

    fun byId(id: String?): LocalModelSpec? = entries.firstOrNull { it.id == id }
}
