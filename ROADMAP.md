# Project Horus — Yol Haritası

Tarih: 2026-07-16 · Dal: `codex/phase1-local-first`

## Ana hedef

Telefonda **çevrimdışı çalışan agentic bir yapay zeka** + PC'deki LLM işlemlerinin telefondan yönetimi + ikisinin hibrit birleşimi. Sıralama kullanıcı tarafından sabitlendi:

1. **Faz 1 — Yerel öncelikli (bu dal):** Telefon, ilk hedef olarak kendi üzerindeki modeli kullanır; PC/Gateway yolu aynen korunur.
2. **Faz 2 — Tam çevrimdışı:** Ağ tamamen kapalıyken sohbet + araç kullanımı; indirme merkezi olgunlaşır; `LOCAL_ONLY` politikası açılır.
3. **Faz 3 — Hibrit:** İzin temelli otomatik devir (telefon ↔ PC ↔ bulut), iş bölüşümü ve maliyet/gizlilik kuralları.

## Değişmez güvenlik sınırları

- Mevcut Gateway sohbeti, mobil görevler (SSE), onay/duraklat/iptal akışı **regresyona uğramaz**.
- Varsayılan politika `GATEWAY_ONLY` kalır; mevcut kurulumlar kendiliğinden telefona geçirilmez.
- Yerel model başarısız olursa istem **sessizce** PC'ye veya buluta gönderilmez; gerekçe gösterilip izin istenir.
- Bulut sağlayıcı anahtarları telefona taşınmaz; bulut çağrıları Gateway üzerinden kalır.
- Desteklenmeyen özellik taklit edilmez; pasif gösterilir ve nedeni açıklanır.

## Doğrulanmış teknik kararlar (2026-07-16)

