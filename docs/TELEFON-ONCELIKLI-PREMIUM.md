# Telefon öncelikli kullanım ve Google Play Premium

Tarih: 3 Ekim 2026.

## Kullanıcı kararı

Öncelik telefonda çalışan özellikleri iyileştirmek. Web istemcisi ve uzak
bilgisayarın GPU'sunda çalışma Premium abonelik kapsamında olacak. Abonelik
Google Play'den alınacak; aynı NOVA hesabı web ve uzak GPU erişimini kullanacak.
Buradaki “web”, mevcut tarayıcı istemcisi olarak yorumlanmıştır.

## Ürün sınırı

| Telefonda temel kullanım | Premium |
| --- | --- |
| İndirilen modelle yerel ve çevrimdışı sohbet | Web istemcisinden hizmete erişim |
| Telefonun kendi CPU/GPU hızlandırması | Uzak bilgisayar/GPU üzerinde çıkarım |
| Yerel araçlar: saat, hesaplama, cihaz durumu, notlar | PC ajanına görev devri |
| Cihazdaki sohbet geçmişi ve dışa aktarma | Hibrit modun uzak yürütme ve PC'ye devir yolları |

Telefon özellikleri hesap, abonelik sorgusu veya çalışan bir Gateway gerektirmemeli.
Modeli ilk kez indirmek internet gerektirir. Premium'un süresinin dolması yerel
modelleri, sohbet geçmişini ve cihazda çalışmayı engellememeli. Telefonun kendi
GPU'su ile uzak bilgisayar GPU'su farklı erişim kurallarına sahiptir.

## Bu aşamanın kod kapsamı

- Eksik veya bozuk yürütme ayarı yerel öncelikli davranır; otomatik PC seçmez.
- Cihaz koşulları verilmeden kullanılan hibrit yönlendirme cihazda kalır.
- Hibrit modda hassas içerik, yerel model bulunmadığında da otomatik gönderilmez;
  kurulum veya açık devir onayı beklenir.
- Çalışma politikası değiştirilince önceki bekleyen devir izni temizlenir.
  Devir anında güncel politika ve bağlantı tekrar kontrol edilir.
- Kapalı İşler sekmesine giden birincil düğmenin yerini sohbet başlangıcı alır.
  İşler özelliği tekrar etkinleştirilirse görev düğmesi aynı özellik bayrağını izler.
- Kurulu ama doğrulanmamış model için hazırlık kartından yerel doğrulama yapılabilir.
- Modeller ekranındaki yerel sohbet başlangıcı Çevrimdışı politikasını seçer.

Doğrulama: 455/455 JVM; 46/46 ilgili emülatör testi (18 kontrol, 26 model/sohbet/
açılış, 2 gerçek ViewModel yönlendirmesi); lint sıfır hata; debug APK ve test APK
derlendi. `docs-check` başarılı. 126 enstrümanlı testin tamamı bu turda koşulmadı;
fiziksel ARM64 cihazda model çıkarımının performansı ölçülmedi. Kanıt dosyaları
`audit-screenshots/mobile-first-*.log`, `mobile-first-*-results.xml` ve
`mobile-first-ui-verification.txt` altında.

## Premium entegrasyonu için önerilen tasarım

Mevcut `gateway/lib/billing.mjs`, Stripe kullanım bildirimi yapar; aktif abonelik
doğrulaması değildir. Android'de Play Billing, uygulamalar arasında ortak Premium
yetkisi veya satın alma ekranı henüz yoktur. Bu belge bir tasarımdır; Premium
erişimi uygulanmış, satın alınabilir veya etkinleştirilmiş sayılmaz.

1. Mevcut NOVA kullanıcı kimliğini temel alan merkezi hesap akışı Android ve web'de
   kullanılmalı. Play satın alımı, Google hesabını tahmin etmek yerine oturumdaki
   NOVA kullanıcısına bağlanmalı; satın alma akışına sunucunun ürettiği karartılmış
   hesap kimliği verilmelidir.
2. Android Play Billing ile gerçek ürün/fiyat/teklif bilgisini alır ve satın alma
   belirtecini kimlikli merkezi API'ye gönderir. İstemci tercihlerine yazılan bir
   `premium=true` değeri yetki kaynağı değildir.
3. Merkezi API belirteci Google Play Developer API `purchases.subscriptionsv2.get`
   ile doğrular. Paket, izin verilen ürün, hesap eşleşmesi, abonelik durumu ve bitiş
   zamanı kontrol edilir; aynı satın alma birden fazla hesaba bağlanamaz. Başarılı
   satın alma sunucudan acknowledge edilir. [Google entegrasyon rehberi](https://developer.android.com/google/play/billing/integrate).
4. Yetki NOVA hesabına kaydedilir. Android ve web aynı kimlikli yetki uç noktasından
   `web_access` ve `remote_compute` durumunu okur. Uzak GPU bilgisayarı satın alma
   doğrulamasının otoritesi olmamalıdır.
5. Web ve uzak yürütme isteklerinde sunucu yetkiyi kontrol eder. Yalnız arayüzde
   düğme kilitlemek, `Origin` veya istemcinin bildirdiği platformu kontrol etmek
   yeterli değildir. Telefonun yerel yolu bu ağ kontrolüne girmez.
6. Yenileme, iptal, ödeme bekleme ve iade olayları RTDN bildirimi sonrasında Google
   API'den yeniden okunur. Süresi dolmamış iptal edilmiş abonelik dönem sonuna kadar
   kullanılabilir; ödemesi bekleyen, duraklatılmış veya süresi dolmuş satın alma
   yanlışlıkla açılmaz. Geri yükleme ve hesap değişimi aynı sahiplik kontrolünü
   kullanır. [Google abonelik yaşam döngüsü](https://developer.android.com/google/play/billing/lifecycle/subscriptions).

## Canlı açılışın bağımlılıkları

Kullanıcı 3 Ekim'de Play abonelik ürününün ve merkezi sunucu adresinin henüz
oluşturulmadığını doğruladı. Bu eksikler telefon iyileştirmeleri ve arayüz
çalışmasından ayrı izlenir; canlı Premium erişimi bu oturumda açılmamıştır.

- Play Console'da gerçek abonelik ürün kimliği, base plan ve fiyatlandırma.
- Merkezi hesap/yetki API'sinin HTTPS adresi ve çalışacağı ortam.
- Android Publisher API erişimi; kimlik bilgileri yalnız sunucuda saklanmalı.
- Play dahili test sürümü ve lisans test kullanıcısıyla satın alma, geri yükleme,
  yenileme/sona erme ve aynı hesapla web/PC erişimi doğrulaması.

Bu yapılandırmalar doğrulanmadan gerçek ücret tahsilatı ve Premium etkinleştirme
tamamlanmış kabul edilmez. Telefon iyileştirmeleri bunlardan bağımsız teslim edilir.
