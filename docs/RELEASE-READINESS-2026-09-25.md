# Yayın hazırlığı — 25 Eylül 2026

Durum: yerel test adayları hazır; genel yayın kapıları henüz kapanmadı.
23 Eylül kaydı tarihsel kanıttır; aşağıdaki bilgiler güncel durumu belirtir.

## Bu turda tamamlananlar

- Çalışan Docker gateway güvenli bind düzeltmesini içeren imaja geçirildi.
  Konteyner içi dinleyici `0.0.0.0`; `/health` 200, kimliksiz `/v1/models` 401.
- Yerel Ollama bağlantısı düzeltildi. Gateway'in erişebildiği
  `host.docker.internal:11434` kullanıldı; kök, Git dışındaki `.env` dosyasında
  yalnız `OLLAMA_URL` değişti. Firewall/portproxy ayarları değiştirilmedi.
- Gerçek yerel `ollama/nemotron-mini:latest` modeli ile kimlik doğrulamalı
  sohbet ve SSE akışı geçti. Geçici kullanıcı, API anahtarı ve ilgili veriler
  test sonunda silindi. Bu kontrol Keycloak girişini veya worker akışını kapsamaz.
- Ayarlar penceresi native dialog oldu: arka plan modal iken etkileşimsiz,
  Tab/Shift+Tab odağı içeride, Escape kapatır ve odak açan düğmeye döner.
- Production kontrolünde `MULTI_USER=0` iken yalnız `DATABASE_URL` bulunmasının
  kimlik doğrulama sayılması düzeltildi. Negatif ve pozitif regresyonlar eklendi.
- Android UI testleri güncel kullanıcı akışlarıyla eşlendi: ekran dışındaki
  kontrole kaydırma, sohbet silme onayı, bağlantı hazırken devir onayı,
  çevrimdışıyken devir yasağı, önerilen modelin tek gösterimi ve maskelenmiş
  `EditableText` doğrulaması. Compose'un ham `InputText` test verisi, ekranda
  görünür metinle karıştırılmıyor; parola semantiği korunuyor.
- Android'de İşler sekmesi mevcut `PHONE_TASKS_TAB_ENABLED=false` kararı gereği
  ilk yayında kapalı. Bu turda telefon otomasyonu açılmadı.
- CI web adayını test/audit/smoke kapılarından sonra; Android imzasız AAB'yi
  release derlemesinden sonra yükler. Browser başarısızlık kanıtları saklanır.

## Doğrulama kanıtı

| Kontrol | Sonuç |
|---|---|
| Gateway | 240 test geçti; security komutu içinde yeniden koşuldu |
| Web / yardımcı testler | 22 / 34 geçti |
| Web Playwright | 7/7; mobil composer, erişilebilir adlar, modal klavye akışı |
| Güvenlik komutu | Syntax, secret scan, yapılandırma, iki npm audit ve web build geçti |
| Canlı gateway + Ollama | Kimliksiz 401; kimlikli models/chat/stream 200; SSE içerik ve bitiş işareti doğrulandı |
| Android JVM / lint | 453/453; debug ve release lint 0 hata, 24 uyarı, 5 ipucu |
| Android instrumentation | Tam koşu: 117/122 geçti; 3 UI hatası düzeltildi, ilgili gruplar ayrı tekrar koşularında 9/9 + 3/3 + 3/3 geçti. İki canlı bağlantı testi açık |
| Üretim preflight | Başarısız: zayıf Postgres parolası ve geliştirme adresleri içeren CSP |

Kanıtlar `audit-screenshots/` altında: `security-2026-09-25.log`,
`tests-2026-09-25.log`, `e2e-2026-09-25.log`, `android-final-2026-09-25.log`;
`release-2026-09-25/live-check.log` ve `preflight.log`.
Worker 35/35 sonucu 23 Eylül'e aittir; bu turda yeniden çalıştırılmadı.

## Aday paketler

