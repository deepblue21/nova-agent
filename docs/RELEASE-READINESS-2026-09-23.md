# Yayın hazırlığı — 23 Eylül 2026

Durum: kritik kod düzeltmeleri doğrulandı; genel yayın onayı verilmedi. Bu kayıt,
eski denetimlerdeki test ve lint sonuçlarının yerine güncel kanıt olarak kullanılmalı.

## Tamamlanan düzeltmeler

- **Gateway / fixed:** `gateway/gateway.mjs` tüm arayüzlerde dinliyordu ve geliştirme
  kipinde boş token kimlik doğrulamayı kaldırıyordu. Dinleyici artık `GATEWAY_BIND`
  değerini kullanır; boş/değişken yoksa `127.0.0.1` olur. Kimlik doğrulamasız çalışma
  yalnız `127.0.0.1` veya `::1` üzerinde mümkündür. Diğer adreslerde token veya etkin
  çok kullanıcılı doğrulama gerekir. Host adları loopback kabul edilmez.
- Dockerfile ve Compose, konteyner içi dinleyiciyi açıkça `0.0.0.0` olarak ayarlar.
  Kök `.env` içindeki `GATEWAY_BIND` yalnız host port yayınına aittir; böylece Caddy,
  Kubernetes ve Docker ağ bağlantıları korunur. Kullanıcının gerçek `.env` dosyaları
  değiştirilmedi. Çalışan gateway konteyneri bu yeni imaja geçirilmedi.
