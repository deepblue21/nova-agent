# NOVA — Production VPS Deploy Runbook (Docker)

Full multi-user stack on your own Linux VPS: gateway + Postgres + Redis + Keycloak +
MinIO + SearXNG + Prometheus/Grafana + voice, behind **Caddy** with automatic TLS.
Builds on `docker-compose.yml` + `docker-compose.faz2.yml` + `docker-compose.prod.yml`.

> For a quick single-token (no DB) deploy, see [`DEPLOY.md`](./DEPLOY.md). This file is
> the hardened, multi-user production path. Read [`SECURITY.md`](./SECURITY.md) first.

## 0. Prerequisites

- A Linux VPS (2 vCPU / 4 GB RAM minimum; more for local LLMs) with a public IP.
- A domain you control. Create DNS **A records** pointing to the VPS:
  - `nova.example.com`  → app (UI + API)
  - `auth.example.com`  → Keycloak (OIDC login)
- Docker Engine + Docker Compose plugin installed.
- Ports **80** and **443** open in the VPS firewall; nothing else needs to be public.

```bash
# firewall: allow only SSH + HTTP/HTTPS (example with ufw)
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw enable
```

## 1. Clone + production .env

```bash
git clone <repo-url> nova && cd nova
cp gateway/.env.example gateway/.env
```

Edit `gateway/.env` with **strong, unique** values (never commit it):

```ini
NODE_ENV=production
MULTI_USER=on
DATABASE_URL=postgres://nova:<STRONG_PG_PW>@postgres:5432/nova
REDIS_URL=redis://redis:6379
ALLOW_ORIGINS=https://nova.example.com
ADMIN_USER_IDS=<your-user-uuid-after-bootstrap>     # fill in after step 6
ALLOW_MODELS=ollama/*,anthropic/claude-sonnet-4-20250514   # optional, cost control
# at least one provider key OR a reachable Ollama (OLLAMA_URL)
ANTHROPIC_API_KEY=...
# OIDC (browser issuer = the auth subdomain; JWKS reached internally)
OIDC_ISSUER=https://auth.example.com/realms/nova
OIDC_JWKS_URL=http://keycloak:8081/realms/nova/protocol/openid-connect/certs
```

Export the deploy-time variables consumed by compose / Caddy (put these in a root
`.env` next to the compose files, or your shell — compose reads the root `.env`):

```ini
DOMAIN=nova.example.com
KC_HOSTNAME=https://auth.example.com
KEYCLOAK_ADMIN=novaadmin
KEYCLOAK_ADMIN_PASSWORD=<STRONG>
POSTGRES_PASSWORD=<STRONG_PG_PW>
MINIO_USER=novaminio
MINIO_PASSWORD=<STRONG>
GRAFANA_PASSWORD=<STRONG>
SEARXNG_SECRET=<random 32+ bytes>
ALLOW_ORIGINS=https://nova.example.com
```

Generate secrets: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`

## 2. Validate before starting

```bash
set -a; . ./gateway/.env; . ./.env; set +a    # source both into the env
npm run prod-check
```

Fix every `✗` (hard failure) before continuing — it checks token strength, CORS,
default/weak secrets (Postgres/Keycloak/MinIO), multi-user DB/admins, and cleartext MCP.

## 3. Build the web UI

```bash
npm --prefix web ci && npm --prefix web run build   # → web/dist (Caddy serves it)
```

## 4. Add the Keycloak route to Caddy

Append an auth-subdomain block to `Caddyfile` so the browser can reach Keycloak over
TLS (Keycloak itself stays loopback-only on the host):

```caddy
{$KC_HOSTNAME_HOST:auth.localhost} {
	encode gzip
	reverse_proxy keycloak:8081
}
```

Set `KC_HOSTNAME_HOST=auth.example.com` in the root `.env`. (Caddy reaches `keycloak:8081`
over the internal Docker network; the public `127.0.0.1:8081` host bind is only for admin.)

## 5. Bring up the hardened stack

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d --build
```

`docker-compose.prod.yml` runs Keycloak in `start` (production) mode, sets
`NODE_ENV=production` + `TRUST_PROXY=1`, adds `restart: unless-stopped`, and **fails fast**
if any required secret / `KC_HOSTNAME` / `ALLOW_ORIGINS` is missing. DB migrations run
automatically via the one-shot `migrate` service in the base compose.

Caddy provisions TLS for `nova.example.com` and `auth.example.com` automatically (Let's
Encrypt) on first start — make sure DNS already resolves to the VPS.

## 6. First admin user

```bash
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 1000
# note the printed user id → put it in ADMIN_USER_IDS in gateway/.env, then:
docker compose up -d gateway
```

In Keycloak admin (`https://auth.example.com`, the bootstrap admin you set): create real
users / configure the `nova` realm, and **delete the demo user** (`demo@example.local`)
shipped in `keycloak/nova-realm.json`.

## 7. Verify

```bash
curl -fsS https://nova.example.com/health
curl -fsS https://nova.example.com/v1/models -H "Authorization: Bearer <token-or-skip-if-OIDC>"
docker compose ps         # all healthy
docker compose logs -f gateway
```

Open `https://nova.example.com` → ⚙ Settings → Sign in with Keycloak → chat.

## 8. Operations

- **Internal panels** (Grafana/Prometheus/MinIO/SearXNG/Keycloak-admin) are loopback-only.
  Reach them via an SSH tunnel: `ssh -L 3001:127.0.0.1:3001 user@vps` → open `localhost:3001`.
- **Backups** — snapshot the Postgres volume regularly:
  ```bash
  docker compose exec postgres pg_dump -U nova nova | gzip > nova-$(date +%F).sql.gz
  ```
  Also back up the `minio_data` and `keycloak_data` volumes.
- **Update / redeploy:**
  ```bash
  git pull && npm --prefix web run build
  docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d --build
  ```
- **Rollback:** `git checkout <prev-sha>` → rebuild, or `docker compose down` then redeploy
  the previous images. Postgres data persists in its volume across redeploys.
- **Secret rotation:** change the value in `.env` / `gateway/.env`, then
  `docker compose ... up -d` the affected service. Rotate `GATEWAY_TOKEN`, provider keys,
  and DB/Keycloak/MinIO passwords periodically.
- **Logs/metrics:** gateway emits pino JSON logs + Prometheus `/metrics`; opt into OTLP
  tracing with `OTEL_EXPORTER_OTLP_ENDPOINT`. Grafana dashboard auto-provisions.

## 9. Android client (prod)

Point the app at `https://nova.example.com/v1`. Remove `usesCleartextTraffic="true"` from
`nova-android/app/src/main/AndroidManifest.xml` (prod is HTTPS). Build with
`cd nova-android && ./gradlew assembleDebug` (JDK 17 + Android SDK).
