# Windows Native Mobile Worker Design

**Status:** Approved on 2026-07-13

## Decision

Project Horus will support a Windows-native execution path for the existing
safe Mobilerun worker. The worker process runs on Windows beside the Android
emulator and connects to the existing Windows ADB server over loopback. Model
inference remains on the same PC through the WSL Ollama service, so the local
GPU continues to provide compute without exposing ADB, Ollama, or worker APIs
to the LAN.

## Why This Path

The original WSL worker path requires an inbound Hyper-V firewall exception to
reach the Windows ADB server. That Windows elevation flow is unavailable in the
current remote session. Direct Windows ADB access is already proven for
`emulator-5554`, and the real Windows user session can reach the WSL Ollama
service at its WSL NAT address. This path removes the blocked cross-boundary
ADB dependency instead of weakening the firewall.

## Alternatives Considered

1. Keep the WSL worker and wait for the Hyper-V UAC approval. This remains a
   valid future option but cannot unblock the current run.
2. Run the worker on Windows, use loopback ADB, and derive the WSL Ollama NAT
   address at launch. This is the chosen path because it needs no firewall
   change and keeps the model on the local GPU.
3. Install a second Windows Ollama runtime and duplicate models. This would
   work but duplicates storage and runtime management, so it is not chosen.

## Runtime Architecture

```text
Android app <- Gateway event stream <- Windows worker <- Mobilerun <- Windows ADB <- emulator-5554
                                      |
                                      +-> derived WSL NAT URL -> WSL Ollama -> local GPU
```

The Gateway remains the only task, lease, event, and confirmation authority.
The Android app receives only sanitized Gateway events. The Windows worker
never receives user API keys, and no worker, ADB, Portal, or Ollama endpoint is
published to the LAN.

## WSL Ollama Resolution

`MOBILE_WORKER_OLLAMA_URL` remains loopback-only for the normal path. A new
mutually exclusive `MOBILE_WORKER_OLLAMA_WSL_DISTRO` setting enables the
Windows-native path. On Windows only, configuration executes this fixed argv
command without a shell:

```text
wsl.exe --distribution <validated distro> --exec ip -4 route get 1.1.1.1
```

The resolver accepts exactly one `src` IPv4 address in `172.16.0.0/12`, then
constructs only `http://<derived-address>:11434`. It rejects a raw Ollama URL
when WSL mode is selected, invalid distro names, timeout/nonzero WSL commands,
missing or ambiguous output, non-WSL-NAT source addresses, and non-Windows
hosts. It never accepts a hostname, arbitrary private address, credentials, or
HTTPS URL in this mode.

This matches the observed WSL route (`src 172.19.99.210`) while refreshing the
address for every worker process. It avoids a persistent proxy, `netsh`
port-forward, broad firewall rule, or LAN listener.

## Windows Worker Launch

`scripts/start-windows-mobile-worker.ps1` will:

1. Load the ignored worker `.env` file without printing its values.
2. Create/use a separate Windows Python 3.12 virtual environment so it never
   overwrites the existing Linux worker environment.
3. Resolve the installed Android SDK platform-tools directory, prepend it to
   `PATH`, and set `ADBUTILS_ADB_PATH` to its `adb.exe`.
4. Force the worker ADB endpoint to `127.0.0.1:5037` and start the normal
   `horus-mobile-worker --once` command.

The script does not start an ADB server, create a firewall rule, or persist a
WSL IP. Existing Windows ADB ownership remains unchanged.

## Safety and Error Handling

- The initial device stays exactly `emulator-5554` and the existing strict
  Settings/version goal policy remains unchanged.
- Screenshot streaming, telemetry, tracing, saved trajectories, credentials,
  coordinate tools, and arbitrary prompts remain disabled.
- A failed WSL resolution fails closed during configuration and maps through
  the existing compute/device lifecycle rather than contacting another host.
- Portal setup is a separate explicit, mutating step. It runs only after the
  Windows runtime and local ADB readiness checks pass.

## Verification

1. Focused configuration tests cover all accepted and rejected WSL resolver
   cases without executing WSL.
2. The existing worker unit suite and smoke preflight pass on the source tree.
3. The Windows launcher proves `adb devices` shows exactly
   `emulator-5554 device` and the derived Ollama URL answers `/api/version`.
4. Only then run the authorized Mobilerun Portal setup, worker smoke lifecycle,
   Android instrumentation, and final APK build.
