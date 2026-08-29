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
 * Katalog — **her satırın** boyutu ve SHA-256'sı HuggingFace API'sinden
 * doğrulandı, indirme URL'si sabit bir commit'e kilitlendi. Tahmin edilen
 * değer eklenmez; doğrulanamayan model katalogda yer almaz.
 *
 * Doğrulama turları: Qwen3 0.6B + Gemma3 1B (2026-07-16) · 4B-14B sınıfı
 * (2026-07-19) · orta sınıf 1.7B/E2B + Granite 350M (2026-08-09).
 *
 * RAM kapsaması bilinçli olarak 2 GB'dan 24 GB'a yayılır ve **her sınıfta
 * kapısız bir seçenek** bulunur; ilk kurulum HF token'ı olmadan tamamlanabilir.
 * Kapılı modeller (Gemma şartları) listede kalır ama gerekliliği açıkça yazar.
 *
 * Yol haritası ve Off Grid karşılaştırması: `docs/MODEL-KATALOG-YOLHARITASI.md`.
 */
object LocalModelCatalog {

    const val QWEN_REVISION = "3adacb36657dbe0119addf143782ed973c680716"
    const val GEMMA_REVISION = "6d54daa71cfbffba6b2843c08eeb1a27e7430bf0"
    const val FUNCTIONGEMMA_REVISION = "f1c7b940a5a2598fb940648fb3cfcc745b18184b"

    // Orta sınıf (5-7 GB RAM) kapısız modeller — 2026-08-09'da HF API'sinden
    // doğrulandı. Bu aralık daha önce boştu: 4 GB'lık 0.6B ile 8 GB'lık 4B
    // arasında hiçbir seçenek yoktu ve 6 GB'lık telefonlar (en yaygın sınıf)
    // ya zayıf ya da "Riskli" bir modele düşüyordu.
    const val QWEN_1_7B_REVISION = "d9b8a9126e5ac18591306eacd4311ba43b92421e"
    const val GEMMA4_E2B_REVISION = "361a4010ad6d88fc5c86e148e333c0342b99763d"

    // Düşük uç için KAPISIZ seçenek. 2 GB sınıfındaki tek model (FunctionGemma)
    // kapılıydı; "ilk kurulum tokensız" ilkesi bununla gerçekten sağlanıyor.
    const val GRANITE_350M_REVISION = "6f1e9bce89b174930a79de82d0dcdede708f8c34"

