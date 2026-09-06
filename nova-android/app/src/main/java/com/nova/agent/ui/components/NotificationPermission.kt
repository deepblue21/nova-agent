package com.nova.agent.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Bildirim iznini **bağlamında** ister ve isteği beklemez.
 *
 * ### Neden gerekliydi
 *
 * Model indirmesi WorkManager'a taşınırken manifest'e `POST_NOTIFICATIONS`
 * eklendi ama çalışma zamanında **hiçbir yerde istenmedi**. Android 13'ten
 * (API 33) beri bu izin varsayılan olarak REDDEDİLİ olduğundan sonuç şuydu:
 *
 * - `setForeground` sessizce başarısız oluyordu (`setForegroundSafely` yutuyor),
 * - kullanıcı "İndir"e basıp 0,5-8,6 GB'lık bir indirme başlatıyor ama
 *   **hiçbir ilerleme görmüyordu**,
 * - bildirimdeki "İptal" düğmesi hiç var olmadığı için indirmeyi durdurmanın
 *   tek yolu uygulamayı açık tutmaktı.
 *
 * Yani indirmeyi arka plana taşımanın kazandırdığı şeyin yarısı kayıptı.
 *
 * ### Neden beklemeden
 *
 * İzin kararı indirmeyi ENGELLEMEZ. Kullanıcı reddederse indirme yine sürer,
 * yalnız bildirim görünmez — worker'ın zaten varsaydığı davranış budur.
 * Sistem penceresi açıkken iş kuyruğa girer; kullanıcı izin verirse worker'ın
 * 1,5 saniyede bir yenilenen `setForegroundAsync` çağrısı bildirimi kendiliğinden
 * getirir.
 *
 * API 33 altında böyle bir izin yok; çağrı sessizce hiçbir şey yapmaz.
 */
@Composable
internal fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Sonuç ne olursa olsun indirme sürer; burada yapılacak bir şey yok. */ }

    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission(context)
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
