# Horus Android Görev Öncelikli Kontrol Merkezi Tasarımı

Tarih: 2026-07-13

Durum: Kullanıcı tarafından onaylandı

## 1. Amaç

NOVA Android uygulamasını, PC'deki yerel LLM ve Mobile Worker ile çalışan telefon otomasyonu için kullanılabilir bir kontrol merkezine dönüştürmek. Uygulama; görev oluşturmayı, bağlantı durumunu anlamayı, çalışan görevi izlemeyi, riskli eylemleri onaylamayı ve gerektiğinde sohbet ya da ses moduna geçmeyi küçük bir telefon ekranında açık ve güvenilir hale getirecek.

Bu çalışma mevcut Kotlin, Jetpack Compose, ViewModel, Gateway API, SSE görev akışı ve adaptif launcher ikonunu korur. Yeni bir ürün mimarisi veya yeni bir ağ sözleşmesi kurmaz; mevcut yetenekleri mobil öncelikli bir bilgi mimarisiyle sunar.

## 2. Mevcut Durum ve Doğrulanan Sorunlar

Emülatörde 1080 x 2280 ekran üzerinde Ses, Sohbet, Görevler ve Gateway Ayarları akışları incelendi.

- Alt çubuk; mod seçimi, model, efor ve düşünme kontrollerini aynı yatay alana koyduğu için ekran dışına taşıyor ve yatay kaydırma gerektiriyor.
- Görevler boş durumu yalnızca bir metin alanı gösteriyor; kullanıcıya örnek, yetenek sınırı veya bağlantı hazırlığı anlatılmıyor.
- Üst başlıktaki model ve efor metni bağlantı durumundan daha görünür, fakat ana hedef PC LLM ve worker hazır olup olmadığını anlamak.
- Sohbet ve ses ekranlarının görsel kimliği tutarlı olsa da bazı dokunma hedeflerinde erişilebilirlik adı yok.
- Gateway ayarı teknik olarak çalışabilir durumda, fakat bağlantıyı kaydetmeden önce veya sonra doğrulayacak görünür bir test akışı bulunmuyor.
- `@mipmap/ic_launcher` ve `@mipmap/ic_launcher_round` adaptif ikonları, API 26 katmanları ve API 33 monochrome katmanları mevcut. Launcher'da koyu zemin, turkuaz yörünge ve amber çekirdek doğru görüntüleniyor; ikon yeniden tasarlanmayacak.

## 3. Değerlendirilen Yaklaşımlar

### Sohbet öncelikli

Uygulama sohbetle açılır ve otomasyon görevleri sohbet içinden başlatılır. Öğrenme maliyeti düşüktür, ancak çalışan görev durumu, onaylar ve worker bağlantısı geri planda kalır.

### Dashboard öncelikli

Bağlantı, model, performans ve görev geçmişi tek ana ekranda gösterilir. Operasyonel görünürlük yüksektir, fakat kişisel kullanım için gereksiz yoğunluk ve daha fazla uygulama kapsamı oluşturur.

### Görev öncelikli kontrol merkezi

Uygulama Görevler sekmesiyle açılır. Bağlantı durumu, doğal dil komutu, hızlı örnekler, aktif görev, onay ve sonuç aynı akışta sunulur. Sohbet ve Ses sabit alt navigasyonda kalır. Project Horus'un PC LLM destekli telefon otomasyonu hedefini en doğrudan desteklediği için seçilen yaklaşım budur.

## 4. Bilgi Mimarisi

Uygulama üç eşit birincil hedef içerir:

1. Görevler: Varsayılan açılış hedefi. Telefon otomasyonu komutu, aktif görev, olay akışı ve onaylar burada bulunur.
2. Sohbet: PC LLM ile genel amaçlı, akışlı metin konuşması.
3. Ses: Mikrofon üzerinden konuşma ve sesli cevap.

Model, efor ve düşünme ayarları birincil navigasyondan çıkarılır. Bunlar Ayarlar içinde `Model ve çalışma biçimi` bölümüne taşınır. Böylece alt navigasyon sabit kalır ve yatay kaydırma tamamen kaldırılır.

## 5. Görsel Sistem

Mevcut NOVA koyu teması korunur:

- Arka plan: siyaha yakın `Bg`.
- Yüzeyler: lacivert-siyah `Bg2`, `Surface1` ve `Surface2`.
- Birincil vurgu: turkuaz `Cyan` ve mavi `Azure` geçişi.
- Dikkat ve aktif çekirdek: launcher ikonuyla uyumlu amber; yalnız bağlantı uyarısı, aktif ilerleme odağı veya önemli durumlarda kullanılır.
- Hata ve iptal: `Coral`.
- Birincil metin: `TextMain`; ikincil metin kontrastı okunabilir düzeyde yükseltilmiş `Muted`.

