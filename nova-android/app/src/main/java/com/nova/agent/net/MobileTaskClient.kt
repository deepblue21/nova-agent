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
    ): Call {
        val body = JSONObject().put("prompt", prompt).toString().toRequestBody(JSON_MEDIA_TYPE)
        return executeTask(request(baseUrl, token, "/mobile/tasks").post(body).build(), callback, taskCreation = true)
    }

    fun getTask(
        baseUrl: String,
        token: String,
        taskId: String,
        callback: (Result<MobileTask>) -> Unit,
    ): Call {
        return executeTask(request(baseUrl, token, "/mobile/tasks/$taskId").get().build(), callback)
    }

    fun command(
        baseUrl: String,
        token: String,
        taskId: String,
        command: String,
        note: String = "",
        callback: (Result<MobileTask>) -> Unit,
    ): Call {
        val body = JSONObject().put("command", command).put("note", note).toString().toRequestBody(JSON_MEDIA_TYPE)
        return executeTask(request(baseUrl, token, "/mobile/tasks/$taskId/commands").post(body).build(), callback)
    }

    fun resolveConfirmation(
        baseUrl: String,
        token: String,
        taskId: String,
        confirmationId: String,
        decision: String,
        callback: (Result<MobileTask>) -> Unit,
    ): Call {
        val body = JSONObject().put("decision", decision).toString().toRequestBody(JSON_MEDIA_TYPE)
        val path = "/mobile/tasks/$taskId/confirmations/$confirmationId"
        return executeTask(request(baseUrl, token, path).post(body).build(), callback)
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
        val url = GatewayConnectionClient.canonicalBaseUrl(baseUrl)
            ?.newBuilder()
            ?.addPathSegments(path.trimStart('/'))
            ?.build()
            ?: throw IllegalArgumentException("Gateway adresi geçersiz")
        val builder = Request.Builder().url(url)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        return builder
    }

    private fun executeTask(
        request: Request,
        callback: (Result<MobileTask>) -> Unit,
        taskCreation: Boolean = false,
    ): Call {
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException("Bağlantı hatası", e)))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { result ->
                    val responseBody = result.body?.string().orEmpty()
                    if (!result.isSuccessful) {
                        val message = if (taskCreation) {
                            userMessageForTaskCreationHttp(result.code, responseBody)
                        } else {
                            userMessageForHttp(result.code)
                        }
                        callback(Result.failure(IllegalStateException(message)))
                        return
                    }
                    val task = parseTask(responseBody)
                    if (task == null) {
                        callback(Result.failure(IllegalStateException("Geçersiz görev yanıtı")))
                    } else {
                        callback(Result.success(task))
                    }
                }
            }
        })
        return call
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
                val status = payload.optString("status").trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(MobileTaskStatus::fromWireOrNull)
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
                    status = status,
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

        private fun userMessageForTaskCreationHttp(code: Int, responseBody: String): String {
            if (code == 400 && hasStrictWorkerUnsupportedError(responseBody)) {
                return "Bu gorev emulator worker'inda desteklenmiyor"
            }
            return userMessageForHttp(code)
        }

        private fun hasStrictWorkerUnsupportedError(responseBody: String): Boolean = try {
            JSONObject(responseBody).optString("error") == STRICT_WORKER_UNSUPPORTED_ERROR
        } catch (_: Exception) {
            false
        }

        private const val STRICT_WORKER_UNSUPPORTED_ERROR = "task is not supported by this emulator worker"

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
