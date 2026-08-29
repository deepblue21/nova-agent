# Project Horus — Google Play Yayın Planı (VPN'siz, mağazadan indirilebilir sürüm)

Tarih: 2026-08-24 · Dal: `codex/phase1-local-first` · Modül: `nova-android` (`com.nova.agent` v1.3 / versionCode 4)
Önceki rapor: [`PLAY-STORE-HAZIRLIK.md`](./PLAY-STORE-HAZIRLIK.md) (2026-08-09) — bu belge onun yerini alır.

---

## 0. Bugün alınan kararlar

| # | Karar | Gerekçe |
|---|-------|---------|
| K1 | **Hibrit ürün.** Varsayılan cihaz-üstü sohbet; opsiyonel bulut hesabı; opsiyonel LAN eşlemesi ile kendi PC'sine bağlanma. | Play'de "kutudan çıktığı gibi çalışır" şartını cihaz-üstü karşılar; PC/bulut ileri düzey katman olarak kalır. |
| K2 | **Kişisel geliştirici hesabı.** | 12 tester / kesintisiz 14 gün kapalı test şartı geçerli → takvim buna göre kuruldu. |
| K3 | **Telefon kontrolü (AccessibilityService / mobilerun) donduruldu.** | Google 28 Ocak 2026'dan beri Accessibility API ile "otonom eylem başlatma, planlama, yürütme"yi açıkça yasaklıyor. Play'de bu özellik reddedilir. |

> K3 için iyi haber: **bugünkü Android uygulamasında AccessibilityService yok.** Manifest'te izin yok, kodda tek satır iz yok (`grep -r Accessibility app/src/main/java` boş). Telefon kontrolü PC tarafındaki `mobile-worker` + ADB üzerinden yürüyor. Yani bu yasak **mevcut yayını engellemiyor**; yalnızca "cihaz-üstü native kontrol" kilometre taşını Play dışına taşıyor.

---

## 1. 9 Ağustos'tan bu yana ne değişti

Önceki raporun blokerlerinin **üçü kapandı**:

| Kalem | 9 Ağustos | 24 Ağustos | Kanıt |
|-------|-----------|------------|-------|
| B1 targetSdk 36 | 🔴 35'te | ✅ **kapandı** | `libs.versions.toml`: `targetSdk = "36"`, `compileSdk = "37"` (AGP 9.2.1 tavanı) |
| B3 `proguard-rules.pro` yok | 🔴 dosya diskte yok | ✅ **yazıldı** | 3636 baytlık dosya; LiteRT JNI + OkHttp kuralları gerekçeli |
| B3 R8 kapalı | 🔴 `isMinifyEnabled = false` | ✅ **açıldı** | `release { isMinifyEnabled = true; isShrinkResources = true }` |
| Y4 boş alan kontrolü | 🔴 yok | 🟡 **kısmen** | `LocalLlmController.kt:109,153` → `usableSpace` okunuyor; kullanıcıya gösterilen hata metni ayrıca doğrulanmalı |
| Y5 Android CI | 🔴 yok | ✅ **eklendi** | `ci.yml:110` `android-unit` işi → `./gradlew testDebugUnitTest` |
| B5 ilk açılış | 🔴 tamamen kırık | 🟡 **kısmen** | `FirstRunGuide.kt` yazıldı (kapatılabilir yönlendirme kartı) — ama **varsayılanlar hâlâ kırık** |
| AAB yapılandırması | — | ✅ **hazır** | `bundle { abi.enableSplit = true; density.enableSplit = true }` |
| Lint kapısı | — | ✅ **açık** | `lint { abortOnError = true; checkReleaseBuilds = true }` |

Kod tarafı beklediğimden ileride. Kalan blokerler artık **dar ve tanımlı**.

---

## 2. Güncel politika takvimi (resmî kaynaklardan, 2026-08-24)

