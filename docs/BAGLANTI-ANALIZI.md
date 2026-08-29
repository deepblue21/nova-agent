# Telefon ↔ PC Bağlantısı: VPN'siz Çözüm Analizi

Tarih: 2026-08-08 · Kapsam: Project Horus gateway + nova-android

---

## 1. Bugün ne var (koddan çıkarılan durum)

| Katman | Gerçek durum |
|---|---|
| Gateway API | Express, `/health` + `/v1/*` + `/stt` + `/tts`. Uzun ömürlü **SSE** (`routes/mobile_tasks.mjs`) ile görev olayları. |
| Kimlik | `Bearer nv_<6hex>_<secret>`, sadece SHA-256 hash saklanıyor (`lib/keys.mjs`), sabit-zamanlı karşılaştırma. Alternatif OIDC JWT. **Sağlam.** |
| Taşıma | Düz HTTP. Caddy TLS'i yalnız gerçek alan adı varsa çözüyor; LAN'da `http://` kullanılıyor. |
| Android istemci | OkHttp + `okhttp-sse`. Tek yapılandırma: `baseUrl` + `token` (DataStore). Ağ hatası katmanına göre sınıflandırılıyor — iyi iş. |
| Bağlama | `start-horus.ps1` `GATEWAY_BIND`'i `0.0.0.0` yapıyor, Windows Güvenlik Duvarı'nı alt ağ veya `100.64.0.0/10` (tailnet) ile daraltıyor. |

**Kritik gözlem:** İstemci tarafı tamamen "bir base URL'e HTTP at" varsayımı üzerine kurulu. Bu bir sorun değil — tam tersine, **en büyük şans**. Taşıma katmanını altından değiştirebilirsen SSE, model listesi, görev akışı, hata sınıflandırıcı dahil hiçbir şeyi yeniden yazmana gerek kalmaz.

---

## 2. Projeyi ayağa kaldırmak için gerçekte gerekenler

**PC tarafı (WSL2 Ubuntu / Windows):**

1. Docker Desktop + WSL2 arka ucu
2. Node ≥ 20.19 (kök `package.json` `engines`)
3. `docker compose up -d --build postgres redis migrate gateway caddy`
4. `docker compose exec gateway node scripts/bootstrap-user.mjs <email> 5` → tek seferlik `nv_...` anahtarı
5. En az bir LLM arkası: Ollama köprüsü (11500) veya bulut sağlayıcı anahtarı `gateway/.env` içinde

**Android tarafı:** JDK 17 + Android SDK 35, `gradlew.bat test lintDebug assembleDebug`. Derleme 31.07.2026'da yeşil (151 test).

**Kapanmamış tek şey:** fiziksel ARM64 cihazda uçtan uca doğrulama.

**Ayağa kaldırmayı gerçekte zorlaştıran şey teknik değil — bağlantı adımı.** Kullanıcıya "PowerShell aç, script çalıştır, güvenlik duvarı kuralı yaz, IP kopyala, telefona iki alan gir, dışarıdaysan ayrıca Tailscale kur ve iki cihazda da giriş yap" demek, ürünü teknik olmayan herkes için burada bitiriyor. Haklısın.

### Yol boyunca çıkan iki ayrı bulgu

- `AndroidManifest.xml` içinde **global** `usesCleartextTraffic="true"` var. LAN HTTP'si için konmuş ama tüm uygulamaya açık kapı bırakıyor. `network_security_config.xml` ile yalnız `127.0.0.1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` ve `*.local` için daraltılmalı.
- `.env` dosyaları `.gitignore`'da doğru şekilde hariç, yalnız `.env.example`'lar izleniyor. Burası temiz.

---

## 3. Neden Tailscale son kullanıcı için duvar

- İki cihaza ayrı uygulama kurulumu + aynı hesapla giriş
- Android'de kalıcı VPN profili → "bu uygulama ağ trafiğinizi izleyebilir" sistem uyarısı, pil endişesi
- Kurumsal telefonlarda MDM çoğu zaman VPN profilini engelliyor
- Kavramsal yük: kullanıcı "tailnet", "100.x adresi", "MagicDNS" öğrenmek zorunda
- Kullanıcı adına *üçüncü bir hesap* daha açılıyor

