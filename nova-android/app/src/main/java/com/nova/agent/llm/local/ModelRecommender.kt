package com.nova.agent.llm.local

/**
 * Cihaz RAM'ine göre model uygunluğu ve öneri — saf ve JVM'de test edilebilir.
 * Amaç: kullanıcıya "bu telefonda hangi model rahat çalışır"ı dürüstçe göstermek
 * ve ilk kurulumda token gerektirmeyen (kapısız) en iyi modeli önermek.
 */
object ModelRecommender {

    enum class Fit(val label: String) {
        COMFORTABLE("Rahat"),
        TIGHT("Sınırlı"),
        RISKY("Riskli"),
        UNKNOWN("Bilinmiyor"),
    }

    /**
     * [deviceRamGb] <= 0 ise ölçülemedi demektir → UNKNOWN.
     * Rahat: RAM ≥ önerilen. Sınırlı: önerilenin 1 GB altına kadar. Altı: Riskli.
     */
    fun fit(spec: LocalModelSpec, deviceRamGb: Double): Fit = when {
        deviceRamGb <= 0.0 -> Fit.UNKNOWN
        deviceRamGb >= spec.recommendedRamGb -> Fit.COMFORTABLE
        deviceRamGb >= spec.recommendedRamGb - 1.0 -> Fit.TIGHT
        else -> Fit.RISKY
    }

    /**
     * Önerilen model. Tercih sırası:
     * 1) Kapısız + rahat çalışanlar arasında EN BÜYÜK (en yetenekli) model.
     * 2) O yoksa kapısız + en az "sınırlı" olanların en büyüğü.
     * 3) O da yoksa kapısız en küçük model (ilk kurulum tokensız kalsın).
     * 4) Katalogda kapısız yoksa listenin ilki.
     * RAM ölçülemediyse (≤0) güvenli taraf: kapısız en küçük.
     */
    fun recommend(
        models: List<LocalModelSpec> = LocalModelCatalog.entries,
        deviceRamGb: Double,
    ): LocalModelSpec {
        val openWeights = models.filter { !it.gated }
        val pool = openWeights.ifEmpty { models }

        if (deviceRamGb <= 0.0) {
            return pool.minByOrNull { it.sizeBytes } ?: models.first()
        }

        val comfortable = pool.filter { fit(it, deviceRamGb) == Fit.COMFORTABLE }
        if (comfortable.isNotEmpty()) return comfortable.maxByOrNull { it.sizeBytes }!!

        val tightOrBetter = pool.filter { fit(it, deviceRamGb) != Fit.RISKY }
        if (tightOrBetter.isNotEmpty()) return tightOrBetter.maxByOrNull { it.sizeBytes }!!

        return pool.minByOrNull { it.sizeBytes } ?: models.first()
    }
}
