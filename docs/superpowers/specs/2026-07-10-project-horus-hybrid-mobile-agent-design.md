# Project Horus Hibrit Mobil Agent Tasarımı

Tarih: 2026-07-10

Durum: Kullanıcı tarafından onaylanan mimari tasarım

İlk dağıtım hedefi: Android, kişisel kullanım ve sideload

## 1. Amaç

Project Horus, kullanıcının Android telefon üzerinden doğal dille verdiği görevleri planlayan, izin verilen telefon eylemlerini uygulayan ve sonucu doğrulayan local-first bir otonom agent olacaktır.

Sistem iki farklı hesaplama kaynağını birlikte kullanacaktır:

- Güçlü ve uzun süren işlemler için kullanıcının yerel GPU'lu bilgisayarı.
- Bilgisayara erişilemediğinde veya görev yeterince küçük olduğunda telefonun CPU, GPU veya NPU kaynakları.

"Tüm istekleri gerçekleştirme" hedefi teknik olarak sınırsız yetki anlamına gelmez. Ürün; Android izinleri, uygulama güvenlik sınırları, kullanıcının verdiği onaylar ve tanımlı araç yetenekleri içinde kalan görevleri gerçekleştirecektir.

## 2. Araştırma Sonuçları ve Kullanılacak Yaklaşımlar

### Nova Agent

`deepblue21/nova-agent`, OpenAI uyumlu gateway, Ollama yönlendirme, tool-calling, zamanlanmış görevler, SSE cevap akışı ve Kotlin/Compose Android istemcisi sağlar. Project Horus'un PC model gateway'i ve mevcut Android uygulama tabanı olarak kullanılacaktır.

### Local Agents

`deepblue21/local-agents`, Android ile PC companion arasında eşleştirme, kısa ömürlü token, kalıcı run/event geçmişi, SSE replay, duraklatma, iptal ve izole runner desenlerini sağlar. Project Horus bu kontrol düzlemi ve bağlantı dayanıklılığı desenlerini uyarlayacaktır.

### Mobilerun

`droidrun/mobilerun`, Android erişilebilirlik ağacı, ekran görüntüsü, Portal uygulaması ve atomik cihaz araçları üzerinden telefon kontrolü sağlar. Basit görevlerde FastAgent, karmaşık görevlerde Manager/Executor ayrımını kullanır. Project Horus telefon eylem katmanında bu mimariyi temel alacaktır.

### Ek Referanslar

- Google LiteRT-LM: Android üzerinde GPU/NPU destekli yerel model ve tool-calling.
- AndroidWorld: tekrarlanabilir Android agent görevleri ve başarı ölçümü.
- MobileWorld: gerçek cihaz, MCP ve kullanıcı etkileşimli agent değerlendirmesi.
- Mobile MCP: cihaz araçlarının MCP biçiminde sunulmasına yönelik referans sözleşme.

## 3. Mimari Karar

Seçilen yaklaşım hibrit local-first mimaridir. PC yalnızca model sunucusu olmayacak; ağır planlama, görsel anlama, uzun bağlam ve çok adımlı görevlerin ana hesaplama düğümü olacaktır. Telefon, kullanıcı arayüzü ve eylem uygulayıcısı olmanın yanında, sınırlı bir çevrimdışı model çalıştırabilecektir.

Üç yaklaşım değerlendirilmiştir:

1. PC merkezli Mobilerun sidecar: en hızlı prototip, fakat PC olmadan çalışmaz.
2. Hibrit PC ve telefon runtime'ı: daha fazla geliştirme gerektirir, ürün hedefini doğrudan karşılar.
3. Tamamen telefon üzerinde çalışma: mahremiyet ve taşınabilirlik sağlar, ancak model kapasitesi, batarya ve sıcaklık nedeniyle karmaşık görevlerde güvenilir değildir.

Project Horus ikinci yaklaşımı kullanacaktır. İlk MVP, risk azaltmak için PC merkezli başlayacak; ardından telefon fallback'i eklenecektir.

## 4. Sistem Bileşenleri

### Horus Android

Kotlin ve Jetpack Compose uygulamasıdır. Kullanıcı komutunu alır, görev durumunu gösterir, onay ister, canlı olayları gösterir ve yerel model seçeneğini barındırır.

Bağımlılıkları:

- Horus Gateway istemcisi.
- Android Keystore ile güvenli token saklama.
- Horus Portal Service.
- Yerel görev durumu için Room tabanlı Local Task Runtime.
- LiteRT-LM tabanlı Phone Inference Runtime.

