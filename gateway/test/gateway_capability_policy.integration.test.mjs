import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { once } from "node:events";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const GATEWAY_DIR = resolve(HERE, "..");

async function listen(server) {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return server.address().port;
}

async function freePort() {
  const server = createServer();
  const port = await listen(server);
  await new Promise((resolveClose) => server.close(resolveClose));
  return port;
}

async function waitForGateway(url, child) {
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (child.exitCode != null) throw new Error(`gateway erken kapandı: ${child.exitCode}`);
    try {
      const response = await fetch(`${url}/health`);
      if (response.ok) return;
    } catch {}
    await new Promise((resolveWait) => setTimeout(resolveWait, 50));
  }
  throw new Error("gateway zamanında hazır olmadı");
}

test("chat endpoint: tools:false modelde canlı-veri auto-agent araç şeması göndermez", async (t) => {
  let showCalls = 0;
  let toolPayloadCalls = 0;
  const directThinkValues = [];
  const ollama = createServer(async (req, res) => {
    let raw = "";
    for await (const chunk of req) raw += chunk;
    const body = raw ? JSON.parse(raw) : {};

    if (req.url === "/api/show") {
      showCalls += 1;
      res.setHeader("Content-Type", "application/json");
      return res.end(JSON.stringify({ capabilities: ["completion"] }));
    }
    if (req.url === "/api/chat") {
      if (Array.isArray(body.tools) && body.tools.length) {
        toolPayloadCalls += 1;
        res.statusCode = 400;
        return res.end("model does not support tools");
      }
      directThinkValues.push(body.think);
      res.setHeader("Content-Type", "application/x-ndjson");
      return res.end(`${JSON.stringify({ message: { content: "plain ok" }, done: true })}\n`);
    }
    if (req.url === "/api/tags") {
      res.setHeader("Content-Type", "application/json");
      return res.end(JSON.stringify({ models: [{ name: "plain:latest" }] }));
    }
    res.statusCode = 404;
    res.end();
  });
  const ollamaPort = await listen(ollama);
  t.after(() => new Promise((resolveClose) => ollama.close(resolveClose)));

  const gatewayPort = await freePort();
  const token = "integration-capability-token";
  const child = spawn(process.execPath, ["gateway.mjs"], {
    cwd: GATEWAY_DIR,
    env: {
      ...process.env,
      PORT: String(gatewayPort),
      OLLAMA_URL: `http://127.0.0.1:${ollamaPort}`,
      DEFAULT_MODEL: "ollama/plain:latest",
      GATEWAY_TOKEN: token,
      DATABASE_URL: "",
      MULTI_USER: "0",
      RATE_MAX: "0",
      AUTO_AGENT_ENABLED: "1",
      REQ_TIMEOUT_MS: "5000",
      MAX_RETRIES: "0",
      SCHEDULER_ENABLED: "0",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stderr = "";
  child.stderr.on("data", (chunk) => { stderr += chunk; });
  t.after(() => {
    if (child.exitCode == null) child.kill();
  });

  const base = `http://127.0.0.1:${gatewayPort}`;
  await waitForGateway(base, child);
  const response = await fetch(`${base}/v1/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      model: "ollama/plain:latest",
      stream: false,
      messages: [{ role: "user", content: "Bugün hava nasıl?" }],
    }),
  });
  const payload = await response.json();

  assert.equal(response.status, 200, stderr || JSON.stringify(payload));
  assert.equal(showCalls, 1, "gateway seçili modeli /api/show ile ölçmeli");
  assert.equal(toolPayloadCalls, 0, "tools:false modele araç şeması hiç gönderilmemeli");
  assert.equal(response.headers.get("x-nova-auto-agent"), null);
  assert.match(payload.choices[0].message.content, /araç çağırmayı desteklemiyor/i);
  assert.match(payload.choices[0].message.content, /plain ok/);

  const thinkingResponse = await fetch(`${base}/v1/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      model: "ollama/plain:latest",
      stream: false,
      think: true,
      messages: [{ role: "user", content: "Kısa bir selam ver." }],
    }),
  });
  const thinkingPayload = await thinkingResponse.json();
  assert.equal(thinkingResponse.status, 200, stderr || JSON.stringify(thinkingPayload));
  assert.equal(showCalls, 2);
  assert.deepEqual(directThinkValues, [false, false]);
  assert.match(thinkingPayload.choices[0].message.content, /Düşün.*desteklemiyor/i);

  const imageThinkingResponse = await fetch(`${base}/v1/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      model: "ollama/plain:latest",
      stream: false,
      think: true,
      messages: [{
        role: "user",
        content: [
          { type: "text", text: "Bu görseli tek cümleyle açıkla." },
          { type: "image_url", image_url: { url: "data:image/png;base64,AQID" } },
        ],
      }],
    }),
  });
  const imageThinkingPayload = await imageThinkingResponse.json();
  assert.equal(imageThinkingResponse.status, 200, stderr || JSON.stringify(imageThinkingPayload));
  assert.equal(showCalls, 3, "görselli istek de thinking yeteneğini ölçmeli");
  assert.deepEqual(directThinkValues, [false, false, false]);
  assert.match(imageThinkingPayload.choices[0].message.content, /Düşün.*desteklemiyor/i);
});
