# NOVA Agent

A voice + chat interface for talking to many LLM providers through **one endpoint**.
The browser UI (React) sends OpenAI-style requests to a small Node **gateway** that
routes them to Anthropic, Google Gemini, OpenAI, local **Ollama**, or an **OpenClaw**
agent — and also proxies speech-to-text and text-to-speech.

```
┌────────────────────┐        OpenAI-style /v1/chat/completions        ┌─────────────────────────┐
│   web/ (Vite +     │  ───────────────────────────────────────────▶  │  gateway/ (Node/Express)│
│   React UI)        │     /stt  /tts  (voice)                         │  auth · CORS · limits   │
└────────────────────┘                                                 └────────────┬────────────┘
                                                                                     │ routes by <provider>/<model>
                                          ┌──────────────────────────────────────────┼───────────────────────────┐
                                          ▼                ▼                ▼          ▼               ▼            ▼
                                     Anthropic         Gemini           OpenAI      Ollama        OpenClaw   Whisper/TTS
```

## Public Kullanım Durumu

Evet, bu repo public yapılırsa insanlar projeyi **kendi bilgisayarlarında yerel
olarak** kullanabilir. Bu projenin public paylaşım hedefi şimdilik **local-first /
self-hosted geliştirme deneyimi**dir; merkezi bir production servis olarak
yayınlama hedeflenmiyor.

İki ana kullanım yolu hazır: hızlı bare-metal deneme ve Docker ile tam yerel stack.
README'deki production/güvenlik uyarıları, projeyi yanlışlıkla internete açık
servis gibi çalıştırmayı önlemek içindir.

### Minimum Gereksinimler

| Kullanım tipi | Minimum gereksinim | Not |
| --- | --- | --- |
| Bare-metal deneme | Git, Node.js 20.19+, npm, modern tarayıcı | Sadece `gateway` + `web`; tek kullanıcı/dev kullanım için en hızlı yol. |
| Model erişimi | En az bir provider API key'i **veya** yerel Ollama | OpenAI/Anthropic/Gemini key girilebilir; yerel kullanımda Ollama önerilir. |
| Docker tam stack | Docker Engine + Docker Compose; Windows'ta WSL Ubuntu önerilir | Postgres, Redis, Keycloak, MinIO, SearXNG, Grafana, voice servisleri dahil. |
| Donanım | 8 GB RAM minimum; 16 GB RAM önerilir | Yerel LLM kullanılırsa model boyutuna göre GPU/VRAM performansı belirler. |
| Portlar | `80`, `443`, `3001`, `8080`, `8081`, `8088`, `9000`, `9001`, `9090` boş olmalı | Docker stack'te bazı paneller bu portlardan açılır. |

### Local Kullanım İçin Public Repo Checklist

- MIT lisansı eklidir; kullanıcılar kodu kullanabilir, değiştirebilir ve dağıtabilir.
  Lisans metni ve telif bildirimi kopyalarda korunmalıdır.
- Gerçek `.env` dosyası, API key, token, admin parolası veya kullanıcı verisi commit etme.
- Local demo parolalarının yalnızca geliştirme amaçlı olduğunu açık tut; dışarıdan erişime
  açılacak bir ortamda `SECURITY.md` listesini uygulamadan devam etme.
- `gateway/.env.example` güncel kalsın; gerçek değerler kullanıcı tarafından kendi
  makinesinde oluşturulmalı.
- Mevcut git geçmişiyle public açmadan önce history'yi ayrıca kontrol et. Eski
  commit'lerde kişisel test verisi veya geçici build dosyası kaldıysa temiz bir
  başlangıç/squash/history rewrite yap.
- Hosted/production servis bu repo paylaşımının kapsamı değildir. İnternete açılacaksa
  ayrıca TLS, domain, secret rotasyonu, `ALLOW_ORIGINS`, Keycloak production ayarı ve
  kapalı internal portlar gerekir.

Hızlı kurulum adımları aşağıda. Public kullanıcı için en kısa yol **Yol B**,
tam ürün deneyimi için **Yol A**.

## 🚀 Hızlı Kurulum (GitHub'dan klonlayıp çalıştırma)

İki yol var: **Docker (önerilen — tam stack)** veya **bare-metal (sadece gateway + web)**.

### Yol A — Docker ile tam stack (Postgres, Redis, Keycloak, SearXNG, MinIO, Grafana, voice)

