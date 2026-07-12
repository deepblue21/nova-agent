# Mobilerun Emulator Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the first safe Android emulator task through a WSL Python Mobilerun worker that uses the PC's local Ollama/GPU and reports an auditable lifecycle to the existing Gateway and Android Tasks UI.

**Architecture:** The Gateway owns task state, lease issuance, worker authentication, persistence, and SSE replay. A dedicated Python worker claims only explicitly allowlisted emulator tasks via worker-only endpoints, uses Mobilerun's direct/FastAgent path through the WSL ADB bridge, and reports sanitized progress and terminal outcomes. The Android client only renders Gateway events and never receives worker, ADB, Portal, or model credentials.

**Tech Stack:** Node.js 20.19+, Express 4, PostgreSQL 16, Node `node:test`, Supertest, Python 3.12, `uv` 0.11+, Mobilerun 0.6.10, local Ollama, WSL2 Ubuntu, Android SDK/ADB, Kotlin 2.0.21, Jetpack Compose, JDK 17, Android SDK 35.

## Global Constraints

- Canonical checkout: `C:\Users\salih\Project_Horus`; WSL path: `/mnt/c/Users/salih/Project_Horus`.
- Work on branch `codex/mobile-task-control-plane`; preserve user changes and never reset/revert unrelated work.
- Gateway remains the task/event/confirmation source of truth; the worker never connects to PostgreSQL.
- User API keys, `GATEWAY_TOKEN`, provider keys, raw UI trees, screenshots, model transcripts, credentials, and worker tokens never appear in Android payloads, task events, stdout, test fixtures, or commits.
- `MOBILE_WORKER_ENABLED=1` and a non-empty dedicated `MOBILE_WORKER_TOKEN` are required for worker routes. Disabled worker routes return `404`.
- Initial device is exactly `emulator-5554`; the only claimable normalized goals are `Open Settings and tell me the Android version` and `Ayarlari ac ve Android surumunu soyle`.
- A successful claim atomically binds `mobile_tasks.device_id` to `emulator-5554`. While the worker is enabled, an unsupported prompt is rejected at task creation with `400`; it is never silently left queued.
- Initial agent mode is direct (`reasoning=False`), no vision, no credentials, no telemetry/tracing, no saved trajectories, and coordinate tools `click_at`, `click_area`, and `long_press_at` remain disabled.
- R2/R3 actions, messages, purchases, account/permission changes, deletion, setting changes, physical phones, native AccessibilityService, and phone-side inference remain out of scope.
- Android stays `compileSdk=35`, `targetSdk=35`, `minSdk=26`, JVM 17. Gateway test command remains `npm --prefix gateway test`.
- Every task starts with a failing focused test, finishes with its focused tests, ends in a dedicated commit, and receives implementation plus requirements review before the next task.
- After every completed task, update the Project Horus living-delivery sections in both `README.md` and `README.tr.md` with verified completed work and the next concrete work item; include those README changes in the task's commit.

---

## File Map

### Gateway worker control plane

- Create `gateway/migrations/010_mobile_worker_leases.sql`: persistent worker leases and idempotent report records.
- Create `gateway/lib/mobile_worker_policy.mjs`: exact normalized-goal policy and bounded report-phase validation.
- Create `gateway/lib/mobile_worker_auth.mjs`: timing-safe dedicated worker bearer-token middleware.
- Create `gateway/lib/mobile_worker_store.mjs`: transactional claim, active-status check, report, and lease-expiry operations.
- Create `gateway/routes/mobile_worker.mjs`: worker-only claim, status, report, and expiry endpoints.
- Modify `gateway/gateway.mjs`: mount the worker router after baseline JSON/rate middleware and before user principal middleware.
- Modify `gateway/routes/mobile_tasks.mjs`: reject unsupported prompts when the strict emulator worker is enabled.
- Modify `gateway/.env.example`: document worker feature, token, device, lease, and strict policy configuration.
- Create `gateway/test/mobile_worker_policy.test.mjs`, `gateway/test/mobile_worker_auth.test.mjs`, `gateway/test/mobile_worker_store.test.mjs`, and `gateway/test/mobile_worker_route.test.mjs`.

### Python worker

- Create `mobile-worker/pyproject.toml` and generated `mobile-worker/uv.lock`: Python 3.12 project with direct dependencies on `mobilerun==0.6.10` and `httpx`.
- Create `mobile-worker/.env.example`: non-secret runtime configuration only.
- Create `mobile-worker/src/horus_mobile_worker/models.py`: typed task, lease, report, and run-outcome value objects.
- Create `mobile-worker/src/horus_mobile_worker/config.py`: strict environment validation and no-secret logging representation.
- Create `mobile-worker/src/horus_mobile_worker/gateway_client.py`: worker bearer HTTP client with a lease header and non-leaking error mapping.
- Create `mobile-worker/src/horus_mobile_worker/runner.py`: Mobilerun direct-mode adapter and Portal/ADB readiness probe.
- Create `mobile-worker/src/horus_mobile_worker/service.py`: bounded poll/claim/run/report orchestration with cancellation/lease checks.
- Create `mobile-worker/src/horus_mobile_worker/__main__.py`: process entry point.
- Create `mobile-worker/tests/test_config.py`, `mobile-worker/tests/test_gateway_client.py`, and `mobile-worker/tests/test_service.py`: standard-library `unittest` coverage using fakes.

