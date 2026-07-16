# NOVA Agent

**English** · [Türkçe](./README.tr.md)

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

## Local-use status

If this repo is made public, people can run it **locally on their own machines**. The
sharing goal is a **local-first / self-hosted developer experience** — not a hosted,
centrally-operated production service. The production/security notes below exist to
keep you from accidentally exposing the gateway to the internet.

### Minimum requirements

| Use type | Minimum | Note |
| --- | --- | --- |
| Bare-metal trial | Git, Node.js 20.19+, npm, a modern browser | Just `gateway` + `web`; fastest path for single-user/dev. |
| Model access | At least one provider API key **or** local Ollama | OpenAI/Anthropic/Gemini keys work; Ollama recommended for local. |
| Docker full stack | Docker Engine + Docker Compose; WSL Ubuntu on Windows | Postgres, Redis, Keycloak, MinIO, SearXNG, Grafana, voice services. |
| Hardware | 8 GB RAM minimum; 16 GB recommended | With a local LLM, GPU/VRAM and model size drive performance. |
| Ports | `80`/`443` for the entry proxy | Internal panels bind to `127.0.0.1` only (see Security). |

### Public-repo checklist

- MIT licensed; users may use, modify and distribute. Keep the license + copyright notice.
- Never commit a real `.env`, API key, token, admin password or user data.
- Keep local default passwords clearly dev-only; do not expose externally without applying
  the [`SECURITY.md`](./SECURITY.md) checklist.
- Keep `gateway/.env.example` current; real values are created per-machine by the user.
- A hosted/production service is out of scope. Exposing to the internet additionally
  requires TLS, a domain, secret rotation, `ALLOW_ORIGINS`, Keycloak production mode and
  closed internal ports.

## Quick start

Two paths: **Docker (recommended — full stack)** or **bare-metal (gateway + web only)**.

### Path A — Docker full stack (Postgres, Redis, Keycloak, SearXNG, MinIO, Grafana, voice)

