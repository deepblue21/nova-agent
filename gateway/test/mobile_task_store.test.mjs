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

test("getTask scopes its lookup to the authenticated user", async () => {
  const calls = [];
  const query = async (sql, params) => {
    calls.push({ sql, params });
    return { rows: [{ id: "task-1", user_id: "user-1", status: "queued" }] };
  };
  const store = createMobileTaskStore({ q: query, withTx: async fn => fn({ query }) });

  const task = await store.getTask("user-1", "task-1");

  assert.equal(task.id, "task-1");
  assert.match(calls[0].sql, /WHERE user_id=\$1 AND id=\$2/);
  assert.deepEqual(calls[0].params, ["user-1", "task-1"]);
});

test("listEvents scopes events to the user and honors afterId", async () => {
  const calls = [];
  const query = async (sql, params) => {
    calls.push({ sql, params });
    return { rows: [{ id: 4, task_id: "task-1", type: "task.paused", payload: {} }] };
  };
  const store = createMobileTaskStore({ q: query, withTx: async fn => fn({ query }) });

  const events = await store.listEvents("user-1", "task-1", 3);

  assert.equal(events[0].id, "4");
  assert.match(calls[0].sql, /JOIN mobile_tasks/);
  assert.match(calls[0].sql, /t\.user_id=\$1 AND e\.task_id=\$2 AND e\.id > \$3/);
  assert.deepEqual(calls[0].params, ["user-1", "task-1", 3, 500]);
});

test("applyCommand rejects terminal tasks before writing an event", async () => {
  const calls = [];
  const client = { query: async (sql, params) => {
    calls.push({ sql, params });
    if (sql.includes("FROM mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", status: "completed" }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  await assert.rejects(store.applyCommand("user-1", "task-1", "cancel"), /terminal/);
  assert.equal(calls.length, 1);
  assert.match(calls[0].sql, /FOR UPDATE/);
});

test("requestConfirmation rejects a second pending confirmation for a task", async () => {
  const calls = [];
  const client = { query: async (sql, params) => {
    calls.push({ sql, params });
    if (sql.includes("FROM mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", status: "executing" }] };
    if (sql.includes("FROM mobile_confirmations")) return { rows: [{ id: "confirmation-1", status: "pending" }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  await assert.rejects(
    store.requestConfirmation("user-1", "task-1", { riskLevel: "R3", action: { kind: "send" } }),
    /pending confirmation/,
  );
  assert.equal(calls.length, 2);
  assert.match(calls[1].sql, /FOR UPDATE/);
});

test("resolveConfirmation approval restores the confirmation resume status", async () => {
  const calls = [];
  const client = { query: async (sql, params) => {
    calls.push({ sql, params });
    if (sql.includes("FROM mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", status: "waiting_for_confirmation" }] };
    if (sql.includes("FROM mobile_confirmations")) return { rows: [{ id: "confirmation-1", task_id: "task-1", resume_status: "executing", status: "pending" }] };
    if (sql.includes("UPDATE mobile_confirmations")) return { rows: [{ id: "confirmation-1", status: "approved" }] };
    if (sql.includes("UPDATE mobile_tasks")) return { rows: [{ id: "task-1", status: "executing" }] };
    if (sql.includes("INSERT INTO mobile_task_events")) return { rows: [{ id: 7, task_id: "task-1", type: "confirmation.approved", payload: { status: "executing" } }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  const out = await store.resolveConfirmation("user-1", "task-1", "confirmation-1", "approve");

  assert.equal(out.confirmation.status, "approved");
  assert.equal(out.task.status, "executing");
  assert.equal(out.event.type, "confirmation.approved");
  assert.equal(out.event.id, "7");
});

test("resolveConfirmation rejection pauses the task", async () => {
  const calls = [];
  const client = { query: async (sql, params) => {
    calls.push({ sql, params });
    if (sql.includes("FROM mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", status: "waiting_for_confirmation" }] };
    if (sql.includes("FROM mobile_confirmations")) return { rows: [{ id: "confirmation-1", task_id: "task-1", resume_status: "executing", status: "pending" }] };
    if (sql.includes("UPDATE mobile_confirmations")) return { rows: [{ id: "confirmation-1", status: "rejected" }] };
    if (sql.includes("UPDATE mobile_tasks")) return { rows: [{ id: "task-1", status: "paused" }] };
    if (sql.includes("INSERT INTO mobile_task_events")) return { rows: [{ id: 8, task_id: "task-1", type: "confirmation.rejected", payload: { status: "paused" } }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  const out = await store.resolveConfirmation("user-1", "task-1", "confirmation-1", "reject");

  assert.equal(out.confirmation.status, "rejected");
  assert.equal(out.task.status, "paused");
  assert.equal(out.event.type, "confirmation.rejected");
  assert.equal(out.event.id, "8");
});
