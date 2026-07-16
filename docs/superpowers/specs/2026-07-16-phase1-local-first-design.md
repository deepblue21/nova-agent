# Faz 1 — Yerel Öncelikli Cihaz-Üstü LLM Tasarımı

Tarih: 2026-07-16 · Durum: Kullanıcı onaylı sıralamaya göre uygulanıyor (Yerel öncelikli → Çevrimdışı → Hibrit)

## 1. Amaç

NOVA/Horus Android uygulamasına, telefonun kendi işlemcisiyle çalışan bir LLM motoru eklemek. PC/Gateway ve bulut yolları aynen korunur; kullanıcı "Yerel öncelikli" politikayı seçtiğinde inference önce telefonda denenir. Ana ürün hedefi: telefonda çevrimdışı çalışabilen agentic yapay zekanın temelini atmak.

## 2. Mimari

```
Sohbet / Ses
  └─ ExecutionPolicy (ayarlarda saklanır)
       ├─ GATEWAY_ONLY  → mevcut NovaClient (Ollama/OpenAI/Gemini/Anthropic, hiç değişmedi)
       ├─ LOCAL_FIRST   → OnDeviceEngine (LiteRT-LM) ; hata → izinli devir kartı
       ├─ LOCAL_ONLY    → Faz 2 (arayüzde pasif, nedeni yazılı)
       └─ HYBRID        → Faz 3 (arayüzde pasif, nedeni yazılı)
```

- `EngineRouter` saf bir karar fonksiyonudur (JVM testli): politika + kurulu model durumu → `Gateway | Local | LocalNeedsSetup`.
- Yerel hata veya eksik model **asla** sessiz devire dönüşmez; `PendingFallback(reason)` üretilir, sohbette gerekçeli izin kartı çıkar ve yalnız "PC'ye gönder" onayıyla Gateway çağrılır.
- Bulut anahtarları telefona gelmez; bulut = Gateway üzerinden (değişmedi).

## 3. Cihaz-üstü motor (doğrulanmış API)

- Bağımlılık: `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (Google Maven).
- Yaşam döngüsü: `Engine(EngineConfig(modelPath, backend = Backend.CPU(), cacheDir))` → `initialize()` (arka plan, ~10 sn'ye kadar) → istek başına `createConversation(ConversationConfig(initialMessages))` → `sendMessageAsync(text, MessageCallback, extraContext)`.
- Çok turlu sohbet: uygulama geçmişi kendinde tutar; her istekte `initialMessages` ile taze konuşma kurulur. Böylece iptal/yarım yanıt sonraki bağlama sızamaz (bilinçli tasarım kararı).
- İptal: `cancelled` bayrağı + konuşmayı kapatma. LiteRT-LM'in bu sürümünde güvenilir akış-iptal API'si garanti edilmediği için iptal sonrası motor durumu korunur, konuşma atılır.
- Düşünme: Qwen3 şablonundaki gerçek `enable_thinking` değişkeni → kullanıcıya **Açık/Kapalı** olarak sunulur. Var olmayan "istek başına düşünme bütçesi" taklit edilmez.
- Hata sınıfları Türkçe ve dürüst: model dosyası yok / bellek yetersiz / desteklenmeyen mimari (x86 emülatörde `UnsatisfiedLinkError` yakalanır) / motor hatası.

## 4. Model kataloğu ve indirme güvenliği

Sabit katalog (Faz 1 — yalnız apache-2.0, kapısız):

| id | dosya | bayt | SHA-256 |
|----|-------|------|---------|
| qwen3-0.6b-int4 | qwen3_0_6b_mixed_int4.litertlm | 497.664.000 | b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9 |
| qwen3-0.6b | Qwen3-0.6B.litertlm | 614.236.160 | 555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4 |

Kaynak: `litert-community/Qwen3-0.6B`, sabit revizyon `3adacb36657dbe0119addf143782ed973c680716` (HuggingFace resolve URL'si revizyona kilitli).

İndirme yaşam döngüsü: `.part` dosyasına akış → `Range` ile sürdürme → indirme sırasında akan SHA-256 → tamamlanınca beklenen özetle karşılaştırma → eşleşirse atomik `rename` + `.ok` işaret dosyası; eşleşmezse dosya silinir ve kurulmaz. Tüm hop'larda HTTPS zorunlu (ağ interceptor'ı HTTP yönlendirmeyi reddeder). "Doğrula" eylemi istendiğinde tam yeniden özetleme yapar.

## 5. Bilgi mimarisi (mockup'lara göre)

Alt gezinme dört hedef: **Kontrol · İşler · Sohbet · Modeller**. Ses, Sohbet üst çubuğundaki mikrofonla açılır (özellik kaybı yok).

- **Kontrol:** politika seçici (Yerel öncelikli / PC-Gateway aktif; Çevrimdışı "Faz 2", Hibrit "Faz 3" pasif ve gerekçeli), durum kartı (aktif model veya Gateway durumu + "Verileriniz cihazınızda kalır" yalnız yerelde), Yeni görev / Sohbet kısayolları, Aktif iş kartı (görev veya sohbet akışı).
- **İşler:** mevcut MobileTaskScreen (davranış değişmedi, sekme adı güncellendi).
- **Sohbet:** mevcut akış + hedef/model bilgi çipleri + kod blokları ayrı kartta blok başına **Kopyala** + izinli devir kartı.
- **Modeller:** Cihazdaki modeller (indir/sürdür/iptal/doğrula/sil/aktif seç, boyut ve RAM bilgisi), PC-Gateway modelleri (mevcut liste, seçim), çevrimdışı hazırlık durumu.

Renk temaları: Turkuaz (varsayılan), Aurora (mor), Amber — ayarlardan seçilir, MaterialTheme vurgu renklerini değiştirir.

## 6. Geriye uyumluluk ve göç

- `AppSettings` yeni alanlar: `executionPolicy` (varsayılan `GATEWAY_ONLY`), `localModelId`, `localThinking`, `themeId`. Eski kurulumda anahtar yoksa varsayılan uygulanır → davranış değişmez.
- `NovaViewModel.complete()` Gateway yolu bire bir korunur; yerel yol ayrı fonksiyondadır.
- Görev/SSE/onay akışına dokunulmaz.

## 7. Test ve tamamlanma

- JVM testleri: katalog bütünlüğü (benzersiz id, 64 hex SHA, https+revizyonlu URL), yönlendirici kararları (sessiz devir yok), indirme yardımcıları (Range/hex/atomik adlandırma), ayar varsayılanları.
- Fiziksel ARM64 cihaz kapısı: model indirme + uçak modunda akışlı yanıt + iptal + Gateway regresyon turu. Bu doğrulama kullanıcının cihazında yapılır; yapılmadan Faz 1 "bitti" ilan edilmez.
