import { once } from "node:events";
import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import request from "supertest";
import { createMobileTasksRouter, parseLastEventId } from "../routes/mobile_tasks.mjs";

const TASK_ID = "11111111-1111-4111-8111-111111111111";
const CONFIRMATION_ID = "22222222-2222-4222-8222-222222222222";

function task(overrides = {}) {
  return { id: TASK_ID, user_id: "user-1", prompt: "Open Settings", status: "queued", ...overrides };
}

function event(overrides = {}) {
  return { id: "1", task_id: TASK_ID, type: "task.state", payload: { status: "paused" }, ...overrides };
}

function createStore(overrides = {}) {
  return {
    createTask: async (_userId, input) => ({ task: { id: "task-1", prompt: input.prompt }, event: event({ type: "task.created" }) }),
    listTasks: async () => [task()],
    getTask: async () => task(),
    listEvents: async () => [],
    applyCommand: async () => ({ task: task({ status: "paused" }), event: event() }),
    resolveConfirmation: async () => ({ task: task({ status: "executing" }), confirmation: { id: CONFIRMATION_ID, status: "approved" }, event: event({ type: "confirmation.approved" }) }),
    ...overrides,
  };
}

function createBroker() {
  const listeners = new Map();
  return {
    published: [],
    subscribes: [],
    unsubscribes: 0,
    publish(savedEvent) { this.published.push(savedEvent); },
    subscribe(taskId, listener) {
      this.subscribes.push(taskId);
      listeners.set(taskId, listener);
      return () => {
        this.unsubscribes += 1;
        listeners.delete(taskId);
      };
    },
    emit(savedEvent) { listeners.get(savedEvent.task_id)?.(savedEvent); },
  };
}

function createTrackedTimers() {
  const active = new Set();
  return {
    created: [],
    clearCalls: 0,
    setInterval(callback, ms) {
      const timer = { callback, ms };
      active.add(timer);
      this.created.push(timer);
      return timer;
    },
    clearInterval(timer) {
      if (active.delete(timer)) this.clearCalls += 1;
    },
    tick() {
      for (const timer of active) timer.callback();
    },
    get activeCount() { return active.size; },
  };
}

async function readUntil(reader, pattern) {
  const decoder = new TextDecoder();
  let text = "";
  for (let count = 0; count < 10; count += 1) {
    const chunk = await reader.read();
    if (chunk.done) break;
    text += decoder.decode(chunk.value, { stream: true });
    if (pattern.test(text)) return text;
  }
  assert.fail(`did not receive SSE output matching ${pattern}`);
}

