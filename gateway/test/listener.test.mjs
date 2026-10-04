import { test } from "node:test";
import assert from "node:assert/strict";
import { fork } from "node:child_process";
import { once } from "node:events";

async function start(t, overrides = {}) {
  const child = fork(new URL("../test-support/listener-probe.mjs", import.meta.url), [], {
    silent: true,
    env: { ...process.env, NODE_ENV: "development", PORT: "0", GATEWAY_BIND: "",
      GATEWAY_TOKEN: "", DATABASE_URL: "", REDIS_URL: "", MULTI_USER: "0",
      SCHEDULER_ENABLED: "0", MOBILE_WORKER_ENABLED: "0", ...overrides },
  });
  t.after(async () => {
    if (child.exitCode === null && child.signalCode === null) {
      const stopped = once(child, "exit");
      child.kill();
      await stopped;
    }
  });
  child.stdout.resume();
  let stderr = "";
  child.stderr.on("data", chunk => { stderr += chunk; });
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("Gateway startup timed out")), 15000);
    child.once("message", address => { clearTimeout(timer); resolve({ address }); });
    child.once("error", error => { clearTimeout(timer); reject(error); });
    child.once("exit", code => { clearTimeout(timer); resolve({ code, stderr }); });
  });
}

test("empty bind defaults to loopback even without a token", async t => {
  const { address } = await start(t);
  assert.equal(address?.address, "127.0.0.1");
  assert.equal((await fetch(`http://127.0.0.1:${address.port}/health`)).status, 200);
});

test("explicit IPv6 loopback is honored", async t => {
  const { address } = await start(t, { GATEWAY_BIND: "::1" });
  assert.equal(address?.address, "::1");
});

test("authenticated LAN binding preserves auth enforcement", async t => {
  const token = "listener-test-token-with-32-characters";
  const { address } = await start(t, { GATEWAY_BIND: "0.0.0.0", GATEWAY_TOKEN: token });
  assert.equal(address?.address, "0.0.0.0");
  const url = `http://127.0.0.1:${address.port}/v1/models`;
  assert.equal((await fetch(url)).status, 401);
  assert.equal((await fetch(url, { headers: { Authorization: `Bearer ${token}` } })).status, 200);
});

for (const bind of ["0.0.0.0", "::", "192.0.2.1"]) {
  test(`unauthenticated network bind ${bind} fails before listening`, async t => {
    const result = await start(t, { GATEWAY_BIND: bind });
    assert.equal(result.code, 1);
    assert.match(result.stderr, /GATEWAY_BIND.*authentication/);
  });
}
