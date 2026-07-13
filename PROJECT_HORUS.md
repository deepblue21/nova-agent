# Project Horus

Project Horus is the Android-first autonomous mobile-agent product built on the Nova Agent gateway and Android client.

The current milestone is Mobile Task Control Plane: persistent tasks, replayable events, user commands, and explicit risk confirmation. Mobilerun device execution, native AccessibilityService control, and LiteRT-LM phone inference are separate milestones.

PC services run in WSL2 Ubuntu. Android builds run with JDK 17 and Android SDK 35.

## Development

```bash
npm run install:all
npm run test:gateway
npm run build
```

Android:

```powershell
cd nova-android
.\gradlew.bat test lintDebug assembleDebug
```

## Mobile Control Plane

Start the local multi-user stack from WSL:

```bash
docker compose up -d --build postgres redis migrate gateway caddy
docker compose exec gateway node scripts/bootstrap-user.mjs horus@local.test 5
HORUS_API_KEY='nova_key_returned_once' npm run smoke:mobile
```

The smoke test creates a task, verifies its queued state, pauses, resumes, and cancels it, then checks replayable SSE event order. It never prints the API key.

## Local Emulator Worker

The Docker Gateway is published only to `127.0.0.1:8088`. Keep the worker disabled until its dedicated token and the WSL ADB bridge are configured; do not publish the worker API, ADB, PostgreSQL, or Redis to the LAN.

Run one worker pass from the WSL worker shell:

```bash
cd /mnt/c/Users/salih/Project_Horus/mobile-worker
set -a; source .env; set +a
export PATH="$HOME/.local/bin:$PATH"
export ANDROID_ADB_SERVER_HOST="$MOBILE_WORKER_ADB_SERVER_HOST"
export ANDROID_ADB_SERVER_PORT="$MOBILE_WORKER_ADB_SERVER_PORT"
mobilerun ping --device emulator-5554
uv run horus-mobile-worker --once
```

`host.docker.internal` is only the WSL-to-Windows host bridge; it is never an Android or UI payload value.
Before enabling the worker, retain a separate bridge proof from the same shell:

```bash
adb -H "$ANDROID_ADB_SERVER_HOST" -P "$ANDROID_ADB_SERVER_PORT" devices
```

The bridge remains unverified until that command lists exactly `emulator-5554` followed by whitespace and `device`. Do not expose the ADB port publicly.

With the worker running and a temporary user API key exported only in the invoking shell, `npm run smoke:mobile-worker` creates the exact allowlisted Settings/version task and waits up to 120 seconds for the audited worker event sequence. The smoke never prints either token.

## Windows-Native Worker

The Windows-native path runs the worker beside the emulator and uses the existing loopback ADB endpoint at `127.0.0.1:5037`. From PowerShell, create the ignored `mobile-worker/.env` from its worker-only example, then validate local prerequisites without creating a virtual environment or changing ADB, firewall, WSL, or Ollama state:

```powershell
.\scripts\start-windows-mobile-worker.ps1 -PrepareOnly
```

Run a single worker pass with its separate `mobile-worker/.venv-windows` environment:

```powershell
.\scripts\start-windows-mobile-worker.ps1 -Once
```

The launcher treats that file as the complete worker configuration: before importing its explicit allowlisted values, it removes all supported worker settings inherited from the calling process. Omitted values are therefore not inherited. Gateway-only keys are rejected, including `MOBILE_WORKER_ENABLED`, `MOBILE_WORKER_LEASE_MS`, and `MOBILE_WORKER_GOAL_POLICY`. The launcher forces loopback ADB and removes any raw `MOBILE_WORKER_OLLAMA_URL`; it passes the validated `-Distro` value (default `Ubuntu`) to Task 1's resolver, which derives the WSL NAT Ollama address only from a `172.16.0.0/12` route source. The example's raw loopback Ollama URL is for non-Windows mode and is removed by the Windows launcher. Do not expose ADB, Ollama, or the worker to the LAN. Portal setup remains a later explicit task, after local readiness and the real Portal/Gateway smoke.
