import { test } from "node:test";
import assert from "node:assert/strict";
import { createWorkerAuth } from "../lib/mobile_worker_auth.mjs";

function call(auth, authorization) {
  const result = { nextCalled: false };
  const res = {
    status(status) { result.status = status; return this; },
    json(body) { result.body = body; return this; },
    end() { result.ended = true; return this; },
  };
  auth({ get: name => name === "Authorization" ? authorization : undefined }, res, () => { result.nextCalled = true; });
  return result;
}

test("worker auth only accepts the configured dedicated bearer", () => {
  const auth = createWorkerAuth({ enabled: true, token: "dedicated-worker-test-token" });
  const denied = call(auth, "Bearer user-api-key");
  const allowed = call(auth, "Bearer dedicated-worker-test-token");

  assert.equal(denied.status, 401);
  assert.deepEqual(denied.body, { error: "worker unauthorized" });
  assert.equal(allowed.nextCalled, true);
});

test("worker auth hides disabled routes and denies absent configuration", () => {
  const disabled = call(createWorkerAuth({ enabled: false, token: "dedicated-worker-test-token" }), "Bearer dedicated-worker-test-token");
  const unconfigured = call(createWorkerAuth({ enabled: true, token: "" }), "Bearer dedicated-worker-test-token");

  assert.equal(disabled.status, 404);
  assert.equal(disabled.ended, true);
  assert.equal(unconfigured.status, 401);
});
