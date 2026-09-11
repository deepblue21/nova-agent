# NOVA Android — kod denetimi (2026-08-27)

İki bağımsız denetim, 11.496 satır ana kaynak (58 dosya). Her bulgu okunan koddaki
somut satırlara dayanıyor; doğrulanamayanlar **şüpheli** olarak işaretli.

**Cevap: hayır, uygulama kusursuz değil.** En ciddi üç kusur, mağazadan indiren
bir kullanıcının ilk 5 dakikasında karşısına çıkıyor.

---

## Bugün düzeltilenler (3)

Üçü de dün `baseUrl` varsayılanını boşaltmamın (B5) açtığı ya da büyüttüğü kusurlar.

### ✔ D1. Çökme — "Görevi başlat" uygulamayı düşürüyordu

`MobileTaskClient.request()` adres geçersizse `IllegalArgumentException` fırlatıyor.
Zincir Compose `onClick`'inden **senkron** geliyor ve hiçbir yerde yakalanmıyordu.

Senaryo: temiz kurulum (`baseUrl = ""`) → eşlen → Görevler → "Görevi başlat" → **çökme.**
Eski varsayılan (`http://10.0.2.2:8088/v1`) ayrıştırılabildiği için bu yol daha önce
hiç açılmamıştı.

**Düzeltme:** `createTask`, `command`, `resolveConfirmation` çağrıları try/catch ile
sarıldı; hata artık çökme yerine kullanıcının gördüğü normal hata yoluna gidiyor.

> Kalan: `streamEvents` aynı istisnayı fırlatabiliyor ama yalnız başarılı bir görev
> oluşturmadan **sonra** çağrılıyor, yani adres o noktada zaten geçerli. Sarmadım.

### ✔ D2. Ayarlar panelini kapatmak sahte hata üretiyordu

`onRestoreAppliedConnection` panel her kapanışında koşulsuz `testConnection()`
çağırıyordu. Açılıştaki koruma (`shouldProbeOnStart`) bu yolu kapsamıyordu.

Senaryo: temiz kurulum, kullanıcı sırf tema seçmek için Ayarlar'ı açıp kapatır →
kalıcı **"Gateway adresi geçersiz"** → Basit modda düzeltebileceği alan ekranda yok.

**Düzeltme:** `NovaViewModel.refreshConnectionState()` eklendi; açılış kuralının
aynısını uyguluyor. Kullanıcının kendi bastığı "Bağlantıyı test et" bunun dışında —
orada geçersiz adresi söylemek doğru.

### ✔ D3. Eşleme sonrası Görevler ekranı eski adreste kalıyordu

`onUpdateTaskConnection`'ı yalnız elle "Kaydet" yolu çağırıyordu; **eşleme yolu
çağırmıyordu.** Bağlantı kartı "PC hazır" derken görev istekleri boş adrese gidiyordu
(D1'in tetikleyicisi de buydu).

**Düzeltme:** Görevler bağlantısı artık ayarları **izliyor** (`LaunchedEffect`), kimse
elle itmiyor. Aynı hatanın gelecekte açılacak her yeni bağlantı yolunda tekrarlanmasını
da önlüyor.

---

## Açık — kritik (2)

### K1. İndirilen model AKTİF OLMUYOR → yeni kullanıcı için gerçek çıkmaz

`NovaViewModel.kt:302`, `SettingsStore.kt:53`, `ModelRecommender.kt:36-53`

`recommended` cihaz RAM'ine göre en uygun modeli seçer, ama `settings.localModelId`
sabit `"qwen3-0.6b-int4"` kalır ve indirme bitince `setLocalModel`'i çağıran **hiçbir
yer yoktur** (tek çağıran `NovaApp.kt:154`, yani elle dokunuş).

Senaryo: 8 GB RAM'li telefon → kart "önerilen: Gemma 4 E4B" der → "Önerileni indir" →
3,4 GB iner ve doğrulanır → sohbete yazılır → **"Telefonda kurulu model yok. Modeller
sekmesinden bir model indirin."** Aynı anda ilk açılış kartı da kaybolur
(`anyInstalled()=true`), yani yönlendirme tam gerektiği anda yok olur.

**Bu, `local_first` varsayılanının ana yolu.** Play sürümünde ilk deneyim bu.

### K2. İndirme arka planda ölüyor, bildirim yok

`NovaViewModel.kt:88` (`viewModelScope`) → `onCleared()` → `shutdown()` → indirme iptal.
Manifest'te Service yok, WorkManager yok, `POST_NOTIFICATIONS` yok.

Senaryo: 8,6 GB indirme başlar, kullanıcı uygulamayı kapatır → sessizce iptal.
`.part` korunduğu için veri kaybı yok ama "indir"e basıp telefonu cebine koyan
**herkes** başarısız olur.

---

## Açık — yüksek (6)

| # | Kusur | Sonuç |
|---|-------|-------|
| Y1 | `NetworkPolicy` yalnız eşlemede çağrılıyor (`PairingClient.kt:40`); sohbet/test/görev yolları kontrolsüz | Gelişmiş modda genel bir IP'ye `Bearer nv_…` **şifresiz** gider. Manifest yorumundaki güvence tutmuyor; testler yeşil kalır çünkü saf fonksiyonu test ediyorlar, çağrıldığını değil |
| Y2 | Doğrulaması başarısız model silinmiyor, `isInstalled` yine true (`LocalModelStore.kt:48,61`) | Bozuk `.litertlm` native motora verilir; SIGSEGV `catch(Throwable)` ile yakalanamaz. Üstelik satırda "yeniden indir" düğmesi yok |
| Y3 | Tam boyutlu `.part` → kalıcı HTTP 416 (`ModelDownloader.kt:68`, `>` yerine `>=`) | %100'de duraklatan kullanıcı bir daha sürdüremez; tek çıkış 8,6 GB'ı baştan indirmek |
| Y4 | Hedef dosya varsa SHA-256 doğrulaması tamamen atlanıyor (`ModelDownloader.kt:70`) | Arayüz "başarılı" der, satır sonsuza dek "Doğrulanmadı" kalır |
| Y5 | Disk dolunca "Bağlantı hatası ... sürdürülebilir" deniyor (`ModelDownloader.kt:149`) | Hem etiket hem öneri yanlış; yer yokken sürdürülemez |
| Y6 | `iptal` `ensureLoaded` sonrası dinlenmiyor; tek motorda iki eşzamanlı üretim (`LocalLlmController.kt:262-345`) | "Dur" → yeni istem → iki coroutine aynı `Engine`'e girer, biri `unload()` çağırırken diğeri üretir → native çökme *(şüpheli, cihazda üretilmedi)* |

---

## Açık — orta (7)

- **mDNS sızıntısı:** `unregisterServiceInfoCallback` hiç çağrılmıyor (`NsdGatewayDiscovery.kt:104`); panel her açılışta kayıt birikir, "arka planda sürmemeli" sözleşmesi tutmuyor.
- **Kaybolan PC listeden düşmüyor:** `onServiceLost` `displayName` ile eşleştiriyor ama o TXT `name`'den geliyor (`NsdGatewayDiscovery.kt:69`); kapanan PC listede kalır, kullanıcı kodu boşuna yakar.
- **API 34 öncesi ikinci PC hiç çözümlenmiyor:** `resolveService` asenkron, `FAILURE_ALREADY_ACTIVE` yutuluyor (`NsdGatewayDiscovery.kt:121`).
- **"Yeniden tara" butonu ölü:** `startDiscovery()` ilk satırda erken dönüyor, job daima aktif (`PairingController.kt:51`).
- **Özel TXT `path` ile eşleme "başarılı" der ama kaydetmez:** `GatewayDiscovery` `/api/v1`'i destekler, `canonicalBaseUrl` reddeder — iki bileşen ayrı ayrı testli, dikiş testsiz.
- **İzin kartı yanlış üç mesaj gösteriyor:** "Telefon modeli yanıt veremedi" (model hiç yok), tek eylem "PC'ye gönder", o da yapılandırılmamış gateway'de patlar (`ChatScreen.kt:204`).
- **Yedekleme kuralları yok:** `allowBackup="true"`, `dataExtractionRules` yok → `hfToken` ve GB'larca model Auto Backup kapsamında; 25 MB kotası yüzünden yedekleme büsbütün başarısız olur.

---

## Test kapsamındaki gerçek boşluklar

1. **Boş `baseUrl` ile hiçbir UI testi yok.** Tüm `NovaAppSettingsSyncTest` testleri Gelişmiş mod + dolu adres kullanıyor. Yeni varsayılanın gerçek hâli (Basit mod + boş adres) hiç test edilmiyor — D2 bu yüzden fark edilmemişti.
2. **Eşleme → görev bağlantısı dikişi testsiz** (D3).
3. **`NetworkPolicy`'nin çağrıldığını doğrulayan test yok** (Y1) — 12 test saf fonksiyonu kapsıyor, çağrı noktalarını değil.
4. **`PairingController` yaşam döngüsü testsiz:** erken dönüş, `stopDiscovery` sonrası state, iptal edilen çağrı.
5. **`saveConnection`'ın sessizce persist etmeyen dalı testsiz.**

---

## Önerilen sıra

1. **K1** — tek satırlık düzeltme değil ama en yüksek getirili: indirme bitince aktif model ayarlansın. Play'e giden ana yolun çıkmazı bu.
2. **Y3 + Y4 + Y5** — indirme dayanıklılığı; üçü de `ModelDownloader.kt`'de, birlikte kapanır.
3. **K2** — `WorkManager` + foreground bildirim. En çok iş, ama Play öncesi zorunlu.
4. **Y1** — `NetworkPolicy`'yi gerçek çağrı noktalarına bağla + çağrıldığını doğrulayan test.
5. **Y2 + Y6** — bozuk model ve eşzamanlı üretim; ikisi de native çökme sınıfı.
6. Orta grup, keşif/eşleme kalemleri eve dönünce LAN testiyle birlikte.

Not: bugünkü üç düzeltme **derlenmedi ve test edilmedi** — `CALISTIR-derleme.bat` hâlâ
çalıştırılmayı bekliyor. Onaylanmadan "düzeldi" saymayın.

---

# İkinci tur — kalan yarı (2026-08-27)

İlk tur bağlantı/eşleme ve model yolunu kapsıyordu. Bu tur sohbet, ses, geçmiş,
araçlar, görev durum makinesi ve uygulama kabuğunu kapsıyor. **23 yeni bulgu.**

> Not: ikinci turdaki denetçilerden biri, düzeltmelerden ÖNCE alınmış bir kaynak
> kopyasını okudu ve D1/D3'ü "hâlâ açık" diye raporladı. Cihazdaki dosyalarda
> üç düzeltme de yerinde — doğrulandı. O bulguları buradan çıkardım.

## KRİTİK (6)

### G1. Hassas istem, izin sorulmadan buluta gidiyor
`NovaViewModel.kt:488-497` (`autoHandoffAfterLocalError`)

`decideHybrid` hassas istemi bilerek telefonda tutuyor. Ama yerel motor hata verince
`autoHandoffAfterLocalError` **`PrivacyClassifier`'ı yeniden sormadan** aynı istemi
gateway'e yolluyor ve izin kartını hiç göstermiyor.

Senaryo: HYBRID + oto-devir açık → "IBAN'ım TR33 0006…" → gizlilik nedeniyle telefonda
kalıyor → model zaman aşımına düşüyor → istem sessizce gateway'e, oradan seçili model
bir bulut sağlayıcısıysa **buluta** gidiyor. `PrivacyClassifier`'daki "override yalnız
otomatik devri engeller" sözü bu yolda tutmuyor.

### G2. Sınıflandırıcı son mesaja bakıyor, gönderilen tüm geçmiş
`NovaViewModel.kt:468-478` + `NovaClient.kt:61`

`privacySensitive` yalnız **son** kullanıcı mesajından hesaplanıyor, ama istek
konuşmanın tamamını taşıyor.

Senaryo: 1. tur kart numarası → telefonda kalıyor (doğru). 3. tur uzun ve masum bir
istem → uzunluk kuralıyla gateway'e → **kart numarası içeren 1. tur da aynı istekle
dışarı çıkıyor.**

### T1. Sayısal olmayan olay kimliği uygulamayı çökertiyor
`MobileTaskReducer.kt:65` (`BigInteger(it.id)`) ↔ `MobileTaskClient.kt:178`

Ayrıştırıcı kimliği serbest string kabul ediyor (test bunu bilerek doğruluyor), reducer
ondalık tamsayı şart koşuyor. İki katman arasında **doğrudan sözleşme çelişkisi**.

Senaryo: gateway ULID/UUID üretirse (`id: evt_01H8…`) ya da JSON'da `"id":1.0` gelirse
ilk olayda ana thread'de `NumberFormatException` → çökme.

### T2. Terminal görev + bekleyen onay = uygulamayı zorla kapatmak
`MobileTaskReducer.kt:60-64`, `MobileTaskScreen.kt:97-122`

`task.state{failed|cancelled}` bekleyen onayı temizlemiyor.

Senaryo: onay istendi, kullanıcı karar vermeden worker zaman aşımına düşüp `failed`
yayınladı → karartma katmanı tüm dokunuşları yutuyor, alttaki içerik erişilemez,
"Yeni görev" ulaşılamaz, Onayla/Reddet 404 dönüyor, görev terminal olduğu için
kurtarıcı SSE olayı da gelemez. **Çıkış yok.**

### V1. Sohbet geçmişi atomik yazılmıyor; bozulunca sessizce sıfırlanıyor
`ConversationStore.kt:66-71`, `:90-103`

Tüm sohbetler tek dosyada, `writeText` önce truncate ediyor. Yazma sırasında süreç
ölürse yarım JSON kalıyor → `parseList` `catch` ile **boş liste** dönüyor → ilk kayıt
dosyayı tek sohbetle eziyor. Geçici dosya + rename yok, yedek yok, uyarı yok.

Senaryo: 80 sohbet kayıtlı → yazma sırasında Android süreci öldürüyor → uygulama
açılınca "Henüz kayıtlı sohbet yok."

### V2. Hata ve "Durdur" sonrası sohbet hiç kaydedilmiyor
`NovaViewModel.kt:646-651`, `:570-589`, `:413-422`

`saveCurrent()` yalnız 4 yerde çağrılıyor; hata yolunda ve Durdur'da yok. Hiçbir yaşam
döngüsü kancası da kaydetmiyor (kaynakta tek `LifecycleEventObserver` yok).

Senaryo: uzun soru, akış ortasında ağ düşüyor → kullanıcı uygulamayı kapatıyor → soru
da kısmi yanıt da geçmişte yok.

## YÜKSEK (7)

| # | Kusur | Sonuç |
|---|-------|-------|
| T3 | SSE `onClosed`'da yeniden bağlanma yok (`MobileTaskViewModel.kt:443`) | Proxy 60 sn'de akışı temiz kapatıyor → ekran "Plan hazırlanıyor"da sonsuza kadar donuyor, sonradan gelen **onay isteği hiç görünmüyor**, görev PC'de asılı kalıyor |
| T4 | Onay paneli parmağın altında değişebiliyor (`MobileTaskReducer.kt:60`) | A'nın özetini okuyup dokunurken B düşerse **B onaylanmış oluyor** — riskli eylem yanlışlıkla onaylanabilir |
| S1 | TTS 4000 karakter sınırı kontrol edilmiyor (`SpeechManager.kt:99`) | 5000 karakterlik yanıtta `speak` ERROR döner, `onDone` hiç gelmez → orb sonsuza dek "Konuşuyorum", ses yok |
| L1 | `onCleared` → `shutdown()` → `viewModelScope.launch` **hiç çalışmıyor** (scope zaten kapalı) | LiteRT motoru, GPU bağlamı ve GB'larca mmap'li model süreç ölene kadar bellekte |
| J1 | Android `org.json` `optString(name, fallback)` JSON null'da **`"null"` dizesi** döndürür | Geçmişten yüklenen mesajın altında "→ null"; `reasoning_content:null` gönderen sağlayıcıda düşünme panelinde "nullnullnull…" |
| M1 | Kullanıcı "Durdur"a basınca "⚠️ Gateway hatası (200)" yazılıyor (`NovaClient.kt:106`) | Kendi iptali sunucu hatası gibi gösteriliyor (yerel yolda bastırılmış, gateway yolunda değil) |
| E1 | Kapanmamış `<think>` bloğu içeriğe düşüp dışa aktarmaya sızıyor; gateway yolunda `ThinkingText.split` **hiç çağrılmıyor** | "Düşünme paylaşımdan çıkarılır" vaadi tutmuyor; aynı model Ollama üzerinden kullanılınca ham `<think>` balonda görünüyor |

## ORTA (10)

- Statüsüz olay, HTTP ile doğrulanmış durumu geri alıyor → "Duraklat" sessizce EXECUTING'e dönüyor (`MobileTaskReducer.kt:66`).
- Yapışkan hata mesajı + sınırsız yeniden deneme; `clearError()` **ölü kod**, hiçbir yerden çağrılmıyor.
- SSE ile terminale geçen görevde akış kapanmıyor → boşta bağlantı taşınıyor.
- API anahtarı panoya `EXTRA_IS_SENSITIVE` olmadan kopyalanıyor → Android 13+ önizleme baloncuğu anahtarı düz metin gösteriyor, maskeleme anlamsızlaşıyor.
- Geçmiş aramasında yarış: eski sorgunun sonucu yenisini eziyor (bağlantı sondaları için yazılan koruma deseninin karşılığı yok).
- Geçmişte silme onaysız ve geri alınamaz; çöp kutusu ikonu paylaş ikonunun bitişiğinde.
- `Calculator` taşmada "sıfıra bölme olabilir" diyor (`9^999`); derin özyinelemede `StackOverflowError` bir `Error` olduğu için "araç hataları uygulamayı düşüremez" garantisi kapsamıyor *(şüpheli)*.
- `modelsCall` `onCleared`'da iptal edilmiyor → ViewModel sızıntısı.
- Pil okunamadığında modele ham `-1` veriliyor → NOVA "pilin %-1" diyor.
- `LaunchedEffect(vm.mode)` gövdede `baseUrl` okuyor ama key'de yok *(düşük etki)*.

## Temiz çıkanlar

`NoteStore`'da yol/dosya adı zaafı yok. `DeviceStatusReader` izin gerektiren API
kullanmıyor. **Tüm `src/main`'de tek bir `Log.*`/`println`/`printStackTrace` yok** —
sohbet içeriği loglanmıyor. `ChatMarkdown.splitBlocks` sonsuz döngüye girmiyor,
`PrivacyClassifier` regex'lerinde ReDoS riski yok.

## Toplam

| | İlk tur | İkinci tur | Toplam |
|---|---|---|---|
| Düzeltildi | 3 | 0 | **3** |
| Kritik | 2 | 6 | **8** |
| Yüksek | 6 | 7 | **13** |
| Orta | 7 | 10 | **17** |

Kalan **38 açık bulgu**. Bunların 8'i (gizlilik sızıntısı, kalıcı kilit, çökme,
veri kaybı) Play'e çıkmadan önce kapatılması zorunlu sınıfta.

