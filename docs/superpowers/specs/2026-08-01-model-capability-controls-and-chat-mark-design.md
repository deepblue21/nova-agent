# NOVA Model Yetenekleri ve Sohbet Marka Hareketi Tasarımı

Tarih: 2026-08-01
Durum: Kullanıcı tarafından onaylandı

## Amaç

Web uygulamasında seçilen modelin gerçek Ollama yeteneklerini canlı katalogdan
okumak; düşünme ve araç kontrollerini yalnız destekleyen modellerde kullanılabilir
yapmak. Aynı çalışma, asistan mesajındaki Pulse Aperture işaretini biraz büyütür ve
yanıt tamamlandıktan sonra da sakin marka hareketini sürdürür.

## Yeteneğin Kaynağı

- Yerel modeller `/api/tags` ile bulunur.
- Her model `/api/show` ile sorgulanır; `capabilities` dizisindeki `tools`,
  `thinking`, `vision`, `audio` ve `embedding` değerleri katalogda taşınır.
- `capabilities` alanı varsa sonuç olumsuz olsa bile ölçülmüş kabul edilir.
- Eski Ollama sürümünde alan yoksa yalnız bilinen aileler için açıkça `family`
  kaynaklı yedek bilgi kullanılabilir.
- Tanınmayan özel model `unknown` kalır; düşünme ve araç kontrolleri kilitlenir.
- Bulut sağlayıcı, gateway ve ajan girdileri kendi güvenilir kaynak etiketlerini taşır.

## Düşünme Denetimi

`Hızlı`, `Dengeli`, `Derin`, `Maks` ve `Düşünme` kontrolleri yerleşimde kalır.
Seçili model düşünmeyi desteklemiyorsa hepsi pasif görünür. Fareyle üzerine gelme
ve klavye odağında, modelin düşünmeyi desteklemediği ya da yeteneğin
doğrulanamadığı açıklanır.

İstek eşlemesi:

| NOVA ayarı | Seviye destekleyen model | Aç/kapat destekleyen model |
|---|---|---|
| Düşünme kapalı | `low` (kapatılamayan modeller) veya `false` | `false` |
| Hızlı | `low` | `true` + kısa üretim bütçesi |
| Dengeli | `medium` | `true` + dengeli bütçe |
| Derin | `high` | `true` + geniş bütçe |
| Maks | `high` + maksimum üretim bütçesi | `true` + maksimum bütçe |

GPT-OSS ailesi `low/medium/high` düzeyli kabul edilir. Diğer Ollama düşünme
modelleri varsayılan olarak aç/kapat sözleşmesini kullanır. Desteklenmeyen modele
`think` gönderilmez; arayüz ve ağ isteği aynı gerçeği söyler.

## Araç, Ajan ve Web Araması

- `tools` destekleniyorsa Ajan ve Takım kontrolleri kullanılabilir.
- Desteklenmiyorsa kontroller görünür fakat pasiftir ve açıklama verir.
- Web araması modelin kendi başına internete çıkması değildir. Model araç çağrısı
  üretir; NOVA gateway arama aracını çalıştırır ve sonucu modele döndürür.
- Model seçicisinde araç ve düşünme rozetleri ölçülmüş/tahmini durumunu ayırır.

## Pulse Aperture Mesaj İşareti

- Asistan avatar yuvası `34 px` yerine `40 px` olur.
- Yanıt beklerken `thinking` hareketi: nefes, çekirdek nabzı, düşük yoğunluklu halo
  ve yörünge.
- Yanıt tamamlanınca `brand` hareketine geçer; daha yavaş nefes ve hafif iç ışık
  sürer, halo kapanır.
- İşaret yeni bir geometriyle çizilmez; mevcut `NovaMark` ve marka tokenları
  kullanılır.
- `prefers-reduced-motion: reduce` bütün hareketleri durdurmaya devam eder.

## Belgeler ve Temizlik

- Türkçe ve İngilizce README; canlı yetenek algılama, pasif kontrol davranışı,
  Ollama yenileme akışı ve zaman damgalı model önerileriyle güncellenir.
- Temizlik yalnız yeniden üretilebilir derleme çıktıları, önbellekler ve doğrulanmış
  geçici klasörlerle sınırlıdır. Kaynak, tasarım varlıkları, kullanıcı değişiklikleri,
  APK teslimatı, `.git` ve sır niteliğindeki dosyalar korunur.
- Silinen her tür için yeniden üretme komutu kayda geçirilir.

## Test ve Kabul Ölçütleri

- Gateway testleri `/api/show` yeteneklerini, olumsuz sonucu ve bilinmeyen durumu
  ayırır.
- Web testleri katalog alanlarını, `think` eşlemesini, pasif düğmeleri ve erişilebilir
  açıklamaları doğrular.
- Marka sözleşmesi testleri mesaj işaretinin beklerken `thinking`, tamamlandığında
  `brand` ve her iki durumda animasyonlu olduğunu doğrular.
- Gateway ve web testlerinin tamamı, web üretim derlemesi ve görsel QA geçer.
- Kullanıcının kirli ana çalışma alanındaki ilgisiz değişikliklere dokunulmaz.
