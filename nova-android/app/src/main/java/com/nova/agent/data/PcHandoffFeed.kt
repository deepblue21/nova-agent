package com.nova.agent.data

/**
 * Kontrol ekranındaki "PC koşumları" kartının SAF sunum mantığı — Faz 11.
 *
 * Neden ayrı dosya: buradaki kararların hepsi ("ne zaman gösterilir",
 * "boş liste ne anlama gelir", "zaman nasıl yazılır") JVM'de test edilebilir
 * olmalı. Compose içine gömülseydi yalnız cihazda doğrulanabilirdi ve bu
 * projede cihaz testleri hiç koşmadı.
 *
 * Kartın tek işi devri GÖRÜNÜR kılmak: telefondan "PC'ye devret" denince iş
 * PC'ye gidiyor, yanıt sohbet balonunda kalıyor ve uygulama kapanınca devrin
 * hiçbir izi kalmıyordu.
 */
object PcHandoffFeed {

    const val TITLE: String = "PC KOŞUMLARI"

    /** Kartta en fazla kaç satır gösterilir; gerisi web arayüzünde. */
    const val MAX_ROWS: Int = 5

    /**
     * Kart gösterilsin mi.
     *
     * Çevrimdışı politikada PC yolu tümüyle kapalıdır; orada koşum geçmişi
     * göstermek olmayan bir yeteneği varmış gibi sunardı.
     */
    fun shouldShow(gatewayRelevant: Boolean): Boolean = gatewayRelevant

    /**
     * Gösterilecek satırlar: en yeniden eskiye, [MAX_ROWS] ile sınırlı.
     *
     * Gateway zaten `created_at DESC` sıralı gönderiyor; yine de burada
     * sıralıyoruz — sıralamayı sunucunun sözüne bırakmak, sözü değişince
     * sessizce yanlış sıra demektir. Tarihi olmayan (0) satırlar sona düşer.
     */
    fun visibleRuns(runs: List<PcAgentRun>): List<PcAgentRun> =
        runs.sortedByDescending { it.createdAt }.take(MAX_ROWS)

    /**
     * Liste yerine gösterilecek durum metni; liste doluysa `null`.
     *
     * Üç durum ayrı ayrı konuşur. "Ulaşılamadı" ile "hiç koşum yok" aynı
     * cümleye düşerse kullanıcı işini kaybettiğini sanar.
     */
    fun emptyMessage(runs: List<PcAgentRun>?, loading: Boolean): String? = when {
        runs != null && runs.isNotEmpty() -> null
        loading -> "Geçmiş okunuyor…"
        runs == null -> "Geçmiş okunamadı. PC bağlantısını kontrol et."
        else -> "Henüz PC'ye devredilmiş iş yok. Sohbette bir yanıtı PC'ye devrettiğinde burada görünür."
    }

    /** Satırdaki tür rozeti; boşsa rozet çizilmez. */
    fun badge(run: PcAgentRun): String = when (run.mode) {
        "openclaw" -> "telefondan"
        "team" -> "takım"
        "agent" -> "araçlı"
        else -> run.mode
    }

    /**
     * Satır başlığı. Gateway istemi 2000 karaktere kadar saklıyor; kartta
     * tek satır yeter. Boş istem uydurulmaz, açıkça "(boş istem)" yazılır.
     */
    fun title(run: PcAgentRun): String {
        val line = run.prompt.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isEmpty()) return "(boş istem)"
        return if (line.length <= 72) line else line.take(71).trimEnd() + "…"
    }

    /**
     * Göreli zaman. [createdAt] 0 ise boş döner (uydurma tarih yazılmaz);
     * gelecekteki bir damga da "az önce" sayılır — saat farkı yüzünden
     * "-3 dk" yazmaktansa.
     */
    fun relativeTime(createdAt: Long, now: Long): String {
        if (createdAt <= 0L) return ""
        val diff = now - createdAt
        if (diff < 60_000L) return "az önce"
        val minutes = diff / 60_000L
        if (minutes < 60L) return "$minutes dk"
        val hours = minutes / 60L
        if (hours < 24L) return "$hours sa"
        val days = hours / 24L
        return if (days < 7L) "$days gün" else "${days / 7L} hafta"
    }
}
