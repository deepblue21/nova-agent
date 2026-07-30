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
    /** Büyük modeller için dürüst uyarı (indirme süresi, RAM baskısı). */
    val note: String? = null,
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
    const val FUNCTIONGEMMA_REVISION = "f1c7b940a5a2598fb940648fb3cfcc745b18184b"

    // Büyük (4B-14B) kapısız modeller — 2026-07-19'da HF API'sinden doğrulandı.
    const val QWEN_4B_REVISION = "84cc5a35c9c65cd18fcd65bb1f3a7d77a4acfe6e"
    const val QWEN_8B_REVISION = "71ff705588319d52d374977eff3da4eee0c0d26e"
    const val QWEN_14B_REVISION = "e4122fd370cec85c61467274b180e0954e4f422d"
    const val GEMMA4_E4B_REVISION = "f7ad3343bd6ebc9607f4dc3bc4f2398bd5749bc5"
    const val GEMMA4_12B_REVISION = "44cf85a326f79b814fa86a60af414c042755b43a"

    private const val QWEN_BASE =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/$QWEN_REVISION/"
    private const val GEMMA_BASE =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/$GEMMA_REVISION/"
    private const val FUNCTIONGEMMA_BASE =
        "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/$FUNCTIONGEMMA_REVISION/"
    private const val QWEN_4B_BASE =
        "https://huggingface.co/litert-community/Qwen3-4B/resolve/$QWEN_4B_REVISION/"
    private const val QWEN_8B_BASE =
        "https://huggingface.co/litert-community/Qwen3-8B/resolve/$QWEN_8B_REVISION/"
    private const val QWEN_14B_BASE =
        "https://huggingface.co/litert-community/Qwen3-14B/resolve/$QWEN_14B_REVISION/"
    private const val GEMMA4_E4B_BASE =
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/$GEMMA4_E4B_REVISION/"
    private const val GEMMA4_12B_BASE =
        "https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm/resolve/$GEMMA4_12B_REVISION/"

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
            downloadUrl = QWEN_BASE + "Qwen3-0.6B.litertlm",
            sizeBytes = 614_236_160L,
            sha256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE",
            recommendedRamGb = 4,
            supportsThinkingToggle = true,
        ),
        // ——— Büyük kapısız modeller (4B-14B). Tüm değerler HF API'sinden
        // doğrulandı (2026-07-19); hepsi apache-2.0 ve token gerektirmez.
        // Uygunluk çipi RAM'e göre dürüstçe "Riskli" gösterebilir — taklit yok.
        LocalModelSpec(
            id = "qwen3-4b-int4",
            displayName = "Qwen3 4B (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_4b_mixed_int4.litertlm",
            downloadUrl = QWEN_4B_BASE + "qwen3_4b_mixed_int4.litertlm",
            sizeBytes = 2_659_057_664L,
            sha256 = "f0794bc77efeaaf4f7af815f04c483b19b8f2ae4a102cef1b7b760a25848a18e",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-4B/blob/main/LICENSE",
            recommendedRamGb = 8,
            supportsThinkingToggle = true,
        ),
        LocalModelSpec(
            id = "gemma4-e4b",
            displayName = "Gemma 4 E4B (uç-cihaz)",
            family = "Gemma 4",
            quantization = "int4",
            fileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = GEMMA4_E4B_BASE + "gemma-4-E4B-it.litertlm",
            sizeBytes = 3_659_530_240L,
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/google/gemma-4-E4B-it",
            recommendedRamGb = 8,
            supportsThinkingToggle = true,
            note = "4B etkin parametre; telefon için optimize edilmiş uç-cihaz sürümü.",
        ),
        LocalModelSpec(
            id = "qwen3-8b-int4",
            displayName = "Qwen3 8B (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_8b_mixed_int4.litertlm",
            downloadUrl = QWEN_8B_BASE + "qwen3_8b_mixed_int4.litertlm",
            sizeBytes = 4_887_412_736L,
            sha256 = "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-8B/blob/main/LICENSE",
            recommendedRamGb = 12,
            supportsThinkingToggle = true,
            note = "12 GB+ RAM'li amiral gemisi telefonlar için. İndirme uzun sürer.",
        ),
        LocalModelSpec(
            id = "gemma4-12b",
            displayName = "Gemma 4 12B",
            family = "Gemma 4",
            quantization = "int4",
            fileName = "gemma-4-12B-it.litertlm",
            downloadUrl = GEMMA4_12B_BASE + "gemma-4-12B-it.litertlm",
            sizeBytes = 6_547_589_312L,
            sha256 = "74fc29a10c20eb5b3ced6c389471a7994a0ffd657255b2a1c764262fb9054aef",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/google/gemma-4-12B-it",
            recommendedRamGb = 16,
            supportsThinkingToggle = true,
            note = "16 GB+ RAM gerekir. Yükleme ve ilk yanıt belirgin şekilde yavaştır.",
        ),
        LocalModelSpec(
            id = "qwen3-14b-int4",
            displayName = "Qwen3 14B (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_14b_mixed_int4.litertlm",
            downloadUrl = QWEN_14B_BASE + "qwen3_14b_mixed_int4.litertlm",
            sizeBytes = 8_655_863_808L,
            sha256 = "71de7d58f1b46a3fcba2f7bb700ebcc3c3715877d9a7028d93dd1bcd89bbe946",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-14B/blob/main/LICENSE",
            recommendedRamGb = 24,
            supportsThinkingToggle = true,
            note = "Katalogdaki en büyük model. Yalnız 24 GB+ RAM'li cihazlarda " +
                "denenmelidir; çoğu telefonda bellek yetersizliğinden başlatılamaz.",
        ),
        LocalModelSpec(
            id = "gemma3-1b-int4",
            displayName = "Gemma 3 1B (int4)",
            family = "Gemma 3",
            quantization = "int4",
            fileName = "gemma3-1b-it-int4.litertlm",
            downloadUrl = GEMMA_BASE + "gemma3-1b-it-int4.litertlm",
            sizeBytes = 584_417_280L,
            sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
            licenseName = "Gemma Şartları",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            recommendedRamGb = 4,
            supportsThinkingToggle = false,
            gated = true,
        ),
        LocalModelSpec(
            id = "functiongemma-270m",
            displayName = "FunctionGemma 270M (araç odaklı)",
            family = "FunctionGemma",
            quantization = "q8",
            fileName = "mobile_actions_q8_ekv1024.litertlm",
            downloadUrl = FUNCTIONGEMMA_BASE + "mobile_actions_q8_ekv1024.litertlm",
            sizeBytes = 288_964_608L,
            sha256 = "33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0",
            licenseName = "Gemma Şartları",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            recommendedRamGb = 2,
            supportsThinkingToggle = false,
            gated = true,
        ),
    )

    val default: LocalModelSpec = entries.first()

    fun byId(id: String?): LocalModelSpec? = entries.firstOrNull { it.id == id }
}
