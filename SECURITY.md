# Güvenlik

NOVA API anahtarları ve upstream çağrıları yönettiği için güvenlik dikkat ister.
Bu dosya, depoyu paylaşmadan/yayına almadan önce yapılması gerekenleri özetler.

## Sırlar (secrets) asla commit edilmez

`.gitignore` şunları hariç tutar: `.env`, `.env.*` (sadece `.env.example` izlenir),
`node_modules/`, `web/dist/`, `*.log`. **Gerçek `.env` dosyanı asla commit etme.**

Repodaki compose/ayar dosyalarındaki değerler **yalnızca yerel geliştirme varsayılanlarıdır**
ve hepsi env değişkeniyle override edilebilir:

| Değişken | Varsayılan (dev) | Override (prod) |
|---|---|---|
| `POSTGRES_PASSWORD` | `nova` | güçlü değer |
| `MINIO_USER` / `MINIO_PASSWORD` | `minio` / `minio12345` | güçlü değer |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | `admin` / `admin` | güçlü değer |
| `GRAFANA_PASSWORD` | `admin` | güçlü değer |
| `SEARXNG_SECRET` | dev string | rastgele 32+ bayt |
| `GATEWAY_TOKEN` | *(boş)* | rastgele 32 bayt hex |
| `CSP_CONNECT_SRC` | local/dev origin'leri | public'te `'self'` veya tam güvenilir origin listesi |

Üret: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`

> **Keycloak realm:** `keycloak/nova-realm.json` yalnız `nova-web` public client'ını
> import eder; varsayılan kullanıcı veya parola import edilmez. Yerel ve production
> kullanıcılarını Keycloak admin panelinden oluştur. Realm import sadece ilk kurulumda
> çalışır.

## Üretim (production) kontrol listesi

1. **`GATEWAY_TOKEN`** güçlü ayarla (veya MULTI_USER + OIDC/JWT kullan).
2. **`ALLOW_ORIGINS`** tam UI origin'lerine kısıtla — asla `*` değil.
3. **`NODE_ENV=production`** — gateway, boş token / `*` CORS ile başlamayı reddeder
   ve upstream hata detaylarını gizler (preflight guard).
4. **TLS** — Caddy/Nginx arkasında çalıştır, gateway'de `TRUST_PROXY=1`.
5. **Tüm varsayılan parolaları değiştir** (yukarıdaki tablo).
6. **Keycloak `start-dev` yerine `start`** (prod modu) + gerçek hostname/TLS.
7. **`ALLOW_MODELS`** allowlist ile maliyet/erişim kontrolü.
8. **`CSP_CONNECT_SRC`** public'te local/dev origin veya wildcard içermemeli; mümkünse
   sadece `'self'` kullan.
9. Portları dışarı açma: yalnızca Caddy 80/443 publish; Postgres/Redis/MinIO/
   Keycloak/Grafana/SearXNG iç ağda kalsın (compose'da gerektiğinde `ports` kaldır).
10. **OIDC issuer'ı boş bırakma:** `OIDC_JWKS_URL` kullanılıyorsa `OIDC_ISSUER`
   de set edilmeli; production preflight bunu zorlar.
11. **Bağımlılık audit'i:** public release öncesi `npm.cmd --prefix gateway audit`
   ve `npm.cmd --prefix web audit` 0 vulnerability dönmeli.
12. **Otomatik kontrol:** prod `.env`'i source ettikten sonra `npm run prod-check`
   çalıştır. Artık şunları da doğrular: `GATEWAY_TOKEN` gücü (≥32), multi-user için
   `DATABASE_URL`/`ADMIN_USER_IDS`, varsayılan/zayıf altyapı parolaları
   (Postgres/Keycloak/MinIO/Redis), public CSP `connect-src`, Keycloak Caddy host'u
   ve cleartext (`http://`) MCP sunucuları.
   Ayrıca prod'da gateway, çok kısa (`<24`) `GATEWAY_TOKEN` ile başlamayı reddeder.

