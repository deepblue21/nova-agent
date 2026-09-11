# Project Horus — Yol Haritası

Tarih: 2026-07-16 (güncellendi 2026-08-09) · Dal: `codex/phase1-local-first`

## Tamamlanan fazlar (özet)

Faz 1–9 ve 10A kod olarak tamamlandı; **453 birim + 122 enstrümanlı test** yazıldı.
Detaylar aşağıdaki bölümlerde.

**Android derleme durumu:** ✅ **2026-08-10'da emülatörde doğrulandı** —
Pixel 10 Pro XL (API 37) üzerinde derlendi, kuruldu ve çalıştı. LiteRT-LM 0.14.0
yükseltmesi, compileSdk 37 geçişi, Faz 9/10A/10D kodu ve 18 modellik katalog
derleyiciden geçti. Kalan: **fiziksel ARM64 cihazda** cihaz-üstü üretim
(Otomatik/CPU/GPU tok/sn) ve eşlemenin gerçek Wi-Fi'de multicast ile çalışması.

| Faz | Kapsam | Durum |
|-----|--------|-------|
| 1 | Yerel öncelikli çekirdek (LiteRT-LM, indirme, arayüz, temalar) | ✅ |
| 2 | Çevrimdışı politika + agentic araç seti + kapılı modeller | ✅ |
| 3 | Hibrit yönlendirme (uzunluk/pil/ısı/gizlilik) + PC ajanına devir | ✅ |
| 4 | Model otomasyonu (öneri + performans metrikleri) | ✅ |
| 5 | Kalıcı sohbet geçmişi (çoklu sohbet, ara/aç/sil/paylaş) | ✅ |
| 6 | Çevrimdışı ses (cihaz-üstü STT tercihi) | ✅ |
| 7 | Veri yönetimi (dışa aktar/paylaş, yerel veriyi temizle) | ✅ |
| 8 | Persona (sistem talimatı) + indirme öncesi yer kontrolü | ✅ |
| 9 | Basit/Gelişmiş arayüz modu + backend (GPU/NPU) ve örnekleme ayarları | ✅ |
| 10A | VPN'siz LAN bağlantısı: mDNS keşfi + kod/bağlantı ile eşleme | ✅ |

Kalan: fiziksel ARM64 cihazda uçtan uca doğrulama — cihaz-üstü üretim (Otomatik/CPU/GPU)
ve eşlemenin gerçek Wi-Fi'de multicast ile çalışması (kullanıcı turu).

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

- Cihaz-üstü motor: **LiteRT-LM Kotlin API** — `com.google.ai.edge.litertlm:litertlm-android` (Google Maven; Engine/EngineConfig/Conversation, `sendMessageAsync` + `MessageCallback`). Faz 1'de 0.13.1 ve `Backend.CPU()` sabitiyle başlandı; **Faz 9'da 0.14.0'a yükseltilip backend seçilebilir yapıldı** (Otomatik/CPU/GPU/NPU).
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

### Katalog genişletme (2026-07-19) — 4B'den 14B'ye kapısız modeller

Kataloğa beş büyük **kapısız, Apache-2.0** model eklendi; tüm boyut/SHA-256 değerleri HuggingFace
API'sinden doğrulandı ve indirme URL'leri sabit commit'e kilitlendi:

| Model | Depo revizyonu | Boyut | Önerilen RAM |
|-------|----------------|-------|--------------|
| Qwen3 4B (int4) | `84cc5a35…` | 2.659.057.664 B | 8 GB |
| Gemma 4 E4B (uç-cihaz) | `f7ad3343…` | 3.659.530.240 B | 8 GB |
| Qwen3 8B (int4) | `71ff7055…` | 4.887.412.736 B | 12 GB |
| Gemma 4 12B | `44cf85a3…` | 6.547.589.312 B | 16 GB |
| Qwen3 14B (int4) | `e4122fd3…` | 8.655.863.808 B | 24 GB |

