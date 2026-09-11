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
/**
 * Şu anda PC'de çalışan devir — Faz 11B.
 *
 * Devir sırasında Kontrol ekranı "Sohbet yanıtı üretiliyor…" diyordu. Bu
 * yanlıştı: iş sohbette değil, PC'de çalışıyor. Kullanıcının o anda sorduğu
 * soru "gönderdim, ne oluyor?" ve ekranda bunun cevabı yoktu.
 *
 * [responding] ilk parça geldiğinde true olur: "PC aldı, düşünüyor" ile
 * "PC yazmaya başladı" kullanıcı için farklı iki durumdur.
 */
data class PcHandoffInFlight(
    val prompt: String,
    val startedAt: Long,
    val responding: Boolean = false,
)

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
    fun title(run: PcAgentRun): String = promptTitle(run.prompt)

    /**
     * İstem metninden tek satırlık başlık. Süren devir henüz bir [PcAgentRun]
     * değil (sunucuda satırı yok), ama aynı başlığı göstermeli — bu yüzden
     * kural istemin kendisi üzerinden tanımlı.
     */
    fun promptTitle(prompt: String): String {
        val line = prompt.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isEmpty()) return "(boş istem)"
        return if (line.length <= 72) line else line.take(71).trimEnd() + "…"
    }

    /**
     * Süren devrin durum cümlesi — Faz 11B.
     *
     * İki ayrı durum iki ayrı cümle: PC istemi aldı ama henüz bir şey
     * yazmadı / PC yanıt yazmaya başladı. Aradaki fark, kullanıcının
     * "takıldı mı?" sorusunun cevabıdır.
     */
    fun inFlightLabel(run: PcHandoffInFlight): String =
        if (run.responding) "PC yanıt yazıyor" else "PC'ye gönderildi, çalışıyor"

    /**
     * Süren iş için geçen süre. Saniye çözünürlüğü: burada dakikalık
     * yuvarlama bilgi vermez — kullanıcı işin ilerlediğini saniyeden görür.
     * Geçmişteki koşumlar için [relativeTime] kullanılır, o ayrı bir soru.
     */
    fun elapsed(startedAt: Long, now: Long): String {
        if (startedAt <= 0L) return ""
        val seconds = ((now - startedAt).coerceAtLeast(0L)) / 1000L
        if (seconds < 60L) return "$seconds sn"
        val minutes = seconds / 60L
        val rest = seconds % 60L
        return if (rest == 0L) "$minutes dk" else "$minutes dk $rest sn"
    }

    /**
     * Durdurma düğmesinin metni ve altındaki uyarı.
     *
     * DÜRÜSTLÜK: telefon akışı keser, gateway de PC'ye giden isteği abort
     * eder — ama PC'deki ajanın gerçekten durduğu GARANTİ DEĞİLDİR; bu
     * OpenClaw'ın isteği yarıda kesilince ne yaptığına bağlı ve gateway
     * bunu bilmiyor. "İptal et" demek, bilmediğimiz bir şeyi vaat etmek olur.
     */
    const val STOP_LABEL: String = "Dinlemeyi durdur"
    const val STOP_NOTE: String =
        "Akış kesilir ve PC'ye giden istek iptal edilir. PC'deki ajanın işi " +
            "gerçekten bırakıp bırakmadığını uygulama göremez."

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