### Android and operations

- Modify `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt`: retain an optional status from a persisted event.
- Modify `nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt`: parse safe worker event status from the event payload.
- Modify `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt`: apply status-bearing worker events to the visible task.
- Modify `nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt` and `nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt`: worker event parser/reducer regression coverage.
- Modify `nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt`: terminal worker status rendering coverage.
- Modify `docker-compose.yml`: publish Gateway only on `127.0.0.1:8088` so the WSL worker and Android emulator can reach it without Caddy/port 80.
- Create `scripts/smoke-mobilerun-worker.mjs`: authenticated end-to-end worker lifecycle smoke that never prints credentials.
- Create `scripts/smoke-mobilerun-worker.test.mjs`: worker-smoke environment and SSE parser tests.
- Modify `package.json`, `PROJECT_HORUS.md`, and `gateway/.env.example`: worker smoke command and setup/run instructions.

---

### Task 1: Define Worker Policy and Dedicated Authentication

**Files:**
- Create: `gateway/lib/mobile_worker_policy.mjs`
- Create: `gateway/lib/mobile_worker_auth.mjs`
- Create: `gateway/test/mobile_worker_policy.test.mjs`
- Create: `gateway/test/mobile_worker_auth.test.mjs`

**Interfaces:**
- Consumes: Node `crypto.timingSafeEqual`, Express `req.headers.authorization`, and the approved `MOBILE_WORKER_GOAL_POLICY=settings_android_version` setting.
- Produces: `allowedWorkerPrompts(policy)`, `isAllowedWorkerPrompt(prompt, policy)`, `parseWorkerReport(input)`, and `createWorkerAuth({ enabled, token })` for the route/store tasks.

- [ ] **Step 1: Write focused failing policy and auth tests**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { allowedWorkerPrompts, isAllowedWorkerPrompt, parseWorkerReport } from "../lib/mobile_worker_policy.mjs";

test("settings policy accepts only normalized safe goals", () => {
  assert.equal(isAllowedWorkerPrompt("  Open Settings and tell me the Android version ", "settings_android_version"), true);
  assert.equal(isAllowedWorkerPrompt("Send a message to Ada", "settings_android_version"), false);
  assert.deepEqual(allowedWorkerPrompts("settings_android_version"), [
    "open settings and tell me the android version",
    "ayarlari ac ve android surumunu soyle",
  ]);
});

test("worker reports reject unknown phases and unsafe fields", () => {
  assert.deepEqual(parseWorkerReport({ phase: "completed", summary: "Android 17", steps: 4, report_id: "11111111-1111-4111-8111-111111111111" }).phase, "completed");
  assert.throws(() => parseWorkerReport({ phase: "delete_everything" }), /invalid worker phase/);
});
```

```js
test("worker auth only accepts the configured dedicated bearer", async () => {
  const auth = createWorkerAuth({ enabled: true, token: "worker-secret" });
  const denied = await call(auth, "Bearer nv_user_key");
  const allowed = await call(auth, "Bearer worker-secret");
  assert.equal(denied.status, 401);
  assert.equal(allowed.nextCalled, true);
});
```

- [ ] **Step 2: Run the focused tests and record the missing-module failure**

Run: `npm --prefix gateway test -- test/mobile_worker_policy.test.mjs test/mobile_worker_auth.test.mjs`

Expected: FAIL with `ERR_MODULE_NOT_FOUND` for the two production modules.

- [ ] **Step 3: Implement the policy module**

```js
export const WORKER_PHASES = new Set([
  "observing", "running", "completed", "failed", "waiting_for_device", "waiting_for_compute",
]);

const SETTINGS_ANDROID_VERSION_PROMPTS = Object.freeze([
  "open settings and tell me the android version",
  "ayarlari ac ve android surumunu soyle",
]);

export function normalizeWorkerPrompt(value) {
  return String(value ?? "").trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US");
}

export function allowedWorkerPrompts(policy) {
  if (policy !== "settings_android_version") return [];
  return SETTINGS_ANDROID_VERSION_PROMPTS;
}

export function isAllowedWorkerPrompt(prompt, policy) {
  return allowedWorkerPrompts(policy).includes(normalizeWorkerPrompt(prompt));
}
```

Implement `parseWorkerReport` with UUID validation for `report_id` and `lease_id`, a 1,000-code-point `summary` cap, an integer `steps` range of `0..8`, and `error_code` allowlist `{ device_unavailable, compute_unavailable, execution_limit, execution_failed }`. Return an immutable normalized object; never include raw upstream error text.

- [ ] **Step 4: Implement dedicated worker auth**

```js
import { timingSafeEqual } from "node:crypto";

function equal(left, right) {
  const a = Buffer.from(String(left));
  const b = Buffer.from(String(right));
  return a.length === b.length && timingSafeEqual(a, b);
}