Dürüstlük sınırı korundu: uygunluk çipi cihaz RAM'ine göre **Riskli** gösterebilir, model kartında
beklenen RAM/indirme uyarısı (`LocalModelSpec.note`) yazar ve `DownloadPreflight` yer yetmezse ağa
çıkmadan uyarır. Varsayılan model kapısız Qwen3 0.6B olarak kaldı (ilk kurulum tokensız).
LiteRT-LM'in `enable_thinking` desteği Gemma 4 şablonunda da doğrulandı → düşünme anahtarı açık.

### Canlı model listesi (2026-07-19) — sabit listeler kaldırıldı

Web ve Android'deki sabit (ve bayatlamış) model listeleri kaldırıldı; kaynak artık
gateway'in `GET /v1/models` ucudur:

- **Gateway:** `lib/model_catalog.mjs` — Ollama `/api/tags`'ten **canlı** yerel model
  listesi (ad, parametre boyutu, quantization, disk boyutu), 30 sn TTL cache,
  `?refresh=1` ile zorla tazeleme. Ollama kapalıysa liste yine döner, `ollama.ok:false`
  + dürüst hata mesajıyla. Bulut modelleri 2026 kimlikleriyle (Opus 4.8 / Sonnet 5 /
  Gemini 3.5 Flash / GPT-5.6) ve anahtar yoksa `available:false` + gerekçe ile gelir.
  `ALLOW_MODELS` allowlist'i listeye de uygulanır.
- **Dinamik yönlendirme artık varsayılan değil.** Yanıtın `defaultModel` alanı ilk canlı
  yerel modeli işaret eder; istemciler seçim yapılmamışken onu seçer. `auto` listede
  durur ama ancak kullanıcı bilinçli seçerse kullanılır. Eski kurulumlardaki kayıtlı
  `auto` (ve artık var olmayan model kimlikleri) bir kez temizlenir.
- **İstemciler:** web'de seçicide "Canlı liste · gateway" rozeti + Yenile düğmesi,
  anahtarsız modeller pasif ve nedenli; Android'de Ayarlar ve Modeller ekranı aynı
  canlı listeyi kullanır (`GatewayConnectionClient.parseCatalog`, saf/testli).
  Gateway'e ulaşılamazsa her iki istemci de küçük bir yedek listeye düşer — uydurma
  model gösterilmez.

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

## Faz 8 — Kişiselleştirme ve indirme dayanıklılığı (2026-07-17)

- **İndirme öncesi yer kontrolü:** `DownloadPreflight` (saf) — yeterli boş alan yoksa ağa çıkmadan
  dürüst uyarı; sürdürmede yalnız kalan bayt hesaplanır.
- **Yerel model kişiliği (persona):** Ayarlar'da isteğe bağlı sistem talimatı; yalnız cihaz-üstü
  modele `ConversationConfig.systemInstruction` ile uygulanır.

## Not (2026-07-17): derleme düzeltmesi

