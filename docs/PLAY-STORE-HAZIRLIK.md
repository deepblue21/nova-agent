# Project Horus — Google Play Yayın Hazırlık Raporu

Tarih: 2026-08-09 · Dal: `codex/phase1-local-first` · Modül: `nova-android` (`com.nova.agent` v1.2 / versionCode 3)

## Özet

Kod olgun (97 Kotlin dosyası, 151 birim testi, temiz mimari, sızdırılmış sır yok). Ama **bugün Play Console'a yükleyemezsin** — imzalama yok, AAB üretimi yok, targetSdk süresi doluyor ve uygulama kutudan çıktığı gibi çalışmıyor.

**En kritik takvim: 31 Ağustos 2026.** O tarihten sonra Play, targetSdk 36'nın altındaki hiçbir yeni uygulamayı veya güncellemeyi kabul etmiyor. Elimizde ~3 hafta var.

---

## 🔴 BLOKER — bunlar olmadan yayın imkânsız

### B1. targetSdk 35 → 36 olmalı (SÜRE DOLUYOR)

`app/build.gradle.kts`: `compileSdk = 35`, `targetSdk = 35`.

31 Ağustos 2026'dan itibaren yeni uygulamalar ve güncellemeler **Android 16 (API 36)** hedeflemek zorunda. Uzatma başvurusu 1 Kasım 2026'ya kadar mümkün ama yeni uygulamalar için güvenilmez.

```kotlin
compileSdk = 36
targetSdk  = 36
```

Ayrıca `gradle.properties` içindeki `android.suppressUnsupportedCompileSdk=35` satırı kaldırılmalı. API 36 davranış değişikliklerinin (özellikle edge-to-edge zorunluluğu ve öngörülü geri hareketi) Compose ekranlarında test edilmesi gerekir.

### B2. Release imzalama yapılandırması yok

`buildTypes { release { ... } }` içinde `signingConfig` yok, projede `.jks`/`.keystore` dosyası yok. İmzasız bir çıktı Play'e yüklenemez.

