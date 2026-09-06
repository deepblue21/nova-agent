# Gerçek cihazda test planı

Emülatör testleri uygulamanın **çöküp çökmediğini** gösterdi. Gösteremediği
şey, denetim raporunda "dürüstlük notu" diye geçen üç madde — ve bunların
üçü de yalnızca gerçek bir telefonda kanıtlanabilir:

| Kapatılacak boşluk | Neden emülatörde olmadı |
|---|---|
| Android 13+ **pano önizlemesi** anahtarı maskeli mi gösteriyor | Önizleme baloncuğu sistem bileşeni; doğrulaması gözle |
| **İndirme bildirimi + iptal** akışı | Gerçek bir 0,5 GB indirme ve gerçek bir bildirim gölgesi gerekiyor |
| **İçerik bildirme kutusu** (Play B6 politika şartı) | Gerçek bir model yanıtı üretilmesi gerekiyor |

Ayrıca üç arka uç (cihazda / PC Gateway / bulut) hiç aynı oturumda uçtan uca
denenmedi.

---

## Kim ne yapıyor

Bu, keyfi bir iş bölümü değil — erişim seviyelerinin getirdiği sınır.

**Claude (Cowork, computer use):**
- APK'yı bağlı telefona kurar — Android Studio'da cihaz seçici → Run.
- **Logcat**'i okur ve kaydırır.
- Telefonu Android Studio'nun *Running Devices* aynasından sürer. Yazı yazmak
  gerektiğinde **telefonun kendi ekran klavyesine tıklar**; Android Studio
  "click" seviyesinde verildiği için IDE'ye tuş gönderemem, ama aynadaki
  klavye harflerine tıklamak sadece tıklamadır. Yavaş, ama yeterli.
- Ekran görüntülerini alır (bunlar aynı zamanda **Play mağaza görselleri**
  olur — gerçek cihaz kareleri emülatör karelerinden iyidir).
- Bulguları rapora yazar, düzeltmeleri yapar, testleri koşturur, commit'ler.

**Salih:**
- Telefonu USB'ye takar, **Geliştirici seçenekleri → USB hata ayıklama**'yı
  açar ve telefonda çıkan **RSA parmak izi onayını** verir. O kutu cihazın
  kendi ekranında ve aynalama başlamadan önce çıkıyor; ona ulaşamam.
- PC'de **Gateway**'i başlatır (B yolu için).
- **Bulut modeli anahtarını kendisi girer.** Anahtarları ve parolaları ben
  kullanmıyorum — bu sınırı aşmıyorum.
- İsterse **Tailscale**'i açar (B yolunun ikinci varyantı için).

**Terminal komutları:** Windows terminaline ve WSL'e yazı yazamam (terminaller
ve IDE'ler yalnız "click" seviyesinde veriliyor). `adb install` yerine Android
Studio'nun Run düğmesi kullanılacak. Eğer düz `adb`/`adb shell` gerekirse
(örn. `adb shell dumpsys`), o komutları **WSL'deki Claude** çalıştırabilir.

---

## Hazırlık

