package com.nova.agent.llm.local

/**
 * "Yerel düşünme" anahtarının seçili modelde ne durumda olduğu — **SAF**,
 * JVM'de test edilebilir.
 *
 * Neden ayrı bir dosya: anahtar uzun süre modelden bağımsız çiziliyordu.
 * Katalogda yalnız Qwen3 varken bu göze batmıyordu; katalog 16 modele
 * çıkınca **düşünmeyi desteklemeyen altı model** oluştu (Granite, Gemma 3 1B,
 * FunctionGemma, Qwen3.5 0.8B ve iki 2507 sürümü). Desteklenmeyen bir anahtarı
 * açıkmış gibi göstermek, projenin "desteklenmeyen özellik taklit edilmez;
 * pasif gösterilir ve nedeni açıklanır" değişmezini ihlal eder.
 */
object LocalThinkingSupport {

    /**
     * Anahtarın çizim durumu.
     *
     * [interactive] false ise anahtar **gizlenmez**, pasif çizilir: kaybolup
     * ortaya çıkan kontrol, sabit ama pasif bir kontrolden daha kafa karıştırıcıdır.
     */
    data class Ui(
        val interactive: Boolean,
        val title: String,
        val explanation: String,
    )

    const val SUPPORTED_EXPLANATION: String =
        "Modelin gerçek enable_thinking anahtarı: Açık/Kapalı. " +
            "Kademeli seviye bu motorda yok."

    fun forModel(spec: LocalModelSpec?): Ui {
        if (spec == null) {
            return Ui(
                interactive = false,
                title = "Yerel düşünme",
                explanation = "Önce Modeller listesinden bir model seçin.",
            )
        }
        if (spec.supportsThinkingToggle) {
            return Ui(
                interactive = true,
                title = "Yerel düşünme (${spec.family})",
                explanation = SUPPORTED_EXPLANATION,
            )
        }
        return Ui(
            interactive = false,
            title = "Yerel düşünme (${spec.family})",
            explanation = "${spec.displayName} bu anahtarı desteklemiyor; yanıt " +
                "biçimi modelin kendi davranışına bağlıdır. Ayrıntı için model " +
                "kartındaki açıklamaya bakın. Anahtarı olan bir model seçerseniz " +
                "burası kendiliğinden etkinleşir.",
        )
    }
}
