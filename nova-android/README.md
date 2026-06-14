# NOVA — Android İstemci

Gateway'e (OpenAI-uyumlu, SSE) bağlanan native Android uygulaması. Kotlin + Jetpack Compose.
Animasyonlu orb, sesli + sohbet modu, model/efor/düşünme seçimi, streaming yanıt.

> API anahtarları **gateway'de** durur. Bu istemci yalnızca gateway adresini ve `GATEWAY_TOKEN`'ı bilir.

---

## Açma & Derleme

Gradle wrapper (jar + `gradlew`/`gradlew.bat`, Gradle 8.9) artık repoya dahildir —
ayrıca `gradle wrapper` çalıştırmana gerek yok. Tek gereksinim **JDK 17** ve Android SDK
(Android Studio ikisini de sağlar).

**Android Studio ile:**
1. **Android Studio** (Ladybug / 2024.2+ önerilir) ile bu klasörü aç.
2. İlk açılışta Gradle senkronu çalışır (wrapper hazır).
3. Bir cihaz/emülatör seç → **Run**.

**Terminalden (JDK 17 kurulu):**
```bash
cd nova-android
./gradlew assembleDebug         # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug          # bağlı cihaza/emülatöre kur
```
Windows'ta `gradlew.bat assembleDebug`.

Sürüm uyumu: AGP 8.5.2 · Kotlin 2.0.21 · Compose BOM 2024.10.01 · compileSdk 35 · minSdk 26 · Gradle 8.9.
Android Studio daha yeni AGP isterse `build.gradle.kts` içindeki sürümleri kabul ettiği değerlere yükselt.

---

## Gateway'e Bağlanma

Uygulamada ⚙️ (sağ üst) → **Gateway Bağlantısı**:

| Alan | Değer |
|---|---|
| Base URL | `http://10.0.2.2:8088/v1` (emülatör) |
| Token | gateway'deki `GATEWAY_TOKEN` |

- **Emülatör:** `10.0.2.2`, makinenin `localhost`'una karşılık gelir.
- **Gerçek cihaz:** gateway'i internete açma. En temizi **Tailscale/WireGuard** — telefon ve PC aynı private ağda; Base URL'e Tailscale IP'sini yaz: `http://100.x.x.x:8088/v1`.
- Gateway `127.0.0.1`'e bağlıysa sadece aynı makineden erişilir; Tailscale arayüzünü dinletmek için gateway'i `HOST=100.x.x.x` ile başlat (ya da `0.0.0.0` + güçlü token + güvenlik duvarı).

Manifest'te `usesCleartextTraffic=true` — yerel/Tailscale http için. İnternet üzerinden kullanacaksan gateway'i TLS arkasına al ve `https` kullan.

---

## Mimari

```
MainActivity (Compose UI)
  └── NovaViewModel
        ├── SettingsStore   (DataStore: baseUrl, token, model, effort, reasoning)
        ├── NovaClient      (OkHttp + SSE → gateway /v1/chat/completions)
        └── SpeechManager   (Android STT + TTS, tr-TR)
```

- **Streaming:** OkHttp `EventSource` ile token token; `x-nova-route` başlığı yanıt altında rozet olarak gösterilir.
- **Ses:** şu an cihazın yerleşik motorları (SpeechRecognizer + TextToSpeech). İleride gateway `/stt` (Whisper) + `/tts`'e geçirilebilir.
- **Model:** istemci hep gateway'e konuşur; seçili model `auto` ya da `ollama/qwen3:14b`, `gemini/...`, `anthropic/...`, `openclaw/default` olarak gider.

---

## Test

Saf mantık (SSE delta ayrıştırma) için JUnit testi var:

```bash
./gradlew test          # app/src/test → NovaClientTest
```

Derleme + statik denetim:
```bash
./gradlew lintDebug assembleDebug
```

> Not: Compose UI'nin tamamı yalnızca Android ortamında (Studio/emülatör) derlenir ve çalışır. Bu repodaki kod elle gözden geçirildi; gerçek derleme/çalıştırma senin makinende yapılır.

---

## İzinler

- `INTERNET` — gateway'e bağlanmak için.
- `RECORD_AUDIO` — sesli mod; ilk kullanımda çalışma anında istenir.

---

## Yapılacaklar (sonraki adımlar)

- Gateway `/stt` + `/tts` ile gerçek ses (orb'u TTS genliğine bağlamak için Web Audio benzeri AudioRecord/Visualizer).
- Çoklu sohbet + kalıcı geçmiş (Room/DataStore).
- Markdown + kod bloğu render (kopyala) — şu an düz metin.
- Görsel (çoklu-medya) gönderme.
- Compose UI testleri (androidx.compose.ui.test).
