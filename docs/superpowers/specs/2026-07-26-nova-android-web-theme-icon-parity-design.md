# NOVA Android–Web Tema ve Animasyonlu İkon Eşliği

Tarih: 2026-07-26
Durum: Kullanıcı tarafından onaylandı; uygulama planı öncesi yazılı tasarım

## 1. Amaç

Android için daha önce hazırlanmış Pulse Aperture ikon ailesini ve tema davranışını
web uygulamasına kayıpsız aktarmak. Android ve web aynı tema kimliklerini, renkleri,
marka geometrisini, hareket sürelerini ve erişilebilirlik davranışını kullanacak.

Aynı çalışma ayrıca:

- bütün ikon renklerini Android ve web ayarlarından seçilebilir yapacak,
- eski ve birbirinden kopmuş tema kimliklerini tek listeye indirecek,
- Kızıl temayı parlak kırmızı bir arayüz yerine siyah/grafit ağırlıklı profesyonel
  bir temaya dönüştürecek,
- mat ve bulanık yüzey dilini iki platformda hizalayacak,
- web’in mobil gezinme ve Ayarlar kontrollerini daha kompakt ve hareketli yapacak,
- bütün varyantları güncel animasyonlarıyla tasarım dosyasında saklayacak.

## 2. Görsel Kaynaklar

Renk ve ışık dağılımı için mevcut animasyonlu SVG kaynakları kullanılacak:

1. `nova-pulse-aperture-orbital-v4-amethyst.svg`
2. `nova-pulse-aperture-orbital-v4-arctic.svg`
3. `nova-pulse-aperture-orbital-v4-sapphire.svg`
4. `nova-pulse-aperture-orbital-v4-emerald.svg`
5. `nova-pulse-aperture-orbital-v4-lime.svg`
6. `nova-pulse-aperture-orbital-v4-amber.svg`
7. `nova-pulse-aperture-orbital-v4-copper.svg`
8. `nova-pulse-aperture-orbital-v4-coral.svg`
9. `nova-pulse-aperture-orbital-v4-ruby.svg`
10. `nova-pulse-aperture-orbital-v4-lavender.svg`
11. `nova-pulse-aperture-orbital-v4-moonstone.svg`

Bu dosyalar yeniden çizilmeyecek. Renk durakları, iç ışık tonları ve katman
ilişkileri kaynaklardan alınacak. Üretim davranışı için Android’deki güncel
`NovaApertureTokens` ve `NovaThinkingIndicator` hareket sözleşmesi esas alınacak.
SVG’lerdeki eski prototip süreleri üretim koduna taşınmayacak.

## 3. Seçilen Yaklaşım

Seçilen yaklaşım, tek kaynaklı token sistemi ve ortak Pulse Aperture bileşenidir.

- `design/nova-tokens.json` elle düzenlenen tek üretim kaynağı olmaya devam eder.
- SVG kaynaklar renk/form referansı olarak `design/brand/pulse-aperture/` altında
  sürümlenir.
- Token üreticisi web ve Android çıktılarını birlikte üretir.
- Web `NovaMark`, Android `NovaThinkingIndicator` ile aynı normalize geometriyi,
  aynı hareket profillerini ve tema paletini okur.
- Her renk için ayrı kopyalanmış React bileşeni veya ayrı CSS animasyonu yazılmaz.

Bu yaklaşım doğrudan SVG dosyası göstermeye göre daha iyi küçük-boyut okunabilirliği,
erişilebilirlik ve durum kontrolü sağlar; on bir ayrı bileşen üretmeye göre tema
kaymasını ve bakım tekrarını önler.

## 4. Tema Kataloğu

Kullanıcıya görünen üretim tema listesi ve kalıcı kimlikleri:

| Kimlik | Görünen ad | Görsel kaynak |
|---|---|---|
| `amethyst` | Ametist | `v4-amethyst` |
| `arctic` | Arktik | `v4-arctic` |
| `sapphire` | Safir | `v4-sapphire` |
| `emerald` | Zümrüt | `v4-emerald` |
| `lime` | Limon | `v4-lime` |
| `amber` | Amber | `v4-amber` |
| `copper` | Bakır | `v4-copper` |
| `coral` | Mercan | `v4-coral` |
| `ruby` | Kızıl | `v4-ruby` |
| `lavender` | Lavanta | `v4-lavender` |
| `moonstone` | Aytaşı | `v4-moonstone` |

Her tema şu alanları taşır:

- `primary`, `secondary`, `tertiary`, `onPrimary`
- Pulse Aperture için `light`, `mid`, `deep`, `glow`, `core`
- isteğe bağlı `surface` bloğu: `bg`, `bg2`, `bg3`, `panel`, `line`,
  `blurTint`, `scrim`
