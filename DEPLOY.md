# NOVA — Faz 0 deploy runbook

Bu, NOVA'yı "gönderilebilir" hale getiren minimum production-ish kurulumdur:
gateway (Node) → Caddy (otomatik TLS) arkasında, `web/dist` aynı domainden
servis edilir. Tek paylaşılan token modelidir — **kapalı beta** için uygundur;
birbirinden bağımsız çok kullanıcı için Faz 1'e (Postgres + Redis + gerçek auth)
geçilmelidir (bkz. `NOVA_Mimari_Inceleme.md`).

## Bu fazda eklenenler

| Dosya | Amaç |
|---|---|
| `gateway/Dockerfile` | Gateway'i üretim imajı olarak paketler (Node 22, prod deps, non-root, healthcheck) |
| `gateway/.dockerignore` | `node_modules` / `.env`'i imaja sızdırmaz |
| `Caddyfile` | TLS sonlandırma + `web/dist` servis + `/health /v1 /stt /tts` reverse proxy |
| `docker-compose.yml` | gateway + caddy servisleri, healthcheck, kalıcı TLS volume'leri |
| `gateway.mjs` (guard) | `NODE_ENV=production` iken boş token / `*` CORS varsa **başlamayı reddeder** |
| `.github/workflows/ci.yml` | install · web build · gateway smoke test · imaj build |

## Adımlar

### Dinleme adresi

Doğrudan `node gateway/gateway.mjs` çalıştırılırsa `GATEWAY_BIND` boş/yok olduğunda
dinleyici `127.0.0.1` olur. LAN için `gateway/.env` içinde `GATEWAY_BIND=0.0.0.0`
ve kimlik doğrulama ayarlanmalıdır; loopback dışındaki kimlik doğrulamasız başlangıç
reddedilir. Üretim token uzunluğu ve CORS kontrolleri ayrıca uygulanır.

Docker'da iç dinleyici `0.0.0.0` olarak sabittir. Kök `.env` içindeki
`GATEWAY_BIND` ise host portunun hangi arayüzde yayımlandığını belirler ve varsayılanı
`127.0.0.1` olur. Bu iki ayar birbirinden ayrıdır.

Güncel yayın engelleri: [yayın hazırlık kaydı](docs/RELEASE-READINESS-2026-09-25.md).

```bash
# 1) Gateway env'ini hazırla
cp gateway/.env.example gateway/.env
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"   # GATEWAY_TOKEN üret
#   gateway/.env içinde en az şunları doldur:
#     GATEWAY_TOKEN=<üretilen>
#     ALLOW_ORIGINS=https://nova.example.com     (asla "*")
#     en az bir sağlayıcı anahtarı (ANTHROPIC_API_KEY / GEMINI_API_KEY / OPENAI_API_KEY) veya Ollama
#   NODE_ENV ve TRUST_PROXY compose tarafından zaten production/1 yapılır.

# 2) Web'i derle (Caddy bunu servis eder)
npm run install:all
npm run build

# 3) Domain'i ver ve ayağa kaldır
export DOMAIN=nova.example.com      # gerçek domain → otomatik TLS. Yerel test için boş bırak (localhost)
export CSP_CONNECT_SRC="'self'"     # public UI yalnız aynı origin gateway'e bağlansın
docker compose up -d --build

# 4) Doğrula
curl -fsS https://$DOMAIN/v1/models -H "Authorization: Bearer <GATEWAY_TOKEN>"
docker compose ps
docker compose logs -f gateway
```

> Guard sayesinde `GATEWAY_TOKEN` boşsa gateway konteyneri bilerek hata verip
> yeniden başlar (fail-fast). Token'ı `gateway/.env`'e koyduğundan emin ol.

## İstemci ayarları

- **Web:** Caddy ile aynı domainden servis edildiği için UI'da Gateway base URL'i
  `https://nova.example.com/v1`, key alanına `GATEWAY_TOKEN`.
- **Android:** ⚙️ → Base URL `https://nova.example.com/v1`, token. Production'da
  `AndroidManifest.xml`'deki `usesCleartextTraffic="true"` kaldırılmalı (artık `https`).

## Android gradle wrapper ✅ (paketlendi)

Gradle wrapper (jar + `gradlew`/`gradlew.bat`, 8.9) artık repoda dahil; `./gradlew`
doğrudan çalışır (JDK 17 + Android SDK ile). Ek bir manuel adım gerekmez.

```bash
cd nova-android && ./gradlew assembleDebug
```

## Tam production VPS kurulumu

Çok kullanıcılı, sertleştirilmiş tam stack (Postgres/Keycloak/MinIO/…) için:
**[`DEPLOY_VPS.md`](./DEPLOY_VPS.md)**.

## Sonraki faz

Faz 1: Postgres + Redis, OIDC/JWT + kullanıcı başına API key, dağıtık rate limit,
kalıcı sohbet geçmişi, kullanım ölçümü + kota. Ayrıntı ve hazır kod parçacıkları
`NOVA_Mimari_Inceleme.md` Bölüm 7–9'da.