    // Qwen'in yeni kuşağı ve uzmanlaşmış 4B sürümleri — 2026-08-10'da HF
    // API'sinden doğrulandı. Hepsi apache-2.0 ve kapısız.
    const val QWEN_3_5_0_8B_REVISION = "512221aa511cc87d7c02720b99707113276592b0"
    const val QWEN_4B_INSTRUCT_2507_REVISION = "a7385088ed97778d7cf91a0b541fa1f95735f768"
    const val QWEN_4B_THINKING_2507_REVISION = "efaa960085085bfd345d0a0d85452fa8395635ae"

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
    private const val QWEN_1_7B_BASE =
        "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/$QWEN_1_7B_REVISION/"
    private const val GEMMA4_E2B_BASE =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$GEMMA4_E2B_REVISION/"
    private const val GRANITE_350M_BASE =
        "https://huggingface.co/litert-community/granite-4.0-350m-litert-lm/resolve/$GRANITE_350M_REVISION/"
    private const val QWEN_3_5_0_8B_BASE =
        "https://huggingface.co/litert-community/Qwen3.5-0.8B/resolve/$QWEN_3_5_0_8B_REVISION/"
    private const val QWEN_4B_INSTRUCT_2507_BASE =
        "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/resolve/" +
            "$QWEN_4B_INSTRUCT_2507_REVISION/"
    private const val QWEN_4B_THINKING_2507_BASE =
        "https://huggingface.co/litert-community/Qwen3-4B-Thinking-2507/resolve/" +
            "$QWEN_4B_THINKING_2507_REVISION/"
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
            note = "Varsayılan başlangıç modeli. Küçük ve hızlıdır; kısa sohbet, " +
                "çeviri ve özetleme için yeterli, uzun akıl yürütmede zorlanır. " +
                "Token gerektirmez.",
        ),
        LocalModelSpec(
            id = "granite-4.0-350m",
            displayName = "Granite 4.0 350M (q8)",
            family = "Granite 4.0",
            quantization = "q8",
            fileName = "granite-4.0-350m_q8_ekv1280.litertlm",
            downloadUrl = GRANITE_350M_BASE + "granite-4.0-350m_q8_ekv1280.litertlm",
            sizeBytes = 468_209_584L,
            sha256 = "c8e9a29493f62b7c44461fb36980987c4c1454c75e95f57ba0539a8edc9dce76",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/ibm-granite/granite-4.0-350m",
            recommendedRamGb = 2,
            supportsThinkingToggle = false,
            note = "Katalogdaki en küçük ve en az RAM isteyen model. Eski ya da " +
                "düşük bellekli telefonlar için; yanıtları kısa ve basittir. " +
                "Lisans onayı ve token gerektirmez.",
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
            note = "int4 sürümüyle aynı model, sıkıştırılmamış hâli. Biraz daha " +
                "tutarlı yanıt verir ama ~1 GB daha fazla RAM ister. " +
                "0,6B int4 cihazında rahat çalışıyorsa bir sonraki adım budur.",
        ),
        // ——— Orta sınıf (5-7 GB RAM) kapısız modeller. Boyut/SHA-256/revizyon
        // 2026-08-09'da HF API'sinden doğrulandı; hepsi apache-2.0 ve tokensız.
        // Bu aralık daha önce tamamen boştu.
        LocalModelSpec(
            id = "qwen3-1.7b-int4",
            displayName = "Qwen3 1.7B (int4)",
            family = "Qwen3",
            quantization = "dynamic int4",
            fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = QWEN_1_7B_BASE + "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
            sizeBytes = 977_184_032L,
            sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-1.7B/blob/main/LICENSE",
            recommendedRamGb = 5,
            supportsThinkingToggle = true,
            note = "Orta sınıf telefonlar için tatlı nokta: 0,6B'den belirgin " +
                "şekilde daha iyi akıl yürütür, 4B'nin RAM baskısını getirmez. " +
                "6 GB RAM'li bir telefonda ilk denenecek model.",
        ),
        LocalModelSpec(
            id = "qwen3-1.7b",
            displayName = "Qwen3 1.7B (tam)",
            family = "Qwen3",
            quantization = "standart",
            fileName = "Qwen3_1.7B.litertlm",
            downloadUrl = QWEN_1_7B_BASE + "Qwen3_1.7B.litertlm",
            sizeBytes = 2_056_729_520L,
            sha256 = "66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-1.7B/blob/main/LICENSE",
            recommendedRamGb = 6,
            supportsThinkingToggle = true,
            note = "1,7B'nin sıkıştırılmamış hâli; aynı modelden daha tutarlı " +
                "yanıt, karşılığında iki katı indirme ve daha fazla RAM.",
        ),
        LocalModelSpec(
            id = "gemma4-e2b",
            displayName = "Gemma 4 E2B (uç-cihaz)",
            family = "Gemma 4",
            quantization = "int4",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = GEMMA4_E2B_BASE + "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/google/gemma-4-E2B-it",
            recommendedRamGb = 6,
            supportsThinkingToggle = true,
            note = "2B etkin parametre; telefon için optimize edilmiş uç-cihaz " +
                "sürümü. E4B'nin küçük kardeşi — 8 GB'a çıkamayan cihazlarda " +
                "Gemma 4 kalitesine en yakın seçenek. Lisans onayı gerektirmez.",
        ),
        LocalModelSpec(
            id = "qwen3.5-0.8b-int8",
            displayName = "Qwen3.5 0.8B (int8)",
            family = "Qwen3.5",
            quantization = "int8",
            fileName = "Qwen3.5-0.8B_int8.litertlm",
            downloadUrl = QWEN_3_5_0_8B_BASE + "Qwen3.5-0.8B_int8.litertlm",
            sizeBytes = 978_249_216L,
            sha256 = "d1bc816351be3862c16b443bcabf6ba81efcba7507d45429e5571bd2861a966c",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3.5-0.8B",
            recommendedRamGb = 5,
            // Qwen3.5 yeni bir mimari (hybrid / gated-deltanet). Qwen3'ün
            // enable_thinking değişkenini desteklediği DOĞRULANMADI, o yüzden
            // desteklendiği iddia edilmiyor.
            supportsThinkingToggle = false,
            note = "Katalogdaki en yeni kuşak (Qwen3.5, Ağustos 2026). 0,8 milyar " +
                "parametreye göre beklenenin üstünde iş çıkarır. int8 olduğu için " +
                "1,7B int4 ile benzer boyuttadır; hangisinin cihazınızda daha iyi " +
                "olduğunu Modeller ekranındaki tok/sn ölçümüyle karşılaştırın.",
        ),
        // ——— Aynı 4B modelinin uzmanlaşmış iki sürümü. Genel amaçlı Qwen3 4B
        // aşağıda; bunlar tek bir işi daha iyi yapmak için ayrıştırılmıştır.
        LocalModelSpec(
            id = "qwen3-4b-instruct-2507",
            displayName = "Qwen3 4B Instruct 2507 (int4)",
            family = "Qwen3",
            quantization = "mixed int4",
            fileName = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            downloadUrl = QWEN_4B_INSTRUCT_2507_BASE +
                "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            sizeBytes = 2_659_057_664L,
            sha256 = "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507/blob/main/LICENSE",
            recommendedRamGb = 8,
            // Bu sürüm bilerek düşünmez; talimat izlemeye ayarlanmıştır.
            supportsThinkingToggle = false,
            note = "Doğrudan yanıt veren 4B sürümü: düşünme adımı yoktur, bu yüzden " +
                "ilk kelimeye kadar geçen süre kısadır. Soru-cevap, çeviri ve " +
                "özetleme gibi günlük işler için Düşünen sürümden daha uygundur.",
        ),
        LocalModelSpec(
            id = "qwen3-4b-thinking-2507",
            displayName = "Qwen3 4B Düşünen 2507 (int4)",
            family = "Qwen3",
            quantization = "dynamic int4",
            fileName = "Qwen3_4b_thinking_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = QWEN_4B_THINKING_2507_BASE +
                "Qwen3_4b_thinking_dynamic_wi4b32_afp32.litertlm",
            sizeBytes = 2_274_193_168L,
            sha256 = "da668b38f27e93f0e40a1d9efd0930ba77fff28a3067547232c93a2001e1c714",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-4B-Thinking-2507",
            recommendedRamGb = 8,
            // Düşünme bu modelde KAPATILAMAZ; anahtar sunmak yanıltıcı olurdu.
            supportsThinkingToggle = false,
            note = "Akıl yürütmeye ayrılmış 4B sürümü: yanıt vermeden önce her zaman " +
                "düşünür ve bu kapatılamaz. Matematik, mantık ve çok adımlı " +
                "problemlerde daha iyi, günlük sohbette daha yavaştır.",
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
            note = "8 GB RAM'li telefonlar için güçlü genel amaçlı model. " +
                "Uzun metin ve çok adımlı akıl yürütmede 1,7B'nin belirgin " +
                "üstünde; ilk yükleme birkaç saniye sürer.",
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
        // ——— int8 quantization'lar. AYNI depo, AYNI sabit revizyon — yalnız
        // farklı dosya. int4'e göre belirgin daha iyi kalite, karşılığında
        // ~2 kat indirme ve RAM. 14B'nin int8'i (14,9 GB) bilerek eklenmedi:
        // hiçbir telefonda çalışmaz, listede yer kaplamasının anlamı yok.
        LocalModelSpec(
            id = "qwen3-4b-int8",
            displayName = "Qwen3 4B (int8)",
            family = "Qwen3",
            quantization = "channelwise int8",
            fileName = "qwen3_4b_channelwise_int8_float32kv.litertlm",
            downloadUrl = QWEN_4B_BASE + "qwen3_4b_channelwise_int8_float32kv.litertlm",
            sizeBytes = 5_672_370_176L,
            sha256 = "3a44aeffa0a3b02b453215f77440069f98efc015c600cb6520b2a01109f4c063",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-4B/blob/main/LICENSE",
            recommendedRamGb = 16,
            supportsThinkingToggle = true,
            note = "4B'nin int8 sürümü: int4'e göre daha tutarlı ve doğru yanıt " +
                "verir, ama iki katından fazla yer kaplar ve 16 GB RAM ister. " +
                "Yalnız amiral gemisi cihazlarda anlamlıdır; önce int4 sürümünü " +
                "deneyip yetmezse buna geçin.",
        ),
        LocalModelSpec(
            id = "qwen3-8b-int8",
            displayName = "Qwen3 8B (int8)",
            family = "Qwen3",
            quantization = "channelwise int8",
            fileName = "qwen3_8b_channelwise_int8_float32kv.litertlm",
            downloadUrl = QWEN_8B_BASE + "qwen3_8b_channelwise_int8_float32kv.litertlm",
            sizeBytes = 8_307_720_192L,
            sha256 = "2c24953f2ade203216d6588376dbabcecdd0aebac11667d4c24f1819e418e82c",
            licenseName = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen3-8B/blob/main/LICENSE",
            recommendedRamGb = 24,
            supportsThinkingToggle = true,
            note = "Katalogdaki en yüksek kaliteli seçenek. 8,3 GB indirme ve " +
                "24 GB RAM ister; çoğu telefonda başlatılamaz. Yalnız 24 GB'lık " +
                "cihazlarda ve Wi-Fi üzerinden indirilmelidir.",
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
            note = "Küçük ve hızlı Gemma. Düşünme anahtarı YOKTUR. " +
                "Kapılıdır: önce HF hesabınızda Gemma şartlarını onaylamanız, " +
                "sonra Ayarlar'a HF token'ı girmeniz gerekir. Tokensız bir " +
                "alternatif isterseniz Qwen3 0,6B kullanın.",
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
            note = "Sohbet için değil, ARAÇ ÇAĞIRMA için eğitilmiş model. " +
                "Çevrimdışı araçları (hesap, saat, cihaz durumu, notlar) en " +
                "güvenilir kullanan seçenektir; serbest sohbette zayıftır. " +
                "Kapılıdır: HF lisans onayı + token gerekir.",
        ),
    )

    val default: LocalModelSpec = entries.first()

    fun byId(id: String?): LocalModelSpec? = entries.firstOrNull { it.id == id }
}
