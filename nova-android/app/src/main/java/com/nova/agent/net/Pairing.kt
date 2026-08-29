package com.nova.agent.net

import java.net.URLDecoder

/**
 * QR'dan okunan eşleme bilgisi.
 *
 * `baseUrl` her zaman `/v1` ile biter — [GatewayConnectionClient] ve
 * [NovaClient] bugünkü varsayımlarını hiç değiştirmeden kullanabilir.
 */
data class PairUri(
    val version: Int,
    val code: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val baseUrl: String,
    val name: String,
)

/**
 * Eşleme kodu / URI ayrıştırma — SAF, JVM'de test edilebilir.
 *
 * Gateway tarafındaki `gateway/lib/pairing.mjs` ile birebir aynı davranmalı.
 * İki taraf da aynı testleri (aynı vakalarla) koşar; biri değişirse diğeri de
 * değişmek zorunda.
 */
object Pairing {

    /** Crockford Base32 — I, L, O, U yok (görsel karışma engellenir). */
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val CODE_LENGTH: Int = 8

    const val SCHEME_PREFIX: String = "horus://pair?"

    /**
     * Kullanıcı girdisini kanonik biçime çevirir: büyük harf, ayraçlar atılır,
     * görsel ikizler eşlenir (O→0, I/L→1). Geçersizse boş dizge — kısmi kod
     * uydurulmaz.
     */
    fun normalizeCode(input: String?): String {
        val raw = (input ?: "")
            .uppercase()
            .filterNot { it.isWhitespace() || it == '-' || it == '_' || it == '.' }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .joinToString("")
        if (raw.length != CODE_LENGTH) return ""
        if (raw.any { it !in ALPHABET }) return ""
        return raw
    }

    /** İnsan gözü için "H7K2-9M4P". Geçersiz kodda girdi olduğu gibi döner. */
    fun formatCode(code: String?): String {
        val c = normalizeCode(code)
        if (c.isEmpty()) return code ?: ""
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    /**
     * Base URL kurar; her zaman `/v1` ile biter. Geçersiz girdide boş dizge.
     * IPv6 literalleri köşeli paranteze alınır.
     */
    fun baseUrl(host: String?, port: Int, tls: Boolean = false, path: String = "/v1"): String {
        val h = (host ?: "").trim()
        if (h.isEmpty()) return ""
        if (port !in 1..65535) return ""
        val authorityHost = if (h.contains(':') && !h.startsWith("[")) "[$h]" else h
        val scheme = if (tls) "https" else "http"
        val defaultPort = if (tls) 443 else 80
        val authority = if (port == defaultPort) authorityHost else "$authorityHost:$port"
        val p = path.ifBlank { "/v1" }.let { if (it.startsWith("/")) it else "/$it" }
        return "$scheme://$authority$p"
    }

    /**
     * `horus://pair?v=1&code=...&host=...&port=...` ayrıştırır.
     * Tanınmayan/bozuk girdide `null` — kısmi veri uydurulmaz, kullanıcıya
     * "bu QR bize ait değil" demek doğrusudur.
     */
    fun parsePairUri(uri: String?): PairUri? {
        val raw = (uri ?: "").trim()
        if (!raw.startsWith(SCHEME_PREFIX, ignoreCase = true)) return null

        val params = parseQuery(raw.substring(raw.indexOf('?') + 1))

        val version = params["v"] ?: "1"
        if (version != "1") return null

        val code = normalizeCode(params["code"])
        if (code.isEmpty()) return null

        val host = (params["host"] ?: "").trim()
        val port = params["port"]?.toIntOrNull() ?: return null
        val tls = params["tls"] == "1"
        val base = baseUrl(host, port, tls)
        if (base.isEmpty()) return null

        return PairUri(
            version = 1,
            code = code,
            host = host,
            port = port,
            tls = tls,
            baseUrl = base,
            name = (params["name"] ?: "").trim().take(64),
        )
    }

    /** Basit sorgu dizesi ayrıştırıcı — yinelenen anahtarda ilki kazanır. */
    private fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = if (eq < 0) pair else pair.substring(0, eq)
            val value = if (eq < 0) "" else pair.substring(eq + 1)
            val decodedKey = urlDecode(key)
            if (decodedKey.isEmpty() || out.containsKey(decodedKey)) continue
            out[decodedKey] = urlDecode(value)
        }
        return out
    }

    private fun urlDecode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        // Bozuk yüzde-kodlaması: ham hâlini kullan, patlamaktansa.
        s
    }
}