Bunların hiçbiri Tailscale'in hatası değil — Tailscale bir ağ ürünü, senin ürünün ise bir asistan. Ağ kurulumu kullanıcının problemi olmamalı.

---

## 4. Seçenek matrisi

Ölçütler: **Sürtünme** (son kullanıcının atması gereken adım), **Gizlilik** (aradaki taraf istemi düz metin görüyor mu), **Maliyet**, **CGNAT/mobil veri** (Türkiye'de operatörlerin çoğu CGNAT — gelen bağlantı imkânsız).

| # | Yaklaşım | Sürtünme | Gizlilik | Maliyet | CGNAT | Karar |
|---|---|---|---|---|---|---|
| A | **mDNS/NSD keşif + QR eşleme** (aynı Wi-Fi) | **Sıfır** — tarat, bitti | Trafik ağdan çıkmıyor | 0 | Gerekmiyor | ✅ Katman 0, hemen yap |
| B | **iroh (QUIC delik açma + relay yedeği)** | Bir kez QR | **Uçtan uca şifreli**; relay şifreli paketi kör iletir | 0 (n0 genel relay) / ~€4 ay (kendi relay'in) | ✅ Çözer | ✅ Katman 1, ana çözüm |
| C | Cloudflare Tunnel (adlı tünel) | Alan adı + CF hesabı + `cloudflared` | ❌ CF TLS'i sonlandırır, istemi görebilir | Alan adı bedeli | ✅ | ⚠️ Belgelenmiş kaçış yolu |
| D | Cloudflare Quick Tunnel (`trycloudflare`) | Sıfır | ❌ Aynı sorun + herkese açık URL | 0 | ✅ | ⚠️ Yalnız demo — 200 eşzamanlı istek tavanı, her yeniden başlatmada URL değişir, SLA yok |
| E | Kendi VPS'inde `rathole` / `frp` / `bore` | VPS kirala, iki tarafta config | Kendi sunucun — iyi | ~€4,35/ay (Hetzner CX22) | ✅ | ⚠️ Güçlü kullanıcı yolu |
| F | Router'da port yönlendirme + DDNS | Çok yüksek | Gateway doğrudan internete açık | 0 | ❌ CGNAT'ta çalışmaz | ❌ Önerme |
| G | Kendi yazdığın WebSocket relay'i | Sıfır | Sen operatör olursun → sorumluluk | Sunucu + bakım | ✅ | ❌ B varken gereksiz |

**Neden F ayrıca tehlikeli:** Ocak 2026'da internet taramaları **175.000 kimlik doğrulamasız Ollama sunucusu** buldu; sebep hack değil, sahiplerinin `0.0.0.0`'a bağlayıp güvenlik duvarını unutmasıydı. Üstüne CVE-2026-7482 ("Bleeding Llama", CVSS 9.1) kimlik doğrulamasız bellek okumasına izin verdi. Ürünün varsayılanı asla "gateway'i internete aç" olmamalı.

---

## 5. Önerilen tasarım: üç katmanlı, kullanıcı hiçbirini bilmiyor

```
Katman 0 — Aynı Wi-Fi          telefon --mDNS keşif--> PC          (en hızlı, ağdan çıkmaz)
Katman 1 — Doğrudan P2P        telefon --QUIC delik açma--> PC     (%~90 başarı, uçtan uca şifreli)
Katman 2 — Relay yedeği        telefon --> relay (kör) --> PC      (simetrik NAT / kurumsal ağ)
```

Uygulama üç katmanı **otomatik** dener ve durum çubuğunda yalnız sonucu gösterir: `Yerel ağ · 4 ms` / `Doğrudan · 38 ms` / `Aktarmalı · 120 ms`. Kullanıcı hiçbir zaman IP, port, tünel veya VPN kelimesini görmez.

### Kilit mimari kararı: uygulama içinde loopback proxy

Android tarafında iroh'u OkHttp'nin altına gömmek yerine, **uygulama içinde 127.0.0.1'de dinleyen küçük bir HTTP proxy** çalıştır. OkHttp `http://127.0.0.1:<port>/v1`'e konuşur; proxy isteği iroh QUIC akışına sarar.

Sonuç: `GatewayConnectionClient`, SSE tüketicisi, model kataloğu ayrıştırıcısı, hata sınıflandırıcı — **hiçbiri değişmez**. Testlerin aynen geçer.

PC tarafında simetrik olarak: iroh uç noktası gelen akışı `http://127.0.0.1:8088`'e proxy'ler. `gateway.mjs` değişmez, `GATEWAY_BIND` **kalıcı olarak `127.0.0.1`'de kalır** — yani güvenlik duvarı kuralı yazmaya, `0.0.0.0`'a bağlamaya hiç gerek kalmaz. Bu, bugünkü kurulumdan *daha güvenli*.

### Neden iroh

- **1.0 sürümü Haziran 2026'da çıktı** — kablo protokolü kararlılık garantisiyle
- **Resmî Kotlin bindingi** (Android) ve **Node.js bindingi** var — tam olarak senin iki tarafın
- Delik açma QUIC içinde (`n0_nat_traversal`); üretimde ~%90 doğrudan bağlantı
- Relay sunucuları **durumsuz ve kör** — şifreli paketi çözmeden iletiyor. Bu, "veri varsayılan olarak cihazda" ilkenle uyumlu; Cloudflare seçeneğinin sağlayamadığı şey.
- Relay **self-host edilebilir**. Başlangıçta n0'ın genel (rate-limited) relay'lerini kullan; ciddileşince €4/ay bir kutuya kendi relay'ini koy.
- Adresleme **IP değil açık anahtar** — QR'a IP yazmıyorsun, düğüm kimliği yazıyorsun. Ev IP'si değişse, kullanıcı 4G'ye geçse bağlantı kendini yeniden kuruyor.

**Dürüst maliyet:** Bu, projeye native (Rust FFI) bağımlılık sokar. Android APK'sı 4 ABI için büyür (~+3-5 MB/ABI), Docker imajı UDP için `network_mode: host` ister ya da sidecar Windows'ta native koşar. Bu bedeli bilerek ödemelisin.

### Eşleme akışı (hedef UX)

```
PC:       .\scripts\start-horus.ps1        →  ekranda tek bir QR
Telefon:  Ayarlar → "PC'yi bağla" → tara   →  bitti
```

QR içeriği: `horus://pair?node=<iroh-node-id>&k=<tek-kullanımlık-eşleme-anahtarı>&name=<PC-adı>`

Tek kullanımlık anahtar 5 dakika geçerli; telefon bunu kalıcı `nv_` anahtarıyla takas eder. Kullanıcı hiçbir şey yazmaz, kopyalamaz.

### İki bağımsız güvenlik faktörü

1. **Düğüm kimliği allowlist'i** — PC yalnız eşlenmiş açık anahtarlardan gelen akışı kabul eder. Eşlenmemiş bir cihaz TLS el sıkışmasını bile tamamlayamaz.
2. **Mevcut `nv_` API anahtarı** — HTTP katmanında aynen kalır.

Yani biri sızsa bile tek başına yetmez. `0.0.0.0` + güvenlik duvarı kuralı modelinden yapısal olarak üstün.

---

## 6. Faz planı

### Faz A — LAN'da sıfır yapılandırma (1-2 gün, en yüksek getiri/emek oranı)

Ev kullanımı gerçek trafiğin büyük çoğunluğu; bu faz tek başına şikâyetin yarısını çözer.

- `gateway`: NSD/DNS-SD yayını `_horus._tcp.local` (Node tarafında `bonjour-service`, ya da Caddy/host tarafında)
- `nova-android`: `NsdManager` ile keşif → bulunan PC'ler listesi → dokun, bağlan
- QR eşleme ekranı (CameraX + ML Kit barcode; salt-cihaz, ağ gerekmez)
- `network_security_config.xml` → global `usesCleartextTraffic` kaldırılır, yalnız özel aralıklar + loopback
- `start-horus.ps1` çıktısına QR ekle

**Kabul:** Temiz bir telefonda, PC'de tek script çalıştırıldıktan sonra tek dokunuşla bağlanma; hiçbir alana elle yazı girilmemesi.

### Faz B — iroh taşıma katmanı (1-2 hafta)

- `connect/` altında yeni bileşen: `horus-connect` — PC'de iroh uç noktası ⇄ `127.0.0.1:8088` proxy'si
- Android'de `IrohTransport` + loopback HTTP proxy; `baseUrl` bunu işaret eder
- Eşlenmiş düğüm kimliği allowlist'i (PC'de kalıcı, Ayarlar'dan cihaz iptali)
- Bağlantı durumu göstergesi (Yerel / Doğrudan / Aktarmalı + gecikme)
- SSE dayanıklılığı: kopmada `Last-Event-ID` ile yeniden bağlanma. **Zaten yeniden oynatılabilir olay akışın var** — bu işi kolaylaştırıyor.
- Pil: uzun SSE için ön plan servisi ya da arka plana düşünce yoklamaya (polling) geçiş

**Kabul:** Telefon mobil veride (CGNAT arkasında), PC ev Wi-Fi'sinde; VPN kurulu değil; sohbet akıyor, görev SSE'si kopmadan geliyor.

### Faz C — Belgeleme ve kaçış yolları (2-3 gün)

- `DEPLOY.md`: Cloudflare Tunnel (kendi alan adıyla) ve `rathole`/`frp` (kendi VPS'inle) yollarını **güçlü kullanıcı seçeneği** olarak yaz; Cloudflare'ın TLS'i sonlandırdığını ve istemleri teorik olarak görebileceğini açıkça belirt
- Tailscale bölümünü silme — "zaten tailnet'in varsa hâlâ çalışır" olarak koru
- Kendi iroh relay'ini kurma rehberi (€4/ay kutu)

---

## 7. Karşılaştırma: bugünkü akış vs. hedef akış

| Adım | Bugün (Tailscale) | Hedef |
|---|---|---|
| PC | Script çalıştır, güvenlik duvarı kuralı, Tailscale kur + giriş | Script çalıştır |
| Telefon | Tailscale kur + giriş + VPN profili onayla, Base URL yaz, anahtar yapıştır | QR tara |
| Ev IP'si değişince | Genelde sorunsuz | Sorunsuz |
| Kurumsal telefon | MDM engelliyor olabilir | Çalışır (giden 443/UDP) |
| Gateway bağlanması | `0.0.0.0` | `127.0.0.1` (daha güvenli) |
| Kullanıcının öğrendiği kavram | tailnet, 100.x, MagicDNS | Yok |

---

## 8. Kararın nerede

Tek gerçek tercih Faz B'nin taşıma katmanı:

- **iroh** — en iyi UX + gizlilik, karşılığında native bağımlılık ve APK boyutu
- **Kendi relay'in (rathole/frp)** — bağımlılık yok, karşılığında kullanıcı VPS kiralamalı
- **Cloudflare Tunnel** — orta sürtünme, ama üçüncü taraf istemleri görebilir → projenin gizlilik ilkesiyle çelişiyor

Faz A her durumda yapılmalı; hangi taşımayı seçersen seç değeri değişmiyor.

---

## 9. Faz A uygulama durumu (2026-08-08)

### Yazıldı ve doğrulandı

| Bileşen | Dosya | Doğrulama |
|---|---|---|
| Eşleme kodu çekirdeği | `gateway/lib/pairing.mjs` | 14 birim testi ✅ |
| Eşleme deposu (atomik tek kullanım) | `gateway/lib/pairing_store.mjs` | `UPDATE ... WHERE claimed_at IS NULL` ile DB seviyesinde |
| Takas uç noktası | `gateway/routes/pairing.mjs` | 12 birim testi ✅ (sahte depo + supertest) |
| Şema | `gateway/migrations/011_pairing.sql` | — |
| QR üretici | `gateway/scripts/pair.mjs` | sözdizimi + ESM/QR render ✅ |
| mDNS tel biçimi | `scripts/lib/mdns.mjs` | 23 birim testi ✅ |
| mDNS yayıncısı | `scripts/announce-mdns.mjs` | gerçek soketle 4 uçtan uca test ✅ |
| Android eşleme ayrıştırıcı | `nova-android/.../net/Pairing.kt` | 18 test ✅ |
| Android keşif çözümleme | `.../net/GatewayDiscovery.kt` | 12 test ✅ |
| Şifresiz bağlantı politikası | `.../net/NetworkPolicy.kt` | 13 test ✅ |
| Eşleme yanıt çözümleme | `.../net/PairingResponses.kt` | 11 test ✅ |
| NSD kabuğu | `.../net/NsdGatewayDiscovery.kt` | Android API — sandbox'ta koşulamaz |
| Eşleme HTTP kabuğu | `.../net/PairingClient.kt` | OkHttp — Gradle derlemesinde doğrulanacak |

**Toplam:** gateway 226/226 test geçiyor (26'sı eşleme), kök `scripts` 34/34, Android
saf çekirdek 54/54. (Gateway ve scripts paketleri 2026-08-09'da yeniden koşuldu ve
yeşil; Android tarafı derleyici doğrulaması bekliyor.)

**En değerli doğrulama — diferansiyel test:** JS `parsePairUri` ile Kotlin `Pairing.parsePairUri`, 307 üretilmiş vaka (6 host tipi × tls × 6 port × 5 ad + 7 bilinçli bozuk girdi) üzerinde **sıfır farkla** aynı sonucu verdi. İki taraf ayrışırsa eşleme sessizce bozulurdu; bu test onu yakalar.

### Yeni akış

```
PC:       .\scripts\start-horus.ps1 -Lan
          → mDNS yayını açılır (arka plan işi "horus-mdns")
          → terminalde QR + 8 karakterlik kod basılır
Telefon:  Ayarlar → PC'yi bağla → listeden PC'yi seç → kodu gir
```

Gateway artık `GATEWAY_BIND=127.0.0.1` kalabilecek duruma yaklaştı; bu fazda hâlâ LAN'a bağlanıyor ama eşleme adımı IP/anahtar yazımını ortadan kaldırdı.

### Tasarım kararları ve gerekçeleri

- **API anahtarı `pair.mjs`'te üretilmiyor.** Kod takas edildiğinde üretiliyor. Terminal ekran görüntüsü sızsa bile kod tüketildikten ya da 5 dk geçtikten sonra değersiz. "Yalnız hash saklanır" değişmezi korunuyor.
- **Kod alfabesi Crockford Base32** (I, L, O, U yok). Elle yazımda 0/O ve 1/I/L karışması normalizasyonda düzeltiliyor — iki tarafta da aynı şekilde.
- **mDNS bağımlılıksız yazıldı.** Kök `package.json`'da hiç `dependencies` yoktu; bu değişmezi bozmamak için responder `node:dgram` üzerine kuruldu. Kapsamı dürüstçe sınırlı: IPv4, yalnız yanıtlayıcı, çakışma sondalaması yok.
- **mDNS host'ta koşuyor, container'da değil.** Docker bridge ağı multicast'i LAN'a geçirmiyor; container içinden yayın yapılsa telefon duymazdı.
- **Cleartext kontrolü manifest'te değil kodda.** Android'in `network_security_config`'i CIDR ifade edemiyor (`192.168.0.0/16` yazılamıyor). `NetworkPolicy` şifresiz bağlantıya yalnız özel/yerel adreslerde izin veriyor; genel bir IP'ye http engelleniyor.
- **`qrcode-terminal` seçildi** (1 paket, 196 KB, geçişli bağımlılık yok, Apache-2.0). `qrcode` 30 paket / 2,8 MB getiriyordu — güvenlik odaklı bir imaja gereksiz yüzey.

### Faz A'da kalanlar

1. **Ayarlar arayüzü** — keşfedilen PC listesi + kod giriş alanı + `PairingClient` bağlantısı (`SettingsPanel.kt`). Bugünkü manuel Base URL/Token alanları yedek olarak kalmalı.
2. **QR kamera tarama** — CameraX + ML Kit. Bu tamamen konfor; keşif + 8 karakterlik kod onsuz da çalışıyor. Kamera izni ve ~5 MB APK artışı getiriyor, o yüzden ayrı bir adım.
3. **Cihazda uçtan uca doğrulama** — gerçek telefon + gerçek Wi-Fi. Multicast birleşmesi (`addMembership`) sandbox'ta LAN arayüzü olmadığı için doğrulanamadı; ilk gerçek koşuda kontrol edilmeli.
4. **`docs-check`** sayaçlarının yeni test sayılarıyla güncellenmesi.

---

---

## 10. Sıradaki tur: uygulama planı (2026-08-09 doğrulaması)

Faz 9'da (Basit/Gelişmiş mod + çevrimdışı motor) yapılan iş bu planı doğrudan
etkiliyor, ve bu bölüm yazılırken üç şey yeniden doğrulandı.

### 10.1 Doğrulanan yeni bilgiler

| Bulgu | Sonuç |
|---|---|
| **`computer.iroh:iroh-android` Maven Central'da yayında (1.1.0)** — ABI başına hazır `libiroh_ffi.so` + JNI ilklendirmesi için `IrohAndroid` | Rust/NDK derleme zinciri kurmaya **gerek yok**. Faz B'nin en pahalı sanılan kısmı ortadan kalktı. |
| **Android'de resmî destek yalnız `aarch64` + `armv7`** | **x86_64 emülatörde iroh yok.** Faz B, emülatörde test edilemez; doğrulama fiziksel telefon ister. Bu, bugünkü emülatör-öncelikli akışın kırılacağı yer. |
| PC tarafı için **resmî Node.js bindingi (N-API)**, Windows x64 ve Linux x64 destekli | Gateway zaten Node. Ayrı bir Rust sidecar'ı **gerekmiyor**; `connect/` bileşeni düz Node olabilir. |

Analizin 5. bölümündeki "native bağımlılık ve APK boyutu bedeli" hâlâ geçerli,
ama "iki tarafta da Rust derlemek" bedeli geçerli değil.

### 10.2 Faz A — bitirilecekler (kod yazıldı, arayüz bağlanmadı)

`pairing.mjs`, `pairing_store.mjs`, `routes/pairing.mjs`, `scripts/pair.mjs`,
`announce-mdns.mjs`, `Pairing.kt`, `GatewayDiscovery.kt`, `NsdGatewayDiscovery.kt`,
`PairingClient.kt`, `NetworkPolicy.kt` **yazıldı ve testli** (diferansiyel test
dahil). Eksik olan tek şey bunları ekrana bağlamak:

1. **Ayarlar > PC bağlantısı**: keşfedilen PC listesi (NsdManager) + 8 karakterlik
   kod alanı + `PairingClient` çağrısı.
2. Eşleme başarılıysa dönen `baseUrl` + `nv_` anahtarı `saveConnection` ile
   yazılır — bugünkü elle giriş yolu Gelişmiş modda **yedek olarak kalır**.
3. `start-horus.ps1` çıktısındaki QR + kod zaten basılıyor; değişiklik gerekmez.
4. Cihazda multicast doğrulaması (`addMembership` sandbox'ta koşulamadı).

**Faz 9 ile kesişim — önemli:** eşleme akışı `SettingsSection.CONNECTION`
altındadır ve **Basit modda görünür**. Elle Base URL/belirteç girme
`SettingsSection.CONNECTION_MANUAL` altındadır ve Gelişmiş'te kalır. Yani mod
sistemi bu işi zaten doğru yere koydu: yeni kullanıcı tarar/kod girer, güçlü
kullanıcı elle yazar. Faz A bittiğinde Basit moddaki "Kayıtlı PC bağlantısı
kullanılıyor…" metni, yerini keşif listesine bırakır.

### 10.3 Faz B — iroh taşıma katmanı

Mimari karar değişmedi (5. bölüm): **uygulama içi loopback proxy**. OkHttp
`http://127.0.0.1:<port>` ile konuşur, proxy isteği iroh QUIC akışına sarar;
`GatewayConnectionClient`, SSE tüketicisi, hata sınıflandırıcı **hiç değişmez**.

Yeni bilgilerle güncellenen sıra:

1. `connect/` — Node tabanlı iroh uç noktası ⇄ `127.0.0.1:8088` proxy'si.
   `GATEWAY_BIND` kalıcı olarak `127.0.0.1`'e döner; güvenlik duvarı kuralı ve
   `0.0.0.0` bağlanması ortadan kalkar (bugünkünden **daha** güvenli).
2. Android'de `computer.iroh:iroh-android` + loopback proxy.
3. Eşlenmiş düğüm kimliği allowlist'i (PC'de kalıcı, Ayarlar'dan cihaz iptali).
4. Bağlantı durumu göstergesi: `Yerel · 4 ms` / `Doğrudan · 38 ms` /
   `Aktarmalı · 120 ms`. Faz 9'daki "çalışan backend" rozetiyle aynı ilke —
   tahmin değil, ölçülen değer.
5. SSE dayanıklılığı: kopmada `Last-Event-ID` ile devam (olay akışı zaten
   yeniden oynatılabilir).

**Kabul edilmesi gereken bedeller (dürüst liste):**

- APK, arm64 + armv7 için büyür. **x86_64 ABI'sinde iroh yoktur** → emülatörde
  taşıma katmanı çalışmaz. Uygulama bu durumu taklit etmemeli: emülatörde
  "Doğrudan bağlantı bu cihazda kullanılamıyor, yerel ağ/elle adres kullanın"
  demeli. Aksi halde Faz 9'daki "desteklenmeyeni taklit etme" ilkesi kırılır.
- Uzun ömürlü SSE + P2P bağlantı, pil için ön plan servisi ya da arka planda
  yoklamaya geçiş ister.
- iroh uç noktası UDP kullanır; Docker'da `network_mode: host` ya da host'ta
  çalışan sidecar gerekir (mDNS yayıncısında verilen kararın aynısı).

### 10.4 Önerilen sıra

Faz A önce ve tek başına yapılmalı: ev kullanımının çoğunu tek başına çözüyor,
emülatörde test edilebiliyor ve Faz B'nin hangi taşımayı seçtiğinden bağımsız
olarak değerini koruyor. Faz B'ye ancak Faz A gerçek bir telefonda
doğrulandıktan sonra girilmeli.

---

## Kaynaklar

- [What is iroh?](https://docs.iroh.computer/what-is-iroh) · [Iroh 1.0 — Dial Keys, not IPs](https://www.iroh.computer/blog/v1) · [iroh-ffi Kotlin README](https://github.com/n0-computer/iroh-ffi/blob/main/README.kotlin.md) · [hello-iroh-ffi (Android örneği)](https://github.com/n0-computer/hello-iroh-ffi)
- **Faz B doğrulaması (2026-08-09):** [iroh dil desteği + platform tablosu](https://docs.iroh.computer/languages) (Android = yalnız aarch64/armv7) · [`computer.iroh:iroh-android` Maven Central](https://central.sonatype.com/artifact/computer.iroh/iroh-android) · [`computer.iroh:iroh`](https://central.sonatype.com/artifact/computer.iroh/iroh)
- [Cloudflare Quick Tunnels dokümanı](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)
- [FRP vs. Rathole vs. ngrok karşılaştırması](https://xtom.com/blog/frp-rathole-ngrok-comparison-best-reverse-tunneling-solution/) · [Cloudflare Tunnels açık kaynak alternatifleri 2026](https://ossalt.com/guides/best-open-source-alternatives-cloudflare-tunnels-2026)
- [Android Network Service Discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd) · [Android mDNS .local çözümlemesi](https://www.esper.io/blog/android-dessert-bites-26-mdns-local-47912385)
- [175.000 açık Ollama sunucusu (The Hacker News)](https://thehackernews.com/2026/01/researchers-find-175000-publicly.html) · [Shodan ile açık LLM sunucusu tespiti (Cisco)](https://blogs.cisco.com/security/detecting-exposed-llm-servers-shodan-case-study-on-ollama)