Gerekenler: Docker + docker compose, ve yerel LLM için [Ollama](https://ollama.com).

```bash
# 1) Klonla
git clone <repo-url> nova && cd nova

# 2) Gateway .env oluştur (token üret + en az bir provider key VEYA Ollama)
cp gateway/.env.example gateway/.env
node -e "console.log('GATEWAY_TOKEN='+require('crypto').randomBytes(32).toString('hex'))" >> gateway/.env
# gateway/.env'i aç; ALLOW_ORIGINS, provider key veya OLLAMA_URL'i ayarla

# 3) Web arayüzünü derle (Caddy web/dist'i servis eder)
npm --prefix web ci && npm --prefix web run build

# 4) (Yerel LLM için) Ollama'yı container'lara aç + model çek
#    WSL/Linux'ta: OLLAMA_HOST=0.0.0.0 ile çalıştır, sonra:
ollama pull gemma4:latest && ollama pull qwen3.5:9b && ollama pull nomic-embed-text
# (Görsel anlama için; tag ortamına göre değişebilir)
ollama pull qwen3.5-omni:latest

# 5) Tüm stack'i kaldır (ilk açılışta imajlar iner, birkaç dk)
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build

# 6) İlk kullanıcı + API key (multi-user modunda)
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 5
```

Aç: **http://localhost** → ⚙ Ayarlar → **Keycloak ile Giriş** (veya API key yapıştır) → sohbet.
Diğer paneller: Grafana `localhost:3001`, Prometheus `localhost:9090`, MinIO `localhost:9001`, SearXNG `localhost:8080`, Keycloak `localhost:8081`.

> ⚠️ Tüm varsayılan parolalar **yalnızca yerel geliştirme içindir.** Yayına almadan önce **[`SECURITY.md`](./SECURITY.md)**'i oku ve hepsini değiştir. Windows + WSL kurulumu: **[`WSL_DOCKER.md`](./WSL_DOCKER.md)**.

### Yol B — Bare-metal (sadece tek kullanıcılı gateway + web, DB yok)

```bash
git clone <repo-url> nova && cd nova
npm run install:all                       # gateway + web bağımlılıkları
cp gateway/.env.example gateway/.env      # GATEWAY_TOKEN + bir provider key ya da Ollama
npm run gateway                           # terminal A → http://localhost:8088/v1
npm run web                               # terminal B → http://localhost:5173
```

Ajan modu (web araması + belgelerle sohbet) ve çok kullanıcı/geçmiş/kota için **Yol A** gerekir.

## Proje durumu ve yol haritası

Production'a (herkese açık, çok kullanıcılı) taşıma çalışması sürüyor. Nerede kaldığımızı
ve sıradaki adımı görmek için **[`PROGRESS.md`](./PROGRESS.md)** dosyasına bak. İlgili dokümanlar:

> **Devam kuralı:** Her oturum sonunda `PROGRESS.md` VE bu README'nin
> "Yapılan fazlar / Kalan işler" bölümleri güncel kalmalı. Yeni oturumda önce
> `PROGRESS.md` içindeki "Oturum Hafızası / Handoff" ve "SIRADAKİ ADIM" bölümleri okunur.

- [`PROGRESS.md`](./PROGRESS.md) — güncel durum + sıradaki adım (buradan başla)
- [`NOVA_Mimari_Inceleme.md`](./NOVA_Mimari_Inceleme.md) — tam mimari inceleme, as-is/to-be, faz yol haritası
- [`DEPLOY.md`](./DEPLOY.md) — Faz 0 deploy runbook'u (Docker + Caddy/TLS + CI)
- [`PHASE1.md`](./PHASE1.md) — Faz 1 (çok kullanıcı: auth, geçmiş, kota) + `gateway.mjs` entegrasyon patch'i
- [`PHASE2.md`](./PHASE2.md) — Faz 2 (gözlemlenebilirlik, object storage, billing, K8s/HPA, voice)
- [`nova-android/README.md`](./nova-android/README.md) — Android istemciyi Android Studio ile açma ve test etme

### Faz panosu — nerede kaldık?

**Şu anki işaretçi:** **Faz 4 tamamen bitti** (4A/4B/4C/4D). 4C vision+voice canlı doğrulandı (görsel→`VISION_MODEL` routing + model görseli tarif etti; async TTS job ses üretti); 4D git history temizlenip force-push edildi (`35d7d37`). Faz 5A CI/güvenlik otomasyonu da hazır. Canlı testte **chat'i kıran bir abort bug'ı** (`req.on('close')`→`res.on('close')`) bulunup düzeltildi. **Kalan tek iş:** bu fix'i commit+push edip Docker gateway imajını rebuild etmek (bkz. PROGRESS). Sıradaki faz: **5B** (gözlemlenebilirlik / prod sertleştirme).

| Faz | Durum | Yaptıklarımız | Kalan / çıkış kriteri |
| --- | --- | --- | --- |
| Faz -1 — İnceleme | ✅ Tamam | Mimari rapor, as-is/to-be diyagramları, risk ve faz haritası çıkarıldı. | Yok. |
| Faz 0 — Gönderilebilir taban | ✅ Tamam | Dockerfile, Caddy/TLS, production token guard, CI iskeleti, deploy runbook'u. | Public release öncesi CI hattı güncellenecek. |
| Faz 1 — Çok kullanıcı | ✅ Tamam | Postgres/Redis, OIDC/JWT, API key, dağıtık rate limit, kalıcı geçmiş, kota/ölçüm; canlı doğrulandı. | Production için gerçek `ADMIN_USER_IDS`, secret rotation ve Keycloak prod mode. |
| Faz 2 — Ölçek + ürün | ✅ Temel tamam | Prometheus `/metrics`, pino log, object storage `/v1/media`, Stripe billing, K8s/HPA, voice servisleri. | Full Docker stack smoke testi tekrar yapılacak. |
| Faz 3 — Ajan + RAG | ✅ Tamam | Tool/function calling, SearXNG web araması, `doc_search`, pgvector RAG, kaynak rozetleri. | Ajan/RAG uçtan uca smoke testleri CI'a eklenecek. |
| Faz 4A — Artifacts/export | ✅ Tamam | HTML/SVG/Mermaid önizleme, artifact indirme, Markdown/JSON/PDF export, local paylaşım linki. | Yok; ileride UX iyileştirmeleri opsiyonel. |
| Faz 4B — Araçlar/belge/persona | ✅ Tamam | PDF/DOCX text extraction, default kapalı QuickJS `code_run`, persona/prompt kütüphanesi. | `code_run` sadece local ve bilinçli opt-in kalacak. |
| Faz 4C — Çok modlu + ses kuyruğu | ✅ Yapıldı (canlı) | `auto` görsel yönlendirme + `VISION_MODEL`, remote image opt-in, BullMQ voice job. **Canlı smoke:** görsel→vision routing + model görseli tarif etti (gemma4); async TTS job ses üretti (Redis+TTS). | `VISION_MODEL`'i kurulu bir vision tag'ine ayarla (default `qwen3.5-omni` çoğu kurulumda yok). |
| Faz 4D — Public repo hijyeni + güvenlik | ✅ Yapıldı | Kişisel izler temizlendi (nova-agent.jsx default prompt generic), CSP/JWT/media/image/admin/history sertleştirildi, audit 0; git history clean-start ile tek temiz commit'e indirildi ve force-push edildi (`35d7d37`). | Opsiyonel: public öncesi repo'yu silip-yeniden-oluştur (da8bece full-SHA GC garantisi). |
| Faz 5A — CI & güvenlik otomasyonu | ✅ Tamam | CI Node 20.19+22 matris, secret scan, gateway/web `npm audit`, canlı smoke adımı; `scripts/secret-scan.mjs` + `smoke-live.mjs` + `security-check.mjs`; mock'lu ajan-döngüsü testleri (suite 39). | Birleşik `npm test` / `npm run security` Windows'ta yeşil doğrulanacak. |
| Faz 5B — Prod sertleştirme | ⏳ Sırada | Güvenlik temeli + CI otomasyonu güçlendi. | Keycloak prod mode, portları iç ağa kapatma, secret rotation, `ALLOW_MODELS`, Sentry/OTel, sürümlü dashboardlar. |
| Faz 6 — CI + Android + ileri ürün | ⏳ Sonra | Android kaynakları `nova-android/` klasörüne çıkarıldı; roadmap netleşti. | Gradle wrapper/test-build, CI security pipeline, çoklu ajan, otomasyon, RBAC. |

### Son çalışma özeti — 13 Haziran 2026 (Faz 5A — CI & güvenlik otomasyonu)

Bu oturumda CI ve güvenlik otomasyonu hattı (Faz 5A) tamamlandı:

1. **CI sertleştirildi** (`.github/workflows/ci.yml`): Node sürüm matrisi `["20.19","22"]`; lockfile'dan `npm ci`; yeni adımlar **secret scan**, **gateway+web `npm audit`** ve **canlı smoke** (`scripts/smoke-live.mjs`); production preflight-fail ve docker imaj job'u korundu.
2. **Secret scanner** (`scripts/secret-scan.mjs`): sıfır bağımlılık, `git ls-files` ile izlenen dosyalar; sağlayıcı/cloud anahtarları + private key + entropi tabanlı `secret = <değer>` sezgisi; dev placeholder allowlist. Temiz ağaçta 0 bulgu, planted secret'lar yakalanıyor.
3. **Canlı smoke** (`scripts/smoke-live.mjs`): çalışan gateway'e karşı `/health`, auth zorlaması, `/v1/models` (zorunlu) + `chat`/`agent`/`rag` (opsiyonel). Bare-metal gateway'de doğrulandı.
4. **Tek komut güvenlik kapısı** (`scripts/security-check.mjs` → `npm run security`): syntax + gateway test + secret-scan + gateway/web audit + web build.
5. **Ajan/RAG CI-smoke (mock'lu)**: `gateway/test/agent.test.mjs`'e 3 `runAgent` testi — `fetch` mock'u ile araç-çağrı döngüsü, kaynak taşıma; canlı altyapı gerekmez. Suite 36 → **39**.
6. **Root script'ler**: `test:gateway`, `secret-scan`, `audit`, `security`, `smoke:live`.
7. **Doğrulama:** sandbox'ta units 21/21, yeni ajan-döngüsü 3/3, secret-scan 0 bulgu, smoke-live canlı OK, `ci.yml` YAML geçerli. Birleşik `npm test` (39) + `npm run security` + `npm run build` **Windows'ta** çalıştırılmalı (web esbuild + mount bayatlığı).

### Son çalışma özeti — 13 Haziran 2026

Bu oturumda public/local paylaşım ve güvenlik sertleştirme hattı kapatıldı:

1. **Public repo hijyeni:** current tree içinde kişisel e-posta/kullanıcı adı/path izleri temizlendi; Keycloak demo kullanıcısı generic `demo@example.local` değerlerine alındı.
2. **Artifact temizliği:** Vite timestamp dosyaları, kökteki kopya Android dosyası, `README (2).md` ve `nova-android.zip` kaldırıldı; Android kaynakları gerçek `nova-android/` klasörü olarak bırakıldı.
3. **Dependency güvenliği:** web tarafındaki Vite/esbuild audit açığı `vite@8.0.16` ve `@vitejs/plugin-react@6.0.2` yükseltmesiyle kapatıldı; gateway/web audit sonucu 0 vulnerability.
4. **Gateway sertleştirme:** public `/health` minimal hale getirildi, production preflight sıkılaştırıldı, JSON/body-limit hataları maskelendi, JWT `sub` kontrolü ve route parametre doğrulamaları eklendi.
5. **Dosya/görsel güvenliği:** remote image fetch opt-in kalır; private/localhost hedefler, redirect/byte limitleri, image data URL base64 doğrulaması ve `/v1/media` MIME allowlist eklendi.
6. **Frontend güvenliği:** CSP wildcard kaldırıldı; markdown linkleri yalnız `http`, `https`, `mailto` şemalarıyla tıklanabilir.
7. **Doğrulama:** `npm.cmd --prefix gateway test` → 36/36, `npm.cmd --prefix gateway audit` → 0 vulnerability, `npm.cmd --prefix web audit` → 0 vulnerability, `npm.cmd run build` geçti.

Kalan önemli not: current tree temiz; fakat eski git commit geçmişinde kişisel test verisi/path izleri bulundu. Public release mevcut history ile yapılmamalı; önce temiz başlangıç/squash/history rewrite seçilmeli.

### Sıradaki faz planı — adım adım

**Faz 4D çıkışı — public release temizliği** (runbook hazır: [`HISTORY_CLEANUP.md`](./HISTORY_CLEANUP.md))

1. ✅ Strateji seçildi: **clean-start (orphan)** — tek commit `da8bece` hem içerik hem author e-postası (kişisel gmail adresi) açısından düşürülmeli.
2. ✅ Tree ek-temizlendi (nova-agent.jsx default prompt generic); timestamp dosyaları zaten silinmiş, clean-start onları almaz.
3. **Kullanıcı (Windows/WSL):** `bash scripts/clean-history.sh` (veya `clean-history.ps1`) — backup + `CONFIRM` + secret-scan kapısı + orphan tek-commit; push öncesi durur.
4. **Kullanıcı:** `git push --force origin main` **veya** GitHub repo'yu silip yeniden oluşturup push (da8bece cache/fork/PR'da kalabilir).
5. **Doğrula:** `da8bece` commit URL'i 404 + `npm run secret-scan` temiz; ardından repo public yapılabilir.

**Faz 4C çıkışı — çok modlu ve ses canlı doğrulama**

1. WSL/Docker ortamında compose stack'i kaldır.
2. Migrate, Keycloak login, `/v1/models`, chat, RAG belge yükleme ve `/v1/media` smoke testlerini yap.
3. Ollama vision modeli (`qwen3.5-omni` veya Qwen 3.6 sınıfı) ile data URL görsel testini yap.
4. `REMOTE_IMAGE_URLS_ENABLED=1` ile güvenli remote image URL senaryosunu test et; private/localhost bloklarının çalıştığını doğrula.
5. Redis + Whisper + TTS servisleri ayaktayken `VOICE_QUEUE_ENABLED=1` ile async STT/TTS job oluşturma, polling ve audio indirme akışını test et.

**Faz 5A — CI ve güvenlik otomasyonu ✅ (tamamlandı)**

1. ✅ CI Node 20.19+ üstüne taşındı (matris: `20.19`, `22`).
2. ✅ CI'a gateway test, web build, gateway/web `npm audit` ve secret scan adımları eklendi.
3. ✅ Ajan/RAG smoke: mock'lu `runAgent` testleri CI'da koşuyor; ayrıca canlı stack için `scripts/smoke-live.mjs`.
4. ✅ Tek komut güvenlik kontrol listesi: `npm run security` (`scripts/security-check.mjs`).

Kalan doğrulama: Windows'ta `npm.cmd run security` ve `npm.cmd --prefix gateway test` (39) yeşil çalıştır.

**Faz 5B — production sertleştirme kararı**

1. Local-first/self-hosted kapsamını README ve SECURITY içinde koru.
2. İnternete açılacak kurulumlar için Keycloak prod mode, TLS, secret rotation, port kapatma ve `ALLOW_MODELS` checklistini ayrı tut.
3. Sentry/OpenTelemetry trace ve sürümlü Grafana dashboardlarını planla.
4. K8s/HPA manifestlerini ajan, RAG ve voice servisleriyle güncelle.

**Faz 6 — Android ve ileri ürün**

1. `nova-android/` içinde Gradle wrapper jar üret.
2. Android Studio/Gradle test-build smoke yap.
3. Android sonucu README/PROGRESS'e işle.
4. Sonra çoklu ajan, otomasyon, takım/çalışma alanı paylaşımı ve RBAC fikir havuzundan seçerek ilerle.

### Donanım notu (yerel LLM performansı)

Bu makinede GPU **RTX 3070 (8 GB VRAM)**. Modellerin yerleşimi (`ollama ps` → PROCESSOR sütunu):

| Model | Boyut | Yerleşim | Tipik hız |
|---|---|---|---|
| `gemma4:e2b` | 7.2 GB | **%100 GPU** | en hızlı |
| `gemma4:e4b` | 9.6 GB | çoğunlukla GPU (hafif taşar) | hızlı |
| `gemma4:latest` (varsayılan) | 10 GB | **~%32 GPU / %68 CPU** | ~19 sn / orta |

`gemma4:latest` 8 GB VRAM'e tam sığmadığı için Ollama modeli CPU+GPU böler — bu yüzden VRAM tam dolmaz ve cevap görece yavaştır. **Bu bir yazılım/NOVA sorunu değil, donanım sınırıdır.** Hız önemliyse menüden `gemma4:e2b` seç (tamamen GPU'da koşar). Doğrulama: bir istek atarken `ollama ps` çalıştır; `100% GPU` = tam GPU, `XX%/YY% CPU/GPU` = bölünmüş.

