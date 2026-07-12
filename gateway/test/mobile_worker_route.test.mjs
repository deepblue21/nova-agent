import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { readFileSync } from "node:fs";
import express from "express";
import request from "supertest";
import { createMobileWorkerRouter } from "../routes/mobile_worker.mjs";
import { MobileWorkerStoreError } from "../lib/mobile_worker_store.mjs";

const TASK_ID = "11111111-1111-4111-8111-111111111111";
const LEASE_ID = "22222222-2222-4222-8222-222222222222";
const REPORT_ID = "33333333-3333-4333-8333-333333333333";
const LEASE_TOKEN = crypto.randomBytes(32).toString("hex");
const WORKER_TOKEN = crypto.randomBytes(32).toString("hex");

function task(overrides = {}) {
  return { id: TASK_ID, status: "executing", device_id: "emulator-5554", ...overrides };
}

function lease(overrides = {}) {
  return { id: LEASE_ID, task_id: TASK_ID, state: "active", expires_at: "2026-07-12T12:00:00.000Z", ...overrides };
}

function event(overrides = {}) {
  return { id: "1", task_id: TASK_ID, type: "worker.running", payload: { status: "executing" }, ...overrides };
}

function storeError(code) {
  return new MobileWorkerStoreError(code);
}

function createStore(overrides = {}) {
  return {
    claimNext: async () => ({ task: task(), lease: { ...lease(), token: LEASE_TOKEN }, events: [event({ type: "worker.claimed" }), event({ type: "worker.executing" })] }),
    getActiveStatus: async () => ({ task: task(), lease: lease() }),
    report: async () => ({ task: task({ status: "completed" }), event: event({ type: "worker.completed", payload: { status: "completed" } }), replayed: false }),
    expireLeases: async () => [{ task: task({ status: "waiting_for_device" }), event: event({ type: "worker.lease_expired", payload: { status: "waiting_for_device" } }) }],
    ...overrides,
  };
}

function createBroker() {
  return { published: [], publish(savedEvent) { this.published.push(savedEvent); } };
}

function createApp(store = createStore(), broker = createBroker(), options = {}) {
  const app = express();
  app.use(express.json());
  app.use(createMobileWorkerRouter({ store, broker, enabled: true, token: WORKER_TOKEN, ...options }));
  app.get("/health", (_req, res) => res.json({ ok: true }));
  app.get("/v1/user-route", (_req, res) => res.json({ ok: true }));
  app.use((_req, res) => res.status(404).end());
  app.use((error, _req, res, _next) => {
    options.onGlobalError?.(error);
    res.status(500).json({ error: "gateway error" });
  });
  return app;
}

function workerRequest(app, method, path) {
  return request(app)[method](path).set("Authorization", `Bearer ${WORKER_TOKEN}`);
}

test("worker authentication applies only to the worker path", async () => {
  const disabled = createApp(createStore(), createBroker(), { enabled: false });
  const enabled = createApp(createStore(), createBroker(), { enabled: true });

  for (const app of [disabled, enabled]) {
    assert.deepEqual((await request(app).get("/health")).body, { ok: true });
    assert.deepEqual((await request(app).get("/v1/user-route")).body, { ok: true });
  }

  const hidden = await request(disabled).post("/v1/internal/mobile-worker/claims").send({ device_id: "emulator-5554" });
  const denied = await request(enabled).post("/v1/internal/mobile-worker/claims").send({ device_id: "emulator-5554" });

  assert.equal(hidden.status, 404);
  assert.equal(denied.status, 401);
});

test("unexpected worker store failures are handled locally without leaking details", async () => {
  const rawMessage = "unexpected private store failure";
  const calls = [];
  const failures = [
    ["claim", createStore({ claimNext: async () => { throw new Error(rawMessage); } }), "post", "/v1/internal/mobile-worker/claims", { device_id: "emulator-5554" }],
    ["status", createStore({ getActiveStatus: async () => { throw new Error(rawMessage); } }), "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`, undefined],
    ["report", createStore({ report: async () => { throw new Error(rawMessage); } }), "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`, { lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" }],
    ["expiry", createStore({ expireLeases: async () => { throw new Error(rawMessage); } }), "post", "/v1/internal/mobile-worker/leases/expire", {}],
  ];

  for (const [name, store, method, path, body] of failures) {
    let globalErrors = 0;
    const app = createApp(store, createBroker(), { onGlobalError: () => { globalErrors += 1; } });
    let response = workerRequest(app, method, path);
    if (name === "status" || name === "report") response = response.set("X-Horus-Lease-Token", LEASE_TOKEN);
    if (body !== undefined) response = response.send(body);
    const result = await response;

    calls.push(globalErrors);
    assert.equal(result.status, 500);
    assert.deepEqual(result.body, { error: "mobile worker request failed" });
    assert.equal(JSON.stringify(result.body).includes(rawMessage), false);
  }

  assert.deepEqual(calls, [0, 0, 0, 0]);
});

