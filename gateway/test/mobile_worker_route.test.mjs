import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import request from "supertest";
import { createMobileWorkerRouter } from "../routes/mobile_worker.mjs";

const TASK_ID = "11111111-1111-4111-8111-111111111111";
const LEASE_ID = "22222222-2222-4222-8222-222222222222";
const REPORT_ID = "33333333-3333-4333-8333-333333333333";
const LEASE_TOKEN = "lease-token";

function task(overrides = {}) {
  return { id: TASK_ID, status: "executing", device_id: "emulator-5554", ...overrides };
}

function lease(overrides = {}) {
  return { id: LEASE_ID, task_id: TASK_ID, state: "active", expires_at: "2026-07-12T12:00:00.000Z", ...overrides };
}

function event(overrides = {}) {
  return { id: "1", task_id: TASK_ID, type: "worker.running", payload: { status: "executing" }, ...overrides };
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
  app.use(createMobileWorkerRouter({ store, broker, enabled: true, token: "worker-secret", ...options }));
  app.use((_req, res) => res.status(404).end());
  app.use((_error, _req, res, _next) => res.status(500).json({ error: "gateway error" }));
  return app;
}

function workerRequest(app, method, path) {
  return request(app)[method](path).set("Authorization", "Bearer worker-secret");
}

test("claim requires a dedicated worker token and publishes only persisted events", async () => {
  const broker = createBroker();
  const app = createApp(createStore(), broker);
  const denied = await request(app).post("/v1/internal/mobile-worker/claims").set("Authorization", "Bearer user-api-key").send({ device_id: "emulator-5554" });
  const claimed = await workerRequest(app, "post", "/v1/internal/mobile-worker/claims").send({ device_id: "emulator-5554" });

  assert.equal(denied.status, 401);
  assert.equal(claimed.status, 201);
  assert.deepEqual(broker.published.map(savedEvent => savedEvent.type), ["worker.claimed", "worker.executing"]);
  assert.equal(JSON.stringify(claimed.body).includes(LEASE_TOKEN), true);
});

test("worker routes validate IDs, lease header, store input, and map lease/task outcomes", async () => {
  const calls = [];
  const store = createStore({
    getActiveStatus: async input => { calls.push(["status", input]); return null; },
    report: async input => { calls.push(["report", input]); return null; },
  });
  const app = createApp(store);
  const invalidId = await workerRequest(app, "get", "/v1/internal/mobile-worker/tasks/not-a-uuid/status").set("X-Horus-Lease-Token", LEASE_TOKEN);
  const missingToken = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`);
  const stale = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" });

  assert.equal(invalidId.status, 400);
  assert.equal(missingToken.status, 409);
  assert.equal(stale.status, 409);
  assert.deepEqual(calls[0], ["report", { taskId: TASK_ID, token: LEASE_TOKEN, lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" }]);
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
  assert.equal(expired.status, 200);
  assert.deepEqual(expired.body, { data: [{ id: TASK_ID, status: "waiting_for_device" }] });
  assert.deepEqual(broker.published.map(savedEvent => savedEvent.type), ["worker.lease_expired"]);
});

test("unknown tasks are 404, invalid payloads are 400, and disabled worker routes are hidden", async () => {
  const app = createApp(createStore({
    getActiveStatus: async () => null,
    report: async () => { throw new Error("task not found"); },
  }));
  const unknown = await workerRequest(app, "get", `/v1/internal/mobile-worker/tasks/${TASK_ID}/status`).set("X-Horus-Lease-Token", LEASE_TOKEN);
  const unknownReport = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "completed" });
  const invalid = await workerRequest(app, "post", `/v1/internal/mobile-worker/tasks/${TASK_ID}/reports`).set("X-Horus-Lease-Token", LEASE_TOKEN).send({ lease_id: LEASE_ID, report_id: REPORT_ID, phase: "invalid" });
  const disabled = await request(createApp(createStore(), createBroker(), { enabled: false })).post("/v1/internal/mobile-worker/claims").set("Authorization", "Bearer worker-secret").send({ device_id: "emulator-5554" });

  assert.equal(unknown.status, 404);
  assert.equal(unknownReport.status, 404);
  assert.equal(invalid.status, 400);
  assert.equal(disabled.status, 404);
});
