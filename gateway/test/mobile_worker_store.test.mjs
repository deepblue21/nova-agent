import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { createMobileWorkerStore } from "../lib/mobile_worker_store.mjs";

const TASK_ID = "11111111-1111-4111-8111-111111111111";
const LEASE_ID = "22222222-2222-4222-8222-222222222222";
const REPORT_ID = "33333333-3333-4333-8333-333333333333";
const LEASE_TOKEN = crypto.randomBytes(32).toString("hex");

function reportInput(overrides = {}) {
  return {
    taskId: TASK_ID,
    lease_id: LEASE_ID,
    report_id: REPORT_ID,
    token: LEASE_TOKEN,
    phase: "completed",
    ...overrides,
  };
}

function createClient({ taskStatus = "queued", leaseExpired = false } = {}) {
  const state = {
    task: { id: TASK_ID, prompt: "open settings and tell me the android version", status: taskStatus, device_id: null },
    lease: { id: LEASE_ID, task_id: TASK_ID, token_hash: "", state: "active", expires_at: leaseExpired ? new Date("2026-07-11T12:00:00Z") : new Date("2026-07-11T12:05:00Z") },
    reports: new Map(),
    events: [],
    calls: [],
  };
  let nextEventId = 1;
  const client = { query: async (sql, params = []) => {
    state.calls.push({ sql, params });
    if (sql.includes("FROM mobile_tasks t") && sql.includes("SKIP LOCKED") && sql.includes("t.status='queued'")) {
      return { rows: state.task.status === "queued" ? [{ ...state.task }] : [] };
    }
    if (sql.includes("INSERT INTO mobile_worker_leases")) {
      state.lease = { ...state.lease, id: LEASE_ID, task_id: params[0], device_id: params[1], token_hash: params[2], expires_at: params[3], state: "active" };
      return { rows: [{ ...state.lease }] };
    }
    if (sql.includes("UPDATE mobile_tasks")) {
      state.task = sql.includes("status='executing'")
        ? { ...state.task, status: "executing", device_id: params[0] }
        : sql.includes("status='waiting_for_device'")
          ? { ...state.task, status: "waiting_for_device" }
          : { ...state.task, status: params[0] };
      return { rows: [{ ...state.task }] };
    }
    if (sql.includes("INSERT INTO mobile_task_events")) {
      const event = { id: nextEventId++, task_id: params[0], type: params[1], payload: params[2] };
      state.events.push(event);
      return { rows: [event] };
    }
    if (sql.includes("FROM mobile_worker_leases l") && sql.includes("FOR UPDATE")) {
      return { rows: [{ ...state.lease, task_status: state.task.status }] };
    }
    if (sql.includes("FROM mobile_worker_reports") && sql.includes("report_id")) {
      const report = state.reports.get(params[1]);
      return { rows: report ? [report] : [] };
    }
    if (sql.includes("FROM mobile_task_events WHERE id=$1")) {
      return { rows: state.events.filter(event => event.id === params[0]) };
    }
    if (sql.includes("INSERT INTO mobile_worker_reports")) {
      const report = { lease_id: params[0], report_id: params[1], event_id: params[3], task_status: params[4] };
      state.reports.set(params[1], report);
      return { rows: [report] };
    }
    if (sql.includes("UPDATE mobile_worker_leases") && sql.includes("state='closed'")) {
      state.lease.state = "closed";
      return { rows: [] };
    }
    if (sql.includes("UPDATE mobile_worker_leases") && sql.includes("state='expired'")) {
      state.lease.state = "expired";
      return { rows: [{ ...state.lease, task_status: state.task.status }] };
    }
    if (sql.includes("WHERE l.state='active' AND l.expires_at <= $1")) {
      return { rows: leaseExpired ? [{ ...state.lease, task_status: state.task.status }] : [] };
    }
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  return { client, state };
}

test("claimNext locks the oldest supported queued task and records lease events", async () => {
  const { client, state } = createClient();
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z"), randomBytes: () => Buffer.alloc(32, 7) });

  const out = await store.claimNext({ deviceId: "emulator-5554", policy: "settings_android_version" });

  assert.equal(out.task.status, "executing");
  assert.equal(out.lease.device_id, "emulator-5554");
  assert.deepEqual(out.events.map(event => event.type), ["worker.claimed", "worker.executing"]);
  assert.match(state.calls[0].sql, /FOR UPDATE SKIP LOCKED/);
  assert.match(state.calls[0].sql, /lower\(regexp_replace\(trim\(t\.prompt\), '\\s\+', ' ', 'g'\)\)/);
  assert.deepEqual(state.calls[0].params, [["open settings and tell me the android version", "ayarlari ac ve android surumunu soyle"]]);
  assert.notEqual(out.lease.token, undefined);
  assert.notEqual(state.lease.token_hash, out.lease.token);
});

test("a report retry with the same lease and report ID returns the saved event once", async () => {
  const { client, state } = createClient({ taskStatus: "executing" });
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z") });
  const tokenHash = (await import("../lib/keys.mjs")).sha256hex(LEASE_TOKEN);
  state.lease.token_hash = tokenHash;

  const first = await store.report(reportInput());
  const retry = await store.report(reportInput());

  assert.equal(first.replayed, false);
  assert.equal(retry.replayed, true);
  assert.equal(state.events.filter(event => event.type === "worker.completed").length, 1);
  assert.equal(retry.event.id, first.event.id);
});

test("expired active leases become waiting_for_device without replaying a device action", async () => {
  const { client, state } = createClient({ taskStatus: "executing", leaseExpired: true });
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client) });

  const expired = await store.expireLeases(new Date("2026-07-11T12:02:01Z"));

  assert.equal(expired[0].task.status, "waiting_for_device");
  assert.equal(expired[0].event.type, "worker.lease_expired");
  assert.equal(state.events.length, 1);
});

test("getActiveStatus returns distinct task and lease records after verifying the lease token", async () => {
  const token = LEASE_TOKEN;
  const store = createMobileWorkerStore({
    q: async sql => {
      assert.match(sql, /t\.id AS task_id/);
      assert.match(sql, /l\.id AS lease_id/);
      return { rows: [{
        task_id: TASK_ID,
        task_user_id: "user-1",
        task_prompt: "open settings and tell me the android version",
        task_device_id: "emulator-5554",
        task_status: "executing",
        task_created_at: new Date("2026-07-11T12:00:00Z"),
        task_updated_at: new Date("2026-07-11T12:00:00Z"),
        lease_id: LEASE_ID,
        lease_device_id: "emulator-5554",
        token_hash: (await import("../lib/keys.mjs")).sha256hex(token),
        lease_state: "active",
        lease_expires_at: new Date("2026-07-11T12:05:00Z"),
        lease_closed_at: null,
        lease_created_at: new Date("2026-07-11T12:00:00Z"),
      }] };
    },
    now: () => new Date("2026-07-11T12:00:00Z"),
  });

  const out = await store.getActiveStatus({ taskId: TASK_ID, token });

  assert.equal(out.task.id, TASK_ID);
  assert.equal(out.lease.id, LEASE_ID);
  assert.equal("token_hash" in out.lease, false);
});
