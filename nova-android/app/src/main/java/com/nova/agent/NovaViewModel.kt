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
import com.nova.agent.data.SettingsStore
import com.nova.agent.data.VoiceState
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

    val messages = mutableStateListOf<ChatMessage>()
    var settings by mutableStateOf(AppSettings()); private set
    var connectionState by mutableStateOf(GatewayConnectionUiState()); private set
    var busy by mutableStateOf(false); private set
    var mode by mutableStateOf(Mode.TASKS)
    var voiceState by mutableStateOf(VoiceState.IDLE); private set
    var voiceSub by mutableStateOf(DEFAULT_SUB); private set
    var level by mutableStateOf(0.08f); private set

    private var es: EventSource? = null
    private var connectionCall: Call? = null
    private val sb = StringBuilder()

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

    fun saveConnection(baseUrl: String, token: String) {
        val updated = settings.copy(baseUrl = baseUrl.trim(), token = token.trim())
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

    fun currentModelName(): String = MODELS.find { it.id == settings.modelId }?.name ?: "auto"
    fun currentEffortName(): String = EFFORTS.find { it.id == settings.effort }?.name ?: ""

    // ---------- sohbet ----------
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(ChatMessage("user", t))
        complete(messages.toList(), speakWhenDone = false)
    }

    fun regenerate() {
        if (busy) return
        while (messages.isNotEmpty() && messages.last().role == "assistant") messages.removeAt(messages.lastIndex)
        if (messages.none { it.role == "user" }) return
        complete(messages.toList(), speakWhenDone = false)
    }

    fun stop() {
        es?.cancel(); es = null
        busy = false
        updateLast { it.copy(streaming = false) }
        if (voiceState != VoiceState.IDLE) { voiceState = VoiceState.IDLE; voiceSub = DEFAULT_SUB }
    }

    fun newChat() {
        stop()
        messages.clear()
    }

    private fun resolveModel(): String = MODELS.find { it.id == settings.modelId }?.model ?: "auto"

    private fun complete(history: List<ChatMessage>, speakWhenDone: Boolean) {
        sb.clear()
        messages.add(ChatMessage("assistant", "", streaming = true))
        busy = true
        if (speakWhenDone) { voiceState = VoiceState.THINKING; voiceSub = "Düşünüyor…"; level = 0.28f }

        es = client.stream(
            baseUrl = settings.baseUrl,
            token = settings.token,
            model = resolveModel(),
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
                    complete(messages.toList(), speakWhenDone = true)
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
        speech.destroy()
        super.onCleared()
    }
}