| Tarih | Ne | Bizim durumumuz |
|-------|-----|-----------------|
| **28 Ocak 2026** (geçti) | Accessibility API ile otonom eylem yürütme **yasak** | ✅ Uygulamada Accessibility yok → K3 ile dondurduk |
| **31 Ağustos 2026** (7 gün) | Yeni uygulama ve güncellemeler için **targetSdk 36** zorunlu | ✅ Zaten 36. 1 Kasım'a kadar uzatma mümkün ama gerekmiyor |
| **30 Eylül 2026** (37 gün) | **Geliştirici doğrulaması** ilk yürürlük fazı — Play'de tüm paket adları için global | 🔴 Hesap yok. Kimlik + adres doğrulaması **bugün başlatılmalı** |
| 2027 | Doğrulamanın sideload/ADB'ye genişlemesi | ℹ️ GitHub release yolunu etkileyecek — K3'teki dondurulmuş özellik için not |

**Kapalı test şartı (K2 gereği bize uyuyor):** 13 Kasım 2023'ten sonra açılmış kişisel hesaplar için en az **12 tester**, **kesintisiz 14 gün** opt-in. Testere "eklemek" yetmiyor — her tester davet linkine kendi Google hesabıyla girip *Become a tester*'a basmalı. 14 günden önce çıkan tester sayılmıyor. Sonrasında üretim başvurusu ≤7 günde inceleniyor.

**Kurumsal hesap bu şarttan muaf** — K2'yi değiştirmek istersen tek kazanç bu 14 gün, bedeli D-U-N-S süreci (haftalar).

---

## 3. Kalan blokerler (yayın imkânsız)

### B2. Release imzalama yok — 🔴 açık

`app/build.gradle.kts:27` hâlâ `// TODO(Play): signingConfigs.release burada bağlanacak`. Projede `.jks`/`.keystore` yok, `keystore.properties` yok.

