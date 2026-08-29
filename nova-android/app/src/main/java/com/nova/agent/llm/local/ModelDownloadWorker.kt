package com.nova.agent.llm.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Model indirmesini uygulamanın yaşam döngüsünden KOPARAN iş (K2).
 *
 * Çözdüğü sorun: indirme `viewModelScope`ta koşuyordu. Kullanıcı uygulamayı
 * son kullanılanlardan kaydırdığında Activity bitiyor, ViewModel temizleniyor
 * ve `shutdown()` indirmeyi iptal ediyordu — 0,5-8,6 GB'lık bir indirme, hiçbir
 * bildirim gösterilmeden sessizce ölüyordu. "İndir"e basıp telefonu cebine
 * koyan herkes başarısız oluyordu.
 *
 * Şimdi: WorkManager işi + ön plan bildirimi. Uygulama kapansa da iş sürer,
 * kullanıcı ilerlemeyi bildirimde görür ve oradan iptal edebilir.
 *
 * İndiricinin kendisi (SHA-256 doğrulaması, .part ile sürdürme, atomik kurulum)
 * değişmedi; yalnız nerede koştuğu değişti.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return Result.failure(errorData(null, "Model kimliği verilmedi"))
        val spec = LocalModelCatalog.byId(modelId)
            ?: return Result.failure(errorData(modelId, "Katalogda böyle bir model yok: $modelId"))
        val hfToken = inputData.getString(KEY_HF_TOKEN).orEmpty()

        val store = LocalModelStore(applicationContext)
        val startBytes = (store.diskState(spec) as? LocalModelDiskState.Partial)?.bytes ?: 0L

        setForegroundSafely(spec, startBytes)

        return withContext(Dispatchers.IO) {
            val downloader = ModelDownloader()
            val handle = downloader.newHandle()
            // İş iptal edilince (bildirimden ya da cancelUniqueWork ile) coroutine
            // iptal olur; bloklayan indirme döngüsü bunu ancak handle üzerinden
            // duyar. Bu nöbetçi köprüyü kurar.
            val cancelBridge = launch {
                try {
                    awaitCancellation()
                } finally {
                    handle.cancel()
                }
            }
            try {
                var lastPublish = 0L
                val result = downloader.download(spec, store, handle, hfToken) { bytes, _ ->
                    // İndirici ~200 ms'de bir haber veriyor; her seferinde
                    // WorkManager veritabanına yazmak gereksiz yük olurdu.
                    val now = System.nanoTime()
                    if (now - lastPublish >= PUBLISH_INTERVAL_NS) {
                        lastPublish = now
                        setProgressAsync(progressData(modelId, bytes))
                        setForegroundAsync(foregroundInfo(spec, bytes))
                    }
                }
                when (result) {
                    is ModelDownloader.Result.Success ->
                        Result.success(workDataOf(KEY_MODEL_ID to modelId))
                    is ModelDownloader.Result.Cancelled ->
                        // İptal bir HATA değil: .part korunur, kullanıcı sürdürebilir.
                        Result.success(workDataOf(KEY_MODEL_ID to modelId, KEY_CANCELLED to true))
                    is ModelDownloader.Result.Failure ->
                        Result.failure(errorData(modelId, result.message))
                }
            } finally {
                cancelBridge.cancel()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = inputData.getString(KEY_MODEL_ID)?.let { LocalModelCatalog.byId(it) }
        return foregroundInfo(spec, 0L)
    }

    /**
     * Bildirim izni yoksa `setForeground` atabilir. İndirmenin bu yüzden hiç
     * başlamaması yanlış olurdu: iş yine kossun, yalnız bildirim görünmesin.
     */
    private suspend fun setForegroundSafely(spec: LocalModelSpec, bytes: Long) {
        try {
            setForeground(foregroundInfo(spec, bytes))
        } catch (_: Exception) {
            // Yut: bildirim gösterilemedi, indirme etkilenmez.
        }
    }

    private fun foregroundInfo(spec: LocalModelSpec?, bytes: Long): ForegroundInfo {
        ensureChannel()
        val total = spec?.sizeBytes ?: 0L
        val percent = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(spec?.displayName?.let { "$it indiriliyor" } ?: "Model indiriliyor")
            .setContentText(
                if (total > 0) "%$percent — ${gb(bytes)} / ${gb(total)} GB" else gb(bytes) + " GB",
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, total <= 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal", cancel)

        // API 34+ zorunlu: tür verilmezse setForeground çalışma zamanında patlar.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Model indirme",
                // LOW: uzun süren indirme ses/titreşimle rahatsız etmemeli.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Telefonda çalışacak modellerin indirilmesi" },
        )
    }

    private fun errorData(modelId: String?, message: String) =
        workDataOf(KEY_MODEL_ID to modelId, KEY_ERROR to message)

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_BYTES = "bytes"
        const val KEY_ERROR = "error"
        const val KEY_CANCELLED = "cancelled"

        /** Tüm model indirme işleri bu etiketi taşır; arayüz bununla izler. */
        const val TAG = "model-download"

        private const val CHANNEL_ID = "nova_model_download"
        private const val NOTIFICATION_ID = 4201
        private const val PUBLISH_INTERVAL_NS = 1_500_000_000L // 1,5 sn

        /** Model başına tek iş: aynı model iki kez indirilmeye çalışılamaz. */
        fun uniqueName(modelId: String): String = "$TAG:$modelId"

        fun progressData(modelId: String, bytes: Long) =
            workDataOf(KEY_MODEL_ID to modelId, KEY_BYTES to bytes)

        /** Bayt -> "3,4" gibi tek ondalıklı GB metni. */
        fun gb(bytes: Long): String {
            val value = bytes / 1_073_741_824.0
            return String.format(java.util.Locale.ROOT, "%.1f", value)
        }
    }
}
