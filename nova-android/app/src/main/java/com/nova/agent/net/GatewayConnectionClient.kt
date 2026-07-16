package com.nova.agent.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class GatewayConnectionStatus { UNKNOWN, CHECKING, READY, AUTH_REQUIRED, UNREACHABLE, INVALID_URL }

data class GatewayConnectionUiState(
    val status: GatewayConnectionStatus = GatewayConnectionStatus.UNKNOWN,
    val message: String = "Bağlantı henüz test edilmedi",
)

sealed interface GatewayConnectionResult {
    data object Ready : GatewayConnectionResult
    data object AuthRequired : GatewayConnectionResult
    data object InvalidUrl : GatewayConnectionResult
    data class Failure(val message: String) : GatewayConnectionResult
}

class GatewayConnectionClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    fun test(baseUrl: String, token: String, callback: (GatewayConnectionResult) -> Unit): Call? {
        val url = modelsUrl(baseUrl) ?: run { callback(GatewayConnectionResult.InvalidUrl); return null }
        val builder = Request.Builder().url(url).get()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
        return client.newCall(builder.build()).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = callback(
                    GatewayConnectionResult.Failure("PC Gateway'e ulaşılamadı"),
                )
                override fun onResponse(call: Call, response: Response) = response.use {
                    callback(
                        when (it.code) {
                            200 -> GatewayConnectionResult.Ready
                            401, 403 -> GatewayConnectionResult.AuthRequired
                            else -> GatewayConnectionResult.Failure("Gateway yanıt vermedi (${it.code})")
                        },
                    )
                }
            })
        }
    }

    companion object {
        fun canonicalBaseUrl(baseUrl: String): HttpUrl? {
            val parsed = baseUrl.trim().toHttpUrlOrNull() ?: return null
            if (parsed.scheme !in setOf("http", "https")) return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            if (parsed.query != null || parsed.fragment != null) return null
            val segments = parsed.pathSegments.filter { it.isNotBlank() }
            if (segments.isNotEmpty() && segments != listOf("v1")) return null
            return parsed.newBuilder().encodedPath("/v1").build()
        }

        fun modelsUrl(baseUrl: String): HttpUrl? {
            return canonicalBaseUrl(baseUrl)?.newBuilder()?.addPathSegment("models")?.build()
        }
    }
}
