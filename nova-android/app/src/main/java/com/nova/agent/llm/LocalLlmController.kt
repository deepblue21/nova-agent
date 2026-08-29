package com.nova.agent.llm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.tool
import com.nova.agent.llm.local.ActiveBackend
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.DownloadPreflight
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.LocalModelStore
import com.nova.agent.llm.local.ModelDownloader
import com.nova.agent.llm.local.ModelMetrics
import com.nova.agent.llm.local.ModelMetricsStore
import com.nova.agent.llm.local.ModelRecommender
import com.nova.agent.llm.local.OnDeviceEngine
import com.nova.agent.llm.local.SamplerSettings
import com.nova.agent.llm.local.tools.DeviceStatusReader
import com.nova.agent.llm.local.tools.HorusToolSet
import com.nova.agent.llm.local.tools.NoteStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nova.agent.llm.local.ModelDownloadWorker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Modeller ekranındaki tek satırın durumu. */
data class LocalModelUi(
    val spec: LocalModelSpec,
    val disk: LocalModelDiskState,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val verifying: Boolean = false,
    val error: String? = null,
)

/** Cihaz motorunun kullanıcıya dönük durumu. */
sealed interface LocalEngineUi {
    data object Idle : LocalEngineUi
    data class Loading(val modelName: String) : LocalEngineUi
    data class Ready(val modelName: String) : LocalEngineUi
    data class Error(val message: String) : LocalEngineUi
}

/**
 * Cihaz-üstü LLM'in tüm yaşam döngüsü: katalog durumu, indirme, doğrulama,
 * motor yükleme ve akışlı üretim. NovaViewModel'e aittir; tüm Compose state
 * mutasyonları [onMain] üzerinden ana thread'de yapılır.
 */
