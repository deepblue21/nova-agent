# Model kataloğu ve sade arayüz — 4 Ekim 2026

Araştırma kesimi: 3 Ekim 2026. Sonraki Ekim yayınları bu listeye dahil değildir.
Katalog 22 pakete çıktı. Yeni paketlerin sürüm, boyut ve LFS SHA-256 değerleri
Hugging Face API'sinden okundu; indirmeler değişebilir `main` dalına bağlanmadı.
Ham kanıtlar yerelde `audit-screenshots/catalog-*-metadata.json` içinde.

## Eklenen seçenekler

| Paket | Tam indirme boyutu (bayt) | Tahmini cihaz RAM'i | Kaynak |
| --- | ---: | ---: | --- |
| Qwen3.5 4B mixed int4 | 2.754.365.536 | 8 GB | [Model kartı](https://huggingface.co/litert-community/Qwen3.5-4B/tree/2345e16c6e6e5a2db95d5bcfa3b4fc4dc2fc0394) |
| Qwen3.5 4B int8 | 4.407.428.464 | 12 GB | Aynı sabit sürüm |
| Granite 4.2 3B int4 | 2.190.708.656 | 8 GB | [Model kartı](https://huggingface.co/litert-community/granite-4.2-3b/tree/3b7f1338ee26a06d016a72a7d5c64f3ed022a466) |
| LFM2.5 2.6B int4 | 1.668.151.680 | 6 GB | [Model kartı](https://huggingface.co/litert-community/LFM2.5-2.6B/tree/88cc3e27a083cc1f134285a35b3618d13d100032) |

Qwen3.5 dönüşümleri yalnız metindir; görsel, araç çağırma ve düşünme anahtarı
içermez. Model kartının Android uyarısı nedeniyle Otomatik mod CPU seçer;
açık GPU/NPU tercihi değiştirilmez. int4 CPU önbelleği ayrıca yaklaşık 3 GB boş
alan ister. Granite şablonu düşünme anahtarını destekler. LFM düşünerek yanıt
verir ve LFM Open License v1.0'a tabidir; Apache lisanslı olarak gösterilmez.

Mevcut [Gemma 4 12B int4](https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm/tree/44cf85a326f79b814fa86a60af414c042755b43a)
ve [Qwen3 14B int4](https://huggingface.co/litert-community/Qwen3-14B/tree/e4122fd370cec85c61467274b180e0954e4f422d)
paketlerinin boyut ve özetleri yeniden doğrulandı; mevcut indirmeler korunuyor.
Yeni Gemma 12B GPU/web paketleri farklı hedefler için olduğundan mevcut telefon
paketinin yerine sessizce geçirilmedi. 15B sınıfında bu araştırmada doğrulanmış
uygun LiteRT-LM paketi bulunmadı; GGUF dosyaları mevcut motorda çalışırmış gibi eklenmedi.

## Uygulama davranışı

- Model satırı: ad, boyut, quantization; kurulu/indirilen/hatalı modelde kısa durum.
- Dokununca cam yüzeyli pencere: özet, RAM tahmini, lisans, cihaz uygunluğu ve işlemler.
- Pencere güncel indirme/doğrulama durumunu izler; kapatmak indirmeyi iptal etmez.
- Pencere seçimi ekran yeniden kurulduğunda korunur. Büyük yazıda içerik kayar,
  kapatma düğmesi görünür kalır. Basit modda model ayarları açılır bölümde bulunur.
- Tema önizlemeleri iki renkli; seçili tema işaretlidir. Cam yüzeylerin vurgusu
  güçlendirildi. Renk değişimi 240 ms sürer; sürekli çalışan efekt eklenmedi.
- İlk kurulum varsayılanı katalog sırasından bağımsız Qwen3 0.6B int4 kalır.

Motor [LiteRT-LM 0.17.1](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.1),
Kotlin derleyicisi 2.3.21 oldu. Yeni motorun ayrı `thought` kanalı NOVA'nın düşünme
akışına aktarılır; düşünme sırasında gelen parçalar zaman aşımı izleyicisine de ulaşır.
API kaynağı: [Message.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Message.kt).

## Doğrulama sınırı

461 JVM ve 34 ilgili Android emülatör testi başarılı. Debug APK/test APK ve
Android lint tamamlandı (0 hata, 25 uyarı, 5 ipucu). 240 Gateway, 22 web, 34 Node
smoke ve 7 Playwright testi başarılı. Ekranlar Android 16 emülatöründe incelendi:
[kompakt liste](design/qa/2026-10-04/models-compact-final.png),
[ayrıntı penceresi](design/qa/2026-10-04/models-dialog-final.png),
[genişletilmiş liste](design/qa/2026-10-04/models-expanded-final.png),
[tema önizlemeleri](design/qa/2026-10-04/themes-preview-final.png).

Model dosyalarının tamamı indirilmedi. Boyut/özet/sürüm kontrolü, Android derlemesi,
JVM ve emülatör UI testleri gerçek ARM64 cihazda çıkarım başarısı veya hız garantisi değildir.
Özellikle 12B/14B paketler için çalışma belleği dosya boyutundan büyüktür; arayüz bunu açıklar.
Google Play ürün kimliği ve ortak hesap sunucusu olmadığı için Premium henüz canlı değildir.
