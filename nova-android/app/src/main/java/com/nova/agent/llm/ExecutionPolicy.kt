package com.nova.agent.llm

/**
 * Yürütme politikası. Varsayılan GATEWAY_ONLY'dir: mevcut kurulumlar
 * kendiliğinden telefona geçirilmez, davranış bire bir korunur.
 *
 * LOCAL_ONLY (Çevrimdışı, Faz 2) artık seçilebilir: istekler yalnız
 * telefonda çalışır ve hiçbir koşulda PC/buluta devredilmez.
 * HYBRID (Faz 3) tamamlanana kadar arayüzde pasiftir; taklit edilmez.
 */
enum class ExecutionPolicy(val id: String, val label: String) {
    GATEWAY_ONLY("gateway_only", "PC / Gateway"),
    LOCAL_FIRST("local_first", "Yerel öncelikli"),
    LOCAL_ONLY("local_only", "Çevrimdışı"),
    HYBRID("hybrid", "Hibrit");

    /** Bu sürümde seçilebilir politikalar; HYBRID Faz 3'te açılacak. */
    val selectableNow: Boolean
        get() = this != HYBRID

    /** İstek telefonda mı çalışır. */
    val runsOnDevice: Boolean
        get() = this == LOCAL_FIRST || this == LOCAL_ONLY

    /** Yerel hata sonrası PC'ye izinli devir önerilebilir mi. */
    val allowsGatewayFallback: Boolean
        get() = this != LOCAL_ONLY

    companion object {
        fun fromId(id: String?): ExecutionPolicy =
            entries.firstOrNull { it.id == id } ?: GATEWAY_ONLY
    }
}

/** Bir sohbet isteminin nereye gideceği kararı. Saf ve test edilebilir. */
sealed interface RouteDecision {
    /** Mevcut Gateway yolu (NovaClient) — hiç değişmedi. */
    data object Gateway : RouteDecision

    /** Telefondaki kurulu modelle üretim. */
    data class Local(val modelId: String) : RouteDecision

    /**
     * Yerel politika seçili ama telefonda kullanılabilir model yok.
     * Sessiz devir YASAK: kullanıcıya gerekçe gösterilir, izin istenir.
     */
    data class LocalNeedsSetup(val reason: String) : RouteDecision
}

object EngineRouter {
    fun decide(
        policy: ExecutionPolicy,
        localModelId: String,
        localModelInstalled: Boolean,
    ): RouteDecision = when (policy) {
        ExecutionPolicy.GATEWAY_ONLY -> RouteDecision.Gateway

        ExecutionPolicy.LOCAL_FIRST, ExecutionPolicy.LOCAL_ONLY ->
            if (localModelInstalled) {
                RouteDecision.Local(localModelId)
            } else {
                RouteDecision.LocalNeedsSetup(
                    if (policy == ExecutionPolicy.LOCAL_ONLY) {
                        "Çevrimdışı mod için telefonda kurulu model gerekli. " +
                            "Modeller sekmesinden bir model indirin."
                    } else {
                        "Telefonda kurulu model yok. Modeller sekmesinden bir model indirin."
                    },
                )
            }

        // Faz 3'e kadar HYBRID arayüzden seçilemez; yine de görülürse güvenli taraf Gateway'dir.
        ExecutionPolicy.HYBRID -> RouteDecision.Gateway
    }
}
