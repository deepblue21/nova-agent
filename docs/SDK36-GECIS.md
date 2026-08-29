# SDK 36 + bağımlılık geçişi — ne değişti, nasıl doğrulanır

Tarih: 2026-08-09 · Modül: `nova-android`

Amaç: Play'in **31 Ağustos 2026** targetSdk 36 zorunluluğunu karşılamak ve 20 aydır donmuş bağımlılıkları güncele çekmek.

---

## Sürüm kararları ve gerekçeleri

Tüm sürümler resmi kaynaklardan doğrulandı (tahmin yok):

| Bağımlılık | Eski | Yeni | Kaynak |
|---|---|---|---|
| compileSdk / targetSdk | 35 | **36** | Play targetSdk politikası |
| compileSdk (yalnız) | 36 | **37** | androidx.core 1.19.0 `minCompileSdk=37` (2026-08-09) |
| AGP | 9.2.1 | 9.2.1 (değişmedi) | AGP 9.2 API 37'ye kadar destekliyor |
| Kotlin | 2.2.21 | 2.2.21 (değişmedi) | litertlm pini — aşağıdaki nota bak |
| Compose BOM | 2024.10.01 | **2026.06.01** | BOM eşleme tablosu → compose 1.11.4, material3 1.4.0 |
| androidx.core | 1.13.1 | **1.19.0** | AndroidX sürüm tablosu, 2026-07-01 |
| androidx.activity | 1.9.3 | **1.13.0** | 2026-03-11 |
| androidx.lifecycle | 2.8.6 | **2.11.0** | 2026-06-17 |
| androidx.datastore | 1.1.1 | **1.2.1** | 2026-07-29 |
| OkHttp | 4.12.0 | **5.4.0** | OkHttp changelog |
| kotlinx-coroutines | 1.8.1 | **1.11.0** | Kotlin/kotlinx.coroutines |
| LiteRT-LM | 0.13.1 | 0.13.1 (dokunulmadı) | model dosyası formatı bu sürüme bağlı |

**Kotlin neden yükseltilmedi?** `litertlm-android:0.13.1` Kotlin 2.3 metadata'sıyla derlendi ve mevcut pin bilinçli seçilmiş. Kotlin metadata bir minör sürüm ileri uyumlu olduğu için 2.2.21 hem litertlm'i hem Compose 1.11.4'ü okuyabiliyor. Aynı anda iki riskli değişiklik yapmamak için Kotlin sabit tutuldu — geri dönüş planı aşağıda.

---

## Dosya bazında değişiklikler

**Yeni: `nova-android/gradle/libs.versions.toml`**
Merkezi sürüm kataloğu. Bundan sonra sürüm yükseltmeleri tek dosyadan yapılır; `build.gradle.kts` dosyalarında çıplak sürüm dizesi kalmadı.

**`build.gradle.kts` (kök ve app)**
Katalog `alias(...)` / `libs.*` referanslarına geçti. `app` tarafında ayrıca:

- `compileSdk`/`targetSdk` katalogdan okunuyor — **artık farklı**: compileSdk 37, targetSdk 36
- `kotlinOptions {}` → `kotlin { compilerOptions { jvmTarget } }` (eski DSL Kotlin 2.2'de kullanımdan kaldırıldı, 2.3'te siliniyor)
- `isMinifyEnabled = true` + `isShrinkResources = true` — release artık R8'den geçiyor
- `packaging.jniLibs.useLegacyPackaging = false` — 16 KB hizalı, sıkıştırılmamış `.so`
- `bundle { abi/density enableSplit }` — AAB cihaz ABI'sine bölünüyor; arm64 telefona x86 LiteRT kütüphaneleri (~26 MB) inmiyor

**Yeni: `app/proguard-rules.pro`**
Dosya daha önce `build.gradle.kts`'te referans veriliyordu ama **diskte yoktu**. `isMinifyEnabled` açıldığı için artık zorunlu.

En kritik kural LiteRT için: `litertlm-android` AAR'ı consumer proguard kuralı içermiyor ve JNI üzerinden Kotlin sınıflarına geri çağrı yapıyor (`Conversation$JniMessageCallbackImpl`). R8 bunları yeniden adlandırsaydı **yalnız release derlemesinde** `NoSuchMethodError` alırdın — yani ancak mağazada fark ederdin.

**`gradle.properties`**
`android.suppressUnsupportedCompileSdk=35` kaldırıldı; compileSdk AGP 9.2'nin desteklediği
aralıkta (tavan API 37).

**Güncelleme (2026-08-09):** androidx.core 1.19.0 ve lifecycle 2.11.0 `minCompileSdk=37`
koyduğu için compileSdk **37**'ye çıkarıldı; derleme aksi halde `checkDebugAarMetadata`
adımında düşüyordu. `targetSdk` bilerek **36**'da bırakıldı — compileSdk yalnız hangi
API'lere karşı derlendiğimizi belirler, çalışma zamanı davranışını değiştirmez.
Derleme makinesinde SDK Platform 37 kurulu olmalı: `sdkmanager "platforms;android-37"`.