export function createWorkerAuth({ enabled, token }) {
  return (req, res, next) => {
    if (!enabled) return res.status(404).end();
    const bearer = /^Bearer\s+(.+)$/i.exec(req.get("Authorization") || "")?.[1] || "";
    if (!token || !equal(bearer, token)) return res.status(401).json({ error: "worker unauthorized" });
    next();
  };
}
```

- [ ] **Step 5: Run focused and full Gateway tests**

Run: `npm --prefix gateway test -- test/mobile_worker_policy.test.mjs test/mobile_worker_auth.test.mjs`

Expected: both focused files pass.

Run: `npm --prefix gateway test`

Expected: all existing Gateway tests plus the new policy/auth tests pass.

- [ ] **Step 6: Commit the pure security boundary**

```bash
git add gateway/lib/mobile_worker_policy.mjs gateway/lib/mobile_worker_auth.mjs gateway/test/mobile_worker_policy.test.mjs gateway/test/mobile_worker_auth.test.mjs
git commit -m "feat: add mobile worker policy and auth"
```

### Task 2: Persist Worker Leases and Idempotent Reports

**Files:**
- Create: `gateway/migrations/010_mobile_worker_leases.sql`
- Create: `gateway/lib/mobile_worker_store.mjs`
- Create: `gateway/test/mobile_worker_store.test.mjs`

**Interfaces:**
- Consumes: `allowedWorkerPrompts`, `parseWorkerReport`, PostgreSQL `q`/`withTx`, `mobile_tasks`, and `mobile_task_events`.
- Produces: `createMobileWorkerStore({ q, withTx, now })` with `claimNext`, `getActiveStatus`, `report`, and `expireLeases` methods. Route code receives `{ task, lease, event }` or `null`, then publishes only stored events.

- [ ] **Step 1: Write the store contract tests before the migration/store**

```js
test("claimNext locks the oldest supported queued task and records lease events", async () => {
  const out = await store.claimNext({ deviceId: "emulator-5554", policy: "settings_android_version" });
  assert.equal(out.task.status, "executing");
  assert.equal(out.lease.device_id, "emulator-5554");
  assert.deepEqual(out.events.map(event => event.type), ["worker.claimed", "worker.executing"]);
});

test("a report retry with the same lease and report ID returns the saved event once", async () => {
  const first = await store.report(reportInput({ phase: "completed" }));
  const retry = await store.report(reportInput({ phase: "completed" }));
  assert.equal(first.replayed, false);
  assert.equal(retry.replayed, true);
  assert.equal(events.filter(event => event.type === "worker.completed").length, 1);
});

test("expired active leases become waiting_for_device without replaying a device action", async () => {
  const expired = await store.expireLeases(new Date("2026-07-11T12:02:01Z"));
  assert.equal(expired[0].task.status, "waiting_for_device");
  assert.equal(expired[0].event.type, "worker.lease_expired");
});
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `npm --prefix gateway test -- test/mobile_worker_store.test.mjs`

Expected: FAIL because `mobile_worker_store.mjs` does not exist.

- [ ] **Step 3: Add the migration**

```sql
CREATE TABLE IF NOT EXISTS mobile_worker_leases (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  device_id text NOT NULL CHECK (char_length(device_id) BETWEEN 1 AND 200),
  token_hash text NOT NULL,
  state text NOT NULL DEFAULT 'active' CHECK (state IN ('active','closed','expired')),
  expires_at timestamptz NOT NULL,
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS mobile_one_active_worker_lease_idx
  ON mobile_worker_leases(task_id) WHERE state = 'active';

CREATE TABLE IF NOT EXISTS mobile_worker_reports (
  lease_id uuid NOT NULL REFERENCES mobile_worker_leases(id) ON DELETE CASCADE,
  report_id uuid NOT NULL,
  phase text NOT NULL,
  event_id bigint REFERENCES mobile_task_events(id) ON DELETE SET NULL,
  task_status text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (lease_id, report_id)
);
```

- [ ] **Step 4: Implement transactional store methods**

`claimNext({ deviceId, policy })` must execute `SELECT ... FOR UPDATE SKIP LOCKED`, filter exact normalized prompt variants in SQL, reject a non-emulator device, insert a token-hash-only lease, update the task's `device_id` to `emulator-5554` and status to `executing`, and insert both `worker.claimed` and `worker.executing` events in the same transaction.

```js
export function createMobileWorkerStore({ q: query = q, withTx: transaction = withTx, now = () => new Date(), randomBytes = crypto.randomBytes } = {}) {
  return {
    claimNext: ({ deviceId, policy }) => claimNext(transaction, { deviceId, policy, now, randomBytes }),
    getActiveStatus: ({ taskId, token }) => getActiveStatus(query, { taskId, token, now }),
    report: (input) => report(transaction, { ...input, now }),
    expireLeases: (at = now()) => expireLeases(transaction, at),
  };
}
```

`report` must lock the lease and task, compare the supplied token against its stored SHA-256 hash with `hashEquals`, reject an expired/non-active lease, look up `(lease_id, report_id)` before inserting an event, map phases using this exact table, and close the lease for all non-progress phases:

| phase | task status | event |
| --- | --- | --- |
| `observing` | `observing` | `worker.observing` |
| `running` | `executing` | `worker.running` |
| `completed` | `completed` | `worker.completed` |
| `failed` | `failed` | `worker.failed` |
| `waiting_for_device` | `waiting_for_device` | `worker.waiting_for_device` |
| `waiting_for_compute` | `waiting_for_compute` | `worker.waiting_for_compute` |

- [ ] **Step 5: Run store tests, migration, and full Gateway suite**

Run: `npm --prefix gateway test -- test/mobile_worker_store.test.mjs`