---

# G1 + G2 düzeltildi (2026-08-27, ikinci oturum)

## G2 — sınıflandırıcı artık gönderilecek şeyin tamamına bakıyor

`PrivacyClassifier.isAnySensitive(texts: List<String>)` eklendi.
`NovaViewModel.conversationSensitive()` gizlilik kararının **tek kaynağı** oldu ve
`messages` içindeki tüm **kullanıcı** mesajlarını tarıyor.

Eski hâli `messages.lastOrNull { role == "user" }` idi — yalnız son istem. Sızıntı tam
buradaydı: kart numarası 1. turda yazılıp telefonda kalıyor, 3. turdaki masum ama uzun
istem uzunluk kuralıyla PC'ye gidiyor ve **kart numarasını içeren 1. tur da onunla
birlikte** dışarı çıkıyordu (istek gövdesi mesaj listesinin tamamını taşır).

Bilinen sınır: yalnız kullanıcı mesajları taranıyor. Asistanın sırrı yankılaması bu
kontrole takılmaz — sır kullanıcı tarafında yazılır, asistan yanıtı ondan türer.

## G1 — otomatik devir gizlilik kapısından geçiyor

`autoHandoffAfterLocalError()` başına eklendi:

```kotlin
if (conversationSensitive()) return false
```

`decideHybrid` hassas konuşmayı **bilerek** cihazda tutuyordu; yerel motor hata verdi
diye aynı içerik sessizce dışarı çıkamaz. Eskiden bu kapı yoktu: hassas istem, izin
kartı hiç gösterilmeden gateway'e — oradan da seçili model bulut sağlayıcısıysa
**buluta** — gidiyordu.

`false` dönünce akış zaten izin kartını kuruyor, yani kullanıcı isterse **kendi eliyle**
onaylıyor (`approveFallback`). `PrivacyClassifier` sınıf yorumundaki "gizlilik override'ı
yalnız OTOMATİK devri engeller, kullanıcının elle tercihini kısıtlamaz" sözü ancak böyle
tutuyor.

Ek olarak izin kartı **nedeni söylüyor**: otomatik devir gizlilik yüzünden durduysa kart
bunu yazıyor. Aksi halde kullanıcı, açık olan kuralının neden işlemediğini bilemezdi.

## Doğrulama

| | Sonuç |
|---|---|
| Derleme (Assemble Project with Tests) | ✔ BUILD SUCCESSFUL |
| Birim testleri | ✔ **291/291 geçti** (288 + 3 yeni) |
| Eski `isSensitive(lastPrompt)` çağrısı | ✔ kalmadı (0 eşleşme) |
| APK | ✔ `app/build/outputs/apk/debug/app-debug.apk`, 67,268,523 bayt |

Yeni testler `PrivacyClassifierTest`'te:
`gecmisteki sir sonraki turda da hassas sayilir` (asıl regresyon kilidi — aynı test hem
`isAnySensitive`'in yakaladığını hem eski `isSensitive(son mesaj)` kuralının
**kaçırdığını** ispatlıyor), `tamamen masum konusma hassas degildir`, `bos liste hassas
degildir`.

