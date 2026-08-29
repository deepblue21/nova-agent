package com.nova.agent.data

/**
 * Kullanıcının sakıncalı bulduğu YAPAY ZEKÂ ÇIKTISI için bildirim — Play B6.
 *
 * Play'in "AI-Generated Content" politikası şunu şart koşuyor: *"Apps that
 * generate content using AI must contain in-app user reporting or flagging
 * features that allow users to report or flag offensive content to developers
 * without needing to exit the app."*
 *
 * "Uygulamadan ÇIKMADAN" kısmı belirleyici: bildirimin tamamlanması için
 * e-posta uygulamasına ya da tarayıcıya atmak yeterli değil. Bu yüzden akış
 * cihazda kapanıyor — kullanıcı sebebi seçiyor, kaydediliyor, onay görüyor.
 *
 * Gönderim ayrı ve İSTEĞE BAĞLI bir adım: NOVA'nın "istemler telefondan
 * çıkmaz" sözü var, dolayısıyla bildirimi (ve içindeki sohbet parçasını)
 * arkamızdan sunucuya yollayamayız. Kullanıcı ne gönderdiğini görerek,
 * Ayarlar'dan kendisi paylaşır.
 *
 * [excerpt] tam mesaj değil, sınırlı bir alıntıdır: bildirimin ne hakkında
 * olduğunu anlatmaya yeter, gereksiz veri taşımaz.
 */
data class ContentReport(
    val id: String,
    val createdAt: Long,
    val reason: ContentReportReason,
    val note: String = "",
    val excerpt: String = "",
    /** Yanıtı hangi motor üretti (telefon modeli / gateway). Moderasyon için. */
    val route: String = "",
) {
    companion object {
        /** Alıntı üst sınırı — bildirim dosyası sohbet arşivine dönüşmesin. */
        const val MAX_EXCERPT = 400

        fun excerptOf(content: String): String =
            content.trim().take(MAX_EXCERPT)
    }
}

/** Play politikasının saydığı sakıncalı içerik türlerine karşılık gelir. */
enum class ContentReportReason(val id: String, val label: String) {
    HARMFUL("harmful", "Zararlı ya da tehlikeli"),
    HATE("hate", "Nefret söylemi veya taciz"),
    SEXUAL("sexual", "Cinsel içerik"),
    VIOLENCE("violence", "Şiddet"),
    MISINFORMATION("misinformation", "Yanıltıcı bilgi"),
    OTHER("other", "Diğer");

    companion object {
        fun fromId(id: String?): ContentReportReason =
            entries.firstOrNull { it.id == id } ?: OTHER
    }
}