## 🗺️ Yol Haritası (Roadmap)

### Faz 3 — Ajan yetenekleri ✅ (11 Haz 2026 canlı doğrulandı)
- ✅ **Tool/function calling döngüsü** — gateway `agent:true` ile yerel modelin araç çağrılarını yürütür (`lib/agent.mjs` + `lib/tools.mjs`). Araçlar: `web_search`, `doc_search`, `calculator`, `current_time`; opsiyonel `code_run` QuickJS sandbox (`CODE_TOOL_ENABLED=1`). UI'da alt bardaki **Ajan** toggle + araç-kullanım kartı. (Qwen/Titus araç çağırır; Gemma zayıf.)
- ✅ **Web araması (SearXNG)** — self-hosted meta arama (`searxng/`); model güncel bilgiye kaynak linkleriyle erişir. Test: "Ankara hava durumu" → `web_search` çağrıldı, MGM kaynaklı cevap.
- ✅ **RAG / belgelerle sohbet** — `pgvector` (migration `003_knowledge.sql`) + `nomic-embed-text` embeddings (`lib/embed.mjs`, `lib/rag.mjs`, `routes/knowledge.mjs`). Ayarlar'dan belge yükle (metin/.txt/.md/.pdf/.docx), ajan `doc_search` ile kaynak göstererek cevaplar. Test: gizli kod "ZÜMRÜT-7" belgeden bulundu.