class LocalLlmController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val onMain: (block: () -> Unit) -> Unit,
    /**
     * Bir model indirilip KURULDUĞUNDA çağrılır (K1).
     *
     * Neden gerekli: `recommended` cihaz RAM'ine göre model seçiyor ama
     * `settings.localModelId` sabit kalıyordu ve indirme bitince onu
     * ayarlayan hiçbir yer yoktu. Sonuç: 3,4 GB model iniyor, doğrulanıyor,
     * kullanıcı sohbete yazıyor ve "Telefonda kurulu model yok" görüyordu —
     * üstelik o anda ilk açılış kartı da kaybolduğu için yönlendirme de
     * ortadan kalkıyordu. Tam bir çıkmaz.
     *
     * Aktifleştirme kararını çağıran verir (bkz. NovaViewModel): kullanıcının
     * zaten çalışan bir seçimi varsa onu ELİNDEN ALMAMAK gerekir.
     */
    private val onModelInstalled: (modelId: String) -> Unit = {},
) {
    private val store = LocalModelStore(app)
    private val engine = OnDeviceEngine(app)
    private val workManager = WorkManager.getInstance(app)
    private var activeWatchdog: kotlinx.coroutines.Job? = null

    /**
     * Üretim jetonu — Y6.
     *
     * Her `generate()` çağrısı bunu artırır; `cancelGenerate()` de artırır.
     * `ensureLoaded` saniyeler-dakikalar sürebildiği için dönüşünde "bu istek
     * hâlâ güncel mi" diye SORULMASI gerekir.
     *
     * Eskiden sorulmuyordu: kullanıcı model yüklenirken "Dur"a basıp yeni bir
     * istem gönderdiğinde eski coroutine `ensureLoaded`'dan dönüp koşulsuz
     * `engine.generate()` çağırıyordu. İki üretim aynı Engine/Conversation
     * üzerinde çakışıyor, `cancelled` bayrağı ortak oluyor ve tercih
     * değiştiyse `ensureLoaded` üretim sürerken `unload()` çağırıyordu —
     * native çökme sınıfı.
     */
    @Volatile
    private var generationToken: Long = 0L

    /** Çevrimdışı araç seti (Faz 2 — agentic çekirdek). Ağa çıkmaz, izin istemez. */
    private val noteStore = NoteStore(File(app.filesDir, "horus_notlar.txt"))
    private val toolBelt = HorusToolSet(app, noteStore)

    /** Model performans metrikleri (Faz 4). Cihazda kalır. */
    private val metricsStore = ModelMetricsStore(File(app.filesDir, "model_metrics.json"))

    var models by mutableStateOf(snapshot(emptyList()))
        private set
    var engineState by mutableStateOf<LocalEngineUi>(LocalEngineUi.Idle)
        private set

    /**
     * Motorun gerçekten yüklendiği hızlandırma. "Otomatik" seçiliyken kullanıcı
     * GPU mu CPU mu çalıştığını buradan görür; arayüz tahmin yürütmez.
     */
    var activeBackend by mutableStateOf(ActiveBackend.NONE)
        private set

    /** Model klasörünün kapladığı alan (bayt) ve boş depolama (bayt). */
    var storageUsedBytes by mutableStateOf(0L)
        private set
    var storageFreeBytes by mutableStateOf(0L)
        private set

    /** Model başına son performans ölçümleri (Faz 4). */
    var metrics by mutableStateOf<Map<String, ModelMetrics>>(emptyMap())
        private set

    /** Cihaza göre önerilen model (Faz 4). */
    val recommended: LocalModelSpec get() = ModelRecommender.recommend(deviceRamGb = deviceRamGb)

    init {
        // K2: uygulamaya donuldugunde SUREN indirmeye yeniden baglan.
        observeDownloads()
    }

    /** Cihazın toplam RAM'i (GB, bir ondalık). Modeller ekranında gösterilir. */
    val deviceRamGb: Double = readDeviceRamGb(app)

    // ---------- durum ----------

    fun refresh() {
        models = snapshot(models)
        storageUsedBytes = store.modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
        storageFreeBytes = app.filesDir.usableSpace
        metrics = metricsStore.all()
    }

    fun isInstalled(modelId: String): Boolean {
        val spec = LocalModelCatalog.byId(modelId) ?: return false
        return store.isInstalled(spec)
    }

    fun anyInstalled(): Boolean = LocalModelCatalog.entries.any { store.isInstalled(it) }

    private fun snapshot(previousList: List<LocalModelUi>): List<LocalModelUi> =
        LocalModelCatalog.entries.map { spec ->
            val previous = previousList.firstOrNull { it.spec.id == spec.id }
            LocalModelUi(
                spec = spec,
                disk = store.diskState(spec),
                downloading = previous?.downloading ?: false,
                downloadedBytes = previous?.downloadedBytes ?: 0L,
                verifying = previous?.verifying ?: false,
                error = previous?.error,
            )
        }

    private fun update(modelId: String, transform: (LocalModelUi) -> LocalModelUi) {
        models = models.map { if (it.spec.id == modelId) transform(it) else it }
    }

    // ---------- indirme ----------

    fun startDownload(spec: LocalModelSpec, hfToken: String = "") {
        // Ikinci is acilmasini ExistingWorkPolicy.KEEP zaten engelliyor; burada
        // yalniz arayuzden gelen cift dokunusu susturuyoruz.
        if (models.firstOrNull { it.spec.id == spec.id }?.downloading == true) return
        if (spec.gated && hfToken.isBlank()) {
            // Ağ isteği atmadan dürüst yönlendirme: kapılı model için token şart.
            update(spec.id) {
                it.copy(
                    error = "Kapılı model: önce HF hesabınla lisansı onayla, sonra " +
                        "Ayarlar > Hugging Face bölümüne erişim token'ı gir.",
                )
            }
            return
        }
        val startBytes = (store.diskState(spec) as? LocalModelDiskState.Partial)?.bytes ?: 0L
        // İndirme öncesi yer kontrolü (ağ isteği atmadan).
        val free = app.filesDir.usableSpace
        if (!DownloadPreflight.hasRoom(free, spec.sizeBytes, startBytes)) {
            update(spec.id) {
                it.copy(error = DownloadPreflight.shortfallMessage(free, spec.sizeBytes, startBytes))
            }
            return
        }
        update(spec.id) {
            it.copy(downloading = true, downloadedBytes = startBytes, error = null)
        }
        // K2: indirme artık burada koşmuyor. WorkManager işi olarak kuyruğa
        // giriyor; uygulama kapansa da sürer, ilerlemesi aşağıdaki gözlemciyle
        // arayüze döner. KEEP: aynı model için ikinci bir iş açılmaz.
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to spec.id,
                    ModelDownloadWorker.KEY_HF_TOKEN to hfToken,
                ),
            )
            .addTag(ModelDownloadWorker.TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    // Yer kontrolü yukarıda zaten yapıldı; bu sistem tarafı ek güvence.
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.uniqueName(spec.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload(spec: LocalModelSpec) {
        workManager.cancelUniqueWork(ModelDownloadWorker.uniqueName(spec.id))
    }

    /**
     * İndirme işlerini izler ve arayüzü tazeler (K2).
     *
     * Gözlemci `scope`a (viewModelScope) bağlı, İŞ değil: ViewModel ölünce
     * yalnız izleme durur, indirme sürer. Uygulamaya dönüldüğünde yeni
     * ViewModel yeniden bağlanır ve süren indirmenin ilerlemesini görür.
     */
    private fun observeDownloads() {
        scope.launch {
            workManager.getWorkInfosByTagFlow(ModelDownloadWorker.TAG).collect { infos ->
                infos.forEach { applyWorkInfo(it) }
            }
        }
    }

    private fun applyWorkInfo(info: WorkInfo) {
        val modelId = info.progress.getString(ModelDownloadWorker.KEY_MODEL_ID)
            ?: info.outputData.getString(ModelDownloadWorker.KEY_MODEL_ID)
            ?: return
        val spec = LocalModelCatalog.byId(modelId) ?: return
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                onMain { update(modelId) { it.copy(downloading = true, error = null) } }

            WorkInfo.State.RUNNING -> {
                val bytes = info.progress.getLong(ModelDownloadWorker.KEY_BYTES, -1L)
                onMain {
                    update(modelId) {
                        it.copy(
                            downloading = true,
                            downloadedBytes = if (bytes >= 0) bytes else it.downloadedBytes,
                            error = null,
                        )
                    }
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val cancelled = info.outputData.getBoolean(ModelDownloadWorker.KEY_CANCELLED, false)
                onMain {
                    update(modelId) {
                        it.copy(downloading = false, disk = store.diskState(spec), error = null)
                    }
                    // K1: yalnız gerçekten kurulduysa aktifleştirme önerilir;
                    // iptal edilen indirme "kuruldu" sayılmaz.
                    if (!cancelled && store.isInstalled(spec)) onModelInstalled(modelId)
                }
            }

            WorkInfo.State.FAILED -> {
                val message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                    ?: "İndirme başarısız oldu."
                onMain {
                    update(modelId) {
                        it.copy(
                            downloading = false,
                            disk = store.diskState(spec),
                            error = message,
                        )
                    }
                }
            }

            WorkInfo.State.CANCELLED ->
                onMain {
                    update(modelId) {
                        it.copy(downloading = false, disk = store.diskState(spec))
                    }
                }
        }
    }

    fun deleteModel(spec: LocalModelSpec) {
        cancelDownload(spec)
        scope.launch(Dispatchers.IO) {
            if (engine.loadedModelPath == store.modelFile(spec).absolutePath) engine.unload()
            store.delete(spec)
            onMain {
                update(spec.id) {
                    it.copy(disk = store.diskState(spec), downloading = false, error = null)
                }
                if (engineState !is LocalEngineUi.Idle) engineState = LocalEngineUi.Idle
                activeBackend = ActiveBackend.NONE
            }
        }
    }

    fun verifyModel(spec: LocalModelSpec) {
        update(spec.id) { it.copy(verifying = true, error = null) }
        scope.launch(Dispatchers.IO) {
            val ok = store.verify(spec)
            onMain {
                update(spec.id) {
                    it.copy(
                        verifying = false,
                        disk = store.diskState(spec),
                        error = if (ok) null else "Doğrulama başarısız: dosya bozuk, yeniden indirin.",
                    )
                }
            }
        }
    }

    // ---------- üretim ----------

    /**
     * Yerel akışlı üretim. [history] son kullanıcı mesajı HARİÇ önceki turlar,
     * [prompt] son kullanıcı mesajıdır. [toolsEnabled] true ise konuşma
     * çevrimdışı araç setiyle (saat, hesap, cihaz durumu, notlar) kurulur.
     * Callback'ler ana thread'de teslim edilir.
     */
    fun generate(
        spec: LocalModelSpec,
        history: List<Pair<String, String>>,
        prompt: String,
        thinking: Boolean,
        toolsEnabled: Boolean,
        persona: String = "",
        backend: BackendPreference = BackendPreference.AUTO,
        /** null = motora samplerConfig verilme (model varsayılanı). */
        sampler: SamplerSettings? = null,
        cb: OnDeviceEngine.Callbacks,
    ) {
        val myToken = synchronized(this) { ++generationToken }
        scope.launch(Dispatchers.IO) {
            val file = store.modelFile(spec)
            if (!file.exists() || file.length() != spec.sizeBytes) {
                onMain { cb.onError("Model dosyası eksik. Modeller sekmesinden indirin.") }
                return@launch
            }
            // Y2 — DOĞRULANMAMIŞ dosya native motora VERİLMEZ.
            // `exists() + length()` yetmiyordu: doğrulaması başarısız kalmış ya
            // da hiç doğrulanmamış bir dosya da bu iki koşulu geçiyor ve bozuk
            // .litertlm `Engine.initialize()`'a gidiyordu. Oradaki native çökme
            // Kotlin tarafından yakalanamaz.
            val diskState = store.diskState(spec)
            if (diskState !is LocalModelDiskState.Installed || !diskState.verified) {
                onMain {
                    cb.onError(
                        "Model dosyası doğrulanmadı; bozuk olabilir ve bu hâliyle " +
                            "çalıştırılmaz. Modeller sekmesinden \"Doğrula\", " +
                            "başarısız olursa yeniden indir.",
                    )
                }
                return@launch
            }
            // Yol AYNI ama backend tercihi değiştiyse motor yeniden kurulur;
            // "zaten yüklü" kararı ikisine birden bakmalı, yoksa gerçek bir
            // yeniden yükleme metriklere 0 ms olarak yazılırdı.
            val alreadyLoaded = engine.isLoadedWith(file.absolutePath, backend)
            if (!alreadyLoaded) {
                onMain { engineState = LocalEngineUi.Loading(spec.displayName) }
            }
            val loadStart = System.currentTimeMillis()
            val loaded = engine.ensureLoaded(file.absolutePath, backend)
            val loadMs = if (alreadyLoaded) 0L else System.currentTimeMillis() - loadStart
            if (loaded.isFailure) {
                val message = OnDeviceEngine.describeError(
                    loaded.exceptionOrNull() ?: RuntimeException("Yerel motor hatası"),
                )
                onMain {
                    engineState = LocalEngineUi.Error(message)
                    cb.onError(message)
                }
                return@launch
            }
            // Y6 — model yüklenirken iptal edilmiş ya da yerine yeni bir istem
            // gönderilmiş olabilir. Bu noktada SESSİZCE çekiliyoruz: kullanıcı
            // zaten yeni bir yanıt bekliyor, eski isteği motora sokmak iki
            // eşzamanlı üretim demek olurdu.
            if (generationToken != myToken) return@launch

            // Gerçekten hangi backend yüklendi — tahmin değil, ölçülen sonuç.
            val loadedBackend = loaded.getOrDefault(ActiveBackend.NONE)
            onMain {
                activeBackend = loadedBackend
                engineState = LocalEngineUi.Ready(spec.displayName)
            }
            // Üretim hızı ölçümü: ilk token'dan onDone'a kadar geçen süre + karakter sayısı.
            var chars = 0
            var firstTokenMs = 0L
            // Donma bekçisi: yanıt hiç başlamaz ya da akış ortada takılırsa
            // sonsuza dek beklemek yerine iptal edip dürüst bir hata veririz.
            val delivered = AtomicBoolean(false)
            val startedAt = System.currentTimeMillis()
            val lastTokenAt = AtomicLong(0L)
            val watchdog = scope.launch(Dispatchers.IO) watchdog@{
                while (isActive && !delivered.get()) {
                    delay(WATCHDOG_TICK_MS)
                    if (delivered.get()) return@watchdog
                    val now = System.currentTimeMillis()
                    val last = lastTokenAt.get()
                    val stuck =
                        if (last == 0L) now - startedAt > FIRST_TOKEN_TIMEOUT_MS
                        else now - last > STALL_TIMEOUT_MS
                    if (stuck && delivered.compareAndSet(false, true)) {
                        this@LocalLlmController.engine.cancel()
                        if (loadMs > 0) recordMetrics(spec.id, loadMs, 0, 0L)
                        onMain {
                            cb.onError(
                                "Yerel model yanıt üretemedi (zaman aşımı). " +
                                    "Üretim iptal edildi; lütfen tekrar deneyin. " +
                                    "Sorun sürerse daha küçük bir model seçin.",
                            )
                        }
                        return@watchdog
                    }
                }
            }
            activeWatchdog = watchdog
            engine.generate(
                history = history,
                prompt = prompt,
                thinking = thinking,
                tools = if (toolsEnabled) listOf(tool(toolBelt)) else emptyList(),
                systemInstruction = persona,
                sampler = sampler,
                cb = object : OnDeviceEngine.Callbacks {
                    override fun onToken(text: String) {
                        if (delivered.get()) return
                        val now = System.currentTimeMillis()
                        lastTokenAt.set(now)
                        if (firstTokenMs == 0L) firstTokenMs = now
                        chars += text.length
                        onMain { cb.onToken(text) }
                    }

                    override fun onDone() {
                        if (!delivered.compareAndSet(false, true)) return
                        watchdog.cancel()
                        val elapsed = if (firstTokenMs > 0) System.currentTimeMillis() - firstTokenMs else 0L
                        recordMetrics(spec.id, loadMs, chars, elapsed)
                        onMain { cb.onDone() }
                    }

                    override fun onError(message: String) {
                        if (!delivered.compareAndSet(false, true)) return
                        watchdog.cancel()
                        // Yükleme ölçümü yine de değerli; üretim hızını atla.
                        if (loadMs > 0) recordMetrics(spec.id, loadMs, 0, 0L)
                        onMain { cb.onError(message) }
                    }
                },
            )
        }
    }

    private fun recordMetrics(modelId: String, loadMs: Long, chars: Int, elapsedMs: Long) {
        val tps = ModelMetricsStore.tokensPerSecond(chars, elapsedMs)
        metricsStore.record(modelId, loadMs, tps, System.currentTimeMillis())
        onMain { metrics = metricsStore.all() }
    }

    fun cancelGenerate() {
        // Y6: jetonu artır — yükleme aşamasında bekleyen bir istek varsa
        // `ensureLoaded`'dan döndüğünde kendini güncel olmayan bulup çekilir.
        synchronized(this) { ++generationToken }
        activeWatchdog?.cancel()
        activeWatchdog = null
        engine.cancel()
    }

    /** Hibrit yönlendirici için pil anlık görüntüsü: (yüzde | -1, şarj oluyor mu). */
    fun batteryNow(): Pair<Int, Boolean> = DeviceStatusReader.battery(app)

    /** Hibrit yönlendirici için ısı durumu (SEVERE+). */
    fun thermalSevereNow(): Boolean = DeviceStatusReader.thermalSevere(app)

    /**
     * ViewModel temizlenirken çağrılır.
     *
     * K2: indirmeyi ARTIK İPTAL ETMİYOR — bütün mesele buydu. Eskiden burada
     * `downloadHandles.values.forEach { it.cancel() }` vardı ve kullanıcı
     * uygulamayı kapattığında 8,6 GB'lık indirme sessizce ölüyordu.
     *
     * Motor boşaltması bilinçli olarak `scope` DIŞINDA: AndroidX, ViewModel'in
     * closeable'larını (yani viewModelScope'u) `onCleared()`'dan ÖNCE kapatıyor,
     * dolayısıyla buradaki `scope.launch` hiç çalışmıyordu ve LiteRT motoru +
     * GB'larca mmap'li model süreç ölene kadar bellekte kalıyordu (denetim L1).
     */
    fun shutdown() {
        Thread { engine.unload() }.apply { isDaemon = true }.start()
    }

    /**
     * Cihaz-üstü yerel veriyi temizler: notlar + performans metrikleri, isteğe bağlı
     * indirilen modeller. Modeller silinirse motor da boşaltılır.
     */
    fun wipeLocalData(includeModels: Boolean) {
        // Burada iptal DOĞRU: kullanıcı açıkça yerel veriyi silmek istiyor.
        LocalModelCatalog.entries.forEach { cancelDownload(it) }
        scope.launch(Dispatchers.IO) {
            noteStore.clear()
            metricsStore.clear()
            if (includeModels) {
                engine.unload()
                LocalModelCatalog.entries.forEach { store.delete(it) }
            }
            onMain {
                metrics = metricsStore.all()
                refresh()
                if (includeModels) engineState = LocalEngineUi.Idle
            }
        }
    }

    companion object {
        /** Bekçi ayarları: E4B gibi büyük modellerde ilk prefill uzun sürebilir. */
        const val FIRST_TOKEN_TIMEOUT_MS = 240_000L
        const val STALL_TIMEOUT_MS = 90_000L
        const val WATCHDOG_TICK_MS = 5_000L

        fun readDeviceRamGb(context: Context): Double {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return 0.0
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            return info.totalMem / 1_073_741_824.0
        }
    }
}
