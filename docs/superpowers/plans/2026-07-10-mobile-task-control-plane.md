# Mobile Task Control Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working Project Horus vertical slice: an authenticated, persistent mobile-task API with replayable SSE events, pause/resume/cancel commands, risk confirmations, and an Android task screen.

**Architecture:** Import `deepblue21/nova-agent` into the current Project Horus Git history and extend its Express/PostgreSQL gateway with a focused mobile-task domain. Keep persistence, state transitions, event delivery, and HTTP routing in separate modules. Extend the existing Kotlin/Compose client with a separate task feature whose reducer is testable without Android runtime.

**Tech Stack:** Node.js 20.19+, Express 4, PostgreSQL 16, Redis 7, Docker Compose, Node `node:test`, Supertest, Kotlin 2.0.21, Jetpack Compose, OkHttp SSE, JDK 17, Android SDK 35.

## Global Constraints

- Active Codex working tree: `C:\Users\salih\Documents\Project_Horus`.
- Final user-facing clone: `C:\Users\salih\Project_Horus`.
- WSL runtime path after delivery: `/mnt/c/Users/salih/Project_Horus`.
- PC services run in WSL2 Ubuntu; Android Gradle builds may run on Windows.
- Verified PC runtime: Python 3.12.3, Docker 29.1.3, RTX 3070 8192 MiB, Ollama 0.30.8.
- WSL ADB is outside this plan and is installed in the Mobilerun worker plan.
- Android remains `compileSdk=35`, `targetSdk=35`, `minSdk=26`, JVM 17.
- Mobile-task APIs require multi-user mode and `req.principal.userId`; no unauthenticated local fallback is added.
- R2 and R3 actions require explicit confirmation. This plan does not execute device actions.
- Raw chain-of-thought and screenshots are never stored by the control plane.
- The existing `com.nova.agent` package remains unchanged in this phase; product-wide renaming is separate work.
- Each task must leave the repository green and end in its own commit.

---

## File Map

### Gateway

- `gateway/migrations/009_mobile_tasks.sql`: persistent tasks, events, and confirmations.
- `gateway/lib/mobile_task_state.mjs`: pure state/command transition rules.
- `gateway/lib/mobile_task_store.mjs`: user-scoped PostgreSQL operations and transactional event writes.
- `gateway/lib/mobile_event_broker.mjs`: live in-process event fan-out; PostgreSQL remains replay source.
- `gateway/routes/mobile_tasks.mjs`: REST, command, confirmation, and SSE endpoints.
- `gateway/gateway.mjs`: router mount after principal authentication.
- `gateway/test/mobile_task_state.test.mjs`: transition matrix tests.
- `gateway/test/mobile_task_store.test.mjs`: SQL contract tests with an injected fake query/transaction adapter.
- `gateway/test/mobile_event_broker.test.mjs`: subscribe/unsubscribe tests.
- `gateway/test/mobile_tasks_route.test.mjs`: authenticated route tests with Supertest.
- `gateway/test/mobile_control_plane.integration.test.mjs`: fake-Portal lifecycle and event replay test.

### Android

- `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt`: API/UI models.
- `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt`: pure event reducer.
- `nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt`: REST and SSE client.
- `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskViewModel.kt`: task lifecycle and connection ownership.
- `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskScreen.kt`: task composer, status, timeline, controls, confirmation prompt.
- `nova-android/app/src/main/java/com/nova/agent/data/Models.kt`: add `TASKS` app mode.
- `nova-android/app/src/main/java/com/nova/agent/MainActivity.kt`: task mode navigation.
- `nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt`: JSON/SSE parsing.
- `nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt`: deterministic UI-state transitions.
- `nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt`: Compose interaction smoke test.

### Operations

- `scripts/smoke-mobile-control-plane.mjs`: authenticated Docker-stack smoke test.
- `package.json`: `smoke:mobile` command.
- `gateway/.env.example`: SSE heartbeat configuration.
- `PROJECT_HORUS.md`: project boundary and local run commands.

---

### Task 1: Import Nova Agent as the Project Base

**Files:**
- Create: `PROJECT_HORUS.md`
- Preserve: `docs/superpowers/specs/2026-07-10-project-horus-hybrid-mobile-agent-design.md`
- Preserve: `docs/superpowers/plans/2026-07-10-mobile-task-control-plane.md`
- Import: Nova Agent root, `gateway/`, `nova-android/`, `web/`, Compose files, scripts, and deployment files

