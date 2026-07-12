import assert from "node:assert/strict";
import test from "node:test";
import {
  WORKER_EVENT_SEQUENCE,
  parseSseFrames,
  runMobilerunWorkerSmoke,
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

test("worker smoke checks the required token before the user API key", () => {
  assert.throws(
    () => workerEnvironment({ HORUS_GATEWAY_URL: "http://127.0.0.1:8088/v1" }),
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

test("worker smoke reads CRLF SSE frames split across live response chunks", async () => {
  const encoder = new TextEncoder();
  const eventText = WORKER_EVENT_SEQUENCE.map((type, index) => (
    `id: ${index + 1}\r\nevent: ${type}\r\ndata: {"id":"${index + 1}"}\r\n\r\n`
  )).join("");
  const firstBoundary = eventText.indexOf("\r\n\r\n");
  const secondBoundary = eventText.indexOf("\r\n\r\n", firstBoundary + 4);
  const chunks = [
    eventText.slice(0, firstBoundary + 3),
    eventText.slice(firstBoundary + 3, secondBoundary + 2),
    eventText.slice(secondBoundary + 2),
  ];
  const originalFetch = globalThis.fetch;
  let requestCount = 0;

  globalThis.fetch = async (url) => {
    requestCount += 1;
    if (String(url).endsWith("/mobile/tasks")) {
      return new Response(JSON.stringify({ id: "task-1" }), { status: 201 });
    }
    return new Response(new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
        controller.close();
      },
    }), { status: 200 });
  };

  try {
    await runMobilerunWorkerSmoke(environment);
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(requestCount, 2);
});
