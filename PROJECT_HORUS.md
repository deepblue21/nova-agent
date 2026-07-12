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
uv run horus-mobile-worker --once
```

With the worker running and a temporary user API key exported only in the invoking shell, `npm run smoke:mobile-worker` creates the exact allowlisted Settings/version task and waits up to 120 seconds for the audited worker event sequence. The smoke never prints either token.