Android uygulaması önce PC gateway erişilebilirliğini ve görev yeteneğini kontrol eder. PC kullanılamıyorsa Local Task Runtime yalnızca telefon modelinin ve yerel araçların desteklediği görevleri üstlenir. Yerel run ve event'ler Room içinde tutulur; bağlantı geri geldiğinde ham ekran görüntüsü veya gizli alanlar gönderilmeden görev özeti senkronize edilir.

### Horus Gateway

Nova Gateway üzerinde geliştirilecek API ve yönlendirme katmanıdır. Kimlik doğrulama, görev oluşturma, run kuyruğu, olay kalıcılığı, model yönlendirme ve PC tool-calling burada bulunur.

İlk mobil API sözleşmesi:

- `POST /v1/mobile/tasks`: görev oluşturur.
- `GET /v1/mobile/tasks/{taskId}`: görev durumunu döndürür.
- `GET /v1/mobile/tasks/{taskId}/events`: geçmişi replay ederek canlı SSE akışı açar.
- `POST /v1/mobile/tasks/{taskId}/commands`: pause, resume, cancel veya steer uygular.
- `POST /v1/mobile/tasks/{taskId}/confirmations/{confirmationId}`: riskli eylemi onaylar veya reddeder.
- `GET /v1/mobile/devices/{deviceId}/commands`: gateway veya PC worker tarafından üretilen cihaz eylemlerini replay destekli SSE ile telefona iletir.
- `POST /v1/mobile/devices/{deviceId}/observations`: cihaz gözlemini gönderir.
- `POST /v1/mobile/devices/{deviceId}/actions/{actionId}/result`: eylem sonucunu gönderir.

### Compute Policy ve Router

Hesaplama kararı iki aşamalıdır. Android Runtime Selector, görevin PC'ye gönderilip gönderilmeyeceğini belirler. Gateway Compute Router ise PC'ye gelen görev için kullanılacak modeli ve agent modunu seçer. İki katman aynı sürümlenmiş karar tablosunu ve yetenek tanımlarını kullanır.

Karar girdileri görev karmaşıklığı, görsel gereksinim, PC erişilebilirliği, model yeteneği, tahmini gecikme, telefon bataryası, termal durum ve kullanıcının gizlilik tercihidir.

Yönlendirme politikası:

- Deterministik cihaz eylemi: model çağırmadan telefonda çalıştır.
- Kısa ve düşük riskli offline görev: telefon modeli.
- Uzun planlama, çoklu uygulama veya görsel yorum: PC GPU.
- PC bağlantısı kesilirse: güvenliyse telefon fallback'i; değilse görev `waiting_for_compute` durumuna alınır.
- Çalışan bir görev içinde model değiştirmek yalnızca kaydedilmiş plan ve gözlem özeti üzerinden yapılır.

### Horus Mobile Worker

İlk MVP'de PC üzerinde çalışan ayrı bir süreçtir. Gateway'den görev veya alt hedef alır, Mobilerun Python API'sini çağırır ve ADB ile bağlı Android cihazda gözlem/eylem döngüsünü yürütür. Worker, provider anahtarlarını veya gateway admin yetkisini telefona aktarmaz. Native Horus Portal tamamlandığında aynı action sözleşmesi korunur ve worker isteğe bağlı geliştirme/test adaptörüne dönüşür.

### Mobile Agent Orchestrator

Görevi durum makinesi üzerinden yürütür. Basit görevlerde Fast Mode, karmaşık görevlerde Planner/Executor Mode kullanır. Agent serbest biçimli dokunma kodu üretemez; yalnızca kayıtlı ve doğrulanan araç şemalarından seçim yapar.

### Horus Portal Service

Android `AccessibilityService` tabanlı eylem katmanıdır. Erişilebilirlik ağacını normalize eder, ekran ve uygulama durumunu toplar, doğrulanmış atomik eylemleri uygular.

İlk araçlar:

- `get_state`
- `take_screenshot`
- `open_app`
- `tap_element`
- `tap_coordinates`
- `long_press`
- `type_text`
- `swipe`
- `system_back`
- `system_home`
- `wait_for_change`

Portal, ödeme veya kimlik doğrulama güvenlik ekranlarını aşmaya çalışmaz. Parola alanlarının içeriğini okumaz ve Android'in güvenli yüzey kısıtlarını atlatmaz.

Portal aktif bir görev sırasında kullanıcıya görünür foreground notification gösterir. AccessibilityService devre dışıysa veya Android servisi durdurursa görev yeni eylem üretmeden `waiting_for_device` durumuna geçer.

