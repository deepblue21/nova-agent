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

Üret: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`

> **Keycloak realm:** `keycloak/nova-realm.json` içindeki demo kullanıcı
> (`demo@example.local` / `nova-local-dev`) **yalnızca yerel testlerdir**. Prod'da: gerçek kullanıcıları
> Keycloak admin panelinden yönet, realm dosyasındaki demo kullanıcıyı kaldır
> veya şifresini değiştir. Realm import sadece ilk kurulumda çalışır.

## Üretim (production) kontrol listesi

1. **`GATEWAY_TOKEN`** güçlü ayarla (veya MULTI_USER + OIDC/JWT kullan).
2. **`ALLOW_ORIGINS`** tam UI origin'lerine kısıtla — asla `*` değil.
3. **`NODE_ENV=production`** — gateway, boş token / `*` CORS ile başlamayı reddeder
   ve upstream hata detaylarını gizler (preflight guard).
4. **TLS** — Caddy/Nginx arkasında çalıştır, gateway'de `TRUST_PROXY=1`.
5. **Tüm varsayılan parolaları değiştir** (yukarıdaki tablo).
6. **Keycloak `start-dev` yerine `start`** (prod modu) + gerçek hostname/TLS.
7. **`ALLOW_MODELS`** allowlist ile maliyet/erişim kontrolü.
8. Portları dışarı açma: yalnızca Caddy 80/443 publish; Postgres/Redis/MinIO/
   Keycloak/Grafana/SearXNG iç ağda kalsın (compose'da gerektiğinde `ports` kaldır).
9. **OIDC issuer'ı boş bırakma:** `OIDC_JWKS_URL` kullanılıyorsa `OIDC_ISSUER`
   de set edilmeli; production preflight bunu zorlar.
10. **Bağımlılık audit'i:** public release öncesi `npm.cmd --prefix gateway audit`
   ve `npm.cmd --prefix web audit` 0 vulnerability dönmeli.
11. **Otomatik kontrol:** prod `.env`'i source ettikten sonra `npm run prod-check`
   çalıştır. Artık şunları da doğrular: `GATEWAY_TOKEN` gücü (≥32), multi-user için
   `DATABASE_URL`/`ADMIN_USER_IDS`, varsayılan/zayıf altyapı parolaları
   (Postgres/Keycloak/MinIO/Redis) ve cleartext (`http://`) MCP sunucuları.
   Ayrıca prod'da gateway, çok kısa (`<24`) `GATEWAY_TOKEN` ile başlamayı reddeder.

## Uygulama içi korumalar (gateway)

- **Auth:** her istek (`/health` `/metrics` hariç) bearer token veya OIDC JWT / API key ister; sabit-zamanlı karşılaştırma.
- **CORS allowlist**, **rate limit** (IP + kullanıcı bazlı, Redis), **kota** (micro-dollar), **body limit**, **mesaj sayısı/metin limiti**, **upstream timeout**.
- **Güvenlik başlıkları:** `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `COOP`, CSP; `X-Powered-By` kapalı.
- **Public health:** `/health` public kalır ama production'da yalnız `{ ok: true }` döner; runtime model/backend detayları sızmaz.
- **Ajan araçları:** `web_search` yalnızca iç SearXNG'e gider. `doc_search` kullanıcıya kapsamlı (user_id izolasyonu). `calculator` sıkı allowlist'li güvenli eval (200 krktr sınırı, yalnız izinli Math adları). `code_run` QuickJS sandbox default kapalıdır.
- **Dosya/görsel girişleri:** remote image URL fetch opt-in'dir, localhost/private IP hedefleri engellenir, redirect ve byte limitleri uygulanır. `/v1/media` yalnız allowlist MIME tiplerini ve geçerli base64'i kabul eder; HTML/SVG gibi aktif içerikler default kabul edilmez.
- **Sırlar sunucuda:** Gateway provider modunda sağlayıcı anahtarları yalnız `gateway/.env`'de; tarayıcıya gitmez.

## Açık bildirimi (disclosure)

Bir güvenlik açığı bulursan lütfen public issue açmadan önce sorumlu şekilde bildir.
