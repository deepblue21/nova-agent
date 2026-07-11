# Mobilerun Emulator Worker Design

Date: 2026-07-11

Status: approved for specification review

## Goal

Deliver the first real, local PC-to-Android execution slice for Project Horus. A Python worker running in WSL will claim one queued task for the existing Android emulator, run a bounded Mobilerun workflow using the PC's local Ollama/GPU, and return replayable task events to the existing Gateway and Android Tasks screen.

The first end-to-end task is intentionally harmless: open Android Settings and report the Android version. It exercises observation, app launch, text extraction, worker lifecycle, and result reporting without sending a message, changing a setting, entering credentials, or making a purchase.

## Scope

Included:

- One WSL Python worker and one configured device: `emulator-5554`.
- Mobilerun Framework and its Portal on the emulator, controlled through the WSL ADB server.
- Direct Mobilerun execution with a local Ollama profile on the PC GPU.
- Gateway-owned worker authentication, task claim leases, execution/result events, and cancellation checks.
- Android timeline support for worker lifecycle events.
- Fake-runner unit/integration tests plus a manually invoked real emulator smoke test.

Excluded:

- Physical-phone Wi-Fi ADB enrollment.
- Native Horus AccessibilityService / Portal implementation.
- Phone-side LiteRT-LM inference and hybrid runtime fallback.
- Vision mode, coordinate clicks, persistent screenshots, trajectory files, credentials, or cloud tracing.
- R2/R3 actions, messages, account changes, purchases, permission changes, deletions, or settings changes.
- Multiple workers, multiple devices, background Android execution, and remote public network exposure.

## Evaluated Approaches

1. Invoke `mobilerun` CLI directly from the Node Gateway. This is quick, but couples HTTP request handling to a long-running Python process and makes cancellation, retries, logs, and isolation fragile.
2. Run an isolated Python worker in WSL and communicate through a narrow Gateway worker API. This keeps the Gateway as task/audit authority and lets the Python process own Mobilerun, ADB, Portal setup, model configuration, and process lifecycle. This is the selected approach.
3. Build the native Android AccessibilityService first. That is the eventual Portal architecture, but it would skip the emulator proof and combine device-control, Android service lifecycle, and security work into one risky change.

## Architecture

```text
Android Tasks UI
    | authenticated task creation / SSE
    v
Gateway + PostgreSQL
    | worker-only claim / report API
    v
WSL Python mobile-worker
    | Mobilerun SDK + local Ollama on PC GPU
    v
ADB server -> Mobilerun Portal -> emulator-5554
```

The Android app remains the user-facing control surface. The Gateway remains the only persistence, confirmation, and event-replay authority. The worker has no user API key, provider key, database credentials, or unrestricted Gateway privileges. It receives a short task payload, performs one bounded run, and submits a structured outcome.

## Gateway and Worker Contract

### Configuration

The Gateway receives these local-only environment values:

```env
MOBILE_WORKER_ENABLED=1
MOBILE_WORKER_TOKEN=<long-random-secret>
MOBILE_WORKER_DEVICE_ID=emulator-5554
MOBILE_WORKER_LEASE_MS=120000
MOBILE_WORKER_GOAL_POLICY=settings_android_version
```

The WSL worker receives the same `MOBILE_WORKER_TOKEN` through its ignored local environment file plus:

```env
HORUS_GATEWAY_URL=http://127.0.0.1:8088/v1
MOBILE_WORKER_DEVICE_ID=emulator-5554
MOBILE_WORKER_OLLAMA_URL=http://127.0.0.1:11434
MOBILE_WORKER_OLLAMA_MODEL=qwen3:14b
MOBILE_WORKER_MAX_STEPS=8
```

The exact Ollama URL stays configurable because the local server may be hosted in WSL or forwarded from Windows. It is never sent to the Android app.

### Authentication

Worker endpoints use a dedicated bearer secret. User API keys, Android session tokens, and `GATEWAY_TOKEN` are not accepted as worker credentials. The Gateway compares the configured worker secret with a timing-safe comparison, rejects missing/incorrect secrets with `401`, and refuses every worker route when `MOBILE_WORKER_ENABLED` is not `1`.

### Claim and Lease

