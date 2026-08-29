package com.nova.agent.util

import org.json.JSONObject

/**
 * `optString`'in JSON null'a karşı güvenli hâli — J1.
 *
 * Android'in `org.json`'u referans JVM uygulamasından **sessizce ayrılır**:
 * değer JSON null ise `optString(name, fallback)` fallback döndürmez, `"null"`
 * DİZESİNİ döndürür (`JSONObject.NULL.toString()`). Sağlayıcılar boş alanları
 * atlamak yerine `null` yazdığı için bu pratikte sık:
 *
 * - `{"route": null}` → sohbet balonunun altında "→ null" yazısı,
 * - `{"reasoning_content": null}` → her deltada bir "null", düşünme panelinde
 *   "nullnullnull…".
 *
 * Testler bunu yakalayamaz: JVM birim testinde kullanılan `org.json` referans
 * uygulaması DOĞRU davranır, hata yalnızca cihazda ortaya çıkar. O yüzden
 * çağrı noktalarının tamamı bu yardımcıdan geçiyor; ham `optString` kullanımı
 * `SourceGuardTest` ile kilitli.
 */
internal fun JSONObject.str(name: String, fallback: String = ""): String =
    if (isNull(name)) fallback else optString(name, fallback)
