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
- Yerel demo parolalarının yalnızca dev amaçlı olduğunu açık tut; [`SECURITY.md`](./SECURITY.md)
  listesini uygulamadan dışarı açma.
- `gateway/.env.example` güncel kalsın; gerçek değerler kullanıcı tarafından kendi makinesinde üretilir.
- Hosted/production servis kapsam dışıdır. İnternete açmak ayrıca TLS, domain, secret rotasyonu,
  `ALLOW_ORIGINS`, Keycloak production modu ve kapalı iç portlar gerektirir.

## 🚀 Hızlı kurulum

İki yol: **Docker (önerilen — tam stack)** veya **bare-metal (sadece gateway + web)**.

### Yol A — Docker tam stack (Postgres, Redis, Keycloak, SearXNG, MinIO, Grafana, ses)

Docker + docker compose ve yerel LLM için [Ollama](https://ollama.com) gerekir.

```bash
# 1) Klonla
git clone <repo-url> nova && cd nova

# 2) Gateway .env oluştur (token üret + en az bir provider key VEYA Ollama)
cp gateway/.env.example gateway/.env
node -e "console.log('GATEWAY_TOKEN='+require('crypto').randomBytes(32).toString('hex'))" >> gateway/.env
# gateway/.env'i düzenle: ALLOW_ORIGINS, bir provider key veya OLLAMA_URL ayarla

# 3) Web arayüzünü derle (Caddy web/dist'i servis eder)
npm --prefix web ci && npm --prefix web run build

# 4) (Yerel LLM için) Ollama'yı container'lara aç + model çek
#    WSL/Linux'ta OLLAMA_HOST=0.0.0.0 ile çalıştır, sonra:
ollama pull qwen3:14b && ollama pull qwen3:8b && ollama pull nomic-embed-text
# (Görsel için; tag kuruluma göre değişir)
ollama pull qwen3.5-omni:latest

# 5) Stack'i kaldır (ilk açılışta imajlar iner, birkaç dakika)
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build

# 6) İlk kullanıcı + API key (multi-user modu)
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 5
```

Aç: **http://localhost** → ⚙ Ayarlar → **Keycloak ile Giriş** (veya API key yapıştır) → sohbet.
İç paneller varsayılan olarak loopback-only'dir (host makinesinden erişilir): Grafana
`127.0.0.1:3001`, Prometheus `:9090`, MinIO `:9001`, SearXNG `:8080`, Keycloak `:8081`.

> ⚠️ Tüm varsayılan parolalar **yalnızca yerel geliştirme içindir.** Public'e açmadan önce
> **[`SECURITY.md`](./SECURITY.md)**'i oku ve hepsini değiştir. Windows + WSL kurulumu:
> **[`WSL_DOCKER.md`](./WSL_DOCKER.md)**.

### Yol B — Bare-metal (tek kullanıcılı gateway + web, DB yok)

```bash
git clone <repo-url> nova && cd nova
npm run install:all                       # gateway + web bağımlılıkları
cp gateway/.env.example gateway/.env      # GATEWAY_TOKEN + bir provider key ya da Ollama
npm run gateway                           # terminal A → http://localhost:8088/v1
npm run web                               # terminal B → http://localhost:5173
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

### Faz panosu

| Faz | Durum | Öne çıkanlar |
| --- | --- | --- |
| -1 — İnceleme | ✅ Tamam | Mimari rapor, as-is/to-be diyagramları, risk + faz haritası. |
| 0 — Gönderilebilir taban | ✅ Tamam | Dockerfile, Caddy/TLS, production token guard, CI iskeleti, deploy runbook. |
| 1 — Çok kullanıcı | ✅ Tamam | Postgres/Redis, OIDC/JWT, API key, dağıtık rate limit, kalıcı geçmiş, kota. |
| 2 — Ölçek + ürün | ✅ Temel tamam | Prometheus `/metrics`, pino log, object storage `/v1/media`, Stripe billing, K8s/HPA, ses. |
| 3 — Ajan + RAG | ✅ Tamam | Tool/function calling, SearXNG web araması, `doc_search`, pgvector RAG, kaynak rozetleri. |
| 4A — Artifacts/export | ✅ Tamam | HTML/SVG/Mermaid önizleme, artifact indirme, Markdown/JSON/PDF export, yerel paylaşım linki. |
| 4B — Araç/belge/persona | ✅ Tamam | PDF/DOCX çıkarımı, opt-in QuickJS `code_run`, persona/prompt kütüphanesi. |
| 4C — Çok modlu + ses kuyruğu | ✅ Tamam | `auto` görsel yönlendirme + `VISION_MODEL`, opt-in remote görsel, BullMQ ses job'ları. |
| 4D — Repo hijyeni + güvenlik | ✅ Tamam | Kişisel izler temizlendi, CSP/JWT/media/admin sertleştirme, temiz git history. |
| 5A — CI & güvenlik otomasyonu | ✅ Tamam | CI Node 20.19+22 matris, secret scan, npm audit, canlı smoke; tek komut `npm run security`. |
| 5B — Gözlemlenebilirlik + sertleştirme | ✅ Tamam | Prometheus ajan metrikleri, opt-in OTLP trace, Grafana auto-provisioning, hata webhook, `npm run prod-check`. |
| 6 — İleri ürün + Android | ✅ Tamam | Zamanlanmış görevler · team mode + canlı ilerleme · kişisel hafıza · model eval · PWA · MCP · Aurora UI redesign · workspace + RBAC · Android Gradle wrapper paketlendi. |
| 7 — İşbirliği (paylaşımlı kaynaklar) | ✅ Temel tamam | Workspace-kapsamlı bilgi tabanı, hafıza ve zamanlanmış görevler (yazma = editör/admin). Opsiyonel: paylaşımlı sohbetler. |
| 8 — Üretim sertleştirme + deploy + ajan derinleştirme | 🟡 Sürüyor | ✅ Güçlendirilmiş `prod-check` (zayıf token/varsayılan secret/MCP-TLS) + preflight, loopback-bound paneller, `docker-compose.prod.yml`. ✅ Ajan derinleştirme: MCP araç görünürlüğü, opt-in SSRF-korumalı `fetch_url`, ajan çalışma geçmişi. Sırada: canlı deploy hazırlığı. |

### Son değişiklikler

Özet changelog; oturum-bazlı tam detay git history ve `PROGRESS.md`'de.

- **Faz 8 (sertleştirme + ajan):** güçlendirilmiş `npm run prod-check` (token gücü,
  varsayılan/zayıf altyapı secret'ları, multi-user DB/admin, cleartext MCP); gateway prod'da
  çok kısa `GATEWAY_TOKEN` ile başlamayı reddeder; iç paneller yalnız `127.0.0.1`'e bağlanır;
  `docker-compose.prod.yml` overlay'i (Keycloak `start` modu, zorunlu-secret guard'ları).
  Ajan derinleştirme: `GET /v1/mcp/tools` görünürlüğü + UI, opt-in `fetch_url` web sayfası
  okuyucu (SSRF-korumalı), ajan çalışma geçmişi (`/v1/agent/runs` + UI).
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
| `qwen3:14b` | ~10 GB | bölünmüş CPU/GPU | orta, en güçlü tool-calling |
| `gemma4:e2b` | ~7.2 GB | **%100 GPU** | en hızlı |

## Depo yapısı

```
Nova_Agent_AI/
├── package.json            # her iki paketi süren yardımcı script'ler
├── docker-compose.yml      # temel stack (Postgres, Redis, gateway, Caddy)
├── docker-compose.faz2.yml # faz-2 eklentileri (Keycloak, MinIO, SearXNG, Prometheus, Grafana, ses)
├── docker-compose.prod.yml # production sertleştirme overlay'i
├── gateway/                # API gateway (Node, build adımı yok)
│   ├── gateway.mjs         # sertleştirilmiş sunucu: auth, CORS allowlist, rate limit
│   ├── lib/                # ajan döngüsü, mcp, rag, memory, rbac, prodcheck, …
│   ├── routes/             # knowledge, memory, scheduled, workspaces, agent runs, …
│   ├── migrations/         # SQL migration'lar (001…008)
│   └── .env.example        # .env'e kopyala ve doldur
├── web/                    # tarayıcı arayüzü (Vite + React + PWA)
│   └── src/nova-agent.jsx  # tam UI bileşeni
└── nova-android/           # native Android istemci (Kotlin + Compose)
```

## Önkoşullar

- **Node.js 20.19 veya üzeri** (`node -v`). Web build Vite 8 kullanır (`^20.19.0 || >=22.12.0`).
- En az bir model backend'i:
  - **Yerel, key'siz:** `http://localhost:11434`'te [Ollama](https://ollama.com).
  - **Bulut:** Anthropic, Gemini ve/veya OpenAI için API key.
- (Opsiyonel) Gerçek ses modu için OpenAI-uyumlu **Whisper** (STT) ve **TTS** sunucuları.

### Production için derleme

```bash
npm run build          # statik dosyaları web/dist/'e üretir
npm run preview        # build'i yerelde http://localhost:4173'te servis eder
```

`web/dist/`'i herhangi bir statik host'tan servis et, gateway'i arkasında çalışır tut.

## Model yönlendirme nasıl çalışır

Model string'ini `"<provider>/<model>"` olarak yolla:

| Örnek | Gider |
| --- | --- |
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
- **Minimal health** — `/health` production'da yalnız `{ ok: true }` döner.
- **Frontend CSP/link güvenliği** — sıkı CSP; markdown linkleri yalnız `http`/`https`/`mailto`.
- **Sertleştirme varsayılanları** — `X-Powered-By` kapalı, temel güvenlik başlıkları, production'da
  upstream hata detayları gizli. `TRUST_PROXY=1` yalnız güvenilir TLS proxy arkasında.

### Production kontrolü (tek komut)

Prod `.env`'i source et, sonra:

```bash
npm run prod-check    # token gücü, CORS, varsayılan/zayıf secret'lar, MCP-TLS, … doğrular
npm run security      # syntax + gateway test + secret scan + gateway/web audit + web build
```

Production için sertleştirme overlay'ini ekle (Keycloak `start` modu, zorunlu-secret guard'ları):

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d
```

Diğer kontroller:

```bash
npm run secret-scan   # izlenen dosyalarda sızmış key/token taraması
npm run audit         # gateway + web için npm audit (moderate)
npm --prefix gateway test   # gateway birim + ajan-döngüsü testleri
npm run smoke:live    # ÇALIŞAN gateway'e karşı uçtan uca smoke
```

## Yapılandırma referansı

Tüm gateway ayarları ortam değişkenidir (tam liste için `gateway/.env.example`):

| Değişken | Varsayılan | Açıklama |
| --- | --- | --- |
| `PORT` | `8088` | gateway portu |
| `GATEWAY_TOKEN` | *(boş)* | bearer token; boş = auth kapalı |
| `ALLOW_ORIGINS` | Vite dev/preview origin'leri | CORS allowlist (virgül listesi, `*` = herhangi) |
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
