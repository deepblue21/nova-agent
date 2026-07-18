package com.nova.agent

import android.app.Application
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
import com.nova.agent.data.ConversationStore
import com.nova.agent.data.ConversationSummary
import com.nova.agent.data.ConversationText
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.MODELS
import com.nova.agent.data.Mode
import com.nova.agent.data.PendingFallback
import com.nova.agent.data.SettingsStore
import com.nova.agent.data.VoiceState
import com.nova.agent.llm.EngineRouter
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.HybridInputs
import com.nova.agent.llm.LocalLlmController
import com.nova.agent.llm.PrivacyClassifier
import com.nova.agent.llm.RouteDecision
import com.nova.agent.llm.ThinkingText
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.llm.local.LocalModelSpec
import com.nova.agent.llm.local.OnDeviceEngine
import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionResult
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.net.NovaClient
import com.nova.agent.voice.SpeechManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.sse.EventSource

private const val DEFAULT_SUB = "Konuşmak için mikrofona dokun"

/** PC'deki ajan koşusu için Gateway model kimliği (görev devri hedefi). */
private const val PC_AGENT_MODEL = "openclaw/default"

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
    val local = LocalLlmController(app, viewModelScope, ::onMain)

    /** Kalıcı sohbet geçmişi (Faz 5). Cihazda JSON. */
    private val convoStore = ConversationStore(File(app.filesDir, "conversations.json"))
    private var currentConversationId: String? = null
    private var currentCreatedAt: Long = 0L
    var history by mutableStateOf<List<ConversationSummary>>(emptyList()); private set
    var historyQuery by mutableStateOf(""); private set

    val messages = mutableStateListOf<ChatMessage>()
    var settings by mutableStateOf(AppSettings()); private set
    var connectionState by mutableStateOf(GatewayConnectionUiState()); private set
    var busy by mutableStateOf(false); private set
    var mode by mutableStateOf(Mode.KONTROL)
    var voiceState by mutableStateOf(VoiceState.IDLE); private set
    var voiceSub by mutableStateOf(DEFAULT_SUB); private set
    var level by mutableStateOf(0.08f); private set

    /** Yerel hata sonrası bekleyen izinli PC devri; onaysız istem dışarı çıkmaz. */
    var pendingFallback by mutableStateOf<PendingFallback?>(null); private set

    private var es: EventSource? = null
    private var connectionCall: Call? = null
    private val sb = StringBuilder()
    private var activeLocal = false

    init {
        speech.initTts()
        viewModelScope.launch {
            settings = store.load()
            if (settings.baseUrl.isNotBlank()) testConnection(settings.baseUrl, settings.token)
        }
        reloadHistory()
    }

    // ---------- sohbet geçmişi (Faz 5) ----------

    private fun reloadHistory() {
        val q = historyQuery
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (q.isBlank()) convoStore.list() else convoStore.search(q)
            }
            history = list
        }
    }

    fun setHistoryQuery(q: String) {
        historyQuery = q
        reloadHistory()
    }

    /** Aktif sohbeti (en az bir kullanıcı mesajı varsa) diske yazar. */
    private fun saveCurrent() {
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
    fun setTheme(id: String) = persist(settings.copy(themeId = id))
    fun setHfToken(token: String) = persist(settings.copy(hfToken = token.trim()))
    fun setHybridAutoFallback(enabled: Boolean) = persist(settings.copy(hybridAutoFallback = enabled))

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
                        )
                        GatewayConnectionResult.InvalidUrl -> GatewayConnectionUiState(
                            GatewayConnectionStatus.INVALID_URL,
                            "Gateway adresi geçersiz",
                        )
                        is GatewayConnectionResult.Failure -> GatewayConnectionUiState(
                            GatewayConnectionStatus.UNREACHABLE,
                            result.message,
                        )
                    }
                }
            }
        }
    }

    private fun persist(s: AppSettings) {
        settings = s
        viewModelScope.launch { store.save(s) }
    }

    fun currentModelName(): String = when (executionPolicy) {
        ExecutionPolicy.LOCAL_FIRST, ExecutionPolicy.LOCAL_ONLY -> activeLocalSpec().displayName
        else -> MODELS.find { it.id == settings.modelId }?.name ?: "auto"
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
        es?.cancel(); es = null
        if (activeLocal) {
            activeLocal = false
            local.cancelGenerate()
        }
        busy = false
        updateLast { it.copy(streaming = false) }
        if (voiceState != VoiceState.IDLE) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB }
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
        val lastPrompt = messages.lastOrNull { it.role == "user" }?.content ?: ""
        return EngineRouter.decideHybrid(
            HybridInputs(
                localModelInstalled = local.isInstalled(spec.id),
                promptChars = promptChars,
                batteryPercent = batteryPercent,
                charging = charging,
                gatewayReady = connectionState.status == GatewayConnectionStatus.READY,
                thermalSevere = local.thermalSevereNow(),
                privacySensitive = PrivacyClassifier.isSensitive(lastPrompt),
            ),
            localModelId = spec.id,
        )
    }

    /**
     * Hibritte yerel hata sonrası kalıcı kurala göre otomatik PC devri.
     * Kural kapalıysa veya PC hazır değilse devretmez (izin kartı kalır).
     */
    private fun autoHandoffAfterLocalError(): Boolean {
        if (executionPolicy != ExecutionPolicy.HYBRID || !settings.hybridAutoFallback) return false
        if (connectionState.status != GatewayConnectionStatus.READY) return false
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
     * Koşu, Gateway'in ajan geçmişine (/v1/agent/runs) otomatik kaydolur.
     */
    fun handoffToPcAgent() {
        if (busy) return
        if (executionPolicy == ExecutionPolicy.LOCAL_ONLY) return
        while (messages.isNotEmpty() && messages.last().role == "assistant") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.none { it.role == "user" }) return
        pendingFallback = null
        complete(messages.toList(), speakWhenDone = false, modelOverride = PC_AGENT_MODEL)
    }

    private fun resolveModel(): String = MODELS.find { it.id == settings.modelId }?.model ?: "auto"

    /** Cihaz-üstü akışlı üretim. Gateway yoluna (complete) hiç dokunmaz. */
    private fun completeLocal(spec: LocalModelSpec, speakWhenDone: Boolean) {
        val prior = messages.toList()
        val prompt = prior.lastOrNull()?.takeIf { it.role == "user" }?.content ?: return
        val history = prior.dropLast(1).map { it.role to it.content }

        sb.clear()
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
            cb = object : OnDeviceEngine.Callbacks {
                override fun onToken(text: String) {
                    if (!activeLocal) return
                    sb.append(text)
                    updateLast { it.copy(content = sb.toString()) }
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
                    val partial = sb.toString()
                    updateLast {
                        it.copy(
                            content = if (partial.isBlank()) "⚠️ $message" else "$partial\n\n⚠️ $message",
                            streaming = false,
                        )
                    }
                    // Sessiz devir yok: yalnız bildirim/izin kartı.
                    pendingFallback = PendingFallback(
                        message,
                        allowGateway = executionPolicy.allowsGatewayFallback,
                    )
                }
            },
        )
    }

    private fun finishLocal(speak: Boolean) {
        val (thoughts, content) = ThinkingText.split(sb.toString())
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
        messages.add(ChatMessage("assistant", "", streaming = true))
        busy = true
        if (speakWhenDone) { voiceState = VoiceState.THINKING; voiceSub = "Düşünüyor…"; level = 0.28f }

        es = client.stream(
            baseUrl = settings.baseUrl,
            token = settings.token,
            model = modelOverride ?: resolveModel(),
            effort = settings.effort,
            reasoning = settings.reasoning,
            history = history,
            cb = object : NovaClient.Callbacks {
                override fun onRoute(route: String) = onMain { updateLast { it.copy(route = route) } }
                override fun onToken(text: String) = onMain { sb.append(text); updateLast { it.copy(content = sb.toString()) } }
                override fun onDone() = onMain { finish(speakWhenDone) }
                override fun onError(message: String) = onMain {
                    sb.append(if (sb.isEmpty()) "⚠️ $message" else "\n\n⚠️ $message")
                    updateLast { it.copy(content = sb.toString(), streaming = false) }
                    busy = false
                    if (speakWhenDone) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB }
                }
            }
        )
    }

    private fun finish(speak: Boolean) {
        val text = sb.toString().ifBlank { "(boş yanıt)" }
        updateLast { it.copy(content = text, streaming = false) }
        busy = false
        es = null
        saveCurrent()
        if (speak) {
            voiceState = VoiceState.SPEAKING
            voiceSub = text.take(160)
            level = 0.5f
            speech.speak(text, onStart = {}, onDone = { onMain { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB; level = 0.08f } })
        }
    }

    private fun updateLast(f: (ChatMessage) -> ChatMessage) {
        val i = messages.lastIndex
        if (i >= 0) messages[i] = f(messages[i])
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
            }
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
        connectionProbes.invalidate()
        connectionCall?.cancel()
        es?.cancel()
        local.shutdown()
        speech.destroy()
        super.onCleared()
    }

}
