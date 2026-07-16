package com.nova.agent.llm.local

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Sabit sürümlü, SHA-256 doğrulamalı, sürdürülebilir model indirici.
 *
 * Kurallar:
 * - Yalnız HTTPS; HTTP'ye yönlendirme ağ katmanında reddedilir.
 * - İndirme .part dosyasına yapılır; SHA-256 beklenen değerle eşleşmeden
 *   model ASLA kurulmaz (atomik rename en sonda).
 * - Yarım indirme Range başlığıyla sürdürülür; sunucu Range desteklemezse
 *   baştan başlanır.
 * - İptal .part dosyasını korur (sürdürülebilir).
 */
class ModelDownloader(
    private val client: OkHttpClient = defaultClient(),
) {

    class Handle internal constructor() {
        @Volatile internal var cancelled = false
        @Volatile internal var call: Call? = null

        fun cancel() {
            cancelled = true
            call?.cancel()
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Failure(val message: String) : Result
    }

    fun newHandle(): Handle = Handle()

    /**
     * Bloklayan indirme; Dispatchers.IO üzerinde çağrılmalı.
     * [hfToken] yalnız kapılı modeller için gerekir; ilk isteğe Bearer olarak
     * eklenir (OkHttp, host değişen yönlendirmelerde Authorization başlığını
     * otomatik düşürür — token CDN'e sızmaz).
     * [onProgress] toplam indirilen bayt ve hedef boyutla, en fazla ~200 ms'de bir çağrılır.
     */
    fun download(
        spec: LocalModelSpec,
        store: LocalModelStore,
        handle: Handle,
        hfToken: String = "",
        onProgress: (Long, Long) -> Unit,
    ): Result {
        if (!spec.downloadUrl.startsWith("https://")) {
            return Result.Failure("Güvensiz indirme adresi engellendi (HTTPS gerekli)")
        }
        store.modelsDir.mkdirs()
        val part = store.partFile(spec)
        val target = store.modelFile(spec)

        // Bozuk kalıntıları temizle.
        if (part.exists() && part.length() > spec.sizeBytes) part.delete()
        if (target.exists() && target.length() != spec.sizeBytes) target.delete()
        if (target.exists()) return Result.Success

        var resumeFrom = if (part.exists()) part.length() else 0L

        val requestBuilder = Request.Builder().url(spec.downloadUrl)
        if (resumeFrom > 0) requestBuilder.header("Range", rangeHeader(resumeFrom))
        if (hfToken.isNotBlank()) requestBuilder.header("Authorization", "Bearer ${hfToken.trim()}")

        val call = client.newCall(requestBuilder.build())
        handle.call = call
        if (handle.cancelled) return Result.Cancelled

        try {
            call.execute().use { response ->
                when {
                    response.code == 206 -> Unit // kaldığı yerden sürüyor
                    response.code == 200 -> {
                        // Sunucu Range'i yok saydı; baştan başla.
                        part.delete()
                        resumeFrom = 0L
                    }
                    response.code == 401 || response.code == 403 -> return Result.Failure(
                        "Bu model kapılı (lisans onayı gerekli). HF hesabınla modelin sayfasında " +
                            "lisansı onayla ve Ayarlar'daki Hugging Face token'ını kontrol et. " +
                            "(HTTP ${response.code})",
                    )
                    else -> return Result.Failure("İndirme hatası (HTTP ${response.code})")
                }

                val body = response.body ?: return Result.Failure("Boş yanıt gövdesi")

                // Özet, dosyanın TAMAMI üzerinden hesaplanır: önce mevcut parça.
                val digest = MessageDigest.getInstance("SHA-256")
                if (resumeFrom > 0) {
                    part.inputStream().use { updateDigest(digest, it) }
                }

                var written = resumeFrom
                var lastTick = 0L
                FileOutputStream(part, resumeFrom > 0).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    val stream = body.byteStream()
                    while (true) {
                        if (handle.cancelled) return Result.Cancelled
                        val n = stream.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 200) {
                            lastTick = now
                            onProgress(written, spec.sizeBytes)
                        }
                    }
                    out.flush()
                }
                onProgress(written, spec.sizeBytes)

                if (written != spec.sizeBytes) {
                    return Result.Failure(
                        "İndirme eksik kaldı (${written}/${spec.sizeBytes} bayt). Tekrar denenebilir.",
                    )
                }

                val hex = LocalModelStore.toHex(digest.digest())
                if (!hex.equals(spec.sha256, ignoreCase = true)) {
                    part.delete()
                    return Result.Failure("SHA-256 doğrulanamadı; dosya kurulmadı. İndirme baştan alınmalı.")
                }

                // Atomik kurulum: önce hedefi temizle, sonra yeniden adlandır.
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    return Result.Failure("Model dosyası yerine taşınamadı")
                }
                store.writeMarker(spec)
                return Result.Success
            }
        } catch (e: IOException) {
            return if (handle.cancelled) {
                Result.Cancelled
            } else {
                Result.Failure("Bağlantı hatası: ${e.message ?: "bilinmiyor"}. Kaldığı yerden sürdürülebilir.")
            }
        } finally {
            handle.call = null
        }
    }

    companion object {
        /** Range başlığı değeri; saf ve test edilebilir. */
        fun rangeHeader(existingBytes: Long): String = "bytes=$existingBytes-"

        private fun updateDigest(digest: MessageDigest, input: InputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // büyük dosya: toplam süre sınırsız
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (!request.url.isHttps) {
                    throw IOException("Güvensiz (HTTP) yönlendirme engellendi: ${request.url.host}")
                }
                chain.proceed(request)
            }
            .build()
    }
}