Expected: focused store tests pass, including lock/query contract assertions and duplicate reports.

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose run --rm migrate`

Expected: `applied 010_mobile_worker_leases.sql` or `skip 010_mobile_worker_leases.sql`, followed by `migrations done`.

Run: `npm --prefix gateway test`

Expected: full suite passes.

- [ ] **Step 6: Commit the lease persistence layer**

```bash
git add gateway/migrations/010_mobile_worker_leases.sql gateway/lib/mobile_worker_store.mjs gateway/test/mobile_worker_store.test.mjs
git commit -m "feat: persist mobile worker leases"
```

### Task 3: Expose Worker-Only Gateway Routes

**Files:**
- Create: `gateway/routes/mobile_worker.mjs`
- Create: `gateway/test/mobile_worker_route.test.mjs`
- Modify: `gateway/gateway.mjs`
- Modify: `gateway/routes/mobile_tasks.mjs`
- Modify: `gateway/test/mobile_tasks_route.test.mjs`

**Interfaces:**
- Consumes: `createWorkerAuth`, `createMobileWorkerStore`, `mobileEventBroker`, and the worker store output from Task 2.
- Produces: `POST /v1/internal/mobile-worker/claims`, `GET /v1/internal/mobile-worker/tasks/:id/status`, `POST /v1/internal/mobile-worker/tasks/:id/reports`, and `POST /v1/internal/mobile-worker/leases/expire`.

- [ ] **Step 1: Write Supertest route tests using injected store/broker fakes**

```js
test("claim requires a worker token and publishes only persisted events", async () => {
  const denied = await request(app).post("/v1/internal/mobile-worker/claims").send({ device_id: "emulator-5554" });
  const claimed = await request(app).post("/v1/internal/mobile-worker/claims")
    .set("Authorization", "Bearer worker-secret")
    .send({ device_id: "emulator-5554" });
  assert.equal(denied.status, 401);
  assert.equal(claimed.status, 201);
  assert.deepEqual(broker.published.map(event => event.type), ["worker.claimed", "worker.executing"]);
});

test("report validates the lease header, is idempotent, and never leaks an internal error", async () => {
  const response = await request(app).post(`/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`)
    .set("Authorization", "Bearer worker-secret")
    .set("X-Horus-Lease-Token", "lease-token")
    .send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed", summary: "Android 17", steps: 3 });
  assert.equal(response.status, 200);
  assert.equal(response.body.status, "completed");
});
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `npm --prefix gateway test -- test/mobile_worker_route.test.mjs`

Expected: FAIL because `mobile_worker.mjs` has not been created.

- [ ] **Step 3: Implement the router with route-local auth**

```js
export function createMobileWorkerRouter({
  store = createMobileWorkerStore(),
  broker = mobileEventBroker,
  enabled = process.env.MOBILE_WORKER_ENABLED === "1",
  token = process.env.MOBILE_WORKER_TOKEN || "",
} = {}) {
  const router = Router();
  router.use(createWorkerAuth({ enabled, token }));
  router.post("/v1/internal/mobile-worker/claims", asyncRoute(async (req, res) => {
    const claimed = await store.claimNext({ deviceId: req.body?.device_id, policy: process.env.MOBILE_WORKER_GOAL_POLICY || "settings_android_version" });
    if (!claimed) return res.status(204).end();
    for (const event of claimed.events) broker.publish(event);
    res.status(201).json({ task: claimed.task, lease: claimed.lease });
  }));
  return router;
}
```

Validate UUID route IDs, require a nonblank `X-Horus-Lease-Token`, only return task status/lease expiry from the status route, and use the parsed report from Task 1. The report route publishes `result.event` only when `result.replayed` is false. Map invalid input to `400`, missing/stale lease to `409`, unknown task to `404`, and disabled worker to `404`.

In `createMobileTasksRouter`, accept `workerEnabled` and `workerGoalPolicy` options. Before `store.createTask`, reject an unsupported prompt only when `workerEnabled` is true:

```js
if (workerEnabled && !isAllowedWorkerPrompt(prompt, workerGoalPolicy)) {
  return res.status(400).json({ error: "task is not supported by this emulator worker" });
}
```

Add a route regression test that makes `workerEnabled: true`, submits `Send a message to Ada`, expects `400`, and asserts that `store.createTask` was never called. Keep existing unrestricted task creation behavior when `workerEnabled` is false.

- [ ] **Step 4: Mount the worker router before principal auth**

In `gateway/gateway.mjs`, import `mobileWorker` and insert `app.use(mobileWorker);` after JSON/rate-limit middleware and before `const principalMiddleware = principal();`. This preserves baseline headers/CORS/body limits while keeping dedicated worker authentication separate from user principal authentication.

- [ ] **Step 5: Run focused and full tests**

Run: `npm --prefix gateway test -- test/mobile_worker_route.test.mjs test/mobile_worker_store.test.mjs`

Expected: worker routes return the documented status codes and publish exactly one stored event per report.

Run: `npm --prefix gateway test`

Expected: full Gateway suite passes.

- [ ] **Step 6: Commit the worker API**

```bash
git add gateway/routes/mobile_worker.mjs gateway/test/mobile_worker_route.test.mjs gateway/gateway.mjs
git commit -m "feat: expose mobile worker control API"
```

### Task 4: Build the Isolated Python Worker

