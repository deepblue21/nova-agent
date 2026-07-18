package com.nova.agent.llm.local

/**
 * İndirme öncesi boş alan kontrolü — saf ve JVM-testli. Ağ isteği atmadan,
 * cihazda yeterli yer olup olmadığını hesaplar. Sürdürme durumunda yalnız
 * kalan bayt + küçük bir pay gerekir.
 */
object DownloadPreflight {

    /** Kurulum/temp için küçük güvenlik payı (%5). */
    private const val HEADROOM = 1.05

    /** İndirmeyi tamamlamak için gereken en az boş bayt (kalan = boyut - halihazır). */
    fun requiredFreeBytes(sizeBytes: Long, alreadyBytes: Long = 0L): Long {
        val remaining = (sizeBytes - alreadyBytes).coerceAtLeast(0L)
        return (remaining * HEADROOM).toLong()
    }

    fun hasRoom(freeBytes: Long, sizeBytes: Long, alreadyBytes: Long = 0L): Boolean =
        freeBytes >= requiredFreeBytes(sizeBytes, alreadyBytes)

    /** Kullanıcıya gösterilecek dürüst eksik-alan mesajı. */
    fun shortfallMessage(freeBytes: Long, sizeBytes: Long, alreadyBytes: Long = 0L): String {
        val needMb = requiredFreeBytes(sizeBytes, alreadyBytes) / 1_048_576
        val freeMb = freeBytes / 1_048_576
        return "Yetersiz depolama: ~$needMb MB gerekli, $freeMb MB boş. " +
            "Yer açıp tekrar deneyin."
    }
}
