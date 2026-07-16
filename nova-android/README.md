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

`assembleDebug` APK'yi `app/build/outputs/apk/debug/app-debug.apk` konumuna üretir.
`installDebug` bağlı cihaza veya emülatöre kurar.

```bash
cd nova-android
./gradlew assembleDebug
./gradlew installDebug
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
./gradlew test
```

Test dosyası: `app/src/test` altında `NovaClientTest`.

Derleme + statik denetim:
```bash
./gradlew lintDebug assembleDebug
```

### 16 Temmuz 2026 doğrulama özeti

- Görev-öncelikli sabit `Görevler` / `Sohbet` / `Ses` navigasyonu teslim edildi; uyarlanabilir
  launcher ikonu korundu ve `com.nova.agent/.MainActivity` olarak çözüldü.
- `:app:testDebugUnitTest`, `:app:lintDebug` ve `:app:assembleDebug` geçti: 36 unit test,
  0 lint hatası (11 uyarı, 1 bilgi). `:app:connectedDebugAndroidTest` Android 17
  `emulator-5554` üzerinde 19/19 geçti; APK aynı emülatöre kuruldu. Bu koşuda fiziksel cihaz bağlı
  değildi ve fiziksel cihaz testi yapılmadı.
- Ayarlardaki bağlantı testi, geçerli emülatör Gateway adresi ve yerel QA kimliğiyle `PC hazır`
  durumuna ulaştı. Sabit doğrulama istemi PC'deki yerel modele aktı; UI-tree örneklemesine göre
  TTFT 48.337 sn, toplam 48.341 sn ve sanitize rota `ollama/gemma4:latest` idi.
- Worker preflight'ları 7 Node + 39 Python testiyle geçti. Güvenli canlı görev girişimi Gateway
  allowlist'i tarafından `Bu gorev emulator worker'inda desteklenmiyor` mesajıyla reddedildi; bu
  nedenle terminal worker sonucu doğrulanmadı.
- Regresyon turunda Ayarlar başlığı/kapatma kontrolü sistem durum çubuğunun altına alındı ve 1.0
  ile 1.3 sistem yazı ölçeklerinde doğrulandı. Görevler composer ve birincil eylem de 1.3 ölçekte
  gerçek IME'nin tamamen üstünde kaldı. En iyi sıcak, UI-dump'sız debug-emülatör örneğinde 69
  frame'in 37'si janky idi (%53,62), p50 34 ms ve p90 44 ms. Perfetto kanıtı emülatör
  grafik/buffer baskısı ile Compose işinin birlikte etkisini gösterdi; kanıtlanmış tek bir uygulama
  hotspot'u bulunmadı. Fiziksel donanımda release-build performans kontrolü takip maddesidir.
- Doğrulanan debug APK SHA-256:
  `E3F1A29FF5C6AF4B5A4CF6494296E0A3700E57B2B3EF2F5D4043466AB6EFF575`.

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
