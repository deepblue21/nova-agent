package com.nova.agent.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Panoya kopyalanan gizli değeri **hassas** olarak işaretler.
 *
 * Android 13'ten (API 33) beri sistem, panoya bir şey kopyalandığında ekranın
 * altında içeriğin ÖNİZLEMESİNİ gösteren bir baloncuk açar. Gateway anahtarı
 * (`nv_…`) arayüzde özenle maskeleniyor, "Göster" düğmesiyle açılıyor — ama
 * "Kopyala"ya basıldığı anda sistem onu düz metin olarak ekranda gösteriyordu.
 * Maskeleme bu noktada anlamsızlaşıyor: omuz üstünden bakan biri, ekran kaydı
 * ya da paylaşılan ekran anahtarı okuyabilir.
 *
 * `EXTRA_IS_SENSITIVE` işaretiyle sistem önizlemede içerik yerine "•••" gösterir.
 * API 33 altında böyle bir baloncuk yok, dolayısıyla yapılacak bir şey de yok.
 *
 * Compose'un `ClipboardManager`'ı bu bayrağı taşıyamıyor; bu yüzden aynı değer
 * platform panosuna işaretli olarak YENİDEN yazılır — son yazan kazanır.
 */
internal fun markClipboardSensitive(context: Context, label: String, value: String) {
    if (Build.VERSION.SDK_INT < 33) return
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        manager.setPrimaryClip(clip)
    }
}