Kartlar 16-20 dp köşe yarıçapı, düşük kontrastlı sınır ve net iç boşluk kullanır. Dekorasyon yerine durum ve eylem hiyerarşisi önceliklidir. Mevcut Material Icons kullanılır; özel görünür varlık veya el yapımı yeni SVG üretilmez.

## 6. Uygulama Kabuğu

### Üst alan

- Solda kompakt NOVA işareti ve `NOVA` başlığı.
- Başlığın altında model listesi yerine anlaşılır bağlantı özeti: `PC hazır`, `Bağlanıyor`, `Kurulum gerekli` veya `Bağlantı yok`.
- Sağda Ayarlar düğmesi.
- `Yeni sohbet` yalnız Sohbet sekmesinde bağlamsal eylem olarak görünür; Görevler ve Ses başlığını kalabalıklaştırmaz.

### Alt navigasyon

- `Görevler`, `Sohbet`, `Ses` eşit genişlikte üç hedef olarak sabitlenir.
- Yatay kaydırma, model seçici, efor segmenti ve reasoning anahtarı alt alandan kaldırılır.
- Her hedef en az 48 dp dokunma alanına, metin etiketine ve erişilebilirlik rolüne sahiptir.

## 7. Görevler Ekranı

### Boş durum

- `Telefonunda ne yapmamı istersin?` başlığı ve PC/worker durumuna bağlı kısa açıklama.
- En fazla üç hızlı görev önerisi: `Android sürümünü bul`, `Ayarlar'ı aç`, `Bir uygulamayı aç`.
- Çok satırlı doğal dil görev alanı ve açık `Görevi başlat` eylemi.
- Gateway veya worker hazır değilse komut kaybolmaz; gönderme eylemi yerine `Bağlantıyı ayarla` ya da `Tekrar dene` gösterilir.

### Aktif görev

- En üstte görev özeti ve Türkçe durum etiketi.
- Duraklat, devam et ve iptal yalnız geçerli durumlarda görünür.
- Olaylar teknik event adları yerine kullanıcıya dönük aşamalarla zaman çizelgesinde sunulur: `Sıraya alındı`, `Cihaz inceleniyor`, `Plan hazırlanıyor`, `Eylem uygulanıyor`, `Sonuç doğrulanıyor`, `Tamamlandı`.
- Teknik event türü tanılama için içeride korunur; ana metin kısa özet olur.
- Terminal durumda sonuç kartı ve `Yeni görev` eylemi gösterilir.

### Onay

Bekleyen riskli eylem, ekranın altına sabitlenen ve geri planı karartan belirgin bir panel olarak açılır. Panel eylemi, hedefi ve risk seviyesini açıklar. `Reddet` ikincil, `Onayla` birincil eylemdir. Bir onay kararı verilene kadar yeni otomasyon eylemi üretilmez.

## 8. Sohbet ve Ses Ekranları

Sohbet ekranı mevcut akışlı mesaj davranışını korur. Boş durumda kısa örnek istemler gösterilir. Mesaj alanı klavye ile birlikte yeniden boyutlanır; gönder/durdur kontrolü açık erişilebilirlik adı alır. Kopyala ve yeniden üret eylemleri en az 48 dp dokunma alanına taşınır.

Ses ekranı mevcut Orb kimliğini korur. Orb daha dengeli bir dikey yerleşimde tutulur; durum, kısa açıklama ve tek mikrofon/durdur düğmesi okunabilir bir grup oluşturur. Mikrofon kontrolüne mevcut duruma göre `Dinlemeyi başlat`, `Dinlemeyi durdur` veya `Konuşmayı durdur` erişilebilirlik adı verilir.

## 9. Ayarlar ve Bağlantı Akışı

Ayarlar tek bir büyük teknik modal yerine kaydırılabilir bir panel olarak düzenlenir:

1. PC bağlantısı: Gateway URL, güvenli token alanı, `Bağlantıyı test et` ve bağlantı sonucu.
2. Model ve çalışma biçimi: model, efor ve düşünme seçimi.
3. Uygulama bilgisi: sürüm ve yerel çalışma açıklaması.

Bağlantı testi, kayıtlı veya formdaki URL/token ile Gateway sağlık kontrolü yapar. Uygulama başarısızlığın türünü kullanıcıya güvenli biçimde açıklar; token, provider anahtarı veya gizli sunucu ayrıntısı loga ya da ekrana yazılmaz. Kaydetme sonucu kısa ve erişilebilir bir durum mesajıyla bildirilir.

## 10. Bileşen Sınırları ve Veri Akışı