Git dışındaki `releases/2026-09-25/` klasörü:

- `nova-web-candidate.zip`: güncel web production build.
- `nova-android-debug-test-only.apk`: yalnız cihaz testi için debug APK.
- `nova-android-release-unsigned.aab`: mağazaya doğrudan gönderilemez; upload imzası eksik.
- `SHA256SUMS.txt`: paket bütünlüğü için SHA-256 değerleri.

Android paketleri 23 Eylül'de başarılı derlenen, bu turda üretim kodu değişmeyen
adaydan alınmıştır. Web paketi 25 Eylül build'idir. Kaynak çalışma ağacı henüz
commit/tag ile sabitlenmedi; bunlar resmi sürüm değil yerel inceleme adaylarıdır.

## Genel yayın için kalan kapılar

1. Yayın hedefi/domain ve kullanıcı kapsamını belirle; TLS, CORS ve CSP'yi bu
   hedefe göre doğrula. Mevcut CSP yerel geliştirme adresleri içeriyor.
2. Veritabanı parolasını kalıcı volume ve tüm istemcilerle birlikte, yedekleme
   ve geri dönüş planıyla döndür. Yalnız `.env` değiştirmek mevcut DB parolasını
   değiştirmez. Admin ve model allowlist uyarılarını yayın kapsamına göre kapat.
3. Android upload keystore/signing ve fiziksel cihaz release testi tamamlanmalı.
   Emülatör bağlantı kayıpları uygulama çökmesi olarak sınıflandırılmadı.
4. Gerçek Keycloak giriş/yenileme, worker görevi/risk onayı ve RAG uçtan uca
   doğrulanmalı. Ollama sohbetinin geçmesi bunların geçtiği anlamına gelmez.
5. Web soğuk yükleme/gerçek mobil performansı ölçülmeli; yaklaşık 663 kB chunk
   uyarısı hâlâ var. İlk kurulum, teşhis, görsel ekleme ve çevrimdışı ses
   kabul senaryoları ayrıca tamamlanmalı.

Bu turda dış ortama yayın veya mağaza yüklemesi yapılmadı.

## Android koşusunun kapsamı

Pixel 9 / Android 16 emülatörü `swiftshader_indirect` ile, testleri çalıştıran
shell ömrü boyunca açık tutulduğunda tüm koşuyu tamamladı. Önceki ADB kopmalarının
kesin kök nedeni kanıtlanmadı. Tam koşunun XML kanıtı
`audit-screenshots/release-2026-09-25/instrumentation-first-complete/` altında.

İki `LiveGatewayOllamaConnectionTest` testi `runLiveGateway=true` verilmediği
için `AssumptionViolatedException` üretti; mevcut raporlayıcı bunu başarısızlık
olarak kaydetti. Canlı testler standart UI kapsamından açıkça ayrılabilir:

```powershell
cd nova-android
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.nova.agent.LiveGatewayOllamaConnectionTest'
```

Canlı Android testleri için erişilebilir gateway adresi, test kullanıcısı ve
`runLiveGateway=true` ayrıca sağlanmalıdır. Docker içinden geçen model smoke'u
emülatörün ağ erişimini doğrulamaz.

Düzeltme sonrası üç grubun XML raporları aynı kanıt klasöründeki
`instrumentation-models-fixed`, `instrumentation-NovaAppSettingsSyncTest-fixed`
ve `instrumentation-NovaSecretFieldTest-fixed` dizinlerindedir. Böylece 120
farklı standart UI testinin başarılı sonucu var; son düzeltmelerden sonra
122 testin tamamı tek koşuda yeniden çalıştırılmadı. İki canlı Android testinin
başarı kanıtı yok. Test için açılan emülatör kapatıldı.

Son `docs-check` ve `git diff --check` başarılı. Aday paketlerin SHA-256
değerleri yeniden doğrulandı; web arşivinde `.env` veya keystore dosyası yok.
