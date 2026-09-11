# NOVA / Horus — Android İstemci

Telefonun **kendi işlemcisinde çevrimdışı LLM** çalıştıran, aynı zamanda PC'deki **Gateway**
üzerinden güçlü modelleri kullanabilen ve ikisini **hibrit** birleştiren native Android uygulaması.
Kotlin + Jetpack Compose.

**Ana hedef:** telefonda agentic bir yapay zekâyı offline çalıştırmak ve bunu PC ile entegre yapmak.

> **Gizlilik sözü:** Yerel/Çevrimdışı modda istem ve yanıt cihazdan hiç çıkmaz. PC'ye devir yalnız
> kullanıcı onayıyla (veya hibritte kullanıcının açtığı kuralla) olur. Bulut sağlayıcı anahtarları
> telefona hiç gelmez; bulut çağrıları yalnız PC'deki Gateway üzerinden yapılır.

---

## Özellik özeti (Faz 1–10A)

| Faz | Özellik |
|-----|---------|
| 1 | **Yerel öncelikli çekirdek** — LiteRT-LM cihaz motoru, SHA-256 doğrulamalı/sürdürülebilir model indirme, Kontrol·İşler·Sohbet·Modeller arayüzü, kod bloğu kopyalama, 4 tema (Turkuaz · Aurora · Amber · Kızıl), güvenli iptal |
| 2 | **Çevrimdışı + agentic araçlar** — `LOCAL_ONLY` politikası (devir kapalı), çevrimdışı araç seti (saat, hesap makinesi, cihaz durumu, not defteri), kapılı Gemma modelleri |
| 3 | **Hibrit** — uzunluk/pil/ısı/gizlilik kurallı yönlendirme, "PC ajanına devret", sor/otomatik devir anahtarı |
| 4 | **Model otomasyonu** — cihaza göre model önerisi, uygunluk çipi, performans metrikleri (yükleme/tok-sn) |
| 5 | **Kalıcı sohbet geçmişi** — çoklu sohbet, otomatik kayıt, ara/aç/sil |
| 6 | **Çevrimdışı ses** — yerel politikalarda cihaz-üstü STT tercihi (`EXTRA_PREFER_OFFLINE`) |
| 7 | **Veri yönetimi** — sohbeti Markdown olarak dışa aktar/paylaş, tüm yerel veriyi temizle |
| 8 | **Kişiselleştirme + dayanıklılık** — yerel model personası (sistem talimatı), indirme öncesi boş alan kontrolü |
| 9 | **Basit/Gelişmiş arayüz modu** + cihaz motoru hızlandırması (Otomatik/CPU/GPU/NPU) ve örnekleme ayarları |
| 10A | **VPN'siz LAN bağlantısı** — mDNS ile PC keşfi, 8 karakterlik kod veya QR bağlantısıyla eşleme |

Test kapsamı: **437 birim + 122 enstrümanlı test.**

**Derleme durumu:** ✅ **2026-08-10'da emülatörde doğrulandı** — Pixel 10 Pro XL
(API 37) üzerinde derlendi, kuruldu, çalıştı. LiteRT-LM 0.14.0, compileSdk 37 ve
tüm yeni ekranlar derleyiciden geçti. Kalan: fiziksel ARM64 cihazda cihaz-üstü
üretim (Otomatik/CPU/GPU tok/sn) ve gerçek Wi-Fi'de mDNS eşlemesi.

Doğrulamak için (JDK 17 + Android SDK Platform 37 gerekir):

```powershell
cd nova-android
.\gradlew.bat testDebugUnitTest assembleDebug
```

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

Sürüm uyumu (tek kaynak `gradle/libs.versions.toml`): **AGP 9.2.1 · Kotlin 2.2.21 ·
Gradle 9.4.1 · Compose BOM 2026.06.01 · compileSdk 37 · targetSdk 36 · minSdk 26 ·
LiteRT-LM 0.14.0 · OkHttp 5.4.0.**

`compileSdk` (37) ile `targetSdk` (36) **bilerek farklı**: androidx.core 1.19.0 ve
lifecycle 2.11.0 `minCompileSdk=37` koyuyor, ama targetSdk'yı yükseltmek yeni çalışma
zamanı davranışlarını üstlenmek demek olurdu. compileSdk 37 için derleme makinesinde
SDK Platform 37 kurulu olmalı: `sdkmanager "platforms;android-37"`.

Kotlin sürümü keyfi değiştirilmemeli: LiteRT-LM Kotlin 2.3 metadata'sıyla derlenmiştir,
2.2.21 bunu okuyabilir. "was compiled with a newer Kotlin compiler" hatası alınırsa
katalogdaki `kotlin` değerini `2.3.21` yapmak yeterlidir.

Enstrümanlı testler (cihaz/emülatör gerekir): `.\gradlew.bat connectedDebugAndroidTest`.

---

## Mimari

```
MainActivity (Compose)
  └── NovaViewModel
        ├── SettingsStore       (DataStore: bağlantı, model, executionPolicy, localModelId,
        │                        localThinking, localTools, themeId, hfToken, hybridAutoFallback,
        │                        persona, uiMode, backendPreference, sampler*)
        ├── ExecutionPolicy + EngineRouter   (yönlendirme kararları — saf/testli)
        ├── PrivacyClassifier                (hassas istem sezgisi — saf/testli)
        ├── LocalLlmController
        │     ├── LocalModelCatalog     (sabit sürüm + SHA-256 + lisans)
        │     ├── ModelDownloader       (HTTPS, Range sürdürme, atomik kurulum)
        │     ├── DownloadPreflight     (indirme öncesi yer kontrolü)
        │     ├── ModelRecommender      (cihaza göre öneri)
        │     ├── ModelMetricsStore     (yükleme/tok-sn kayıtları)
        │     ├── LocalEngineSettings   (backend tercihi + örnekleme — saf/testli)
        │     ├── LocalThinkingSupport  (düşünme anahtarı modele bağlı — saf/testli)
        │     ├── OnDeviceEngine        (LiteRT-LM: Engine/Conversation, akışlı üretim, araçlar, persona)
        │     └── HorusToolSet          (çevrimdışı araçlar: saat, hesap, cihaz, notlar)
        ├── PairingController   (mDNS keşfi + kod/QR bağlantısıyla eşleme)
        ├── ConversationStore   (kalıcı sohbet geçmişi — cihazda JSON)
        ├── NovaClient          (OkHttp + SSE → gateway /v1/chat/completions)
        └── SpeechManager       (Android STT + TTS, tr-TR, çevrimdışı tercihi)
```