**Interfaces:**
- Consumes: current Project Horus planning repository and `https://github.com/deepblue21/nova-agent.git` branch `main`.
- Produces: one Git working tree containing the approved design, this plan, and the Nova source baseline.

- [ ] **Step 1: Verify both working trees are clean**

Run:

```powershell
git status --short
git -c safe.directory=C:/tmp/nova-research-nova-agent -C C:\tmp\nova-research-nova-agent status --short
```

Expected: no output from either command. Stop if either tree is dirty.

- [ ] **Step 2: Add and fetch the Nova upstream**

Run:

```powershell
git remote add nova-upstream https://github.com/deepblue21/nova-agent.git
git fetch nova-upstream main
```

Expected: `nova-upstream/main` is created.

- [ ] **Step 3: Merge the source histories without discarding Project Horus docs**

Run:

```powershell
git merge --allow-unrelated-histories --no-commit nova-upstream/main
git status --short
```

Expected: Nova files are staged; both Project Horus documents remain present. Resolve only direct path conflicts and never delete either Project Horus document.

- [ ] **Step 4: Add the Project Horus boundary document**

Create `PROJECT_HORUS.md` with:

````markdown
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
````

- [ ] **Step 5: Verify the imported baseline**

Run in WSL:

```bash
cd /mnt/c/Users/salih/Documents/Project_Horus
npm ci --prefix gateway
npm test --prefix gateway
```

Expected: existing gateway tests pass before Horus feature code begins.

- [ ] **Step 6: Commit the baseline merge**

```powershell
git add -A
git commit -m "chore: import Nova Agent as Project Horus baseline"
```

---

### Task 2: Define the Mobile Task State Machine

**Files:**
- Create: `gateway/lib/mobile_task_state.mjs`
- Create: `gateway/test/mobile_task_state.test.mjs`

**Interfaces:**
- Consumes: command strings `pause`, `resume`, `cancel`, `steer`.
- Produces: `TASK_STATUSES`, `TERMINAL_STATUSES`, `isTerminal(status)`, and `applyTaskCommand(status, command)`.

- [ ] **Step 1: Write the failing transition tests**

Create `gateway/test/mobile_task_state.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { applyTaskCommand, isTerminal } from "../lib/mobile_task_state.mjs";

test("pause and resume preserve a controllable run", () => {
  assert.equal(applyTaskCommand("executing", "pause"), "paused");
  assert.equal(applyTaskCommand("paused", "resume"), "queued");
});

test("cancel ends any non-terminal run", () => {
  assert.equal(applyTaskCommand("queued", "cancel"), "cancelled");
  assert.equal(applyTaskCommand("waiting_for_confirmation", "cancel"), "cancelled");
});

test("steer does not change status", () => {
  assert.equal(applyTaskCommand("paused", "steer"), "paused");
  assert.equal(applyTaskCommand("executing", "steer"), "executing");
});

test("invalid transitions throw", () => {
  assert.throws(() => applyTaskCommand("completed", "resume"), /terminal/);
  assert.throws(() => applyTaskCommand("queued", "resume"), /cannot resume/);
  assert.throws(() => applyTaskCommand("queued", "unknown"), /unknown command/);
});

test("terminal detection is explicit", () => {
  assert.equal(isTerminal("completed"), true);
  assert.equal(isTerminal("failed"), true);
  assert.equal(isTerminal("cancelled"), true);
  assert.equal(isTerminal("paused"), false);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm --prefix gateway test -- test/mobile_task_state.test.mjs`

Expected: FAIL with `ERR_MODULE_NOT_FOUND`.

- [ ] **Step 3: Implement the pure state module**

Create `gateway/lib/mobile_task_state.mjs`:

```js
export const TASK_STATUSES = Object.freeze([
  "queued", "routing", "observing", "planning", "executing", "verifying",
  "waiting_for_confirmation", "waiting_for_device", "waiting_for_compute",
  "paused", "completed", "failed", "cancelled",
]);

export const TERMINAL_STATUSES = new Set(["completed", "failed", "cancelled"]);

export function isTerminal(status) {
  return TERMINAL_STATUSES.has(status);
}

export function applyTaskCommand(status, command) {
  if (!TASK_STATUSES.includes(status)) throw new Error("unknown task status");
  if (isTerminal(status)) throw new Error("terminal task cannot be changed");
  if (command === "cancel") return "cancelled";
  if (command === "pause") {
    if (status === "paused") return status;
    return "paused";
  }
  if (command === "resume") {
    if (status !== "paused") throw new Error("cannot resume a task that is not paused");
    return "queued";
  }
  if (command === "steer") return status;
  throw new Error("unknown command");
}
```

