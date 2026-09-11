package com.nova.agent

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.agent.data.AppSettings
import com.nova.agent.data.ChatMessage
import com.nova.agent.data.Conversation
import com.nova.agent.data.ConversationExporter
import com.nova.agent.data.ConversationStore
import com.nova.agent.data.ConversationSummary
import com.nova.agent.data.ConversationText
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.FALLBACK_MODELS
import com.nova.agent.data.GatewayCatalog
import com.nova.agent.data.ModelOption
import com.nova.agent.data.Mode
import com.nova.agent.data.ContentReport
import com.nova.agent.data.ContentReportReason
import com.nova.agent.data.ContentReportStore
import com.nova.agent.data.FallbackKind
import com.nova.agent.data.PcAgentRun
import com.nova.agent.data.PcHandoffInFlight
import com.nova.agent.data.PendingFallback
import com.nova.agent.data.SettingsStore
import com.nova.agent.data.ToolStep
import com.nova.agent.data.UiMode
import com.nova.agent.data.VoiceState
import com.nova.agent.feature.pairing.PairingController
import com.nova.agent.llm.EngineRouter
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.HybridInputs
import com.nova.agent.llm.LocalLlmController
import com.nova.agent.llm.PrivacyClassifier
import com.nova.agent.llm.RouteDecision
import com.nova.agent.llm.ThinkingText
import com.nova.agent.llm.local.BackendPreference
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.OnDeviceEngine
import com.nova.agent.llm.local.SamplerPreset
import com.nova.agent.llm.local.SamplerSettings
import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionResult
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.net.NovaClient
import com.nova.agent.voice.SpeechManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.sse.EventSource

private const val DEFAULT_SUB = "Konuşmak için mikrofona dokun"

/** PC'deki ajan koşusu için Gateway model kimliği (görev devri hedefi). */
private const val PC_AGENT_MODEL = "openclaw/default"

/**
 * Devir bitişinden sonraki güvence tazelemesinin gecikmesi (ms) — Faz 11.
 * Gateway koşum satırını yanıt kapandıktan sonra yazıyor; tek bir anlık
 * sorgu kendi kaydını ıskalayabilir.
 */
private const val HANDOFF_RUNS_RECHECK_MS = 1_500L

internal class LatestConnectionProbe {
    private var generation = 0L

    fun start(): Long = ++generation

    fun complete(probe: Long, block: () -> Unit) {
        if (probe == generation) block()
    }

    fun invalidate() {
        generation++
    }
}

class NovaViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = NovaClient()
    private val connectionClient = GatewayConnectionClient()
    private val connectionProbes = LatestConnectionProbe()
    private val speech = SpeechManager(app)
    private val main = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) { main.post(block) }

    /** Cihaz-üstü LLM yaşam döngüsü (Faz 1 — yerel öncelikli). */
    val local = LocalLlmController(
        app,
        viewModelScope,
        ::onMain,
        onModelInstalled = ::activateIfNothingUsable,
    )

    /**
     * K1 — yeni kurulan modeli, ortada kullanılabilir bir model YOKSA aktif yapar.
     *
     * Kural bilinçli olarak "her indirmede geç" değil: kullanıcının hâlihazırda
     * çalışan bir seçimi varsa onu elinden almak sürpriz olurdu. Ama seçili
     * model kurulu değilse ortada çalışan bir şey yok demektir; o durumda yeni
     * modeli aktif etmemek kullanıcıyı "model indirdim ama hâlâ model yok
     * diyor" çıkmazında bırakıyordu.
     */
    private fun activateIfNothingUsable(modelId: String) {
        if (local.isInstalled(settings.localModelId)) return
        setLocalModel(modelId)
    }

    /**
     * mDNS keşfi + kod takası (Faz A). Takastan dönen anahtar burada
     * saklanmaz: doğrudan mevcut `saveConnection` yoluna verilir, yani
     * kaydetme/doğrulama davranışı elle girişle birebir aynı kalır.
     */
    val pairing = PairingController(
        app = app,
        scope = viewModelScope,
        onMain = ::onMain,
        onPaired = { baseUrl, apiKey -> saveConnection(baseUrl, apiKey) },
    )

    /** Kalıcı sohbet geçmişi (Faz 5). Cihazda JSON. */
    private val convoStore = ConversationStore(File(app.filesDir, "conversations.json"))

    /**
     * Play B6 — yapay zekâ içerik bildirimleri. Cihazda kalır; gönderim
     * kullanıcının açık eylemidir (bkz. [shareContentReports]).
     */
    private val reportStore = ContentReportStore(File(app.filesDir, "content_reports.json"))
    private var currentConversationId: String? = null
    private var currentCreatedAt: Long = 0L
    var history by mutableStateOf<List<ConversationSummary>>(emptyList()); private set
    var historyQuery by mutableStateOf(""); private set

    val messages = mutableStateListOf<ChatMessage>()
    var settings by mutableStateOf(AppSettings()); private set
    var connectionState by mutableStateOf(GatewayConnectionUiState()); private set
    /** Gateway'den gelen canlı model kataloğu; null = henüz yok, yedek liste kullanılır. */
    var gatewayCatalog by mutableStateOf<GatewayCatalog?>(null); private set
    var busy by mutableStateOf(false); private set
    var mode by mutableStateOf(Mode.KONTROL)
    var voiceState by mutableStateOf(VoiceState.IDLE); private set
    var voiceSub by mutableStateOf(DEFAULT_SUB); private set
    var level by mutableStateOf(0.08f); private set

    /** Yerel hata sonrası bekleyen izinli PC devri; onaysız istem dışarı çıkmaz. */
    var pendingFallback by mutableStateOf<PendingFallback?>(null); private set

    /**
     * PC'ye devredilen işlerin geçmişi (Faz 11). `null` = henüz okunmadı ya da
     * gateway'e ulaşılamadı; boş liste = gerçekten koşum yok. İkisi farklı
     * mesaj gösterir, çünkü farklı şeylerdir.
     */
    var pcRuns by mutableStateOf<List<PcAgentRun>?>(null); private set
    var pcRunsLoading by mutableStateOf(false); private set

    /**
     * Şu anda PC'de çalışan devir (Faz 11B); yoksa null.
     *
     * `busy` tek başına yetmiyordu: Kontrol ekranı devir sırasında "Sohbet
     * yanıtı üretiliyor…" diyordu, oysa iş sohbette değil PC'de çalışıyor.
     * Bu durum ayrı taşınmadan doğru cümle kurulamıyor.
     */
    var pcHandoff by mutableStateOf<PcHandoffInFlight?>(null); private set

    private var es: EventSource? = null
    private var connectionCall: Call? = null
    private var modelsCall: Call? = null
    private var runsCall: Call? = null
    /** Devir tamamlanınca geçmişi bir kez tazele. */
    private var refreshRunsWhenDone = false
    private val sb = StringBuilder()
    private val thoughtBuf = StringBuilder()
    private var activeLocal = false

    init {
        speech.initTts()
        viewModelScope.launch {
            settings = store.load()
            refreshConnectionState()
        }
        reloadHistory()
    }

    // ---------- sohbet geçmişi (Faz 5) ----------

    /**
     * Arama yarışı sayacı.
     *
     * Her tuş vuruşu ayrı bir arama başlatıyordu ve sonuçlar BİTİŞ SIRASINA göre
     * yazılıyordu. "abc" hızlı yazıldığında "a" araması en son bitip "abc"nin
     * sonucunu ezebiliyordu: kullanıcı yazdığı sorguyla ilgisiz bir liste
     * görüyordu. Bağlantı sondaları için `LatestConnectionProbe` deseni zaten
     * vardı; geçmiş aramasının karşılığı yoktu.
     */
    private var historyGeneration = 0L

    private fun reloadHistory() {
        val q = historyQuery
        val generation = ++historyGeneration
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (q.isBlank()) convoStore.list() else convoStore.search(q)
            }
            if (generation != historyGeneration) return@launch
            history = list
        }
    }

    fun updateHistoryQuery(q: String) {
        historyQuery = q
        reloadHistory()
    }

    /** Aktif sohbeti (en az bir kullanıcı mesajı varsa) diske yazar. */
    /**
     * Sohbeti diske yazar — V2.
     *
     * [blocking] YALNIZ `onCleared` için: AndroidX, ViewModel'in closeable'larını
     * (yani viewModelScope'u) `onCleared()`'dan ÖNCE kapatıyor, dolayısıyla
     * oradan `viewModelScope.launch` ile kaydetmek HİÇ çalışmaz. Yıkım anında
     * senkron yazmak, kaydetmemekten iyidir.
     */
    private fun saveCurrent(blocking: Boolean = false) {
        val snapshot = messages.map { it.copy(streaming = false) }
        if (snapshot.none { it.role == "user" }) return
        val id = currentConversationId ?: UUID.randomUUID().toString()
        currentConversationId = id
        if (currentCreatedAt == 0L) currentCreatedAt = System.currentTimeMillis()
        val convo = Conversation(
            id = id,
            title = ConversationText.titleFrom(snapshot),
            createdAt = currentCreatedAt,
            updatedAt = System.currentTimeMillis(),
            messages = snapshot,
        )
        if (blocking) {
            runCatching { convoStore.save(convo) }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { convoStore.save(convo) }
            reloadHistory()
        }
    }

    fun openConversation(id: String) {
        if (busy) return
        saveCurrent()
        stop()
        viewModelScope.launch {
            val convo = withContext(Dispatchers.IO) { convoStore.load(id) } ?: return@launch
            messages.clear()
            messages.addAll(convo.messages)
            currentConversationId = convo.id
            currentCreatedAt = convo.createdAt
            pendingFallback = null
            mode = Mode.CHAT
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { convoStore.delete(id) }
            if (id == currentConversationId) {
                currentConversationId = null
                currentCreatedAt = 0L
                messages.clear()
            }
            reloadHistory()
        }
    }

    /** Sohbeti Markdown olarak sistem paylaşım sayfasına gönderir. */
    /** Ayarlar'daki sayaç için. */
    var contentReports by mutableStateOf<List<ContentReport>>(emptyList()); private set

    /**
     * Kısa onay mesajı. Bildirim akışının "kaydedildi" geri bildirimi için:
     * kullanıcı bildirdiğini gördüğünden emin olmalı, yoksa aynı yanıtı
     * tekrar tekrar bildirir ya da işe yaramadığını sanır.
     */
    var notice by mutableStateOf<String?>(null); private set

    fun clearNotice() { notice = null }

    /**
     * Sakıncalı yanıtı bildirir — Play B6.
     *
     * Akış BURADA kapanır: kayıt cihaza yazılır ve kullanıcı onay görür.
     * Politika bildirimin uygulamadan çıkmadan yapılabilmesini şart koşuyor;
     * paylaşım ayrı ve isteğe bağlı bir adım.
     */
    fun reportContent(message: ChatMessage, reason: ContentReportReason, note: String) {
        val report = ContentReport(
            id = java.util.UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            reason = reason,
            note = note,
            excerpt = ContentReport.excerptOf(message.content),
            route = message.route.orEmpty(),
        )
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) { reportStore.add(report) }
            contentReports = next
            notice = "Bildirimin kaydedildi. Ayarlar > Bildirimler'den gönderebilirsin."
        }
    }

    fun refreshContentReports() {
        viewModelScope.launch {
            contentReports = withContext(Dispatchers.IO) { reportStore.list() }
        }
    }

    fun clearContentReports() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { reportStore.clear() }
            contentReports = emptyList()
        }
    }

    /** Bildirimleri kullanıcının seçtiği kanaldan paylaşır. İçeriği görerek gönderir. */
    fun shareContentReports() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { reportStore.exportText() }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "NOVA — içerik bildirimleri")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, "Bildirimleri gönder")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { getApplication<Application>().startActivity(chooser) }
        }
    }

    fun shareConversation(id: String) {
        viewModelScope.launch {
            val convo = withContext(Dispatchers.IO) { convoStore.load(id) } ?: return@launch
            val markdown = ConversationExporter.toMarkdown(convo)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, convo.title)
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
            val chooser = Intent.createChooser(send, "Sohbeti paylaş")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { getApplication<Application>().startActivity(chooser) }
        }
    }

    /** Tüm sohbet geçmişini siler (Ayarlar > veri temizleme). */
    fun clearAllConversations() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { convoStore.clear() }
            currentConversationId = null
            currentCreatedAt = 0L
            messages.clear()
            reloadHistory()
        }
    }

    /**
     * Tüm yerel veriyi temizler: sohbet geçmişi + notlar + performans metrikleri,
     * [includeModels] ise indirilen modeller de. Ayarları/bağlantıyı etkilemez.
     */
    fun wipeAllLocalData(includeModels: Boolean) {
        clearAllConversations()
        local.wipeLocalData(includeModels)
    }

    // ---------- ayarlar ----------
    fun setModel(id: String) = persist(settings.copy(modelId = id))
    fun setEffort(id: String) = persist(settings.copy(effort = id))
    fun setReasoning(enabled: Boolean) = persist(settings.copy(reasoning = enabled))
    fun toggleReasoning() = setReasoning(!settings.reasoning)

    val executionPolicy: ExecutionPolicy
        get() = ExecutionPolicy.fromId(settings.executionPolicy)

    /** Yalnız açık politikalar seçilebilir; HYBRID Faz 3'e kadar pasiftir. */
    fun setExecutionPolicy(policy: ExecutionPolicy) {
        if (policy.selectableNow) persist(settings.copy(executionPolicy = policy.id))
    }

    fun setLocalModel(id: String) {
        if (LocalModelCatalog.byId(id) != null) persist(settings.copy(localModelId = id))
    }

    fun setLocalThinking(enabled: Boolean) = persist(settings.copy(localThinking = enabled))
    fun setLocalTools(enabled: Boolean) = persist(settings.copy(localTools = enabled))
    fun setTheme(id: String) =
        persist(settings.copy(themeId = com.nova.agent.ui.theme.normalizeThemeId(id)))
    fun setHfToken(token: String) = persist(settings.copy(hfToken = token.trim()))
    fun setHybridAutoFallback(enabled: Boolean) = persist(settings.copy(hybridAutoFallback = enabled))
    fun setPersona(text: String) = persist(settings.copy(persona = text.trim()))

    // --- Faz 9: arayüz yoğunluğu + cihaz motoru ---

    /** Yalnız görünürlük değişir; hiçbir ayarın değeri sıfırlanmaz. */
    fun setUiMode(mode: UiMode) = persist(settings.copy(uiMode = mode.id))

    /**
     * Hızlandırma tercihi. Motor bir sonraki üretimde yeni tercihle yeniden
     * yüklenir; şimdiden boşaltmak, sürmekte olan bir yanıtı kesip
     * kullanıcının istemediği bir iptale yol açardı.
     */
    fun setBackendPreference(preference: BackendPreference) =
        persist(settings.copy(backendPreference = preference.id))

    /** İlk açılış model kartını kalıcı olarak kapatır ("şimdilik atla"). */
    fun dismissFirstRunGuide() = persist(settings.copy(firstRunGuideDismissed = true))

    fun setSamplerPreset(preset: SamplerPreset) =
        persist(settings.copy(samplerPreset = preset.id))

    /** Elle örnekleme değerleri; diske yazılırken de kırpılır. */
    fun setCustomSampler(sampler: SamplerSettings) {
        val safe = sampler.clamped()
        persist(
            settings.copy(
                samplerTopK = safe.topK,
                samplerTopP = safe.topP,
                samplerTemperature = safe.temperature,
            ),
        )
    }

    fun activeLocalSpec(): LocalModelSpec =
        LocalModelCatalog.byId(settings.localModelId) ?: LocalModelCatalog.default

    fun saveConnection(baseUrl: String, token: String) {
        val trimmedBaseUrl = baseUrl.trim()
        val trimmedToken = token.trim()
        val canonicalBaseUrl = GatewayConnectionClient.canonicalBaseUrl(trimmedBaseUrl)?.toString()
        if (canonicalBaseUrl == null) {
            testConnection(trimmedBaseUrl, trimmedToken)
            return
        }
        val updated = settings.copy(baseUrl = canonicalBaseUrl, token = trimmedToken)
        persist(updated)
        testConnection(updated.baseUrl, updated.token)
    }

    /**
     * Bağlantı durumunu mevcut ayarlara göre tazeler — **kendiliğinden** çağrılan
     * her yerde bu kullanılmalı, `testConnection()` değil.
     *
     * Kural açılıştakiyle aynı: belirteç yoksa bağlantı hiç kurulmamıştır, sonda
     * atmanın anlamı yoktur. Ayarlar paneli kapanışı eskiden koşulsuz
     * `testConnection()` çağırıyordu; temiz kurulumda (adres boş) paneli sırf
     * tema için açıp kapatan kullanıcı, hiç kurmadığı bir bağlantı için kalıcı
     * "Gateway adresi geçersiz" hatası görüyordu ve Basit modda düzeltebileceği
     * bir alan ekranda yoktu.
     *
     * Kullanıcının kendi bastığı "Bağlantıyı test et" bunun dışındadır: orada
     * geçersiz adresi söylemek doğru davranıştır.
     */
    fun refreshConnectionState() {
        if (GatewayConnectionUiState.shouldProbeOnStart(settings.baseUrl, settings.token)) {
            testConnection(settings.baseUrl, settings.token)
        } else {
            connectionCall?.cancel()
            connectionState = GatewayConnectionUiState.notConfigured()
        }
    }

    fun testConnection(baseUrl: String = settings.baseUrl, token: String = settings.token) {
        val probe = connectionProbes.start()
        connectionCall?.cancel()
        connectionState = GatewayConnectionUiState(
            GatewayConnectionStatus.CHECKING,
            "Bağlanıyor",
        )
        connectionCall = connectionClient.test(baseUrl, token) { result ->
            onMain {
                connectionProbes.complete(probe) {
                    connectionState = when (result) {
                        GatewayConnectionResult.Ready -> GatewayConnectionUiState(
                            GatewayConnectionStatus.READY,
                            "PC hazır",
                        )
                        GatewayConnectionResult.AuthRequired -> GatewayConnectionUiState(
                            GatewayConnectionStatus.AUTH_REQUIRED,
                            "Kimlik doğrulama gerekli",
                            "Adres doğru; erişim belirteci eksik ya da yanlış. PC'de " +
                                "start-horus -Lan çıktısındaki API anahtarını gir.",
                        )
                        GatewayConnectionResult.InvalidUrl -> GatewayConnectionUiState(
                            GatewayConnectionStatus.INVALID_URL,
                            "Gateway adresi geçersiz",
                            "Biçim: http://<PC-IP>:18088/v1 — örn. http://192.168.1.20:18088/v1",
                        )
                        is GatewayConnectionResult.Failure -> GatewayConnectionUiState(
                            GatewayConnectionStatus.UNREACHABLE,
                            result.message,
                            result.hint,
                        )
                    }
                    if (result == GatewayConnectionResult.Ready) {
                        refreshGatewayModels(baseUrl, token)
                        // Faz 11: bağlantı kurulur kurulmaz PC'ye devredilmiş
                        // işlerin geçmişi de gelsin — Kontrol ekranı açıldığında
                        // hazır olsun diye.
                        refreshPcRuns(baseUrl, token)
                    }
                }
            }
        }
    }

    /**
     * Canlı model listesini gateway'den çeker (Ollama'da yüklü modeller +
     * anahtarı tanımlı bulut sağlayıcıları). Başarısız olursa yedek liste
     * kullanılmaya devam eder — uydurma model gösterilmez.
     */
    fun refreshGatewayModels(baseUrl: String = settings.baseUrl, token: String = settings.token) {
        modelsCall?.cancel()
        modelsCall = connectionClient.fetchModels(baseUrl, token) { catalog ->
            onMain {
                gatewayCatalog = catalog
                // Kullanıcı henüz seçim yapmadıysa ya da kayıtlı seçim artık
                // listede yoksa canlı varsayılana (ilk yerel model) geç.
                val list = catalog?.models.orEmpty()
                if (list.isNotEmpty()) {
                    val current = list.firstOrNull { it.id == settings.modelId }
                    if (current == null || !current.available) {
                        val fallback = catalog?.defaultModelId
                            ?.let { id -> list.firstOrNull { it.id == id && it.available } }
                            ?: list.firstOrNull { it.available && it.model != "auto" }
                        if (fallback != null) persist(settings.copy(modelId = fallback.id))
                    }
                }
            }
        }
    }

    /**
     * PC'ye devredilen işlerin geçmişini tazeler — Faz 11.
     *
     * Bağlantı hazır değilken çağrı yapılmaz: kurulmamış bir bağlantı için ağ
     * hatası üretip kullanıcıya "geçmişe ulaşılamadı" demek yanlış olurdu.
     */
    fun refreshPcRuns(baseUrl: String = settings.baseUrl, token: String = settings.token) {
        if (connectionState.status != GatewayConnectionStatus.READY) return
        if (baseUrl.isBlank() || token.isBlank()) return
        runsCall?.cancel()
        pcRunsLoading = true
        runsCall = connectionClient.fetchAgentRuns(baseUrl, token) { runs ->
            onMain {
                pcRunsLoading = false
                // null (ulaşılamadı) gelirse ELDEKİ listeyi silme: kullanıcı
                // ağ dalgalanmasında geçmişinin uçtuğunu sanmasın.
                if (runs != null) pcRuns = runs
            }
        }
    }

    /**
     * Devir bitişinde geçmişi tazeler — İKİ kez, bilerek.
     *
     * Yarış durumu gerçek: gateway koşum satırını **yanıt bittikten sonra**
     * yazıyor (akış `finish(res)` ile kapanıyor, kayıt ondan sonra geliyor).
     * Telefon `onDone` anında tek sefer sorarsa kendi az önce yarattığı satırı
     * ıskalayabilir ve kullanıcı "devrettim ama listede yok" görür.
     *
     * İlk çağrı hızlı yol (çoğu zaman yeter), ikincisi güvence. Gateway
     * tarafında kayıt artık `await` ediliyor; bu gecikme onun ağ turunu
     * karşılıyor.
     */
    private fun refreshPcRunsAfterHandoff() {
        refreshPcRuns()
        viewModelScope.launch {
            delay(HANDOFF_RUNS_RECHECK_MS)
            refreshPcRuns()
        }
    }

    /** Seçicide gösterilecek liste: canlı varsa o, yoksa yedek. */
    fun modelOptions(): List<ModelOption> =
        gatewayCatalog?.models?.takeIf { it.isNotEmpty() } ?: FALLBACK_MODELS

    private fun persist(s: AppSettings) {
        settings = s
        viewModelScope.launch { store.save(s) }
    }

    fun currentModelName(): String = when (executionPolicy) {
        ExecutionPolicy.LOCAL_FIRST, ExecutionPolicy.LOCAL_ONLY -> activeLocalSpec().displayName
        else -> modelOptions().find { it.id == settings.modelId }?.name ?: "auto"
    }

    fun currentEffortName(): String = EFFORTS.find { it.id == settings.effort }?.name ?: ""

    // ---------- sohbet ----------
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMessage("user", t))
        routeAndComplete(speakWhenDone = false)
    }

    fun regenerate() {
        if (busy) return
        while (messages.isNotEmpty() && messages.last().role == "assistant") messages.removeAt(messages.lastIndex)
        if (messages.none { it.role == "user" }) return
        routeAndComplete(speakWhenDone = false)
    }

    fun stop() {
        // M1: iptali istemci işaretler, yoksa "Gateway hatası (200)" yazılıyordu.
        client.cancelStream(es); es = null
        // Faz 11B: devir dinlemesi de burada biter. Not: akışı kesmek PC'deki
        // ajanı durdurmayı GARANTİ ETMEZ — düğme metni bunu açıkça söylüyor.
        pcHandoff = null
        refreshRunsWhenDone = false
        if (activeLocal) {
            activeLocal = false
            local.cancelGenerate()
        }
        busy = false
        updateLast { it.copy(streaming = false) }
        if (voiceState != VoiceState.IDLE) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB }
        // V2: "Durdur" da bir bitiş. Kısmi yanıt kullanıcı için değerli olabilir;
        // eskiden bu yolda hiç kaydedilmediği için soru da yanıt da kayboluyordu.
        saveCurrent()
    }

    fun newChat() {
        saveCurrent()
        stop()
        messages.clear()
        currentConversationId = null
        currentCreatedAt = 0L
        pendingFallback = null
    }

    /**
     * Politikaya göre istemi yönlendirir. LOCAL_FIRST'te yerel model yoksa
     * veya hata verirse istem SESSİZCE dışarı gönderilmez; izin kartı çıkar.
     */
    private fun routeAndComplete(speakWhenDone: Boolean) {
        pendingFallback = null
        val spec = activeLocalSpec()
        val decision = if (executionPolicy == ExecutionPolicy.HYBRID) {
            hybridDecision(spec)
        } else {
            EngineRouter.decide(executionPolicy, spec.id, local.isInstalled(spec.id))
        }
        when (decision) {
            is RouteDecision.Gateway -> complete(messages.toList(), speakWhenDone)
            is RouteDecision.Local -> completeLocal(spec, speakWhenDone)
            is RouteDecision.LocalNeedsSetup -> {
                pendingFallback = PendingFallback(
                    decision.reason,
                    allowGateway = executionPolicy.allowsGatewayFallback,
                    kind = FallbackKind.NO_LOCAL_MODEL,
                )
                if (speakWhenDone) {
                    voiceState = VoiceState.IDLE
                    voiceSub = decision.reason
                    level = 0.08f
                }
            }
        }
    }

    /**
     * Hibrit yönlendirme girdilerini toplar ve saf kurala verir:
     * kısa işler telefonda; uzun istem veya düşük pil (şarjsız) PC'de.
     */
    private fun hybridDecision(spec: LocalModelSpec): RouteDecision {
        val (batteryPercent, charging) = local.batteryNow()
        val promptChars = messages.lastOrNull { it.role == "user" }?.content?.length ?: 0
        return EngineRouter.decideHybrid(
            HybridInputs(
                localModelInstalled = local.isInstalled(spec.id),
                promptChars = promptChars,
                batteryPercent = batteryPercent,
                charging = charging,
                gatewayReady = connectionState.status == GatewayConnectionStatus.READY,
                thermalSevere = local.thermalSevereNow(),
                privacySensitive = conversationSensitive(),
            ),
            localModelId = spec.id,
        )
    }

    /**
     * Dışarı gidecek konuşma hassas mı — gizlilik kararının TEK kaynağı (G2).
     *
     * Gönderilen şey `messages` listesinin tamamıdır, o yüzden karar da
     * tamamına bakar. Yalnız KULLANICI mesajları taranır: sır oraya yazılır,
     * asistan yanıtı ondan türer. Asistanın sırrı yankılaması bu kontrole
     * takılmaz — bilinen ve kabul edilen sınır.
     */
    private fun conversationSensitive(): Boolean =
        PrivacyClassifier.isAnySensitive(
            messages.filter { it.role == "user" }.map { it.content },
        )

    /**
     * Hibritte yerel hata sonrası kalıcı kurala göre otomatik PC devri.
     * Kural kapalıysa veya PC hazır değilse devretmez (izin kartı kalır).
     */
    private fun autoHandoffAfterLocalError(): Boolean {
        if (executionPolicy != ExecutionPolicy.HYBRID || !settings.hybridAutoFallback) return false
        if (connectionState.status != GatewayConnectionStatus.READY) return false
        // GİZLİLİK KAPISI (G1). decideHybrid bu konuşmayı BİLEREK cihazda
        // tutmuştu; yerel motor hata verdi diye aynı içerik sessizce dışarı
        // çıkamaz. Eskiden bu satır yoktu: hassas istem, izin kartı hiç
        // gösterilmeden gateway'e — oradan da seçili model bir bulut
        // sağlayıcısıysa buluta — gidiyordu.
        //
        // Kullanıcı yine de göndermek isterse izin kartından kendi eliyle
        // onaylar (approveFallback). Sınıf yorumundaki "gizlilik override'ı
        // yalnız OTOMATİK devri engeller" sözü ancak böyle tutar.
        if (conversationSensitive()) return false
        while (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.none { it.role == "user" }) return false
        complete(messages.toList(), speakWhenDone = false)
        return true
    }

    /** Yerel hata sonrası kullanıcı onayıyla istemi PC Gateway'e gönderir. */
    fun approveFallback() {
        val pending = pendingFallback ?: return
        // Çevrimdışı modda devir kapalıdır; bu yol hiçbir koşulda açılmaz.
        if (!pending.allowGateway || busy) return
        pendingFallback = null
        while (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.none { it.role == "user" }) return
        complete(messages.toList(), speakWhenDone = false)
    }

    fun rejectFallback() {
        pendingFallback = null
    }

    /**
     * Faz 3 D2 — görev devri: son kullanıcı sorusunu tüm bağlamla birlikte
     * PC'deki OpenClaw ajanına gönderir (mevcut Gateway akış yolu, model
     * override). Kullanıcı dokunuşu = açık rıza; Çevrimdışı modda kapalıdır.
     *
     * Faz 11 — koşum kaydı. Buradaki yorum eskiden "koşu ajan geçmişine
     * otomatik kaydolur" diyordu ve bu YANLIŞTI: gateway'in ajan dalı
     * `provider === "ollama"` ile sınırlı, OpenClaw ise `provider: "openclaw"`
     * ile kayıtlı. Yani devredilen iş `agent_runs`'a hiç yazılmıyordu —
     * telefondan gönderilen işin hiçbir izi kalmıyordu. Kayıt gateway
     * tarafında `openclawRunFromCompletion` ile eklendi; burada da bitişte
     * geçmiş tazelenir ki devir Kontrol ekranında görünsün.
     *
     * Not: kayıt GATEWAY sürümüne bağlıdır. Eski bir gateway'e bağlıyken devir
     * yine çalışır ama geçmişte görünmez; liste boş kalır, uygulama koşum
     * uydurmaz.
     */
    fun handoffToPcAgent() {
        if (busy) return
        if (executionPolicy == ExecutionPolicy.LOCAL_ONLY) return
        while (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.none { it.role == "user" }) return
        pendingFallback = null
        refreshRunsWhenDone = true
        pcHandoff = PcHandoffInFlight(
            prompt = messages.last { it.role == "user" }.content,
            startedAt = System.currentTimeMillis(),
        )
        complete(messages.toList(), speakWhenDone = false, modelOverride = PC_AGENT_MODEL)
    }

    private fun resolveModel(): String = modelOptions().find { it.id == settings.modelId }?.model ?: "auto"

    /** Cihaz-üstü akışlı üretim. Gateway yoluna (complete) hiç dokunmaz. */
    private fun completeLocal(spec: LocalModelSpec, speakWhenDone: Boolean) {
        val prior = messages.toList()
        val prompt = prior.lastOrNull()?.takeIf { it.role == "user" }?.content ?: return
        val history = prior.dropLast(1).map { it.role to it.content }

        sb.clear()
        thoughtBuf.clear()
        messages.add(ChatMessage("assistant", "", route = "telefon/${spec.id}", streaming = true))
        busy = true
        activeLocal = true
        if (speakWhenDone) { voiceState = VoiceState.THINKING; voiceSub = "Düşünüyor…"; level = 0.28f }

        local.generate(
            spec = spec,
            history = history,
            prompt = prompt,
            thinking = settings.localThinking,
            toolsEnabled = settings.localTools,
            persona = settings.persona,
            backend = settings.backendPreferenceValue,
            sampler = settings.effectiveSampler,
            cb = object : OnDeviceEngine.Callbacks {
                override fun onToken(text: String) {
                    if (!activeLocal) return
                    sb.append(text)
                    val (thoughts, content) = renderStreamed()
                    updateLast { it.copy(content = content, thoughts = thoughts) }
                }

                override fun onDone() {
                    if (!activeLocal) return
                    activeLocal = false
                    finishLocal(speakWhenDone)
                }

                override fun onError(message: String) {
                    if (!activeLocal) return
                    activeLocal = false
                    busy = false
                    if (speakWhenDone) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f }
                    // Hibrit + kalıcı izin: kullanıcı kuralıyla otomatik PC devri.
                    if (autoHandoffAfterLocalError()) return
                    val (thoughts, partial) = renderStreamed()
                    updateLast {
                        it.copy(
                            content = if (partial.isBlank()) "⚠️ $message" else "$partial\n\n⚠️ $message",
                            thoughts = thoughts,
                            streaming = false,
                        )
                    }
                    // V2: yerel hata yolunda da kaydet.
                    saveCurrent()
                    // Sessiz devir yok: yalnız bildirim/izin kartı.
                    // Otomatik devir gizlilik yüzünden durduysa bunu SÖYLE —
                    // aksi halde kullanıcı kuralının neden işlemediğini bilemez.
                    pendingFallback = PendingFallback(
                        if (conversationSensitive()) {
                            message + "\n\nBu sohbette hassas görünen bir bilgi var " +
                                "(kart/IBAN/kimlik/parola gibi), bu yüzden otomatik devir " +
                                "yapılmadı. Yine de PC'ye göndermek istersen onayla."
                        } else {
                            message
                        },
                        allowGateway = executionPolicy.allowsGatewayFallback,
                    )
                }
            },
        )
    }

    private fun finishLocal(speak: Boolean) {
        val (thoughts, content) = renderStreamed()
        val text = content.ifBlank { "(boş yanıt)" }
        updateLast { it.copy(content = text, thoughts = thoughts, streaming = false) }
        busy = false
        saveCurrent()
        if (speak) {
            voiceState = VoiceState.SPEAKING
            voiceSub = text.take(160)
            level = 0.5f
            speech.speak(
                text,
                onStart = {},
                onDone = {
                    onMain { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f }
                },
            )
        }
    }

    private fun complete(
        history: List<ChatMessage>,
        speakWhenDone: Boolean,
        modelOverride: String? = null,
    ) {
        sb.clear()
        thoughtBuf.clear()
        messages.add(ChatMessage("assistant", "", streaming = true))
        busy = true
        if (speakWhenDone) { voiceState = VoiceState.THINKING; voiceSub = "Düşünüyor…"; level = 0.28f }

        val model = modelOverride ?: resolveModel()
        es = client.stream(
            baseUrl = settings.baseUrl,
            token = settings.token,
            model = model,
            effort = settings.effort,
            reasoning = settings.reasoning,
            history = history,
            // Araç destekli modelde ajan modu kendiliğinden açılır: kullanıcı
            // ayrı bir anahtar çevirmez, ama hangi araçların çalıştığını
            // araç izi kartında görür.
            agent = agenticForModel(model),
            cb = object : NovaClient.Callbacks {
                override fun onRoute(route: String) = onMain { updateLast { it.copy(route = route) } }
                override fun onToken(text: String) = onMain {
                    sb.append(text)
                    // Faz 11B: ilk parça geldiğinde "gönderildi" → "yanıt yazıyor".
                    // Kullanıcının "takıldı mı?" sorusunun cevabı bu geçiş.
                    pcHandoff?.takeIf { !it.responding }?.let { pcHandoff = it.copy(responding = true) }
                    val (thoughts, content) = renderStreamed()
                    updateLast { it.copy(content = content, thoughts = thoughts) }
                }
                override fun onThought(text: String) = onMain {
                    thoughtBuf.append(text)
                    val (thoughts, _) = renderStreamed()
                    updateLast { it.copy(thoughts = thoughts) }
                }
                override fun onTool(step: ToolStep) = onMain { mergeToolStep(step) }
                override fun onDone() = onMain { finish(speakWhenDone) }
                override fun onError(message: String) = onMain {
                    val (thoughts, partial) = renderStreamed()
                    val body = if (partial.isBlank()) "⚠️ $message" else "$partial\n\n⚠️ $message"
                    updateLast { it.copy(content = body, thoughts = thoughts, streaming = false) }
                    busy = false
                    // Devir hata verdiyse gateway koşumu kaydetmedi; tazeleme
                    // yapmıyoruz ki liste "yeni bir şey oldu" izlenimi vermesin.
                    refreshRunsWhenDone = false
                    pcHandoff = null
                    if (speakWhenDone) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB }
                    // V2: hata da bir bitiş; akış ortasında ağ düşerse soru ve
                    // kısmi yanıt diske yazılmadan kaybolmasın.
                    saveCurrent()
                }
            }
        )
    }

    private fun finish(speak: Boolean) {
        // E1: gateway yolu da düşünmeyi ayıklar. Eskiden yalnızca `finishLocal`
        // ayıklıyordu; buradaki eksiklik "düşünme paylaşımdan çıkarılır" vaadini
        // gateway yolunda tümden geçersiz kılıyordu.
        val (thoughts, content) = renderStreamed()
        val text = content.ifBlank { "(boş yanıt)" }
        updateLast { it.copy(content = text, thoughts = thoughts, streaming = false) }
        busy = false
        es = null
        saveCurrent()
        pcHandoff = null
        // Faz 11: devir bittiyse PC koşum geçmişini tazele.
        if (refreshRunsWhenDone) { refreshRunsWhenDone = false; refreshPcRunsAfterHandoff() }
        if (speak) {
            voiceState = VoiceState.SPEAKING
            voiceSub = text.take(160)
            level = 0.5f
            speech.speak(text, onStart = {}, onDone = { onMain { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f } })
        }
    }

    /**
     * Akıştaki ham metni balon içeriği + düşünme paneline böler — E1.
     *
     * İki ayrı sızıntıyı birlikte kapatır:
     * 1. **Gateway yolu düşünmeyi hiç ayıklamıyordu.** `finishLocal` ayıklıyor,
     *    `finish` ayıklamıyordu. Aynı model Ollama üzerinden gateway'e bağlanınca
     *    (gateway `reasoning_content` alanını ayırmayan sağlayıcıda) ham
     *    `<think>` balonda görünüyor, dışa aktarmaya ve panoya da gidiyordu.
     * 2. **Akış sırasında ham metin gösteriliyordu.** Her iki yolda da `onToken`
     *    doğrudan `sb.toString()` yazıyordu; düşünme canlı canlı balonda akıyor,
     *    ancak bitişte kayboluyordu. Kullanıcı bu arada kopyalarsa düşünme
     *    metnini kopyalıyordu.
     *
     * Gateway'in ayrı kanaldan gönderdiği düşünme (`onThought` → [thoughtBuf]) ile
     * metnin içinden ayıklanan blok birleştirilir; iki kaynak birbirini ezmez.
     */
    private fun renderStreamed(): Pair<String, String> {
        val (inline, content) = ThinkingText.split(sb.toString())
        val streamed = thoughtBuf.toString().trim()
        val thoughts = listOf(streamed, inline)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        return thoughts to content
    }

    private fun updateLast(f: (ChatMessage) -> ChatMessage) {
        val i = messages.lastIndex
        if (i >= 0) messages[i] = f(messages[i])
    }

    /**
     * Seçili modelin araç yeteneği. Kullanıcı ayrı bir anahtar çevirmez:
     * destekleyen modelde ajan modu açılır, desteklemeyende kapalı kalır —
     * yoksa gateway boş araç turu döndürür ya da model uydurur.
     * Katalog gelmediyse (yedek liste) ajan açılmaz; tahminle araç çalıştırmayız.
     */
    /**
     * Seçili PC modeli görsel alabiliyor mu — Faz 12A.
     *
     * Canlı katalog gelmediyse (eski gateway ya da bağlantı yok) `false`:
     * bilmediğimizi "evet" saymak, görmeyen bir modele görsel gönderip
     * uydurma yanıt almak olurdu.
     */
    fun gatewayModelSeesImages(): Boolean =
        modelOptions().firstOrNull { it.id == settings.modelId }?.vision == true

    fun agenticForModel(model: String): Boolean =
        modelOptions().firstOrNull { it.model == model || it.id == model }?.tools == true

    /** Sohbet başlığındaki rozet için: şu anki seçim araç destekliyor mu. */
    fun currentModelSupportsTools(): Boolean = agenticForModel(resolveModel())

    /**
     * Araç adımlarını birleştirir. Gateway aynı adımı önce `done:false`,
     * sonra sonuçla birlikte `done:true` gönderir; ikinci kayıt öncekini
     * günceller, yenisini eklemez (web'deki onTool ile aynı mantık).
     */
    private fun mergeToolStep(step: ToolStep) {
        updateLast { msg ->
            val list = msg.tools.toMutableList()
            if (step.done) {
                val idx = list.indexOfLast { it.name == step.name && (step.query.isEmpty() || it.query == step.query) }
                if (idx >= 0) {
                    val prev = list[idx]
                    list[idx] = step.copy(query = prev.query.ifEmpty { step.query })
                } else {
                    list.add(step)
                }
            } else {
                list.add(step)
            }
            msg.copy(tools = list)
        }
    }

    // ---------- ses ----------
    fun startListening() {
        if (busy) return
        if (!speech.isRecognitionAvailable) { voiceSub = "Cihazda konuşma tanıma yok"; return }
        voiceState = VoiceState.LISTENING
        voiceSub = "Dinliyorum…"
        speech.startListening(
            onRms = { level = it },
            onPartial = { voiceSub = it },
            onResult = { text ->
                if (text.isNotBlank()) {
                    messages.add(ChatMessage("user", text))
                    routeAndComplete(speakWhenDone = true)
                } else { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f }
            },
            onEnd = { err ->
                if (voiceState == VoiceState.LISTENING) {
                    voiceState = VoiceState.IDLE
                    voiceSub = err ?: DEFAULT_SUB
                    level = 0.08f
                }
            },
            preferOffline = executionPolicy.prefersOfflineVoice,
        )
    }

    fun stopListeningOrSpeaking() {
        when (voiceState) {
            VoiceState.LISTENING -> { speech.stopListening(); voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f }
            VoiceState.SPEAKING -> { speech.stopSpeaking(); voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f }
            else -> {}
        }
    }

    override fun onCleared() {
        // V2: yıkım anında son hâli yaz. Hiçbir yaşam döngüsü kancası
        // kaydetmiyordu; kullanıcı uygulamayı kapatınca kaydedilmemiş sohbet
        // yok oluyordu.
        saveCurrent(blocking = true)
        connectionProbes.invalidate()
        connectionCall?.cancel()
        // Model kataloğu çağrısı da iptal edilmeli; edilmediği için ViewModel
        // yıkıldıktan sonra da yanıt bekleyip onu canlı tutuyordu.
        modelsCall?.cancel()
        // Aynı gerekçe koşum geçmişi çağrısı için de geçerli (Faz 11).
        runsCall?.cancel()
        client.cancelStream(es)
        local.shutdown()
        // mDNS taraması ve süren takas çağrısı ViewModel'den uzun yaşamamalı.
        pairing.dispose()
        speech.destroy()
        super.onCleared()
    }

}