**Files:**
- Create: `mobile-worker/pyproject.toml`
- Create: `mobile-worker/.env.example`
- Create: `mobile-worker/src/horus_mobile_worker/__init__.py`
- Create: `mobile-worker/src/horus_mobile_worker/models.py`
- Create: `mobile-worker/src/horus_mobile_worker/config.py`
- Create: `mobile-worker/src/horus_mobile_worker/gateway_client.py`
- Create: `mobile-worker/src/horus_mobile_worker/runner.py`
- Create: `mobile-worker/src/horus_mobile_worker/service.py`
- Create: `mobile-worker/src/horus_mobile_worker/__main__.py`
- Create: `mobile-worker/tests/test_config.py`
- Create: `mobile-worker/tests/test_gateway_client.py`
- Create: `mobile-worker/tests/test_service.py`

**Interfaces:**
- Consumes: Task 3 HTTP contract and local `mobilerun==0.6.10` / Ollama configuration.
- Produces: `horus-mobile-worker` process that claims one lease, checks status before/after execution, reports progress/terminal outcome, and never writes raw device/model data to Gateway events.

- [ ] **Step 1: Create failing standard-library unit tests**

```python
class WorkerServiceTests(unittest.IsolatedAsyncioTestCase):
    async def test_safe_claim_runs_and_reports_sanitized_completion(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task=task(), lease=lease()))
        runner = FakeRunner(RunOutcome(success=True, summary="Android 17", steps=3))
        await WorkerService(client, runner, settings()).run_once()
        self.assertEqual([report.phase for report in client.reports], ["observing", "running", "completed"])
        self.assertEqual(client.reports[-1].summary, "Android 17")

    async def test_cancelled_status_prevents_runner_invocation(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task=task(), lease=lease()), statuses=["cancelled"])
        runner = FakeRunner(RunOutcome(success=True, summary="unexpected", steps=1))
        await WorkerService(client, runner, settings()).run_once()
        self.assertEqual(runner.calls, [])

    async def test_pause_during_execution_cancels_the_runner_and_omits_terminal_report(self) -> None:
        client = FakeGatewayClient(claim=ClaimedTask(task=task(), lease=lease()), statuses=["executing", "paused"])
        runner = BlockingFakeRunner()
        await WorkerService(client, runner, settings(status_poll_seconds=0.01)).run_once()
        self.assertEqual(runner.cancelled_task_ids, ["task-1"])
        self.assertNotIn("completed", [report.phase for report in client.reports])
```

- [ ] **Step 2: Run tests to demonstrate the initial failure**

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus/mobile-worker -- python3 -m unittest discover -s tests -v`

Expected: FAIL because the worker package does not exist.

- [ ] **Step 3: Define the pinned Python project and data model**

```toml
[project]
name = "horus-mobile-worker"
version = "0.1.0"
requires-python = ">=3.12,<3.13"
dependencies = [
  "httpx>=0.27,<1",
  "mobilerun==0.6.10",
]

[project.scripts]
horus-mobile-worker = "horus_mobile_worker.__main__:main"
```

```python
@dataclass(frozen=True)
class RunOutcome:
    success: bool
    summary: str
    steps: int
    error_code: str | None = None

class TaskRunner(Protocol):
    async def readiness(self, device_id: str) -> None: ...
    async def run(self, *, task_id: str, prompt: str, device_id: str) -> RunOutcome: ...
    async def cancel(self, task_id: str) -> None: ...
```

`WorkerSettings.from_env` must require `HORUS_GATEWAY_URL`, `MOBILE_WORKER_TOKEN`, and `MOBILE_WORKER_DEVICE_ID=emulator-5554`; validate the Ollama URL; clamp `MOBILE_WORKER_MAX_STEPS` to `1..8`; and expose `redacted()` that replaces token values with `<redacted>`.

- [ ] **Step 4: Implement the HTTP client and orchestration service**

Use `httpx.AsyncClient(timeout=15.0)` and attach only `Authorization: Bearer <worker-token>` plus `X-Horus-Lease-Token` for per-lease status/report calls.

```python
async def run_once(self) -> bool:
    claimed = await self._gateway.claim(self._settings.device_id)
    if claimed is None:
        return False
    await self._gateway.report(claimed, phase="observing", summary="Emulator and Portal readiness check")
    await self._runner.readiness(claimed.task.device_id)
    await self._assert_active(claimed)
    await self._gateway.report(claimed, phase="running", summary="Safe Settings workflow started")
    outcome = await self._runner.run(task_id=claimed.task.id, prompt=claimed.task.prompt, device_id=claimed.task.device_id)
    await self._assert_active(claimed)
    await self._gateway.report(claimed, phase="completed" if outcome.success else "failed", summary=outcome.summary, steps=outcome.steps, error_code=outcome.error_code)
    return True
