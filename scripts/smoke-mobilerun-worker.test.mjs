import assert from "node:assert/strict";
import test from "node:test";
import {
  WORKER_EVENT_SEQUENCE,
  parseSseFrames,
  workerEnvironment,
} from "./smoke-mobilerun-worker.mjs";

const environment = {
  HORUS_GATEWAY_URL: "http://127.0.0.1:8088/v1",
  HORUS_API_KEY: "test-api-key",
  MOBILE_WORKER_TOKEN: "test-worker-token",
};

test("worker smoke refuses a non-loopback Gateway URL", () => {
  assert.throws(
    () => workerEnvironment({ ...environment, HORUS_GATEWAY_URL: "http://0.0.0.0:8088/v1" }),
    /loopback/,
  );
});

test("worker smoke requires the dedicated worker token", () => {
  assert.throws(
    () => workerEnvironment({ ...environment, MOBILE_WORKER_TOKEN: "" }),
    /MOBILE_WORKER_TOKEN/,
  );
});

test("worker smoke parses complete SSE frames without losing event order", () => {
  const frames = parseSseFrames(
    "id: 1\nevent: task.created\ndata: {\"status\":\"queued\"}\n\n" +
      "id: 2\nevent: worker.claimed\ndata: {\"status\":\"executing\"}\n\n",
  );

  assert.deepEqual(frames.map(({ id, type }) => ({ id, type })), [
    { id: "1", type: "task.created" },
    { id: "2", type: "worker.claimed" },
  ]);
  assert.deepEqual(WORKER_EVENT_SEQUENCE, [
    "task.created",
    "worker.claimed",
    "worker.executing",
    "worker.observing",
    "worker.running",
    "worker.completed",
  ]);
});
