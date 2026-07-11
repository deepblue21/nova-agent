package com.nova.agent.net

import com.nova.agent.feature.tasks.MobileConfirmation
import com.nova.agent.feature.tasks.MobileTask
import com.nova.agent.feature.tasks.MobileTaskEvent
import com.nova.agent.feature.tasks.MobileTaskStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject

class MobileTaskClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build(),
) {

    interface EventCallbacks {
        fun onEvent(event: MobileTaskEvent)
        fun onClosed()
        fun onError(message: String, recoverable: Boolean)
    }

    fun createTask(
        baseUrl: String,
        token: String,
        prompt: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        val body = JSONObject().put("prompt", prompt).toString().toRequestBody(JSON_MEDIA_TYPE)
        executeTask(request(baseUrl, token, "/mobile/tasks").post(body).build(), callback)
    }

    fun getTask(
        baseUrl: String,
        token: String,
        taskId: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        executeTask(request(baseUrl, token, "/mobile/tasks/$taskId").get().build(), callback)
    }

    fun command(
        baseUrl: String,
        token: String,
        taskId: String,
        command: String,
        note: String = "",
        callback: (Result<MobileTask>) -> Unit,
    ) {
        val body = JSONObject().put("command", command).put("note", note).toString().toRequestBody(JSON_MEDIA_TYPE)
        executeTask(request(baseUrl, token, "/mobile/tasks/$taskId/commands").post(body).build(), callback)
    }

    fun resolveConfirmation(
        baseUrl: String,
        token: String,
        taskId: String,
        confirmationId: String,
        decision: String,
        callback: (Result<MobileTask>) -> Unit,
    ) {
        val body = JSONObject().put("decision", decision).toString().toRequestBody(JSON_MEDIA_TYPE)
        val path = "/mobile/tasks/$taskId/confirmations/$confirmationId"
        executeTask(request(baseUrl, token, path).post(body).build(), callback)
    }

    fun streamEvents(
        baseUrl: String,
        token: String,
        taskId: String,
        lastEventId: String?,
        callbacks: EventCallbacks,
    ): EventSource {
        val builder = request(baseUrl, token, "/mobile/tasks/$taskId/events").get()
        if (!lastEventId.isNullOrBlank()) builder.header("Last-Event-ID", lastEventId)

        return EventSources.createFactory(client).newEventSource(
            builder.build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    parseEvent(data, id)?.let(callbacks::onEvent)
                }

                override fun onClosed(eventSource: EventSource) {
                    callbacks.onClosed()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val code = response?.code
                    callbacks.onError(
                        if (code == null) "Bağlantı hatası" else userMessageForHttp(code),
                        code == null || code == 429 || code >= 500,
                    )
                }
            },
        )
    }

    private fun request(baseUrl: String, token: String, path: String): Request.Builder {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        return builder
    }

    private fun executeTask(request: Request, callback: (Result<MobileTask>) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Bağlantı hatası", e)))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { result ->
                    if (!result.isSuccessful) {
                        callback(Result.failure(IllegalStateException(userMessageForHttp(result.code))))
                        return
                    }
                    val task = parseTask(result.body?.string().orEmpty())
                    if (task == null) {
                        callback(Result.failure(IllegalStateException("Geçersiz görev yanıtı")))
                    } else {
                        callback(Result.success(task))
                    }
                }
            }
        })
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseTask(data: String): MobileTask? {
            return try {
                val objectValue = JSONObject(data)
                val id = objectValue.optString("id").trim()
                if (id.isEmpty()) null else MobileTask(
                    id = id,
                    prompt = objectValue.optString("prompt"),
                    status = MobileTaskStatus.fromWire(objectValue.optString("status")),
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseEvent(data: String, sseId: String?): MobileTaskEvent? {
            if (data.isBlank() || data == "[DONE]") return null
            return try {
                val objectValue = JSONObject(data)
                val id = objectValue.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: sseId.orEmpty()
                val taskId = objectValue.optString("task_id").trim()
                val type = objectValue.optString("type").trim()
                if (id.isBlank() || taskId.isEmpty() || type.isEmpty()) return null

                val payload = objectValue.optJSONObject("payload") ?: JSONObject()
                val confirmation = if (type == "confirmation.requested") {
                    val confirmationId = payload.optString("confirmation_id").trim()
                    confirmationId.takeIf { it.isNotEmpty() }?.let {
                        MobileConfirmation(
                            id = it,
                            riskLevel = payload.optString("risk_level"),
                            actionSummary = payloadSummary(payload, type),
                        )
                    }
                } else {
                    null
                }

                MobileTaskEvent(
                    id = id,
                    taskId = taskId,
                    type = type,
                    summary = payloadSummary(payload, type),
                    confirmation = confirmation,
                )
            } catch (_: Exception) {
                null
            }
        }

        fun userMessageForHttp(code: Int): String = when (code) {
            401 -> "Yetkisiz erişim"
            404 -> "Görev bulunamadı"
            409 -> "Görev durumu değişti; tekrar deneyin"
            429 -> "Çok fazla istek; daha sonra tekrar deneyin"
            else -> "Görev servisi hatası"
        }

        private fun payloadSummary(payload: JSONObject, fallback: String): String {
            val explicit = payload.optString("summary").ifBlank {
                payload.optString("action_summary")
            }
            if (explicit.isNotBlank()) return explicit

            when (val action = payload.opt("action")) {
                is String -> if (action.isNotBlank()) return action
                is JSONObject -> action.optString("summary")
                    .ifBlank { action.optString("target") }
                    .takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

            return payload.optString("status").ifBlank { fallback }
        }
    }
}