### Phone Inference Runtime

LiteRT-LM kullanarak desteklenen telefonlarda küçük, quantized ve tool-calling uyumlu model çalıştırır. Telefon modeli yalnızca sınırlı araç listesi, kısa bağlam ve adım bütçesiyle çalışır. Model yüklenemiyorsa uygulama çökmek yerine PC moduna geçer veya kullanıcıya görevin beklediğini bildirir.

## 5. Görev Akışı

1. Kullanıcı metin veya ses ile görev oluşturur.
2. Gateway görevi kaydeder ve `queued` olayı üretir.
3. Risk sınıflandırıcısı görev için izin verilen araçları ve onay kurallarını belirler.
4. Compute Router telefon veya PC modelini seçer.
5. Agent mevcut cihaz gözlemini ister.
6. Agent tek atomik eylem veya tamamlanma kararı üretir.
7. Policy Engine eylemi doğrular.
8. Riskli eylemse görev `waiting_for_confirmation` durumuna geçer.
9. Portal eylemi uygular ve yapılandırılmış sonucu gönderir.
10. Agent yeni gözlemle hedefi doğrular; tamamlanmadıysa döngü devam eder.
11. Başarı, hata, iptal veya adım sınırı sonunda terminal olay kaydedilir.

PC'siz akışta aynı adımlar Local Task Runtime içinde yürütülür. Gateway'e ait task kimliği yerine yerel UUID kullanılır; bağlantı geldiğinde görev yeniden çalıştırılmaz, yalnızca sonuç ve audit özeti senkronize edilir.

Görev durumları:

`queued -> routing -> observing -> planning -> executing -> verifying -> completed`

Yan durumlar:

- `waiting_for_confirmation`
- `waiting_for_device`
- `waiting_for_compute`
- `paused`
- `failed`
- `cancelled`

## 6. Risk ve Onay Modeli

Eylemler dört sınıfa ayrılır:

- R0 Okuma: ekranı okuma, uygulama durumunu alma. Otomatik.
- R1 Geri alınabilir: uygulama açma, gezinme, taslak yazma. Otomatik, kayıtlı.
- R2 Dış etki: mesaj gönderme, dosya yükleme, ayar değiştirme. Eylem öncesi kullanıcı onayı.
- R3 Kritik: ödeme, satın alma, veri silme, hesap veya izin değişikliği. Ayrıntılı onay ve gerektiğinde cihaz biyometrisi.

Agent, onaylanan eylemin hedefini veya parametrelerini değiştiremez. Değişiklik yeni bir onay gerektirir. Kullanıcı iptali tüm bekleyen eylemleri geçersiz kılar.

## 7. Güvenlik Sınırları

- Provider anahtarları telefona gönderilmez.
- PC ve telefon Tailscale veya eşdeğer özel ağ üzerinden haberleşir.
- Cihaz eşleştirme tek kullanımlık kod ve rotasyonlu refresh token kullanır.
- Telefon tokenları Android Keystore ile korunur.
- Her cihazın görevleri ve event geçmişi cihaz kimliğine göre ayrılır.
- Agent araçları varsayılan olarak kapalıdır; yetenekler açık allowlist ile etkinleştirilir.
- Eylem sonuçları idempotency anahtarı taşır; bağlantı tekrarında aynı dış etki yinelenmez.
- Ham chain-of-thought saklanmaz. Plan özeti, eylem, sonuç ve kullanıcı onayı saklanır.
- Ekran görüntüsü saklama varsayılan olarak kapalıdır; hata ayıklama için açıkça etkinleştirilir.
- Server-to-device action SSE akışı her yeniden bağlantıda access token'ı tekrar doğrular ve yalnızca eşleşmiş cihazın komutlarını döndürür.

## 8. Hata Yönetimi

- PC bağlantısı kesilirse çalışan eylem sonucu alınmadan yeni eylem üretilmez.
- Telefon offline fallback'i görev yeteneklerini karşılamıyorsa görev bekletilir.
- Aynı eylem iki kez başarısız olursa Planner yeni plan üretir.
- Üç ardışık başarısız eylemde görev durur ve kullanıcıya açıklanabilir hata sunulur.
- Uygulama veya servis yeniden başladığında terminal olmayan run'lar `paused` olarak açılır.
- Dış etkili eylemler otomatik yeniden oynatılmaz.
- Adım, süre ve token bütçeleri görev başında belirlenir.

