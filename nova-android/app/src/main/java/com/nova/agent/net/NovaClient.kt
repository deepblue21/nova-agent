package com.nova.agent.net

import com.nova.agent.data.ChatMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NOVA Gateway istemcisi. /v1/chat/completions ucuna OpenAI-uyumlu istek atar,
 * SSE ile token token yanıt akıtır. Anahtarlar gateway'de; burada sadece gateway token'ı kullanılır.
 */
class NovaClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // streaming: zaman aşımı yok
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    interface Callbacks {
        fun onRoute(route: String) {}
        fun onToken(text: String) {}
        fun onDone() {}
        fun onError(message: String) {}
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun stream(
        baseUrl: String,
        token: String,
        model: String,
        effort: String,
        reasoning: Boolean,
        history: List<ChatMessage>,
        cb: Callbacks,
    ): EventSource? {
        val messages = JSONArray()
        for (m in history) {
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("effort", effort)
            .put("think", reasoning)
            .put("messages", messages)

        val gatewayBaseUrl = GatewayConnectionClient.canonicalBaseUrl(baseUrl)
        if (gatewayBaseUrl == null) {
            cb.onError("Gateway adresi geçersiz")
            return null
        }
        val url = gatewayBaseUrl.newBuilder()
            .addPathSegment("chat")
            .addPathSegment("completions")
            .build()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
        if (token.isNotBlank()) reqBuilder.addHeader("Authorization", "Bearer $token")
        val request = reqBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                response.header("x-nova-route")?.let { cb.onRoute(it) }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                parseDelta(data)?.let { cb.onToken(it) }
            }

            override fun onClosed(eventSource: EventSource) {
                cb.onDone()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val msg = when {
                    code == 401 -> "Yetkisiz — gateway token yanlış"
                    code == 403 -> "Model izinli değil"
                    code == 429 -> "Çok fazla istek"
                    code != null -> "Gateway hatası ($code)"
                    t != null -> "Bağlantı hatası: ${t.message}"
                    else -> "Bilinmeyen hata"
                }
                cb.onError(msg)
            }
        }

        return EventSources.createFactory(client).newEventSource(request, listener)
    }

    companion object {
        /** OpenAI-uyumlu SSE 'data' satırından içerik token'ı çıkarır; yoksa null. Saf/test edilebilir. */
        fun parseDelta(data: String): String? {
            if (data.isEmpty() || data == "[DONE]") return null
            return try {
                val obj = JSONObject(data)
                val choices = obj.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val content = choices.getJSONObject(0).optJSONObject("delta")?.optString("content", "").orEmpty()
                content.ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
    }
}