```

Run the adapter in an `asyncio.Task` and poll `getActiveStatus` every `status_poll_seconds` while it is running. If status is no longer `observing` or `executing`, call `await runner.cancel(task_id)`, cancel/await the adapter task, and return without a terminal report. `MobilerunTaskRunner.cancel` cancels its tracked `MobileAgent.run()` task; no new Mobilerun operation is started after cancellation is observed.

Map readiness failures to `waiting_for_device`, Ollama connectivity failures to `waiting_for_compute`, `asyncio.TimeoutError` or step exhaustion to `failed/execution_limit`, and every other caught adapter failure to `failed/execution_failed`. Log only `task_id`, `lease_id`, phase, and bounded summary.

- [ ] **Step 5: Implement the Mobilerun direct-mode adapter**

Construct a `MobileConfig` with `DeviceConfig(serial=device_id, platform="android", use_tcp=False, auto_setup=False)`, direct `AgentConfig(reasoning=False, max_steps=settings.max_steps, streaming=False)`, disabled coordinate tools, disabled telemetry/tracing, no trajectory saving, and no credentials. Configure all direct-agent LLM profiles as Ollama at `settings.ollama_url` with `settings.ollama_model`; set `temperature=0.0`, `max_tokens=2048`, and `context_window=32768`.

```python
async def run(self, *, task_id: str, prompt: str, device_id: str) -> RunOutcome:
    config = self._config_for(device_id)
    agent = MobileAgent(goal=prompt, config=config)
    result = await agent.run()
    return RunOutcome(
        success=bool(result.success),
        summary=_cap_summary(result.reason if result.success else "The safe emulator workflow did not complete"),
        steps=min(int(result.steps or 0), self._settings.max_steps),
        error_code=None if result.success else "execution_failed",
    )
```

`readiness` must invoke `mobilerun ping --device emulator-5554` using `asyncio.create_subprocess_exec`, capture output privately, and raise a `DeviceUnavailable` exception on a non-zero exit. The only run goal enters through Gateway's exact allowlist; do not add a direct arbitrary-prompt CLI.

- [ ] **Step 6: Install/lock dependencies and run all Python tests**

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus/mobile-worker -- uv lock`

Expected: `uv.lock` is created with `mobilerun==0.6.10` and `httpx`.

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus/mobile-worker -- uv run python -m unittest discover -s tests -v`

Expected: all Python config/client/service tests pass without a connected device, Portal, LLM, or secret.

- [ ] **Step 7: Commit the worker package**

```bash
git add mobile-worker
git commit -m "feat: add Mobilerun emulator worker"
```

### Task 5: Render Worker State in Android Tasks

**Files:**
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt`
- Modify: `nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt`
- Modify: `nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt`
- Modify: `nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt`

**Interfaces:**
- Consumes: task-event payload `{ status, summary, steps, error_code }` from Task 3.
- Produces: UI task status that follows replayed `worker.*` events and a timeline that shows only the sanitized summary supplied by Gateway.

- [ ] **Step 1: Add failing parser/reducer tests**

```kotlin
@Test
fun parsesWorkerEventStatusAndUpdatesTheVisibleTask() {
    val parsed = MobileTaskClient.parseEvent(
        """{"id": "2", "task_id": "task-1", "type": "worker.completed", "payload": {"status": "completed", "summary": "Android 17"}}""",
        "2",
    )!!
    val state = reduceMobileTask(
        MobileTaskUiState(task = MobileTask("task-1", "Open Settings and tell me the Android version", MobileTaskStatus.EXECUTING)),
        MobileTaskMutation.EventReceived(parsed),
    )
    assertEquals(MobileTaskStatus.COMPLETED, state.task?.status)
    assertEquals("Android 17", state.events.single().summary)
}
```

- [ ] **Step 2: Run focused JVM tests and verify failure**

Run: `cd nova-android; .\gradlew.bat testDebugUnitTest --tests com.nova.agent.MobileTaskClientTest --tests com.nova.agent.MobileTaskReducerTest --console=plain`

Expected: FAIL because `MobileTaskEvent` has no status field and the reducer does not update the task from events.

- [ ] **Step 3: Carry status from client parser to reducer**

```kotlin
data class MobileTaskEvent(
    val id: String,
    val taskId: String,
    val type: String,
    val summary: String,
    val status: MobileTaskStatus? = null,
    val confirmation: MobileConfirmation? = null,
)
```

In `parseEvent`, map `payload.optString("status")` only when nonblank. In `reduceMobileTask`, update only the matching in-memory task:

```kotlin
val updatedTask = state.task?.takeIf { it.id == mutation.event.taskId }?.let { current ->
    mutation.event.status?.let { status -> current.copy(status = status) } ?: current
}
```

Keep numeric event ordering, duplicate suppression, confirmation semantics, and `loading = false` unchanged.

Map task-creation HTTP `400` to `Bu gorev emulator worker'inda desteklenmiyor` when the Gateway returns the strict worker error; keep the generic safe message for other `400` responses.

- [ ] **Step 4: Extend Compose instrumentation coverage**

```kotlin
composeRule.onNodeWithText("COMPLETED").assertIsDisplayed()
composeRule.onNodeWithText("Android 17").assertIsDisplayed()
```

Build the screen state with a terminal `worker.completed` event; do not add worker tokens, configuration controls, screenshots, or device details to the Android interface.

- [ ] **Step 5: Run focused, full Android, and emulator tests**

Run: `cd nova-android; .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain`

Expected: unit tests, lint, and debug APK succeed.

Run: `cd nova-android; .\gradlew.bat connectedDebugAndroidTest --console=plain`

Expected: Compose test passes on `emulator-5554`.

- [ ] **Step 6: Commit the UI lifecycle update**

```bash
git add nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt
git commit -m "feat: render mobile worker lifecycle"
```

### Task 6: Make the Local Worker Reachable and Prepare WSL ADB