- [ ] **Step 4: Run the focused and full gateway tests**

Run:

```bash
npm --prefix gateway test -- test/mobile_task_state.test.mjs
npm --prefix gateway test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add gateway/lib/mobile_task_state.mjs gateway/test/mobile_task_state.test.mjs
git commit -m "feat: define mobile task state transitions"
```

---

### Task 3: Add Persistent Tasks, Events, and Confirmations

**Files:**
- Create: `gateway/migrations/009_mobile_tasks.sql`
- Create: `gateway/lib/mobile_task_store.mjs`
- Create: `gateway/test/mobile_task_store.test.mjs`

**Interfaces:**
- Consumes: authenticated `userId`, prompt, task commands, confirmation decisions.
- Produces: `createMobileTaskStore({ q, withTx })` with `createTask`, `listTasks`, `getTask`, `listEvents`, `applyCommand`, `requestConfirmation`, and `resolveConfirmation`.

- [ ] **Step 1: Write the migration**

Create `gateway/migrations/009_mobile_tasks.sql`:

```sql
CREATE TABLE IF NOT EXISTS mobile_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  prompt text NOT NULL CHECK (char_length(prompt) BETWEEN 1 AND 4000),
  device_id text,
  status text NOT NULL DEFAULT 'queued' CHECK (status IN (
    'queued','routing','observing','planning','executing','verifying',
    'waiting_for_confirmation','waiting_for_device','waiting_for_compute',
    'paused','completed','failed','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mobile_task_events (
  id bigserial PRIMARY KEY,
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  type text NOT NULL CHECK (char_length(type) BETWEEN 1 AND 100),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mobile_confirmations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  risk_level text NOT NULL CHECK (risk_level IN ('R2','R3')),
  action jsonb NOT NULL,
  resume_status text NOT NULL DEFAULT 'executing',
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  decided_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS mobile_tasks_user_idx ON mobile_tasks(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS mobile_events_task_idx ON mobile_task_events(task_id, id);
CREATE INDEX IF NOT EXISTS mobile_confirmations_task_idx ON mobile_confirmations(task_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS mobile_one_pending_confirmation_idx
  ON mobile_confirmations(task_id) WHERE status = 'pending';
```

- [ ] **Step 2: Write store contract tests with an injected adapter**

Create `gateway/test/mobile_task_store.test.mjs`. Use a fake `q` and `withTx` that record SQL and return scripted rows. Assert these exact properties:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { createMobileTaskStore } from "../lib/mobile_task_store.mjs";