### Sıradaki (Faz 4 — ürünleşme & üretkenlik)
- ✅ **Araç genişletme / Faz 4B** — kaynak rozetleri + `doc_search` için PDF/DOCX metin çıkarımı + default kapalı QuickJS `code_run` sandbox + persona/prompt kütüphanesi tamamlandı.
- ✅ **Artifacts paneli** — HTML/SVG/Mermaid önizleme + indirme hazır; Mermaid CDN'siz yerel render ediliyor; hata/boş içerik fallback'i ve scriptsiz sandbox doğrulandı.
- ✅ **Sohbet dışa aktarma + paylaşım** — Markdown/JSON indirme, PDF olarak yazdır/kaydet ve hash tabanlı local paylaşım/import linki hazır.
- ✅ **Persona/prompt kütüphanesi** — Ayarlar'da Genel NOVA, Kod İnceleyici, SOC Analisti, Mimari Planlayıcı, Yerel LLM Koçu ve Özel Persona kartları var; seçim header'a yansır ve IndexedDB'de kalıcıdır.
- ✅ **Çok modlu + ses kuyruğu** — gateway `auto` görsel yönlendirme (`VISION_MODEL`, `ROUTE_VISION`); remote image URL fetch opt-in; BullMQ ses job endpoint'i + UI kuyruk modu. **Canlı doğrulandı (13 Haz):** görsel istek vision modele yönlendi ve model görseli doğru tarif etti; async TTS job kuyruğa girip ses üretti. (`scripts/smoke-live.mjs` ile `SMOKE_VISION=1`/`SMOKE_VOICE=1`.)

