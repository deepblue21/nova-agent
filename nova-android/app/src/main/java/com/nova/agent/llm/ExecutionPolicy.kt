package com.nova.agent.llm

/**
 * Yürütme politikası. Varsayılan GATEWAY_ONLY'dir: mevcut kurulumlar
 * kendiliğinden telefona geçirilmez, davranış bire bir korunur.
 *
 * LOCAL_ONLY (Çevrimdışı): istekler yalnız telefonda çalışır, devir kapalıdır.
 * HYBRID (Faz 3): kısa işler telefonda, uzun işler ve düşük pil PC'de;
 * yerel hata sonrası devir kullanıcı kuralına bağlıdır (sor / otomatik).
 */
enum class ExecutionPolicy(val id: String, val label: String) {
    GATEWAY_ONLY("gateway_only", "PC / Gateway"),
    LOCAL_FIRST("local_first", "Yerel öncelikli"),
    LOCAL_ONLY("local_only", "Çevrimdışı"),
    HYBRID("hybrid", "Hibrit");

    /** Tüm politikalar artık seçilebilir (Faz 3 D1 ile HYBRID de açıldı). */
    val selectableNow: Boolean
        get() = true

    /** İstek (öncelikle) telefonda mı çalışır. */
    val runsOnDevice: Boolean
        get() = this == LOCAL_FIRST || this == LOCAL_ONLY || this == HYBRID

    /** Yerel hata sonrası PC'ye izinli devir önerilebilir mi. */
    val allowsGatewayFallback: Boolean
        get() = this != LOCAL_ONLY

    /**
     * Ses tanımada çevrimdışı paket tercih edilsin mi. Telefonda çalışan
     * politikalarda gizlilik/çevrimdışı tutarlılığı için açıktır; salt-Gateway'de
     * en iyi tanıma için serbest bırakılır.
     */
    val prefersOfflineVoice: Boolean
        get() = runsOnDevice

    companion object {
        fun fromId(id: String?): ExecutionPolicy =
            entries.firstOrNull { it.id == id } ?: GATEWAY_ONLY
    }
}

/** Hibrit karar girdileri; tamamı çağıran tarafça ölçülür, saf kalır. */
data class HybridInputs(
    val localModelInstalled: Boolean,
    val promptChars: Int,
    val batteryPercent: Int, // bilinmiyorsa -1
    val charging: Boolean,
    val gatewayReady: Boolean,
    /** PowerManager THERMAL_STATUS_SEVERE ve üstü (API 29+; bilinmiyorsa false). */
    val thermalSevere: Boolean = false,
    /** İstem hassas görünüyor mu (PrivacyClassifier). Doğruysa cihazda tutulur. */
    val privacySensitive: Boolean = false,
)

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

        // HYBRID için asıl karar decideHybrid'dedir; bu basit yol güvenli tarafta kalır.
        ExecutionPolicy.HYBRID -> RouteDecision.Gateway
    }

    /** Uzun istemler telefonun küçük modeli yerine PC'ye gider. */
    const val LONG_PROMPT_CHARS = 1200

    /** Bu düzeyin altında ve şarjda değilken PC tercih edilir. */
    const val LOW_BATTERY_PERCENT = 20

    /**
     * Hibrit yönlendirme (Faz 3 D1). Kurallar şeffaf ve sabittir:
     * 1) Ne yerel model ne PC hazırsa → kurulum gerekçesi (istek hiçbir yere gitmez).
     * 2) Yerel model yoksa → PC (hibrit seçimi PC kullanımına verilmiş açık rızadır).
     * 3) Gizli görünen istem + telefonda model varsa → telefon (otomatik devre engel).
     * 4) İstem uzunsa ve PC hazırsa → PC.
     * 5) Pil düşük + şarjda değil + PC hazırsa → PC.
     * 6) Cihaz ciddi ısınmışsa + PC hazırsa → PC.
     * 7) Aksi halde → telefon.
     */
    fun decideHybrid(inputs: HybridInputs, localModelId: String): RouteDecision = when {
        !inputs.localModelInstalled && !inputs.gatewayReady ->
            RouteDecision.LocalNeedsSetup(
                "Hibrit: ne telefonda kurulu model var ne PC erişilebilir. " +
                    "Modeller'den model indirin veya Gateway bağlantısını kurun.",
            )

        !inputs.localModelInstalled -> RouteDecision.Gateway

        // Gizlilik önceliği: hassas istem, telefonda model varken PC'ye kaçmaz.
        inputs.privacySensitive -> RouteDecision.Local(localModelId)

        inputs.promptChars >= LONG_PROMPT_CHARS && inputs.gatewayReady -> RouteDecision.Gateway

        // Cihaz ciddi ısınmışsa yükü PC'ye ver (Faz 3 D3).
        inputs.thermalSevere && inputs.gatewayReady -> RouteDecision.Gateway

        inputs.batteryPercent in 0..LOW_BATTERY_PERCENT && !inputs.charging && inputs.gatewayReady ->
            RouteDecision.Gateway

        else -> RouteDecision.Local(localModelId)
    }
}
