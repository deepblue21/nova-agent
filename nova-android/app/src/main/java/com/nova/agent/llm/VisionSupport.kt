package com.nova.agent.llm

import com.nova.agent.llm.local.LocalModelSpec

/**
 * Bir görselin NEREYE gidebileceğine karar verir — Faz 12A. SAF, JVM'de testli.
 *
 * Buradaki tek kural şu: **görsel, onu gerçekten görebilen bir yere gider ya da
 * hiç gitmez.** Sessizce düşürülmesi en kötü sonuç olurdu — kullanıcı ekranda
 * küçük resmi görür, model resmi hiç almamıştır ve gelen yanıt kibar bir
 * uydurma olur. Bu, projedeki "PC devri kaydolur" yalanının aynısı olurdu.
 *
 * İkinci kural: **çevrimdışı politikada görsel telefondan ÇIKMAZ.** Kullanıcı
 * Çevrimdışı'yı seçtiyse bu bir gizlilik sözüdür; "model göremiyor, PC'ye
 * gönderelim mi?" diye sormak o sözü bozardı.
 */
object VisionSupport {

    /** Görselin gideceği yer ya da gidemeyeceğinin nedeni. */
    sealed interface Target {
        /** Telefondaki model görebiliyor; istem cihazdan çıkmaz. */
        data class OnDevice(val modelName: String) : Target

        /** PC Gateway'e gider. */
        data object Gateway : Target

        /**
         * Hiçbir yere gidemez. [reason] kullanıcıya gösterilir;
         * [suggestsModelDownload] true ise çözüm bir model indirmektir.
         */
        data class Unsupported(
            val reason: String,
            val suggestsModelDownload: Boolean = false,
        ) : Target
    }

    /**
     * Şu anki hedefe göre görselin nereye gideceği.
     *
     * @param localSpec telefonda seçili model (kurulu olmayabilir)
     * @param localInstalled o model gerçekten kurulu ve kullanılabilir mi
     * @param gatewayReady PC bağlantısı hazır mı
     * @param gatewayModelSeesImages seçili PC modeli görsel alabiliyor mu
     */
    fun decide(
        policy: ExecutionPolicy,
        localSpec: LocalModelSpec?,
        localInstalled: Boolean,
        gatewayReady: Boolean,
        gatewayModelSeesImages: Boolean,
    ): Target {
        val deviceSees = localInstalled && localSpec?.supportsVision == true

        if (policy == ExecutionPolicy.LOCAL_ONLY) {
            // Çevrimdışı: PC hiçbir koşulda önerilmez.
            return if (deviceSees) {
                Target.OnDevice(localSpec!!.displayName)
            } else {
                Target.Unsupported(
                    "Çevrimdışı modda görseli yalnız telefondaki model okuyabilir ve " +
                        "seçili model görsel göremiyor. Modeller sekmesinden görsel " +
                        "okuyabilen bir model indirebilirsin.",
                    suggestsModelDownload = true,
                )
            }
        }

        if (policy.runsOnDevice && deviceSees) return Target.OnDevice(localSpec!!.displayName)

        if (gatewayReady && gatewayModelSeesImages) return Target.Gateway

        if (gatewayReady) {
            return Target.Unsupported(
                "Seçili PC modeli görsel almıyor. Sohbet başlığından görsel " +
                    "okuyabilen bir model seç.",
            )
        }

        return Target.Unsupported(
            "Görseli okuyabilecek bir hedef yok: telefondaki model görsel görmüyor " +
                "ve PC bağlantısı hazır değil.",
            suggestsModelDownload = true,
        )
    }

    /**
     * Cihaz-üstü yola gönderilecek görsel için en uzun kenar (piksel).
     *
     * Telefon modelinin görü kodlayıcısı görseli zaten küçük bir ızgaraya
     * indiriyor; 12 MP'lik bir fotoğrafı olduğu gibi vermek yalnız RAM yakar.
     * Sınır cömert tutuldu: metin okunabilirliği için 768 gerçekçi bir taban.
     */
    const val MAX_EDGE_PX: Int = 768

    /** JPEG yeniden kodlama kalitesi; 85 görsel kayıpla boyut arasında denge. */
    const val JPEG_QUALITY: Int = 85
}
