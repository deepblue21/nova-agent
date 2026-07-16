package com.nova.agent.llm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelDiskState
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.LocalModelStore
import com.nova.agent.llm.local.ModelDownloader
import com.nova.agent.llm.local.OnDeviceEngine
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

    var models by mutableStateOf(snapshot(emptyList()))
        private set
    var engineState by mutableStateOf<LocalEngineUi>(LocalEngineUi.Idle)
        private set

    /** Cihazın toplam RAM'i (GB, bir ondalık). Modeller ekranında gösterilir. */
    val deviceRamGb: Double = readDeviceRamGb(app)

    // ---------- durum ----------

    fun refresh() {
        models = snapshot(models)
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

    fun startDownload(spec: LocalModelSpec) {
        if (downloadHandles.containsKey(spec.id)) return
        val handle = downloader.newHandle()
        downloadHandles[spec.id] = handle
        val startBytes = (store.diskState(spec) as? LocalModelDiskState.Partial)?.bytes ?: 0L
        update(spec.id) {
            it.copy(downloading = true, downloadedBytes = startBytes, error = null)
        }
        scope.launch(Dispatchers.IO) {
            val result = downloader.download(spec, store, handle) { bytes, _ ->
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
     * [prompt] son kullanıcı mesajıdır. Callback'ler ana thread'de teslim edilir.
     */
    fun generate(
        spec: LocalModelSpec,
        history: List<Pair<String, String>>,
        prompt: String,
        thinking: Boolean,
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
            val loaded = engine.ensureLoaded(file.absolutePath)
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
            engine.generate(
                history = history,
                prompt = prompt,
                thinking = thinking,
                cb = object : OnDeviceEngine.Callbacks {
                    override fun onToken(text: String) = onMain { cb.onToken(text) }
                    override fun onDone() = onMain { cb.onDone() }
                    override fun onError(message: String) = onMain { cb.onError(message) }
                },
            )
        }
    }

    fun cancelGenerate() {
        engine.cancel()
    }

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
