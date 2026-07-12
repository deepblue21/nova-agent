# NOVA Agent

[English](./README.md) · **Türkçe**

Birçok LLM sağlayıcısıyla **tek bir uç nokta** üzerinden konuşmak için sesli + sohbet
arayüzü. Tarayıcı arayüzü (React), OpenAI-tarzı istekleri küçük bir Node **gateway**'ine
yollar; gateway bunları Anthropic, Google Gemini, OpenAI, yerel **Ollama** veya bir
**OpenClaw** ajanına yönlendirir — ayrıca konuşma→metin ve metin→konuşma proxy'ler.

```
┌────────────────────┐        OpenAI-tarzı /v1/chat/completions        ┌─────────────────────────┐
│   web/ (Vite +     │  ───────────────────────────────────────────▶  │  gateway/ (Node/Express)│
│   React arayüz)    │     /stt  /tts  (ses)                           │  auth · CORS · limit    │
└────────────────────┘                                                 └────────────┬────────────┘
                                                                                     │ <provider>/<model> ile yönlendirir
                                          ┌──────────────────────────────────────────┼───────────────────────────┐
                                          ▼                ▼                ▼          ▼               ▼            ▼
                                     Anthropic         Gemini           OpenAI      Ollama        OpenClaw   Whisper/TTS
```

## Yerel kullanım durumu

Bu repo public yapılırsa insanlar projeyi **kendi bilgisayarlarında yerel olarak**
çalıştırabilir. Paylaşım hedefi **local-first / self-hosted geliştirme deneyimi**dir —
merkezi çalışan bir production servisi değil. Aşağıdaki production/güvenlik notları,
gateway'i yanlışlıkla internete açmanı önlemek içindir.

### Minimum gereksinimler

| Kullanım tipi | Minimum | Not |
| --- | --- | --- |
| Bare-metal deneme | Git, Node.js 20.19+, npm, modern tarayıcı | Sadece `gateway` + `web`; tek kullanıcı/dev için en hızlı yol. |
| Model erişimi | En az bir provider API key'i **veya** yerel Ollama | OpenAI/Anthropic/Gemini key'i girilebilir; yerelde Ollama önerilir. |
| Docker tam stack | Docker Engine + Docker Compose; Windows'ta WSL Ubuntu | Postgres, Redis, Keycloak, MinIO, SearXNG, Grafana, ses servisleri. |
| Donanım | 8 GB RAM minimum; 16 GB önerilir | Yerel LLM'de GPU/VRAM ve model boyutu performansı belirler. |
| Portlar | Giriş proxy'si için `80`/`443` | İç paneller yalnız `127.0.0.1`'e bağlanır (bkz. Güvenlik). |

### Public repo kontrol listesi

- MIT lisanslı; kullanıcılar kullanabilir, değiştirebilir ve dağıtabilir. Lisans + telif bildirimini koru.
- Gerçek `.env`, API key, token, admin parolası veya kullanıcı verisi asla commit etme.
- Yerel varsayılan parolaların yalnızca dev amaçlı olduğunu açık tut; [`SECURITY.md`](./SECURITY.md)
  listesini uygulamadan dışarı açma.
- `gateway/.env.example` güncel kalsın; gerçek değerler kullanıcı tarafından kendi makinesinde üretilir.
- Hosted/production servis kapsam dışıdır. İnternete açmak ayrıca TLS, domain, secret rotasyonu,
  `ALLOW_ORIGINS`, Keycloak production modu ve kapalı iç portlar gerektirir.

## Hızlı kurulum

İki yol: **Docker (önerilen — tam stack)** veya **bare-metal (sadece gateway + web)**.

### Yol A — Docker tam stack (Postgres, Redis, Keycloak, SearXNG, MinIO, Grafana, ses)