The worker periodically calls `POST /v1/internal/mobile-worker/claims` with `{ "device_id": "emulator-5554" }`. The Gateway atomically selects the oldest `queued` task whose `device_id` is either `NULL` or exactly `emulator-5554`, locks it, assigns a random opaque lease token, sets a finite lease expiry, moves the task to `executing`, and persists `worker.claimed` and `worker.executing` events. No eligible task returns `204 No Content`.

The first slice uses an exact, normalized goal allowlist. Only these text goals are claimable:

```text
Open Settings and tell me the Android version
Ayarlari ac ve Android surumunu soyle
```

When the emulator worker is enabled, every other prompt is rejected by task creation with a user-safe unsupported-goal error. It is never queued, guessed at, or sent to a cloud model.

The claim response contains only:

```json
{
  "task": { "id": "uuid", "prompt": "string", "device_id": "emulator-5554" },
  "lease": { "id": "uuid", "token": "opaque", "expires_at": "ISO-8601" }
}
```

The Gateway stores only a hash of `lease.token`; the worker receives it once. No raw event history, screenshots, user API credentials, model credentials, or confirmation details are returned. A task that is paused, cancelled, waiting for confirmation, terminal, leased by another worker, assigned to another device, or outside the exact goal allowlist is never claimable.

`GET /v1/internal/mobile-worker/tasks/:taskId/status` accepts the same worker bearer token plus `X-Horus-Lease-Token`. It returns only `{ "status": "...", "lease_expires_at": "..." }` for the active lease and returns `409` when a user pause/cancel or lease loss means that no more device actions may begin.

### Progress and Result Reporting

The worker sends `POST /v1/internal/mobile-worker/tasks/:taskId/reports` with `X-Horus-Lease-Token` and this bounded body:

```json
{
  "lease_id": "uuid",
  "phase": "observing | running | completed | failed | waiting_for_device | waiting_for_compute",
  "summary": "capped user-safe text",
  "steps": 0,
  "error_code": "optional safe category"
}
```

`summary` is capped to 1,000 characters and `steps` to the configured maximum. `error_code` is one of `device_unavailable`, `compute_unavailable`, `execution_limit`, or `execution_failed`; raw Python, ADB, Portal, model, and provider errors are never persisted or returned.

The worker reports a bounded status vocabulary under a valid lease:

- `observing`: device/Portal reachability was checked.
- `running`: Mobilerun began the direct workflow.
- `completed`: the task succeeded, with a sanitized result summary and step count.
- `failed`: the task could not complete, with a sanitized error category and user-safe reason.
- `waiting_for_device`: ADB or Portal readiness failed before an action.
- `waiting_for_compute`: local Ollama is unavailable before an action.

For `completed`, `failed`, `waiting_for_device`, and `waiting_for_compute`, the Gateway atomically validates the lease and current status, stores the mapped task status, closes the active lease, persists a corresponding event, and publishes the saved event to the existing in-process broker. The lease row retains its terminal report ID and outcome, so a retry with the same `lease_id` and token returns the already stored task without a duplicate event. A mismatched lease ID/token returns `409`.

The exact task/event mapping is:

| Report phase | Stored task status | Persisted event |
| --- | --- | --- |
| `observing` | `observing` | `worker.observing` |
| `running` | `executing` | `worker.running` |
| `completed` | `completed` | `worker.completed` |
| `failed` | `failed` | `worker.failed` |
| `waiting_for_device` | `waiting_for_device` | `worker.waiting_for_device` |
| `waiting_for_compute` | `waiting_for_compute` | `worker.waiting_for_compute` |

The worker must fetch the task's current status before starting a new device action and after every Mobilerun action boundary. A user `pause` or `cancel` wins over the worker. If the worker crashes or loses the lease, no new action is attempted; when the lease expires, the Gateway moves the task to `waiting_for_device` with a `worker.lease_expired` event. Recovery is explicit through the existing Resume command.

## Worker Runtime

`mobile-worker/` is a Python 3.12 WSL package managed by `uv`. It exposes a small adapter boundary:

```python
class TaskRunner(Protocol):
    async def run(self, task_id: str, prompt: str, device_id: str) -> RunOutcome: ...
```

The production adapter constructs Mobilerun `MobileConfig` with:

