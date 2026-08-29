package com.nova.agent.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * `POST <baseUrl>/pair/claim` — tek kullanımlık kodu kalıcı anahtara çevirir.
 *
 * Bu sınıf bilinçli olarak İNCE bir ağ kabuğudur. Karar veren her şey
 * [PairingResponses] (saf, testli) ve [NetworkPolicy] (saf, testli) içindedir.
 */
class PairingClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {

    fun claim(
        baseUrl: String,
        code: String,
        callback: (PairingResult) -> Unit,
    ): Call? {
        val normalized = Pairing.normalizeCode(code)
        if (normalized.isEmpty()) {
            callback(PairingResult.Failure("Kod 8 karakter olmalı", "QR'ın altındaki kodu birebir gir."))
            return null
        }

        // Şifresiz bağlantı yalnız yerel ağa: anahtar açık internetten geçmesin.
        val verdict = NetworkPolicy.check(baseUrl)
        if (verdict is NetworkPolicy.Verdict.Blocked) {
            callback(PairingResult.Failure(verdict.reason, verdict.hint))
            return null
        }

        val url = PairingResponses.claimUrl(baseUrl)
        if (url == null) {
            callback(PairingResult.Failure("Adres geçersiz", "Adresin sonu /v1 olmalı."))
            return null
        }

        val body = JSONObject().put("code", normalized).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        return client.newCall(Request.Builder().url(url).post(body).build()).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val (message, hint) = GatewayConnectionClient.classifyFailure(e)
                    callback(PairingResult.Failure(message, hint))
                }

                override fun onResponse(call: Call, response: Response) = response.use {
                    callback(PairingResponses.interpret(it.code, it.body?.string().orEmpty(), baseUrl))
                }
            })
        }
    }
}
