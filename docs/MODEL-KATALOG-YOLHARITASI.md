# Cihaz-üstü model kataloğu — yol haritası

Tarih: 2026-08-09 · Kapsam: `nova-android` yerel model kataloğu ve seçimi

Karşılaştırma kaynağı: **Off Grid** (`ai.offgridmobile`, MIT, iOS + Android) —
telefonda çevrimdışı LLM çalıştıran olgun bir uygulama. Aşağıdaki inceleme onun
model seçim akışına dayanıyor.

---

## 1. Off Grid ne yapıyor, biz ne yapıyoruz

| Eksen | Off Grid | NOVA (bugün) | Durum |
|---|---|---|---|
| İlk açılışta model indirme ekranı | Kurulumdan hemen sonra, "Skip for Now" ile atlanabilir | Modeller sekmesi var ama zorunlu bir ilk adım değil | ≈ |
| Model başına **boyut** | Var | Var (`sizeLabel`) | ✅ |
| Model başına **gereken RAM** | Var | Var (`recommendedRamGb` + Rahat/Sınırlı/Riskli çipi) | ✅ **daha iyi** — cihazın RAM'iyle karşılaştırıyoruz |
| **Quantization çeşitliliği** | GGUF: aynı model için Q4/Q5/Q8 | Yalnız Qwen3 0.6B'de int4 + tam | ❌ **boşluk** |
| RAM sınıflarına yayılım | Geniş | 3-4 GB, sonra **atlayıp** 8 GB | ❌ **boşluk** |
| Model açıklaması | Kısa | `note` alanı var ama **çoğu modelde boş** ve yalnız kurulu değilken görünüyor | ❌ **boşluk** |
| Yerel ağdaki sunucuya bağlanma | OpenAI uyumlu (Ollama/LM Studio) | Gateway + mDNS keşif + eşleme | ✅ |
| Araç kullanımı | Web arama, hesap, tarih, cihaz bilgisi | Hesap, tarih/saat, cihaz durumu, notlar | ≈ |
| CPU/GPU/NPU seçimi | Var | Var (Faz 9) | ✅ |
| Görsel üretme / görme | Var | Yok | Kapsam dışı (Faz 12) |

**Sonuç:** NOVA seçim akışında Off Grid'in gerisinde değil; iki somut boşluk
var — **RAM sınıfı kapsaması** ve **açıklama**. Quantization çeşitliliği
üçüncü sırada.

---

## 2. Bugünkü RAM kapsaması ve delik

| Önerilen RAM | Model | Kapılı mı |
|---|---|---|
| 2 GB | FunctionGemma 270M | 🔒 evet |
| 3 GB | Qwen3 0.6B (int4) | hayır |
| 4 GB | Qwen3 0.6B (tam), Gemma 3 1B | 🔒 Gemma kapılı |
| **5-7 GB** | **— YOK —** | |
| 8 GB | Qwen3 4B, Gemma 4 E4B | hayır |
| 12 GB | Qwen3 8B | hayır |
| 16 GB | Gemma 4 12B | hayır |
| 24 GB | Qwen3 14B | hayır |

İki sorun:

1. **5-7 GB'lık telefonlar için hiçbir şey yok.** Bu, sahadaki en yaygın
   sınıf. Bu cihazlar 0.6B'ye (zayıf) ya da 4B'ye (Riskli) düşüyor.
2. **Düşük ucun tek seçeneği kapılı.** 2 GB sınıfındaki tek model
   FunctionGemma ve HF token istiyor — "ilk kurulum tokensız" ilkesiyle çelişiyor.

---

## 3. Yol haritası

### M1 — RAM boşluğunu kapat (bu turda yapıldı)

Kapısız, Apache-2.0, boyut + SHA-256 + revizyon HF API'sinden doğrulanmış:

| Model | Dosya | Boyut | Önerilen RAM |
|---|---|---|---|
| Granite 4.0 350M (q8) | `granite-4.0-350m_q8_ekv1280.litertlm` | 468.209.584 B | 2 GB |
| Qwen3 1.7B (int4) | `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm` | 977.184.032 B | 5 GB |
| Qwen3 1.7B (tam) | `Qwen3_1.7B.litertlm` | 2.056.729.520 B | 6 GB |
| Gemma 4 E2B (uç-cihaz) | `gemma-4-E2B-it.litertlm` | 2.588.147.712 B | 6 GB |

Granite, düşük ucu **tokensız** hale getirir. Qwen3 1.7B'nin iki
quantization'ı aynı modelde int4/tam seçimini 0.6B dışında da açar.

**Arayüz değişmez** — bunlar yalnız katalog verisi; mevcut satır düzeni,
uygunluk çipi ve öneri banner'ı aynı bileşenlerle çalışır.

