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

function lifecycleClient() {
  const task = { id: "task-1", user_id: "user-1", status: "waiting_for_confirmation" };
  return {
    query: async (sql, params) => {
      if (sql.includes("FROM mobile_tasks")) return { rows: [{ ...task }] };
      if (sql.includes("FROM mobile_confirmations")) {
        return { rows: [{ id: "confirmation-1", task_id: "task-1", resume_status: "executing", status: "pending" }] };
      }
      if (sql.includes("UPDATE mobile_confirmations")) return { rows: [{ id: "confirmation-1", status: "approved" }] };
      if (sql.includes("UPDATE mobile_tasks")) {
        task.status = params[0];
        return { rows: [{ ...task }] };
      }
      if (sql.includes("INSERT INTO mobile_task_events")) return { rows: [{ id: 9, task_id: "task-1", type: "task.changed", payload: {} }] };
      throw new Error(`unexpected SQL: ${sql}`);
    },
  };
}

test("cancel then approve cannot revive a task with a stale confirmation", async () => {
  const client = lifecycleClient();
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  const cancelled = await store.applyCommand("user-1", "task-1", "cancel");
  assert.equal(cancelled.task.status, "cancelled");
  await assert.rejects(
    store.resolveConfirmation("user-1", "task-1", "confirmation-1", "approve"),
    /waiting_for_confirmation/,
  );
});

test("pause then approve cannot revive a task with a stale confirmation", async () => {
  const client = lifecycleClient();
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  const paused = await store.applyCommand("user-1", "task-1", "pause");
  assert.equal(paused.task.status, "paused");
  await assert.rejects(
    store.resolveConfirmation("user-1", "task-1", "confirmation-1", "approve"),
    /waiting_for_confirmation/,
  );
});

test("requestConfirmation rejects an unknown resume status before opening a transaction", async () => {
  let transactions = 0;
  const store = createMobileTaskStore({
    q: async () => { throw new Error("query should not run"); },
    withTx: async fn => { transactions += 1; return fn({ query: async () => ({ rows: [] }) }); },
  });

  await assert.rejects(
    store.requestConfirmation("user-1", "task-1", { riskLevel: "R2", action: {}, resumeStatus: "unknown" }),
    /resume status/,
  );
  assert.equal(transactions, 0);
});

test("requestConfirmation rejects terminal resume statuses before opening a transaction", async () => {
  let transactions = 0;
  const store = createMobileTaskStore({
    q: async () => { throw new Error("query should not run"); },
    withTx: async fn => { transactions += 1; return fn({ query: async () => ({ rows: [] }) }); },
  });

  for (const resumeStatus of ["completed", "failed", "cancelled"]) {
    await assert.rejects(
      store.requestConfirmation("user-1", "task-1", { riskLevel: "R2", action: {}, resumeStatus }),
      /resume status/,
    );
  }
  assert.equal(transactions, 0);
});

test("createTask caps prompts by Unicode code points", async () => {
  const prompt = `${"x".repeat(3999)}😀y`;
  const expected = `${"x".repeat(3999)}😀`;
  let taskParams;
  const client = { query: async (sql, params) => {
    if (sql.includes("INSERT INTO mobile_tasks")) {
      taskParams = params;
      return { rows: [{ id: "task-1", user_id: "user-1", prompt: params[1], status: "queued" }] };
    }
    if (sql.includes("INSERT INTO mobile_task_events")) return { rows: [{ id: 10, task_id: "task-1", type: "task.created", payload: {} }] };
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  await store.createTask("user-1", { prompt });

  assert.equal(taskParams[1], expected);
  assert.equal(Array.from(taskParams[1]).length, 4000);
});

test("applyCommand caps notes by Unicode code points", async () => {
  const note = `${"x".repeat(999)}😀y`;
  const expected = `${"x".repeat(999)}😀`;
  let eventParams;
  const client = { query: async (sql, params) => {
    if (sql.includes("FROM mobile_tasks")) return { rows: [{ id: "task-1", user_id: "user-1", status: "executing" }] };
    if (sql.includes("UPDATE mobile_tasks")) return { rows: [{ id: "task-1", status: "paused" }] };
    if (sql.includes("INSERT INTO mobile_task_events")) {
      eventParams = params;
      return { rows: [{ id: 11, task_id: "task-1", type: "task.pause", payload: params[2] }] };
    }
    throw new Error(`unexpected SQL: ${sql}`);
  }};
  const store = createMobileTaskStore({ q: client.query, withTx: async fn => fn(client) });

  await store.applyCommand("user-1", "task-1", "pause", note);

  assert.equal(eventParams[2].note, expected);
  assert.equal(Array.from(eventParams[2].note).length, 1000);
});
