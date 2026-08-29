# NOVA — Gizlilik Politikası

**Son güncelleme:** 29 Ağustos 2026
**Uygulama:** NOVA (`com.nova.agent`)
**Sorumlu:** Salih Sürücü — salihsrc42@gmail.com

> Bu metin uygulamanın **gerçekte ne yaptığını** anlatır. Her madde koddan
> doğrulanmıştır; genel şablon cümlesi kullanılmamıştır. Yanlış bir madde
> görürsen bildir, düzeltilir.

---

## Kısaca

NOVA varsayılan olarak **telefonda** çalışır. Cihaza indirdiğin bir dil modeliyle
sohbet edersin ve istemlerin telefondan çıkmaz. İstersen kendi bilgisayarındaki
Gateway'e bağlanırsın; o durumda istemler **senin kurduğun** sunucuya gider.

NOVA'nın geliştiricisine ait bir sunucu **yoktur**. Uygulamada analitik, reklam
ve çökme raporlama SDK'sı **yoktur**. Verilerini toplamıyoruz, çünkü verilerinin
geldiği bir yer yok.

---

## Toplanan veriler

**Geliştirici olarak hiçbir veri toplamıyoruz.** Uygulamanın bize veri gönderdiği
hiçbir yol yok.

Aşağıdakiler **yalnız telefonunda**, uygulamanın özel depolama alanında tutulur:

| Veri | Neden | Nerede |
|---|---|---|
| Sohbet geçmişi (mesajların ve yanıtlar) | Sohbetlerine geri dönebilmen için | Cihaz |
| Notlar (çevrimdışı not aracı) | İstediğinde kaydettiğin notlar | Cihaz |
| Ayarlar (tema, model seçimi, kişilik metni) | Tercihlerinin korunması | Cihaz |
| Gateway adresi ve erişim anahtarı | Kendi PC'ne bağlanabilmen için | Cihaz |
| Hugging Face erişim belirteci (girdiysen) | Lisans onayı isteyen modelleri indirebilmen için | Cihaz |
| İndirilen model dosyaları | Çevrimdışı çalışabilmen için | Cihaz |
| İçerik bildirimleri | Sakıncalı bulduğun yanıtları işaretleyebilmen için | Cihaz |
| Model başarım ölçümleri (hız vb.) | Hangi modelin cihazına uyduğunu görmen için | Cihaz |

Bu verilerin tamamını **Ayarlar → Veri yönetimi → Yerel veriyi temizle** ile
silebilirsin. Uygulamayı kaldırmak da hepsini siler.

---

## Verinin cihaz dışına çıktığı durumlar

Üçü de **senin başlattığın** eylemlerdir; hiçbiri arka planda olmaz.

### 1. Kendi PC'ne bağlanmayı seçtiğinde

Yürütme politikasını **PC / Gateway** ya da **Hibrit** yaptığında (veya telefon
modeli hata verip sen açıkça onayladığında), istemin ve sohbet bağlamın
**senin kurduğun** Gateway sunucusuna gider. O sunucu senin bilgisayarında
çalışır; adresini sen girersin. Oradan bir bulut sağlayıcısına yönlendirilip
yönlendirilmediğini **senin Gateway yapılandırman** belirler — bu NOVA'nın
kontrolünde değildir ve o sağlayıcının gizlilik politikası geçerli olur.

**Çevrimdışı** politikasında bu yol tümüyle kapalıdır.

Hassas görünen içerikte (kart numarası, IBAN, kimlik, parola gibi) otomatik
devir **yapılmaz**; her seferinde açık onayın istenir.

### 2. Model indirirken

Model dosyaları **huggingface.co** üzerinden indirilir. Lisans onayı isteyen
("kapılı") modeller için girdiğin Hugging Face belirteci **yalnız bu adrese** ve
yalnız indirme sırasında gönderilir. Başka hiçbir yere gönderilmez.

### 3. Sesle konuşma kullandığında

Mikrofonu açtığında konuşmayı **Android'in kendi konuşma tanıma servisi** çözer.
Cihazında çevrimdışı Türkçe ses paketi kuruluysa işlem telefonda kalır; kurulu
değilse Android bunu üreticinin/Google'ın servisine gönderebilir. Bu, NOVA'nın
değil işletim sisteminin davranışıdır. Telefonda çalışan politikalarda NOVA
çevrimdışı tanımayı **tercih eder**.

Ses kaydı NOVA tarafından saklanmaz; yalnız çözülen metin sohbete girer.

---

## İzinler ve neden istendikleri

| İzin | Neden |
|---|---|
| `INTERNET` | Model indirme ve (seçtiysen) Gateway bağlantısı |
| `ACCESS_NETWORK_STATE` | Bağlantı var mı diye bakmak |
| `RECORD_AUDIO` | Yalnız sesle konuşma kullandığında; mikrofona basmadan açılmaz |
| `POST_NOTIFICATIONS` | Model indirme ilerlemesini göstermek |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | İndirme ekranı kapansa da sürsün diye |

Konum, rehber, SMS, çağrı kaydı, dosya erişimi izinleri **istenmez**.

---

## Yapay zekâ ile üretilen içerik

NOVA'nın yanıtları yapay zekâ tarafından üretilir ve **yanlış olabilir**. Tıbbi,
hukuki ve finansal konularda uzmanına danış.

Sakıncalı bir yanıt görürsen, yanıtın altındaki **bayrak** simgesiyle
bildirebilirsin. Bildirim telefonunda kaydedilir; bize göndermek istersen
Ayarlar → İçerik bildirimleri'nden paylaşırsın — **ne gönderileceğini önce
görürsün**. Gönderilmedikçe bildirim cihazdan çıkmaz.

---

## Çocuklar

NOVA 13 yaşın altındaki çocuklara yönelik değildir ve onlardan bilerek veri
toplamaz.

---

## Yedekleme

Android'in otomatik yedeklemesi devrededir ama **erişim anahtarların, Hugging
Face belirtecin ve model dosyaların yedeğe dâhil edilmez**. Sohbet geçmişin ve
notların yedeklenebilir; bu yedek senin Google hesabındadır, bizde değil.
Cihazdan cihaza aktarımda anahtarların korunur ki yeni telefonda baştan eşleme
yapmak zorunda kalma.

---

## Değişiklikler

Bu politika değişirse bu sayfadaki tarih güncellenir. Kayda değer bir değişiklik
olursa uygulama içinde bildirilir.

## İletişim

Soru ve talepler için: **salihsrc42@gmail.com**
