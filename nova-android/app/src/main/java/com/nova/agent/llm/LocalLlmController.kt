package com.nova.agent.llm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.tool
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.LocalModelStore
import com.nova.agent.llm.local.ModelDownloader
import com.nova.agent.llm.local.OnDeviceEngine
import com.nova.agent.llm.local.tools.DeviceStatusReader
import com.nova.agent.llm.local.tools.HorusToolSet
import com.nova.agent.llm.local.tools.NoteStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
) {
    private val store = LocalModelStore(app)
    private val engine = OnDeviceEngine(app)
    private val downloader = ModelDownloader()
    private val downloadHandles = mutableMapOf<String, ModelDownloader.Handle>()

    /** Çevrimdışı araç seti (Faz 2 — agentic çekirdek). Ağa çıkmaz, izin istemez. */
    private val noteStore = NoteStore(File(app.filesDir, "horus_notlar.txt"))
    private val toolBelt = HorusToolSet(app, noteStore)

    /** Model performans metrikleri (Faz 4). Cihazda kalır. */
    private val metricsStore = ModelMetricsStore(File(app.filesDir, "model_metrics.json"))

    var models by mutableStateOf(snapshot(emptyList()))
        private set
    var engineState by mutableStateOf<LocalEngineUi>(LocalEngineUi.Idle)
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
        if (downloadHandles.containsKey(spec.id)) return
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
        val handle = downloader.newHandle()
        downloadHandles[spec.id] = handle
        val startBytes = (store.diskState(spec) as? LocalModelDiskState.Partial)?.bytes ?: 0L
        update(spec.id) {
            it.copy(downloading = true, downloadedBytes = startBytes, error = null)
        }
        scope.launch(Dispatchers.IO) {
            val result = downloader.download(spec, store, handle, hfToken) { bytes, _ ->
                onMain { update(spec.id) { ui -> ui.copy(downloadedBytes = bytes) } }
            }
            onMain {
                downloadHandles.remove(spec.id)
                when (result) {
                    is ModelDownloader.Result.Success ->
                        update(spec.id) {
                            it.copy(downloading = false, disk = store.diskState(spec), error = null)
                        }
                    is ModelDownloader.Result.Cancelled ->
                        update(spec.id) {
                            it.copy(downloading = false, disk = store.diskState(spec))
                        }
                    is ModelDownloader.Result.Failure ->
                        update(spec.id) {
                            it.copy(
                                downloading = false,
                                disk = store.diskState(spec),
                                error = result.message,
                            )
                        }
                }
            }
        }
    }

    fun cancelDownload(spec: LocalModelSpec) {
        downloadHandles[spec.id]?.cancel()
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
        cb: OnDeviceEngine.Callbacks,
    ) {
        scope.launch(Dispatchers.IO) {
            val file = store.modelFile(spec)
            if (!file.exists() || file.length() != spec.sizeBytes) {
                onMain { cb.onError("Model dosyası eksik. Modeller sekmesinden indirin.") }
                return@launch
            }
            val alreadyLoaded = engine.loadedModelPath == file.absolutePath
            if (!alreadyLoaded) {
                onMain { engineState = LocalEngineUi.Loading(spec.displayName) }
            }
            val loadStart = System.currentTimeMillis()
            val loaded = engine.ensureLoaded(file.absolutePath)
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
            onMain { engineState = LocalEngineUi.Ready(spec.displayName) }
            // Üretim hızı ölçümü: ilk token'dan onDone'a kadar geçen süre + karakter sayısı.
            var chars = 0
            var firstTokenMs = 0L
            engine.generate(
                history = history,
                prompt = prompt,
                thinking = thinking,
                tools = if (toolsEnabled) listOf(tool(toolBelt)) else emptyList(),
                cb = object : OnDeviceEngine.Callbacks {
                    override fun onToken(text: String) {
                        if (firstTokenMs == 0L) firstTokenMs = System.currentTimeMillis()
                        chars += text.length
                        onMain { cb.onToken(text) }
                    }

                    override fun onDone() {
                        val elapsed = if (firstTokenMs > 0) System.currentTimeMillis() - firstTokenMs else 0L
                        recordMetrics(spec.id, loadMs, chars, elapsed)
                        onMain { cb.onDone() }
                    }

                    override fun onError(message: String) {
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
        engine.cancel()
    }

    fun cancelGenerate() {
        engine.cancel()
    }

    /** Hibrit yönlendirici için pil anlık görüntüsü: (yüzde | -1, şarj oluyor mu). */
    fun batteryNow(): Pair<Int, Boolean> = DeviceStatusReader.battery(app)

    /** Hibrit yönlendirici için ısı durumu (SEVERE+). */
    fun thermalSevereNow(): Boolean = DeviceStatusReader.thermalSevere(app)

    fun shutdown() {
        downloadHandles.values.forEach { it.cancel() }
        downloadHandles.clear()
        scope.launch(Dispatchers.IO) { engine.unload() }
    }

    companion object {
        fun readDeviceRamGb(context: Context): Double {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return 0.0
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            return info.totalMem / 1_073_741_824.0
        }
    }
}