- Cihaz-üstü motor: **LiteRT-LM Kotlin API** — `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (Google Maven; Engine/EngineConfig/Conversation, `sendMessageAsync` + `MessageCallback`, `Backend.CPU()`).
- Referans model: **litert-community/Qwen3-0.6B** @ revizyon `3adacb36657dbe0119addf143782ed973c680716` (apache-2.0):
  - `qwen3_0_6b_mixed_int4.litertlm` — 497.664.000 B — SHA-256 `b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9`
  - `Qwen3-0.6B.litertlm` — 614.236.160 B — SHA-256 `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4`
- Düşünme kontrolü: Qwen3 şablonunun gerçek `enable_thinking` değişkeni ile **Açık/Kapalı**. Beş kademeli seviye API'de yok → taklit edilmeyecek.
- İptal: LiteRT-LM'de akış-iptal API'si garanti değil → konuşma nesnesi kapatılır, her istek **taze konuşma** kurar; yarım yanıt sonraki bağlama sızamaz.
- Gemma modelleri (lisans onayı gerektirdiği için) Faz 2 indirme merkezine ertelendi; Faz 1 kataloğu yalnız apache-2.0 Qwen3 artifact'lerini içerir.

## Faz 1 teslimatları

| # | Teslimat | Durum |
|---|----------|-------|
| D1 | `ExecutionPolicy` (GATEWAY_ONLY varsayılan / LOCAL_FIRST) + yönlendirici; ayar göçü | bu dalda |
| D2 | `OnDeviceEngine` — LiteRT-LM yükleme, akışlı üretim, güvenli iptal, dürüst hata | bu dalda |
| D3 | Model indirme merkezi — sabit sürüm + SHA-256 doğrulama + kaldığı yerden devam + atomik kurulum, yalnız HTTPS | bu dalda |
| D4 | Yeni bilgi mimarisi: **Kontrol / İşler / Sohbet / Modeller** + Ses (Sohbet içinden) + renk temaları | bu dalda |
| D5 | Sohbette hedef rozeti, kod bloğu başına Kopyala, izinli PC devri kartı | bu dalda |
| D6 | JVM birim testleri + README Yapılanlar/Yapılacaklar + fiziksel ARM64 cihaz doğrulaması | cihaz doğrulaması kullanıcıda |

### Faz 1 kabul ölçütleri

- En az bir doğrulanmış model telefona indirilip **Gateway kapalıyken** akışlı yanıt üretir (fiziksel ARM64 cihazda).
- `LOCAL_FIRST` seçiliyken yerel hata → izinli devir kartı; onaysız hiçbir istem cihaz dışına çıkmaz.
- `GATEWAY_ONLY` davranışı bire bir eskisi gibidir; mevcut testler geçer.
- İndirme: yarım kalan indirme sürdürülür, SHA-256 uyuşmazsa dosya kurulmaz.

## Faz 2 — Tam çevrimdışı (başladı)

- `LOCAL_ONLY` politikası: **eklendi (2026-07-16)** — Kontrol'den "Çevrimdışı" seçilebilir;
  bu modda yerel hata olsa bile istem cihaz dışına gönderilmez (devir kartında PC seçeneği yoktur).
- Yerel araç kullanımı (LiteRT-LM ToolSet): **eklendi (2026-07-16)** — telefonda tamamen çevrimdışı
  araç seti: saat/tarih, hesap makinesi (güvenli ayrıştırıcı), cihaz durumu (pil/RAM/depolama/uçak modu),
  not defteri (cihaz-içi dosya). Modeller > "Yerel araçlar (deneysel)" anahtarıyla açılıp kapanır;
  araç çağrısının model kararına bağlı olduğu arayüzde dürüstçe belirtilir.
- Depolama yönetimi: **eklendi** — Modeller ekranında model klasörü boyutu + boş alan.
- Lisans onaylı modeller: **eklendi (2026-07-16)** — Gemma 3 1B (int4, kapılı) katalogda; HF token
  Ayarlar'da saklanır, yalnız huggingface.co'ya gönderilir (OkHttp host değişen yönlendirmede
  Authorization'ı düşürür), 401/403 dürüst mesajla açıklanır. FunctionGemma aynı mekanizmayla
  tek satırda eklenebilir (sıradaki).
- Çevrimdışı ses: mevcut Android STT/TTS'in çevrimdışı paketlerle davranış testi (cihazda).

## Faz 3 — Hibrit (başladı)

- `HYBRID` politikası D1: **eklendi (2026-07-16)** — Kontrol'den seçilebilir. Sabit ve şeffaf kurallar:
  kısa istemler telefonda; ≥1200 karakter veya (pil ≤ %20 ve şarjda değil) → PC; model yoksa PC;
  ikisi de yoksa dürüst kurulum mesajı. Yerel hata sonrası devir kullanıcı kuralına bağlı:
  varsayılan "her seferinde sor" (izin kartı), istenirse "otomatik devret" anahtarı (Kontrol > Hibrit kuralları).
- Görev devri D2: **eklendi (2026-07-17)** — Sohbetteki "PC ajanına devret" çipi, son soruyu tüm
  bağlamla mevcut Gateway akış yolundan `openclaw/default` ajanına gönderir; koşu Gateway'in ajan
  geçmişine otomatik kaydolur. Dokunuş = açık rıza; Çevrimdışı modda çip hiç görünmez.
- Isı farkındalığı D3: **eklendi (2026-07-17)** — PowerManager THERMAL_STATUS_SEVERE+ iken hibrit
  yönlendirici PC'yi tercih eder (API 29+; okunamazsa taklit yok, telefonda kalır).
- FunctionGemma 270M (araç-çağrısı için eğitilmiş, kapılı, 289 MB) kataloğa eklendi — çevrimdışı
  agentic çekirdek için önerilen model.
- Gizlilik-farkındalıklı yönlendirme D4: **eklendi (2026-07-17)** — `PrivacyClassifier` hassas
  görünen istemleri (şifre, TCKN, IBAN, kart no, anahtar/token) tanır; Hibrit modda bunlar uzun
  veya düşük pil olsa bile telefonda tutulur, otomatik PC devrine kaçmaz. Elle "PC ajanına devret"
  kullanıcının kendi tercihi olarak açık kalır.
- İstek sınıflandırmanın daha da incelmesi (araç ihtiyacı tahmini) — gelecek.

## Faz 4 — Model otomasyonu (başladı, 2026-07-17)

- **Cihaza göre öneri:** `ModelRecommender` (saf) — RAM'e göre uygunluk (Rahat/Sınırlı/Riskli) ve
  kapısız-öncelikli en iyi model seçimi. RAM ölçülemezse kapısız en küçük (ilk kurulum tokensız).
- **Performans metrikleri:** `ModelMetricsStore` (cihazda JSON) — model başına yükleme süresi ve
  yaklaşık tok/sn; her yerel üretimde ölçülüp kaydedilir.
- **Modeller arayüzü:** önerilen model banner'ı (tek dokunuşla indir / aktif yap), satırlarda
  uygunluk çipi ve son performans özeti, "önerilen" rozeti.
- Sıradaki: düşük RAM'de otomatik quantization tercihi, indirme öncesi yer kontrolü uyarısı.

## Faz 5 — Kalıcı sohbet geçmişi (2026-07-17)

- **Depo:** `ConversationStore` (cihazda JSON) — çoklu sohbeti tüm mesajlarıyla saklar; en yeni
  başta, üst sınır aşılınca en eskiler düşer. Saf serileştirme + arama (başlık ve içerik).
- **ViewModel:** her tamamlanan tur otomatik kaydedilir; "yeni sohbet" öncekini kaydeder; sohbet
  aç/sil/ara. Başlık ilk kullanıcı mesajından üretilir.
- **Arayüz:** Sohbet'te "Geçmiş" çipi → `ChatHistoryPanel` (ara, aç, sil).
- Veri yalnız cihazda kalır; hiçbir yere gönderilmez.

## Faz 6 — Çevrimdışı ses (2026-07-17)

- Yerel/Çevrimdışı/Hibrit politikalarda ses tanıma `EXTRA_PREFER_OFFLINE` ile cihaz-üstü pakete
  yönlendirilir; salt-Gateway'de en iyi tanıma için serbest bırakılır (`prefersOfflineVoice`, saf/testli).
- Çevrimdışı paket yoksa ağ hatası dürüst bir mesaja çevrilir (Türkçe çevrimdışı tanıma indirme yönergesi).
- TTS zaten çevrimdışı çalışır; ek değişiklik gerekmedi.

## Faz 7 — Veri yönetimi ve dışa aktarma (2026-07-17)

- **Dışa aktar/paylaş:** `ConversationExporter` (saf) sohbeti Markdown'a çevirir (düşünme hariç);
  Geçmiş panelinde "Paylaş" sistem paylaşım sayfasını açar.
- **Yerel veriyi temizle:** Ayarlar > Veri yönetimi — onaylı; sohbet geçmişi + notlar + performans
  kayıtları, isteğe bağlı indirilen modeller. Ayarlar/Gateway bağlantısı korunur.

## Test kapsamı (2026-07-17)

Birim testler (JVM): yönlendirici (gateway/yerel/çevrimdışı/hibrit + gizlilik + ısı), PrivacyClassifier,
ModelRecommender, ModelMetrics, LocalTools (hesap/not), ConversationStore, ConversationExporter,
VoicePolicy, katalog bütünlüğü, SSE/parse yardımcıları. Enstrümanlı (Compose): kabuk/navigasyon,
Kontrol (politika/hibrit kart), Modeller (öneri/liste), Sohbet geçmişi (liste/paylaş/sil), Ayarlar.

## Durum özeti (2026-07-17)

Faz 1-7 çekirdek teslimatları **kod olarak tamamlandı**; Faz 1-4'ün son tam derlemesi yeşildi
(`testDebugUnitTest` + `lintDebug` + `assembleDebug`). Faz 5-7 saf/testli katman + statik doğrulama
ile eklendi; toplu derleme + fiziksel ARM64 cihaz doğrulaması kullanıcının tek seferlik turunda.

## Depo düzeni notları

- `codex/mobile-task-control-plane` dalındaki commit edilmemiş çalışma `wip:` commit'iyle güvenceye alındı (c4390bd).
- Bu dal, doğrulanmış son Android uygulamasının (`codex/android-control-center-redesign`, 688fe1b) üzerine kuruludur.