**Files:**
- Modify: `docker-compose.yml`
- Modify: `gateway/.env.example`
- Modify: `mobile-worker/.env.example`
- Modify: `PROJECT_HORUS.md`
- Modify: `package.json`

**Interfaces:**
- Consumes: worker endpoints from Task 3, Windows Android SDK ADB server, WSL Ubuntu, and the Docker gateway service.
- Produces: loopback-only Gateway connectivity at `http://127.0.0.1:8088/v1`, documented worker configuration, and a verified WSL ADB client path to `emulator-5554`.

- [ ] **Step 1: Add configuration and a failing connectivity assertion**

Add a Node smoke preflight test that refuses to run when `HORUS_GATEWAY_URL` is not loopback or `MOBILE_WORKER_TOKEN` is absent:

```js
assert.throws(() => workerEnvironment({ HORUS_GATEWAY_URL: "http://0.0.0.0:8088/v1" }), /loopback/);
assert.throws(() => workerEnvironment({ HORUS_GATEWAY_URL: "http://127.0.0.1:8088/v1" }), /MOBILE_WORKER_TOKEN/);
```

Run: `node --test scripts/smoke-mobilerun-worker.test.mjs`

Expected: FAIL because the worker smoke/preflight module does not exist.

- [ ] **Step 2: Publish only the loopback Gateway port**

Add this under the `gateway` service in `docker-compose.yml`:

```yaml
    ports:
      - "127.0.0.1:8088:8088"
```

Do not publish PostgreSQL, Redis, or the worker API separately. Keep Caddy optional; its pre-existing host port 80 conflict is not a reason to expose the Gateway to the LAN.

Add this exact configuration to `gateway/.env.example` and `mobile-worker/.env.example`:

```env
MOBILE_WORKER_ENABLED=0
MOBILE_WORKER_TOKEN=
MOBILE_WORKER_DEVICE_ID=emulator-5554
MOBILE_WORKER_LEASE_MS=120000
MOBILE_WORKER_GOAL_POLICY=settings_android_version
HORUS_GATEWAY_URL=http://127.0.0.1:8088/v1
MOBILE_WORKER_OLLAMA_URL=http://127.0.0.1:11434
MOBILE_WORKER_OLLAMA_MODEL=qwen3:14b
MOBILE_WORKER_MAX_STEPS=8
```

- [ ] **Step 3: Establish the WSL ADB bridge without exposing it publicly**

Install the Linux ADB client only after checking whether it is already available. The WSL root user is used so the setup never asks for a password in the worker shell:

```bash
wsl.exe -d Ubuntu -- adb version
wsl.exe -d Ubuntu -u root -- apt-get update
wsl.exe -d Ubuntu -u root -- apt-get install -y adb
```

On Windows, restart the Android SDK ADB server with listen-all enabled but hidden. Derive the source from the WSL `eth0` interface rather than `hostname -I`, because Docker bridge addresses can also appear there. Resolve the host through `host.docker.internal`; on this WSL configuration the generated `/etc/resolv.conf` nameserver is a local loopback address and must not be used as a Windows-host address. Create a firewall rule that permits TCP 5037 only from that explicit WSL address; this command requires an elevated PowerShell and must not use `Any` as the remote address:

```powershell
$adb = 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$eth0 = (wsl.exe -d Ubuntu -- ip -o -4 addr show dev eth0).Trim()
$wslIp = [regex]::Match($eth0, '\b(?:\d{1,3}\.){3}\d{1,3}(?=/)').Value
$hostRows = wsl.exe -d Ubuntu -- getent ahostsv4 host.docker.internal
$windowsHost = [regex]::Match(($hostRows -join "`n"), '\b(?:\d{1,3}\.){3}\d{1,3}\b').Value
if (-not $wslIp -or -not $windowsHost) { throw 'Unable to resolve the WSL source or Windows host address' }
Get-NetFirewallRule -DisplayName 'Horus WSL ADB bridge' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName 'Horus WSL ADB bridge' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5037 -Program $adb -RemoteAddress $wslIp
& $adb kill-server
Start-Process -FilePath $adb -ArgumentList @('-a', 'server', 'nodaemon') -WindowStyle Hidden
```

In WSL, derive the Windows host IP from `host.docker.internal`, set it only for the worker shell, and prove it sees the existing emulator:

```bash
export ADB_SERVER_SOCKET="tcp:$(getent ahostsv4 host.docker.internal | awk 'NR == 1 { print $1 }'):5037"
adb devices
```

Expected: a line exactly matching `emulator-5554\tdevice`. If it does not appear, stop the Mobilerun installation and report the bridge failure; do not fall back to USB, expose a public ADB port, or use a second ADB server.

- [ ] **Step 4: Add/document the worker smoke command**

Add:

```json
"smoke:mobile-worker": "node scripts/smoke-mobilerun-worker.mjs"
```

The smoke script must use `HORUS_GATEWAY_URL`, `HORUS_API_KEY`, and wait for the exact worker event sequence `task.created`, `worker.claimed`, `worker.executing`, `worker.observing`, `worker.running`, `worker.completed`; it must set a 120-second `AbortController` timeout and print only `mobilerun worker smoke: ok` on success.

Document worker launch with:

```bash
cd /mnt/c/Users/salih/Project_Horus/mobile-worker
set -a; source .env; set +a
export PATH="$HOME/.local/bin:$PATH"
uv run horus-mobile-worker --once
```

- [ ] **Step 5: Verify isolated connectivity and configuration**

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose up -d --build postgres redis migrate gateway`

