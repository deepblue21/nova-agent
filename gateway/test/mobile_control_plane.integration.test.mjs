import { once } from "node:events";
import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import request from "supertest";
import { MobileEventBroker } from "../lib/mobile_event_broker.mjs";
import { createMobileTasksRouter } from "../routes/mobile_tasks.mjs";
import { createMemoryMobileTaskStore } from "./helpers/memory_mobile_task_store.mjs";

const USER_ID = "user-1";

function createApp(store, broker) {
  const app = express();
  app.use(express.json());
  app.use((req, _res, next) => {
    req.principal = { userId: USER_ID };
    next();
  });
  app.use(createMobileTasksRouter({ store, broker, heartbeatMs: 60_000 }));
  return app;
}

async function startServer(app) {
  const server = app.listen(0, "127.0.0.1");
  await once(server, "listening");
  return server;
}

async function stopServer(server) {
  if (!server?.listening) return;
  server.closeAllConnections?.();
  await new Promise((resolve, reject) => {
    server.close(error => (error ? reject(error) : resolve()));
  });
}

async function readWithTimeout(reader, timeoutMs = 1_000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("timed out waiting for SSE data")), timeoutMs);
    reader.read().then(
      value => {
        clearTimeout(timeout);
        resolve(value);
      },
      error => {
        clearTimeout(timeout);
        reject(error);
      },
    );
  });
}

async function readUntil(reader, pattern) {
  const decoder = new TextDecoder();
  let text = "";
  for (let count = 0; count < 10; count += 1) {
    const chunk = await readWithTimeout(reader);
    if (chunk.done) break;
    text += decoder.decode(chunk.value, { stream: true });
    if (pattern.test(text)) return text;
  }
  assert.fail(`did not receive SSE output matching ${pattern}`);
}

test("mobile task control plane replays confirmation lifecycle and reaches cancellation", async (t) => {
  const store = createMemoryMobileTaskStore();
  const broker = new MobileEventBroker();
  const app = createApp(store, broker);
  let server;
  let reader;

  t.after(async () => {
    await reader?.cancel().catch(() => {});
    await stopServer(server);
  });

  const created = await request(app)
    .post("/v1/mobile/tasks")
    .send({ prompt: "Open Settings and prepare Wi-Fi change" });
  assert.equal(created.status, 201);
  assert.equal(created.body.prompt, "Open Settings and prepare Wi-Fi change");

  const [firstEvent] = await store.listEvents(USER_ID, created.body.id);
  assert.equal(firstEvent.type, "task.created");

  server = await startServer(app);
  const { port } = server.address();
  const sseResponse = await fetch(`http://127.0.0.1:${port}/v1/mobile/tasks/${created.body.id}/events`);
  reader = sseResponse.body.getReader();
  assert.match(await readUntil(reader, /event: task\.created/), /event: task\.created/);

  const confirmationRequest = await store.requestConfirmation(USER_ID, created.body.id, {
    riskLevel: "R2",
    action: { type: "change_setting", target: "wifi" },
  });
  assert.equal(confirmationRequest.task.status, "waiting_for_confirmation");
  broker.publish(confirmationRequest.event);
  assert.match(await readUntil(reader, /event: confirmation\.requested/), /event: confirmation\.requested/);

  const approved = await request(app)
    .post(`/v1/mobile/tasks/${created.body.id}/confirmations/${confirmationRequest.confirmation.id}`)
    .send({ decision: "approve" });
  assert.equal(approved.status, 200);
  assert.equal(approved.body.status, "executing");

  await reader.cancel();
  reader = undefined;
  await stopServer(server);
  server = undefined;

  const replay = await store.listEvents(USER_ID, created.body.id, firstEvent.id);
  assert.deepEqual(replay.map(event => event.type), ["confirmation.requested", "confirmation.approved"]);
  assert.ok(Number(replay[0].id) > Number(firstEvent.id));
  assert.ok(Number(replay[1].id) > Number(replay[0].id));

  const cancelled = await request(app)
    .post(`/v1/mobile/tasks/${created.body.id}/commands`)
    .send({ command: "cancel" });
  assert.equal(cancelled.status, 200);
  assert.equal(cancelled.body.status, "cancelled");
  assert.equal((await store.getTask(USER_ID, created.body.id)).status, "cancelled");
});
