# NOVA / Horus — Android İstemci

Telefonun **kendi işlemcisinde çevrimdışı LLM** çalıştıran, aynı zamanda PC'deki **Gateway**
üzerinden güçlü modelleri kullanabilen ve ikisini **hibrit** birleştiren native Android uygulaması.
Kotlin + Jetpack Compose.

**Ana hedef:** telefonda agentic bir yapay zekâyı offline çalıştırmak ve bunu PC ile entegre yapmak.

> **Gizlilik sözü:** Yerel/Çevrimdışı modda istem ve yanıt cihazdan hiç çıkmaz. PC'ye devir yalnız
> kullanıcı onayıyla (veya hibritte kullanıcının açtığı kuralla) olur. Bulut sağlayıcı anahtarları
> telefona hiç gelmez; bulut çağrıları yalnız PC'deki Gateway üzerinden yapılır.

---

## Özellik özeti (Faz 1–8)

| Faz | Özellik |
|-----|---------|
| 1 | **Yerel öncelikli çekirdek** — LiteRT-LM cihaz motoru, SHA-256 doğrulamalı/sürdürülebilir model indirme, Kontrol·İşler·Sohbet·Modeller arayüzü, kod bloğu kopyalama, 3 tema, güvenli iptal |
| 2 | **Çevrimdışı + agentic araçlar** — `LOCAL_ONLY` politikası (devir kapalı), çevrimdışı araç seti (saat, hesap makinesi, cihaz durumu, not defteri), kapılı Gemma modelleri |
| 3 | **Hibrit** — uzunluk/pil/ısı/gizlilik kurallı yönlendirme, "PC ajanına devret", sor/otomatik devir anahtarı |
| 4 | **Model otomasyonu** — cihaza göre model önerisi, uygunluk çipi, performans metrikleri (yükleme/tok-sn) |
| 5 | **Kalıcı sohbet geçmişi** — çoklu sohbet, otomatik kayıt, ara/aç/sil |
| 6 | **Çevrimdışı ses** — yerel politikalarda cihaz-üstü STT tercihi (`EXTRA_PREFER_OFFLINE`) |
| 7 | **Veri yönetimi** — sohbeti Markdown olarak dışa aktar/paylaş, tüm yerel veriyi temizle |
| 8 | **Kişiselleştirme + dayanıklılık** — yerel model personası (sistem talimatı), indirme öncesi boş alan kontrolü |

Test kapsamı: **119 birim + 36 enstrümanlı test.** Son durum: `testDebugUnitTest` + `assembleDebug`
= **BUILD SUCCESSFUL**.

---

## Derleme, test, kurulum

Gereksinim: **JDK 17** + Android SDK (Android Studio ikisini de sağlar). Gradle wrapper repoda dahil.

```powershell
cd nova-android
.\gradlew.bat testDebugUnitTest assembleDebug   # test + APK
.\gradlew.bat installDebug                        # cihaz/emülatör bağlıyken kur
```