Expected: PostgreSQL, Redis, and Gateway become healthy; Gateway is reachable only from loopback on port 8088.

Run: `wsl.exe -d Ubuntu -- curl --fail http://127.0.0.1:8088/health`

Expected: `{ "ok": true }` or equivalent compact health JSON.

Run: `node --test scripts/smoke-mobilerun-worker.test.mjs`

Expected: configuration preflight tests pass.

- [ ] **Step 6: Commit local-runtime wiring**

```bash
git add docker-compose.yml gateway/.env.example mobile-worker/.env.example PROJECT_HORUS.md package.json scripts/smoke-mobilerun-worker.mjs scripts/smoke-mobilerun-worker.test.mjs
git commit -m "chore: wire local mobile worker runtime"
```

### Task 7: Install Portal and Prove the Safe End-to-End Workflow

**Files:**
- Modify: `scripts/smoke-mobilerun-worker.mjs`
- Modify: `PROJECT_HORUS.md`
- Create: `.superpowers/sdd/task-11-report.md` (ignored execution record)

**Interfaces:**
- Consumes: all previous tasks, WSL ADB bridge, Mobilerun Portal, local Ollama, Docker Gateway, and the installed Android emulator.
- Produces: a real worker lifecycle from Android-created task to `worker.completed`, plus an APK that visibly renders the terminal status.

- [ ] **Step 1: Prove Portal installation and reachability before a task**

Run from the WSL worker environment with the bridged `ADB_SERVER_SOCKET`:

```bash
cd /mnt/c/Users/salih/Project_Horus/mobile-worker
uv sync
uv run mobilerun setup --device emulator-5554
uv run mobilerun ping --device emulator-5554
```

Expected: Portal is installed and accessible. Do not run the task if this command fails.

- [ ] **Step 2: Run the worker against the exact allowlisted task**

Create a temporary bootstrap user/API key without printing it; export it only into the shell that runs the smoke. Start `horus-mobile-worker --once` in a hidden/background WSL process with its own ignored `.env`, then run:

```bash
HORUS_GATEWAY_URL=http://127.0.0.1:8088/v1 \
HORUS_API_KEY="$temporary_api_key" \
node /mnt/c/Users/salih/Project_Horus/scripts/smoke-mobilerun-worker.mjs
```

Expected: `mobilerun worker smoke: ok`; no screenshot, trajectory, provider key, API key, raw UI tree, or model transcript is written under the repository.

- [ ] **Step 3: Verify replay and Android rendering**

Run: `npm --prefix gateway test`

Expected: all Gateway tests pass after the real run.

Run: `cd nova-android; .\gradlew.bat installDebug connectedDebugAndroidTest --console=plain`

Expected: debug APK installs and Compose instrumentation passes on `emulator-5554`.

Use ADB UI-tree inspection to verify the real Tasks screen shows the terminal `COMPLETED` state and the sanitized Android-version summary. Capture one screenshot into ignored `.superpowers/sdd/` evidence only; do not upload it or store it in Gateway.

- [ ] **Step 4: Run the full delivery matrix**

Run: `npm --prefix gateway test`

Expected: all Gateway tests pass.

Run: `npm --prefix web run build`

Expected: production web build succeeds; a chunk-size warning is non-fatal.

Run: `cd nova-android; .\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain`

Expected: Android unit tests, lint, APK assembly, and emulator instrumentation all pass.

Run: `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose ps`

Expected: PostgreSQL, Redis, and Gateway are healthy; migration completed successfully.

- [ ] **Step 5: Record the real outcome without source churn**

Write the precise result, environment evidence, test counts, APK path, Portal version, and any bridge limitation to the ignored report. Do not change source files solely to record a runtime result. If the smoke exposes a defect, stop this task and create a focused corrective task with a failing regression test.

```bash
git status --short
```

Expected: no tracked source change is introduced by the verification-only task.

## Plan Self-Review

### Spec Coverage

- Dedicated local worker, PC GPU/Ollama, exact emulator, direct Mobilerun configuration, safety defaults, sanitized lifecycle events, and no direct database access are covered by Tasks 1-4.
- Android status rendering and replay are covered by Task 5.
- Loopback-only Gateway reachability, WSL ADB bridge, Portal setup, real emulator smoke, and delivery validation are covered by Tasks 6-7.
- Physical phones, native Portal, vision/reasoning, R2/R3, and phone inference are explicitly excluded in global constraints and no task adds them.

### Placeholder Scan

The plan contains no unfinished placeholder markers, undefined handoff, or generic test instruction. Every code task has named paths, interfaces, failing-test evidence, expected command output, and a commit boundary.

### Type Consistency

- Gateway report payload uses `lease_id`, `report_id`, `phase`, `summary`, `steps`, and optional `error_code` consistently across policy, store, routes, worker model/client, and smoke plans.
- Worker task/lease shapes match the approved route response: `{ task, lease }` with `task.id`, `task.prompt`, `task.device_id`, `lease.id`, `lease.token`, and `lease.expires_at`.
- Android derives only the existing wire `status` field as `MobileTaskStatus`; it does not depend on worker-only identifiers.