test("createTask inserts an owned task and task.created event in one transaction", async () => {
  const calls = [];
  const client = { query: async (sql, params) => {
    calls.push({ sql, params });
    if (sql.includes("INSERT INTO mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", prompt: "Open Settings", status: "queued" }] };
    if (sql.includes("INSERT INTO mobile_task_events")) return { rows: [{ id: "1", task_id: "task-1", type: "task.created", payload: { status: "queued" } }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });
  const out = await store.createTask("user-1", { prompt: "Open Settings", deviceId: null });
  assert.equal(out.task.id, "task-1");
  assert.equal(out.event.type, "task.created");
  assert.equal(calls.length, 2);
});
```

Add equally explicit tests for user-scoped `getTask`, `listEvents(afterId)`, terminal command rejection, one pending confirmation, approval restoring `resume_status`, and rejection moving the task to `paused`.

- [ ] **Step 3: Run the store test to verify it fails**

Run: `npm --prefix gateway test -- test/mobile_task_store.test.mjs`

Expected: FAIL with `ERR_MODULE_NOT_FOUND`.

- [ ] **Step 4: Implement the store factory**

Create `gateway/lib/mobile_task_store.mjs`. Use `q` and `withTx` from `db.mjs` as defaults. Keep every ownership check in SQL. The exported factory must return these exact signatures:

```js
export function createMobileTaskStore({ q: query = q, withTx: transaction = withTx } = {}) {
  return {
    createTask: (userId, { prompt, deviceId = null }) => createTask(transaction, userId, prompt, deviceId),
    listTasks: (userId, limit = 50) => listTasks(query, userId, limit),
    getTask: (userId, taskId) => getTask(query, userId, taskId),
    listEvents: (userId, taskId, afterId = 0, limit = 500) => listEvents(query, userId, taskId, afterId, limit),
    applyCommand: (userId, taskId, command, note = "") => applyCommand(transaction, userId, taskId, command, note),
    requestConfirmation: (userId, taskId, input) => requestConfirmation(transaction, userId, taskId, input),
    resolveConfirmation: (userId, taskId, confirmationId, decision) => resolveConfirmation(transaction, userId, taskId, confirmationId, decision),
  };
}
```

Use `SELECT ... FOR UPDATE` before command and confirmation transitions. Every mutating method returns `{ task, event, confirmation? }`. Event payloads are JSON objects, event IDs are returned as strings, prompts are capped at 4000 characters, command notes at 1000 characters, and event payload JSON at 32 KiB before database insertion.

`resolveConfirmation` accepts only wire decisions `approve` and `reject`. It stores them as `approved` and `rejected`, emits `confirmation.approved` or `confirmation.rejected`, restores `resume_status` after approval, and moves the task to `paused` after rejection.

- [ ] **Step 5: Run tests and apply the migration against Docker PostgreSQL**

Run:

```bash
npm --prefix gateway test -- test/mobile_task_store.test.mjs
docker compose up -d postgres
docker compose run --rm migrate
docker compose exec -T postgres psql -U nova -d nova -c "\d mobile_tasks"
docker compose exec -T postgres psql -U nova -d nova -c "\d mobile_task_events"
docker compose exec -T postgres psql -U nova -d nova -c "\d mobile_confirmations"
```

Expected: tests pass; all three tables and their indexes exist.

- [ ] **Step 6: Commit**

```bash
git add gateway/migrations/009_mobile_tasks.sql gateway/lib/mobile_task_store.mjs gateway/test/mobile_task_store.test.mjs
git commit -m "feat: persist mobile tasks and events"
```

---

### Task 4: Add Live Event Fan-Out and SSE Formatting

**Files:**
- Create: `gateway/lib/mobile_event_broker.mjs`
- Create: `gateway/test/mobile_event_broker.test.mjs`

**Interfaces:**
- Consumes: `{ id, task_id, type, payload, created_at }` event objects.
- Produces: singleton `mobileEventBroker`, class `MobileEventBroker`, and pure `formatSseEvent(event)`.

- [ ] **Step 1: Write failing broker tests**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { MobileEventBroker, formatSseEvent } from "../lib/mobile_event_broker.mjs";

test("subscribers only receive their task events", () => {
  const broker = new MobileEventBroker();
  const seen = [];
  const unsubscribe = broker.subscribe("task-1", event => seen.push(event.id));
  broker.publish({ id: "1", task_id: "task-2", type: "task.state", payload: {} });
  broker.publish({ id: "2", task_id: "task-1", type: "task.state", payload: {} });
  unsubscribe();
  broker.publish({ id: "3", task_id: "task-1", type: "task.state", payload: {} });
  assert.deepEqual(seen, ["2"]);
});

test("SSE output carries replay id and event type", () => {
  const text = formatSseEvent({ id: "42", task_id: "task-1", type: "confirmation.requested", payload: { risk_level: "R2" } });
  assert.match(text, /^id: 42\nevent: confirmation\.requested\ndata: /);
  assert.match(text, /"risk_level":"R2"/);
  assert.ok(text.endsWith("\n\n"));
});
```

- [ ] **Step 2: Run the test and verify failure**

Run: `npm --prefix gateway test -- test/mobile_event_broker.test.mjs`

Expected: module-not-found failure.

- [ ] **Step 3: Implement the broker**

```js
import { EventEmitter } from "node:events";

export class MobileEventBroker {
  #events = new EventEmitter();
  constructor() { this.#events.setMaxListeners(1000); }
  publish(event) { this.#events.emit(String(event.task_id), event); }
  subscribe(taskId, listener) {
    const key = String(taskId);
    this.#events.on(key, listener);
    return () => this.#events.off(key, listener);
  }
}

export function formatSseEvent(event) {
  return `id: ${event.id}\nevent: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`;
}

export const mobileEventBroker = new MobileEventBroker();
```

- [ ] **Step 4: Run focused and full gateway tests**

Run: `npm --prefix gateway test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add gateway/lib/mobile_event_broker.mjs gateway/test/mobile_event_broker.test.mjs
git commit -m "feat: stream live mobile task events"
```

---

### Task 5: Expose Authenticated Mobile Task Routes

**Files:**
- Create: `gateway/routes/mobile_tasks.mjs`
- Create: `gateway/test/mobile_tasks_route.test.mjs`
- Modify: `gateway/gateway.mjs`
- Modify: `gateway/package.json`
- Modify: `gateway/package-lock.json`

**Interfaces:**
- Consumes: `req.principal.userId`, `mobileTaskStore`, and `mobileEventBroker`.
- Produces: `createMobileTasksRouter({ store, broker, heartbeatMs })` and `/v1/mobile/tasks` endpoints.

- [ ] **Step 1: Add the route-test dependency**

Run: `npm --prefix gateway install --save-dev supertest@7.1.4`

Expected: `supertest` appears under `devDependencies` and the lockfile changes.

- [ ] **Step 2: Write failing route tests**

Create an Express app in `gateway/test/mobile_tasks_route.test.mjs`, inject `req.principal = { userId: "user-1" }`, and inject a fake store. Cover these exact cases:

```js
test("POST /v1/mobile/tasks validates and creates a task", async () => {
  const res = await request(app).post("/v1/mobile/tasks").send({ prompt: "Open Settings" });
  assert.equal(res.status, 201);
  assert.equal(res.body.id, "task-1");
});

test("task IDs are validated", async () => {
  const res = await request(app).get("/v1/mobile/tasks/not-a-uuid");
  assert.equal(res.status, 400);
  assert.equal(res.body.error, "invalid id");
});

test("commands publish the persisted event", async () => {
  const res = await request(app)
    .post("/v1/mobile/tasks/11111111-1111-4111-8111-111111111111/commands")
    .send({ command: "pause" });
  assert.equal(res.status, 200);
  assert.deepEqual(published, ["task.state"]);
});

test("confirmation decision only accepts approve or reject", async () => {
  const res = await request(app)
    .post("/v1/mobile/tasks/11111111-1111-4111-8111-111111111111/confirmations/22222222-2222-4222-8222-222222222222")
    .send({ decision: "maybe" });
  assert.equal(res.status, 400);
});
```

Also test prompt length, missing ownership returning 404, `Last-Event-ID` parsing through an exported pure helper, and `formatSseEvent` replay order.

- [ ] **Step 3: Implement the router**

The router must expose:

```text
GET    /v1/mobile/tasks
POST   /v1/mobile/tasks
GET    /v1/mobile/tasks/:id
GET    /v1/mobile/tasks/:id/events
POST   /v1/mobile/tasks/:id/commands
POST   /v1/mobile/tasks/:id/confirmations/:confirmationId
```

Implementation rules:

- `POST` trims prompt and rejects empty or over-4000 text.
- UUID path parameters use the same UUID regex as `routes/agent_runs.mjs`.
- List limit is clamped to `1..100`.
- `GET events` verifies task ownership before setting SSE headers.
- It replays `store.listEvents(userId, taskId, afterId)` before subscribing.
- It emits `: heartbeat\n\n` every `MOBILE_SSE_HEARTBEAT_MS`, default 15000 ms.
- Close handling clears the interval and unsubscribes exactly once.
- Every mutation publishes only the event already persisted by the store.
- Store transition errors return 409; validation errors return 400; absent owned rows return 404.

Mount the default router inside the existing `if (MULTI_USER)` block:

```js
import { mobileTasks } from "./routes/mobile_tasks.mjs";
// ...
app.use(mobileTasks);
```

- [ ] **Step 4: Run route and full gateway tests**

Run:

```bash
npm --prefix gateway test -- test/mobile_tasks_route.test.mjs
npm --prefix gateway test
```

Expected: all tests pass and no test process remains open after SSE cleanup tests.

- [ ] **Step 5: Commit**

```bash
git add gateway/routes/mobile_tasks.mjs gateway/test/mobile_tasks_route.test.mjs gateway/gateway.mjs gateway/package.json gateway/package-lock.json
git commit -m "feat: expose authenticated mobile task API"
```

---

### Task 6: Prove Replay and Confirmation with a Fake Portal

**Files:**
- Create: `gateway/test/helpers/memory_mobile_task_store.mjs`
- Create: `gateway/test/mobile_control_plane.integration.test.mjs`

**Interfaces:**
- Consumes: the real router and broker with an in-memory store implementing the production store interface.
- Produces: an end-to-end test that acts as the fake Portal until Mobilerun is integrated.

- [ ] **Step 1: Implement the test-only memory store**

The helper must implement every production store method and preserve monotonically increasing string event IDs. `requestConfirmation` must move a task to `waiting_for_confirmation`; `resolveConfirmation(..., "approve")` must restore `executing`; rejection must move it to `paused`.

- [ ] **Step 2: Write the lifecycle integration test**

The test must perform this sequence:

1. Create `Open Settings and prepare Wi-Fi change` through HTTP.
2. Subscribe to the task broker.
3. Simulate Fake Portal by calling `store.requestConfirmation` with `R2` and `{ type: "change_setting", target: "wifi" }`.
4. Publish the persisted `confirmation.requested` event.
5. Approve through the public HTTP confirmation endpoint.
6. Disconnect, then call `listEvents` with the first event ID.
7. Assert replay contains `confirmation.requested` followed by `confirmation.approved` with larger numeric IDs.
8. Cancel through the public command endpoint and assert terminal state `cancelled`.

- [ ] **Step 3: Run focused and full tests**

Run:

```bash
npm --prefix gateway test -- test/mobile_control_plane.integration.test.mjs
npm --prefix gateway test
```

Expected: all assertions pass.

- [ ] **Step 4: Commit**

```bash
git add gateway/test/helpers/memory_mobile_task_store.mjs gateway/test/mobile_control_plane.integration.test.mjs
git commit -m "test: cover mobile task replay and confirmation"
```

---

### Task 7: Add Android Mobile Task Models and Network Client

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt`
- Create: `nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt`
- Create: `nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt`

**Interfaces:**
- Consumes: gateway base URL ending in `/v1`, bearer token, REST JSON, and SSE events.
- Produces: `MobileTask`, `MobileTaskEvent`, `MobileConfirmation`, and `MobileTaskClient` callbacks.

- [ ] **Step 1: Define Android task models**

Use exact enums and fields:

```kotlin
enum class MobileTaskStatus {
    QUEUED, ROUTING, OBSERVING, PLANNING, EXECUTING, VERIFYING,
    WAITING_FOR_CONFIRMATION, WAITING_FOR_DEVICE, WAITING_FOR_COMPUTE,
    PAUSED, COMPLETED, FAILED, CANCELLED;

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: FAILED
    }
}

