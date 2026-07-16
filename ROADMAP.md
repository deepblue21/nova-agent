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

## Faz 2 — Tam çevrimdışı (sonraki)

- `LOCAL_ONLY` politikası: ağ isteği atılmaz; Gateway bölümleri "çevrimdışı" olarak işaretlenir.
- İndirme merkezi: lisans onaylı (Gemma) modeller, depolama yönetimi, model silme/yeniden doğrulama.
- Yerel araç kullanımı (LiteRT-LM ToolSet): saat, hesaplama, cihaz-içi arama gibi çevrimdışı araçlar → telefonda **agentic** akışın çekirdeği.
- Çevrimdışı ses: mevcut Android STT/TTS'in çevrimdışı paketlerle davranış testi.

## Faz 3 — Hibrit (en son)

- `HYBRID` politikası: istek sınıflandırma (uzunluk/araç ihtiyacı/gizlilik etiketi) → telefon veya PC seçimi, her devir için tek dokunuşla izin veya kalıcı kural.
- Görev devri: telefonda başlayan işin PC'de sürmesi (Gateway agent runs ile köprü).
- Maliyet/pil/ısı farkındalığı: düşük pilde yerel küçük model, şarjda büyük bağlam gibi kurallar.

## Depo düzeni notları

- `codex/mobile-task-control-plane` dalındaki commit edilmemiş çalışma `wip:` commit'iyle güvenceye alındı (c4390bd).
- Bu dal, doğrulanmış son Android uygulamasının (`codex/android-control-center-redesign`, 688fe1b) üzerine kuruludur.