- `NovaApp`: seçili birincil hedefi, ayar panelini ve uygulama kabuğunu yönetir.
- `NovaTopBar`: seçili hedefe göre başlık ve bağlamsal eylemleri gösterir.
- `NovaBottomNavigation`: yalnız üç birincil hedefi yönetir.
- `MobileTaskScreen`: boş, aktif, onay ve terminal görev durumlarını sunar; ağ çağrısı yapmaz.
- `MobileTaskViewModel`: mevcut create, SSE, command ve confirmation davranışını korur; kullanıcıya dönük bağlantı ve yeniden deneme durumlarını sağlar.
- `ConnectionSettingsScreen`: form durumunu, doğrulamayı ve bağlantı testi sonucunu sunar.
- `NovaClient` veya küçük bir bağlantı denetleyicisi: Gateway sağlık isteğini gerçekleştirir.

Akış: kullanıcı girdisi -> ViewModel doğrulaması -> mevcut client çağrısı -> reducer/state güncellemesi -> Compose ekranı. SSE event'leri mevcut tek yönlü state akışında kalır. UI hiçbir tokenı event veya task payload'ına eklemez.

## 11. Hata Yönetimi

- Gateway URL geçersizse ağ çağrısı yapılmadan alan altında açıklama gösterilir.
- Gateway erişilemiyorsa mevcut görev/prompt korunur ve `Tekrar dene` sunulur.
- Token eksik veya reddedilmişse `Kimlik doğrulama gerekli` mesajı ve Ayarlar eylemi gösterilir.
- SSE geçici olarak koparsa aktif görev kaybolmaz; yeniden bağlanma durumu zaman çizelgesinde tek bir anlaşılır bildirim olur.
- Görev başarısız veya iptal edilirse terminal kartı neden özeti ve yeni görev eylemi gösterir.
- Hata mesajlarında URL dışındaki gizli yapılandırma ve token yer almaz.

## 12. Erişilebilirlik ve Kullanılabilirlik Ölçütleri

- Etkileşimli hedefler en az 48 x 48 dp.
- İkon düğmelerinin bağlama göre Türkçe erişilebilirlik adları var.
- Birincil metin ve eylemler koyu zeminde okunabilir kontrasta sahip.
- Durum yalnız renkle anlatılmaz; metin ve ikon birlikte kullanılır.
- 1.3x yazı ölçeğinde temel eylemler taşmadan kullanılabilir.
- Alt navigasyon hiçbir desteklenen telefon genişliğinde yatay kaymaz.
- Klavye açıldığında görev ve sohbet gönderme alanı görünür kalır.

## 13. Test ve Doğrulama

### Otomatik testler

- Varsayılan hedefin Görevler olması ve üç hedef arasında geçiş.
- Bağlantı durumu ve URL doğrulama durumları.
- Görev boş, aktif, bekleyen onay, başarısız ve tamamlanmış Compose durumları.
- Duruma bağlı pause/resume/cancel eylemleri.
- Erişilebilirlik etiketleri ve temel test tag'leri.
- Mevcut Android birim, Compose instrumentation ve lint paketlerinin regresyonsuz geçmesi.

### Emülatör ve gerçek cihaz

- Temiz kurulum, launcher ikonu ve activity çözümleme.
- Görevler, Sohbet, Ses ve Ayarlar ekranlarında ekran görüntüsü ve UI ağacı denetimi.
- Klavye, büyük yazı, permission, hata ve yeniden deneme akışları.
- APK kurulumu ve PC Gateway/Ollama ile akışlı sohbet.
- Mobile Worker ile kontrollü `Android sürümünü bul` görevinin oluşturulması, çalışması ve tamamlanması.
- İlk yanıt gecikmesi, toplam görev süresi, event aralıkları, adım sayısı ve başarısız tekrarların kaydı.

## 14. Kapsam Dışı

- Launcher ikonunu yeniden tasarlamak.
- Yeni Gateway route'ları veya worker policy'si kurmak.
- Native Portal, telefon üzerinde LLM veya çevrimdışı inference eklemek.
- Görev geçmişi, analitik dashboard veya çok kullanıcılı hesap akışı eklemek.
- Play Store dağıtımı ve release signing yapılandırmak.

## 15. Tamamlanma Ölçütleri

- APK başarıyla derlenir ve bağlı Android hedefe kurulur.
- Üç birincil hedef sabit alt navigasyonda taşmadan kullanılabilir.
- Kullanıcı uygulamadan PC Gateway bağlantısını test edebilir.
- PC LLM ile en az bir akışlı sohbet yanıtı alınır.
- Mobile Worker ile kontrollü telefon görevi uçtan uca tamamlanır veya dış bağımlılık engeli kesin kanıtla raporlanır.
- Emülatör/cihaz kullanılabilirlik denetiminde kritik erişilebilirlik veya ana akışı durduran hata kalmaz.