data class MobileTask(val id: String, val prompt: String, val status: MobileTaskStatus)
data class MobileConfirmation(val id: String, val riskLevel: String, val actionSummary: String)
data class MobileTaskEvent(val id: String, val taskId: String, val type: String, val summary: String, val confirmation: MobileConfirmation? = null)
```

- [ ] **Step 2: Write failing parser tests**

Cover task JSON, `task.state`, `confirmation.requested`, malformed JSON, `[DONE]`, and large numeric event IDs kept as strings.

- [ ] **Step 3: Implement `MobileTaskClient`**

Required methods:

```kotlin
fun createTask(baseUrl: String, token: String, prompt: String, callback: (Result<MobileTask>) -> Unit)
fun getTask(baseUrl: String, token: String, taskId: String, callback: (Result<MobileTask>) -> Unit)
fun command(baseUrl: String, token: String, taskId: String, command: String, note: String = "", callback: (Result<MobileTask>) -> Unit)
fun resolveConfirmation(baseUrl: String, token: String, taskId: String, confirmationId: String, decision: String, callback: (Result<MobileTask>) -> Unit)
fun streamEvents(baseUrl: String, token: String, taskId: String, lastEventId: String?, callbacks: EventCallbacks): EventSource
```

Define the callback contract in `MobileTaskClient`:

```kotlin
interface EventCallbacks {
    fun onEvent(event: MobileTaskEvent)
    fun onClosed()
    fun onError(message: String, recoverable: Boolean)
}
```

Use the same OkHttp timeouts and bearer behavior as `NovaClient`. REST callbacks must close response bodies. SSE `onEvent` must parse the full event object and expose `id` from the SSE frame when JSON omits it. HTTP 401, 404, 409, and 429 must map to Turkish user-facing messages without exposing response internals.

- [ ] **Step 4: Run Android unit tests**

Run on Windows:

```powershell
cd nova-android
.\gradlew.bat testDebugUnitTest
```

Expected: existing and new parser tests pass.

- [ ] **Step 5: Commit**

```bash
git add nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt nova-android/app/src/main/java/com/nova/agent/net/MobileTaskClient.kt nova-android/app/src/test/java/com/nova/agent/MobileTaskClientTest.kt
git commit -m "feat: add Android mobile task client"
```

---

### Task 8: Add a Reducer and Task ViewModel

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt`
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskViewModel.kt`
- Create: `nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt`

**Interfaces:**
- Consumes: `MobileTask`, `MobileTaskEvent`, and existing `SettingsStore` connection values.
- Produces: immutable `MobileTaskUiState` and UI actions `createTask`, `pause`, `resume`, `cancel`, `approve`, `reject`, `clearError`.

- [ ] **Step 1: Write failing reducer tests**

Test initial state, task creation, ordered event de-duplication by ID, pending confirmation display, approval clearing confirmation, cancellation, and connection error retention.

- [ ] **Step 2: Implement the immutable reducer**

```kotlin
data class MobileTaskUiState(
    val prompt: String = "",
    val task: MobileTask? = null,
    val events: List<MobileTaskEvent> = emptyList(),
    val pendingConfirmation: MobileConfirmation? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

sealed interface MobileTaskMutation {
    data class PromptChanged(val value: String) : MobileTaskMutation
    data class TaskLoaded(val task: MobileTask) : MobileTaskMutation
    data class EventReceived(val event: MobileTaskEvent) : MobileTaskMutation
    data class Failed(val message: String) : MobileTaskMutation
    data object Loading : MobileTaskMutation
    data object ErrorCleared : MobileTaskMutation
}

fun reduceMobileTask(state: MobileTaskUiState, mutation: MobileTaskMutation): MobileTaskUiState
```

Events are sorted by numeric `BigInteger(id)` and duplicate IDs replace nothing. `confirmation.requested` sets `pendingConfirmation`; `confirmation.approved` and `confirmation.rejected` clear it.

- [ ] **Step 3: Implement `MobileTaskViewModel`**

Use `AndroidViewModel`, `SettingsStore`, `MobileTaskClient`, and main-thread state updates. Own exactly one `EventSource`; cancel it before switching tasks and in `onCleared`. Reconnect with the last event ID after a recoverable SSE failure using delays 1 s, 2 s, 4 s, capped at 10 s. Do not reconnect terminal tasks.

- [ ] **Step 4: Run Android tests**

Run: `.\gradlew.bat testDebugUnitTest` from `nova-android`.

Expected: reducer and parser tests pass.

- [ ] **Step 5: Commit**

```bash
git add nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskViewModel.kt nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt
git commit -m "feat: manage Android mobile task lifecycle"
```

---

### Task 9: Add the Android Task Screen and Navigation

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskScreen.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/data/Models.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/MainActivity.kt`
- Modify: `nova-android/app/build.gradle.kts`
- Create: `nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt`

**Interfaces:**
- Consumes: `MobileTaskUiState` and callback lambdas; it does not call the network directly.
- Produces: accessible task composer, timeline, pause/resume/cancel controls, and R2/R3 confirmation UI.

- [ ] **Step 1: Add Compose test dependencies and instrumentation runner**

Add to `defaultConfig`:

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

Add dependencies:

```kotlin
androidTestImplementation(composeBom)
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **Step 2: Write the failing Compose UI test**

Render `MobileTaskScreen` with a pending R2 confirmation. Assert nodes tagged `task_prompt`, `task_submit`, `task_timeline`, `confirmation_panel`, `confirmation_approve`, and `confirmation_reject` exist. Click approve and assert the callback receives `approve`.

- [ ] **Step 3: Implement `MobileTaskScreen`**

The composable signature is:

```kotlin
@Composable
fun MobileTaskScreen(
    state: MobileTaskUiState,
    onPromptChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onCommand: (String) -> Unit,
    onDecision: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout requirements:

- Stable full-width column, no nested cards.
- Prompt input and send icon remain fixed height while empty/loading.
- Timeline uses `LazyColumn` and keyed event IDs.
- Controls use familiar play/pause/stop icons with content descriptions.
- Pending confirmation is a bottom band showing risk level and action summary, with explicit Reject and Approve commands.
- Terminal tasks hide pause/resume and keep the timeline visible.
- Long text wraps and never overlays controls.

- [ ] **Step 4: Add task mode to the existing app**

Change `Mode` to:

```kotlin
enum class Mode { VOICE, CHAT, TASKS }
```

Instantiate `MobileTaskViewModel` beside `NovaViewModel` in `MainActivity`. In the main content switch, render `MobileTaskScreen` for `Mode.TASKS`. Add a third dock tab named `Görevler` with a task/play icon. Preserve voice and chat behavior.

- [ ] **Step 5: Run unit, lint, instrumentation, and assemble checks**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected: unit/lint/build pass; instrumentation passes when an emulator or device is available. If no target is connected, record instrumentation as the only unavailable check and do not claim it passed.

- [ ] **Step 6: Commit**

```bash
git add nova-android/app
git commit -m "feat: add Android mobile task workspace"
```

---

### Task 10: Docker Smoke Test, Documentation, and Delivery Clone

**Files:**
- Create: `scripts/smoke-mobile-control-plane.mjs`
- Modify: `package.json`
- Modify: `gateway/.env.example`
- Modify: `PROJECT_HORUS.md`

**Interfaces:**
- Consumes: `HORUS_BASE_URL`, `HORUS_API_KEY`, Docker PostgreSQL, Redis, migrate, and gateway services.
- Produces: one-command authenticated API smoke and final clone at `C:\Users\salih\Project_Horus`.

- [ ] **Step 1: Write the smoke script**

The script must:

1. Require `HORUS_API_KEY` and default `HORUS_BASE_URL` to `http://localhost/v1`.
2. Create a task.
3. Fetch it and assert `queued`.
4. Pause and assert `paused`.
5. Resume and assert `queued`.
6. Cancel and assert `cancelled`.
7. Fetch events with `Last-Event-ID` from the first event using an `AbortController` timeout and assert strictly increasing IDs.
8. Print `mobile control plane smoke: ok` and exit 0.

Use only Node built-in `fetch` and `assert`; never print the API key.

- [ ] **Step 2: Add scripts and environment documentation**

Add to root `package.json`:

```json
"smoke:mobile": "node scripts/smoke-mobile-control-plane.mjs"
```

Add to `gateway/.env.example`:

```env
MOBILE_SSE_HEARTBEAT_MS=15000
```

Document these WSL commands in `PROJECT_HORUS.md`:

```bash
docker compose up -d --build postgres redis migrate gateway caddy
docker compose exec gateway node scripts/bootstrap-user.mjs horus@local.test 5
HORUS_API_KEY='nova_key_returned_once' npm run smoke:mobile
```

- [ ] **Step 3: Run the full verification matrix**

Run in WSL:

```bash
npm --prefix gateway test
npm run build
docker compose config
docker compose up -d --build postgres redis migrate gateway caddy
docker compose ps
```

Create an API key with the documented bootstrap command, export it only in the current shell, then run `npm run smoke:mobile`.

Run on Windows:

```powershell
cd nova-android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: gateway suite, web build, Compose validation, healthy Docker services, mobile API smoke, Android unit tests, lint, and APK assembly all pass.

- [ ] **Step 4: Commit operations and docs**

```bash
git add scripts/smoke-mobile-control-plane.mjs package.json gateway/.env.example PROJECT_HORUS.md
git commit -m "chore: add Horus mobile control plane smoke"
```

- [ ] **Step 5: Create the requested delivery clone**

First verify the target does not already contain user work:

```powershell
Test-Path C:\Users\salih\Project_Horus
```

Expected: `False`. If `True`, inspect it and stop rather than overwriting it.

Create the clone:

```powershell
git clone C:\Users\salih\Documents\Project_Horus C:\Users\salih\Project_Horus
```

Verify from WSL:

```bash
cd /mnt/c/Users/salih/Project_Horus
git status --short
git log -5 --oneline
npm --prefix gateway test
```

Expected: clean status, the feature commits are present, and gateway tests pass from the requested path.

- [ ] **Step 6: Final checkpoint**

Record:

- Gateway test count and result.
- Android unit/lint/assemble result.
- Docker service health.
- Smoke-test result.
- APK path.
- Any unavailable emulator-only check.

Do not begin Mobilerun worker integration until this checkpoint is green.
