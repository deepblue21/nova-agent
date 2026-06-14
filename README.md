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
- Keep local demo passwords clearly dev-only; do not expose externally without applying
  the [`SECURITY.md`](./SECURITY.md) checklist.
- Keep `gateway/.env.example` current; real values are created per-machine by the user.
- A hosted/production service is out of scope. Exposing to the internet additionally
  requires TLS, a domain, secret rotation, `ALLOW_ORIGINS`, Keycloak production mode and
  closed internal ports.

## 🚀 Quick start

Two paths: **Docker (recommended — full stack)** or **bare-metal (gateway + web only)**.

### Path A — Docker full stack (Postgres, Redis, Keycloak, SearXNG, MinIO, Grafana, voice)

Needs Docker + docker compose, and [Ollama](https://ollama.com) for local LLMs.

```bash
# 1) Clone
git clone <repo-url> nova && cd nova

# 2) Create the gateway .env (generate a token + at least one provider key OR Ollama)
cp gateway/.env.example gateway/.env
node -e "console.log('GATEWAY_TOKEN='+require('crypto').randomBytes(32).toString('hex'))" >> gateway/.env
# edit gateway/.env: set ALLOW_ORIGINS, a provider key or OLLAMA_URL

# 3) Build the web UI (Caddy serves web/dist)
npm --prefix web ci && npm --prefix web run build

# 4) (For local LLMs) expose Ollama to the containers + pull models
#    On WSL/Linux run with OLLAMA_HOST=0.0.0.0, then:
ollama pull qwen3:14b && ollama pull qwen3:8b && ollama pull nomic-embed-text
# (For vision; tag varies by setup)
ollama pull qwen3.5-omni:latest

# 5) Bring the stack up (first run pulls images, a few minutes)
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build

# 6) First user + API key (multi-user mode)
docker compose exec gateway node scripts/bootstrap-user.mjs you@example.com 5
```

Open **http://localhost** → ⚙ Settings → **Sign in with Keycloak** (or paste an API key) → chat.
Internal panels are loopback-only by default (reach them from the host machine): Grafana
`127.0.0.1:3001`, Prometheus `:9090`, MinIO `:9001`, SearXNG `:8080`, Keycloak `:8081`.

> ⚠️ All default passwords are **for local development only.** Before going public, read
> **[`SECURITY.md`](./SECURITY.md)** and change them all. Windows + WSL setup:
> **[`WSL_DOCKER.md`](./WSL_DOCKER.md)**.

### Path B — Bare-metal (single-user gateway + web, no DB)

```bash
git clone <repo-url> nova && cd nova
npm run install:all                       # gateway + web deps
cp gateway/.env.example gateway/.env      # GATEWAY_TOKEN + a provider key or Ollama
npm run gateway                           # terminal A → http://localhost:8088/v1
npm run web                               # terminal B → http://localhost:5173
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

### Phase board

| Phase | Status | Highlights |
| --- | --- | --- |
| -1 — Review | ✅ Done | Architecture report, as-is/to-be diagrams, risk + phase map. |
| 0 — Shippable base | ✅ Done | Dockerfile, Caddy/TLS, production token guard, CI skeleton, deploy runbook. |
| 1 — Multi-user | ✅ Done | Postgres/Redis, OIDC/JWT, API keys, distributed rate limit, persistent history, quota. |
| 2 — Scale + product | ✅ Core done | Prometheus `/metrics`, pino logs, object storage `/v1/media`, Stripe billing, K8s/HPA, voice. |
| 3 — Agent + RAG | ✅ Done | Tool/function calling, SearXNG web search, `doc_search`, pgvector RAG, source badges. |
| 4A — Artifacts/export | ✅ Done | HTML/SVG/Mermaid preview, artifact download, Markdown/JSON/PDF export, local share link. |
| 4B — Tools/docs/persona | ✅ Done | PDF/DOCX extraction, opt-in QuickJS `code_run`, persona/prompt library. |
| 4C — Multimodal + voice queue | ✅ Done | `auto` image routing + `VISION_MODEL`, opt-in remote images, BullMQ voice jobs. |
| 4D — Repo hygiene + security | ✅ Done | Personal traces removed, CSP/JWT/media/admin hardening, clean git history. |
| 5A — CI & security automation | ✅ Done | CI Node 20.19+22 matrix, secret scan, npm audit, live smoke; one-command `npm run security`. |
| 5B — Observability + hardening | ✅ Done | Prometheus agent metrics, opt-in OTLP traces, Grafana auto-provisioning, error webhook, `npm run prod-check`. |
| 6 — Advanced product + Android | ✅ Done | Scheduled agent tasks · team mode + live progress · personal memory · model eval · PWA · MCP · Aurora UI redesign · workspaces + RBAC · Android Gradle wrapper bundled. |
| 7 — Collaboration (shared resources) | ✅ Core done | Workspace-scoped knowledge base, memory and scheduled tasks (write = editor/admin). Optional: shared conversations. |
| 8 — Production hardening + deploy + agent deepening | 🟡 In progress | ✅ Hardened `prod-check` (weak token/default secrets/MCP-TLS) + preflight, loopback-bound panels, `docker-compose.prod.yml`. ✅ Agent deepening: MCP tool introspection, opt-in SSRF-guarded `fetch_url`, agent run history. Next: live-deploy prep. |

### Recent changes

A condensed changelog; full per-session detail lives in git history and `PROGRESS.md`.

- **Phase 8 (hardening + agent):** strengthened `npm run prod-check` (token strength,
  default/weak infra secrets, multi-user DB/admins, cleartext MCP); gateway refuses to
  start in production with a too-short `GATEWAY_TOKEN`; internal panels bind `127.0.0.1`
  only; `docker-compose.prod.yml` overlay (Keycloak `start` mode, required-secret guards).
  Agent deepening: `GET /v1/mcp/tools` introspection + UI, opt-in `fetch_url` web-page
  reader (SSRF-guarded), agent run history (`/v1/agent/runs` + UI).
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
| `qwen3:14b` | ~10 GB | split CPU/GPU | medium, strongest tool-calling |
| `gemma4:e2b` | ~7.2 GB | **100% GPU** | fastest |

## Repository layout

```
Nova_Agent_AI/
├── package.json            # convenience scripts that drive both packages
├── docker-compose.yml      # base stack (Postgres, Redis, gateway, Caddy)
├── docker-compose.faz2.yml # phase-2 add-ons (Keycloak, MinIO, SearXNG, Prometheus, Grafana, voice)
├── docker-compose.prod.yml # production hardening overlay
├── gateway/                # the API gateway (Node, no build step)
│   ├── gateway.mjs         # hardened server: auth, CORS allowlist, rate limit
│   ├── lib/                # agent loop, mcp, rag, memory, rbac, prodcheck, …
│   ├── routes/             # knowledge, memory, scheduled, workspaces, agent runs, …
│   ├── migrations/         # SQL migrations (001…008)
│   └── .env.example        # copy to .env and fill in
├── web/                    # the browser UI (Vite + React + PWA)
│   └── src/nova-agent.jsx  # the full UI component
└── nova-android/           # native Android client (Kotlin + Compose)
```

## Prerequisites

- **Node.js 20.19 or newer** (`node -v`). The web build uses Vite 8 (`^20.19.0 || >=22.12.0`).
- At least one model backend:
  - **Local, no key:** [Ollama](https://ollama.com) on `http://localhost:11434`.
  - **Cloud:** an API key for Anthropic, Gemini, and/or OpenAI.
- (Optional) OpenAI-compatible **Whisper** (STT) and **TTS** servers for real voice mode.

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
- **Minimal health output** — `/health` returns only `{ ok: true }` in production.
- **Frontend CSP/link safety** — restrictive CSP; markdown links only `http`/`https`/`mailto`.
- **Hardening defaults** — `X-Powered-By` off, baseline security headers, upstream error
  details hidden in production. `TRUST_PROXY=1` only behind a trusted TLS proxy.

### Production checklist (one command)

Source your prod `.env`, then:

```bash
npm run prod-check    # validates token strength, CORS, default/weak secrets, MCP-TLS, …
npm run security      # syntax + gateway tests + secret scan + gateway/web audit + web build
```

For production, layer the hardening overlay (Keycloak `start` mode, required-secret guards):

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml up -d
```

Other checks:

```bash
npm run secret-scan   # scan tracked files for leaked keys/tokens
npm run audit         # npm audit for gateway + web (moderate level)
npm --prefix gateway test   # gateway unit + agent-loop tests
npm run smoke:live    # end-to-end smoke against a RUNNING gateway
```

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