### M2 — Açıklamaları tamamla (bu turda yapıldı)

- Katalogdaki **her** modele `note` yazıldı: ne işe yaradığı, kimin için
  uygun olduğu, dürüst sınırı.
- `note` artık **kurulduktan sonra da** görünüyor. Önceden `!installed`
  koşuluna bağlıydı; kullanıcı indirdiği modelin ne olduğunu unutunca
  açıklama kayboluyordu.

### M2b — Gemma ≤12B ve Qwen genişletmesi (2026-08-10)

**Gemma ≤12B: kapsama zaten tamdı.** litert-community'de `.litertlm` formatında,
kapısız ve 12B'ye kadar olan Gemma'ların **hepsi** katalogda: E2B, E4B, 12B.
Eklenebilecek başka bir şey yok, çünkü:

| Depo | Neden eklenmedi |
|---|---|
| `Gemma3-4B-IT`, `Gemma3-12B-IT`, `Gemma3-27B-IT` | Dosyalar **`.task` formatında** (eski MediaPipe). `OnDeviceEngine` LiteRT-LM kullanıyor ve `.litertlm` bekliyor — bu dosyalar yüklenmez. Ayrıca depolar kapılı olduğu için HF API'si **SHA-256'yı maskeliyor**; katalog kuralı gereği doğrulanamayan değer eklenemez. |
| `gemma-4-26B-A4B-it`, `gemma-4-31B-it` | 12B sınırının üstünde. |
| `Gemma2-2B-IT`, `gemma-3-270m-it` | Kapılı + eski kuşak; kapısız muadilleri (Granite 350M, Qwen3 0.6B) zaten var. |

Yani "Gemma 12B'ye kadar" isteği **karşılanmış durumda** — eksik olan bir şey
bulunamadı, bulunanlar teknik olarak kullanılamaz.

**Arama kapsamı (2026-08-10, üç bağımsız açı):**

1. `litert-community` deposunun tamamı (indirme sayısına ve son güncellemeye göre)
2. Hub geneli: `library=litert-lm` + "gemma" → 8 sonuç, **hepsi bireysel hesaplardan
   topluluk fine-tune'u** (uncensored/abliterated, toplantı özeti, AI-metin tespiti,
   donanıma özel NPU derlemeleri). Bir asistan kataloğuna uygun değiller; güvenlik
   filtresi kaldırılmış olanlar bilinçli olarak dışarıda bırakıldı.
