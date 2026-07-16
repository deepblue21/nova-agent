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
import com.nova.agent.data.EFFORTS
import com.nova.agent.data.MODELS
import com.nova.agent.data.Mode
import com.nova.agent.data.PendingFallback
import com.nova.agent.data.SettingsStore
import com.nova.agent.data.VoiceState
import com.nova.agent.llm.EngineRouter
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.LocalLlmController
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
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.sse.EventSource

private const val DEFAULT_SUB = "Konuşmak için mikrofona dokun"

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
    }

    // ---------- ayarlar ----------
    fun setModel(id: String) = persist(settings.copy(modelId = id))
    fun setEffort(id: String) = persist(settings.copy(effort = id))
    fun setReasoning(enabled: Boolean) = persist(settings.copy(reasoning = enabled))
    fun toggleReasoning() = setReasoning(!settings.reasoning)

    val executionPolicy: ExecutionPolicy
        get() = ExecutionPolicy.fromId(settings.executionPolicy)

    /** Yalnız Faz 1'de açık politikalar seçilebilir; diğerleri arayüzde pasiftir. */
    fun setExecutionPolicy(policy: ExecutionPolicy) {
        if (policy.selectableInPhase1) persist(settings.copy(executionPolicy = policy.id))
    }

    fun setLocalModel(id: String) {
        if (LocalModelCatalog.byId(id) != null) persist(settings.copy(localModelId = id))
    }

    fun setLocalThinking(enabled: Boolean) = persist(settings.copy(localThinking = enabled))
    fun setTheme(id: String) = persist(settings.copy(themeId = id))

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
        stop()
        messages.clear()
        pendingFallback = null
    }

    /**
     * Politikaya göre istemi yönlendirir. LOCAL_FIRST'te yerel model yoksa
     * veya hata verirse istem SESSİZCE dışarı gönderilmez; izin kartı çıkar.
     */
    private fun routeAndComplete(speakWhenDone: Boolean) {
        pendingFallback = null
        val spec = activeLocalSpec()
        when (val decision = EngineRouter.decide(executionPolicy, spec.id, local.isInstalled(spec.id))) {
            is RouteDecision.Gateway -> complete(messages.toList(), speakWhenDone)
            is RouteDecision.Local -> completeLocal(spec, speakWhenDone)
            is RouteDecision.LocalNeedsSetup -> {
                pendingFallback = PendingFallback(decision.reason)
                if (speakWhenDone) {
                    voiceState = VoiceState.IDLE
                    voiceSub = decision.reason
                    level = 0.08f
                }
            }
        }
    }

    /** Yerel hata sonrası kullanıcı onayıyla istemi PC Gateway'e gönderir. */
    fun approveFallback() {
        if (pendingFallback == null || busy) return
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

    private fun resolveModel(): String = MODELS.find { it.id == settings.modelId }?.model ?: "auto"

    /** Cihaz-üstü akışlı üretim. Gateway yoluna (complete) hiç dokunmaz. */
    private fun completeLocal(spec: LocalModelSpec, speakWhenDone: Boolean) {
        val prior = messages.toList()
        val prompt = prior.lastOrNull()?.takeIf { it.role == "user" }?.con