## Production compose overlay (Faz 8)

İç servis panelleri (Keycloak, SearXNG, MinIO, Prometheus, Grafana, Whisper, TTS)
artık `docker-compose.faz2.yml`'de **yalnızca `127.0.0.1`'e** bağlanır — host'tan
erişilir ama ağdan/internetten erişilemez. Yalnızca Caddy 80/443 publish eder.

Production için sertleştirme overlay'ini ekle:

Prod `.env` içinde güçlü secret'lar, `KC_HOSTNAME`, `ALLOW_ORIGINS` ve
`CSP_CONNECT_SRC` hazırlandıktan sonra:

```bash
npm run prod-check
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml`: Keycloak'ı **`start`** (prod modu) ile çalıştırır,
`NODE_ENV=production` + `TRUST_PROXY=1` set eder, `restart: unless-stopped` ekler ve
`${VAR:?...}` ile **zorunlu secret'lar** (Keycloak/Postgres/MinIO/Grafana parolaları,
`KC_HOSTNAME`, `KC_HOSTNAME_HOST`, `ALLOW_ORIGINS`) atanmadan başlatmayı engeller. Prod overlay
ayrıca Caddy `connect-src` değerini varsayılan olarak `'self'` yapar; farklı UI/API origin'i
varsa `CSP_CONNECT_SRC` yalnız tam güvenilir origin'lerle genişletilmeli. Panellere erişim için
SSH tüneli kullan ya da her alt-alan için kimlik doğrulamalı bir Caddy route ekle.

## Uygulama içi korumalar (gateway)

- **Auth:** her istek (`/health` `/metrics` hariç) bearer token veya OIDC JWT / API key ister; sabit-zamanlı karşılaştırma.
- **CORS allowlist**, **rate limit** (IP + kullanıcı bazlı, Redis), **kota** (micro-dollar), **body limit**, **mesaj sayısı/metin limiti**, **upstream timeout**.
- **Güvenlik başlıkları:** `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `COOP`, CSP; `X-Powered-By` kapalı.
- **Public health:** `/health` public kalır ama production'da yalnız `{ ok: true }` döner; runtime model/backend detayları sızmaz.
- **Ajan araçları:** `web_search` yalnızca iç SearXNG'e gider. `doc_search` kullanıcıya kapsamlı (user_id izolasyonu). `calculator` sıkı allowlist'li güvenli eval (200 krktr sınırı, yalnız izinli Math adları). `code_run` QuickJS sandbox default kapalıdır.
- **Dosya/görsel girişleri:** remote image URL fetch opt-in'dir, localhost/private IP hedefleri engellenir, redirect ve byte limitleri uygulanır. `/v1/media` yalnız allowlist MIME tiplerini ve geçerli base64'i kabul eder; HTML/SVG gibi aktif içerikler default kabul edilmez.
- **Sırlar sunucuda:** Gateway provider modunda sağlayıcı anahtarları yalnız `gateway/.env`'de; tarayıcıya gitmez.

## Windows native mobile worker

The Windows launcher accepts only the ignored `mobile-worker/.env`; it parses
noncomment `KEY=value` entries without executing them and never prints loaded
values. Its prepare-only mode validates local paths and tools but does not create
an environment, sync packages, start or stop ADB, change firewall rules, or modify
WSL/Ollama state.

The worker is forced to the existing loopback ADB endpoint at `127.0.0.1:5037`.
It removes a raw Ollama URL and lets the Windows-only resolver derive the WSL NAT
address from one validated `172.16.0.0/12` route source. Do not publish ADB,
Ollama, the worker, or Portal to the LAN. Portal setup is a later explicit
operation after local readiness and real Portal/Gateway smoke evidence.

## Açık bildirimi (disclosure)

Bir güvenlik açığı bulursan lütfen public issue açmadan önce sorumlu şekilde bildir.