3. Resmî hesaplar: `google` → yalnız 2 depo (`gemma-3n-E2B/E4B-it-litert-lm`,
   2025 tarihli, `license:gemma` yani kapılı; elimizdeki Gemma 4'ten eski).
   `Qwen` → **hiç litert-lm deposu yok**; tüm Qwen dönüşümleri zaten
   `litert-community` üzerinden geliyor.

**Ayrıca mevcut girdiler yeniden doğrulandı:** `gemma-4-12B-it.litertlm`
(6.547.589.312 B, SHA `74fc29a1…`) ve `gemma-4-E4B-it.litertlm`
(3.659.530.240 B, SHA `0b2a8980…`) katalogdaki değerlerle **birebir aynı**.

**Qwen: üç model eklendi** (hepsi kapısız, Apache-2.0, 2026-08-10'da doğrulandı):

| Model | Boyut | RAM | Neden |
|---|---|---|---|
| Qwen3.5 0.8B (int8) | 978.249.216 B | 5 GB | Katalogdaki **en yeni kuşak** (Ağustos 2026, hybrid/gated-deltanet mimarisi) |
| Qwen3 4B Instruct 2507 (int4) | 2.659.057.664 B | 8 GB | Düşünmeyen, doğrudan yanıtlayan 4B — ilk kelimeye kadar süre kısa |
| Qwen3 4B Düşünen 2507 (int4) | 2.274.193.168 B | 8 GB | Akıl yürütmeye ayrılmış 4B — matematik/mantık için |

Son ikisi aynı 4B'nin uzmanlaşmış sürümleri; genel amaçlı `Qwen3 4B` katalogda
kalıyor. Üçünde de `supportsThinkingToggle = false`, çünkü Instruct hiç
düşünmez, Düşünen ise düşünmeyi **kapatamaz** ve Qwen3.5'in yeni mimarisinde
`enable_thinking` desteği doğrulanmadı — desteklenmeyen anahtar iddia edilmiyor.

**Katalog: 16 model**, 2 GB'dan 24 GB'a her sınıfta kapısız seçenek.

### M2c — Düşünme anahtarı modele bağlandı (2026-08-10, yapıldı)

**Kusur:** `LocalModelSpec.supportsThinkingToggle` katalog dışında hiç
kullanılmıyordu. Modeller ekranındaki "Yerel düşünme" anahtarı modelden
bağımsızdı, başlığı da sabit "(Qwen3)" yazıyordu.

Katalogda yalnız Qwen3 varken bu göze batmıyordu. **Katalog 16 modele çıkınca
düşünmeyi desteklemeyen altı model oluştu** (Granite 350M, Gemma 3 1B,
FunctionGemma, Qwen3.5 0.8B ve iki 2507 sürümü) ve anahtar onlarda da etkin
görünüyordu — yani desteklenmeyen bir özellik çalışıyormuş gibi sunuluyordu.

**Düzeltme:** `LocalThinkingSupport` (saf, testli) anahtarın durumunu seçili
modelden türetiyor. Desteklemeyen modelde anahtar **gizlenmiyor, pasif
çiziliyor ve nedeni yazılıyor** — projenin "desteklenmeyen özellik taklit
edilmez; pasif gösterilir ve nedeni açıklanır" değişmezinin birebir karşılığı.
Kaybolup ortaya çıkan bir kontrol, sabit ama pasif olandan daha kafa
karıştırıcı olurdu.

Başlık da artık modelin ailesini yazıyor ("Yerel düşünme (Gemma 4)"), sabit
"(Qwen3)" değil.

`LocalThinkingSupportTest` sözleşmeyi **katalogun tamamında** doğruluyor: her
model için anahtar durumu `supportsThinkingToggle` ile birebir aynı olmalı.
Yeni model eklendiğinde bu test onu da kapsar.

### M3 — Quantization çeşitliliği (2026-08-10, yapıldı)

**Beklenmedik kolaylık:** int8 sürümleri **zaten sabitlenmiş revizyonlarda**
duruyordu — yeni commit gerekmedi, yalnız aynı depodaki farklı dosya.

| Model | Dosya | Boyut | RAM |
|---|---|---|---|
| Qwen3 4B (int8) | `qwen3_4b_channelwise_int8_float32kv.litertlm` | 5.672.370.176 B | 16 GB |
| Qwen3 8B (int8) | `qwen3_8b_channelwise_int8_float32kv.litertlm` | 8.307.720.192 B | 24 GB |

Off Grid'in GGUF ile sunduğu Q4/Q8 seçimi böylece LiteRT-LM tarafında da var.

**14B'nin int8'i (14,9 GB) bilerek eklenmedi:** hiçbir telefonda çalışmaz,
listede yer kaplamasının tek etkisi kullanıcıyı 15 GB'lık boşa indirmeye
çağırmak olurdu.

Test, çiftin karışmadığını doğruluyor: aynı revizyon, **farklı dosya adı**,
**farklı özet**, int8 daha büyük ve daha çok RAM istiyor.

### M3b — Katalogun tamamı sabitlendiği revizyonda doğrulandı (2026-08-10)

Katalogun 6 girdisi önceki oturumlardan geliyordu ve **sabitlendikleri
revizyonda hiç doğrulanmamıştı**. Bir özet tutmazsa kullanıcı GB'larca
indirdikten sonra öğrenirdi. Hepsi `/tree/<revizyon>` üzerinden kontrol edildi:

| Depo | Sonuç |
|---|---|
| Qwen3-0.6B (int4 + tam) | boyut + SHA-256 **birebir** ✓ |
| Qwen3-4B, Qwen3-8B, Qwen3-14B (int4) | boyut + SHA-256 **birebir** ✓ |
| gemma-4-E4B, gemma-4-12B | boyut + SHA-256 **birebir** ✓ |
| Gemma3-1B-IT, FunctionGemma (kapılı) | dosya sabitlenen revizyonda **var**, boyut **birebir**; SHA-256 API tarafından maskeleniyor (kapılı depo), bu yüzden hash bağımsız olarak doğrulanamadı |

Son satır bilinçli bir sınır: kapılı depoların özetini yalnız lisansı onaylamış
bir hesap görebilir. Katalogdaki değerler daha önceki bir turda girilmiş; boyut
ve varlık doğrulandı, hash doğrulanamadı.

Off Grid'in GGUF ile yaptığını LiteRT-LM ile yapmak: aynı model ailesinin
farklı quantization'larını yan yana sunmak. Katalogda hazır adaylar var
(`Qwen3-4B`, `Qwen3-8B` depolarında birden fazla dosya). Yapılacak: her
depodaki dosya listesini HF API'sinden doğrulayıp int4/tam çiftlerini eklemek.

**Ön koşul:** M1'deki modellerin fiziksel cihazda gerçekten yüklendiğinin
görülmesi. Katalog şişirmeden önce bir modelin çalıştığını bilmek gerekir.

### M4 — Cihaza özel derlemeler (araştırma)

`gemma-4-E2B-it-litert-lm` deposunda **donanıma özel** dosyalar var:
`_qualcomm_sm8750`, `_Google_Tensor_G5`, `-gpu`. Faz 9'da eklenen backend
seçimiyle doğal olarak eşleşiyor: GPU seçildiyse `-gpu` dosyasını, Snapdragon
8 Elite'te `_qualcomm_sm8750` dosyasını indirmek.

**Yapılmadı — iki bağımsız sebep (2026-08-10'da doğrulandı):**

1. **`-gpu` dosyaları, katalogun sabitlediği revizyonlarda YOK.** HF model
   API'si `gemma-4-E4B` için `sha=f7ad3343…`, `gemma-4-12B` için
   `sha=44cf85a3…` döndürüyor ve bu commit'lerin `siblings` listesinde
   yalnız standart `.litertlm` var. `-gpu` dosyaları yalnız `/tree/main`'de,
   yani **daha yeni ve sha'sı güvenilir şekilde okunamayan** bir commit'te
   görünüyor. Bu dosyaları mevcut revizyon sabitlerine bağlamak indirmede
   **404** üretirdi. Katalog kuralı (sabit commit + doğrulanmış SHA-256) tam
   olarak bu tuzağı yakalamak için var.
2. CPU'da çalışıp çalışmadıkları hâlâ bilinmiyor. README yalnız "XNNPack for
   CPU, ML Drift for GPU" diyor — ayrı yürütme yolları, geri düşme garantisi yok.

Kazanç gerçek olurdu (E2B: 2,0 GB vs 2,6 GB — %23 küçük), ama önce yeni
commit sha'sının güvenilir okunması, sonra cihazda ölçüm gerekiyor.

### M5 — İlk açılış model adımı (2026-08-10, yapıldı)

**Sorun:** uygulamanın manşet özelliği telefonda çevrimdışı LLM çalıştırmak,
ama varsayılan politika `GATEWAY_ONLY` olduğu için yeni bir kullanıcı Kontrol
ekranında bundan **hiç haberdar olmuyordu**. Modeller sekmesi vardı, oraya
yönlendiren yoktu. Kontrol'deki "Model indir" düğmesi yalnız politika zaten
telefonu kullanıyorken çıkıyor — yani tavuk-yumurta.

**Çözüm — Off Grid'den bilinçli olarak ayrıldık.** Off Grid kurulumdan hemen
sonra zorunlu bir model indirme ekranı gösteriyor. Burada Kontrol ekranında
**kapatılabilir bir kart** seçildi; gerekçe: zorunlu bir ekran, yalnız PC'ye
bağlanmak isteyen kullanıcıyı ilgilenmediği bir GB'lık indirmeyle karşılar.
Kart görünür olur ama yolu tıkamaz.

Kart yalnız üç koşul birden sağlanınca çıkar: hiç model kurulu değil,
kullanıcı kapatmamış, indirme sürmüyor. "Şimdilik atla" **kalıcıdır**
(`firstRunGuideDismissed`); kart bir daha çıkmaz.

Metin cihaza göre önerilen modeli ve boyutunu yazar (uydurmaz — çağıran
taraftan gelir) ve PC'ye bağlanmak isteyene bu adımın **gerekmediğini**
açıkça söyler.

Karar mantığı `FirstRunGuide` içinde saf ve testli; `ControlScreen`'e eklenen
parametrelerin varsayılanı "gösterme" olduğu için mevcut enstrümanlı testler
etkilenmedi.

---

## 4. Değişmeyen ilkeler

- Katalogdaki her satırın boyutu ve SHA-256'sı **HF API'sinden doğrulanır**,
  indirme URL'si **sabit commit'e kilitlenir**. Tahmin edilen değer eklenmez.
- Uygunluk çipi cihaz RAM'ine göre **Riskli** demekten çekinmez.
- Varsayılan model **kapısız** kalır (ilk kurulum tokensız).
- Desteklenmeyen (ör. donanıma özel varyant) taklit edilmez; ölçülmeden
  eklenmez.

---

## Kaynaklar

- [Off Grid incelemesi (GIGAZINE, 2026-04-01)](https://gigazine.net/gsc_news/en/20260401-off-grid-mobile-ai/) ·
  [Off Grid kaynak kodu (MIT)](https://github.com/alichherawalla/off-grid-mobile-ai) ·
  [Google Play sayfası](https://play.google.com/store/apps/details?id=ai.offgridmobile)
- HF API doğrulamaları (2026-08-09):
  [Qwen3-1.7B](https://huggingface.co/litert-community/Qwen3-1.7B) ·
  [gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) ·
  [granite-4.0-350m-litert-lm](https://huggingface.co/litert-community/granite-4.0-350m-litert-lm)