async function waitFor(predicate, description) {
  for (let count = 0; count < 20; count += 1) {
    if (predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  assert.fail(`timed out waiting for ${description}`);
}

function createApp(store = createStore(), broker = createBroker(), options = {}) {
  const app = express();
  app.use(express.json());
  app.use((req, _res, next) => {
    req.principal = { userId: "user-1" };
    next();
  });
  app.use(createMobileTasksRouter({ store, broker, ...options }));
  app.use((error, _req, res, _next) => {
    options.onError?.(error);
    res.status(500).json({ error: "gateway error" });
  });
  return app;
}

test("POST /v1/mobile/tasks validates and creates a task", async () => {
  const broker = createBroker();
  const res = await request(createApp(createStore(), broker))
    .post("/v1/mobile/tasks")
    .send({ prompt: "  Open Settings  " });

  assert.equal(res.status, 201);
  assert.equal(res.body.id, "task-1");
  assert.equal(res.body.prompt, "Open Settings");
  assert.deepEqual(broker.published.map(({ type }) => type), ["task.created"]);
});

test("POST /v1/mobile/tasks rejects empty and overlong prompts", async () => {
  const app = createApp();
  const blank = await request(app).post("/v1/mobile/tasks").send({ prompt: "   " });
  const long = await request(app).post("/v1/mobile/tasks").send({ prompt: "x".repeat(4001) });

  assert.equal(blank.status, 400);
  assert.equal(long.status, 400);
});

test("POST /v1/mobile/tasks rejects unsupported prompts before creating a task when the worker is enabled", async () => {
  let creates = 0;
  const store = createStore({ createTask: async () => { creates += 1; return null; } });
  const res = await request(createApp(store, undefined, { workerEnabled: true, workerGoalPolicy: "settings_android_version" }))
    .post("/v1/mobile/tasks")
    .send({ prompt: "Send a message to Ada" });

  assert.equal(res.status, 400);
  assert.deepEqual(res.body, { error: "task is not supported by this emulator worker" });
  assert.equal(creates, 0);
});

test("POST /v1/mobile/tasks preserves unrestricted creation while the worker is disabled", async () => {
  let prompt;
  const store = createStore({ createTask: async (_userId, input) => { prompt = input.prompt; return { task: { id: "task-1", prompt }, event: event({ type: "task.created" }) }; } });
  const res = await request(createApp(store, undefined, { workerEnabled: false }))
    .post("/v1/mobile/tasks")
    .send({ prompt: "Send a message to Ada" });

  assert.equal(res.status, 201);
  assert.equal(prompt, "Send a message to Ada");
});

test("task IDs are validated", async () => {
  const res = await request(createApp()).get("/v1/mobile/tasks/not-a-uuid");

  assert.equal(res.status, 400);
  assert.equal(res.body.error, "invalid id");
});

test("missing owned tasks return 404", async () => {
  const store = createStore({ getTask: async () => null });
  const res = await request(createApp(store)).get(`/v1/mobile/tasks/${TASK_ID}`);

  assert.equal(res.status, 404);
});

test("list limits are clamped to the supported range", async () => {
  const limits = [];
  const store = createStore({ listTasks: async (_userId, limit) => { limits.push(limit); return []; } });
  const app = createApp(store);

  assert.equal((await request(app).get("/v1/mobile/tasks?limit=0")).status, 200);
  assert.equal((await request(app).get("/v1/mobile/tasks?limit=999")).status, 200);
  assert.deepEqual(limits, [1, 100]);
});

test("commands publish the persisted event", async () => {
  const published = [];
  const broker = { publish: (savedEvent) => published.push(savedEvent.type), subscribe: () => () => {} };
  const res = await request(createApp(createStore(), broker))
    .post(`/v1/mobile/tasks/${TASK_ID}/commands`)
    .send({ command: "pause" });

  assert.equal(res.status, 200);
  assert.deepEqual(published, ["task.state"]);
});

test("commands validate input and map transition errors to 409", async () => {
  const invalid = await request(createApp())
    .post(`/v1/mobile/tasks/${TASK_ID}/commands`)
    .send({ command: "launch" });
  const store = createStore({ applyCommand: async () => { throw new Error("cannot resume a task that is not paused"); } });
  const conflict = await request(createApp(store))
    .post(`/v1/mobile/tasks/${TASK_ID}/commands`)
    .send({ command: "resume" });

  assert.equal(invalid.status, 400);
  assert.equal(conflict.status, 409);
  assert.equal(conflict.body.error, "task transition conflict");
});

test("unexpected store errors propagate to the gateway error handler", async () => {
  const databaseError = new Error("database credentials leaked");
  let received;
  const store = createStore({ applyCommand: async () => { throw databaseError; } });
  const res = await request(createApp(store, undefined, { onError: error => { received = error; } }))
    .post(`/v1/mobile/tasks/${TASK_ID}/commands`)
    .send({ command: "pause" });

  assert.equal(res.status, 500);
  assert.deepEqual(res.body, { error: "gateway error" });
  assert.equal(received, databaseError);
});

test("confirmation decision only accepts approve or reject", async () => {
  const res = await request(createApp())
    .post(`/v1/mobile/tasks/${TASK_ID}/confirmations/${CONFIRMATION_ID}`)
    .send({ decision: "maybe" });

  assert.equal(res.status, 400);
});

test("confirmation decisions publish their persisted event and hide missing rows", async () => {
  const broker = createBroker();
  const accepted = await request(createApp(createStore(), broker))
    .post(`/v1/mobile/tasks/${TASK_ID}/confirmations/${CONFIRMATION_ID}`)
    .send({ decision: "approve" });
  const missing = await request(createApp(createStore({ resolveConfirmation: async () => null })))
    .post(`/v1/mobile/tasks/${TASK_ID}/confirmations/${CONFIRMATION_ID}`)
    .send({ decision: "reject" });

  assert.equal(accepted.status, 200);
  assert.deepEqual(broker.published.map(({ type }) => type), ["confirmation.approved"]);
  assert.equal(missing.status, 404);
});

test("parseLastEventId accepts non-negative integer headers only", () => {
  assert.equal(parseLastEventId(undefined), 0);
  assert.equal(parseLastEventId("42"), 42);
  assert.equal(parseLastEventId("-1"), 0);
  assert.equal(parseLastEventId("4.2"), 0);
  assert.equal(parseLastEventId("nope"), 0);
});

test("SSE replays, heartbeats, and cleans up the broker and interval exactly once", async (t) => {
  const calls = [];
  const replay = event({ id: "7", type: "task.created" });
  const store = createStore({
    getTask: async () => { calls.push("getTask"); return task(); },
    listEvents: async (_userId, _taskId, afterId) => { calls.push(`listEvents:${afterId}`); return [replay]; },
  });
  const broker = createBroker();
  const timers = createTrackedTimers();
  const app = createApp(store, broker, {
    heartbeatMs: 5,
    setIntervalFn: timers.setInterval.bind(timers),
    clearIntervalFn: timers.clearInterval.bind(timers),
  });
  const server = app.listen(0);
  t.after(() => server.close());
  await once(server, "listening");

  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/v1/mobile/tasks/${TASK_ID}/events`, {
    headers: { "Last-Event-ID": "6" },
  });
  const reader = response.body.getReader();
  try {
    assert.equal(response.headers.get("content-type"), "text/event-stream; charset=utf-8");
    assert.deepEqual(calls, ["getTask", "listEvents:6"]);
    assert.deepEqual(broker.subscribes, [TASK_ID]);
    assert.match(await readUntil(reader, /^id: 7\nevent: task\.created\ndata: /m), /^id: 7\nevent: task\.created\ndata: /m);
    assert.equal(timers.activeCount, 1);
    assert.equal(timers.created[0].ms, 5);

    timers.tick();
    assert.match(await readUntil(reader, /: heartbeat\n\n/), /: heartbeat\n\n/);

    broker.emit(event({ id: "8", type: "task.paused" }));
    assert.match(await readUntil(reader, /event: task\.paused/), /event: task\.paused/);
  } finally {
    await reader.cancel();
  }
  await waitFor(() => broker.unsubscribes === 1 && timers.activeCount === 0, "SSE cleanup");
  assert.equal(broker.unsubscribes, 1);
  assert.equal(timers.clearCalls, 1);
});

test("SSE returns 404 before it writes streaming headers for an unowned task", async () => {
  const app = createApp(createStore({ getTask: async () => null }));
  const res = await request(app).get(`/v1/mobile/tasks/${TASK_ID}/events`);

  assert.equal(res.status, 404);
  assert.notEqual(res.headers["content-type"], "text/event-stream; charset=utf-8");
});