Ayrıca bu turda bulunan ve düzeltilen dördüncü kusur: `NovaSecretFieldTest.FakeClipboard`
içindeki `var text` alanı `ClipboardManager.getText()/setText()` ile *platform declaration
clash* üretiyordu; **enstrümanlı test kaynağı bugüne kadar hiç derlenmiyordu.** Push CI'ı
yalnız JVM testlerini koştuğu, enstrümanlı workflow ise elle tetiklendiği için
görülmemiş. Alan `stored` olarak yeniden adlandırıldı.

**Kalan:** 36 açık bulgu. Sıradaki kritikler K1 (indirilen model aktif olmuyor),
K2 (indirme arka planda ölüyor), T1 (`BigInteger(id)` çökmesi), T2 (kalıcı modal kilidi),
V1/V2 (sohbet geçmişi veri kaybı).

---

# K1, K2, T1, T2, V1, V2 düzeltildi (2026-08-27, üçüncü oturum)

## K1 — indirilen model artık aktif oluyor

`LocalLlmController`'a `onModelInstalled` geri çağrısı eklendi; kurulum bitince
`NovaViewModel.activateIfNothingUsable` çalışıyor.

Kural bilinçli olarak "her indirmede geç" değil: seçili model zaten kuruluysa
kullanıcının çalışan tercihi elinden alınmaz. Ama kurulu değilse ortada
kullanılabilir bir şey yok demektir — çıkmaz tam oradaydı.

## K2 — indirme uygulamadan koparıldı

