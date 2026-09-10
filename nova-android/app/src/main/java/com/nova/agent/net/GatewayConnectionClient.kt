package com.nova.agent.net

import com.nova.agent.data.GatewayCatalog
import com.nova.agent.data.ModelOption
import com.nova.agent.data.PcAgentRun
import com.nova.agent.util.str
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
) {
    companion object {
        /**
         * Açılışta bağlantı sondası atılmalı mı — **saf**, testli.
         *
         * Yalnız `baseUrl`e bakmak yetmez: eskiden varsayılan adres dolu
         * (`10.0.2.2:8088`) olduğu için **her temiz kurulum** açılışta sonda
         * atıp "Bağlantı reddedildi" gösteriyordu. Yani kullanıcı, hiç
         * kurmadığı bir bağlantı için ilk ekranda hata görüyordu. Adres artık
         * boş geliyor (B5), ama kural belirteçte kalıyor: eşlemeden bir adres
         * gelse bile belirteç yoksa sonda atmanın anlamı yok.
         *
         * Gerçek bir Gateway bağlantısı `Bearer nv_…` belirteci ister; belirteç
         * yoksa bağlantı kurulmamış demektir ve sonda atmanın anlamı yoktur.
         */
        fun shouldProbeOnStart(baseUrl: String, token: String): Boolean =
            baseUrl.isNotBlank() && token.isNotBlank()

        /**
         * Henüz PC bağlantısı kurmamış kullanıcının gördüğü nötr durum.
         * Hata değil, bilgi: bu adım çevrimdışı kullanım için gerekmiyor.
         */
        fun notConfigured(): GatewayConnectionUiState = GatewayConnectionUiState(
            status = GatewayConnectionStatus.UNKNOWN,
            message = "PC bağlantısı kurulmadı",
            hint = "Telefonda çevrimdışı model kullanmak için gerekmez. " +
                "PC'ye bağlanmak istersen Ayarlar > PC bağlantısı.",
        )
    }
}

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

    /**
     * PC'ye devredilen işlerin geçmişini çeker — Faz 11.
     *
     * Hata durumunda `null` döner: "geçmiş yok" ile "geçmişe ulaşılamadı"
     * BAŞKA şeylerdir; boş liste döndürmek ikincisini birincisi gibi
     * gösterirdi ve kullanıcı işini kaybettiğini sanırdı.
     */
    fun fetchAgentRuns(
        baseUrl: String,
        token: String,
        callback: (List<PcAgentRun>?) -> Unit,
    ): Call? {
        val url = agentRunsUrl(baseUrl) ?: run { callback(null); return null }
        val builder = Request.Builder().url(url).get()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
        return client.newCall(builder.build()).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = callback(null)
                override fun onResponse(call: Call, response: Response) = response.use {
                    if (it.code != 200) return@use callback(null)
                    callback(parseAgentRuns(it.body?.string().orEmpty()))
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
                    "Sunucu adı bulunamadı. IP adresini (ör. http://192.168.1.20:18088/v1) " +
                    "PC'de start-horus çıktısından kopyala."
            e is NoRouteToHostException || e.cause is NoRouteToHostException ->
                "Ağ rotası yok: bu adrese giden yol bulunamadı" to
                    "Telefon farklı bir ağda olabilir (mobil veri?). Wi-Fi'ye geç veya Tailscale kullan."
            e is SSLException || e.cause is SSLException ->
                "Güvenli bağlantı (TLS) kurulamadı" to
                    "Yerel ağda https yerine http kullan (ör. http://192.168.1.20:18088/v1). " +
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
                        val id = o.str("id").takeIf { it.isNotBlank() } ?: continue
                        add(
                            ModelOption(
                                id = id,
                                name = o.str("name").ifBlank { id },
                                model = id,
                                group = o.str("group").ifBlank { "Diğer" },
                                desc = o.str("desc"),
                                available = o.optBoolean("available", true),
                                reason = o.str("reason"),
                                tools = o.optBoolean("tools", false),
                                toolsSource = o.str("toolsSource"),
                            ),
                        )
                    }
                }
                if (models.isEmpty()) return null
                val ollama = root.optJSONObject("ollama")
                GatewayCatalog(
                    models = models,
                    defaultModelId = root.str("defaultModel").takeIf { it.isNotBlank() },
                    ollamaOk = ollama?.optBoolean("ok", true) ?: true,
                    ollamaError = ollama?.str("error")?.takeIf { it.isNotBlank() },
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Gateway adresini kanonik hâle getirir — ve **ağ politikasını uygular** (Y1).
         *
         * Bu fonksiyon uygulamadaki TEK boğaz noktasıdır: `NovaClient.stream`,
         * `MobileTaskClient.request`, `test()` ve `modelsUrl()` hepsi buradan
         * geçer. Politikayı buraya koymak, tek tek çağrı noktalarını
         * güncellemeye göre daha güvenli: yarın eklenecek yeni bir istemci de
         * kendiliğinden kapsanır.
         *
         * Eskiden `NetworkPolicy` YALNIZ `PairingClient`te çağrılıyordu.
         * Manifestteki `usesCleartextTraffic="true"` yorumu "gerçek kontrol
         * kodda, NetworkPolicy genel bir IP'ye http'yi engeller" diyordu ama bu
         * güvence eşleme dışında hiçbir yerde geçerli değildi: Gelişmiş modda
         * elle `http://<genel-ip>/v1` + belirteç girilince `Bearer nv_…`
         * başlığı açık internete ŞİFRESİZ gidiyordu. Testler yeşil kalıyordu
         * çünkü saf fonksiyonu test ediyorlardı, çağrılıp çağrılmadığını değil.
         *
         * https her zaman serbest; kısıtlanan yalnız şifresiz http'nin yerel
         * olmayan bir adrese gitmesi.
         */
        fun canonicalBaseUrl(baseUrl: String): HttpUrl? {
            val parsed = baseUrl.trim().toHttpUrlOrNull() ?: return null
            if (parsed.scheme !in setOf("http", "https")) return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            if (parsed.query != null || parsed.fragment != null) return null
            val segments = parsed.pathSegments.filter { it.isNotBlank() }
            if (segments.isNotEmpty() && segments != listOf("v1")) return null
            val canonical = parsed.newBuilder().encodedPath("/v1").build()
            // Politika, OkHttp'nin ÇÖZDÜĞÜ host üzerinden uygulanır; kendi
            // dizge ayrıştırmamıza değil, gerçekte bağlanılacak adrese bakar.
            if (!NetworkPolicy.allowsHost(canonical.scheme, canonical.host)) return null
            return canonical
        }

        fun modelsUrl(baseUrl: String): HttpUrl? {
            return canonicalBaseUrl(baseUrl)?.newBuilder()?.addPathSegment("models")?.build()
        }

        /** `<base>/v1/agent/runs` — aynı politika boğazından geçer. */
        fun agentRunsUrl(baseUrl: String): HttpUrl? = canonicalBaseUrl(baseUrl)
            ?.newBuilder()?.addPathSegment("agent")?.addPathSegment("runs")?.build()

        /**
         * `GET /v1/agent/runs` yanıtını ayrıştırır — saf, JVM'de test edilebilir.
         *
         * Bozuk gövdede `null` (= ulaşılamadı), gövde geçerli ama liste boşsa
         * boş liste (= gerçekten koşum yok) döner. Kimliksiz satır atlanır:
         * silme çağrısı kimliğe dayanır, kimliksiz satır silinemez bir hayalet
         * olurdu.
         */
        fun parseAgentRuns(body: String): List<PcAgentRun>? {
            if (body.isBlank()) return null
            return try {
                val arr = org.json.JSONObject(body).optJSONArray("data") ?: return null
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.str("id").takeIf { it.isNotBlank() } ?: continue
                        add(
                            PcAgentRun(
                                id = id,
                                mode = o.str("mode"),
                                model = o.str("model"),
                                prompt = o.str("prompt"),
                                tools = o.str("tools"),
                                result = o.str("result"),
                                createdAt = o.optLong("created_at", 0L).coerceAtLeast(0L),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
