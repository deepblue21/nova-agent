package com.nova.agent.net

import com.nova.agent.data.GatewayCatalog
import com.nova.agent.data.ModelOption
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
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
    /** Nedene özel çözüm önerisi; boşsa gösterilmez. */
    val hint: String = "",
)

sealed interface GatewayConnectionResult {
    data object Ready : GatewayConnectionResult
    data object AuthRequired : GatewayConnectionResult
    data object InvalidUrl : GatewayConnectionResult
    data class Failure(val message: String, val hint: String = "") : GatewayConnectionResult
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
                override fun onFailure(call: Call, e: IOException) {
                    val (message, hint) = classifyFailure(e)
                    callback(GatewayConnectionResult.Failure(message, hint))
                }
                override fun onResponse(call: Call, response: Response) = response.use {
                    callback(
                        when (it.code) {
                            200 -> GatewayConnectionResult.Ready
                            401, 403 -> GatewayConnectionResult.AuthRequired
                            404 -> GatewayConnectionResult.Failure(
                                "Sunucu bulundu ama /v1/models yok (404)",
                                "Adres bir Gateway olmayabilir. Adresin sonu /v1 olmalı; " +
                                    "başka bir servisin portunu yazmış olabilirsin.",
                            )
                            in 500..599 -> GatewayConnectionResult.Failure(
                                "Gateway iç hata verdi (${it.code})",
                                "PC'de 'docker compose logs gateway' ile son hatayı kontrol et.",
                            )
                            else -> GatewayConnectionResult.Failure("Gateway yanıt vermedi (${it.code})")
                        },
                    )
                }
            })
        }
    }

    /**
     * Canlı model listesini çeker. Hata durumunda `null` döner; çağıran taraf
     * yedek listeye düşer (uydurma model gösterilmez).
     */
    fun fetchModels(
        baseUrl: String,
        token: String,
        callback: (GatewayCatalog?) -> Unit,
    ): Call? {
        val url = modelsUrl(baseUrl) ?: run { callback(null); return null }
        val builder = Request.Builder().url(url).get()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
        return client.newCall(builder.build()).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = callback(null)
                override fun onResponse(call: Call, response: Response) = response.use {
                    if (it.code != 200) return@use callback(null)
                    callback(parseCatalog(it.body?.string().orEmpty()))
                }
            })
        }
    }

    companion object {
        /**
         * Ağ hatasını katmanına göre sınıflar ve kullanıcıya nedene özel,
         * eyleme dönük Türkçe (mesaj, ipucu) çifti döndürür. Saf/JVM-testli.
         */
        fun classifyFailure(e: Throwable): Pair<String, String> = when {
            e is SocketTimeoutException || e.cause is SocketTimeoutException ->
                "Zaman aşımı: PC'ye ulaşılamadı" to
                    "Telefon ile PC aynı ağda mı? PC'de gateway'i telefona açmak için " +
                    "PowerShell'de .\\scripts\\start-horus.ps1 -Lan (veya -Tailscale) çalıştır. " +
                    "Güvenlik duvarı da bağlantıyı engelliyor olabilir."
            e is ConnectException || e.cause is ConnectException ->
                "Bağlantı reddedildi: adres bulundu ama bu portta dinleyen yok" to
                    "IP doğru görünüyor; port yanlış olabilir ya da gateway yalnız " +
                    "127.0.0.1'e bağlı. PC'de .\\scripts\\start-horus.ps1 -Lan ile başlat " +
                    "ve ekranda yazan adresi birebir gir."
            e is UnknownHostException || e.cause is UnknownHostException ->
                "Adres çözülemedi" to
                    "Sunucu adı bulunamadı. IP adresini (ör. http://192.168.1.20:8088/v1) " +
                    "PC'de start-horus çıktısından kopyala."
            e is NoRouteToHostException || e.cause is NoRouteToHostException ->
                "Ağ rotası yok: bu adrese giden yol bulunamadı" to
                    "Telefon farklı bir ağda olabilir (mobil veri?). Wi-Fi'ye geç veya Tailscale kullan."
            e is SSLException || e.cause is SSLException ->
                "Güvenli bağlantı (TLS) kurulamadı" to
                    "Yerel ağda https yerine http kullan (ör. http://192.168.1.20:8088/v1). " +
                    "Alan adı + gerçek sertifika varsa https kalabilir."
            else ->
                "PC Gateway'e ulaşılamadı" to
                    (e.message?.takeIf { it.isNotBlank() }
                        ?.let { "Teknik ayrıntı: $it" } ?: "")
        }

        /**
         * `GET /v1/models` yanıtını ayrıştırır — saf ve JVM'de test edilebilir.
         * Bozuk/eksik gövdede `null` döner; kısmi veri uydurulmaz.
         */
        fun parseCatalog(body: String): GatewayCatalog? {
            if (body.isBlank()) return null
            return try {
                val root = org.json.JSONObject(body)
                val arr = root.optJSONArray("data") ?: return null
                val models = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                        add(
                            ModelOption(
                                id = id,
                                name = o.optString("name").ifBlank { id },
                                model = id,
                                group = o.optString("group").ifBlank { "Diğer" },
                                desc = o.optString("desc"),
                                available = o.optBoolean("available", true),
                                reason = o.optString("reason"),
                                tools = o.optBoolean("tools", false),
                                toolsSource = o.optString("toolsSource"),
                            ),
                        )
                    }
                }
                if (models.isEmpty()) return null
                val ollama = root.optJSONObject("ollama")
                GatewayCatalog(
                    models = models,
                    defaultModelId = root.optString("defaultModel").takeIf { it.isNotBlank() },
                    ollamaOk = ollama?.optBoolean("ok", true) ?: true,
                    ollamaError = ollama?.optString("error")?.takeIf { it.isNotBlank() },
                )
            } catch (_: Exception) {
                null
            }
        }

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
