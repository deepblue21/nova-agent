# Windows Native Mobile Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the existing safe Mobilerun worker on Windows against the local emulator while preserving WSL Ollama GPU inference without LAN exposure or a Hyper-V firewall change.

**Architecture:** `WorkerSettings` gains a fail-closed Windows-only WSL NAT resolver. A PowerShell launcher creates a separate Windows Python 3.12 environment, gives Mobilerun the existing Windows ADB executable and loopback server, and lets the configuration derive the only permitted WSL Ollama URL. Gateway and Android contracts stay unchanged.

**Tech Stack:** Python 3.12, uv 0.11+, Mobilerun 0.6.10, Windows PowerShell, Windows Android SDK/ADB, WSL2 Ubuntu, Ollama, existing Node smoke tests.

## Global Constraints

- Canonical checkout is `C:\Users\salih\Project_Horus` on branch `codex/mobile-task-control-plane`.
- Preserve the Linux worker environment; the Windows launcher uses its own `.venv-windows` environment.
- Gateway remains the only task/event/confirmation authority; Android receives no worker, ADB, Portal, or model credentials.
- Do not add a public ADB port, firewall rule, persistent proxy, `netsh portproxy`, Docker port, or Ollama LAN publication.
- Normal `MOBILE_WORKER_OLLAMA_URL` remains loopback-only. WSL mode accepts only a derived `172.16.0.0/12` NAT source on `http` port `11434`.
- Initial device remains exactly `emulator-5554`; existing direct-mode safety restrictions remain unchanged.
- Each task starts with a focused failing test, has a dedicated commit, and updates `README.md` and `README.tr.md` with verified work and the next concrete task.

---

### Task 1: Fail-Closed WSL Ollama Resolver

**Files:**
- Create: `mobile-worker/src/horus_mobile_worker/wsl_ollama.py`
- Modify: `mobile-worker/src/horus_mobile_worker/config.py`
- Modify: `mobile-worker/tests/test_config.py`
- Modify: `README.md`
- Modify: `README.tr.md`

**Interfaces:**
- Produces `resolve_wsl_ollama_url(distro, *, run, platform_name) -> str` and
  `WorkerSettings.ollama_url`.
- Consumes `MOBILE_WORKER_OLLAMA_WSL_DISTRO` as an alternative to
  `MOBILE_WORKER_OLLAMA_URL`.

- [ ] Write tests for a valid `src 172.19.99.210`, invalid distro identifier,
  raw URL plus WSL mode, non-Windows host, timeout/nonzero command, missing or
  multiple `src` values, and an out-of-range source.
- [ ] Run `python -m unittest tests.test_config -v` and confirm the resolver
  import/test fails before implementation.
- [ ] Implement argument-list-only WSL execution, strict output parsing, and
  configuration integration; do not accept user-provided remote URLs.
- [ ] Run `python -m unittest discover -s tests -v` and `uv lock --check`.
- [ ] Update both README living-delivery sections with the verified resolver
  behavior and name the Windows launcher as the next work item.
- [ ] Commit with `feat: resolve Windows worker WSL Ollama`.

### Task 2: Windows Native Launcher and Operational Documentation

**Files:**
- Create: `scripts/start-windows-mobile-worker.ps1`
- Modify: `mobile-worker/.env.example`
- Modify: `PROJECT_HORUS.md`
- Modify: `SECURITY.md`
- Modify: `README.md`
- Modify: `README.tr.md`

**Interfaces:**
- Consumes the Task 1 WSL setting and the ignored worker `.env` file.
- Produces a one-command Windows worker pass that uses `.venv-windows`,
  `ADBUTILS_ADB_PATH`, and `127.0.0.1:5037`.

- [ ] Write a PowerShell `-PrepareOnly` mode that validates the SDK/ADB path,
  loads safe `KEY=value` entries without echoing values, and prints only
  redacted readiness facts.
- [ ] Run `-PrepareOnly` before adding the implementation and record the
  expected missing-script failure.
- [ ] Implement the launcher with a separate Windows Python 3.12 environment,
  loopback ADB environment, and no firewall/server mutation.
- [ ] Run the prepare-only proof and the Python worker tests; document the
  exact Windows path, WSL NAT safety rule, and no-LAN policy in both READMEs.
- [ ] Commit with `feat: add Windows mobile worker launcher`.

### Task 3: Real Windows Mobilerun Proof

**Files:**
- Modify: `PROJECT_HORUS.md`
- Modify: `README.md`
- Modify: `README.tr.md`
- Create: `.superpowers/sdd/windows-native-worker-report.md` (ignored)

**Interfaces:**
- Consumes Task 1 resolver, Task 2 launcher, existing Gateway smoke script,
  Windows ADB `emulator-5554`, WSL Ollama, and explicit Portal setup.
- Produces verified command evidence only; it does not alter Gateway/Android
  source contracts.

- [ ] Run the launcher prepare-only check, `adb devices`, and an Ollama
  `/api/version` check using the derived address. Require the exact emulator
  `device` state before Portal setup.
- [ ] Run authorized `mobilerun setup --device emulator-5554` and
  `mobilerun ping --device emulator-5554`; stop on failure.
- [ ] Start one worker pass and run `smoke:mobile-worker`, requiring the exact
  persisted lifecycle `task.created`, `worker.claimed`, `worker.executing`,
  `worker.observing`, `worker.running`, `worker.completed`.
- [ ] Run Gateway tests, Android unit/lint/assembly/instrumentation, build the
  debug APK, and update both READMEs with only verified results.
- [ ] Commit documentation-only runtime evidence if and only if the proof
  succeeds; otherwise create a focused regression task before changing code.

## Self-Review

- The resolver task owns the security boundary and has focused failure cases.
- The launcher task owns Windows-only process setup and all operator-facing
  docs; it does not duplicate worker policy logic.
- The proof task is intentionally last because it can install Portal and act
  on the emulator only after local-only validation succeeds.