(Linux/macOS'ta `./gradlew …`.) Üretilen APK:
`app/build/outputs/apk/debug/app-debug.apk`.

Sürüm uyumu: **AGP 8.5.2 · Kotlin 2.2.21 · Compose BOM 2024.10.01 · compileSdk 35 · minSdk 26 ·
Gradle 8.9 · LiteRT-LM 0.13.1.** (Kotlin 2.2.21 zorunlu: LiteRT-LM 2.2 metadata'sıyla derlenmiştir.)

Enstrümanlı testler (cihaz/emülatör gerekir): `.\gradlew.bat connectedDebugAndroidTest`.

---

## Mimari

```
MainActivity (Compose)
  └── NovaViewModel
        ├── SettingsStore       (DataStore: bağlantı, model, executionPolicy, localModelId,
        │                        localThinking, localTools, themeId, hfToken, hybridAutoFallback, persona)
        ├── ExecutionPolicy + EngineRouter   (yönlendirme kararları — saf/testli)
        ├── PrivacyClassifier                (hassas istem sezgisi — saf/testli)
        ├── LocalLlmController
        │     ├── LocalModelCatalog     (sabit sürüm + SHA-256 + lisans)
        │     ├── ModelDownloader       (HTTPS, Range sürdürme, atomik kurulum)
        │     ├── DownloadPreflight     (indirme öncesi yer kontrolü)
        │     ├── ModelRecommender      (cihaza göre öneri)
        │     ├── ModelMetricsStore     (yükleme/tok-sn kayıtları)
        │     ├── OnDeviceEngine        (LiteRT-LM: Engine/Conversation, akışlı üretim, araçlar, persona)
        │     └── HorusToolSet          (çevrimdışı araçlar: saat, hesap, cihaz, notlar)
        ├── ConversationStore   (kalıcı sohbet geçmişi — cihazda JSON)
        ├── NovaClient          (OkHttp + SSE → gateway /v1/chat/completions)
        └── SpeechManager       (Android STT + TTS, tr-TR, çevrimdışı tercihi)
```

- **Akış:** OkHttp `EventSource` ile token token; `x-nova-route` rozeti hangi hedefin yanıtladığını gösterir.
- **Yerel motor:** `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (CPU). İlk yükleme saniyeler
  sürebilir, arka planda yapılır. Her istek taze `Conversation` kurar → iptal edilen yarım yanıt
  sonraki bağlama sızamaz. `<think>…</think>` blokları ayrıştırılıp ayrı gösterilir.

---

## Yürütme politikaları

Kontrol ekranından seçilir. Varsayılan **GATEWAY_ONLY** — mevcut kurulumlar bire bir korunur.

| Politika | Davranış |
|----------|----------|
| **PC / Gateway** (`GATEWAY_ONLY`) | Tüm istekler PC'deki Gateway'e (Ollama/OpenAI/Gemini/Anthropic). Varsayılan. |
| **Yerel öncelikli** (`LOCAL_FIRST`) | Önce telefon; hata olursa **sessizce** değil, gerekçeli izin kartıyla PC'ye devir sorulur. |
| **Çevrimdışı** (`LOCAL_ONLY`) | Yalnız telefon; hiçbir koşulda devir yok, istem cihaz dışına çıkmaz. |
| **Hibrit** (`HYBRID`) | Akıllı seçim: kısa istem telefonda; 1200+ karakter, pil ≤ %20 (şarjsız) veya cihaz aşırı ısınmışsa PC; **gizli görünen istemler (şifre/TCKN/IBAN/kart) telefonda tutulur.** Yerel hata sonrası devir varsayılan "sor", istenirse "otomatik". |

---

## Modeller ve kaynaklar

Tümü **Hugging Face `litert-community`** deposundan, `.litertlm` formatında; her dosya sabit bir
depo revizyonuna kilitli ve indirildikten sonra SHA-256 ile doğrulanır. İndirme yalnız HTTPS, yarıda
kalırsa `Range` ile sürer, özet tutmazsa dosya kurulmaz.

| Model | Depo | Lisans | Kapı |
|-------|------|--------|------|
| Qwen3 0.6B (int4 + tam) | `litert-community/Qwen3-0.6B` | Apache-2.0 (açık kaynak) | Kapısız |
| Gemma 3 1B (int4) | `litert-community/Gemma3-1B-IT` | Gemma Şartları (açık ağırlık) | Kapılı |
| FunctionGemma 270M | `litert-community/functiongemma-270m-ft-mobile-actions` | Gemma Şartları | Kapılı |

**Kapılı modeller** için: HF hesabında modelin sayfasında lisansı onayla → Ayarlar > Hugging Face'e
erişim token'ı gir. Token cihazda kalır, yalnız huggingface.co'ya gönderilir (yönlendirmede CDN'e
sızmaz). Modeller ekranı, cihaz RAM'ine göre en uygun **kapısız** modeli önerir (ilk kurulum tokensız).

---

## Çevrimdışı araçlar (agentic çekirdek)

`HorusToolSet`, LiteRT-LM'in `@Tool` mekanizmasıyla konuşmaya bağlanır (otomatik araç çağırma).
Tümü çevrimdışıdır, ağa çıkmaz, ek izin istemez:

| Araç | İşlev |
|------|-------|
| `simdikiTarihSaat` | Tarih, saat, gün (tr-TR) |
| `hesapla` | + − × ÷ % ^ ve parantezli güvenli hesap (kod çalıştırmaz) |
| `cihazDurumu` | Pil %, şarj, RAM, boş depolama, uçak modu |
| `notKaydet` / `notlariListele` | Cihaz-içi not defteri |

Dürüst sınır: araç çağrısı model kararına bağlıdır; Qwen3-0.6B gibi küçük modellerde her istemde
tetiklenmeyebilir. Araç odaklı iş için **FunctionGemma 270M** önerilir. Anahtar: Modeller > "Yerel araçlar".

---

## Gateway'e bağlanma

Ayarlar > PC bağlantısı:

| Alan | Değer |
|------|-------|
| Base URL | `http://10.0.2.2:8088/v1` (emülatör) · gerçek cihazda Tailscale IP: `http://100.x.x.x:8088/v1` |
| Token | Gateway'deki `GATEWAY_TOKEN` |

Gerçek cihaz için en temizi **Tailscale/WireGuard** (telefon + PC aynı private ağda). İnternet
üzerinden kullanılacaksa Gateway'i TLS arkasına alıp `https` kullan.

---

## Gizlilik ve güvenlik

- Yerel/Çevrimdışı modda istem/yanıt cihazdan çıkmaz; sessiz devir yok.
- İndirmeler sabit revizyon + SHA-256; bozuk dosya kurulmaz, bozuk kayıtlar nazikçe yok sayılır.
- HF token ve Gateway token maskeli alanlarda; loglara/paylaşıma yazılmaz.
- "Yerel veriyi temizle" (Ayarlar): sohbet geçmişi + notlar + metrikler, isteğe bağlı indirilen modeller.

## İzinler

- `INTERNET` — Gateway/indirmeler için. `RECORD_AUDIO` — sesli mod (çalışma anında istenir).

---

## Yol haritası (gelecek)

Tamamlanan Faz 1–8 üzerine planlanan sonraki adımlar:

**Yakın vade**
- Fiziksel ARM64 cihazda uçtan uca doğrulama turu (kabul kapısı).
- Düşük RAM'de otomatik quantization tercihi ve indirme sonrası otomatik "önerileni aktif yap".
- Çevrimdışı STT/TTS dil paketi rehberi + eksikse uygulama içi yönlendirme.
- Enstrümanlı test kapsamının CI'da (emülatör) koşulması.

**Orta vade**
- **Görev devri derinleştirme:** telefonda başlayan işin PC'de sürmesi; Gateway ajan koşusu köprüsü,
  canlı ilerleme (SSE) ve İşler ekranında birleşik takip.
- **Çok-modluluk:** Gemma 3n benzeri modelle cihaz-üstü görsel/ses girişi (LiteRT-LM vision/audio backend).
- **Model yaşam döngüsü:** güncelleme bildirimi, delta/parça indirme, depolama baskısında otomatik boşaltma.
- İstek sınıflandırmanın incelmesi (araç ihtiyacı tahmini, gizlilik etiketi seviyeleri).

**Uzun vade**
- NPU/GPU backend seçenekleri ve cihaz profillerine göre otomatik hızlandırma.
- Çoklu dil (i18n) — arayüzün İngilizce ve diğer diller için kaynaklaştırılması.
- Yerel RAG: cihazdaki belgeler üzerinde çevrimdışı gömme + arama.
- Play Store dağıtımı, release imzalama ve sürüm kanalları.

> Not: "açık kaynak" (Apache/MIT, ör. Qwen3) ile "açık ağırlık" (Gemma Şartları) ayrımı katalogda
> korunur; kapısız modeller ilk kurulum deneyimini token gerektirmeden tamamlar.