- **Akış:** OkHttp `EventSource` ile token token; `x-nova-route` rozeti hangi hedefin yanıtladığını gösterir.
- **Yerel motor:** `com.google.ai.edge.litertlm:litertlm-android:0.14.0`. Hızlandırma seçilebilir
  (Otomatik / CPU / GPU / NPU); Otomatik önce GPU dener, olmazsa CPU'ya düşer ve gerçekten çalışan
  backend Ayarlar'da yazar. İlk yükleme saniyeler
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

| Model | Depo | Boyut | Önerilen RAM | Lisans | Kapı |
|-------|------|-------|--------------|--------|------|
| Granite 4.0 350M (q8) | `litert-community/granite-4.0-350m-litert-lm` | 0,4 GB | 2 GB | Apache-2.0 | Kapısız |
| Qwen3 0.6B (int4 + tam) | `litert-community/Qwen3-0.6B` | 0,5 / 0,6 GB | 3–4 GB | Apache-2.0 (açık kaynak) | Kapısız |
| Qwen3 1.7B (int4 + tam) | `litert-community/Qwen3-1.7B` | 0,9 / 1,9 GB | 5–6 GB | Apache-2.0 | Kapısız |
| Gemma 4 E2B (uç-cihaz) | `litert-community/gemma-4-E2B-it-litert-lm` | 2,4 GB | 6 GB | Apache-2.0 | Kapısız |
| Qwen3.5 0.8B (int8) | `litert-community/Qwen3.5-0.8B` | 0,9 GB | 5 GB | Apache-2.0 | Kapısız |
| Qwen3 4B (int4) | `litert-community/Qwen3-4B` | 2,5 GB | 8 GB | Apache-2.0 | Kapısız |
| Qwen3 4B Instruct 2507 | `litert-community/Qwen3-4B-Instruct-2507` | 2,5 GB | 8 GB | Apache-2.0 | Kapısız |
| Qwen3 4B Düşünen 2507 | `litert-community/Qwen3-4B-Thinking-2507` | 2,1 GB | 8 GB | Apache-2.0 | Kapısız |
| Gemma 4 E4B (uç-cihaz) | `litert-community/gemma-4-E4B-it-litert-lm` | 3,4 GB | 8 GB | Apache-2.0 | Kapısız |
| Qwen3 8B (int4) | `litert-community/Qwen3-8B` | 4,6 GB | 12 GB | Apache-2.0 | Kapısız |
| Qwen3 4B (int8) | `litert-community/Qwen3-4B` | 5,3 GB | 16 GB | Apache-2.0 | Kapısız |
| Qwen3 8B (int8) | `litert-community/Qwen3-8B` | 7,7 GB | 24 GB | Apache-2.0 | Kapısız |
| Gemma 4 12B | `litert-community/gemma-4-12B-it-litert-lm` | 6,1 GB | 16 GB | Apache-2.0 | Kapısız |
| Qwen3 14B (int4) | `litert-community/Qwen3-14B` | 8,1 GB | 24 GB | Apache-2.0 | Kapısız |
| Gemma 3 1B (int4) | `litert-community/Gemma3-1B-IT` | 0,5 GB | 4 GB | Gemma Şartları (açık ağırlık) | Kapılı |
| FunctionGemma 270M | `litert-community/functiongemma-270m-ft-mobile-actions` | 0,3 GB | 2 GB | Gemma Şartları | Kapılı |

**Orta sınıf (1.7B / E2B) ve Granite 350M** kataloğa 2026-08-09'da eklendi. Amaç RAM kapsamasındaki
deliği kapatmaktı: 4 GB'lık 0.6B ile 8 GB'lık 4B arasında hiçbir seçenek yoktu ve 5–7 GB'lık
telefonlar (en yaygın sınıf) ya zayıf ya "Riskli" bir modele düşüyordu. Granite ayrıca düşük ucu
**tokensız** yapar — 2 GB sınıfındaki tek model (FunctionGemma) kapılıydı. Artık 2/3/4/6/8/12/16 GB'ın
her birinde kapısız ve rahat çalışan bir seçenek var; bu bir birim testiyle kilitli.
Katalogdaki **13 modelin tamamında** ne işe yaradığını ve sınırını anlatan bir açıklama satırı vardır.

**Büyük modeller (4B–14B)** kataloğa 2026-07-19'da eklendi; hepsi Apache-2.0 ve kapısızdır.
Uygunluk çipi cihaz RAM'ine göre dürüstçe **Riskli** gösterebilir ve model kartında beklenen
RAM/indirme uyarısı yazar — desteklenmeyen bir şey "çalışıyormuş gibi" sunulmaz. 12B ve üstü
yalnız 16–24 GB RAM'li amiral gemisi cihazlarda anlamlıdır; indirme öncesi boş alan kontrolü
(`DownloadPreflight`) ağa çıkmadan uyarır.

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
