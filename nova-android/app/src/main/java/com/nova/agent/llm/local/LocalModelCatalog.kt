package com.nova.agent.llm.local

/**
 * Telefona indirilebilir model tanımı. Katalog sabittir:
 * indirme URL'si belirli bir depo revizyonuna kilitlidir ve dosya
 * kurulmadan önce SHA-256 özeti buradaki değerle doğrulanır.
 *
 * [gated] true ise model Hugging Face'te lisans onayı gerektirir:
 * indirme için kullanıcının HF hesabında lisansı onaylamış olması ve
 * Ayarlar'a HF erişim token'ı girmesi gerekir. Token cihazda kalır ve
 * yalnız huggingface.co'ya gönderilir.
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
    /** HF'te kapılı (lisans onayı + token gerekli) mi. */
    val gated: Boolean = false,
) {
    val sizeLabel: String
        get() = "%.1f GB".format(sizeBytes / 1_073_741_824.0).replace('.', ',')
}

/**
 * Katalog — tüm boyut/SHA-256 değerleri HuggingFace API'sinden doğrulandı
 * (Qwen: 2026-07-16, Gemma: 2026-07-16). Kapısız Qwen3 varsayılan yoldur;
 * Gemma kapılıdır ve HF lisans onayı ister.
 */
object LocalModelCatalog {

    const val QWEN_REVISION = "3adacb36657dbe0119addf143782ed973c680716"
    const val GEMMA_REVISION = "6d54daa71cfbffba6b2843c08eeb1a27e7430bf0"

    private const val QWEN_BASE =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/$QWEN_REVISION/"
    private const val GEMMA_BASE =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/$GEMMA_REVISION/"

    val entries: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "qwen3-0.6b-int4",
            displayName = "Qwen3 0.6B (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            downloadUrl = QWEN_BASE + "qwen3_0_6b_mixed_int4.litertlm",
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
            download