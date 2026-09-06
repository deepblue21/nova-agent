package com.nova.agent.llm.local

/**
 * İndirme işinin **kimliği ve sözleşmesi** — saf, Android'siz, JVM'de test edilebilir.
 *
 * Worker'dan ayrı durmasının nedeni: `ModelDownloadWorker` bir
 * `CoroutineWorker`, yani sınıfı yüklemek WorkManager'ı da yüklüyor. Etiket
 * biçimi gibi tamamen saf bir kural için birim testin Android'e uzanması
 * gerekmemeli. Projedeki `DownloadPreflight`, `FirstRunGuide`,
 * `MobileTaskReducer` de aynı ayrımı izliyor.
 */
object ModelDownloadJob {

    /** Tüm model indirme işleri bu etiketi taşır; arayüz bununla izler. */
    const val TAG = "model-download"

    const val KEY_MODEL_ID = "model_id"
    const val KEY_HF_TOKEN = "hf_token"
    const val KEY_BYTES = "bytes"
    const val KEY_ERROR = "error"
    const val KEY_CANCELLED = "cancelled"

    /** Model başına tek iş: aynı model iki kez indirilmeye çalışılamaz. */
    fun uniqueName(modelId: String): String = "$TAG:$modelId"

    /**
     * Model kimliğini işin ETİKETİNE yazar.
     *
     * Neden gerekli: WorkManager, iş uçtaki bir duruma geçtiğinde `progress`i
     * TEMİZLER ve iptal edilen bir işin `outputData`'sı boştur (Worker sonuç
     * döndürmeye fırsat bulamaz). Yalnız o ikisine bakan gözlemci `CANCELLED`
     * durumunda hangi modelden söz edildiğini bulamıyordu; o satır arayüzde
     * "indiriliyor" olarak asılı kalıyor, kullanıcı ne iptali görebiliyor ne
     * yeniden başlatabiliyordu (süreç yeniden başlayana kadar).
     *
     * Etiketler işin ömrü boyunca değişmez — güvenilir kaynak onlar.
     *
     * Önek bilerek `uniqueName`inkinden (`model-download:`) farklı: benzersiz
     * iş adı etiket kümesine sızarsa yanlışlıkla eşleşmesin.
     */
    private const val MODEL_TAG_PREFIX = "$TAG/model="

    fun modelTag(modelId: String): String = MODEL_TAG_PREFIX + modelId

    fun modelIdFromTags(tags: Set<String>): String? = tags
        .firstOrNull { it.startsWith(MODEL_TAG_PREFIX) }
        ?.removePrefix(MODEL_TAG_PREFIX)
        ?.takeIf { it.isNotEmpty() }
}