Docker + docker compose ve yerel LLM için [Ollama](https://ollama.com) gerekir.

1. Repoyu klonla.

```bash
git clone <repo-url> nova && cd nova
```

2. Gateway `.env` dosyasını oluştur. Token üret ve `gateway/.env` içinde
   `ALLOW_ORIGINS` ile en az bir provider key veya `OLLAMA_URL` ayarla.

```bash
cp gateway/.env.example gateway/.env
node -e "console.log('GATEWAY_TOKEN='+require('crypto').randomBytes(32).toString('hex'))" >> gateway/.env
```

3. Web arayüzünü derle. Caddy `web/dist` klasörünü servis eder.

```bash
npm --prefix web ci && npm --prefix web run build
```

4. Yerel LLM için Ollama'yı container'lara aç ve modelleri çek. WSL/Linux'ta önce
   Ollama'yı `OLLAMA_HOST=0.0.0.0` ile çalıştır.

```bash
ollama pull qwen3:14b && ollama pull gemma4:e2b && ollama pull nomic-embed-text
```

Makinen kaldırıyorsa en güçlü yerel tool-calling modeli:

```bash
ollama pull qwen3.6:35b
```

Görsel kullanım için tag kuruluma göre değişir:

```bash
ollama pull qwen3.5-omni:latest
```

5. Stack'i kaldır. İlk açılışta imajlar iner ve birkaç dakika sürebilir.

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build
```

6. Multi-user modu için ilk kullanıcıyı ve API key'i oluştur.

```bash
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 5
```

Aç: **http://localhost**, **Ayarlar** bölümüne gir, sonra Keycloak admin içinde kullanıcı
oluşturduysan Keycloak ile giriş yap veya oluşturulan API key'i yapıştır.
İç paneller varsayılan olarak loopback-only'dir (host makinesinden erişilir): Grafana
`127.0.0.1:3001`, Prometheus `:9090`, MinIO `:9001`, SearXNG `:8080`, Keycloak `:8081`.

> Önemli: tüm varsayılan parolalar **yalnızca yerel geliştirme içindir.** Public'e
> açmadan önce **[`SECURITY.md`](./SECURITY.md)**'i oku ve hepsini değiştir. Windows +
> WSL kurulumu: **[`WSL_DOCKER.md`](./WSL_DOCKER.md)**.

### Yol B — Bare-metal (tek kullanıcılı gateway + web, DB yok)

Bağımlılıkları kur ve gateway env dosyasını oluştur:

```bash
git clone <repo-url> nova && cd nova
npm run install:all
cp gateway/.env.example gateway/.env
```

`gateway/.env` içinde `GATEWAY_TOKEN` ve bir provider key ya da Ollama ayarı yap.
Sonra gateway'i bir terminalde, web arayüzünü başka terminalde başlat:

```bash
npm run gateway
```

```bash
npm run web
```

Ajan modu (web araması + belgelerle sohbet) ve çok kullanıcı/geçmiş/kota için **Yol A** gerekir.

## Proje durumu ve yol haritası

Public, çok kullanıcılı dağıtıma taşıma çalışması sürüyor. Güncel durum ve sıradaki adım için
[`PROGRESS.md`](./PROGRESS.md). İlgili dokümanlar:

- [`PROGRESS.md`](./PROGRESS.md) — güncel durum + sıradaki adım (buradan başla)
- [`NOVA_Mimari_Inceleme.md`](./NOVA_Mimari_Inceleme.md) — tam mimari inceleme, as-is/to-be, faz haritası
- [`DEPLOY.md`](./DEPLOY.md) — Faz 0 deploy runbook'u (Docker + Caddy/TLS + CI)
- [`PHASE1.md`](./PHASE1.md) — Faz 1 (çok kullanıcı: auth, geçmiş, kota)
- [`PHASE2.md`](./PHASE2.md) — Faz 2 (gözlemlenebilirlik, object storage, billing, K8s/HPA, ses)
- [`nova-android/README.md`](./nova-android/README.md) — Android istemci

### Project Horus: Android-oncelikli mobil ajan

Project Horus, bu depodaki aktif Android-oncelikli gelistirme hattidir. Yerel PC/GPU
inference icin Nova Gateway'i, gorev kontrol yuzu olarak Android istemciyi kullanir.
Bu bolum canli teslimat kaydidir: Her tamamlanan Horus gorevinden sonra hem dogrulanmis
yapilanlar hem de siradaki somut is burada guncellenir.

**Tamamlanan ve dogrulananlar**

- Gateway'de kalici ve kimligi dogrulanmis mobil gorevler, SSE tekrar oynatma,
  duraklat/devam et/iptal ve R2/R3 onay kayitlari.
- Android **Gorevler** alani: gorev olusturma, timeline replay, kontroller ve risk onay
  arayuzu; unit, lint, APK ve emulator Compose kontrolleri gecti.
- API anahtarini yazdirmadan gorev olusturan, okuyan, duraklatan, devam ettiren, iptal eden
  ve event'leri replay eden Docker mobil kontrol duzlemi smoke testi.
- Debug APK: `nova-android/app/build/outputs/apk/debug/app-debug.apk`.
- Ayrilmis worker-goal politikasi ve yalnizca worker icin kimlik dogrulamasi tamamlandi.
- Gateway worker lease'leri yalnizca token hash'i ile kalici. Odakli anlamsal store testleri hem
  durum hem de rapor islemlerinde bilinmeyen gorevi (`404`), eksik, bayat, aktif olmayan veya yanlis
  lease'e sahip gecerli gorevden (`409`) ayirir; event'ler ve kalici kayitlar lease token'ini icermez.
- Yalnizca worker'a ait Gateway kontrol router'lari yerel `.env` yukleyicisinden sonra factory ile
  olusturulur; sonra temel middleware'den sonra ve kullanici-principal kimlik dogrulamasindan once
  mount edilir. Ayrilmis worker bearer auth claim, status, report ve expiry endpoint'lerini korur;
  Task 4 icin gereken tek kullanimlik opak `lease.token` yalnizca claim yanitinda doner, status/report
  yanitlarinda asla yer almaz.
- Dogrulanmis Gorev 3 duzeltmesi: worker bearer auth ve statik guvenli `500` siniri yalnizca
  `/v1/internal/mobile-worker` kapsamina alindi; boylece public `/health` ve siradan Gateway
  rotalari, worker iki moddayken de mount edilen router'dan gecer ve beklenmeyen store hata ayrintilari sizmaz.
- Gorev 4: izole `mobile-worker` paketi `mobilerun==0.6.10` ve `httpx` surumlerini kilitler;
  yalnizca `emulator-5554` ve yerel Ollama kabul eder, worker token'ini redakte eder, lease
  header'larini worker HTTP sinirinda tutar ve sadece sinirli guvenli raporlar ile loglar uretir.
  Hazirlik, ajan isi ve her rapordan once aktif lease yeniden denetlenir; ortak izlenen-gorev yolu
  duraklatma, iptal veya lease kaybi kazandiginda hazirligi ya da calistirmayi iptal edip bekler.
  Ozel Mobilerun ping'i sinirli surede sonlanir ve process temizlenir, ekran goruntusu akisi zorla
  kapatilir, Ollama HTTP timeout'lari `waiting_for_compute` olur ve rapor phase/error degerleri
  Gateway allowlist'lerine gore yerelde dogrulanir. `uv lock --check` ve standart kutuphane worker
  test paketi gecer. Canli emulator, Portal, Gateway ve yerel Ollama entegrasyonu kasitli olarak
  Gorev 6-7'ye ertelenmistir; siradaki is Gorev 5 olarak kalir.

**Devam eden is**

- Gorev 5: Android Gorevler ekraninda worker durumunu goster.

**Siradaki isler**

1. Android Gorevler ekraninda worker durumunu goster.
2. Yerel worker'i erisilebilir yap ve WSL ADB'yi hazirla.
3. Portal'i kur ve `emulator-5554` uzerinde guvenli Ayarlar/surum akisini kanitla.

Ayrintili uygulama sirasi:
[`docs/superpowers/plans/2026-07-11-mobilerun-emulator-worker.md`](./docs/superpowers/plans/2026-07-11-mobilerun-emulator-worker.md).

### Faz panosu

| Faz | Durum | Öne çıkanlar |
| --- | --- | --- |
| -1 — İnceleme | Tamam | Mimari rapor, as-is/to-be diyagramları, risk + faz haritası. |
| 0 — Gönderilebilir taban | Tamam | Dockerfile, Caddy/TLS, production token guard, CI iskeleti, deploy runbook. |
| 1 — Çok kullanıcı | Tamam | Postgres/Redis, OIDC/JWT, API key, dağıtık rate limit, kalıcı geçmiş, kota. |
| 2 — Ölçek + ürün | Temel tamam | Prometheus `/metrics`, pino log, object storage `/v1/media`, Stripe billing, K8s/HPA, ses. |
| 3 — Ajan + RAG | Tamam | Tool/function calling, SearXNG web araması, `doc_search`, pgvector RAG, kaynak rozetleri. |
| 4A — Artifacts/export | Tamam | HTML/SVG/Mermaid önizleme, artifact indirme, Markdown/JSON/PDF export, yerel paylaşım linki. |
| 4B — Araç/belge/persona | Tamam | PDF/DOCX çıkarımı, opt-in QuickJS `code_run`, persona/prompt kütüphanesi. |
| 4C — Çok modlu + ses kuyruğu | Tamam | `auto` görsel yönlendirme + `VISION_MODEL`, opt-in remote görsel, BullMQ ses job'ları. |
| 4D — Repo hijyeni + güvenlik | Tamam | Kişisel izler temizlendi, CSP/JWT/media/admin sertleştirme, temiz git history. |
| 5A — CI & güvenlik otomasyonu | Tamam | CI Node 20.19+22 matris, secret scan, npm audit, canlı smoke; tek komut `npm run security`. |
| 5B — Gözlemlenebilirlik + sertleştirme | Tamam | Prometheus ajan metrikleri, opt-in OTLP trace, Grafana auto-provisioning, hata webhook, `npm run prod-check`. |
| 6 — İleri ürün + Android | Tamam | Zamanlanmış görevler, team mode + canlı ilerleme, kişisel hafıza, model eval, PWA, MCP, Aurora UI redesign, workspace + RBAC, Android Gradle wrapper paketlendi. |
| 7 — İşbirliği (paylaşımlı kaynaklar) | Temel tamam | Workspace-kapsamlı bilgi tabanı, hafıza ve zamanlanmış görevler (yazma = editör/admin). Opsiyonel: paylaşımlı sohbetler. |
| 8 — Üretim sertleştirme + deploy + ajan derinleştirme | Kod/config tamam; runtime smoke bekliyor | Güçlendirilmiş `prod-check` + preflight, loopback-bound paneller, `docker-compose.prod.yml`, Caddy `/health` proxy, Keycloak public host rotası, production `CSP_CONNECT_SRC`. Ajan derinleştirme: MCP araç görünürlüğü, opt-in SSRF-korumalı `fetch_url`, ajan çalışma geçmişi, Ollama `think`/effort passthrough, scheduled runner fix, hava durumu edge testleri. Bekleyen: canlı WSL/Ollama/Docker smoke. |
| 9 — Public release handoff + first-run reliability | Başlamadı | Faz 8 smoke geçtikten sonraki aday: public-local kurulum polish, ilk çalıştırma diagnostikleri, net release checklist ve kullanıcı troubleshooting. |

### Son değişiklikler

Özet changelog; oturum-bazlı tam detay git history ve `PROGRESS.md`'de.

- **Faz 8 (sertleştirme + ajan):** güçlendirilmiş `npm run prod-check` (token gücü,
  varsayılan/zayıf altyapı secret'ları, multi-user DB/admin, cleartext MCP); gateway prod'da
  çok kısa `GATEWAY_TOKEN` ile başlamayı reddeder; iç paneller yalnız `127.0.0.1`'e bağlanır;
  `docker-compose.prod.yml` overlay'i (Keycloak `start` modu, zorunlu-secret guard'ları).
  Ajan derinleştirme: `GET /v1/mcp/tools` görünürlüğü + UI, opt-in `fetch_url` web sayfası
  okuyucu (SSRF-korumalı), ajan çalışma geçmişi (`/v1/agent/runs` + UI).
- **Son Faz 8 kapanışı:** Ollama agent/team çağrıları artık `think` ve effort ayarlarını
  (`temperature`, `top_p`, `num_predict`) gateway üzerinden gerçekten geçiriyor; zamanlanmış
  görevlerde `agent:false` doğrudan provider chat yolunu kullanıyor; hava durumu fallback'i
  ve eksik-veri yolları negatif/edge testlerle korunuyor; Caddy artık `/health` proxy ediyor;
  Keycloak public auth host'u `KC_HOSTNAME_HOST` ile compose'a bağlandı ve `prod-check`
  tarafından doğrulanıyor.
  **Neden:** bunlar dokümante edilen davranış ile gerçek deploy yolu arasındaki runtime
  boşluklardı. Kapatılmaları sessiz model-yönlendirme hatalarını, zamanlanmış görevlerin
  yanlış execution path'e girmesini, hava çıktısında bozulmayı, public health check kırılmasını
  ve yarım ayarlanmış auth subdomain riskini önler.
  Production CSP `CSP_CONNECT_SRC` ile daraltıldı: base compose local/dev origin'leri
  korur, production overlay `connect-src 'self'` varsayar ve `prod-check` wildcard/local
  dev origin görürse hard fail verir.
  İzlenen Keycloak realm artık varsayılan kullanıcı/parola import etmiyor; public web
  client'ta password grant kapalı, giriş PKCE browser flow üzerinden kalıyor.
  **Doğrulandı:** gateway testleri `99/99`, web testleri `3/3`, `npm run security`,
  production compose config ve güçlü-env `prod-check` geçti. Kalan iş: Faz 8'i kapatmadan
  önce canlı WSL/Ollama/Docker smoke.
- **Sonraki faz adayı:** Faz 9 public release handoff + first-run reliability. Gerçek
  Docker/Ollama/WSL stack üzerinde Faz 8 live smoke geçmeden başlanmayacak.
- **Faz 7 (işbirliği):** `documents`/`user_memory`/`scheduled_tasks` `workspace_id` aldı;
  listeleme/arama kişisel + üye workspace'leri kapsar; yazma işlemleri yazma rolü ister.
- **Faz 6:** kullanım panelinde model başına maliyet, canlı team-mode ilerleme akışı, kişisel
  uzun-dönem hafıza, model eval/kıyas, PWA, MCP entegrasyonu, Aurora UI redesign, workspace +
  3-rol RBAC, Android Gradle wrapper.

### Donanım notu (yerel LLM performansı)

**8 GB VRAM** (örn. RTX 3070) GPU'da, Q4_K_M'de 14B model (~10 GB) tam sığmaz ve Ollama
modeli CPU+GPU böler — bu yüzden cevaplar daha yavaştır; bu bir donanım sınırıdır, NOVA
sorunu değil. Tam-GPU hız için 8B sınıfı bir model seç; daha fazla yetenek için bölünmeyi
kabul et. Yerleşimi `ollama ps` ile doğrula (`PROCESSOR` sütunu: `100% GPU` vs bölünmüş).

| Model | Boyut | Yerleşim | Hız |
|---|---|---|---|
| `qwen3:8b` | ~5.2 GB | **%100 GPU** | hızlı |
| `qwen3.6:35b` | ~24 GB | yüksek VRAM/RAM veya bölünme gerekir | en güçlü yerel tool-calling |
| `qwen3:14b` | ~10 GB | bölünmüş CPU/GPU | orta, dengeli tool-calling |
| `gemma4:e2b` | ~7.2 GB | **%100 GPU** | en hızlı native tools |

## Depo yapısı

```
Nova_Agent_AI/
├── package.json
├── docker-compose.yml
├── docker-compose.faz2.yml
├── docker-compose.prod.yml
├── gateway/
│   ├── gateway.mjs
│   ├── lib/
│   ├── routes/
│   ├── migrations/
│   └── .env.example
├── web/
│   └── src/nova-agent.jsx
└── nova-android/
```

| Yol | Amaç |
| --- | --- |
| `package.json` | Her iki paketi süren yardımcı script'ler. |
| `docker-compose.yml` | Temel stack: Postgres, Redis, gateway, Caddy. |
| `docker-compose.faz2.yml` | Faz-2 eklentileri: Keycloak, MinIO, SearXNG, Prometheus, Grafana, ses. |
| `docker-compose.prod.yml` | Production sertleştirme overlay'i. |
| `gateway/` | API gateway; Node runtime, build adımı yok. |
| `gateway/gateway.mjs` | Sertleştirilmiş sunucu: auth, CORS allowlist, rate limit. |
| `gateway/lib/` | Ajan döngüsü, MCP, RAG, memory, RBAC, prodcheck ve yardımcılar. |
| `gateway/routes/` | Knowledge, memory, scheduled tasks, workspaces, agent runs ve API route'ları. |
| `gateway/migrations/` | SQL migration'lar `001` ile `008` arası. |
| `gateway/.env.example` | `.env` olarak kopyalanır ve makineye göre doldurulur. |
| `web/` | Vite, React ve PWA destekli tarayıcı arayüzü. |
| `web/src/nova-agent.jsx` | Ana tarayıcı UI bileşeni. |
| `nova-android/` | Kotlin ve Compose ile native Android istemci. |

## Önkoşullar

- **Node.js 20.19 veya üzeri** (`node -v`). Web build Vite 8 kullanır (`^20.19.0 || >=22.12.0`).
- En az bir model backend'i:
  - **Yerel, key'siz:** `http://localhost:11434`'te [Ollama](https://ollama.com).
  - **Bulut:** Anthropic, Gemini ve/veya OpenAI için API key.
- (Opsiyonel) Gerçek ses modu için OpenAI-uyumlu **Whisper** (STT) ve **TTS** sunucuları.

### Production için derleme

Build komutu statik dosyaları `web/dist/` içine üretir; preview bu build'i yerelde
`http://localhost:4173` adresinde servis eder.

```bash
npm run build
npm run preview
```

`web/dist/`'i herhangi bir statik host'tan servis et, gateway'i arkasında çalışır tut.

## Model yönlendirme nasıl çalışır

Model string'ini `"<provider>/<model>"` olarak yolla:

| Örnek | Gider |
| --- | --- |
| `ollama/qwen3.6:35b` | yerel Ollama |
| `ollama/qwen3:14b` | yerel Ollama |
| `gemini/gemini-2.5-flash` | Google Gemini |
| `anthropic/claude-sonnet-4-20250514` | Anthropic |
| `openai/gpt-4o-mini` | OpenAI |
| `openclaw/<agent>` | OpenClaw ajan katmanı |
| `auto` | dinamik seçim (aşağı bak) |

**Dinamik yönlendirme (`auto`)** isteğin `effort`'una (`fast`/`balanced`/`deep`/`max`) ve
toplam bağlam uzunluğuna göre seçer, mevcut provider key'lerini tercih eder. Her kademeyi
`.env`'de `ROUTE_FAST` / `ROUTE_BALANCED` / `ROUTE_DEEP` / `ROUTE_MAX` ile override et.

## Uç noktalar (gateway)

| Metod | Yol | Amaç | Auth |
| --- | --- | --- | --- |
| `GET` | `/health` | canlılık probu | **public** |
| `GET` | `/v1/models` | sunulan model id'leri | token |
| `POST` | `/v1/chat/completions` | OpenAI-uyumlu sohbet (SSE); `agent`/`team` modu, oto hafıza | token |
| `POST` | `/v1/eval` | bir promptu birden çok modele koşar; çıktı + gecikme/token/maliyet | token |
| `GET/POST/DELETE` | `/v1/memory` | kişisel uzun-dönem hafıza notları (sistem prompt'una oto-hatırlama) | token |
| `GET/POST/PATCH/DELETE` | `/v1/workspaces[...]` | workspace + RBAC (admin/editör/izleyici) + üye yönetimi | token |
| `GET/DELETE` | `/v1/agent/runs` | ajan/team çalışma geçmişi | token |
| `GET` | `/v1/mcp/tools` | yapılandırılan MCP sunucuları + keşfedilen araçlar | token |
| `GET/POST/PATCH/DELETE` | `/v1/scheduled` | zamanlanmış/otomatik ajan görevleri | token |
| `GET/POST/DELETE` | `/v1/knowledge` | RAG bilgi tabanı (yükle/listele/sil) | token |
| `POST` | `/stt` · `/tts` | konuşma→metin / metin→konuşma | token |

## Güvenlik

Bu proje API key'leri ve keyfi upstream çağrılarını yönetir, bu yüzden gateway çeşitli
korumalarla gelir. **`localhost` dışına açmadan önce bunları gözden geçir** — ve
[`SECURITY.md`](./SECURITY.md)'i tam oku.

- **Key'ler sunucuda kalsın.** UI'da **Gateway** sağlayıcısını tercih et; provider key'leri
  yalnız `gateway/.env`'de durur, tarayıcıya gitmez.
- **`GATEWAY_TOKEN` (bearer auth).** Set edilince `/health` hariç her istek `Authorization:
  Bearer <token>` yollamalı (sabit-zamanlı karşılaştırma). Varsayılan boş = açık. Production'da
  gateway tokensiz (veya çok kısa) başlamayı reddeder. Üret:
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
  ```
- **CORS allowlist (`ALLOW_ORIGINS`).** Yalnız listedeki origin'ler çağırabilir. `*` = sadece dev.
- **Rate limit (`RATE_MAX` / `RATE_WINDOW_MS`)** — IP + kullanıcı bazlı; `RATE_MAX=0` kapatır.
- **Model allowlist (`ALLOW_MODELS`)** — örn. `ollama/*,gemini/gemini-2.5-flash`.
- **Girdi limitleri** — `BODY_LIMIT`, `MAX_MESSAGES`, `MAX_MESSAGE_CHARS`, `REQ_TIMEOUT_MS`.
- **Medya/görsel doğrulama** — `/v1/media` MIME allowlist + base64 kontrolü; remote görsel
  opt-in, private/localhost hedefleri engellenir, redirect/byte limitleri.
- **Ajan araçları** — `web_search` yalnız iç SearXNG'e gider; `calculator` sıkı-allowlist güvenli
  eval; `code_run` (QuickJS) ve `fetch_url` (SSRF-korumalı) varsayılan kapalı.
- **Keycloak varsayılanları** — izlenen realm varsayılan kullanıcı veya parola import etmez;
  web client PKCE authorization-code akışını kullanır ve password grant kapalıdır.
- **Minimal health** — `/health` production'da yalnız `{ ok: true }` döner.
- **Frontend CSP/link güvenliği** — sıkı CSP; production `CSP_CONNECT_SRC` yalnız `'self'`
  veya tam güvenilir origin'ler olmalı; markdown linkleri yalnız `http`/`https`/`mailto`.
- **Sertleştirme varsayılanları** — `X-Powered-By` kapalı, temel güvenlik başlıkları, production'da
  upstream hata detayları gizli. `TRUST_PROXY=1` yalnız güvenilir TLS proxy arkasında.

### Production kontrolü (tek komut)

Prod `.env`'i source et, sonra:

Production hazırlık kontrolünü ve tam security kapısını çalıştır:

```bash
npm run prod-check
npm run security
```

Production için sertleştirme overlay'ini ekle (Keycloak `start` modu, zorunlu-secret guard'ları):

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d
```

Diğer kontroller:

| Komut | Amaç |
| --- | --- |
| `npm run secret-scan` | İzlenen dosyalarda sızmış key veya token taraması. |
| `npm run audit` | Gateway ve web için moderate seviyede npm audit. |
| `npm --prefix gateway test` | Gateway birim ve ajan-döngüsü testleri. |
| `npm run smoke:live` | Çalışan gateway'e karşı uçtan uca smoke. |

## Yapılandırma referansı

Tüm gateway ayarları ortam değişkenidir (tam liste için `gateway/.env.example`):

| Değişken | Varsayılan | Açıklama |
| --- | --- | --- |
| `PORT` | `8088` | gateway portu |
| `GATEWAY_TOKEN` | *(boş)* | bearer token; boş = auth kapalı |
| `ALLOW_ORIGINS` | Vite dev/preview origin'leri | CORS allowlist (virgül listesi, `*` = herhangi) |
| `CSP_CONNECT_SRC` | local/dev + provider-direct origin'leri | Caddy browser `connect-src`; production'da `'self'` veya tam güvenilir origin'ler |
| `RATE_MAX` / `RATE_WINDOW_MS` | `120` / `60000` | IP bazlı rate limit |
| `ALLOW_MODELS` | *(hepsi)* | model allowlist, `provider/*` destekler |
| `BODY_LIMIT` | `25mb` | maks JSON gövde boyutu |
| `MAX_MESSAGE_CHARS` / `MAX_MESSAGES` | `100000` / `400` | istek başına metin + mesaj sınırı |
| `MAX_DOC_BYTES` / `MAX_DOC_FILE_BYTES` | `1048576` / `10485760` | bilgi metni + ham yükleme sınırı |
| `MAX_MEDIA_BYTES` / `ALLOWED_MEDIA_MIME_TYPES` | `26214400` / allowlist | `/v1/media` sınırları |
| `HEALTH_DETAILS_ENABLED` | `0` | `1` ise non-prod `/health` detayları |
| `CODE_TOOL_ENABLED` (+`_TIMEOUT_MS`/`_MEMORY_MB`/`_MAX_*`) | `0` | opt-in QuickJS `code_run` aracı + limitler |
| `FETCH_TOOL_ENABLED` / `FETCH_TOOL_MAX_BYTES` | `0` / `2000000` | opt-in SSRF-korumalı `fetch_url` aracı |
| `REQ_TIMEOUT_MS` / `MAX_RETRIES` | `60000` / `2` | upstream timeout + retry |
| `TRUST_PROXY` | `0` | reverse-proxy başlıklarına güven (`1` ile aç) |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY` | *(boş)* | provider key'leri |
| `OLLAMA_URL` | `http://localhost:11434` | yerel Ollama base URL'i |
| `DEFAULT_MODEL` | `ollama/qwen3:14b` | `auto`'nun düştüğü model |
| `VISION_MODEL` / `ROUTE_VISION` | `ollama/qwen3.5-omni:latest` / *(unset)* | görsel istek yönlendirme |
| `REMOTE_IMAGE_URLS_ENABLED` / `REMOTE_IMAGE_MAX_BYTES` | `0` / `10485760` | remote görsel fetch (private host engellenir) |
| `ROUTE_FAST/BALANCED/DEEP/MAX` | *(unset)* | effort bazlı model override |
| `OPENCLAW_URL` / `OPENCLAW_TOKEN` / `OPENCLAW_PATH` | localhost varsayılanları | OpenClaw ajan katmanı |
| `WHISPER_URL` / `TTS_URL` / `TTS_MODEL` / `TTS_VOICE` | localhost varsayılanları | ses STT/TTS |
| `VOICE_QUEUE_ENABLED` (+`_CONCURRENCY`/`_RESULT_TTL_SEC`/`_MAX_AUDIO_BYTES`) | `0` | async BullMQ/Redis ses job'ları |
| `TTS_MAX_INPUT_CHARS` | `8000` | maks TTS girdi uzunluğu |
| `OTEL_EXPORTER_OTLP_ENDPOINT` / `_TRACES_ENDPOINT` / `OTEL_SERVICE_NAME` | *(unset)* / *(unset)* / `nova-gateway` | opt-in OTLP/HTTP trace |
| `TEAM_CONCURRENCY` | `3` | team modunda paralel alt-ajan |
| `AUTO_AGENT_ENABLED` | `1` | canlı-veri sorgularında (hava/haber/fiyat) ajan araçlarını oto-aç; SearXNG + tool-calling model gerekir |
| `MEMORY_ENABLED` / `MEMORY_MAX_ITEMS` / `MEMORY_MAX_CHARS` | `1` / `60` / `500` | kişisel hafıza + limitler |
| `EVAL_CONCURRENCY` / `EVAL_MAX_MODELS` | `3` / `6` | model-kıyas paralelliği + üst sınır |
| `MCP_SERVERS` / `MCP_CACHE_MS` | *(unset)* / `300000` | harici MCP araç sunucuları (opt-in) + araç-listesi cache |

## Ses modu

Arayüz, tarayıcı konuşma tanımayı kutudan çıkar çıkmaz destekler. Daha kaliteli gerçek
STT/TTS için OpenAI-uyumlu sunucular çalıştır ve `WHISPER_URL` / `TTS_URL`'i onlara yönlendir
(UI gateway'deki `/stt` ve `/tts`'i çağırır, gateway de o sunuculara proxy'ler). Uzun yerel ses
işleri için Redis varken `VOICE_QUEUE_ENABLED=1` yap; gateway o zaman `POST /v1/voice/jobs`,
`GET /v1/voice/jobs/:id` ve `GET /v1/voice/jobs/:id/audio` sunar.

## Sorun giderme

- **`401 unauthorized`** — `GATEWAY_TOKEN` set ama UI yollamıyor. Token'ı Ayarlar'da Gateway
  sağlayıcısının key alanına yapıştır.
- **CORS hatası** — UI origin'i `ALLOW_ORIGINS`'te değil. Ekle ve gateway'i yeniden başlat.
- **Ollama "connection refused"** — `OLLAMA_ORIGINS=* ollama serve` ile başlat.
- **`429 rate limit exceeded`** — `RATE_MAX`'a takıldın; yükselt veya pencerenin sıfırlanmasını bekle.
- **Boş/bozuk akış** — provider key'inin set olduğunu ve model id'sinin var olduğunu doğrula.

## Lisans

MIT. Bkz. [`LICENSE`](./LICENSE).