Gerekli: upload keystore üret → `keystore.properties` (gitignore'lu) → `signingConfigs.release` bağla → Play App Signing'e kaydol.
**Anahtar ve parola bende hiç görünmemeli**; keystore'u üreten komutu sen çalıştıracaksın.

### B3. `bundleRelease` bir kez bile koşmadı — 🔴 açık

`app/build/outputs/` altında yalnız `apk/` ve test sonuçları var, `bundle/` yok. R8 artık **açık** — yani ilk `bundleRelease` denemesi LiteRT JNI veya OkHttp reflection tarafında patlayabilir. Kurallar yazıldı ama **çalıştırılarak doğrulanmadı**. Bu, planın en yüksek belirsizlikli adımı.

### B4. Gizlilik politikası URL'si yok — 🔴 açık

`RECORD_AUDIO` isteniyor, sohbet metni işleniyor, HF token + gateway anahtarı saklanıyor. Barındırılmış bir URL şart. GitHub Pages yeterli.

### B5. Varsayılanlar hâlâ "PC zorunlu" — 🔴 açık (planın kalbi)

`SettingsStore.kt`:

```kotlin
val baseUrl: String = "http://10.0.2.2:8088/v1"   // emülatör loopback
val executionPolicy: String = "gateway_only"       // PC zorunlu
```

Play'den indiren kullanıcı için bu **hiçbir şey çalışmıyor** demek: politika PC istiyor, adres emülatör loopback'i, telefonda model yok. `FirstRunGuide` kartı yazıldı ama kart varsayılanı düzeltmiyor.

K1 (hibrit) gereği hedef: varsayılan `local_first`, `baseUrl` **boş**, PC/bulut bağlantısı Ayarlar'da opsiyonel. Kart zaten doğru yere (Modeller) yönlendiriyor.

### B6. YZ içeriği bildirme akışı yok — 🔴 açık

Play'in AI-Generated Content politikası, metin üreten uygulamalarda rahatsız edici içeriğin **uygulamadan çıkmadan** bildirilebilmesini istiyor. Sohbet balonuna "Bildir" + basit gönderim akışı gerekiyor. Ayrıca Play Console'da üretken YZ beyanı doldurulacak.

### B7. Data Safety envanteri yok — 🔴 açık

Beyan edilecek kalemler: ses kaydı (STT), sohbet metinleri, HF token, gateway/bulut API anahtarı, cihaz durumu (pil/RAM/depolama). Her biri için toplanıyor mu / paylaşılıyor mu / şifreli mi / silinebilir mi. **Hibrit karar (K1) bu formu ikiye ayırıyor**: cihaz-üstü modda hiçbir şey cihazdan çıkmıyor, bulut modunda sohbet metni sunucuya gidiyor. Form bunu "opsiyonel özellik" olarak ayırt edebiliyor — doğru doldurulması reddi önler.

---

## 4. Yüksek öncelikli (yayınlanır ama üretimde patlar)

| # | Konu | Durum |
|---|------|-------|
| Y1 | Model indirmesi `viewModelScope`'ta — uygulama arkaya atılınca 0,5–8,6 GB'lık indirme ölüyor. `WorkManager` + foreground notification + `POST_NOTIFICATIONS` gerekiyor. Kodda `WorkManager` izi yok. | 🔴 açık |
| Y2 | `allowBackup="true"` ama `res/xml/` **boş** — DataStore (HF token, gateway anahtarı, persona, sohbet geçmişi) Google hesabına yedekleniyor. `data_extraction_rules.xml` + `backup_rules.xml` şart. | 🔴 açık |
| Y3 | Sırlar düz metin DataStore'da; Keystore destekli saklama önerilir. | 🔴 açık (kapalı test sırasında kapatılabilir) |
| Y6 | `usesCleartextTraffic="true"` tüm uygulamada. Gerçek kontrol `NetworkPolicy.kt`'de ve testlerle kilitli — ama **inceleme notlarına açıklama yazılmalı**. Hibrit kararla birlikte bulut/PC HTTPS olduğu için cleartext yalnız LAN eşlemesi için gerekiyor. | 🟡 açıklama gerekiyor |
| O1 | `strings.xml`'de tek kayıt (`app_name`); tüm arayüz Kotlin'e gömülü Türkçe. Mağaza listesi TR + EN olacaksa i18n gerekiyor. | 🔴 açık |
| O4 | Mağaza görselleri yok: 512×512 ikon, 1024×500 feature graphic, en az 2 ekran görüntüsü. `design/` klasörü var, asset üretilmemiş. | 🔴 açık |
| S3 | Fiziksel ARM64 cihazda uçtan uca tur **hâlâ yapılmadı** (ROADMAP Faz 1'in tek eksiği). Emülatörde iroh de çalışmıyor. | 🔴 açık, Play'den önce zorunlu |

---

## 5. VPN'siz bağlantı: üç katman, hangisi Play'e giriyor

Hibrit karar (K1) bunu net bir katmanlamaya çeviriyor:

| Katman | Ne | Durum | Play sürümünde |
|--------|-----|-------|----------------|
| **1. Cihaz-üstü** | LiteRT-LM ile telefonda model; internet bile gerekmiyor | ✅ çalışıyor (Faz 1–9) | **Varsayılan.** B5 ile varsayılan hâline getirilecek |
| **2. LAN eşlemesi** | mDNS keşfi + 8 karakterlik kod / `horus://pair` bağlantısı. Kullanıcı IP ya da anahtar yazmıyor | ✅ arayüze bağlandı (Faz 10A) | **Opsiyonel ayar.** Kalan: cihazda multicast doğrulaması |
| **3. Uzak erişim** | Mobil veri / farklı ağdan PC'ye. İki seçenek: (a) **iroh** taşıma katmanı — CGNAT arkası, sunucu maliyeti yok, Faz 10B; (b) **bulut gateway** — VPS + Caddy TLS, `DEPLOY_VPS.md` hazır, aylık maliyet var | 🔴 ikisi de yapılmadı | **İlk yayında yok.** Yayından sonra faz |

**Tailscale (VPN) tam olarak burada devreden çıkıyor:** bugün 3. katmanın yerini tutuyor. Katman 1 ve 2 zaten VPN'siz. İlk yayın için 3. katman **gerekmiyor** — bu yüzden "VPN'siz Play sürümü" hedefi bugünkü kodla ulaşılabilir durumda.

> Uyarı — iroh: Android'de resmî destek yalnız aarch64/armv7. x86_64 emülatörde çalışmıyor, doğrulama fiziksel telefon istiyor (`BAGLANTI-ANALIZI.md` §10.3).

---

## 6. Sıralı iş planı

### Hafta 0 — bugün, paralel başlat (seni bekletmemesi gerekenler)

1. **Play Console hesabını aç** (25 USD tek seferlik) ve **kimlik + adres doğrulamasını başlat**. Girdiğin bilgiler ödeme profiliyle **birebir** aynı olmalı. 30 Eylül yürürlüğü var, süreç günler sürüyor → bugün başlamalı.
2. **Gizlilik politikası metnini yaz ve GitHub Pages'te yayınla** (kod beklemez).
3. **12 tester listesini toplamaya başla** — 14 günlük saat, ilk kapalı test yüklemesiyle başlıyor; isim toplamak en yavaş adım.

### Hafta 1 — mağazaya yüklenebilir bir AAB (B2, B3)

4. Upload keystore üret + `signingConfigs.release` bağla + `keystore.properties` gitignore'a.
5. `bundleRelease` çalıştır. **R8 açık olduğu için ilk denemede patlaması normal** — LiteRT JNI ve OkHttp kurallarını gerçek çıktıya göre düzelt.
6. Çıkan AAB'yi `bundletool` ile **fiziksel ARM64 telefona** kur ve uçtan uca tur at (S3). Bu adım aynı zamanda ROADMAP Faz 1'in kalan eksiğini kapatır.

### Hafta 2 — reddedilmemek (B5, B6, B7, B4, Y1, Y2)

7. **B5:** varsayılan `local_first`, `baseUrl` boş, PC/bulut Ayarlar'da opsiyonel. `FirstRunGuide` akışını bu varsayılanla yeniden test et.
8. **Y1:** indirmeyi `WorkManager` + foreground notification'a taşı, `POST_NOTIFICATIONS` ekle, boş alan kontrolünün kullanıcıya doğru mesajı verdiğini doğrula.
9. **Y2:** `data_extraction_rules.xml` + `backup_rules.xml` ile token'ları yedekten çıkar.
10. **B6:** sohbet mesajına "Bildir" akışı.
11. **B4/B7:** gizlilik politikası URL'sini bağla, Data Safety formunu hibrit ayrımıyla doldur, üretken YZ beyanını ver.

### Hafta 3 — kapalı test başlat (S1, O4, Y6)

12. Mağaza görsellerini üret (O4), listeyi TR + EN doldur.
13. İnceleme notlarına `usesCleartextTraffic` açıklamasını yaz (Y6): "cleartext yalnız RFC1918/loopback/link-local adreslerde, `NetworkPolicy.kt` ile kod tarafında kilitli; genel IP'ye şifresiz bağlantı engelli, birim testleriyle doğrulanmış."
14. 12 tester'ı kapalı teste al → **14 günlük saat başlar**.
15. Bu 14 gün içinde Y3, O1 ve varsa O2 (çökme raporlama) kalemlerini kapat.

### Hafta 5-6 — üretim

16. Üretim erişimi başvurusu (≤7 gün inceleme) → yayın.

**Gerçekçi yayın tarihi: Ekim ortası 2026.** Zinciri kısaltan tek şey kurumsal hesap (14 günü siler), uzatan şey doğrulama gecikmesi.

---

## 7. Hemen başlanacak ilk üç iş

| Sıra | İş | Kim yapar |
|------|----|-----------|
| 1 | Play Console hesabı + kimlik doğrulaması | **Sen** (kimlik belgesi gerekiyor) |
| 2 | Upload keystore üretimi | **Sen** çalıştırırsın, komutu ve `build.gradle.kts` bağlamasını ben yazarım (parola bende görünmez) |
| 3 | B5 — varsayılanları `local_first`'e çevirme + testlerin güncellenmesi | **Ben** |

`bundleRelease` ve `bundletool` adımları JDK 17 + Android SDK 37 gerektirdiği için **Windows PowerShell'den senin makinende** koşacak; buradaki Linux ortamında JDK 11 var ve Android SDK yok.

---

## 8. Kaynaklar

- [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878) — 31 Ağustos 2026 / API 36, 1 Kasım uzatması
- [Closed testing requirements for production access](https://support.google.com/googleplay/android-developer/answer/14151465) — 12 tester / kesintisiz 14 gün, 13 Kasım 2023 sonrası kişisel hesaplar
- [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) — "otonom olarak eylem başlatan, planlayan ve yürüten" kullanım yasak
- [Google Play Accessibility Services Policy Update (2026)](https://myappmonitor.com/blog/google-play-accessibility-services-policy-update) — 28 Ocak 2026 yürürlük tarihi
- [Understanding Google Play's AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/14094294)
- [Android Developer Verification 2026: Dates, Steps & Deadline](https://primetestlab.com/blog/android-developer-verification-2026) — 30 Eylül 2026 ilk yürürlük fazı
- Repo içi: [`PLAY-STORE-HAZIRLIK.md`](./PLAY-STORE-HAZIRLIK.md), [`BAGLANTI-ANALIZI.md`](./BAGLANTI-ANALIZI.md), [`SDK36-GECIS.md`](./SDK36-GECIS.md), [`../ROADMAP.md`](../ROADMAP.md)

---

## 9. Uygulama günlüğü

### 2026-08-24 — B2 ve B5 kodu yazıldı (derleme bekliyor)

**B5 — varsayılanlar cihaz-üstü oldu.** `AppSettings`:
`executionPolicy` `gateway_only` → **`local_first`**, `baseUrl` `http://10.0.2.2:8088/v1` → **boş**.
Eski kurulumlar etkilenmiyor: anahtar diskte olduğu için kendi değerini koruyor, sessiz taşıma yok.
Açılış sondası zaten belirtece bağlı (`shouldProbeOnStart`), bu yüzden boş adres ilk ekranda hata
üretmiyor — nötr "PC bağlantısı kurulmadı" durumu gösteriliyor.
Dokunulan dosyalar: `SettingsStore.kt`, `ExecutionPolicy.kt` (KDoc), `FirstRunGuide.kt` (KDoc),
`LocalLlmHelpersTest.kt`, `ConnectionStartupProbeTest.kt`.

**B2 — imzalama bağlandı.** `app/build.gradle.kts` artık `nova-android/keystore.properties`
dosyasını okuyor; dosya yoksa release **imzasız** kalıyor (debug anahtarıyla sessizce imzalamak
Play'e yanlış anahtarla yüklemekten daha kötü olurdu). `.gitignore`'a `keystore.properties`,
`*.jks`, `*.keystore` eklendi. Anahtar üretimi: `scripts/new-upload-keystore.ps1` — anahtar **depo
dışında** (`%USERPROFILE%\.horus-keys\`), parola SecureString ile soruluyor, komut satırına ve
loglara düşmüyor.

**Sıradaki doğrulama (Windows PowerShell'de koşacak):**

```powershell
cd nova-android
.\gradlew.bat testDebugUnitTest        # B5 testleri
cd ..
.\scripts\new-upload-keystore.ps1      # anahtar üret (parola sende kalır)
cd nova-android
.\gradlew.bat bundleRelease            # B3 — R8 açık, ilk denemede patlayabilir
```
