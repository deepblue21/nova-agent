# Telefon arayüzü: koyu cam

3 Ekim 2026. Telefon öncelikli yönlendirme düzeltmelerinin ardından uygulandı.

## İnceleme ve karar

Emülatörde Kontrol, Sohbet ve Modeller ekranları incelendi. Ana ekranda iki ayrı
model kurulum çağrısı, ilk kullanımda açık duran dört teknik politika seçeneği
ve boş PC/iş panelleri vardı. Modeller ekranı bütün kataloğu ilk anda gösteriyordu.
Küçük yazılar Material'ın sabit 24sp satır yüksekliğini devralıyor, bazı açıklamalar
koyu zeminde zor okunuyordu.

Seçilen yön: mevcut NOVA markasını ve kullanıcının seçtiği renk temasını koruyan
koyu, renkli cam yüzeyler. Hiyerarşi ve okunabilirlik, saydamlıktan önce geliyor.

- Kontrol / Basit: tek kurulum çağrısı; çalışma biçimi açılır seçim alanında.
  Boş iş ve ilgisiz PC geçmişi gizli; gerçek iş ve PC bağlantısı korunur.
- Modeller / Basit: önerilen, seçilen ve yönetilmesi gereken modeller önce gelir.
  Katalog tek düğmeyle açılır. Kurulu, yarım kalmış, indirilen ve hata veren
  modeller saklanmaz. PC modelleri PC/Hibrit kullanımında ve Gelişmiş modda görünür.
- Sohbet: daha kısa üst bilgi, cam giriş alanı ve odaklandığında renkli kenarlık.
  Büyük yazıda üst kontroller satır kırar; giriş alanı en fazla altı satır gösterir,
  daha uzun taslak içeride kayar.
- Ortak kabuk: kenarlardan ayrılmış alt gezinme, yumuşak ışık alanları, ince cam
  kenarlıkları ve yazı boyutuna bağlı satır yüksekliği.
- Etkiler: 120–220ms açılma/kapanma, 180ms seçim ve odak geçişleri. Arka plan
  ışıkları sabittir; canlı blur ve sürekli dekoratif animasyon eklenmedi.

Bu tercih, telefonda model çalışırken arayüzün gereksiz çizim işini azaltmayı
amaçlar; fiziksel cihazda FPS/batarya iyileşmesi ölçülmüş değildir.

## Araştırma dayanağı

- [Android erişilebilirlik rehberi](https://developer.android.com/guide/topics/ui/accessibility/apps):
  küçük metin için 4.5:1 kontrast ve en az 48dp dokunma alanı. Yeni açılır
  kontrollerde 48dp alt sınır, politika seçiminde radyo grubu semantiği kullanıldı.
- [Compose Brush](https://developer.android.com/develop/ui/compose/graphics/draw/brush):
  cam tonu ve ışık alanları gradyanlarla çizildi.
- [Compose çizim değiştiricileri](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers):
  arka plan fırçaları `drawWithCache` ile önbelleğe alındı.
- [Android çizim performansı](https://developer.android.com/topic/performance/rendering/):
  efekt kapsamı sınırlı tutuldu; ek bulanıklaştırma katmanı oluşturulmadı.
- [Compose ekran ve klavye boşlukları](https://developer.android.com/develop/ui/compose/system/insets-ui):
  klavye alanı kabukta ayrılır, alt katmanlarda tekrar eklenmez. Klavye açıkken
  alt gezinme; yatay klavyede üst çubuk ve sohbet bilgi düğmeleri çekilir.
  Klavye kapanınca geri gelirler. Bu düzenleme yatay emülatörde gözlenen
  mesaj alanının klavye arkasında kalması sorununa karşı yapıldı.

Renklerin kaynağı `design/nova-tokens.json`. `muted2` açıklama rengi buradan
aydınlatıldı; Android ve web çıktıları `npm run tokens` ile üretildi.
Yeni açıklama renginin tüm tanımlı temaların düz `bg` / `bg2` / `bg3` zeminlerinde
hesaplanan en düşük kontrastı 6,62:1. Bu sayı diğer bütün renk çiftlerinin veya
cam üzerindeki her pikselin erişilebilirlik denetimi değildir.

## Görsel kanıt

Önce: `audit-screenshots/glass-before-control.png`, `glass-before-chat.png`,
`glass-before-models.png`. Ekran görüntüleri gerçek Android emülatöründen alındı.

Sonra: `glass-after-control.png`, `glass-after-chat.png`, `glass-after-models.png`.
Ek kontroller: `glass-control-large.png`, `glass-models-large.png`,
`glass-chat-large.png`, `glass-chat-keyboard.png`, `glass-chat-keyboard-large.png`,
`glass-chat-landscape-keyboard.png`. Büyük yazı kontrolü 1,3 ölçeğiyle yapıldı.
Yatay klavye görüntüsünde yön `rotation=1` olarak doğrulandı; giriş ve gönder
düğmesi görünür. Kontrol sonunda yazı ölçeği 1,0, otomatik yön açık ve kullanıcı
yönü 0 olarak geri yüklendi. Emülatör: Android 16, portre 1080 × 2424.

## Doğrulama

- Android JVM: 455/455.
- İlgili emülatör süiti: 59/59; ilave iki indirme regresyonu eski kodda başarısız,
  düzeltmeden sonra başarılı. Toplam 61 farklı ilgili test.
- Son klavye yerleşimiyle açılış, gezinme, sohbet ve indirme için 12/12 tekrar
  kontrolü: `audit-screenshots/glass-completion-tests.txt`.
- Web: ortak renk çıktısıyla 22/22 test ve üretim derlemesi başarılı.
- Android debug APK/test APK derlemesi başarılı. Lint: 0 hata, 26 uyarı, 5 ipucu.
- Kaynakta toplam 133 enstrümanlı test bulunuyor; tamamı bu çalışmada koşulmadı.
- Fiziksel ARM64 cihazda model çıkarımı, FPS ve pil tüketimi ölçülmedi.

İndirme önerisi, indirme sürerken veya hata varken yönetim kartını örtmez;
duraklatma ve hata mesajı Basit modda da erişilebilir. Test kanıtları:
`glass-download-red.txt`, `glass-final-targeted-tests.txt`.

## Sınırlar

Google Play ürün kimliği ve ortak hesap/abonelik sunucusu henüz yok. Bu arayüz
değişikliği ödeme veya Premium yetkisini canlıya açmaz. Ayrıntı:
[Telefon öncelikli Premium planı](TELEFON-ONCELIKLI-PREMIUM.md).