- `DeviceConfig(serial="emulator-5554", platform="android", use_tcp=False, auto_setup=False)`.
- `AgentConfig(reasoning=False, max_steps=8, streaming=False)`.
- An Ollama LLM profile pointing to `MOBILE_WORKER_OLLAMA_URL` and `MOBILE_WORKER_OLLAMA_MODEL`.
- `ToolsConfig` retaining Mobilerun's default disabled coordinate tools: `click_at`, `click_area`, and `long_press_at`.
- `LoggingConfig(save_trajectory="none")`, tracing disabled, telemetry disabled, credentials disabled, and no vision mode.

The direct/FastAgent path is used for this slice because it has lower latency and smaller blast radius for the one safe scenario. Mobilerun reasoning mode and screenshots are reserved for a later reviewed milestone.

Before task processing, an operator runs `mobilerun setup --device emulator-5554` and `mobilerun ping --device emulator-5554`. The setup installs Mobilerun's Portal and enables its AccessibilityService on the emulator. The worker refuses to begin if its explicit readiness probe fails.

The worker emits structured stdout JSON suitable for local debugging, but never prints tokens, API keys, raw UI trees, screenshots, raw model replies, or credential values. The Gateway event payload contains only the status, worker/device identity, step count, and capped human-readable summary.

## Safety and Privacy Rules

- Every action in this milestone is R0 or R1. `MOBILE_WORKER_GOAL_POLICY=settings_android_version` allows only the two normalized Settings/version phrases defined above; it rejects all other prompts before claim instead of attempting risk classification by an LLM.
- Screen captures and trajectories are not saved. `vision`, cloud telemetry, Langfuse/Phoenix tracing, and credentials are disabled.
- The worker has no direct PostgreSQL connection and never calls user-facing Gateway routes.
- Mobilerun Portal communicates locally via ADB; no device data is intentionally uploaded to a cloud service.
- The emulator is a dedicated test device. Physical devices require a separate Wi-Fi ADB enrollment design and an explicit user approval.

## Android Behavior

The existing `MobileTaskReducer` treats persisted worker events as status-bearing timeline entries. `worker.claimed`, `worker.observing`, and `worker.running` show the task as active; `worker.completed` and `worker.failed` show their terminal result. The app does not receive worker tokens, ADB details, model URLs, Portal state, raw UI trees, or screenshots.

The existing pause, cancel, confirmation, SSE replay, and reconnect behavior remains authoritative. The UI will surface a concise device label and sanitized result text only.

## Failure Handling

- Portal/ADB unavailable: report `waiting_for_device`; do not call Mobilerun.
- Local Ollama unavailable: report `waiting_for_compute`; do not fall back to cloud or phone inference.
- Lease lost, task paused, or task cancelled: stop before the next device action and publish no completion event from the stale worker.
- Mobilerun timeout or step budget exhausted: report `failed` with the safe category `execution_limit`.
- Worker process exit: lease expiry creates a visible recoverable state; no automated replay of a possibly external effect occurs.

## Test and Acceptance Gates

1. Gateway unit tests cover worker authentication, device filtering, task claim atomicity, lease expiry, duplicate result idempotency, pause/cancel races, and event replay order.
2. Python unit tests use a fake Gateway client and fake `TaskRunner` to verify poll/claim/report behavior, failure mapping, and no secret leakage.
3. Gateway/Python integration tests run against a fake Mobilerun adapter and prove Android-visible event replay from `queued` through a terminal state.
4. Emulator environment check verifies WSL ADB sees `emulator-5554`, the Mobilerun Portal is installed, and `mobilerun ping` succeeds.
5. Real smoke test runs the allowlisted Settings/version goal against the emulator, verifies a terminal Gateway event and its replay, and confirms no screenshot/trajectory file was created.
6. Android Compose and emulator tests verify the Tasks timeline shows the worker lifecycle and terminal result.

## Handoff Boundary

After these gates pass, the next independent design is physical-device Wi-Fi ADB enrollment. Native Horus Portal, LiteRT-LM phone inference, broader goal routing, vision/reasoning, and R2/R3 confirmation-aware action execution remain separate designs.

## Source Notes

- Mobilerun Framework quickstart: <https://docs.mobilerun.ai/framework/quickstart>
- Mobilerun device setup: <https://docs.mobilerun.ai/framework/guides/device-setup>
- Mobilerun SDK configuration: <https://docs.mobilerun.ai/framework/sdk/configuration>
- Mobilerun agent architecture: <https://docs.mobilerun.ai/framework/concepts/architecture>
