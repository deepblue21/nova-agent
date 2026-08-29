package com.nova.agent.net

import com.nova.agent.util.str
import org.json.JSONObject

/** Eşleme takasının sonucu. */
sealed interface PairingResult {
    /** Kalıcı API anahtarı alındı. Anahtar yalnız burada görünür, loglanmaz. */
    data class Paired(val apiKey: String, val baseUrl: String, val label: String) : PairingResult
    data class Failure(val message: String, val hint: String = "") : PairingResult
}

/**
 * Eşleme yanıtı çözümlemesi — SAF, JVM'de test edilebilir.
 *
 * [PairingClient] yalnız ağ kabuğudur; karar veren mantık burada durur, böylece
 * her HTTP durumu için verilen mesaj/ipucu birim testiyle kilitlenir.
 * (`GatewayConnectionClient.classifyFailure` ile aynı desen.)
 */
object PairingResponses {

    /** `http://host:18088/v1` → `http://host:18088/v1/pair/claim`. */
    fun claimUrl(baseUrl: String?): String? {
        val b = (baseUrl ?: "").trim().trimEnd('/')
        if (b.isEmpty()) return null
        if (!b.startsWith("http://", true) && !b.startsWith("https://", true)) return null
        if (NetworkPolicy.hostOf(b).isEmpty()) return null
        return "$b/pair/claim"
    }

    /** Gövdeden API anahtarını çıkarır. Bozuk/eksikte `null` — uydurma yok. */
    fun parseApiKey(body: String?): String? {
        val raw = (body ?: "").trim()
        if (raw.isEmpty()) return null
        return try {
            JSONObject(raw).str("api_key", "").trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun parseLabel(body: String?): String = try {
        JSONObject((body ?: "").trim()).str("label", "").trim().ifEmpty { "telefon" }
    } catch (_: Exception) {
        "telefon"
    }

    /** HTTP durumunu kullanıcıya dönük sonuca çevirir. */
    fun interpret(status: Int, body: String, baseUrl: String): PairingResult = when (status) {
        200 -> {
            val key = parseApiKey(body)
            if (key == null) {
                PairingResult.Failure(
                    "Sunucu beklenen yanıtı vermedi",
                    "Adres bir Horus Gateway olmayabilir. QR'ı tekrar okut.",
                )
            } else {
                PairingResult.Paired(key, baseUrl.trim().trimEnd('/'), parseLabel(body))
            }
        }
        400 -> PairingResult.Failure(
            "Kod biçimi geçersiz",
            "QR'ın altındaki 8 karakteri birebir gir (O/0 ve I/1 karışıklığı otomatik düzeltilir).",
        )
        404 -> PairingResult.Failure(
            "Bu kod tanınmadı",
            "PC'de kod üretildikten sonra gateway yeniden başlatıldıysa kod düşmüş olabilir. " +
                "PC'de start-horus'u tekrar çalıştırıp yeni bir kod üret.",
        )
        410 -> PairingResult.Failure(
            "Kodun süresi doldu veya zaten kullanıldı",
            "Kodlar 5 dakika geçerli ve tek kullanımlıktır. PC'de yeni bir kod üret.",
        )
        429 -> PairingResult.Failure(
            "Çok fazla başarısız deneme",
            "Birkaç dakika bekle, sonra PC'de yeni bir kod üretip tekrar dene.",
        )
        503 -> PairingResult.Failure(
            "Eşleme bu sunucuda kapalı",
            "PC'de PAIRING_ENABLED=0 ayarlanmış. Ayarı kaldırıp gateway'i yeniden başlat.",
        )
        in 500..599 -> PairingResult.Failure(
            "Gateway iç hata verdi ($status)",
            "PC'de 'docker compose logs gateway' ile son hatayı kontrol et.",
        )
        else -> PairingResult.Failure("Eşleme başarısız ($status)")
    }
}
