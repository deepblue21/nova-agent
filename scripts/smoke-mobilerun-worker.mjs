#!/usr/bin/env node
import assert from "node:assert/strict";

const SMOKE_TIMEOUT_MS = 120_000;
const WORKER_PROMPT = "Open Settings and tell me the Android version";

export const WORKER_EVENT_SEQUENCE = Object.freeze([
  "task.created",
  "worker.claimed",
  "worker.executing",
  "worker.observing",
  "worker.running",
  "worker.completed",
]);

function required(value, name) {
  const text = String(value || "").trim();
  if (!text) throw new Error(`${name} is required`);
  return text;
}

function loopbackGatewayUrl(value) {
  let url;
  try {
    url = new URL(required(value, "HORUS_GATEWAY_URL"));
  } catch {
    throw new Error("HORUS_GATEWAY_URL must be a loopback HTTP URL");
  }
  if (!/^https?:$/.test(url.protocol) || !["127.0.0.1", "::1", "localhost"].includes(url.hostname)) {
    throw new Error("HORUS_GATEWAY_URL must use a loopback host");
  }
  return url.toString().replace(/\/$/, "");
}

export function workerEnvironment(values = process.env) {
  return Object.freeze({
    gatewayUrl: loopbackGatewayUrl(values.HORUS_GATEWAY_URL),
    apiKey: required(values.HORUS_API_KEY, "HORUS_API_KEY"),
    workerToken: required(values.MOBILE_WORKER_TOKEN, "MOBILE_WORKER_TOKEN"),
  });
}

export function parseSseFrames(text) {
  return String(text)
    .split(/\r?\n\r?\n/)
    .map(frame => frame.trim())
    .filter(frame => frame && !frame.startsWith(":"))
    .map(frame => {
      const lines = frame.split(/\r?\n/);
      const id = lines.find(line => line.startsWith("id: "))?.slice(4);
      const type = lines.find(line => line.startsWith("event: "))?.slice(7);
      const data = lines.filter(line => line.startsWith("data: ")).map(line => line.slice(6)).join("\n");
      if (!id || !type || !data) return null;
      return { id, type, data: JSON.parse(data) };
    })
    .filter(Boolean);
}

async function createTask({ gatewayUrl, apiKey }, signal) {
  const response = await fetch(`${gatewayUrl}/mobile/tasks`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ prompt: WORKER_PROMPT }),
    signal,
  });
  const body = await response.json().catch(() => null);
  assert.equal(response.status, 201, "worker smoke task creation failed");
  assert.ok(body?.id, "worker smoke task response includes an id");
  return body;
}

async function waitForWorkerEvents({ gatewayUrl, apiKey }, taskId, signal) {
  const response = await fetch(`${gatewayUrl}/mobile/tasks/${taskId}/events`, {
    headers: { Authorization: `Bearer ${apiKey}` },
    signal,
  });
  assert.equal(response.status, 200, "worker smoke events request failed");
  const reader = response.body?.getReader();
  assert.ok(reader, "worker smoke events response has a readable body");

  const decoder = new TextDecoder();
  const events = [];
  let pending = "";
  try {
    while (events.length < WORKER_EVENT_SEQUENCE.length) {
      const { done, value } = await reader.read();
      assert.equal(done, false, "worker smoke events stream ended early");
      pending += decoder.decode(value, { stream: true });
      const boundary = pending.lastIndexOf("\n\n");
      if (boundary < 0) continue;
      const complete = pending.slice(0, boundary + 2);
      pending = pending.slice(boundary + 2);
      events.push(...parseSseFrames(complete));
    }
  } finally {
    await reader.cancel().catch(() => {});
  }

  assert.deepEqual(events.slice(0, WORKER_EVENT_SEQUENCE.length).map(event => event.type), WORKER_EVENT_SEQUENCE);
}

export async function runMobilerunWorkerSmoke(values = process.env) {
  const environment = workerEnvironment(values);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), SMOKE_TIMEOUT_MS);
  try {
    const task = await createTask(environment, controller.signal);
    await waitForWorkerEvents(environment, task.id, controller.signal);
  } finally {
    clearTimeout(timeout);
    controller.abort();
  }
}

if (process.argv[1]?.endsWith("smoke-mobilerun-worker.mjs")) {
  runMobilerunWorkerSmoke().then(
    () => console.log("mobilerun worker smoke: ok"),
    error => {
      console.error(`mobilerun worker smoke: failed: ${error.message}`);
      process.exitCode = 1;
    },
  );
}