Needs Docker + docker compose, and [Ollama](https://ollama.com) for local LLMs.

1. Clone the repo.

```bash
git clone <repo-url> nova && cd nova
```

2. Create the gateway env. Generate a token and set `ALLOW_ORIGINS` plus at least one
   provider key or `OLLAMA_URL` in `gateway/.env`.

```bash
cp gateway/.env.example gateway/.env
node -e "console.log('GATEWAY_TOKEN='+require('crypto').randomBytes(32).toString('hex'))" >> gateway/.env
```

3. Build the web UI. Caddy serves `web/dist`.

```bash
npm --prefix web ci && npm --prefix web run build
```

4. For local LLMs, expose Ollama to the containers and pull models. On WSL/Linux,
   start Ollama with `OLLAMA_HOST=0.0.0.0` first.

```bash
ollama pull qwen3:14b && ollama pull gemma4:e2b && ollama pull nomic-embed-text
```

Optional strongest local tool-calling model if your machine can run it:

```bash
ollama pull qwen3.6:35b
```

For vision, the tag varies by setup:

```bash
ollama pull qwen3.5-omni:latest
```

5. Bring the stack up. First run pulls images and can take a few minutes.

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build
```

6. Create the first user and API key in multi-user mode.

```bash
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 5
```

Open **http://localhost**, go to **Settings**, then either sign in with Keycloak after
creating a user in Keycloak admin or paste the generated API key.
Internal panels are loopback-only by default (reach them from the host machine): Grafana
`127.0.0.1:3001`, Prometheus `:9090`, MinIO `:9001`, SearXNG `:8080`, Keycloak `:8081`.

> Important: all default passwords are **for local development only.** Before going
> public, read **[`SECURITY.md`](./SECURITY.md)** and change them all. Windows + WSL
> setup: **[`WSL_DOCKER.md`](./WSL_DOCKER.md)**.

### Path B — Bare-metal (single-user gateway + web, no DB)

Install dependencies and create the gateway env:

```bash
git clone <repo-url> nova && cd nova
npm run install:all
cp gateway/.env.example gateway/.env
```

Set `GATEWAY_TOKEN` plus a provider key or Ollama in `gateway/.env`. Then start the
gateway in one terminal and the web UI in another:

```bash
npm run gateway
```

```bash
npm run web
```

Agent mode (web search + chat-with-docs) and multi-user/history/quota need **Path A**.

## Project status & roadmap

Work toward a public, multi-user deployment is ongoing. See [`PROGRESS.md`](./PROGRESS.md)
for the live status and next step. Related docs:

- [`PROGRESS.md`](./PROGRESS.md) — current status + next step (start here)
- [`NOVA_Mimari_Inceleme.md`](./NOVA_Mimari_Inceleme.md) — full architecture review, as-is/to-be, phase map
- [`DEPLOY.md`](./DEPLOY.md) — Phase 0 deploy runbook (Docker + Caddy/TLS + CI)
- [`PHASE1.md`](./PHASE1.md) — Phase 1 (multi-user: auth, history, quota)
- [`PHASE2.md`](./PHASE2.md) — Phase 2 (observability, object storage, billing, K8s/HPA, voice)
- [`nova-android/README.md`](./nova-android/README.md) — the Android client

### Project Horus: Android-first mobile agent

Project Horus is the active Android-first track in this repository. It uses the Nova
Gateway for local PC/GPU inference and the Android client as the task-control surface.
This section is a living delivery record: it is updated after every completed Horus task
with both verified work and the next concrete work item.

**Completed and verified**

- Persistent authenticated mobile tasks, replayable SSE events, pause/resume/cancel, and
  R2/R3 confirmation records in the Gateway.
- Android **Tasks** workspace with task creation, timeline replay, controls, and risk
  confirmation UI; unit, lint, APK, and emulator Compose checks passed.
- Docker mobile-control-plane smoke that creates, reads, pauses, resumes, cancels, and
  replays task events without printing the API key.
- Debug APK: `nova-android/app/build/outputs/apk/debug/app-debug.apk`.
- Task 7 control-center verification delivered the fixed task-first **Tasks / Chat / Voice**
  navigation while retaining the adaptive launcher icon. The full gate passed with 50 unit tests,
  27 connected Android tests on Android 17 `emulator-5554`, zero lint errors (11 warnings and one
  informational issue), and a successful debug APK install/launcher resolution. No physical ADB
  serial was connected, so physical-phone testing was not performed. The verified APK SHA-256 is
  `4D65812810CBC0C6D80081CC40A5FF716A3A52829A68EB049C6D7681A104E689`.
- Final regression hardening pins each running task to its original Gateway, rejects stale or
  foreign-task callbacks/events, canonicalizes accepted Gateway addresses to `/v1`, and fails
  malformed saved addresses safely. TalkBack editing semantics remain available for the masked
  token field; bottom insets, busy voice controls, and loading-state task prompts are also covered.
- The in-app Gateway probe reached `PC ready` with a local QA identity. The fixed connectivity
  prompt completed through the PC model with UI-tree-sampled TTFT 48.337 s, total 48.341 s, and
  sanitized route `ollama/gemma4:latest`; no raw model body or credential is recorded here.
- Worker preflight passed (7 Node tests and 39 Python tests), but the safe live Android-version
  attempt was rejected before task creation by the Gateway allowlist with the sanitized message
  `Bu gorev emulator worker'inda desteklenmiyor`; no terminal worker run is claimed.
- The Task 7 regression pass resolved the Settings/status-bar overlap at system font scales 1.0
  and 1.3, and kept the Tasks composer plus primary action fully above a real IME at 1.3. The best
  warm no-dump debug-emulator sample still recorded 37/69 janky frames (53.62%), p50 34 ms and
  p90 44 ms. Perfetto evidence points to mixed emulator graphics/buffer pressure and Compose work,
  without one proven app-owned hotspot; release-build performance on physical hardware remains a
  follow-up rather than a claimed benchmark.
- Dedicated worker-goal policy and worker-only authentication are complete.
- Gateway worker leases are persisted with token hashes only. Focused semantic store tests now
  distinguish an unknown task (`404`) from a valid task with a missing, stale, inactive, or
  wrong lease (`409`) for both status and report operations; events and persisted records never
  contain the lease token.
- Worker-only Gateway control routers are constructed from factories after the local `.env` loader,
  then mounted after baseline middleware and before user-principal authentication. Dedicated worker
  bearer auth gates claim, status, report, and expiry endpoints; claim alone returns the one-time
  opaque `lease.token` needed by the worker, while status/report responses never expose it.
- Verified Task 3 correction: worker bearer auth and its static safe `500` boundary are scoped to
  `/v1/internal/mobile-worker`, so public `/health` and ordinary Gateway routes continue through
  the mounted router in both worker modes without exposing unexpected store-error details.
- Task 4: the isolated `mobile-worker` package pins `mobilerun==0.6.10` and `httpx`, accepts only
  `emulator-5554` and local Ollama, redacts its worker token, keeps lease headers at the worker HTTP
  boundary, and emits only bounded safe reports and logs. Fresh active-lease checks now precede
  readiness, agent work, and every report; one monitored-task path cancels and awaits readiness or
  execution when pause, cancel, or lease loss wins. Private Mobilerun ping is bounded and reaped,
  screenshot streaming is forced off, Ollama HTTP timeouts map to `waiting_for_compute`, and report
  phase/error values are checked against the Gateway allowlists locally. `uv lock --check` and the
  standard-library worker suite pass. Live emulator, Portal, Gateway, and local Ollama integration
  is intentionally deferred to Tasks 6-7.
- Task 5: Gateway persists replay-safe worker reports with `status` plus only the parsed bounded
  `summary`, `steps`, and `error_code` fields; worker tokens, hashes, and raw input never enter the
  event payload. Android replays the Gateway event as `COMPLETED` with `Android 17` and derives the
  visible matching-task status from the newest numeric status event, so a delayed older
  `worker.running` event cannot regress completion. Strict worker-only task-creation rejections map
  to the safe Turkish message only for the exact error literal; other `400` responses remain generic.
  Focused JVM tests and full unit/lint/debug-APK verification pass; terminal Compose coverage also
  passed on Pixel_10_Pro_XL (Android 17), rendering `COMPLETED` and `Android 17` from the sanitized
  worker event.
- Android adaptive launcher icon: the manifest resolves standard and round launcher icons to native
  API 26+ foreground/background XML with graphite `#10242D`, a safe-zone turquoise and light signal,
  and an amber core; Android 13+ overlays add the single-path themed monochrome silhouette. Resource
  processing, lint, debug APK assembly, and installation passed; `emulator-5554` resolves
  `com.nova.agent/.MainActivity`.
- Task 6 status: Linux ADB is installed and loopback Gateway wiring is verified, but the
  WSL-to-Windows ADB bridge is NOT verified. Firewall elevation was requested and the Windows UAC
  request was canceled. No broad firewall rule, public ADB, Portal, or Mobilerun workaround was used.
- Task 6A: focused worker tests verify validated remote ADB endpoint settings and propagation to
  the Mobilerun readiness ping. The WSL-to-Windows bridge itself remains unverified.
- Task 1: the Windows-native worker can derive its Ollama URL only from a validated WSL distro's
  `ip -4 route get 1.1.1.1` output. It uses an argument-list-only `wsl.exe` invocation, accepts
  exactly one `src` IPv4 address in `172.16.0.0/12`, and constructs `http://<ip>:11434`; invalid
  distro values, non-Windows hosts, failed lookups, other ranges, and a nonempty raw Ollama URL in
  WSL mode are rejected. Focused configuration tests pass.
- Task 2: the Windows-native launcher treats the ignored worker-only `mobile-worker/.env` as the
  complete configuration: it clears every supported worker setting inherited from the parent
  process before importing only explicit allowlisted file values, and rejects gateway-only keys.
  It redacts every loaded value in its prepare-only output, finds Windows `adb.exe`, forces the worker to
  `127.0.0.1:5037`, and uses a separate `mobile-worker/.venv-windows`. It deletes any raw Ollama
  URL before selecting the validated WSL distro, so Task 1 derives the WSL NAT address rather than
  accepting a second endpoint. The local-only preflight does not create a venv, sync packages, or
  change ADB, firewall, WSL, or Ollama state. ADB, Ollama, and the worker remain unpublished to the
  LAN; Portal setup remains an explicit later action.

  From this checkout path, `C:\Users\salih\Project_Horus`, run exactly:

  ```powershell
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-windows-mobile-worker.ps1 -PrepareOnly
  ```

  `-PrepareOnly` does not set up Portal, assert the exact emulator device state, call Ollama, sync
  packages, or run the worker. It is a local launcher preflight only.

**Next**

- Set up the local Portal, then run the real runtime checks for the exact emulator and derived
  WSL Ollama readiness with the corrected worker-only `.env.example`, followed by the Gateway
  smoke. `-PrepareOnly` does not perform those operations.

The detailed implementation sequence is in
[`docs/superpowers/plans/2026-07-11-mobilerun-emulator-worker.md`](./docs/superpowers/plans/2026-07-11-mobilerun-emulator-worker.md).

### Phase board

| Phase | Status | Highlights |
| --- | --- | --- |
| -1 — Review | Done | Architecture report, as-is/to-be diagrams, risk + phase map. |
| 0 — Shippable base | Done | Dockerfile, Caddy/TLS, production token guard, CI skeleton, deploy runbook. |
| 1 — Multi-user | Done | Postgres/Redis, OIDC/JWT, API keys, distributed rate limit, persistent history, quota. |
| 2 — Scale + product | Core done | Prometheus `/metrics`, pino logs, object storage `/v1/media`, Stripe billing, K8s/HPA, voice. |
| 3 — Agent + RAG | Done | Tool/function calling, SearXNG web search, `doc_search`, pgvector RAG, source badges. |
| 4A — Artifacts/export | Done | HTML/SVG/Mermaid preview, artifact download, Markdown/JSON/PDF export, local share link. |
| 4B — Tools/docs/persona | Done | PDF/DOCX extraction, opt-in QuickJS `code_run`, persona/prompt library. |
| 4C — Multimodal + voice queue | Done | `auto` image routing + `VISION_MODEL`, opt-in remote images, BullMQ voice jobs. |
| 4D — Repo hygiene + security | Done | Personal traces removed, CSP/JWT/media/admin hardening, clean git history. |
| 5A — CI & security automation | Done | CI Node 20.19+22 matrix, secret scan, npm audit, live smoke; one-command `npm run security`. |
| 5B — Observability + hardening | Done | Prometheus agent metrics, opt-in OTLP traces, Grafana auto-provisioning, error webhook, `npm run prod-check`. |
| 6 — Advanced product + Android | Done | Scheduled agent tasks, team mode + live progress, personal memory, model eval, PWA, MCP, Aurora UI redesign, workspaces + RBAC, Android Gradle wrapper bundled. |
| 7 — Collaboration (shared resources) | Core done | Workspace-scoped knowledge base, memory and scheduled tasks (write = editor/admin). Optional: shared conversations. |
| 8 — Production hardening + deploy + agent deepening | Code/config done; runtime smoke pending | Hardened `prod-check` + preflight, loopback-bound panels, `docker-compose.prod.yml`, Caddy `/health` proxy, Keycloak public host routing, production `CSP_CONNECT_SRC`. Agent deepening: MCP tool introspection, opt-in SSRF-guarded `fetch_url`, agent run history, Ollama `think`/effort passthrough, scheduled runner fix, weather edge tests. Pending: live WSL/Ollama/Docker smoke. |
| 9 — Public release handoff + first-run reliability | Not started | Next candidate after Phase 8 smoke: public-local install polish, first-run diagnostics, clearer release checklist, and user-facing troubleshooting. |

### Recent changes

A condensed changelog; full per-session detail lives in git history and `PROGRESS.md`.

- **Phase 8 (hardening + agent):** strengthened `npm run prod-check` (token strength,
  default/weak infra secrets, multi-user DB/admins, cleartext MCP); gateway refuses to
  start in production with a too-short `GATEWAY_TOKEN`; internal panels bind `127.0.0.1`
  only; `docker-compose.prod.yml` overlay (Keycloak `start` mode, required-secret guards).
  Agent deepening: `GET /v1/mcp/tools` introspection + UI, opt-in `fetch_url` web-page
  reader (SSRF-guarded), agent run history (`/v1/agent/runs` + UI).
- **Latest Phase 8 closure:** Ollama agent/team calls now pass `think` and effort options
  (`temperature`, `top_p`, `num_predict`) through the gateway; scheduled tasks honor
  `agent:false` by using direct provider chat; weather fallback and missing-data paths are
  covered by negative/edge tests; Caddy now proxies `/health`; Keycloak's public auth host
  is wired through `KC_HOSTNAME_HOST` and checked by `prod-check`.
  **Why:** these were runtime gaps between the documented behavior and the actual deploy
  path. Closing them prevents silent model-routing mismatches, scheduled tasks taking the
  wrong execution path, weather output corruption, broken public health checks, and a
  half-configured auth subdomain.
  Production CSP was tightened with `CSP_CONNECT_SRC`: local/dev origins stay available
  for base compose, while the production overlay defaults to `connect-src 'self'` and
  `prod-check` hard-fails wildcard/local dev origins.
  The tracked Keycloak realm no longer imports a default user/password, and the public
  web client has password grant disabled so sign-in stays on the PKCE browser flow.
  **Verified:** gateway tests `99/99`, web tests `3/3`, `npm run security`, production
  compose config, and a strong-env `prod-check` pass. Remaining work is a live
  WSL/Ollama/Docker smoke before Phase 8 can be closed.
- **Next phase candidate:** Phase 9 is public release handoff + first-run reliability.
  Do not start it until Phase 8 live smoke passes on a real Docker/Ollama/WSL stack.
- **Phase 7 (collaboration):** `documents`/`user_memory`/`scheduled_tasks` gained
  `workspace_id`; list/search span personal + member workspaces; writes require write role.
- **Phase 6:** per-model cost in the usage panel, live team-mode progress streaming,
  personal long-term memory, model eval/comparison, PWA, MCP integration, Aurora UI
  redesign, workspaces + 3-role RBAC, Android Gradle wrapper.

### Hardware note (local LLM performance)

On a GPU with **8 GB VRAM (e.g. RTX 3070)**, a 14B model at Q4_K_M (~10 GB) does not fully
fit and Ollama splits it across CPU+GPU, so answers are slower — this is a hardware limit,
not a NOVA issue. For full-GPU speed pick an 8B-class model; for more capability accept the
split. Verify placement with `ollama ps` (the `PROCESSOR` column: `100% GPU` vs split).

| Model | Footprint | Placement | Speed |
|---|---|---|---|
| `qwen3:8b` | ~5.2 GB | **100% GPU** | fast |
| `qwen3.6:35b` | ~24 GB | needs high VRAM/RAM or split | strongest local tool-calling |
| `qwen3:14b` | ~10 GB | split CPU/GPU | medium, balanced tool-calling |
| `gemma4:e2b` | ~7.2 GB | **100% GPU** | fastest native tools |

## Repository layout

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

| Path | Purpose |
| --- | --- |
| `package.json` | Convenience scripts that drive both packages. |
| `docker-compose.yml` | Base stack: Postgres, Redis, gateway, Caddy. |
| `docker-compose.faz2.yml` | Phase-2 add-ons: Keycloak, MinIO, SearXNG, Prometheus, Grafana, voice. |
| `docker-compose.prod.yml` | Production hardening overlay. |
| `gateway/` | API gateway; Node runtime, no build step. |
| `gateway/gateway.mjs` | Hardened server: auth, CORS allowlist, rate limit. |
| `gateway/lib/` | Agent loop, MCP, RAG, memory, RBAC, prodcheck and helpers. |
| `gateway/routes/` | Knowledge, memory, scheduled tasks, workspaces, agent runs and API routes. |
| `gateway/migrations/` | SQL migrations `001` through `008`. |
| `gateway/.env.example` | Copy to `.env` and fill in per machine. |
| `web/` | Browser UI with Vite, React and PWA support. |
| `web/src/nova-agent.jsx` | Main browser UI component. |
| `nova-android/` | Native Android client with Kotlin and Compose. |

## Prerequisites

- **Node.js 20.19 or newer** (`node -v`). The web build uses Vite 8 (`^20.19.0 || >=22.12.0`).
- At least one model backend:
  - **Local, no key:** [Ollama](https://ollama.com) on `http://localhost:11434`.
  - **Cloud:** an API key for Anthropic, Gemini, and/or OpenAI.
- (Optional) OpenAI-compatible **Whisper** (STT) and **TTS** servers for real voice mode.

### Build for production

Build writes static files to `web/dist/`; preview serves that build locally on
`http://localhost:4173`.

```bash
npm run build
npm run preview
```

Serve `web/dist/` from any static host and keep the gateway running behind it.

## How model routing works

Send a model string as `"<provider>/<model>"`:

| Example | Goes to |
| --- | --- |
| `ollama/qwen3.6:35b` | local Ollama |
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
| `POST` | `/v1/chat/completions` | OpenAI-compatible chat (streaming SSE); `agent`/`team` modes, auto memory recall | token |
| `POST` | `/v1/eval` | run one prompt against several models; returns output + latency/tokens/cost | token |
| `GET/POST/DELETE` | `/v1/memory` | personal long-term memory notes (auto-recalled into the system prompt) | token |
| `GET/POST/PATCH/DELETE` | `/v1/workspaces[...]` | workspaces + RBAC (admin/editor/viewer) + member management | token |
| `GET/DELETE` | `/v1/agent/runs` | agent/team run history | token |
| `GET` | `/v1/mcp/tools` | configured MCP servers + discovered tools | token |
| `GET/POST/PATCH/DELETE` | `/v1/scheduled` | scheduled/automated agent tasks | token |
| `GET/POST/DELETE` | `/v1/knowledge` | RAG knowledge base (upload/list/delete) | token |
| `POST` | `/stt` · `/tts` | speech-to-text / text-to-speech | token |

## Security

This project handles API keys and arbitrary upstream calls, so the gateway ships with
several protections. **Review these before exposing it beyond `localhost`** — and read
[`SECURITY.md`](./SECURITY.md) in full.

- **Keep keys on the server.** Prefer the **Gateway** provider in the UI so provider keys
  live only in `gateway/.env` and never reach the browser.
- **`GATEWAY_TOKEN` (bearer auth).** When set, every request except `/health` must send
  `Authorization: Bearer <token>` (constant-time compare). Blank by default = open. In
  production the gateway refuses to start without it (or a too-short one). Generate one:
  ```bash
  node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
  ```
- **CORS allowlist (`ALLOW_ORIGINS`).** Only listed origins may call the gateway. `*` = dev only.
- **Rate limiting (`RATE_MAX` / `RATE_WINDOW_MS`)** — per-IP + per-user; `RATE_MAX=0` disables.
- **Model allowlist (`ALLOW_MODELS`)** — e.g. `ollama/*,gemini/gemini-2.5-flash`.
- **Input limits** — `BODY_LIMIT`, `MAX_MESSAGES`, `MAX_MESSAGE_CHARS`, `REQ_TIMEOUT_MS`.
- **Media/image validation** — `/v1/media` MIME allowlist + base64 check; remote image fetch
  is opt-in and blocks private/localhost targets with redirect/byte limits.
- **Agent tools** — `web_search` hits internal SearXNG only; `calculator` is a strict-allowlist
  safe eval; `code_run` (QuickJS) and `fetch_url` (SSRF-guarded) are off by default.
- **Keycloak defaults** — the tracked realm imports no default users or passwords; the
  web client uses PKCE authorization code flow and password grant is disabled.
- **Minimal health output** — `/health` returns only `{ ok: true }` in production.
- **Frontend CSP/link safety** — restrictive CSP; production `CSP_CONNECT_SRC` should be
  `'self'` or exact trusted origins only; markdown links only `http`/`https`/`mailto`.
- **Hardening defaults** — `X-Powered-By` off, baseline security headers, upstream error
  details hidden in production. `TRUST_PROXY=1` only behind a trusted TLS proxy.

### Production checklist (one command)

Source your prod `.env`, then:

Run production readiness and the full security gate:

```bash
npm run prod-check
npm run security
```

For production, layer the hardening overlay (Keycloak `start` mode, required-secret guards):

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d
```

Other checks:

| Command | Purpose |
| --- | --- |
| `npm run secret-scan` | Scan tracked files for leaked keys or tokens. |
| `npm run audit` | Run npm audit for gateway and web at moderate level. |
| `npm --prefix gateway test` | Run gateway unit and agent-loop tests. |
| `npm run smoke:live` | Run end-to-end smoke against a running gateway. |

## Configuration reference

All gateway settings are environment variables (see `gateway/.env.example` for the full list):

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8088` | gateway port |
| `GATEWAY_TOKEN` | *(empty)* | bearer token; empty = auth disabled |
| `ALLOW_ORIGINS` | Vite dev/preview origins | CORS allowlist (comma list, `*` = any) |
| `CSP_CONNECT_SRC` | local/dev + provider-direct origins | Caddy browser `connect-src`; production should use `'self'` or exact trusted origins |
| `RATE_MAX` / `RATE_WINDOW_MS` | `120` / `60000` | per-IP rate limit |
| `ALLOW_MODELS` | *(all)* | model allowlist, supports `provider/*` |
| `BODY_LIMIT` | `25mb` | max JSON body size |
| `MAX_MESSAGE_CHARS` / `MAX_MESSAGES` | `100000` / `400` | per-request text + message caps |
| `MAX_DOC_BYTES` / `MAX_DOC_FILE_BYTES` | `1048576` / `10485760` | knowledge text + raw upload caps |
| `MAX_MEDIA_BYTES` / `ALLOWED_MEDIA_MIME_TYPES` | `26214400` / allowlist | `/v1/media` caps |
| `HEALTH_DETAILS_ENABLED` | `0` | expose non-prod `/health` details when `1` |
| `CODE_TOOL_ENABLED` (+`_TIMEOUT_MS`/`_MEMORY_MB`/`_MAX_*`) | `0` | opt-in QuickJS `code_run` tool + limits |
| `FETCH_TOOL_ENABLED` / `FETCH_TOOL_MAX_BYTES` | `0` / `2000000` | opt-in SSRF-guarded `fetch_url` tool |
| `REQ_TIMEOUT_MS` / `MAX_RETRIES` | `60000` / `2` | upstream timeout + retries |
| `TRUST_PROXY` | `0` | trust reverse-proxy headers (`1` to enable) |
| `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY` | *(empty)* | provider keys |
| `OLLAMA_URL` | `http://localhost:11434` | local Ollama base URL |
| `DEFAULT_MODEL` | `ollama/qwen3:14b` | what `auto` falls back to |
| `VISION_MODEL` / `ROUTE_VISION` | `ollama/qwen3.5-omni:latest` / *(unset)* | image-request routing |
| `REMOTE_IMAGE_URLS_ENABLED` / `REMOTE_IMAGE_MAX_BYTES` | `0` / `10485760` | remote image fetch (private hosts blocked) |
| `ROUTE_FAST/BALANCED/DEEP/MAX` | *(unset)* | per-effort model overrides |
| `OPENCLAW_URL` / `OPENCLAW_TOKEN` / `OPENCLAW_PATH` | localhost defaults | OpenClaw agent layer |
| `WHISPER_URL` / `TTS_URL` / `TTS_MODEL` / `TTS_VOICE` | localhost defaults | voice STT/TTS |
| `VOICE_QUEUE_ENABLED` (+`_CONCURRENCY`/`_RESULT_TTL_SEC`/`_MAX_AUDIO_BYTES`) | `0` | async BullMQ/Redis voice jobs |
| `TTS_MAX_INPUT_CHARS` | `8000` | max TTS input length |
| `OTEL_EXPORTER_OTLP_ENDPOINT` / `_TRACES_ENDPOINT` / `OTEL_SERVICE_NAME` | *(unset)* / *(unset)* / `nova-gateway` | opt-in OTLP/HTTP tracing |
| `TEAM_CONCURRENCY` | `3` | parallel sub-agents in team mode |
| `AUTO_AGENT_ENABLED` | `1` | auto-enable agent tools for live-data queries (weather/news/prices); needs SearXNG + a tool-calling model |
| `MEMORY_ENABLED` / `MEMORY_MAX_ITEMS` / `MEMORY_MAX_CHARS` | `1` / `60` / `500` | personal memory recall + limits |
| `EVAL_CONCURRENCY` / `EVAL_MAX_MODELS` | `3` / `6` | model-comparison parallelism + cap |
| `MCP_SERVERS` / `MCP_CACHE_MS` | *(unset)* / `300000` | external MCP tool servers (opt-in) + tool-list cache |

## Voice mode

The UI supports browser speech recognition out of the box. For higher-quality real STT/TTS,
run OpenAI-compatible servers and point `WHISPER_URL` / `TTS_URL` at them (the UI calls
`/stt` and `/tts` on the gateway, which proxies to those servers). For longer local voice
jobs, set `VOICE_QUEUE_ENABLED=1` with Redis available; the gateway then exposes
`POST /v1/voice/jobs`, `GET /v1/voice/jobs/:id`, and `GET /v1/voice/jobs/:id/audio`.

## Troubleshooting

- **`401 unauthorized`** — `GATEWAY_TOKEN` is set but the UI isn't sending it. Paste the token
  into the Gateway provider's key field in Settings.
- **CORS error** — the UI origin isn't in `ALLOW_ORIGINS`. Add it and restart the gateway.
- **Ollama "connection refused"** — start it with `OLLAMA_ORIGINS=* ollama serve`.
- **`429 rate limit exceeded`** — you hit `RATE_MAX`; raise it or wait for the window.
- **Empty/garbled stream** — confirm the provider key is set and the model id exists.

## License

MIT. See [`LICENSE`](./LICENSE).