### Olgunlaştırma (Faz 5 — prod sertleştirme & güvenlik)
- ⏳ **Güvenlik:** Keycloak prod modu (`start`), tüm varsayılan parolaları rotate, portları iç ağa kapat, `ALLOW_MODELS` allowlist, OIDC issuer'ı env/config'e taşı. (Bkz. [`SECURITY.md`](./SECURITY.md).)
- ⏳ **Gözlemlenebilirlik:** Sentry/OpenTelemetry trace + sürümlü Grafana dashboard'ları; ajan araç-çağrı metrikleri.
- 🟡 **Kod kalitesi:** ✅ ajan-döngüsü smoke testleri (mock'lu) CI'da koşuyor + tek komut `npm run security`; kalan: provider modülü için daha geniş integration testleri.
- 🟡 **Dağıtım:** ✅ CI'da secret scan + audit + canlı `/health`/auth/models smoke; kalan: K8s/HPA manifest'lerini ajan+RAG+voice servisleriyle güncelle.
- ⏳ **Android istemci:** kaynaklar artık `nova-android/` klasöründe; kalan iş `gradle-wrapper.jar` üretmek (`gradle wrapper --gradle-version 8.9`).

### Fikir havuzu (Faz 6+)
- Çoklu ajan iş birliği (paralel alt-görevler), zamanlanmış/otomatik ajan görevleri, MCP sunucu entegrasyonu (harici araçlar), takım/çalışma alanı paylaşımı, ince taneli RBAC.

> Reboot sonrası: bir WSL penceresi aç ve `docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d` çalıştır (docker servisi otomatik başlar, compose native kurulu). WSL kurulum runbook'u: [`WSL_DOCKER.md`](./WSL_DOCKER.md).

## Repository layout

```
Nova_Agent_AI/
├── package.json            # convenience scripts that drive both packages
├── .gitignore
├── gateway.mjs             # thin redirect → gateway/gateway.mjs (kept for old commands)
├── gateway/                # the API gateway (Node, no build step)
│   ├── gateway.mjs         # hardened server: auth, CORS allowlist, rate limit, .env
│   ├── package.json
│   └── .env.example        # copy to .env and fill in
└── web/                    # the browser UI (Vite + React)
    ├── index.html
    ├── vite.config.js
    ├── package.json
    └── src/
        ├── main.jsx        # React entry — mounts <App/>
        └── nova-agent.jsx  # the full UI component
```

## Prerequisites

- **Node.js 20.19 or newer** (`node -v`). The web build uses Vite 8, whose engine requirement is `^20.19.0 || >=22.12.0`.
- At least one model backend:
  - **Local, no key:** [Ollama](https://ollama.com) running on `http://localhost:11434`.
  - **Cloud:** an API key for Anthropic, Gemini, and/or OpenAI.
- (Optional) OpenAI-compatible **Whisper** (STT) and **TTS** servers for real voice mode.

## Quick start

From the project root (`Nova_Agent_AI/`):

```bash
# 1. Install dependencies for both packages
npm run install:all

# 2. Configure the gateway
cd gateway
cp .env.example .env
# open .env and set GATEWAY_TOKEN + at least one provider key (or run Ollama)
cd ..

# 3. Start the gateway (terminal A)
npm run gateway        # → http://localhost:8088/v1

# 4. Start the UI (terminal B)
npm run web            # → http://localhost:5173
```

Open <http://localhost:5173>, click the gear icon, choose the **Gateway** provider
(base URL `http://localhost:8088/v1`), paste your `GATEWAY_TOKEN` into the key field,
and start chatting. Leave the model as **`auto`** to let the gateway pick a model based
on effort and context length.

### Build for production

```bash
npm run build          # outputs static files to web/dist/
npm run preview        # serve the build locally on http://localhost:4173
```

Serve `web/dist/` from any static host and keep the gateway running behind it.

## How model routing works

Send a model string as `"<provider>/<model>"`:

| Example | Goes to |
| --- | --- |
| `ollama/qwen3:14b` | local Ollama |
| `gemini/gemini-2.5-flash` | Google Gemini |
| `anthropic/claude-sonnet-4-20250514` | Anthropic |
| `openai/gpt-4o-mini` | OpenAI |
| `openclaw/<agent>` | OpenClaw agent layer |
| `auto` | dynamic pick (see below) |

**Dynamic routing (`auto`)** chooses by the request's `effort` (`fast`/`balanced`/`deep`/`max`)
and total context length, preferring whichever provider keys are present. Override any tier
with `ROUTE_FAST` / `ROUTE_BALANCED` / `ROUTE_DEEP` / `ROUTE_MAX` in `.env`.

## Endpoints (gateway)

| Method | Path | Purpose | Auth |
| --- | --- | --- | --- |
| `GET` | `/health` | liveness probe | **public** |
| `GET` | `/v1/models` | list of advertised model ids | token |
| `POST` | `/v1/chat/completions` | OpenAI-compatible chat (streaming SSE) | token |
| `POST` | `/stt` | speech-to-text (`{audio, mime, language}` base64) | token |
| `POST` | `/tts` | text-to-speech (returns audio bytes) | token |

## Security

This project handles API keys and arbitrary upstream calls, so the gateway ships with
several protections. **Review these before exposing it beyond `localhost`.**

- **Keep keys on the server.** Prefer the **Gateway** provider in the UI so provider keys
  live only in `gateway/.env` and never reach the browser. The UI *can* call Anthropic/Gemini/OpenAI
  directly with a key you type in, but that key is then stored in the browser and sent from the
  client — use that mode only for local experimentation.
- **`GATEWAY_TOKEN` (bearer auth).** When set, every request except `/health` must send
  `Authorization: Bearer <token>`. The comparison is constant-time. **It is blank by default,
  which leaves the gateway open** — generate one and set it:
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
  ```
- **CORS allowlist (`ALLOW_ORIGINS`).** Only the listed origins may call the gateway from a
  browser (defaults to the Vite dev/preview origins). `*` allows any origin and is for local dev only.
- **Rate limiting (`RATE_MAX` / `RATE_WINDOW_MS`).** Per-IP fixed window (120 req/min by default);
  set `RATE_MAX=0` to disable.
- **Model allowlist (`ALLOW_MODELS`).** Restrict which providers/models can be invoked,
  e.g. `ollama/*,gemini/gemini-2.5-flash`.
- **Input limits.** `BODY_LIMIT` caps request size (25 MB by default, to allow image data URLs),
  `MAX_MESSAGES` caps message count, `MAX_MESSAGE_CHARS` caps total text size, and
  `REQ_TIMEOUT_MS` aborts slow upstreams.
- **Media/image validation.** `/v1/media` accepts only allowlisted MIME types and valid base64;
  remote image URL fetch is opt-in, blocks private/localhost targets, enforces redirects and byte limits,
  and validates data URLs before sending them to vision providers.
- **Minimal health output.** `/health` is public but returns only `{ ok: true }` unless
  `HEALTH_DETAILS_ENABLED=1` is set outside production.
- **Frontend CSP/link safety.** The hosted UI ships with a restrictive CSP; markdown links only allow
  `http:`, `https:` and `mailto:` schemes.
- **Hardening defaults.** `X-Powered-By` is disabled, baseline security headers are sent,
  and in `NODE_ENV=production` upstream error details are hidden from clients.
- **Secrets hygiene.** `.gitignore` excludes `.env` and `node_modules`. Never commit a real `.env`.
- **`TRUST_PROXY`.** Leave at `0` unless the gateway sits behind a trusted reverse proxy
  (only then should `X-Forwarded-For` be honored for rate-limit IPs).

### Recommended production checklist

1. Set a strong `GATEWAY_TOKEN`.
2. Set `ALLOW_ORIGINS` to your exact UI origin(s) — never `*`.
3. Set `NODE_ENV=production`.
4. Put the gateway behind TLS (a reverse proxy such as Caddy/Nginx) and set `TRUST_PROXY=1` there.
5. Consider an `ALLOW_MODELS` allowlist to control cost and exposure.

### Security & quality checks (one command)

Run the full gate before opening a PR or a public release:

```bash
npm run security      # syntax + gateway tests + secret scan + gateway/web audit + web build
```

Individual checks are also available:

```bash
npm run secret-scan   # scan tracked files for leaked keys/tokens (also a CI step)
npm run audit         # npm audit for gateway + web (moderate level)
npm --prefix gateway test   # gateway unit + agent-loop tests (39)
npm run smoke:live    # end-to-end smoke against a RUNNING gateway (see below)
```

`smoke:live` checks `/health`, auth enforcement and `/v1/models` against a live gateway, with
optional chat/agent/RAG checks:

```bash
GATEWAY_URL=http://localhost:8088 GATEWAY_TOKEN=<token> \
  SMOKE_AGENT=1 SMOKE_RAG=1 npm run smoke:live
```

CI (`.github/workflows/ci.yml`) runs install → secret scan → tests → build → audit → live smoke
on Node 20.19 and 22, plus the production-preflight guard and the gateway Docker image build.

## Configuration reference

All gateway settings are environment variables (see `gateway/.env.example` for the full list):

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8088` | gateway port |
| `GATEWAY_TOKEN` | *(empty)* | bearer token; empty = auth disabled |
| `ALLOW_ORIGINS` | Vite dev/preview origins | CORS allowlist (comma list, `*` = any) |
| `RATE_MAX` / `RATE_WINDOW_MS` | `120` / `60000` | per-IP rate limit |
| `ALLOW_MODELS` | *(all)* | model allowlist, supports `provider/*` |
| `BODY_LIMIT` | `25mb` | max JSON body size |
| `MAX_MESSAGE_CHARS` | `100000` | max total text bytes per chat request |
| `MAX_DOC_BYTES` | `1048576` | max extracted knowledge text size |
| `MAX_DOC_FILE_BYTES` | `10485760` | max raw PDF/DOCX/text upload size for knowledge ingest |
| `MAX_MEDIA_BYTES` | `26214400` | max `/v1/media` upload size |
| `ALLOWED_MEDIA_MIME_TYPES` | image/audio/video/pdf allowlist | comma-separated MIME allowlist for `/v1/media` |
| `HEALTH_DETAILS_ENABLED` | `0` | expose non-production `/health` runtime details when set to `1` |
| `CODE_TOOL_ENABLED` | `0` | enable optional local-only QuickJS `code_run` tool (`1` to enable) |
| `CODE_TOOL_TIMEOUT_MS` | `1000` | max code sandbox runtime per call |
| `CODE_TOOL_MEMORY_MB` | `16` | max QuickJS sandbox heap |
| `CODE_TOOL_MAX_CODE_CHARS` / `CODE_TOOL_MAX_OUTPUT_CHARS` | `6000` / `4000` | code and output limits |
| `MAX_MESSAGES` | `400` | max messages per request |
| `REQ_TIMEOUT_MS` | `60000` | upstream timeout |
| `MAX_RETRIES` | `2` | retries on 429/5xx/network errors |
| `TRUST_PROXY` | `0` | trust reverse-proxy headers (`1` to enable) |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY` | *(empty)* | provider keys |
| `OLLAMA_URL` | `http://localhost:11434` | local Ollama base URL |
| `DEFAULT_MODEL` | `ollama/qwen3:14b` | what `auto` falls back to |
| `VISION_MODEL` | `ollama/qwen3.5-omni:latest` | image-containing `auto` requests route here; set to the exact local Qwen 3.5/3.6 tag you installed |
| `ROUTE_VISION` | *(unset)* | optional override for image-containing `auto` requests |
| `REMOTE_IMAGE_URLS_ENABLED` | `0` | fetch remote image URLs for local/non-OpenAI vision providers; private hosts are blocked |
| `REMOTE_IMAGE_MAX_BYTES` | `10485760` | max remote image download size |
| `ROUTE_FAST/BALANCED/DEEP/MAX` | *(unset)* | per-effort model overrides |
| `OPENCLAW_URL` / `OPENCLAW_TOKEN` / `OPENCLAW_PATH` | localhost defaults | OpenClaw agent layer |
| `WHISPER_URL` / `WHISPER_MODEL` | localhost defaults | speech-to-text |
| `TTS_URL` / `TTS_MODEL` / `TTS_VOICE` | localhost defaults | text-to-speech |
| `VOICE_QUEUE_ENABLED` | `0` | enable BullMQ/Redis async STT/TTS jobs |
| `VOICE_QUEUE_CONCURRENCY` | `2` | async voice worker concurrency |
| `VOICE_QUEUE_RESULT_TTL_SEC` | `3600` | how long completed voice job results stay available |
| `VOICE_QUEUE_MAX_AUDIO_BYTES` | `26214400` | max queued STT audio payload size |
| `TTS_MAX_INPUT_CHARS` | `8000` | max TTS input length |

## Voice mode

The UI supports browser speech recognition out of the box. For higher-quality real STT/TTS,
run OpenAI-compatible servers and point `WHISPER_URL` / `TTS_URL` at them (the UI calls
`/stt` and `/tts` on the gateway, which proxies to those servers). Enable "real voice" in the
UI settings and set the STT/TTS URLs to `http://localhost:8088/stt` and `http://localhost:8088/tts`.

For longer local voice jobs, set `VOICE_QUEUE_ENABLED=1` with Redis available. The gateway then
exposes `POST /v1/voice/jobs`, `GET /v1/voice/jobs/:id`, and `GET /v1/voice/jobs/:id/audio`.
In the UI, enable **Gerçek ses** and **Kuyruk**; the default job endpoint is `/v1/voice/jobs`.

## Troubleshooting

- **`401 unauthorized`** — `GATEWAY_TOKEN` is set on the gateway but the UI isn't sending it.
  Paste the token into the Gateway provider's key field in the UI settings.
- **CORS error in the browser console** — the UI origin isn't in `ALLOW_ORIGINS`. Add it
  (e.g. `http://localhost:5173`) and restart the gateway.
- **Ollama "connection refused"** — start it with browser access allowed:
  `OLLAMA_ORIGINS=* ollama serve`.
- **`429 rate limit exceeded`** — you hit `RATE_MAX`; raise it or wait for the window to reset.
- **Empty/garbled stream** — confirm the chosen provider's key is set in `gateway/.env`
  and the model id exists for that provider.

## License

MIT. See [`LICENSE`](./LICENSE).
