package com.nova.agent.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Panoya kopyalama — uygulamadaki TEK yol.
 *
 * ### Neden tek yazma
 *
 * Android 13'ten (API 33) beri sistem, panoya bir şey kopyalandığında içeriğin
 * ÖNİZLEMESİNİ ekranda gösteren bir baloncuk açar. Gateway anahtarı (`nv_…`)
 * arayüzde maskeleniyor, ama "Kopyala"ya basıldığı anda sistem onu düz metin
 * gösteriyordu.
 *
 * İlk düzeltme bunu KAPATMIYORDU: Compose'un panosuna yazıp hemen ardından
 * işaretli clip'le üzerine yazıyordu. İki ayrı `setPrimaryClip` çağrısı demek,
 * **birincisi işaretsiz** demek — önizleme o anda zaten anahtarı göstermiş
 * oluyordu. İkinci yazma yalnızca ekrandaki baloncuğu tazeliyor, olan biteni
 * geri almıyor. Pencereyi daraltmak sızıntıyı kapatmaz.
 *
 * Doğrusu tek çağrı: clip, `EXTRA_IS_SENSITIVE` işareti ÜZERİNDEYKEN yazılır,
 * dolayısıyla sistemin gördüğü ilk ve tek hâli maskelenmiş hâlidir.
 *
 * API 33 altında böyle bir önizleme yok; işaret de yok sayılır.
 */
internal fun copyToClipboard(
    context: Context,
    text: String,
    label: String = "NOVA",
    sensitive: Boolean = false,
) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
    }
}

/**
 * Kopyalama işini bir fonksiyon olarak verir.
 *
 * Çağıran bileşenlere Android arayüzü yerine sade bir `(String) -> Unit`
 * geçiyoruz. Bu yalnız sadelik değil: testlerde `ClipboardManager`'ı taklit
 * etmek, Kotlin'in yedek alandan ürettiği `getText()/setText()` imzaları o
 * arayüzünkilerle çakıştığı için "platform declaration clash" veriyordu ve
 * enstrumanlı test kaynağı bu yüzden uzun süre HİÇ derlenmemişti. Fonksiyon
 * tipinde böyle bir tuzak yok.
 */
@Composable
internal fun rememberClipboardCopy(sensitive: Boolean = false): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, sensitive) {
        { text: String -> copyToClipboard(context, text, sensitive = sensitive) }
    }
}
