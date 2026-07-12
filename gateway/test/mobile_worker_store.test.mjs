import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { createMobileWorkerStore } from "../lib/mobile_worker_store.mjs";

const TASK_ID = "11111111-1111-4111-8111-111111111111";
const OLDER_TASK_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const NEWER_TASK_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
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

function createClient({ taskStatus = "queued", leaseExpired = false, leaseState = "active", tasks } = {}) {
  const state = {
    tasks: (tasks ?? [{ id: TASK_ID, prompt: "open settings and tell me the android version", status: taskStatus }]).map(task => ({
      device_id: null,
      created_at: new Date("2026-07-11T12:00:00Z"),
      ...task,
    })),
    lease: { id: LEASE_ID, task_id: TASK_ID, token_hash: "", state: leaseState, expires_at: leaseExpired ? new Date("2026-07-11T12:00:00Z") : new Date("2026-07-11T12:05:00Z") },
    reports: new Map(),
    events: [],
    calls: [],
  };
  Object.defineProperty(state, "task", { get: () => state.tasks.find(task => task.id === state.lease.task_id) ?? state.tasks[0] });
  const taskById = id => state.tasks.find(task => task.id === id);
  const normalized = value => String(value).trim().replace(/\s+/g, " ").toLowerCase();
  let nextEventId = 1;
  const client = { query: async (sql, params = []) => {
    state.calls.push({ sql, params });
    if (sql.includes("SELECT id FROM mobile_tasks WHERE id=$1")) {
      const task = taskById(params[0]);
      return { rows: task ? [{ id: task.id }] : [] };
    }
    if (sql.includes("FROM mobile_tasks t") && sql.includes("SKIP LOCKED") && sql.includes("t.status='queued'")) {
      const task = state.tasks
        .filter(candidate => candidate.status === "queued" && params[0].includes(normalized(candidate.prompt)))
        .sort((left, right) => new Date(left.created_at) - new Date(right.created_at))[0];
      return { rows: task ? [{ ...task }] : [] };
    }
    if (sql.includes("INSERT INTO mobile_worker_leases")) {
      state.lease = { ...state.lease, id: LEASE_ID, task_id: params[0], device_id: params[1], token_hash: params[2], expires_at: params[3], state: "active" };
      return { rows: [{ ...state.lease }] };
    }
    if (sql.includes("UPDATE mobile_tasks")) {
      const taskId = sql.includes("status='executing'") ? params[1] : sql.includes("status='waiting_for_device'") ? params[0] : params[1];
      const task = taskById(taskId);
      task.status = sql.includes("status='executing'") ? "executing" : sql.includes("status='waiting_for_device'") ? "waiting_for_device" : params[0];
      if (sql.includes("status='executing'")) task.device_id = params[0];
      return { rows: [{ ...task }] };
    }
    if (sql.includes("INSERT INTO mobile_task_events")) {
      const event = { id: nextEventId++, task_id: params[0], type: params[1], payload: params[2] };
      state.events.push(event);
      return { rows: [event] };
    }
    if (sql.includes("FROM mobile_worker_leases l") && sql.includes("FOR UPDATE")) {
      if (sql.includes("l.id=$1") && (params[0] !== state.lease.id || params[1] !== state.lease.task_id)) return { rows: [] };
      if (sql.includes("l.expires_at <= $1")) {
        return { rows: state.lease.state === "active" && new Date(state.lease.expires_at) <= new Date(params[0]) ? [{ ...state.lease, task_status: state.task.status }] : [] };
      }
      return { rows: [{ ...state.lease, task_status: state.task.status }] };
    }
    if (sql.includes("FROM mobile_worker_reports") && sql.includes("report_id")) {
      const report = state.reports.get(`${params[0]}:${params[1]}`);
      return { rows: report ? [report] : [] };
    }
    if (sql.includes("FROM mobile_task_events WHERE id=$1")) {
      return { rows: state.events.filter(event => event.id === params[0]) };
    }
    if (sql.includes("INSERT INTO mobile_worker_reports")) {
      const report = { lease_id: params[0], report_id: params[1], event_id: params[3], task_status: params[4] };
      state.reports.set(`${params[0]}:${params[1]}`, report);
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
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  return { client, state };
}

test("claimNext locks the oldest supported queued task and records lease events", async () => {
  const { client, state } = createClient();
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z"), randomBytes: () => Buffer.alloc(32, 7) });

  const out = await store.claimNext({ deviceId: "emulator-5554", policy: "settings_android_version" });

  assert.equal(out.task.status, "executing");
  assert.equal(out.task.device_id, "emulator-5554");
  assert.equal(out.lease.device_id, "emulator-5554");
  assert.deepEqual(out.events.map(event => event.type), ["worker.claimed", "worker.executing"]);
  assert.match(state.calls[0].sql, /FOR UPDATE SKIP LOCKED/);
  assert.match(state.calls[0].sql, /lower\(regexp_replace\(trim\(t\.prompt\), '\\s\+', ' ', 'g'\)\)/);
  assert.deepEqual(state.calls[0].params, [["open settings and tell me the android version", "ayarlari ac ve android surumunu soyle"]]);
  const taskUpdate = state.calls.find(call => call.sql.includes("UPDATE mobile_tasks") && call.sql.includes("status='executing'"));
  assert.ok(taskUpdate);
  assert.match(taskUpdate.sql, /SET status='executing', device_id=\$1, updated_at=now\(\)/);
  assert.deepEqual(taskUpdate.params, ["emulator-5554", TASK_ID]);
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
      if (sql.includes("SELECT id FROM mobile_tasks WHERE id=$1")) return { rows: [{ id: TASK_ID }] };
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

test("getActiveStatus distinguishes an unknown task from an inactive or wrong lease", async () => {
  const now = () => new Date("2026-07-11T12:00:00Z");
  const missingTask = createMobileWorkerStore({
    q: async sql => {
      assert.match(sql, /SELECT id FROM mobile_tasks WHERE id=\$1/);
      return { rows: [] };
    },
    now,
  });
  const inactiveLease = createMobileWorkerStore({
    q: async sql => {
      if (sql.includes("SELECT id FROM mobile_tasks WHERE id=$1")) return { rows: [{ id: TASK_ID }] };
      assert.match(sql, /FROM mobile_worker_leases l/);
      return { rows: [] };
    },
    now,
  });

  await assert.rejects(
    missingTask.getActiveStatus({ taskId: TASK_ID, token: LEASE_TOKEN }),
    error => error?.code === "TASK_NOT_FOUND",
  );
  await assert.rejects(
    inactiveLease.getActiveStatus({ taskId: TASK_ID, token: LEASE_TOKEN }),
    error => error?.code === "LEASE_NOT_ACTIVE",
  );
});

test("claimNext claims the oldest safe queued task and keeps credentials out of events", async () => {
  const { client, state } = createClient({
    tasks: [
      { id: NEWER_TASK_ID, prompt: "open settings and tell me the android version", status: "queued", created_at: new Date("2026-07-11T12:01:00Z") },
      { id: OLDER_TASK_ID, prompt: "  OPEN settings AND tell me the Android version  ", status: "queued", created_at: new Date("2026-07-11T12:00:00Z") },
      { id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc", prompt: "open the browser", status: "queued", created_at: new Date("2026-07-11T11:59:00Z") },
    ],
  });
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z"), randomBytes: () => Buffer.alloc(32, 7) });

  const out = await store.claimNext({ deviceId: "emulator-5554", policy: "settings_android_version" });

  assert.equal(out.task.id, OLDER_TASK_ID);
  assert.equal(out.lease.token_hash, undefined);
  assert.equal(JSON.stringify(out.events).includes(out.lease.token), false);
  assert.equal(state.tasks.find(task => task.id === NEWER_TASK_ID).status, "queued");
  assert.match(state.calls[0].sql, /ORDER BY t\.created_at ASC/);
  assert.match(state.calls[0].sql, /FOR UPDATE SKIP LOCKED/);
});

test("reports map every allowed phase and close every non-progress lease", async () => {
  const phases = {
    observing: ["observing", "worker.observing", "active"],
    running: ["executing", "worker.running", "active"],
    completed: ["completed", "worker.completed", "closed"],
    failed: ["failed", "worker.failed", "closed"],
    waiting_for_device: ["waiting_for_device", "worker.waiting_for_device", "closed"],
    waiting_for_compute: ["waiting_for_compute", "worker.waiting_for_compute", "closed"],
  };

  for (const [phase, [status, type, leaseState]] of Object.entries(phases)) {
    const { client, state } = createClient({ taskStatus: "executing" });
    state.lease.token_hash = (await import("../lib/keys.mjs")).sha256hex(LEASE_TOKEN);
    const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z") });

    const out = await store.report(reportInput({ phase, report_id: `${REPORT_ID.slice(0, -1)}${Object.keys(phases).indexOf(phase)}` }));

    assert.equal(out.task.status, status, phase);
    assert.equal(out.event.type, type, phase);
    assert.equal(state.lease.state, leaseState, phase);
    assert.equal(JSON.stringify(out).includes(LEASE_TOKEN), false, phase);
  }
});

test("reports reject expired and non-active leases with a typed lease outcome", async () => {
  for (const overrides of [{ leaseExpired: true }, { leaseState: "closed" }]) {
    const { client, state } = createClient({ taskStatus: "executing", ...overrides });
    state.lease.token_hash = (await import("../lib/keys.mjs")).sha256hex(LEASE_TOKEN);
    const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client), now: () => new Date("2026-07-11T12:00:00Z") });

    await assert.rejects(
      store.report(reportInput()),
      error => error?.code === "LEASE_NOT_ACTIVE",
    );
    assert.equal(state.events.length, 0);
  }
});

test("reports distinguish an unknown task from missing and wrong leases", async () => {
  const now = () => new Date("2026-07-11T12:00:00Z");
  const unknown = createClient({ tasks: [] });
  const missingLease = createClient({ taskStatus: "executing" });
  const wrongLease = createClient({ taskStatus: "executing" });
  wrongLease.state.lease.token_hash = (await import("../lib/keys.mjs")).sha256hex(LEASE_TOKEN);
  const unknownStore = createMobileWorkerStore({ q: unknown.client.query, withTx: async fn => fn(unknown.client), now });
  const missingLeaseStore = createMobileWorkerStore({ q: missingLease.client.query, withTx: async fn => fn(missingLease.client), now });
  const wrongLeaseStore = createMobileWorkerStore({ q: wrongLease.client.query, withTx: async fn => fn(wrongLease.client), now });

  await assert.rejects(
    unknownStore.report(reportInput()),
    error => error?.code === "TASK_NOT_FOUND",
  );
  await assert.rejects(
    missingLeaseStore.report(reportInput({ lease_id: "44444444-4444-4444-8444-444444444444" })),
    error => error?.code === "LEASE_NOT_ACTIVE",
  );
  await assert.rejects(
    wrongLeaseStore.report(reportInput({ token: crypto.randomBytes(32).toString("hex") })),
    error => error?.code === "LEASE_NOT_ACTIVE",
  );
  assert.equal(unknown.state.events.length, 0);
  assert.equal(missingLease.state.events.length, 0);
  assert.equal(wrongLease.state.events.length, 0);
});

test("claimNext rejects a non-emulator device with a typed input outcome before it queries", async () => {
  const { client, state } = createClient();
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client) });

  await assert.rejects(
    store.claimNext({ deviceId: "device-serial", policy: "settings_android_version" }),
    error => error?.code === "INVALID_REQUEST",
  );
  assert.equal(state.calls.length, 0);
});

test("expireLeases leaves an unexpired active lease untouched", async () => {
  const { client, state } = createClient({ taskStatus: "executing" });
  const store = createMobileWorkerStore({ q: client.query, withTx: async fn => fn(client) });

  const out = await store.expireLeases(new Date("2026-07-11T12:02:01Z"));

  assert.deepEqual(out, []);
  assert.equal(state.lease.state, "active");
  assert.equal(state.task.status, "executing");
  assert.equal(state.events.length, 0);
});