**Araştırma kararı değiştirdi.** İlk plan `dataSync` ön plan servisiydi; ama
Android 15+ hedefleyen uygulamalarda bu tür **24 saatte 6 saatle sınırlı**
([FGS timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)).
Zaman sınırı olmayan doğru API "user-initiated data transfer" (UIDT, API 34+),
**ama Jetpack'te desteği yok**: `setUserInitiated()` WorkManager'da bulunmuyor,
ham `JobScheduler` `JobService` yazmak gerekiyor
([UIDT](https://developer.android.com/develop/background-work/background-tasks/uidt)).

Karar: WorkManager + `dataSync`, sınırı manifestte açıkça belgelenmiş hâlde.
8,6 GB için ~4 Mbit/s sürekli hız yeterli; sayaç uygulama öne gelince sıfırlanır
ve `.part` korunduğu için indirme kaldığı yerden sürer.

- Yeni `ModelDownloadWorker`: ön plan bildirimi, ilerleme (1,5 sn'de bir), iptal
  aksiyonu, `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 34+ zorunlu).
- `LocalLlmController` işi kuyruğa alıyor, `getWorkInfosByTagFlow` ile izliyor.
  Gözlemci `viewModelScope`'a bağlı **ama iş değil**: uygulama ölünce yalnız
  izleme durur.
- Bağımlılık: `androidx.work:work-runtime:2.11.2`. **`-ktx` değil** — o artifact
  2.9.0'dan beri boş, `CoroutineWorker` dahil her şey ana artifact'ta.
- İzinler: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`.

**Bonus (denetim L1):** `shutdown()` içindeki motor boşaltması `viewModelScope`
ile yazılmıştı ve o scope `onCleared`'dan ÖNCE kapandığı için **hiç
çalışmıyordu**. Ayrı bir daemon thread'e alındı.

## T1 — sayısal olmayan olay kimliği çökertmiyor

`BigInteger(it.id)` yerine `orderEvents`: kimliklerin tamamı sayısalsa sayısal
sıraya girer, değilse geliş sırası korunur. Sayısal olmayan kimlikte "doğru" bir
sayısal sıra yoktur; uydurmak yerine dokunmuyoruz (SSE zaten sıralı teslim eder).

## T2 — terminal görev kilidi açıldı

`task.state{failed|cancelled|completed}` artık bekleyen onayı temizliyor.

Yol boyunca `isTerminal()` kuralının **üç ayrı yere kopyalanmış** olduğu çıktı
(ekranda özel uzantı, VM'de özel fonksiyon, reducer'dan erişilemez). Kusurun
fark edilmemesinin bir nedeni buydu — kural enum üyesine taşındı, üçü de oradan
okuyor.

## V1 — geçmiş atomik yazılıyor, bozulma sessizce silinmiyor

- Geçici dosya + `fd.sync()` + `renameTo`. Ya eski tam içerik ya yeni tam içerik.
- Bozuk dosya `<dosya>.corrupt` olarak karantinaya alınıyor; ilk `save()` artık
  onu ezip yok edemiyor. `[]` gerçekten boştur, karantinaya alınmaz.

## V2 — kaydetme boşlukları kapandı

`saveCurrent()` artık `stop()`, gateway hata yolu ve yerel hata yolunda da
çağrılıyor. `onCleared` için bloklayan varyant eklendi — orada
`viewModelScope.launch` çalışmaz (L1 ile aynı tuzak).

## Doğrulama

| | Sonuç |
|---|---|
| Derleme | ✔ BUILD SUCCESSFUL (WorkManager 2.11.2 çözüldü) |
| Birim testleri | ✔ **303/303 geçti** (291 + 12 yeni) |
| APK | ✔ `app/build/outputs/apk/debug/app-debug.apk`, 67.787.469 bayt |

**12 yeni test:** T1 için dört (ULID, float `"1.0"`, karışık kimlik, reducer'dan
uçtan uca), T2 için üç (failed/cancelled temizler, EXECUTING korur), V1 için
dört (karantina, `[]` karantinaya alınmaz, `.tmp` kalmaz, mevcut yedek ezilmez),
artı G2'den devreden regresyon kilidi.

**Test edilemeyenler (dürüstlük notu):** K1'in aktifleştirme kuralı ve V2'nin
kaydetme çağrıları ViewModel içinde — JVM biriminden erişilemiyor. K2'nin worker'ı
enstrümanlı test ister. Bunlar **derlendi ama davranışsal olarak doğrulanmadı**;
gerçek cihazda görülmeleri gerekiyor.

**Kalan:** 30 açık bulgu. Sıradaki yüksekler Y1 (`NetworkPolicy` çağrı
noktalarına bağlı değil), Y2 (bozuk model native motora veriliyor), Y3/Y4/Y5
(indirme dayanıklılığı: HTTP 416, atlanan SHA, disk dolu mesajı), Y6 (tek
motorda iki eşzamanlı üretim), S1 (TTS 4000 karakter kilidi), J1 (Android
`org.json` null farkı).

Ayrıca derlemede görülen yeni uyarı: `androidx.compose.ui.platform.ClipboardManager`
kullanımdan kaldırılmış, yerine `Clipboard` öneriliyor. Kusur değil, sonraki tur işi.

---

# Tur 3 — Y1…Y6 + güvenlik ve kod denetim testleri

## Y1 — ağ politikası artık çağrı noktasına bağlı

`NetworkPolicy` vardı ama **kimse çağırmıyordu**: ayarlar ekranındaki tek kontrol
kaydetme anındaydı, sonrasında istek atan her yol politikayı atlıyordu.

- `GatewayConnectionClient.canonicalBaseUrl()` tek boğaz noktası oldu: URL'i
  `/v1` yoluna sabitliyor ve **her seferinde** `NetworkPolicy.allowsHost()`
  çağırıyor. Model listesi de (`modelsUrl`) aynı noktadan türüyor.
- `hostOf()` otorite sınırını yanlış buluyordu: yalnızca `/` arıyordu, bu yüzden
  `http://evil.com?x=@127.0.0.1` gibi bir dizgede yanlış konağı okuyabiliyordu.
  Sınır artık `/`, `?` ve `#` üçlüsünün ilki.
- Yeni `allowsHost(scheme, host)`, ayrıştırılmış `HttpUrl` üzerinden çalışıyor —
  dizgeyi ikinci kez elle ayrıştırmak yok.

## Y2 — doğrulanmamış model native motora verilmiyor

İki taraflı düzeltildi:

- `LocalModelStore.verify()` başarısız olunca artık işaretçiyi **ve model
  dosyasını** siliyor. Bozuk dosya diskte kalıp her açılışta yeniden denenmez.
- `LocalLlmController` yüklemeden önce `diskState.verified` bakıyor;
  `Installed` olması tek başına yetmiyor.

Bu ikisi ayrı: birincisi bozuk dosyayı temizler, ikincisi temizlenmemiş bir şey
kalırsa motoru korur. Native tarafta bozuk GGUF çökme demek, tek katman az.

## Y3 — HTTP 416 "hata" olmaktan çıktı

Yarım dosya tam boya ulaşmışken sunucu `416 Range Not Satisfiable` döndürüyor,
kod bunu `else` dalında yakalayıp **"İndirme hatası (HTTP 416)"** diye
gösteriyordu — indirme aslında bitmişti. Artık `.part` beklenen boydaysa
doğrulanıp kuruluyor, ağa hiç çıkılmıyor.

## Y4 — atlanan SHA doğrulaması

Hedef dosya zaten varsa kod doğrudan `Result.Success` dönüyordu; içeriğin doğru
olduğunu kimse kontrol etmiyordu. Artık aynı `verifyAndMark()` yolundan geçiyor.

## Y5 — disk dolu mesajı

`IOException` gövdesindeki metne bakmak yerine `modelsDir.usableSpace` ölçülüyor
(`LOW_SPACE_BYTES = 16 MiB`). Metin eşleştirmesi yerelleştirme ve üretici
farklarında sessizce yanlış cevap verirdi.

## Y6 — tek motorda iki eşzamanlı üretim

`@Volatile generationToken`: her üretim kendi jetonunu alıyor, jeton değiştiyse
eski akış çıktıyı yazmadan çekiliyor. `stop()` de jetonu artırıyor.

**Bonus:** `shutdown()` indirmeleri de iptal ediyordu — ekran kapanınca arka
plandaki model indirmesi ölüyordu. Artık yalnızca motoru boşaltıyor; indirme
WorkManager'ın işi (K2).

## Yeni test türleri

Bunlar mevcut birim testlerinden ayrı, farklı bir soruyu soruyorlar.

**`NetworkPolicyEnforcementTest` — 8 güvenlik testi.** "Politika doğru cevap
veriyor mu" değil, **"politika gerçekten uygulanıyor mu"**. Yasak konaklara
`canonicalBaseUrl()` üzerinden gidiliyor; `null` dönmesi bekleniyor. Y1'in
otorite-sınırı hatası (`?x=@127.0.0.1`) doğrudan kilitlendi.

**`SourceGuardTest` — 13 kod denetim testi.** Kaynağı okuyup düzeltilen kusurun
geri gelmediğini doğruluyorlar: `BigInteger(it.id)` yok, `shutdown()` içinde
indirme iptali yok, `verify()` başarısızlıkta siliyor, hedef dosya doğrulanmadan
`Success` dönmüyor.

İlk koşuda **ikisi kırmızı yandı** — ve sebebi öğretici: guard'lar kodu
denetlemeli ama yorumu da tarıyorlardı, benim KDoc'larımda eski hatalı satır
aynen yazılıydı. `stripComments()` eklendi. Kural doğruydu, ölçtüğü metin
yanlıştı. Ters yönü daha önemli: yorum taranırsa bir kural yalnızca yorumla
"sağlanmış" görünebilirdi.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **324/324 geçti** (1 sn 677 ms) |
| APK | ✔ `app/build/outputs/apk/debug/app-debug.apk`, 67.990.113 bayt, 28.08.2026 05:25 UTC |

303 → 324: 21 yeni test (8 güvenlik + 13 kod denetim).

**Kalan:** ~24 açık bulgu. Sıradakiler S1 (TTS 4000 karakter kilidi), J1
(Android `org.json` null → `"null"`), mDNS sızıntısı, ölü "Yeniden tara"
düğmesi, panonun hassas işaretlenmemesi, geçmiş arama yarışı, onaysız silme,
Hesap Makinesi taşması, iptal edilmeyen `modelsCall`, batarya `-1`.

---

# Tur 4 — T3, T4, E1, S1, J1, M1 (yüksek grubun kalanı)

## T3 — temiz kapanış da bir kopmadır

`onClosed()` yalnızca `eventSource = null` yapıyordu. Araya giren proxy
(nginx/Cloudflare) boşta akışı ~60 sn'de **hatasız** kapatır: `onError` hiç
çağrılmaz, yeniden bağlanma tetiklenmez. Kod zaten geri çekilmeli yeniden
bağlanmaya sahipti — ama yalnızca `onError`'dan ulaşılabiliyordu, yani en sık
kopma biçimi tam olarak kör noktadaydı. Görev PC'de sürerken telefon "Plan
hazırlanıyor"da donuyor, sonradan gelen **onay isteği hiç görünmüyordu**.

Üç şey birlikte düzeltildi:

- `onClosed` → görev terminal değilse `scheduleReconnect()`. Nesil koruması
  (`streamGeneration`) bizim kendi iptalimizi zaten eliyor.
- **Sağlıklı akış sayacı sıfırlar:** 20 sn'den uzun yaşamış bir akışın kapanması
  proxy'nin normal döngüsüdür, arıza değil. Yoksa sessiz ama uzun süren bir
  görev, yalnızca 60 sn'de bir kapatıldığı için birkaç dakika sonra
  "vazgeçildi" sayılırdı.
- **Yeniden deneme artık sınırlı** (6 ardışık başarısız deneme). Sunucu
  gerçekten kapalıysa sonsuza dek denemek yerine durum söyleniyor ve ekrana
  "Yeniden bağlan" düğmesi geliyor — `clearError()` bu iş için yazılmıştı ama
  hiçbir yerden çağrılmıyordu, ekranda çıkışsız hata kalıyordu.

**Bonus:** görev SSE üzerinden terminale geçtiğinde akış kapatılmıyordu (HTTP
yolu kapatıyordu, olay yolu kapatmıyordu) — biten görevin boşta bağlantısı ekran
açık kaldığı sürece taşınıyordu.

## T4 — onay paneli parmağın altında değişmiyor

`pendingConfirmation` yeni istekle doğrudan **eziliyordu**. A'nın özetini okuyup
dokunurken B düşerse, kullanıcı A sandığı şeye basıp **B'yi onaylıyordu** —
üstelik B daha riskli olabilir. Riskli eylem onayının tüm değeri "ne
onayladığını gördün" garantisine dayanır.

Artık ekrandaki onay çözülene kadar sabit; yeni istek `queuedConfirmations`
kuyruğuna girer ve sırası gelince panele kendi gelir. Kaybolmaz.

Bu düzeltme **gizli bir bağımlılığı** açığa çıkardı: gecikmiş HTTP yanıtının
görev durumunu ezmesine karşı koruma, aslında T4 hatasının kendisiydi (onay
ezildiği için kimlik tutmuyor, mutasyon düşüyordu). T4 kapanınca koruma da gitti;
`ConfirmationResolved` içine açıkça yazıldı. Eski test bunu `assertNull` ile
kilitliyordu — iddia güncellendi, testin **koruduğu şey** (A'nın eski durumu
B'nin panelinin üstüne yazılmasın) duruyor.

Ayrıca: **statüsüz olay durumu geri alıyordu.** Kullanıcı "Duraklat"a basıp HTTP
yanıtı PAUSED yazdıktan sonra gelen statüsüz bir `worker.*` olayı, geriye bakıp
son statülü olayı (EXECUTING) buluyor ve duraklatmayı sessizce geri alıyordu.

## E1 — düşünme sızıntısı (iki ayrı kusur)

1. **Gateway yolu düşünmeyi hiç ayıklamıyordu.** `finishLocal` ayıklıyor,
   `finish` ayıklamıyordu. Aynı model Ollama üzerinden gateway'e bağlanınca ham
   `<think>` balonda görünüyor, dışa aktarmaya ve panoya da gidiyordu —
   "düşünme paylaşımdan çıkarılır" vaadi gateway yolunda tümden geçersizdi.
2. **Akış sırasında ham metin gösteriliyordu.** Her iki yolda da `onToken`
   doğrudan `sb.toString()` yazıyordu; düşünme canlı canlı balonda akıyor,
   yalnızca bitişte kayboluyordu. Kullanıcı o arada kopyalarsa düşünmeyi
   kopyalıyordu.

Tek bir `renderStreamed()` boğaz noktası eklendi; gateway'in ayrı kanaldan
gönderdiği düşünme ile metnin içinden ayıklanan blok birleşiyor, iki kaynak
birbirini ezmiyor.

**Sözleşme değişti — dürüstlük notu:** kapanmamış `<think>` bloğu eskiden
"şeffaflık" gerekçesiyle bilerek içerikte bırakılıyordu ve **iki test bunu
doğruluyordu**. O karar yanlıştı: iki söz veriyor, ikisini de tutmuyordu. Artık
kapanmamış blok düşünme sayılıyor. Şeffaflık kaybolmuyor — metin düşünme
panelinde duruyor, yalnızca doğru yere yazılıyor. İki test yeni sözleşmeye
güncellendi, gerekçesi test dosyalarında yazılı. Ayrıca birden fazla blok da
destekleniyor (bazı modeller araç turları arasında yeni blok açar; tek bloğa
bakan eski kod ikincisini içerikte bırakıyordu).

## S1 — TTS uzunluk sınırı

`speak()` girdisi `getMaxSpeechInputLength()` sınırını (çoğu cihazda 4000)
aşarsa **ERROR döner ve hiçbir geri çağrı gelmez**: ne `onDone`, ne `onError`.
Dönüş değeri de kontrol edilmiyordu, yani hata tümüyle sessizdi — 5000
karakterlik yanıtta orb sonsuza dek "Konuşuyorum"da kalıyor, ses çıkmıyordu.

Yeni `TtsChunker` (saf, JVM-testli) cümle → kelime → sert kesim önceliğiyle
bölüyor; parçalar kuyruğa veriliyor, `onDone` yalnızca son parça bitince bir kez
çağrılıyor. Her çıkış yolu `onDone`'a bağlandı.

## J1 — Android `org.json`'un null farkı

Android'in `org.json`'u referans JVM uygulamasından sessizce ayrılır: değer JSON
null ise `optString(name, fallback)` fallback döndürmez, **`"null"` dizesini**
döndürür. Sağlayıcılar boş alanları atlamak yerine `null` yazdığı için pratikte
sık: `{"route": null}` → balonun altında "→ null"; `{"reasoning_content": null}`
→ her deltada bir "null", düşünme panelinde "nullnullnull…".

Tek yardımcıya (`JSONObject.str`) indirildi ve **36 çağrı noktasının tamamı**
oradan geçiyor.

**Bunu birim testi yakalayamaz** — JVM'de kullanılan referans `org.json` doğru
davranır, hata yalnızca cihazda çıkar. O yüzden asıl koruma guard testi: üretim
kodunda ham `optString` kalmadığını doğruluyor.

## M1 — kullanıcının kendi iptali "Gateway hatası (200)"

`EventSource.cancel()` OkHttp'de `onFailure`'ı `IOException("Canceled")` ile ve
**hâlâ açık olan 200 yanıtıyla** tetikler; kod `code != null` görüp "Gateway
hatası (200)" yazıyordu. Kullanıcı "Durdur"a bastığında kendi iptali sunucu
hatası gibi görünüyordu.

Metne bakmak (`t.message == "Canceled"`) çözüm değil — Y5'te aynı hatayı
temizledik. İptal artık işaretleniyor: `NovaClient.cancelStream()` nesli artırır,
eski dinleyicinin geri çağrıları sessizce düşer. Ayrıca 2xx yanıtta akış koparsa
artık durum kodu değil kopma nedeni yazılıyor.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **355/355 geçti** (1 sn 741 ms) |
| Yeni testler | 21 (8 kod denetim + 13 davranış) |

324 → 355. Guard testleri 13 → 21.

**İlk koşuda 3 test kırmızıydı ve üçü de gerçek çelişkiydi**, düzeltmelerin
kendisindeki hata değil: (1) hata temizleme fazla genişti — biten görevde hata
mesajı ne olduğunun kaydıdır, silinmemeli; (2) T4'ün açığa çıkardığı gizli
bağımlılık; (3) E1'in bilerek değiştirdiği düşünme sözleşmesi. Üçü de kaynakta
düzeltildi ya da testte gerekçesiyle güncellendi.

**Kalan:** ~17 açık bulgu — tamamı "orta" sınıfta. Keşif/eşleme kalemleri (mDNS
sızıntısı, kaybolan PC listede kalıyor, ölü "Yeniden tara" düğmesi, API 34
öncesi ikinci PC), izin kartının yanlış üç mesajı, panonun hassas
işaretlenmemesi, geçmiş arama yarışı, onaysız silme, Hesap Makinesi taşması,
iptal edilmeyen `modelsCall`, batarya `-1`, yedekleme kuralları.

---

# Tur 5 — arayüz mantık denetimi (emülatörde + kodda)

Uygulama ilk kez **emülatörde çalıştırıldı** (Pixel 10 Pro XL, API 37). Dört tur
kod düzeltmesinden sonra kimse uygulamayı açmamıştı; açıldı, çöküş yok.

## Önce bir yanlış alarm — dürüstlük notu

Emülatörde ilk göze çarpan şey şuydu: üstte "PC bağlantısı kurulmadı" yazarken
yürütme politikası **"PC / Gateway"** seçili, arayüz **Gelişmiş** modda ve
gateway adresi `http://10.0.2.2:8088/v1` (üstelik gateway artık 18088'de).
Üçü de "temiz kurulum böyle açılıyor" gibi duruyordu.

Değildi. Kodda varsayılanlar doğru: `executionPolicy = "local_first"`,
`baseUrl = ""`, `uiMode = SIMPLE`. Emülatördeki değerler **eski geliştirme
kurulumundan kalma diskteki ayarlar**. Ekranda görüneni koda bakmadan bulgu
saymak, olmayan üç kusur uydurmak olurdu.

Aynı şekilde Kontrol ekranının altındaki kartın kesik görünmesi de kusur değil:
ekran kaydırılabilir ve `Scaffold` gezinme çubuğunun payını zaten uyguluyor.

## U1 — izin kartı: yanlış başlık, imkânsız eylem, eksik çıkış

Kart üç ayrı şekilde yanlıştı ve üçü aynı anda görülüyordu:

- **Başlık her durumda "Telefon modeli yanıt veremedi".** Oysa kart, telefonda
  kurulu model HİÇ YOKKEN de açılıyor (`RouteDecision.LocalNeedsSetup`). Var
  olmayan bir modeli "yanıt veremedi" diye suçluyordu.
- **Tek eylem "PC'ye gönder", yalnız politikaya bakılarak gösteriliyordu.**
  Varsayılan kurulumda gateway adresi BOŞ. Yani kart, basıldığında kesin
  başarısız olacak bir eylemi tek çıkış yolu olarak sunuyordu.
- **Asıl çözüm hiç sunulmuyordu:** model yoksa yapılacak şey model indirmektir.
  Karttan Modeller'e giden bir yol yoktu.

`PendingFallback` artık `kind` taşıyor (`NO_LOCAL_MODEL` / `LOCAL_ERROR`); kart
başlığı buna göre değişiyor, "Modelleri aç" birincil eylem oluyor ve "PC'ye
gönder" **yalnız PC gerçekten ulaşılabilirken** öneriliyor. Ulaşılamıyorsa
nedeni yazılıyor.

## U2 — sohbet çiplerinde eylem, durumla aynı görünüyordu

Tek satırda üç farklı cins vardı ve üçü birebir aynı görünüyordu: durum
göstergesi (hedef, model), gezinme (Geçmiş) ve **gerçek bir eylem** —
"PC ajanına devret", son soruyu tüm bağlamıyla PC'ye gönderir.

Sohbeti cihaz dışına çıkaran bir eylemin pasif bir durum etiketiyle aynı
görünmesi kabul edilemez; G1/G2'de kapattığımız sızıntı sınıfının arayüz
karşılığı bu. Eylem çipleri artık vurgulu, durum çipleri sönük.

## U3 — Görevler ekranı: bağlantısız açık ama gönderilemez girdi

Metin alanı ve üç hızlı komut, PC bağlantısı olmadan da **etkindi**. Kullanıcı
"Ayarlar'ı aç"a basıyor, metin alana yazılıyor, sonra ekrandaki tek düğme onu
Ayarlar'a götürüyordu. Görevi PC'deki çalışan yürüttüğü için bağlantısız bu
ekranda yapılabilecek tek anlamlı iş bağlantıyı kurmaktır. Girdi artık yalnız
bağlıyken görünüyor, bağlantısızken nedeni yazıyor.

## U4 — "CİHAZDAKİ MODELLER" başlığı yalandı

Liste kataloğun tamamını gösteriyor — henüz indirilmemiş modelleri de. Hiçbir
şey indirmemiş kullanıcıya "bunlar cihazında" diyordu. Başlık **"TELEFON
MODELLERİ"** oldu.

## U5 — önerilen model iki kez

Öneri afişi ("Önerileni indir") hemen altındaki listede aynı modeli tekrar
gösteriyordu: arka arkaya iki özdeş kart. Afiş artık yalnız model **cihazda
değilken** çıkıyor ve afiş gösterilirken o modelin satırı listeden çıkarılıyor.
Model kurulduğunda afiş kapanıyor, satır listeye dönüyor — silme/doğrulama
yönetimi kaybolmuyor.

## U6 — hazır olmayan duruma onay ikonu

"Çevrimdışı için model gerekli" cümlesinin yanında ✓ (CheckCircle) duruyordu.
Onay işareti "tamamlandı" demektir; cümleyle taban tabana zıt. İki durum yalnız
renkle ayrılıyordu, yani renk körlüğünde hiç ayrılmıyordu. Hazır değilken artık
indirme ikonu kullanılıyor.

## U7 — donmuş telefon-kontrol sekmesi kaldırıldı

"İşler" sekmesi gezinmeden çıkarıldı. Üç bağımsız gerekçe aynı yeri gösteriyor:

1. **Ürün kararı:** telefon kontrolü (AccessibilityService / mobilerun) ilk
   sürüme girmiyor — bu kararı sen verdin.
2. **Play politikası (28 Ocak 2026):** AccessibilityService ile özerk eylem
   yasak. Sekmenin hızlı komutları ("Ayarlar'ı aç", "Bir uygulamayı aç") tam
   olarak bu davranışı tarif ediyor; mağaza incelemesinde doğrudan ret gerekçesi.
3. **Kullanılabilirlik:** görevi PC'deki çalışan yürütüyor. Mağazadan indiren
   kullanıcının PC'si yok; dört ana sekmeden biri onun için hiçbir koşulda
   çalışmıyordu.

**Kod silinmedi.** `NovaAppShell.PHONE_TASKS_TAB_ENABLED = false` tek anahtar;
`true` yapmak sekmeyi olduğu gibi geri getirir. Sekme kapalıyken TASKS
durumunda kalan kullanıcı Sohbet'e alınıyor — yoksa gezinme çubuğunda o sekme
olmadığı için erişilemeyen bir ekranda kilitlenirdi.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **362/362 geçti** (1 sn 741 ms) |
| Emülatör | ✔ Kurulum + açılış sorunsuz; değişiklikler ekranda doğrulandı |

355 → 362: 7 yeni kod denetim testi. Guard testleri 21 → 28.

Bir guard ilk koşuda kırmızı yandı ve **haklı olarak**: "onay ikonu kullanma"
kuralını tüm dosyaya uygulamıştım, o da gateway model satırındaki ✓ işaretini
yakaladı — orada ✓ "bu model seçili" demek ve doğru. Kural doğruydu, kapsamı
genişti; yalnız çevrimdışı hazırlık kartına daraltıldı.

**Kalan:** ~17 orta bulgu (mDNS sızıntısı, kaybolan PC listede kalıyor, ölü
"Yeniden tara" düğmesi, panonun hassas işaretlenmemesi, geçmiş arama yarışı,
onaysız silme, batarya `-1`, yedekleme kuralları).

---

# Tur 6 — kalan orta bulgular (denetim listesi kapandı)

## Keşif / eşleme (5)

**mDNS geri çağrıları geri alınmıyordu.** API 34+ `registerServiceInfoCallback`
bulunan her servis için kayıt bırakıyor, `awaitClose` yalnız
`stopServiceDiscovery` çağırıyordu. Panel her açılışta kayıtlar birikiyor ve
kapanmış akışa veri göndermeye devam ediyordu — "arka planda sürmemeli"
sözleşmesinin tam tersi. Kayıtlar listeleniyor ve kapanışta geri alınıyor.

**Kapanan PC listede kalıyordu.** `onServiceLost` yalnız servis adını bilir;
kod ise `displayName` ile eşleştiriyordu ve o ad TXT'teki `name` alanından
geliyor — ikisinin aynı olması şart değil. Sonuç: kapanmış bir PC listede
duruyor, kullanıcı ölü kayıt için boşuna eşleme kodu yakıyordu. Artık servis
adı → baseUrl dizini tutuluyor.

**API 34 öncesi ikinci PC hiç çözümlenmiyordu.** `resolveService` ASENKRONDUR;
eski kod onu tek iş parçacıklı havuza atıyordu ama iş parçacığı çağrıyı başlatıp
hemen dönüyordu — yani sıraya alma diye bir şey yoktu. İki PC arka arkaya
bulununca ikinci çağrı `FAILURE_ALREADY_ACTIVE` alıyor, `onResolveFailed` da onu
sessizce yutuyordu. Artık iş parçacığı çözümleme bitene kadar bekliyor (zaman
aşımıyla), kuyruk gerçekten sıralı.

**"Yeniden tara" düğmesi ölüydü.** Doğrudan `startDiscovery`'ye bağlıydı, o da
`discoveryJob` aktifse ilk satırda dönüyordu. mDNS keşfi normalde HİÇ bitmez,
yani job her zaman aktifti: düğme hiçbir şey yapmıyordu — ne tarama, ne geri
bildirim. PC'sini yeni açan kullanıcı için tek çıkış paneli kapatıp açmaktı.
Yeni `rescan()` taramayı kapatıp listeyi temizleyerek baştan başlatıyor.

**Özel TXT `path` dikişi — iki bileşen birbirini yalanlıyordu.** Keşif
`path=/api/v1` gibi özel bir yolu kabul ediyordu ve **bir test bunu
doğruluyordu**. Oysa `canonicalBaseUrl` Y1'den beri `/v1` dışını REDDEDİYOR.
İkisi ayrı ayrı testliydi, aradaki dikiş değildi: böyle bir gateway listede
çıkıyor, eşleme "başarılı" diyor, sonra her istek sessizce düşüyordu. NOVA
yalnız `/v1` konuşur; keşif artık aynı kuralı uyguluyor ve kullanılamayacak
PC'yi hiç önermiyor. Testin iddiası tersine çevrildi, gerekçesi yazılı.

## Veri ve sızıntı (8)

**API anahtarı panoya hassas işaretlenmeden gidiyordu.** Android 13+ panoya
kopyalanan içeriğin ÖNİZLEMESİNİ ekranda gösteriyor. Anahtar arayüzde özenle
maskeleniyordu ama "Kopyala"ya basıldığı anda sistem onu düz metin olarak
gösteriyordu — maskelemenin tüm anlamı orada kaçıyordu.
`ClipDescription.EXTRA_IS_SENSITIVE` eklendi.

**Geçmiş aramasında yarış.** Her tuş vuruşu ayrı arama başlatıyor, sonuçlar
BİTİŞ sırasına göre yazılıyordu: "abc" hızlı yazıldığında "a" araması en son
bitip "abc"nin sonucunu ezebiliyordu. Bağlantı sondaları için yazılmış nesil
deseninin karşılığı geçmişte yoktu; eklendi.

**Silme onaysız ve geri alınamazdı**, üstelik çöp kutusu ikonu paylaş ikonunun
bitişiğindeydi. Yanlış dokunuş bir sohbeti kalıcı yok ediyordu. Satır içi iki
adımlı onay eklendi.

**`modelsCall` `onCleared`'da iptal edilmiyordu** → ViewModel yıkımından sonra
yanıt bekleyip onu canlı tutuyordu.

**Bilinmeyen pil ham `-1` olarak modele veriliyordu** → NOVA "pilin %-1"
diyordu. `-1` bir yüzde değil, "bilinmiyor" demek; öyle söyleniyor.

**`LaunchedEffect(vm.mode)` gövdede `baseUrl` okuyup key'de tutmuyordu:**
Modeller ekranı açıkken eşleme tamamlanıp adres dolduğunda katalog
tazelenmiyordu.

**Hesap Makinesi taşma mesajı hiçbir koşulda doğru olamazdı.** `9^999` için
"Tanımsız sonuç (sıfıra bölme olabilir)" yazıyordu — oysa sıfıra bölme AYRICA ve
daha önce yakalanıyor, yani o dala asla ulaşamaz. Taşma ve tanımsız sonuç artık
ayrı ayrı söyleniyor.

**Derin iç içe ifade uygulamayı çökertiyordu.** `((((…))))` özyineleme zincirini
yığın taşana kadar sürdürüyordu. `StackOverflowError` bir `Exception` DEĞİL,
`Error`'dır: `catch (e: CalcException)` onu yakalamaz. "Araç hataları uygulamayı
düşüremez" garantisi tam burada kırılıyordu. Yığın taşmasını yakalamaya çalışmak
güvenilmez (yakalandığı anda yığın zaten tükenmiştir); doğrusu oraya hiç
varmamak — derinlik sınırı kondu.

## Yedekleme kuralları (Play blokeri)

`allowBackup="true"` ve hiçbir kural dosyası yoktu. İki sonucu vardı:
Hugging Face belirteci ve gateway anahtarı kullanıcının Drive'ına kopyalanıyordu;
ve indirilen modeller GB'larca yer tuttuğu için 25 MB'lık Auto Backup kotası
aşılıyor, uygulamanın yedeği TÜMÜYLE atlanıyordu — yani yedeklenmesi anlamlı
olan sohbet ve notlar da yedeklenmiyordu. `backup_rules.xml` (API ≤30) ve
`data_extraction_rules.xml` (API 31+) eklendi: modeller ve sırlar buluta
gitmiyor, sırlar cihazdan cihaza aktarımda KALIYOR (telefon yenilendiğinde
yeniden eşleme gerekmesin).

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **378/378 geçti** (1 sn 808 ms) |
| Emülatör | ✔ Yeni manifest'le kurulum ve açılış sorunsuz |

362 → 378: 16 yeni test (11 kod denetim + 5 Hesap Makinesi). Guard testleri
28 → 39.

## Denetim listesi kapandı

İlk turda 41 bulgu vardı. **Kalan gerçek kusur yok.** Bilinen tek açık kalem
bir kusur değil, derleyici uyarısı: `androidx.compose.ui.platform.ClipboardManager`
kullanımdan kaldırıldı, yerine `Clipboard` öneriliyor.

Bundan sonrası kod değil **Play hazırlığı**: gizlilik politikası URL'i (B4),
uygulama içi AI içerik bildirimi (B6), Data Safety formu (B7), mağaza görselleri,
Play Console hesabı + geliştirici doğrulaması (30 Eylül 2026), 12 test
kullanıcısı / 14 gün, ve imzalı release AAB için upload keystore.

---

# Tur 7 — Play B6: uygulama içi yapay zekâ içerik bildirimi

Kod denetimi kapandıktan sonra kalan **tek kod blokeri** buydu.

## Gereklilik (politika metni)

Play'in AI-Generated Content politikası: *"Apps that generate content using AI
must contain in-app user reporting or flagging features that allow users to
report or flag offensive content to developers **without needing to exit the
app**."* Ayrıca: *"Developers should utilize user reports to inform content
filtering and moderation."*

Belirleyici kısım **"uygulamadan çıkmadan"**. E-posta uygulamasına ya da
tarayıcıya atarak biten bir akış bu şartı karşılamaz.

## Çözülmesi gereken çelişki

NOVA'nın "istemler telefondan çıkmaz" sözü var ve geliştiriciye ait bir sunucu
**yok**. Bildirimi arkadan yüklemek hem bu sözü hem Data Safety beyanını
çiğnerdi. Ama yalnız cihazda tutmak da "geliştiriciye bildirebilme" şartını
zorlar.

Ayrım şurada: **bildirmek** ile **göndermek** aynı şey değil.

- **Bildirme** cihazda kapanır: yanıtın altındaki bayrak → sebep seçimi →
  isteğe bağlı not → kaydedildi onayı. Uygulamadan çıkılmaz. ✔ politika şartı
- **Gönderme** ayrı ve isteğe bağlı: Ayarlar → İçerik bildirimleri →
  "Geliştiriciye gönder". Kullanıcı gönderilecek metni paylaşım ekranında
  **görür**.

## Eklenenler

- `data/ContentReport.kt` — bildirim modeli + politikanın saydığı sakıncalı
  içerik türlerine karşılık gelen sebepler. Alıntı 400 karakterle sınırlı:
  bildirim dosyası sohbet arşivine dönüşmesin.
- `data/ContentReportStore.kt` — cihazdaki depo. V1'deki atomik yazma deseni
  (geçici dosya + `fd.sync()` + `renameTo`) ve bozuk dosyayı silmek yerine
  `.corrupt` olarak karantinaya alma kararı burada da geçerli. 200 kayıt sınırı.
- Sohbet ekranı: her yapay zekâ yanıtının yanında **bayrak** eylemi +
  `ReportContentDialog`. Bildirilecek alıntı kullanıcıya gösteriliyor — neyi
  bildirdiğini görmeden onaylamasını istemek "ne paylaştığını gör" ilkesinin
  ihlali olurdu.
- Ayarlar: "İçerik bildirimleri" bölümü — sayaç, gönder, sil.
- Kısa onay mesajı (`notice`): kullanıcı bildirdiğini görmeli, yoksa aynı yanıtı
  tekrar tekrar bildirir ya da işe yaramadığını sanır.

**Bildirim eylemi neden Ayarlar'da değil:** Ayarlar'a gömülü bir form "içeriği
bildir" değil "bir yerde şikâyet et" olurdu; kullanıcı hangi yanıtı bildirdiğini
de seçemezdi.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **389/389 geçti** (3 sn 221 ms) |
| Emülatör | ✔ Kurulum ve açılış sorunsuz, Ayarlar açılıyor |

378 → 389: 11 yeni test (8 depo + 3 kod denetim). Guard testleri 39 → 42.

**Dürüstlük notu:** bildirim iletişim kutusunun kendisi cihazda **görsel olarak
denenmedi**. Emülatöre metin yazmak bu oturumdaki erişim seviyesiyle mümkün
değil, dolayısıyla gerçek bir yapay zekâ yanıtı üretilip bayrağa basılamadı.
Depo mantığı, akışın cihazda kapandığı ve sessiz yükleme olmadığı testlerle
kilitli; kutunun görsel akışı gerçek cihazda bir kez denenmeli.

## Play durumu

| Kalem | Durum |
|---|---|
| B6 — uygulama içi AI içerik bildirimi | ✔ **kapandı** (kod) |
| B4 — gizlilik politikası | ✔ **yayına hazır** → `site/privacy/index.html` + `.github/workflows/pages.yml`; adımlar `docs/play/YAYIN-KONTROL-LISTESI.md` |
| B7 — Data Safety formu | ✔ cevaplar hazır → `docs/play/DATA-SAFETY-FORMU.md`; **Play Console'a senin girmen gerekiyor** |
| Mağaza görselleri, açıklamalar | ✖ açık |
| Play Console hesabı + geliştirici doğrulaması | ✖ açık (30 Eylül 2026 sınırı) |
| 12 test kullanıcısı / 14 gün | ✖ açık |
| Upload keystore + imzalı AAB | ✖ açık — `scripts/new-upload-keystore.ps1` ile **senin** oluşturman gerekiyor |

---

# Tur 8 — kendi düzeltmemdeki hata, indirme görünürlüğü, araç sürümleri

## P1 — "hassas pano" düzeltmesi sızıntıyı KAPATMIYORDU

Turlardan birinde gizli değerin panoya `EXTRA_IS_SENSITIVE` işaretiyle
yazılmasını sağlamıştım. Kodu yeniden okuyunca düzeltmenin işe yaramadığını
gördüm: `NovaSecretField` panoya **iki kez** yazıyordu — önce Compose'un
panosuyla (işaretsiz), hemen ardından işaretli clip'le üzerine.

Android 13+ önizleme baloncuğu **ilk** `setPrimaryClip` çağrısında açılıyor.
Yani `nv_…` anahtarı, ikinci yazma gelene kadar zaten düz metin olarak
ekrandaydı. İkinci yazma baloncuğu tazeliyor, olan biteni geri almıyor.
**Pencereyi daraltmak sızıntıyı kapatmaz.**

Düzeltme: `SensitiveClipboard.kt` tek bir `copyToClipboard` fonksiyonuna
indirildi — clip, işaret ÜZERİNDEYKEN tek çağrıyla yazılıyor; sistemin gördüğü
ilk ve tek hâli maskelenmiş hâli.

Bunun yan faydası olarak `NovaSecretField`'ın kancası `ClipboardManager`
yerine sade bir `(String) -> Unit` oldu. Enstrumanlı testte
`ClipboardManager`'ı taklit etmek Kotlin'in yedek alandan ürettiği
`getText()/setText()` imzaları yüzünden "platform declaration clash" veriyordu;
o kaynak bu yüzden uzun süre hiç derlenmemişti. Fonksiyon tipinde bu tuzak yok.
Sohbetteki iki kopyalama da kullanımdan kalkan `LocalClipboardManager`'dan
kurtarıldı.

**Guard testi de yanlıştı:** eski `gizli deger panoya hassas isaretlenerek
kopyalanir` testi `markClipboardSensitive` metnini arıyordu — yani sızıntıyı
kapatmayan mekanizmanın VARLIĞINI doğruluyordu. Test artık gerçek güvenceye
bakıyor: alanın varsayılan kancası `rememberClipboardCopy(sensitive = true)`
olmalı ve sohbet metni (gizli değil) bu işareti taşımamalı.

## P2 — iptal edilen indirme satırı "indiriliyor"da asılı kalıyordu

WorkManager, iş uçtaki bir duruma geçince `progress`i **temizler**; iptal edilen
bir işin `outputData`'sı da boştur (worker sonuç döndürmeye fırsat bulamaz).
Gözlemci model kimliğini yalnız bu ikisinden okuduğu için `CANCELLED` durumunda
`null` alıp `return` ediyordu — satır arayüzde sonsuza kadar "indiriliyor"
kalıyor, kullanıcı ne iptali görüyor ne yeniden başlatabiliyordu.

Model kimliği artık işin **etiketine** yazılıyor (`model-download/model=<id>`);
etiketler iş ömrü boyunca değişmez, güvenilir kaynak onlar. Önek bilerek
benzersiz iş adınınkinden (`model-download:`) farklı ki iş adı etiket kümesine
sızarsa yanlışlıkla eşleşmesin.

İşin kimliği ve anahtarları saf, Android'siz bir `ModelDownloadJob`'a taşındı:
etiket biçimi gibi tamamen saf bir kural için birim testin WorkManager'a
uzanması gerekmiyor (`DownloadPreflight`, `FirstRunGuide`, `MobileTaskReducer`
de aynı ayrımı izliyor).

## P3 — bildirim izni hiç istenmiyordu

Model indirmesi WorkManager'a taşınırken manifest'e `POST_NOTIFICATIONS`
eklenmiş ama çalışma zamanında **hiçbir yerde istenmemişti**. Android 13'ten
beri bu izin varsayılan olarak reddedili; sonuç:

- `setForeground` sessizce başarısız oluyordu,
- kullanıcı 0,5–8,6 GB'lık bir indirme başlatıp **hiçbir ilerleme görmüyordu**,
- bildirimdeki "İptal" düğmesi hiç var olmadığı için indirmeyi durdurmanın tek
  yolu uygulamayı açık tutmaktı.

İndirmeyi arka plana taşımanın kazandırdığının yarısı kayıptı. İzin artık
"İndir"e basıldığı anda — yani bağlamında — isteniyor ve **beklenmiyor**:
reddedilse bile indirme sürer, yalnız bildirim çıkmaz.

## P4 — bildirimde "3.4 GB", Modeller ekranında "3,7 GB"

`ModelDownloadWorker.gb()` `Locale.ROOT` ile nokta üretiyordu; `sizeLabel`
virgül. Aynı model iki ekranda iki farklı ondalık ayırıcıyla görünüyordu.
Biçimlendirme cihaz diline bırakılmadı — arayüzün tamamı Türkçe, karışık
ayırıcı istemiyoruz.

## Araç sürümleri — AGP 9.2.1 → 9.4.0, Gradle 9.4.1 → 9.6.0

Bu **istenmiş bir değişiklik değildi**: Android Studio açılınca Upgrade
Assistant kendiliğinden uyguladı. Geri almayı denerken yalnız Gradle sarmalayıcı
geri alınabildi (çalışma dizini silme izni yok), AGP 9.4.0'da kaldı — AGP 9.4
en az Gradle 9.6.0 istediği için build "minimum supported Gradle version"
hatası verdi. **Hata bendendi.**

İkisi tek bir karar olduğundan tutarlı hâle getirildi ve — testler bu sürümlerde
de tamamen yeşil olduğu için — yükseltmede kalındı. Tuzak artık
`libs.versions.toml`'da yazılı: `agp` ve `gradle-wrapper` **birlikte** değişir.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **397/397 geçti** (3 sn 900 ms) |
| Toolchain | ✔ AGP 9.4.0 + Gradle 9.6.0 üzerinde yeşil |

389 → 397: 8 yeni test (2 pano guard'ı + 6 indirme etiketi). Guard testleri
42 → 44; biri düzeltilmiş sayılmıyor, **yanlış olduğu için yeniden yazıldı**.

**Dürüstlük notu:** P2, P3 ve P4 cihazda görsel olarak denenmedi — üçü de
gerçek bir model indirmesi (GB'larca) gerektiriyor. Mantık testlerle kilitli,
ama ilk gerçek indirmede bildirimin çıktığı ve iptal edilen satırın düzgün
döndüğü bir kez gözle görülmeli.

---

# Tur 9 — "bazı modeller çevrimdışı çalışmıyor"

Salih'in bildirdiği hata. Üç adımda arandı: katalog verisi → durum mantığı →
bağlam sınırları.

## Katalog doğrulandı — sorun orada değil

Kataloğun her satırı **elle girilmiş** bir boyut ve SHA-256 taşıyor.
`LocalModelStore.diskState`, dosyayı ancak `length() == spec.sizeBytes` ise
"kurulu" sayıyor; bu yüzden tek bir yanlış bayt sayısı o modeli **kalıcı olarak
"yarım indirme"** durumunda bırakır ve model asla çalışmaz. Tam olarak "bazı
modeller" belirtisi.

18 kaydın tamamı Hugging Face'e karşı, **kataloğun sabitlediği revizyonda**
kontrol edildi:

| Sonuç | Sayı |
|---|---|
| Boyut bayt bayt doğru | 16 |
| Kapılı — erişim reddedildi, kataloğun `gated = true` dediği ikisi | 2 |
| Uyuşmazlık | **0** |

**Bu arada bir yanlış alarm ürettim ve düzelttim:** ilk kontrolü `main` dalında
yaptım; `Qwen3.5-0.8B` (8 Eylül) ve `gemma-4-12B` (4 Eylül) o gün yeniden
yüklendiği için boyutlar farklı çıktı. Sabitlenmiş revizyonda ikisi de
kataloğunkiyle aynı. Katalog haklı, dal ilerlemiş. Sabitleme tam bunun için
var.

Ayrıca ilk ayrıştırıcım iki `BASE` sabitini satıra bölünmüş oldukları için
yanlış çözdü ve "revizyon eksik" gibi gösterdi — o da bendendi.

## P5 — doğrulanmamış model dosyası bir ÇIKMAZ yoluydu

Asıl neden. "Kurulu" tanımı **iki yerde farklı**:

- **Yönlendirme**: `EngineRouter.decide` ← `isInstalled` ← `diskState is
  Installed` — doğrulanmamış dosyayı **kabul eder**.
- **Üretim**: `LocalLlmController.generate` — `Installed && verified` ister,
  aksi hâlde reddeder.

Sonuç: doğrulanmamış bir dosya istemi yerel motora **yönlendirir**, motor yolu
onu **reddeder**. Çevrimdışı modda PC'ye devir kapalı olduğu için
(`allowsGatewayFallback = false`) istem hiçbir yere gitmez. Kullanıcı Modeller
sekmesine gidip "Doğrula"ya basmadıkça çevrimdışı sohbet ölü.

İşaretin eksik olması dosyanın bozuk olduğu anlamına **gelmez**. Bilinen iki
sebep, ikisinde de baytlar sağlam:

1. İndirme bitip `renameTo` olduktan sonra, işaret yazılmadan sürecin ölmesi.
2. **Diskin dolması** yüzünden işaretin yazılamaması — ki bu tam da GB'larca
   model indirilirken olur. `writeMarker` sonucu `runCatching` ile
   **yutuyordu**: indirme "başarılı" diyor, durum doğrulanmamış kalıyor.

**Düzeltme.** SHA-256 hesaplamak yerel bir iş; ağ istemez, yani çevrimdışıyken
de yapılabilir. Kullanıcıyı başka bir ekrandaki düğmeye yollamak yerine üretim
yolu artık dosyayı **orada doğruluyor**. Tutarsa devam eder; tutmazsa
`store.verify` dosyayı diskten siler (Y2 kararı) ve mesaj dürüst olur.
`writeMarker` da artık başarısını döndürüyor.

`LocalModelStore` `Context` yerine `filesDir` alıyor — çevrimdışı kullanımın
kalbindeki bu mantık böylece JVM'de gerçek dosyalarla test edilebiliyor. Daha
önce **hiç** test edilmiyordu.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **411/411 geçti** |
| Katalog ↔ HF (sabit revizyon) | ✔ 16/16 bayt bayt, 2 kapılı |

397 → 411: 12 yeni `LocalModelStoreTest` (durum geçişleri, doğrulama, silme) +
2 guard testi. Guard testleri 44 → 46.

## Açık şüphe — henüz KANITLANMADI

İki modelin dosya adı bağlam sınırını yazıyor: `granite-4.0-350m_q8_**ekv1280**`
ve `mobile_actions_q8_**ekv1024**`. Uygulama konuşmayı turlar arası canlı
tutuyor (KV önbelleği korunuyor) ve sistem istemi + çevrimdışı araç şeması
bunun üstüne biniyor. 1024–1280 tokenlık bir bütçe birkaç turda dolar; büyük
modellerde (ekv4096+) aynı sohbet sorunsuz sürer.

Bu, "bazı modeller" belirtisinin **alt kümeye özgü** ikinci adayı ve P5'ten
bağımsız. `LocalModelSpec`te bağlam uzunluğu alanı yok — uygulama bu tavanı
bilmiyor.

**Neden düzeltmiyorum:** diğer modellerin bağlam uzunluğunu bilmiyorum ve
tahmin edilen değer katalogda yer almaz (bu dosyanın kendi kuralı). Doğru yol
granite'i (468 MB, emülatöre iner) indirip birkaç tur konuşmak ve sınırı
gerçekten görmek. Görmeden sayı yazmayacağım.

---

# Tur 10 — release derlemesi: R8'in sessizce kıracağı şey

Release yapılandırması bugüne kadar **hiç derlenmedi**; Play ise yalnız onu
kabul ediyor. Debug'da `isMinifyEnabled = false`, release'de R8 + kaynak
küçültme açık. Yani "debug'da çalışıyor" bu konuda hiçbir şey söylemiyor.

Derlemeyi yerelde koşturamadım (aşağıda), o yüzden R8'in kırabileceği tek şeyi
statik olarak aradım: **ada göre çözülen her şey.** Uygulama kodunda yansıma
yok — `javaClass`, `Class.forName`, `simpleName` hiçbir yerde geçmiyor. İki yol
kaldı:

1. `object : MessageCallback` (LiteRT geri çağrısı) — arayüz zaten
   `-keep class com.google.ai.edge.litertlm.**` ile korunuyor, uygulayan sınıf
   yeniden adlandırılsa da metot adları arayüze bağlı kalır. **Güvenli.**
2. `OneTimeWorkRequestBuilder<ModelDownloadWorker>()` — **güvenli değil.**

## P6 — güncellemeden sonra ölen indirme

WorkManager, iş kuyruğa girerken worker'ın **tam sınıf adını** kendi
veritabanına yazar ve çalıştıracağı anda `Class.forName` ile çözer
(doğrulandı). R8'in ürettiği ad derlemeler arasında **sabit değil** ve
`proguard-rules.pro`'da bu sınıfı koruyan bir kural yoktu.

Sonuç: sürüm N'de kuyruğa girmiş yarım bir indirme, sürüm N+1'de artık var
olmayan bir ada bakar. Uygulama varsayılan `WorkerFactory`'yi kullandığı için
istisna yutulur ve iş "başarısız" işaretlenir — yani **8,6 GB'a kadar
çıkabilen yarım bir indirme, uygulama güncellenince sessizce ölür.** Özel bir
fabrika kullanılsaydı doğrudan `ClassNotFoundException` ile çökerdi.

Bu hata debug'da **hiç** görünmez: yalnız release'de, üstelik yalnız
**güncellemeden sonra** ortaya çıkar. Yani ilk yayında değil, ikinci yayında.

Düzeltme: yalnız o sınıfın adı ve WorkManager'ın yansımayla çağırdığı yapıcı
korunuyor. Kütüphane topluca açılmadı — dosyanın kendi kuralı bu.

## Doğrulama

| | Sonuç |
|---|---|
| Birim testleri | ✔ **412/412 geçti** |

411 → 412: proguard kuralını kilitleyen guard testi. Guard testleri 46 → 47.

**Kendi ayağıma sıktım:** dokümanlardaki test sayısını 411'e güncelledim, sonra
bu guard testini ekleyip 412 yaptım ve `docs-check`'i tekrar koşturmadım. CI ilk
push'ta tam bunu yakaladı — yani bu turda eklediğim tetikleyici düzeltmesi daha
ilk çalışmasında işini gördü.

**Yapamadığım:** release derlemesini yerelde koşturamadım. Android Studio bana
"click" seviyesinde veriliyor; koşum yapılandırması oluşturmak yazı gerektiriyor,
`.run/` dosyalarını IDE yeniden başlatmadan almadı, `Build → Assemble Project`
ise yalnız **aktif türü** (debug) derliyor — release'e hiç girmedi, `outputs/`
altında tek bir release çıktısı oluşmadı. Bu yüzden doğrulamayı CI'a taşıdım
(`android-release` işi): `lintRelease` + `bundleRelease` koşuyor ve lint raporu
ile R8'in `usage.txt`'sini artifact olarak yüklüyor. **Bir sonraki push cevabı
verecek.**

---

# Tur 11 — Faz 11A: telefondan devredilen işin izi yoktu (2026-09-10)

Play'e yükleme durduruldu; kullanıcı "biraz daha geliştirelim" dedi. Seçilen iş:
**Faz 11 — telefon ↔ PC görev devri.** Kod yazmadan önce mevcut durumu haritaladım
ve ilk bulgu inşa planını değiştirdi.

## P7 — Kod kendi hakkında yanlış konuşuyordu (gerçek, kritik)

`NovaViewModel.handoffToPcAgent()` KDoc'u şunu yazıyordu:

> *"Koşu, Gateway'in ajan geçmişine (/v1/agent/runs) otomatik kaydolur."*

**Kaydolmuyordu.** Zincir:

1. Telefon devirde `model = "openclaw/default"` gönderiyor ve `agenticForModel()`
   katalogdaki `tools: true` yüzünden `agent: true` ekliyor. *(Doğrulandı.)*
2. `gateway.mjs:592` ajan dalı: `if (agent && provider === "ollama" && …)`.
3. `lib/model_catalog.mjs:236` — `openclaw/default` → `provider: "openclaw"`.
4. Yani dal **atlanıyor**, istek `viaOpenClaw`'a düşüyor ve tek
   `recordRun(mode:"agent")` çağrısına (`gateway.mjs:647`) hiç ulaşılmıyor.
5. Uygulama da `/v1/agent/runs`'ı zaten hiç okumuyordu.

Sonuç: devir çalışıyor, yanıt sohbet balonuna geliyor, uygulama kapanınca **hiçbir
iz kalmıyor.** Ne sunucuda, ne telefonda.

**Yanlış yorum eksik yorumdan kötüdür.** Yorum olmasaydı bu boşluk ilk denemede
görülürdü. Yorum, olmayan bir güvence verdiği için kimse bakmadı — ben de bu turda
"ekrandan önce doğrula" demeseydim, varsayıma dayalı bir ekran yapacaktım.

### Düzeltme

**Gateway.** `agent_runs_store.mjs` içine saf `openclawRunFromCompletion()` eklendi;
`gateway.mjs` düz tamamlama yolunda çağırıyor. Kural bilerek **dar**: yalnız
`provider === "openclaw"`. `agent: true` gönderilmiş bir bulut modeli ajan koşumu
**değildir** — araç döngüsü hiç çalışmadı — ve onu geçmişe yazmak ilk yalanı
düzeltirken ikinci bir yalan üretirdi. Araç izi boş bırakılıyor: OpenClaw yanıtı düz
metin olarak röle ediliyor, gateway hangi araçların çağrıldığını **görmüyor**;
uydurmaktansa boş.

**Android.** `GET /v1/agent/runs` istemcisi + saf `parseAgentRuns`. Kontrol ekranına
"PC KOŞUMLARI" kartı; tüm sunum kararları `PcHandoffFeed` içinde (saf, JVM'de testli)
— Compose'a gömülseydi yalnız cihazda doğrulanabilirdi ve bu projede cihaz testleri
hiç koşmadı.

Kartın üç ayrı cümlesi var, çünkü üç ayrı durum: *okunuyor* / *okunamadı* /
*henüz koşum yok*. İkincisiyle üçüncüsünü aynı cümleye koymak kullanıcıya işini
kaybettiğini düşündürür. Aynı gerekçeyle ağ hatasında (`null`) eldeki liste
**silinmiyor**.

Yanlış KDoc, kodun gerçekte yaptığını anlatacak şekilde yeniden yazıldı ve neden
yanlış olduğu orada kayıtlı.

## P8 — Devir sonrası tazeleme yarışı (kendi eklediğim hatayı yakaladım)

İlk hâlde devir bitince (`finish()`) geçmişi bir kez tazeliyordum. Diff'i tekrar
okurken sıralamayı fark ettim: gateway koşum satırını **yanıt kapandıktan sonra**
yazıyor — akış `finish(res)` ile bitiyor, `recordRun` ondan sonra geliyor. Telefonun
`onDone` anındaki tek sorgusu, kendi az önce yarattığı satırı ıskalayabilir.
Kullanıcının göreceği şey: *"devrettim ama listede yok."*

İki taraftan birden kapatıldı: gateway'de kayıt artık `await` ediliyor (yanıt zaten
bittiği için istemciye gecikme eklemez ama satırın istek bitmeden yazılmasını
garantiler) ve telefon devirden sonra bir de 1,5 sn sonra soruyor. Guard testi ikisini
birden kilitliyor.

## Doğrulanan ön koşul

`agent_runs` tablosu ve `/v1/agent/runs` rotası **yalnız `MULTI_USER` modunda**
var (`gateway.mjs:187` — `DATABASE_URL` set **ve** `MULTI_USER !== "0"`). Bunu
varsaymadım: `docker-compose.yml:63` gateway servisine `DATABASE_URL`'i koşulsuz
veriyor ve `gateway/.env` `MULTI_USER`'ı kapatmıyor → yığın multi-user modunda.
Eski/tek-kullanıcı bir gateway'de kart "Geçmiş okunamadı" der; uygulama koşum
uydurmaz.

## Doğrulama

| | Sonuç |
|---|---|
| Gateway testleri | ✔ **229/229 geçti** (226 → 229, yerelde koşturuldu) |
| `docs-check` | ✔ geçti (429 birim + 122 enstrümanlı) |
| Saf Faz 11 mantığı | ✔ **30/30** — kotlinc 2.2.21 ile derlenip koşturuldu |
| Android birim testleri | ✔ **429/429 geçti** — CI (`android-unit`, 0867daf) |
| Android release derlemesi | ✔ `lintRelease` + `bundleRelease` geçti — CI (`android-release`) |

**Saf mantığı gerçekten koşturdum.** Bu turda VM'e JDK 21 kurdum ve `PcHandoffFeed`
+ `PcAgentRun`'ı projenin **tam** Kotlin sürümüyle (2.2.21) derleyip 30 iddiayı
çalıştırdım — göreli zaman eşikleri, sıralama, kesme, boş-durum cümleleri. Hepsi
geçti. Bu, "testleri yazdım" ile "testler geçiyor" arasındaki farkı kapatıyor.

**Yapamadığım:** tam Android derlemesi yerelde koşmuyor. Cihazdaki VM'in ağ izni
yalnız github.com'a açık — `services.gradle.org`, `dl.google.com` ve
`repo1.maven.org` 403 dönüyor; Android SDK ve Gradle 9.6.0 indirilemiyor. Compose
kartının ve istemcinin derlendiğini bu yüzden CI'a bıraktım — **ve CI cevap verdi:**
`0867daf` üzerinde altı işin altısı da yeşil (`android-unit` 429/429, `android-release`
lint + AAB). Bu, bu dalın **ilk tam yeşil koşusu**; bir önceki `a6a3938` kırmızıydı.

**Push'la ilgili gerçek bir sorun ortaya çıktı:** `e1176e7` ve `f877c6d` GitHub'a hiç
ulaşmamıştı. `git ls-remote` ile bakınca uzak dalın hâlâ `a6a3938`'de olduğu görüldü;
yani CI'ın bulduğu iki şeyi düzelten commit aylardır değil ama günlerdir yüklenmemiş
duruyordu ve GitHub'daki CI kırmızı kalmıştı. Ders: "push ettim" ile "uzakta var"
aynı şey değil — `git status`'ün "ahead" sayısı yalnız son fetch'e göre doğrudur,
kesin cevap `git ls-remote`'tadır.

Testler: 412 → 429 birim (13 yeni `PcHandoffFeedTest` + 4 yeni guard; guard 47 → 51).

---

# Tur 12 — Faz 11B: "gönderdim, ne oluyor?" sorusunun cevabı (2026-09-11)

Faz 11A devri **kaydedilir** yaptı. 11B onu **izlenebilir** yapıyor: iş PC'de
çalışırken kullanıcının ekranda gördüğü şey.

## Önce inşa etmedim, ölçtüm

Plan "canlı ilerleme (SSE)" diyordu. İkinci bir SSE kanalı yazmadan önce o
kanalın ne taşıyacağına baktım ve cevap şuydu: **hiçbir yeni şey.** Sohbet
akışının kendisi zaten SSE; devir sırasında metin canlı geliyor. Gateway'in
OpenClaw'dan aldığı şey de düz metinden ibaret (`viaOpenClaw` akıştaki
`delta.content` / `token` / `text` alanlarını çekip **geri kalan her şeyi
atıyor**). İkinci bir kanal, var olmayan veriyi taşımak için kurulmuş boş bir
makine olurdu.

Gerçek boşluk başka yerdeydi.

## P9 — Kontrol ekranı devir sırasında yanlış şey söylüyordu

`ActiveWorkCard` üç dal biliyordu: mobil görev / `chatBusy` / boş. PC devri
`chatBusy` dalına düşüyor ve ekranda şu yazıyordu:

> *"Sohbet yanıtı üretiliyor…"*

İş sohbette değil, **PC'de** çalışıyordu. Kullanıcının o anda sorduğu soru
"gönderdim, ne oluyor?" ve ekranda bunun cevabı yoktu: hangi iş, nereye gitti,
ne kadar oldu, PC aldı mı — hiçbiri.

**Düzeltme.** Süren devir `pcHandoff` ile ayrı bir durum olarak taşınıyor ve
Kontrol kartının İLK dalı oldu. Ekranda artık istemin kendisi, durum ve geçen
süre var. `responding` alanı iki durumu ayırıyor: *PC'ye gönderildi, çalışıyor*
ile *PC yanıt yazıyor*. Bu ayrım kullanıcının "takıldı mı?" sorusunun cevabı ve
ilk parça geldiğinde kendiliğinden geçiyor.

Süren iş ayrıca "PC KOŞUMLARI" kartının başında canlı satır olarak duruyor;
bitince aynı iş gerçek koşum satırına dönüşüyor. Başlık kuralı ikisinde de aynı
(`promptTitle`) — yoksa iş bitince ekrandaki metin sebepsiz değişirdi.

### Durdurma: yapabildiğimizi söylüyoruz, fazlasını değil

Düğme **"Dinlemeyi durdur"** diyor, "İptal et" değil. Telefon akışı kesiyor,
gateway de `res.on("close")` ile PC'ye giden isteği abort ediyor — ama
OpenClaw'ın isteği yarıda kesilince işi gerçekten bırakıp bırakmadığını gateway
**görmüyor**. Altındaki not bunu olduğu gibi yazıyor. Bir guard testi metnin
"İptal" demesini engelliyor.

## Araç adımı: geçiş var, uydurma yok

`viaOpenClaw` akıştaki her yapısal alanı atıyordu. Artık `openclawToolStep()`
(saf, testli) üst akıştan **gerçekten gelen** bir araç adımını telefona
geçiriyor. OpenClaw böyle bir olay göndermiyorsa hiçbir şey değişmez. Tanınmayan
her şey `null` — gateway adım **üretmez**. Testlerin yarısı tam olarak bunu,
"uydurmuyor mu?" sorusunu sınıyor.

## Yoldan çıkan bir kusur

Faz 11A'da `ControlScreen`'e `nowMillis: Long = System.currentTimeMillis()`
diye bir parametre koymuştum. Compose varsayılan argümanları **her yeniden
bileşimde** yeniden hesaplar; yani parametre hiçbir zaman kararlı olmuyor ve
gereksiz yeniden bileşim tetikliyordu. Varsayılan `0L` yapıldı (0 = gerçek
saat) ve saat `remember` içine alındı. Süren devir varken saniyede bir tik atan
efekt devir bitince iptal oluyor — boşta dönen sayaç bırakılmadı.

## Doğrulama

| | Sonuç |
|---|---|
| Gateway testleri | ✔ **231/231** (229 → 231, yerelde koşturuldu) |
| Saf Faz 11B mantığı | ✔ **13/13** — kotlinc 2.2.21 ile derlenip koşturuldu |
| `docs-check` | ✔ geçti (437 birim + 122 enstrümanlı) |
| Android birim + release | ⏳ CI (yerelde Android SDK indirilemiyor) |

Testler: 429 → 437 birim (5 yeni `PcHandoffFeedTest` + 3 yeni guard; guard 51 → 54),
gateway 229 → 231.