---

## API 36 davranış değişiklikleri (kodda karşılananlar)

**Kenardan kenara çizim zorunlu.** targetSdk 36'da devre dışı bırakma seçeneği kaldırıldı. `MainActivity.onCreate`'e `enableEdgeToEdge(...)` eklendi; `themes.xml`'den artık yok sayılan `statusBarColor` / `navigationBarColor` / `windowLightStatusBar` silindi (bırakılsalardı sessiz bir yanılsama yaratırlardı).

Inset tarafı zaten büyük ölçüde hazırdı: `NovaAppShell` üst çubukta `statusBarsPadding()`, Material3 `NavigationBar` kendi `navigationBars` inset'ini uyguluyor, sohbet girişinde `imePadding()` var. Yine de cihazda göz kontrolü şart.

**Öngörülü geri (predictive back)** hedef 36'da varsayılan açık; manifest'e `android:enableOnBackInvokedCallback="true"` açıkça yazıldı.

**Yönelim/yeniden boyutlanma kısıtları** 600dp+ ekranlarda uygulanmıyor. Uygulama zaten yönelim kilidi kullanmıyor, ek iş yok — ama tablette bir tur atılmalı.

---

## Doğrulama — Windows'ta, sırayla

JDK 17 ve Android SDK 36 platformu gerekiyor. Önce SDK bileşenini kur (Android Studio → SDK Manager → **Android 16 / API 36** ve **Build-Tools 36.x**).

```powershell
cd C:\Users\salih\Project_Horus\nova-android

# 1) Bağımlılıklar gerçekten çözülüyor mu (en hızlı geri bildirim)
.\gradlew.bat --refresh-dependencies :app:dependencies --configuration releaseRuntimeClasspath

# 2) Derleme + birim testler (151 test geçmeli)
.\gradlew.bat testDebugUnitTest

# 3) Lint
.\gradlew.bat lintDebug

# 4) Release AAB — R8 ilk kez devrede
.\gradlew.bat bundleRelease
```

Adım 4 imzasız bir AAB üretir (imzalama henüz bağlanmadı — bloker B2). Amaç şu an **R8'in patlamadığını görmek**.

### R8 çıktısını gerçek cihazda denemek

Bu adımı atlama. R8 hataları yalnız çalışma zamanında görünür ve en olası kurban LiteRT.

```powershell
.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release-unsigned.apk
```

Kurulum imzasız APK'da başarısız olursa geçici bir hata ayıklama imzasıyla dene. Cihazda kontrol listesi:

1. Bir model indir → SHA-256 doğrulaması geçiyor mu
2. Modeli aktif et → **yerel akışlı yanıt geliyor mu** (LiteRT keep kurallarının asıl testi bu)
3. Sistem çubukları: üst çubuk durum çubuğuyla çakışmıyor, alt gezinme çubuğu sistem çubuğunun altında kalmıyor
4. Klavye açıkken sohbet girişi görünür kalıyor
5. Geri hareketi: uzun basınca önceki ekranın önizlemesi geliyor

---

## Bir şey patlarsa

**`was compiled with a newer Kotlin compiler`**
Compose 1.11.4 metadata'sı Kotlin 2.2.21'e ağır geldi demektir. `libs.versions.toml` içinde tek satır:

```toml
kotlin = "2.3.21"
```

litertlm bundan etkilenmez — yeni derleyici eski metadata'yı her zaman okur. Endişe her zaman ters yöndeydi.

**Compose API kırılmaları (1.7 → 1.11, 20 ay)**
Beklenen yerler: `material-icons-extended` (BOM'da 1.7.8'de donduruldu, Google artık güncellemiyor), deneysel `@OptIn` işaretleri, `NavigationBar`/`Scaffold` inset varsayılanları. Derleme hatalarını bana ver, tek tek geçelim.

**OkHttp 5**
`Response.body` artık non-null. Koddaki `response.body ?: ...` ve `it.body?.string()` kullanımları **derlenir** — yalnız "gereksiz güvenli çağrı" uyarısı verir, davranış aynıdır. Uyarıları temizlemek isteğe bağlı, acele değil.

**R8 sonrası çalışma zamanı çökmesi**
`app/build/outputs/mapping/release/mapping.txt` ile izi çöz. Eksik kural bulursak `proguard-rules.pro`'ya gerekçesiyle ekleriz. Acil çıkış: `isMinifyEnabled = false` — Play bunu reddetmez, sadece paket büyür.

---

## Sırada ne var

Bu geçiş **B1** blokerini kapatıyor. Kalan blokerler `docs/PLAY-STORE-HAZIRLIK.md` içinde:

- **B2** imzalama yapılandırması (bir sonraki adım)
- **B4** gizlilik politikası
- **B5** ilk açılış deneyimi (`gateway_only` → `local_first`)
- **B6** YZ içerik bildirme akışı
- **B7** Data Safety envanteri