Faz 5-7 eklemelerinde derleyicisiz kaçan hatalar kullanıcının derlemesinde yakalandı ve düzeltildi
(SettingsPanel `TextButton` import, LocalLlmController metric import'ları + yinelenen `cancelGenerate`).
Ardından tüm modülde paket-farkında import ve yinelenen-fonksiyon denetimi çalıştırıldı.

## Durum özeti (2026-07-17)

Faz 1-8 çekirdek teslimatları **kod olarak tamamlandı ve derleme yeşil**: `testDebugUnitTest`
(127 test) + `assembleDebug` = BUILD SUCCESSFUL. Süreçte yakalanan derleme/test hataları düzeltildi
(Kotlin 2.2.21 yükseltmesi, eksik import'lar, `setHistoryQuery` JVM setter çakışması, bozuk-JSON
nazik ele alma). Kalan tek adım fiziksel ARM64 cihazda uçtan uca doğrulama.

## Faz 9 — Çevrimdışı optimizasyon + Basit/Gelişmiş mod (2026-08-09)

Amaç: uygulamayı "ilk kez açan biri" için çalışır kılmak. İki ayrı iş yapıldı;
ikisi de **davranışı değiştirmeden** yapıldı.

### D1 — Arayüz yoğunluğu: Basit / Gelişmiş

- `data/UiMode.kt` (saf): `UiMode` + `SettingsSection` tablosu. Hangi bölümün
  hangi modda görüneceği **tek yerde** tanımlı; ekranlar kendi içinde "bunu da
  gizle" mantığı yazmaz.
- **Yalnız görünürlük değişir.** Gizlenen bir ayarın değeri silinmez,
  sıfırlanmaz, varsayılana çekilmez — kayıtlı değeriyle çalışmaya devam eder.
  `UiModeTest` bunu bölüm bölüm kilitler.
- **Göç:** temiz kurulum Basit'te başlar, **mevcut kurulum Gelişmiş'te kalır**.
  Zaten ayar yapmış birinin kontrollerinin güncelleme sonrası kaybolması bir
  regresyondur. Karar `initialUiModeFor` içinde saf ve testli; `SettingsStore.load`
  "temiz kurulum mu" sorusunu hiçbir göç yazmadan **önce** yanıtlar.
- Basit modda gizlenen: elle Base URL/belirteç, Gateway model seçici, çaba
  düzeyi + akıl yürütme, kişilik, HF belirteci, cihaz motoru ayarları.
  Basit modda **korunan**: bağlantı durumu + test, tema, **veri temizleme**,
  sürüm bilgisi. Gizlilik ve veri kontrolleri sadeleştirme adına kaldırılmaz.
- Modeller ekranında kapılı modeller Basit modda listelenmez (indirmeleri
  yalnız Gelişmiş'te görünen HF belirtecine bağlı — gösterilseler çıkmaz sokak
  olurdu). Kullanıcı zaten indirmiş ya da seçmişse satır **gizlenmez**.

### D2 — Cihaz-üstü motor: hızlandırma ve örnekleme

- **LiteRT-LM 0.13.1 → 0.14.0.** Tek gerekçe hızlandırma; kullanılan API yüzeyi
  aynı kaldı.
- **`Backend.CPU()` artık çakılı değil.** `BackendPreference` (Otomatik/CPU/GPU/
  NPU) eklendi. Otomatik = önce GPU, olmazsa CPU. **Kullanıcı açıkça bir backend
  seçtiyse sessizce başkasına düşülmez** — sessiz devir yasağının motor
  tarafındaki karşılığı. Yüklenemeyen backend, ne yapılacağını söyleyen bir
  hatayla döner.
- Arayüz **tercihi değil, ölçülen sonucu** yazar: "Şu an çalışan: GPU".
  Otomatik seçiliyken gerçekte ne olduğunu görmenin tek dürüst yolu bu.
- Başarısız denemede yarım kalan `Engine` kapatılır; GPU bağlamı sızmaz.
- **Örnekleme (`SamplerConfig`) ilk kez bağlandı.** Hazır ayarlar:
  Model varsayılanı / Kesin / Dengeli / Yaratıcı / Elle.
  **Varsayılan "Model varsayılanı"dır ve motora hiçbir sampler değeri
  göndermez** — yani bu sürüm kimsenin yanıtlarını kendiliğinden değiştirmez.
  Elle değerler diske yazılmadan önce de sonra da kırpılır.
- Örnekleme değişince konuşma taze kurulur (KV önbelleği korunsa ayar hiç
  uygulanmazdı). Kaydırıcılar sürüklerken yerel durumda kalır, diske yalnız
  parmak kalkınca yazılır.

**Testler:** `UiModeTest`, `LocalEngineSettingsTest`, `AppSettingsEngineTest`
(JVM) + `SettingsUiModeTest` (enstrümanlı). Mevcut enstrümanlı ayar testleri
artık modu açıkça `ADVANCED` veriyor.

**Kullanıcıda kalan doğrulama:** fiziksel ARM64 cihazda `assembleDebug` sonrası
Otomatik / CPU / GPU üçünün de denenmesi ve tok/sn farkının Modeller
ekranındaki metrikten okunması.

## Faz 10A — VPN'siz LAN bağlantısı: eşleme arayüze bağlandı (2026-08-09)

Analiz ve seçenek matrisi `docs/BAGLANTI-ANALIZI.md`'de. Faz A'nın çekirdek kodu
(mDNS yayını, eşleme kodu, takas ucu, Kotlin ayrıştırıcılar) daha önce yazılmış
ve testlenmişti; **eksik olan tek şey arayüze bağlanmasıydı**. Bu turda bağlandı.

- `feature/pairing/PairingUiState.kt` (saf): durum + `PairingForm` kuralları —
  ne zaman gönderilebilir, liste boşken ne yazar, seçim nasıl uzlaştırılır.
- `feature/pairing/PairingController.kt`: `NsdGatewayDiscovery` + `PairingClient`
  kabuğu. Panel açılınca tarar, kapanınca durdurur (mDNS taraması pil harcar).
- Ayarlar > PC bağlantısı: bulunan PC listesi + 8 karakterlik kod alanı.
  **Eşleme her iki modda da görünür**; elle adres/belirteç Gelişmiş'te *yedek*
  olarak kalır. Kullanıcı hiçbir yere IP ya da anahtar yazmıyor.

**Korunan güvenceler:**

- Takastan dönen `nv_` anahtarı **state'te tutulmaz, loglanmaz**; doğrudan
  mevcut `saveConnection` yoluna verilir — kaydetme/doğrulama davranışı elle
  girişle birebir aynı.
- Tek kullanımlık kod, takas sürerken ikinci kez gönderilemez (aksi halde
  ikinci istek 410 döner ve kullanıcı başarılı denemesinin başarısız olduğunu
  sanardı).
- Hiçbir şey bulunamazsa sonsuza kadar "aranıyor…" gösterilmez: nedeni
  (AP izolasyonu / farklı ağ) ve elle çıkış yolu yazılır. NSD yoksa ya da
  başlatılamazsa akış kapanır ve tarama bitmiş sayılır.
- 8 karakter yazılmış ama alfabede olmayan karakter varsa (Crockford Base32'de
  U yoktur) buton pasif kalırken **sebebi yazılır**.

**Testler:** `PairingFormTest` (JVM) + `PairingSectionTest` (enstrümanlı).

**Yapıştırılan eşleme bağlantısı (aynı turda eklendi).** Kod alanı iki şeyi
birden kabul eder: 8 karakterlik kod ya da QR'ın içerdiği tam
`horus://pair?v=1&code=…&host=…&port=…` bağlantısı. İkincisi adresi de
taşıdığı için **keşif hiçbir şey bulamasa bile** (AP izolasyonu, farklı ağ)
Basit modda tek hamlede bağlanılır — daha önce bu durumda Gelişmiş moda geçip
adresi elle yazmak gerekiyordu. Ayrıştırıcı `Pairing.parsePairUri` zaten
yazılmış ve gateway'deki JS eşleniğiyle 307 vakada diferansiyel testliydi;
yalnız kullanılmıyordu. Sıfır yeni bağımlılık.

**Kalan:** cihazda multicast doğrulaması (`addMembership` sandbox'ta
koşulamadı) ve isteğe bağlı QR **kamera** taraması. Kamera için iki yol var ve
ikisinin de kalıcı bedeli farklı: GmsBarcodeScanner (CAMERA izni yok, APK
büyümüyor ama Google Play Services zorunlu) ya da CameraX + paketli ML Kit
(her cihazda çalışır ama CAMERA izni + ~5 MB). Karar verilmedi; keşif + kod +
yapıştırılan bağlantı üçlüsü kamerasız da tam bir yol sunuyor.

## Faz 10C — Model kataloğu: RAM kapsaması + açıklamalar (2026-08-09)

Karşılaştırma: **Off Grid** (`ai.offgridmobile`, MIT). Ayrıntılı inceleme ve
sıradaki adımlar: `docs/MODEL-KATALOG-YOLHARITASI.md`.

İnceleme sonucu NOVA seçim akışında geride değildi — boyut, gereken RAM ve
cihaz RAM'iyle karşılaştırma (Rahat/Sınırlı/Riskli çipi) zaten vardı. İki
somut boşluk kapatıldı:

**1. RAM kapsaması.** 4 GB'lık 0.6B ile 8 GB'lık 4B arasında **hiçbir seçenek
yoktu**; 5-7 GB'lık telefonlar (en yaygın sınıf) ya zayıf ya "Riskli" bir
modele düşüyordu. Eklenen dört model (kapısız, Apache-2.0, boyut + SHA-256 +
revizyon 2026-08-09'da HF API'sinden doğrulandı):

| Model | Boyut | Önerilen RAM |
|-------|-------|--------------|
| Granite 4.0 350M (q8) | 468.209.584 B | 2 GB |
| Qwen3 1.7B (int4) | 977.184.032 B | 5 GB |
| Qwen3 1.7B (tam) | 2.056.729.520 B | 6 GB |
| Gemma 4 E2B (uç-cihaz) | 2.588.147.712 B | 6 GB |

Granite ayrıca düşük ucu **tokensız** yapıyor: 2 GB sınıfındaki tek model
(FunctionGemma) kapılıydı, artık kapısız bir alternatif var. Testle kilitlendi:
2/3/4/6/8/12/16 GB'ın her birinde kapısız ve "Rahat" çalışan bir model olmalı.

**2. Açıklamalar.** Katalogdaki **13 modelin tamamına** ne işe yaradığını,
kimin için uygun olduğunu ve dürüst sınırını anlatan `note` yazıldı. Açıklama
artık **kurulduktan sonra da** görünüyor — önceden `!installed` koşuluna
bağlıydı ve kullanıcı indirdiği modelin ne olduğunu unuttuğunda tam o an
kayboluyordu. Kapılı modellerin token gerekliliğini yazması testle zorunlu.

**Arayüz değişmedi** — satır düzeni, uygunluk çipi ve öneri banner'ı aynı
bileşenlerle çalışıyor; eklenenler katalog verisi ve tek satırlık görünürlük
düzeltmesi.

**Yan etki (bilinçli):** RAM ölçülemediğinde önerilen model artık Granite 350M
(en küçük kapısız). `ModelAutomationTest` bu kuralı kimliğe sabitlemek yerine
"kapısızların en küçüğü" olarak doğruluyor, böylece katalog büyüdükçe kırılmıyor.

**3. Düşünme anahtarı modele bağlandı.** `supportsThinkingToggle` alanı katalog
dışında hiç kullanılmıyordu; anahtar modelden bağımsız çiziliyor ve başlığı
sabit "(Qwen3)" yazıyordu. Katalog 16 modele çıkınca düşünmeyi desteklemeyen
**altı model** oluştu ve anahtar onlarda da etkin görünüyordu. `LocalThinkingSupport`
(saf, testli) artık durumu seçili modelden türetiyor; desteklemeyen modelde
anahtar gizlenmiyor, **pasif çiziliyor ve nedeni yazılıyor**. Sözleşme
katalogun tamamında testle doğrulanıyor.

**Katalog: 16 model** (Gemma 4 E2B/E4B/12B, Qwen3 0.6B–14B, Qwen3.5 0.8B,
iki uzmanlaşmış 4B, Granite 350M), 2 GB'dan 24 GB'a her sınıfta kapısız seçenek.
Gemma tarafında eklenecek başka bir şey **kalmadı**: üç bağımsız arama
(litert-community, hub geneli `library=litert-lm`, resmî `google`/`Qwen`
hesapları) yeni bir resmî `.litertlm` modeli bulmadı. Gemma 3'ün 4B/12B
dosyaları `.task` formatında olduğu için motor tarafından yüklenemez.

**Sıradaki (M3-M5):** aynı ailede quantization çeşitliliği, donanıma özel
derlemeler (`-gpu` dosyaları katalogun sabitlediği revizyonlarda YOK — daha
yeni bir commit'te; pinlemek 404 üretirdi), ilk açılış model adımı.

## Faz 10D — İlk açılış adımı + erişilebilirlik (2026-08-10)

**İlk açılış model kartı (M5).** Uygulamanın manşet özelliği telefonda
çevrimdışı LLM çalıştırmak, ama varsayılan politika `GATEWAY_ONLY` olduğu için
yeni kullanıcı Kontrol ekranında bundan **hiç haberdar olmuyordu**. Off Grid
bunu zorunlu bir indirme ekranıyla çözüyor; burada **kapatılabilir bir kart**
seçildi — zorunlu ekran, yalnız PC'ye bağlanmak isteyeni ilgilenmediği bir
GB'lık indirmeyle karşılardı. Kart üç koşul birden sağlanınca çıkar (model yok,
kapatılmamış, indirme sürmüyor); "şimdilik atla" kalıcıdır. Karar mantığı
`FirstRunGuide` içinde saf ve testli.

**Erişilebilirlik denetimi.** Kod taranarak iki somut ihlal bulundu ve
düzeltildi:

- **`heading()` semantiği hiç kullanılmıyordu (0 örnek).** Bölüm başlıkları
  ("YÜRÜTME POLİTİKASI", "CİHAZDAKİ MODELLER", Ayarlar kart başlıkları) düz
  metindi; TalkBack kullanıcısı başlıklar arasında gezinemiyordu. Beş noktaya
  `heading()` eklendi — **görsel olarak hiçbir şey değişmedi**.
- **48 dp altı dokunma hedefleri.** Modeller ekranındaki *İndir* ve *Sürdür*
  düğmeleri 40 dp, önerilen model indirme düğmesi 46 dp idi; Material asgarisi
  48 dp. Üçü de düzeltildi. (Tema renk noktası 12 dp kaldı — dekoratif, tıklanmaz.)

Kaynak: [Compose erişilebilirlik teknikleri](https://developer.android.com/develop/ui/compose/designsystems/material3)
— "Informative Content" kategorisi başlık semantiğini açıkça sayıyor.

## Faz 10E — Emülatörde bulunan ilk gerçek hata (2026-08-10)

Uygulama ilk kez emülatörde çalıştırıldı ve **ekrana bakarak** bir hata
bulundu — statik incelemeyle görülemeyecek türden:

**Temiz kurulumda ilk ekranda hata mesajı çıkıyordu.** Üst çubukta ve hedef
kartında "Bağlantı reddedildi: adres bulundu ama bu portta dinleyen yok"
yazıyordu. Sebep: varsayılan `baseUrl` (`10.0.2.2:8088`) hiçbir zaman boş
olmadığı için `NovaViewModel.init` **koşulsuz** bağlantı sondası atıyordu.
Yani kullanıcı, hiç kurmadığı bir bağlantı için açılışta hata görüyordu —
üstelik uygulamanın manşet özelliği (çevrimdışı model) bunu hiç gerektirmiyor.

**Düzeltme:** `GatewayConnectionUiState.shouldProbeOnStart` (saf, testli) —
sonda yalnız **belirteç de varsa** atılır, çünkü gerçek bir Gateway bağlantısı
`Bearer nv_…` ister. Belirteç yoksa nötr `notConfigured()` durumu gösterilir:
"PC bağlantısı kurulmadı · Telefonda çevrimdışı model kullanmak için gerekmez."

Düzeltme aynı emülatörde yeniden derlenip **doğrulandı**: üst çubuk artık
hata değil bilgi gösteriyor.

## Faz 11A — Devir izlenebilirliği: sessiz devrin sonu (2026-09-10)

**Bulunan hata: kod kendi hakkında yanlış konuşuyordu.** `NovaViewModel.handoffToPcAgent()`
KDoc'u "Koşu, Gateway'in ajan geçmişine (`/v1/agent/runs`) otomatik kaydolur" diyordu.
Kaydolmuyordu. Gateway'in ajan dalı `if (agent && provider === "ollama" && …)` ile
sınırlı; OpenClaw ise katalogda `provider: "openclaw"` olarak kayıtlı, dolayısıyla o dala
**hiç girmiyor** ve tek `recordRun(mode:"agent")` çağrısına ulaşılmıyordu. Sonuç: telefondan
PC'ye devredilen iş çalışıyor ama **hiçbir iz bırakmıyordu** — ne `agent_runs` tablosunda,
ne uygulamada. Uygulama zaten `/v1/agent/runs`'ı hiç okumuyordu.

Bu, "yanlış yorum eksik yorumdan kötüdür" örneğidir: yorum olmasaydı eksiklik ilk denemede
görülürdü; yorum, olmayan bir güvence verdiği için kimse bakmadı.

**Yapılanlar:**

| Katman | Değişiklik |
|---|---|
| Gateway | `openclawRunFromCompletion()` (saf, testli) + düz tamamlama yolunda `recordRun`. Kural **dar**: yalnız `provider === "openclaw"`. `agent:true` gönderilmiş bir bulut modeli ajan koşumu değildir (araç döngüsü çalışmadı); onu yazmak ilk yalanı düzeltirken ikincisini üretirdi. |
| Android | `GET /v1/agent/runs` istemcisi + `parseAgentRuns` (saf). `null` = ulaşılamadı, boş liste = koşum yok — iki ayrı cümle. Ağ hatasında eldeki liste **silinmez**. |
| Android | Kontrol ekranına "PC KOŞUMLARI" kartı; `PcHandoffFeed` (saf sunum mantığı). Telefondan devredilenler "telefondan" rozetiyle ayrışır. |
| Android | Yanlış KDoc düzeltildi; devir bitince geçmiş kendiliğinden tazelenir. |
| Web | Ayarlar'daki geçmiş açıklaması üçüncü türü de sayıyor. |

**Ön koşul (belgelenmeli):** `agent_runs` ve `/v1/agent/runs` yalnız `MULTI_USER`
modunda vardır (`DATABASE_URL` set + `MULTI_USER !== "0"`). Salih'in compose yığını bu
modda çalışıyor (doğrulandı). Eski/tek-kullanıcı bir gateway'e bağlıyken devir yine
çalışır ama kart "Geçmiş okunamadı" der — uygulama koşum **uydurmaz**.

Testler: 412 → 429 birim (13 yeni `PcHandoffFeedTest`, 4 yeni guard), gateway 226 → 229.

## Gelecek yol haritası (Faz 10B+)

**Faz 10B — iroh taşıma katmanı (CGNAT arkası, VPN'siz).** Faz 10A aynı Wi-Fi'yi
çözdü; bu adım mobil veri / farklı ağ durumunu çözer. `computer.iroh:iroh-android`
Maven Central'da hazır derlenmiş (Rust/NDK zinciri gerekmiyor), PC tarafı için
resmî Node bindingi var. **Dikkat: Android'de resmî destek yalnız aarch64/armv7
— x86_64 emülatörde iroh çalışmaz**, doğrulama fiziksel telefon ister.
Ayrıntı ve kabul edilmesi gereken bedeller `docs/BAGLANTI-ANALIZI.md` §10.3'te.

**Faz 11 — Görev devri derinleştirme (telefon ↔ PC).** Telefonda başlayan işin PC'de sürmesi;
Gateway ajan koşusu köprüsü, canlı ilerleme (SSE) ve birleşik takip. Hibrit vizyonunun
"PC entegrasyonu" yarısını tamamlar.

- **11A — devir izlenebilirliği: tamamlandı (2026-09-10).** Aşağıya bakın.
- **11B — süren devrin görünürlüğü: tamamlandı (2026-09-11).** Kontrol ekranı devir
  sırasında "Sohbet yanıtı üretiliyor…" diyordu; iş PC'de çalışırken bu yanlıştı.
  Süren devir ayrı bir durum oldu (istem + durum + geçen süre + dinlemeyi durdurma),
  koşum kartında canlı satır olarak duruyor. **İkinci bir SSE kanalı yazılmadı:**
  sohbet akışı zaten SSE ve gateway OpenClaw'dan düz metinden başka bir şey almıyor;
  ayrı bir kanal var olmayan veriyi taşıyan boş bir makine olurdu. Bunun yerine
  `openclawToolStep()` ile üst akış araç adımı gönderiyorsa geçiriliyor — göndermiyorsa
  hiçbir şey değişmiyor, adım uydurulmuyor.
- Kalan: koşum ayrıntısı (satıra dokununca tam sonuç metni) ve telefondan koşum silme.

**Faz 12 — Çok-modluluk (cihaz-üstü).** Gemma 3n benzeri modelle telefonda görsel/ses girişi
(LiteRT-LM vision/audio backend). Sohbete görsel ekleme, çevrimdışı görüntü/ses anlama.

- **12A — görü altyapısı: tamamlandı (2026-09-11).** Yetenek modeli, motorun görü
  backend'iyle kurulması, `Content.ImageBytes` yolu ve iki taraflı "uydurma yok"
  kuralı. Doğrulananlar: litertlm 0.14.0'da `Content.ImageBytes/ImageFile/AudioBytes/
  AudioFile` var ve `visionBackend` **EngineConfig** parametresi (depo v0.14.0-alpha.0
  etiketinden okundu); katalogdaki Gemma 4 E2B/E4B paketleri görü ve ses modellerini
  içeriyor ("loaded as needed" — model kartları).
- **12B — sohbette görsel ekleme (sıradaki):** foto seçici, küçük resim, gateway
  yolunda `image_url` içerik parçası, hedef göremiyorsa açık ret. Arayüz bilerek
  SONRAYA bırakıldı: cihazda Android derlemesi koşamıyorum, bu yüzden önce CI'ın
  litertlm API kullanımını (`visionBackend`, `Content.ImageBytes`) doğrulaması
  gerekiyor — API yanlışsa üstüne 300 satır arayüz yazmış olmayayım.

**Faz 13 — Model yaşam döngüsü.** Güncelleme bildirimi, delta/parça indirme, depolama baskısında
otomatik boşaltma, düşük RAM'de otomatik quantization tercihi.

**Faz 14 — Dağıtım.** Cihaz profiline göre otomatik hızlandırma ayarı (backend seçimi Faz 9'da
geldi); çoklu dil (i18n); cihazda çevrimdışı RAG (yerel gömme + arama); Play Store dağıtımı,
release imzalama ve sürüm kanalları.

**Sürekli.** Enstrümanlı testlerin CI'da (emülatör) koşulması — **eklendi (2026-07-19)**:
elle tetiklenen `android-instrumented.yml` workflow'u (KVM + API 34, rapor artefaktı); push
CI'ında JVM birim testleri + `docs-check` (doküman sayıları koddan doğrulanır) koşuyor.
Kalan: çevrimdışı STT/TTS dil paketi rehberi; istek sınıflandırmanın incelmesi (araç
ihtiyacı tahmini, gizlilik etiketi seviyeleri).

> İlke: her yeni faz da "desteklenmeyeni taklit etme, sessiz devir yok, veri varsayılan olarak
> cihazda" güvencelerini korur.

## Depo düzeni notları

- `codex/mobile-task-control-plane` dalındaki commit edilmemiş çalışma `wip:` commit'iyle güvenceye alındı (c4390bd).
- Bu dal, doğrulanmış son Android uygulamasının (`codex/android-control-center-redesign`, 688fe1b) üzerine kuruludur.
