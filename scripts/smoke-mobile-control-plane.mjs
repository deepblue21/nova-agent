#!/usr/bin/env node
import assert from "node:assert/strict";

const DEFAULT_BASE_URL = "http://localhost/v1";
const REQUEST_TIMEOUT_MS = 10_000;

export function requireApiKey(value) {
  const apiKey = String(value || "").trim();
  if (!apiKey) throw new Error("HORUS_API_KEY is required");
  return apiKey;
}

export function parseSseFrames(text) {
  return text
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

async function request(baseUrl, apiKey, path, { method = "GET", body, headers = {} } = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${apiKey}`,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await response.text();
    let json = null;
    try { json = JSON.parse(text); } catch {}
    return { status: response.status, json, text };
  } finally {
    clearTimeout(timeout);
  }
}

async function readSseFrames(baseUrl, apiKey, taskId, afterId, expectedCount) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let reader;
  try {
    const response = await fetch(`${baseUrl}/mobile/tasks/${taskId}/events`, {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        ...(afterId == null ? {} : { "Last-Event-ID": String(afterId) }),
      },
      signal: controller.signal,
    });
    assert.equal(response.status, 200, `events returned ${response.status}`);
    reader = response.body?.getReader();
    assert.ok(reader, "events response has a readable body");

    const decoder = new TextDecoder();
    let pending = "";
    const frames = [];
    while (frames.length < expectedCount) {
      const { done, value } = await reader.read();
      assert.equal(done, false, "events stream ended before the expected replay");
      pending += decoder.decode(value, { stream: true });
      const boundary = pending.lastIndexOf("\n\n");
      if (boundary < 0) continue;
      const complete = pending.slice(0, boundary + 2);
      pending = pending.slice(boundary + 2);
      frames.push(...parseSseFrames(complete));
    }
    return frames.slice(0, expectedCount);
  } finally {
    clearTimeout(timeout);
    await reader?.cancel().catch(() => {});
    controller.abort();
  }
}

function taskFrom(response, expectedStatus) {
  assert.equal(response.status, expectedStatus ? 200 : 201, response.text);
  assert.ok(response.json?.id, "task response includes an id");
  if (expectedStatus) assert.equal(response.json.status, expectedStatus);
  return response.json;
}

export async function runMobileControlPlaneSmoke({
  baseUrl = process.env.HORUS_BASE_URL || DEFAULT_BASE_URL,
  apiKey = process.env.HORUS_API_KEY,
} = {}) {
  const key = requireApiKey(apiKey);
  const base = String(baseUrl).replace(/\/$/, "");
  const created = taskFrom(await request(base, key, "/mobile/tasks", {
    method: "POST",
    body: { prompt: "Horus mobile control plane smoke" },
  }));

  const fetched = await request(base, key, `/mobile/tasks/${created.id}`);
  assert.equal(fetched.status, 200, fetched.text);
  assert.equal(fetched.json?.status, "queued");

  const [firstEvent] = await readSseFrames(base, key, created.id, null, 1);
  assert.equal(firstEvent.type, "task.created");

  taskFrom(await request(base, key, `/mobile/tasks/${created.id}/commands`, {
    method: "POST",
    body: { command: "pause" },
  }), "paused");
  taskFrom(await request(base, key, `/mobile/tasks/${created.id}/commands`, {
    method: "POST",
    body: { command: "resume" },
  }), "queued");
  taskFrom(await request(base, key, `/mobile/tasks/${created.id}/commands`, {
    method: "POST",
    body: { command: "cancel" },
  }), "cancelled");

  const replay = await readSseFrames(base, key, created.id, firstEvent.id, 3);
  assert.deepEqual(replay.map(event => event.type), ["task.pause", "task.resume", "task.cancel"]);
  for (let index = 1; index < replay.length; index += 1) {
    assert.ok(BigInt(replay[index].id) > BigInt(replay[index - 1].id), "replay event IDs strictly increase");
  }

  console.log("mobile control plane smoke: ok");
}

if (process.argv[1]?.endsWith("smoke-mobile-control-plane.mjs")) {
  runMobileControlPlaneSmoke().catch(error => {
    console.error(`mobile control plane smoke: failed: ${error.message}`);
    process.exitCode = 1;
  });
}
