# NOVA — Android İstemci

Hem **telefonun kendi işlemcisinde** (LiteRT-LM) hem de PC'deki Gateway'de (OpenAI-uyumlu, SSE)
çalışan native Android uygulaması. Kotlin + Jetpack Compose. Kontrol merkezi, model indirme
merkezi, sohbet + ses, telefon otomasyon görevleri, streaming yanıt.

> API anahtarları **gateway'de** durur. Bu istemci yalnızca gateway adresini ve `GATEWAY_TOKEN`'ı bilir.
> Yerel modda ise istem ve yanıt cihazdan hiç çıkmaz; PC'ye devir yalnız kullanıcı onayıyla olur.

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
MainActivity (Compose UI: Kontrol / İşler / Sohbet / Modeller + Ses)
  └── NovaViewModel
        ├── SettingsStore        (DataStore: baseUrl, token, model, effort, reasoning,
        │                         executionPolicy, localModelId, localThinking, themeId)
        ├── ExecutionPolicy      (GATEWAY_ONLY varsayılan | LOCAL_FIRST) + EngineRouter
        ├── LocalLlmController
        │     ├── LocalModelCatalog  (sabit sürüm + SHA-256 + lisans)
        │     ├── ModelDownloader    (HTTPS, Range sürdürme, atomik kurulum)
        │     └── OnDeviceEngine     (LiteRT-LM: Engine/Conversation, akışlı üretim)
        ├── NovaClient           (OkHttp + SSE → gateway /v1/chat/completions)
        └── SpeechManager        (Android STT + TTS, tr-TR)
```

- **Streaming:** OkHttp `EventSource` ile token token; `x-nova-route` başlığı yanıt altında rozet olarak gösterilir.
- **Ses:** şu an cihazın yerleşik motorları (SpeechRecognizer + TextToSpeech). Ses ekranı, Sohbet üst çubuğundaki mikrofonla açılır.
- **Model:** Gateway modunda seçili model `auto` ya da `ollama/...`, `gemini/...`, `anthropic/...` olarak gateway'e gider. Yerel modda LiteRT-LM `.litertlm` dosyası cihazda çalışır.

---

## Yerel öncelikli mod (Faz 1)

- Motor: `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (CPU backend). İlk yükleme saniyeler sürebilir; arka planda yapılır.
- Referans model: `litert-community/Qwen3-0.6B` — revizyon `3adacb36…` kilitli. Katalogdaki iki artifact:
  `qwen3_0_6b_mixed_int4.litertlm` (497.664.000 B) ve `Qwen3-0.6B.litertlm` (614.236.160 B); ikisi de Apache-2.0.
- İndirme: yalnız HTTPS, `Range` ile sürdürme, indirme sırasında akan SHA-256; özet eşleşmezse dosya **kurulmaz**. Dosyalar `filesDir/models/` altındadır.
- Yönlendirme: varsayılan politika `GATEWAY_ONLY` (mevcut davranış bire bir). `LOCAL_FIRST` seçilirse istek önce telefonda çalışır; model yoksa veya hata olursa istem **sessizce dışarı gönderilmez** — sohbette gerekçeli izin kartı çıkar.
- Düşünme: Qwen3'ün gerçek `enable_thinking` anahtarı Açık/Kapalı olarak sunulur; var olmayan kademeli "düşünme bütçesi" taklit edilmez. `<think>…</think>` blokları yanıttan ayrıştırılıp ayrı gösterilir.
- İptal: aktif konuşma kapatılır; her istek taze `Conversation` kurduğu için yarım yanıt sonraki bağlama sızamaz.
- Dürüst sınırlar: x86 emülatörde yerel motor `ARM64 gerekir` hatası verir (Gateway yolu etkilenmez). `LOCAL_ONLY` (Faz 2) ve `HYBRID` (Faz 3) arayüzde pasiftir.

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
- `:app:testDebugUnitTest`, `:app:lintDebug` ve `:app:assembleDebug` geçti: 50 unit test,
  0 lint hatası (11 uyarı, 1 bilgi). `:app:connectedDebugAndroidTest` Android 17
  `emulator-5554` üzerinde 27/27 geçti; APK aynı emülatöre kuruldu. Bu koşuda fiziksel cihaz bağlı
  değildi ve fiziksel cihaz testi yapılmadı.
- Son regresyon sağlamlaştırması, etkin görevi başlangıçtaki Gateway ayarına sabitler; bayat 