Gerekli: upload keystore üret, `keystore.properties` (gitignore'lu) ile `build.gradle.kts`'e bağla, Play App Signing'e kaydol.

### B3. AAB üretimi hiç denenmemiş

`app/build/outputs/` altında yalnız `apk/` var. Play 2021'den beri **yalnızca AAB** kabul ediyor. `bundleRelease` bir kez bile çalıştırılmamış — R8, kaynak küçültme ve LiteRT native kütüphanelerinin paketlenmesi doğrulanmadı.

Ayrıca `proguard-rules.pro` dosyası `build.gradle.kts`'te referans veriliyor ama **dosya diskte yok**. `isMinifyEnabled = false` olduğu için şimdilik patlamıyor; açtığın anda OkHttp/LiteRT reflection kuralları gerekecek.

### B4. Gizlilik politikası URL'si yok

Uygulama `RECORD_AUDIO` istiyor, sohbet içeriği işliyor, HF token saklıyor. Play, veri toplayan her uygulama için barındırılan bir gizlilik politikası URL'si şart koşuyor. Repoda hiçbir gizlilik metni yok.

### B5. İlk açılış deneyimi kırık — "asgari işlevsellik" reddi riski

`SettingsStore.kt` varsayılanları:

```kotlin
val baseUrl: String = "http://10.0.2.2:8088/v1"   // emülatör loopback
val executionPolicy: String = "gateway_only"       // PC zorunlu
```

Play'den indiren bir kullanıcı uygulamayı açtığında: politika `gateway_only`, adres emülatör loopback'i, telefonda model yok. **Hiçbir şey çalışmıyor.** Kullanıcının önce Docker/WSL üzerinde bir gateway kurması gerekiyor — tüketici uygulaması için imkânsız.

Bu hem Play'in "Minimum Functionality" politikasına takılır hem de garanti 1 yıldız getirir.

Çözüm yönü: Play sürümünde varsayılan `local_first` (veya `local_only`) olmalı; ilk açılışta model indirme sihirbazı çıkmalı; PC bağlantısı "ileri düzey/opsiyonel" bir ayar olarak konumlanmalı.

### B6. Üretken YZ içerik bildirimi (AI-Generated Content policy)

Play, YZ ile metin üreten uygulamalarda **uygulama içinden, uygulamadan çıkmadan** rahatsız edici içeriği bildirme/işaretleme özelliği zorunlu kılıyor. Kodda böyle bir mekanizma yok.

Sohbet balonuna bir "Bildir" seçeneği + basit bir gönderim akışı gerekiyor.

### B7. Data Safety formu için envanter yok

Play Console'da beyan edilecek: ses kaydı (STT), sohbet metinleri, HF token, gateway API anahtarı, cihaz durumu (pil/RAM/depolama). Her biri için "toplanıyor mu / paylaşılıyor mı / şifreli mi / silinebilir mi" cevapları hazırlanmalı. Formdaki beyanla kodun uyuşmaması yayından kaldırma sebebi.

---

## 🟠 YÜKSEK — yayınlanır ama üretimde patlar

### Y1. Model indirmeleri arka planda ölüyor

`LocalLlmController(app, viewModelScope, ...)` — indirme `viewModelScope`'ta koşuyor. Kullanıcı uygulamadan çıktığında veya ekran kapandığında Android süreci öldürüyor; **0,5–8,6 GB'lık indirme kesiliyor**.

`.part` dosyası korunduğu için veri kaybı yok ama kullanıcı deneyimi kabul edilemez: "indir"e basıp telefonu cebine koyan herkes başarısız olacak.

Gerekli: `WorkManager` + `setForeground` (veya `DownloadManager`), `POST_NOTIFICATIONS` izni, ilerleme bildirimi. Şu an manifest'te bildirim izni bile yok.

### Y2. Yedekleme kuralları yok — token'lar Google'a gidiyor

Manifest'te `android:allowBackup="true"`, ama `dataExtractionRules`/`fullBackupContent` tanımlı değil. Yani `nova_settings` DataStore'u — **HF token'ı, gateway API anahtarı, persona, sohbet geçmişi dahil** — Google hesabına yedekleniyor.

En az: `<application ... android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="@xml/backup_rules">` ile sırların hariç tutulması.

### Y3. Sırlar düz metin DataStore'da

`hfToken` ve `token` `preferencesDataStore` içinde şifresiz. Root'lu cihazda veya yedekten okunabilir. `EncryptedSharedPreferences` / Keystore destekli saklama önerilir.

### Y4. Depolama koruması zayıf

Modeller `context.filesDir/models` altında — iç depolama. 8,6 GB'lık Qwen3-14B için indirme öncesi boş alan kontrolü ROADMAP'te "sıradaki" olarak duruyor ama `ModelDownloader`'da yok. Disk dolduğunda `IOException` ile jenerik "Bağlantı hatası" mesajı dönüyor — yanıltıcı.

### Y5. Android CI'da hiç derlenmiyor

`.github/workflows/ci.yml` yalnız web + gateway test ediyor. Android birim testleri ve `assembleDebug` CI'da yok; enstrümanlı iş yalnız elle tetikleniyor. Play'e giden artefaktın hiçbir otomatik kapısı yok.

### Y6. usesCleartextTraffic tüm uygulamada açık

Manifest yorumu doğru: CIDR aralığı network_security_config ile ifade edilemiyor ve gerçek kontrol `NetworkPolicy.kt`'de (testlerle kilitli — iyi iş). Ancak Play inceleme ekibi bunu görür; **inceleme notlarına açıklama yazılmalı**. Ek olarak `networkSecurityConfig` ile `cleartextTrafficPermitted=false` + kod tarafı istisna daha savunulabilir bir duruş verir.

---

## 🟡 ORTA — kalite ve mağaza sunumu

| # | Konu | Durum |
|---|------|-------|
| O1 | Yerelleştirme | `strings.xml`'de tek kayıt (`app_name`). Tüm arayüz metni Kotlin'e gömülü Türkçe. İngilizce mağaza listesi ile uygulama dili uyuşmayacak. |
| O2 | Çökme raporlama | Yok. Play Console'un vitals'ı yeterli değil; Crashlytics/Sentry olmadan alan hatalarını göremezsin. |
| O3 | İçerik derecelendirmesi | Anketi doldururken "kullanıcı üretimi içerik + YZ sohbet" işaretlenmeli. |
| O4 | Mağaza görselleri | Repoda ekran görüntüsü, feature graphic (1024×500), 512×512 ikon yok. `design/` klasörü var ama mağaza asset'i üretilmemiş. |
| O5 | Native ABI'ler | APK'da x86/x86_64 LiteRT kütüphaneleri var (~26 MB). AAB otomatik böleceği için sorun değil, sadece bilgi. |
| O6 | Sürüm numarası | versionCode 3 / versionName 1.2. İlk yayın için 1.0.0 / versionCode 1 daha temiz olabilir. |

### İyi durumda olanlar ✅

- **16 KB sayfa boyutu uyumu tam.** Tüm arm64 `.so` dosyaları (`libLiteRt.so`, `liblitertlm_jni.so`, `libLiteRtClGlAccelerator.so` dahil) 16384 hizalı. 1 Kasım 2025 zorunluluğu karşılanıyor.
- **minSdk 26** — makul, adaptif ikonlar çalışıyor.
- İndirme güvenliği: yalnız HTTPS, sabit commit revizyonu, SHA-256 doğrulama, atomik kurulum. Bu ortalamanın çok üstünde.
- Sırlar git'te yok, `secret-scan` CI'da koşuyor.
- Kod tabanında dürüstlük ilkesi tutarlı: desteklenmeyen özellik taklit edilmiyor.

---

## 📋 SÜREÇ — hesap ve test zorunlulukları

**S1. Kapalı test zorunluluğu.** 13 Kasım 2023'ten sonra açılmış **kişisel** geliştirici hesapları için: en az **12 test kullanıcısı**, **kesintisiz 14 gün** kapalı testte kalmalı; ancak ondan sonra üretime başvurabilirsin. Her tester'ın davet linkine kendi Google hesabıyla girip "Become a tester"a basması şart — listeye eklemek yetmiyor.

Kurumsal (tüzel kişilik kayıtlı) hesaplar bu şarttan muaf.

**S2. Geliştirici doğrulaması.** Kişisel hesapta kimlik + adres doğrulaması, kurumsal hesapta D-U-N-S numarası gerekiyor. Bu süreç günler sürebilir — bugün başlatılmalı.

**S3. Gerçek cihaz doğrulaması.** ROADMAP'te Faz 1'in tek eksiği olarak duruyor: fiziksel ARM64 cihazda uçtan uca tur hâlâ yapılmamış. Play'e gitmeden önce zorunlu.

---

## Önerilen sıra

**Hafta 1 — mağazaya girebilmek (B1, B2, B3, S2)**

1. Play Console hesabını aç ve doğrulamayı başlat (paralel yürüsün, seni bekletmesin).
2. `targetSdk` → 36, API 36 davranışlarını cihazda test et. (`compileSdk` 2026-08-09'da
   **37**'ye çıktı — androidx.core 1.19.0 gereği; targetSdk'dan bağımsızdır.)
3. Upload keystore üret, `signingConfigs.release` bağla, `keystore.properties`'i gitignore'a ekle.
4. `proguard-rules.pro` yaz, `isMinifyEnabled = true` ile `bundleRelease` çalıştır, çıkan AAB'yi cihaza `bundletool` ile kurup dumanı gör.

**Hafta 2 — reddedilmemek (B4, B5, B6, B7, Y1, Y2)**

5. İlk açılış akışını `local_first` varsayılanıyla yeniden kur; PC bağlantısını ileri düzey ayara indir.
6. İndirmeyi `WorkManager` + foreground notification'a taşı, `POST_NOTIFICATIONS` ekle, boş alan kontrolü koy.
7. Gizlilik politikasını yaz ve barındır (GitHub Pages yeter).
8. Sohbet mesajına "Bildir" akışı ekle.
9. `data_extraction_rules.xml` + `backup_rules.xml` ile token'ları yedekten çıkar.

**Hafta 3 — kapalı test (S1, S3, O4)**

10. Mağaza görsellerini üret, listeyi doldur, Data Safety formunu ver.
11. 12 tester topla, kapalı teste al, 14 günlük saati başlat.
12. Bu 14 gün içinde Y3/Y5/O1/O2 kalemlerini kapat.

---

## Açık stratejik soru

Play'e giden ürün **ne**? İki seçenek var ve mimari kararlar buna bağlı:

- **(A) Bağımsız cihaz-üstü YZ sohbeti.** PC/gateway tamamen opsiyonel bir "ileri düzey" özellik. Play için en temiz hikâye, en kolay onay.
- **(B) PC ajanı için mobil kumanda.** O zaman gateway kurulumu ürünün parçası ve tüketici mağazasında yeri tartışmalı; Play muhtemelen "asgari işlevsellik"ten reddeder.

Kod bugün (B) varsayılanıyla, (A) yeteneğiyle geliyor. Öneri: **(A)'yı varsayılan yap, (B)'yi gizli mücevher olarak tut.**
