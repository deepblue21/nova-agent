import assert from "node:assert/strict";
import test from "node:test";
import { parseSseFrames, requireApiKey } from "./smoke-mobile-control-plane.mjs";

test("smoke requires an API key without echoing it", () => {
  assert.throws(() => requireApiKey(""), /HORUS_API_KEY is required/);
  assert.equal(requireApiKey("nova_secret"), "nova_secret");
});

test("SSE frames retain IDs and event payloads", () => {
  const frames = parseSseFrames(
    "id: 900719925474099312345\nevent: task.pause\ndata: {\"id\":\"900719925474099312345\"}\n\n",
  );

  assert.deepEqual(frames, [{ id: "900719925474099312345", type: "task.pause", data: { id: "900719925474099312345" } }]);
});