1. Telefon USB'de, USB hata ayıklama açık, RSA onayı verilmiş.
2. Android Studio'nun cihaz seçicisinde telefon görünüyor.
3. Kurulacak APK: `nova-android/app/build/outputs/apk/debug/app-debug.apk`
   (bugün üretildi; içindeki üç düzeltme dex'te doğrulandı).
4. **Temiz kurulum**: önceki sürüm varsa kaldırılmalı. Aksi hâlde eski
   `SharedPreferences` ve indirilmiş modeller ilk çalıştırma akışını gizler.

---

## A yolu — cihazda çalışan model (çevrimdışı)

| # | Adım | Beklenen | Kapattığı bulgu |
|---|---|---|---|
| A1 | Modeller → önerilen modelde "İndir" | **Bildirim izni istenir** — indirme başlamadan değil, tam bu anda | P3 |
| A2 | İzni **reddet**, indirmeye devam et | İndirme sürer, yalnız bildirim çıkmaz | P3 |
| A3 | Uygulamayı kaldır/yeniden kur, izni **kabul et** | Bildirim gölgesinde ilerleme + **İptal** düğmesi | P3 |
| A4 | Bildirimdeki GB metnini Modeller ekranıyla karşılaştır | İkisi de **virgül**: "0,4 GB / 0,5 GB" | P4 |
| A5 | Bildirimden **İptal** | Satır "indiriliyor"da ASILI KALMAZ; yeniden indirilebilir hâle döner | P2 |
| A6 | İndirmeyi tamamla, sohbet et | Yanıt gelir | — |
| A7 | **Uçak modu**, tekrar sohbet et | Yanıt yine gelir | — |
| A8 | Uzun bir yanıt üret, sesli okut | Ses **sonuna kadar** okur, ortada kesilmez | S1 |
| A9 | Cihaza uygun olmayan büyük bir model seç | Uygulama uyarır / önermez; OOM ile çökmez | — |

## B yolu — PC'deki Gateway (RTX 3070 üzerinde Ollama)

| # | Adım | Beklenen | Kapattığı bulgu |
|---|---|---|---|
| B1 | Aynı Wi-Fi'de, Eşleme → keşif | Gateway mDNS ile listede çıkar | mDNS |
| B2 | **"Yeniden tara"** düğmesine bas | Liste gerçekten yeniden taranır (düğme ölü değil) | "yeniden tara ölü değil" |
| B3 | Eşle, model listesini çek | PC modelleri (gemma3:4b, qwen3:14b) görünür | — |
| B4 | Ayarlar → Gateway anahtarını **kopyala** | **Sistem önizleme baloncuğunda anahtar MASKELİ** — düz metin görünürse düzeltme çalışmıyor | **P1** |
| B5 | Sohbet ederken **Wi-Fi'yi kapat-aç** | Akış yeniden bağlanır, sohbet kaldığı yerden sürer | T3 |
| B6 | PC'de **Gateway sürecini öldür** | Sınırlı sayıda deneme → **açık hata mesajı**; sessizce asılı kalmaz | T3 |
| B7 | Kart numarası / IBAN görünümlü bir metin yaz | **Otomatik aktarım yok**, onay istenir | gizlilik |
| B8 | Üst üste iki onay gerektiren istek | İkinci onay kuyruğa girer, birincisini ezmez | T4 |
| B9 | **Tailscale** ile LAN dışından, Gelişmiş mod → adresi elle gir | Bağlanır; `v1` yol koruması geçerli adresi reddetmez | `GatewayDiscovery` dikişi |

## C yolu — bulut modeli

| # | Adım | Beklenen |
|---|---|---|
| C1 | Anahtar tanımlı değilken bulut modeli | Pasif, sebebi yazılı: "Anahtar tanımlı değil" |
| C2 | Salih anahtarı girer | Model seçilebilir hâle gelir |
| C3 | Sohbet | Yanıt akar; düşünce blokları doğru ayrışır |

## Yollardan bağımsız

| # | Adım | Beklenen | Kapattığı bulgu |
|---|---|---|---|
| X1 | Bir yapay zekâ yanıtındaki **bayrak** simgesine bas | Kutu açılır, **bildirilecek alıntı görünür** | **B6 (Play şartı)** |
| X2 | Bildir | **Uygulamadan çıkmadan** tamamlanır, kısa onay mesajı çıkar | **B6** |
| X3 | Ayarlar → İçerik bildirimleri | Sayaç arttı; paylaş ve sil çalışıyor | B6 |
| X4 | Android 13+ **temalı simgeler** açık | Monokrom simge düzgün görünür | — |
| X5 | Sohbet geçmişi: ara, sil | Silme onay ister | — |
| X6 | Ayarlar → **tüm verileri sil** | Geçmiş, modeller ve gizli değerler beklendiği gibi | — |

---

## Kayıt

- **Logcat**: her yol için ayrı ayrı okunur. Üretim kodunda `Log` çağrısı
  yasak (guard testi bunu koruyor), yani NOVA'dan gelen gürültü olmayacak —
  görülen her istisna gerçek bir bulgudur.
- **Ekran görüntüleri**: Sohbet (dolu yanıt) · Modeller (indirme kartı) ·
  Kontrol · Ayarlar (gizlilik). Play en az 2 istiyor; bu 4 kare hem testin
  kanıtı hem mağaza görseli olur.

## Bulgular ne olacak

Her bulgu `docs/UYGULAMA-DENETIMI.md`'ye yeni bir tur olarak yazılır —
**yanlış alarmlar da dâhil**, önceki turlarda olduğu gibi. Düzeltme → birim
testi (mümkünse guard testi) → 397+ test yeşil → commit. Sonra yeni APK ve
tekrar cihazda doğrulama.

> **Play açısından kritik olan:** X1–X3. Uygulama içi yapay zekâ içerik
> bildirimi Play'in yazılı şartı ve şu an cihazda **hiç denenmedi**. Bu üç
> satır geçmeden mağaza gönderimi yapılmamalı.