test("claim requires a dedicated worker token and publishes only persisted events", async () => {
  const broker = createBroker();
  const app = createApp(createStore(), broker);
  const denied = await request(app).post("/v1/internal/mobile-worker/claims").set("Authorization", `Bearer ${crypto.randomBytes(32).toString("hex")}`).send({ device_id: "emulator-5554" });
  const claimed = await workerRequest(app, "post", "/v1/internal/mobile-worker/claims").send({ device_id: "emulator-5554" });

  assert.equal(denied.status, 401);
  assert.equal(claimed.status, 201);
  assert.deepEqual(broker.published.map(savedEvent => savedEvent.type), ["worker.claimed", "worker.executing"]);
  assert.equal(JSON.stringify(claimed.body).includes(LEASE_TOKEN), true);
});

test("worker routes validate IDs, lease header, store input, and map lease/task outcomes", async () => {
  const calls = [];
  const store = createStore({
    getActiveStatus: async input => { calls.push(["status", input]); throw storeError("LEASE_NOT_ACTIVE"); },
    report: async input => { calls.push(["report", input]); throw storeError("LEASE_NOT_ACTIVE"); },
  });
  const app = createApp(store);
  const invalidId = await workerRequest(app, "get", "/v1/internal/mobile-worker/tasks/not-a-uuid/status").set("X-Horus-Lease-Token", LEASE_TOKEN);
  const missingToken = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`);
  const staleStatus = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`).set("X-Horus-Lease-Token", LEASE_TOKEN);
  const stale = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" });

  assert.equal(invalidId.status, 400);
  assert.equal(missingToken.status, 409);
  assert.equal(staleStatus.status, 409);
  assert.equal(stale.status, 409);
  assert.deepEqual(calls[0], ["status", { taskId: TASK_ID, token: LEASE_TOKEN }]);
  assert.deepEqual(calls[1], ["report", { taskId: TASK_ID, token: LEASE_TOKEN, lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" }]);
});

test("report is idempotent, returns no internal data, and publishes a persisted event once", async () => {
  const broker = createBroker();
  let calls = 0;
  const stored = event({ type: "worker.completed", payload: { status: "completed" } });
  const app = createApp(createStore({
    report: async () => ({ task: task({ status: "completed" }), event: stored, replayed: calls++ > 0 }),
  }), broker);
  const send = () => workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`)
    .set("X-Horus-Lease-Token", LEASE_TOKEN)
    .send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed", summary: "Android 17", steps: 3 });
  const first = await send();
  const retry = await send();

  assert.equal(first.status, 200);
  assert.deepEqual(first.body, { id: TASK_ID, status: "completed" });
  assert.equal(retry.status, 200);
  assert.deepEqual(broker.published, [stored]);
});

test("status and expiry routes return only persisted safe data and events", async () => {
  const broker = createBroker();
  const app = createApp(createStore(), broker);
  const status = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`).set("X-Horus-Lease-Token", LEASE_TOKEN);
  const expired = await workerRequest(app, "post", "/v1/internal/mobile-worker/leases/expire").send({});

  assert.equal(status.status, 200);
  assert.deepEqual(status.body, { status: "executing", lease_expires_at: "2026-07-12T12:00:00.000Z" });
  assert.equal(JSON.stringify(status.body).includes(LEASE_TOKEN), false);
  assert.equal(expired.status, 200);
  assert.deepEqual(expired.body, { data: [{ id: TASK_ID, status: "waiting_for_device" }] });
  assert.deepEqual(broker.published.map(savedEvent => savedEvent.type), ["worker.lease_expired"]);
});

test("unknown tasks are 404, invalid payloads are 400, and disabled worker routes are hidden", async () => {
  const app = createApp(createStore({
    getActiveStatus: async () => { throw storeError("TASK_NOT_FOUND"); },
    report: async () => { throw storeError("TASK_NOT_FOUND"); },
  }));
  const unknown = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`).set("X-Horus-Lease-Token", LEASE_TOKEN);
  const unknownReport = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" });
  const invalid = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "invalid" });
  const disabled = await request(createApp(createStore(), createBroker(), { enabled: false })).post("/v1/internal/mobile-worker/claims").set("Authorization", `Bearer ${WORKER_TOKEN}`).send({ device_id: "emulator-5554" });

  assert.equal(unknown.status, 404);
  assert.equal(unknownReport.status, 404);
  assert.equal(invalid.status, 400);
  assert.equal(disabled.status, 404);
});

test("gateway constructs mobile routers after loading .env and before principal auth", () => {
  const source = readFileSync(new URL("../gateway.mjs", import.meta.url), "utf8");
  const envLoaded = source.indexOf("})();", source.indexOf("zero-dependency .env loader"));
  const workerConstructed = source.indexOf("createMobileWorkerRouter(");
  const tasksConstructed = source.indexOf("createMobileTasksRouter(");
  const workerMounted = source.indexOf("app.use(mobileWorker);");
  const principalAuth = source.indexOf("const principalMiddleware = principal();");

  assert.doesNotMatch(source, /import \{ mobileWorker \} from "\.\/routes\/mobile_worker\.mjs"/);
  assert.doesNotMatch(source, /import \{ mobileTasks \} from "\.\/routes\/mobile_tasks\.mjs"/);
  assert.ok(envLoaded < workerConstructed);
  assert.ok(envLoaded < tasksConstructed);
  assert.ok(workerMounted < principalAuth);
});