## 9. Test Stratejisi

### Birim Testleri

- Compute Router karar tablosu.
- Risk sınıflandırma ve onay geçişleri.
- Eylem şeması doğrulama.
- Görev durum makinesi.
- Idempotency ve SSE replay.

### Entegrasyon Testleri

- Gateway ile sahte Portal arasında tam görev döngüsü.
- Ollama erişilebilir ve erişilemez durumları.
- Telefon modelinin yüklenmesi, timeout ve fallback davranışı.
- Ağ kopması, yeniden bağlanma ve iptal.

### Android Testleri

- Compose görev, onay ve canlı olay ekranları.
- AccessibilityService ile kontrollü test uygulamasında eylemler.
- Gerçek cihazda batarya, termal durum ve foreground service yaşam döngüsü.

### Agent Değerlendirmesi

- AndroidWorld görevlerinden seçilmiş tekrarlanabilir alt küme.
- MobileWorld gerçek cihaz görevleri.
- Her sürüm için başarı oranı, ortalama adım, süre, kullanıcı müdahalesi ve yanlış dış etki sayısı.

## 10. Uygulama Fazları

### Alt Proje A: Mobile Task Control Plane

Gateway task API'si, kalıcı event'ler, Android görev ekranı, pause/cancel ve confirmation. İlk uygulama planı yalnızca bu alt projeyi kapsayacaktır.

Çıkış ölçütü: Sahte Portal ile bir görev oluşturulabilir, SSE üzerinden izlenebilir, onaylanabilir ve yeniden bağlanınca geçmiş kaybolmaz.

### Alt Proje B: Mobilerun PC Worker Entegrasyonu

Gerçek Android cihaz Mobilerun worker üzerinden kontrol edilir.

Çıkış ölçütü: Uygulama açma, ekranda eleman bulma, metin yazma ve sonucun doğrulanması gerçek cihazda çalışır.

### Alt Proje C: Native Horus Portal

ADB gerektirmeyen Android AccessibilityService ve güvenli action executor geliştirilir.

Çıkış ölçütü: Aynı araç sözleşmesi PC worker yerine native Portal ile çalışır.

### Alt Proje D: Hybrid Compute Router

PC Ollama ve telefon LiteRT-LM arasında yetenek tabanlı yönlendirme eklenir.

Çıkış ölçütü: PC kapanınca desteklenen basit görev telefonda tamamlanır; desteklenmeyen görev güvenli biçimde bekler.

### Alt Proje E: Güvenlik, Dayanıklılık ve Benchmark

Eşleştirme, token rotasyonu, event replay, audit, AndroidWorld/MobileWorld testleri ve sürüm kapıları tamamlanır.

## 11. MVP Kapsamı

MVP şunları içerecektir:

- Android öncelikli tek kullanıcı.
- Sideload dağıtım.
- PC GPU üzerinde Ollama.
- Mobilerun üzerinden gerçek cihaz kontrolü.
- Metin komutu, canlı görev akışı, duraklatma, iptal ve riskli eylem onayı.
- Beş temel senaryo: uygulama açma, ayar bulma, form doldurma, mesaj taslağı hazırlama, ekrandan bilgi çıkarma.

MVP şunları içermeyecektir:

- Google Play dağıtımı.
- iOS desteği.
- Ödeme veya satın alma otomasyonu.
- Çok kullanıcılı bulut hizmeti.
- Telefon üzerinde büyük görsel model.
- Erişilebilirlik veya güvenlik kısıtlarını aşma.

## 12. Başarı Ölçütleri

- Kontrollü MVP senaryolarında en az yüzde 80 görev başarısı.
- R2 ve R3 eylemlerinde onaysız dış etki sayısı sıfır.
- Bağlantı kopup geri geldiğinde event ve görev durumunun kaybolmaması.
- Aynı dış etkili eylemin ağ tekrarı nedeniyle iki kez uygulanmaması.
- Kullanıcı iptalinin bir sonraki cihaz eyleminden önce etkili olması.
- Telefon fallback modunda desteklenmeyen görevin tahmin yürütmeden güvenli şekilde durması.

## 13. Dağıtım Kısıtı

İlk sürüm kişisel kullanım ve sideload hedefler. Genel amaçlı, otonom AccessibilityService kullanımı Google Play politikasıyla uyumlu değildir. Play Store hedefi ileride seçilirse ürün yetenekleri deterministik otomasyon veya doğrulanmış erişilebilirlik aracı kapsamına göre yeniden tasarlanmalıdır.
