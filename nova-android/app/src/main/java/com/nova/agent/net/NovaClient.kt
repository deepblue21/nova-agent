package com.nova.agent.net

import com.nova.agent.data.ChatMessage
import com.nova.agent.data.ToolSource
import com.nova.agent.data.ToolStep
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
        /** Gerçek düşünme token'ları (gateway `reasoning_content` relay'i). */
        fun onThought(text: String) {}
        /** Ajan araç adımı (gateway `tool_step` deltası). */
        fun onTool(step: ToolStep) {}
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
        /**
         * Araç çağırma döngüsü. Seçili model araç destekliyorsa açılır —
         * yoksa gateway boş araç turu döndürür ya da model uydurur.
         */
        agent: Boolean = false,
        /** Görevi paralel alt-ajanlara böl (yalnız yerel modellerde anlamlı). */
        team: Boolean = false,
        /** Sunucu tarafı sohbet geçmişi; null ise yalnız yerelde tutulur. */
        conversationId: String? = null,
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
        if (agent) payload.put("agent", true)
        if (team) payload.put("team", true)
        if (!conversationId.isNullOrBlank()) payload.put("conversation_id", conversationId)

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
                val delta = parseStreamDelta(data) ?: return
                delta.thought?.let { cb.onThought(it) }
                delta.tool?.let { cb.onTool(it) }
                delta.content?.let { cb.onToken(it) }
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

    /** Tek bir SSE deltasından çıkarılabilen parçalar. */
    data class StreamDelta(
        val content: String? = null,
        val thought: String? = null,
        val tool: ToolStep? = null,
    )

    companion object {
        /** Geriye dönük yardımcı: yalnız içerik token'ı. */
        fun parseDelta(data: String): String? = parseStreamDelta(data)?.content

        /**
         * OpenAI-uyumlu SSE 'data' satırını ayrıştırır: içerik, düşünme
         * (`reasoning_content`) ve ajan araç adımı (`tool_step`).
         * Saf/test edilebilir — ağ ya da Android bağımlılığı yok.
         */
        fun parseStreamDelta(data: String): StreamDelta? {
            if (data.isEmpty() || data == "[DONE]") return null
            return try {
                val obj = JSONObject(data)
                val choices = obj.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null

                val content = delta.optString("content", "").ifEmpty { null }
                val thought = delta.optString("reasoning_content", "").ifEmpty { null }
                val tool = delta.optJSONObject("tool_step")?.let(::parseToolStep)

                if (content == null && thought == null && tool == null) null
                else StreamDelta(content = content, thought = thought, tool = tool)
            } catch (_: Exception) {
                null
            }
        }

        private fun parseToolStep(o: JSONObject): ToolStep? {
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
            val args = o.optJSONObject("args")
            val query = listOf("query", "location", "expression", "role")
                .firstNotNullOfOrNull { key -> args?.optString(key)?.takeIf { it.isNotBlank() } }
                .orEmpty()
            val sources = o.optJSONArray("sources")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optJSONObject(i) ?: continue
                        val url = s.optString("url")
                        val title = s.optString("title").ifBlank { url.ifBlank { "Kaynak" } }
                        add(ToolSource(title = title, url = url, index = s.optInt("n", 0)))
                    }
                }
            }.orEmpty()
            return ToolStep(name = name, query = query, done = o.optBoolean("done", false), sources = sources)
        }
    }
}