- kaynak SVG ve üretim önizlemesi kimliği

### 4.1 Eski tema kimliklerinin geçişi

Mevcut kayıtlı tercihler bozulmayacak. Başlangıçta tek seferlik eşleme uygulanır:

- `aurora` ve `nova` → `arctic`
- `plum` → `lavender`
- `violet` ve `aperture` → `amethyst`
- `kizil` → `ruby`
- `okyanus` → `sapphire`
- `zumrut` → `emerald`
- `gul` → `coral`
- `gunbatimi` → `copper`

`amber` aynı kalır. Bilinmeyen veya bozuk tema kimliği güvenli biçimde
`amethyst` temasına düşer.

## 5. Kızıl Temanın Yüzey Sistemi

Kızıl tema kırmızı zemin kullanmaz. Yüzey alanının yaklaşık yüzde 85–90’ı siyah
ve grafit tonlarında kalır:

- ana zemin: `#050507`
- yükseltilmiş yüzey: `#0A0A0D`
- en arka katman: `#030304`
- mat panel: `rgba(255,255,255,0.035)`
- kızıl yüzey tonu: `#17070C`
- ana vurgu: `#EF3158`
- koyu vurgu: `#B80E36`
- açık ışık: `#FF8F9F`
- sıcak çekirdek: `#FFF9F2`

Kırmızı yalnız seçili durum, ince aktif gösterge, marka ışığı ve kontrollü odak
parlamasında görünür. Büyük panel, modal veya sayfa zemini kırmızıya boyanmaz.
Yıkıcı eylemler tema vurgusundan ayrı kalır; Kızıl temada da silme düğmesi normal
yıkıcı renk ve açık etiket kullanır.

## 6. Marka Bileşeni ve Kullanım Durumları

Web `NovaMark` tek bileşen olarak şu durumları destekler:

```tex
NovaMark(
  size,
  halo,
  animated,
  variant = activeThemeId,
  motion = "brand" | "thinking" | "preview"
)
```

- `brand`: üst çubuk, sol ray ve boş sohbet ekranı; halo yok, sakin nefes.
- `thinking`: yanıt bekleme durumu; seyrek nokta halkası ve enerji yayı açık.
- `preview`: Ayarlar’daki seçili veya üzerine gelinen tema örneği.
- `animated = false`: statik, yüksek kontrastlı ve erişilebilir kare.

Web’deki eski jenerik yıldız, spinner veya marka yerine kullanılan başka ikonlar
Pulse Aperture ile değiştirilir. Favicon/PWA ikonu aktif tema ile değişmez; üretim
varsayılanı Ametist’in statik, halosuz biçimidir.

## 7. Ortak Hareket Sözleşmesi

Üretim hareketleri Android’deki güncel davranışla aynıdır:

- gövde nefesi: `2750 ms`
- çekirdek nabzı: `1375 ms`
- nokta/yay yörüngesi: `4200 ms`
- iç ışık süpürmesi: `5500 ms`
- mikro eğim: en fazla `±0.7°`
- merkez işareti: dinlenmede `18`, tepede `23` oranı
- halo nokta sayısı: `14`

Ana gövde tam tur dönmez. Yalnız halo ve enerji yayı yörüngede hareket eder.
Çekirdek büyür/küçülür, iç ışık gövde içinde dolaşır ve yüzey taraması düşük
yoğunlukta geçer.

`prefers-reduced-motion: reduce` ve Android sistem animasyon ölçeği kapalıyken
nefes, yörünge, parıltı ve mikro eğim durur. Tema seçimi yine renk ve opaklıkla
anlaşılır kalır.

## 8. Ayarlar ve Tema Seçimi

Android ve web Ayarlar → Görünüm aynı 11 temayı aynı sırada gösterir.

- Web geniş görünümde iki veya üç sütun, dar görünümde iki sütun kullanır.
- Tema kartları büyük metin düğmeleri yerine kompakt renk örneği, küçük Pulse
  Aperture işareti ve ad içerir.
- Yalnız seçili veya işaretçiyle üzerine gelinen önizleme animasyon oynatır.
- Seçim anında kart hafifçe sıkışır, sonra yerine oturur; yalnız renk değiştirmekle
  kalmaz.
- Tercih web’de mevcut kalıcı ayar deposuna, Android’de `SettingsStore` içine yazılır.
- Yeniden başlatma sonrası seçilen tema korunur.

## 9. Mat/Blur Yüzey ve Kompakt Kontroller

Web’in mobil/dar yerleşiminde onaylanan yön “Yüzen Mat Cam Dock”tur:

- alt gezinme yüksekliği yaklaşık `58 px`,
- öğe dokunma alanı erişilebilirliği koruyacak biçimde en az `44 px`,
- aktif öğe en fazla `2 px` yükselir,
- düşük doygunluklu mat zemin ve yaklaşık `18–22 px` arka plan bulanıklığı,
- aktif öğede ince tema renkli iç çizgi ve düşük yoğunluklu alt ışık,
- geçiş süresi `180–250 ms`,
- Ayarlar düğmesinde tıklama sırasında küçük sıkışma ve en fazla `22°` kontrollü
  mikro dönüş.

Kontrol, İşler, Sohbet ve Modeller öğeleri yalnız renk değiştirmez; ikon/etike
birlikte kısa konum ve opaklık geçişi yapar. Hareket hiçbir zaman gezinmeyi
geciktirmez ve azaltılmış hareket ayarında kapanır.

## 10. Tasarım Dosyası

`docs/design/Nova İkon.dc.html` güncellenerek yeniden kullanılabilir marka
kütüphanesine dönüştürülür:

- 11 renk varyantının canlı animasyonlu önizlemesi,
- launcher/brand/thinking/preview kullanım durumları,
- 96, 72, 48 ve 36 px küçük boyut kontrolü,
- açık/koyu ve mat/blur yüzey örnekleri,
- Kızıl temanın siyah ağırlıklı panel örneği,
- azaltılmış hareket statik durumu,
- tema kimliği ve kaynak dosya adı.

Tasarım dosyası uygulama kodundan bağımsız bir kopya çizmez; aynı kaynak SVG’lere
ve token manifestine bağlanır.

## 11. Veri Akışı ve Hata Davranışı

1. Kullanıcı bir tema seçer.
2. Kalıcı tercih tema kimliği olarak kaydedilir.
3. Tema motoru renk ve isteğe bağlı yüzey tokenlarını uygular.
4. `NovaMark` aynı kimliğin Pulse Aperture paletini okur.
5. Sayfa/uygulama yeniden açıldığında kimlik doğrulanır ve tekrar uygulanır.

Eksik renk alanı derleme/test hatasıdır. Çalışma zamanında bilinmeyen kimlik,
uygulamayı kırmadan Ametist’e düşer ve eski kimlik geçiş tablosu bir kez uygulanır.

## 12. Test ve Görsel Doğrulama

Otomatik kontroller:

- Android ve web tema kimlikleri, sırası ve görünen adları eşleşir.
- Her temada beş Pulse Aperture rengi ve gerekli arayüz renkleri vardır.
- Eski tema kimliklerinin tamamı beklenen yeni kimliğe taşınır.
- Kızıl tema yüzeyleri siyah/grafit eşiklerini korur; kırmızı zemin üretmez.
- `NovaMark` brand/thinking/preview durumlarını ve `animated=false` seçeneğini üretir.
- Ayarlar tüm temaları gösterir, seçimi kaydeder ve yeniden yükler.
- Azaltılmış hareket medya sorgusu bütün marka ve navigasyon animasyonlarını durdurur.
- Web birim testleri ve üretim derlemesi geçer.
- Android token/test derlemesi ve tema seçici testleri geçer.

Görsel QA:

- kaynak SVG ve web önizlemesi aynı karede karşılaştırılır,
- Ametist, Kızıl, Arktik ve Aytaşı en az dört temsilî tema olarak incelenir,
- başlık, boş sohbet, yanıt bekleme ve Ayarlar önizlemesi kontrol edilir,
- masaüstü ve dar web yerleşimi karşılaştırılır,
- Kızıl temada siyah yüzey oranı ve metin kontrastı ayrıca doğrulanır.

## 13. Tamamlanma Ölçütleri

- 11 tema hem Android hem web Ayarlar → Görünüm bölümünden seçilebilir.
- Seçilen tema arayüzü ve Pulse Aperture işaretini birlikte değiştirir.
- Web işaretinin geometri ve hareketleri Android üretim sözleşmesiyle eşleşir.
- Kızıl tema siyah ağırlıklı ve profesyonel görünür.
- Bütün varyantlar güncel animasyonlarıyla tasarım dosyasında bulunur.
- Mobil web alt gezinmesi ve Ayarlar kontrolü kompakt mikro hareketlere sahiptir.
- Tema seçimi yeniden başlatmada korunur.
- Azaltılmış hareket davranışı iki platformda çalışır.
- Otomatik testler, üretim derlemeleri ve görsel QA geçer.

## 14. Kapsam Dışı

- Yeni launcher geometrisi çizmek.
- Pulse Aperture dışında ikinci bir marka işareti eklemek.
- Uygulamanın bilgi mimarisini veya sayfa sayısını değiştirmek.
- Tema seçimini kullanıcı hesabı üzerinden cihazlar arasında eşitlemek.
- Üretim ortamına dağıtım veya yayınlama.