- **Android:** NSD kayıt temizliği, kayıt oluşturma ile aynı API 34 kontrolüne alındı.
  API 34 öncesindeki çözümleme yolu korunur; lint bastırılmadı. API dayanağı:
  [Android NsdManager](https://developer.android.com/reference/android/net/nsd/NsdManager#unregisterServiceInfoCallback(android.net.nsd.NsdManager.ServiceInfoCallback)).
- **Mobil web:** boş sohbet karşılama bölümü gerektiğinde kayar; composer ve alt
  kontroller küçülüp birbirini örtmez. Sabit 640px minimum kök yükseklik kaldırıldı.
  390×844'te textarea merkezindeki öğe düzeltme öncesinde “Düşünme” düğmesi,
  sonrasında textarea olarak ölçüldü.
- Mesaj alanına erişilebilir ad, gizli anahtar alanlarına gerçek `label` ilişkisi
  ve açıklama bağlantısı eklendi. Anahtar gösterildiğinde erişilebilir adı korunur.
- CI debug lint ve altı Playwright regresyon testini çalıştırır. Release lint artık
  `continue-on-error` ile geçiştirilemez; rapor yükleme adımı başarısızlıkta da çalışır.

## Çalıştırılan doğrulamalar

| Kontrol | Sonuç |
|---|---|
| `npm test` | Gateway 239/239, web 22/22, yardımcı/smoke 34/34 |
| `node --test gateway/test/listener.test.mjs` | 6/6; eski kodda 6 test başarısız, düzeltmeyle başarılı |
| `npm --prefix web run test:e2e` | 6/6; 390×844, 320×568, 390×480, 768×1024, 1440×900 ve gizli alan etiketleri |
| Worker, Python 3.12 | 35/35 |
| Android `testDebugUnitTest` | 453 test, 0 hata/başarısızlık |
| Android `lintDebug`, `lintRelease` | Her biri 0 hata, 24 uyarı, 5 ipucu |
| Android `assembleDebug`, `bundleRelease` | Başarılı; mağaza imzası/yayın kabulü doğrulanmadı |
| `npm run security` | Syntax, gateway testleri, secret scan, statik yapılandırma, iki npm audit, web build geçti |
| `npm run docs-check`, `git diff --check` | Başarılı |
| `docker compose config --quiet` | Başarılı |
| `docker build -t nova-release-check:local gateway` | Başarılı |
| Yeni imaj, `--network none` ile geçici konteyner | Dinleyici `0.0.0.0`, health 200, tokensız models 401 |

Gateway regresyonları gerçek uygulama soketini gözlemler: varsayılan IPv4 loopback,
açık IPv6 loopback, token ile IPv4 wildcard ve üç kimlik doğrulamasız dış bind
denemesi. Token ile models isteği 200, tokensız istek 401 verdi. Ayrı salt okunur
güvenlik incelemesinde somut bypass veya uyumluluk regresyonu bulunmadı.

Web testleri production build üzerinde çalışır; gerçek sağlayıcı isteklerini keser.
Bu nedenle bunlar LLM uçtan uca testleri değildir. Görünürlük yanında `elementFromPoint`,
çok satırlı yazma, Tab odağı, alt gezinmenin ekranda kalması ve yatay taşma sınanır.

Yerel loglar `audit-screenshots/` altında: `lint-before.log`, `android-after.log`,
`tests-after.log`, `web-e2e.log`, `security-after.log`, `docker-build-after.log`.
Android raporları `nova-android/app/build/reports/` altında. Önceki ekran görüntüleri
korundu; eski görseller düzeltme sonrası kanıt olarak sunulmamalı.

## Açık yayın engelleri ve sonraki çalışma

1. **Ollama bağlantısı:** mevcut Docker servislerinde gateway, Postgres ve Redis
   sağlıklı; gateway içinden yapılandırılmış Ollama `/api/tags` isteği
   `UND_ERR_SOCKET / other side closed` ile başarısız. Köprü/servis yolu düzeltilip
   gerçek yerel modelle yanıt ve akış doğrulanmalı. Ağ/firewall ayarları değiştirilmedi.
2. **Emülatör kararlılığı:** güncel debug APK kuruldu, `am start -W` başarılı oldu
   (soğuk açılış 2682ms), uygulama PID'si alındı ve o anda crash buffer boştu.
   Takip eden UI ağacı/ekran görüntüsü çağrısında emülatör ADB'den tamamen kayboldu.
   Uygulama çökmesi kanıtlanmış değildir. Görsel akış ve instrumentation testi
   hâlâ bloke; kararlı emülatör veya fiziksel cihazda yeniden yapılmalı.
3. **Gerçek uçtan uca doğrulama:** Keycloak oturumu, worker görevi, risk onayı,
   telefon–PC devir, RAG ve gerçek LLM akışı henüz bu değişikliklerle doğrulanmadı.
   Geçici Docker testi yalnız yeni imajın bind/auth uyumluluğunu kanıtlar.
4. **Performans:** web build başarılı; 662,67 kB parça uyarısı sürüyor. Ana giriş
   parçası yaklaşık 367 kB; Mermaid kaynakta dinamik yükleniyor. Uyarı tek başına
   ilk yükleme süresi değildir. Üretim sunumunda soğuk yükleme, yavaş ağ ve gerçek
   cihaz ölçümleri yapılmadan performans tamamlandı sayılmamalı.
5. **Kurulum deneyimi:** mevcut `scripts/start-horus.ps1` üzerine servis bazında
   teşhis ve bağlantı sihirbazı kabul senaryoları hazırlanmalı. Temiz kurulumda
   bağımlılık yok, port dolu, yanlış token, Ollama kapalı ve tekrar başlatma
   senaryoları gerçek ortamda geçmeli.
6. **Ürün ve cihaz kapsamı:** bilgi tabanı/workspace/hafıza/otomasyon gezinmesi,
   Android görsel ekleme, çevrimdışı ses rehberi ve modal odak yönetimi ayrı
   teslimatlar olarak ele alınmalı. Bu turda bunların tamamlandığı iddia edilmez.
7. **Dağıtım:** Android lint uyarılarının triyajı, fiziksel cihazda release davranışı,
   doğru mağaza imzası, TLS/domain ve production yapılandırması yayın öncesi kapıdır.

İlk worker denemesi sistem Python 3.13'ünde `httpx` eksikliğiyle başarısız oldu;
projenin desteklediği Python 3.12 ortamında bağımlılıklar hazırlanarak 35/35 geçti.
Yerel shell korumaları nedeniyle Gradle/Docker/ADB ve bağımlılık kurulumları uygun
çalıştırma izniyle tekrarlandı; başarısız ilk çağrılar başarı kanıtı olarak sayılmadı.
