import { test } from "node:test";
import assert from "node:assert/strict";
import { MobileEventBroker, formatSseEvent } from "../lib/mobile_event_broker.mjs";

test("subscribers only receive their task events", () => {
  const broker = new MobileEventBroker();
  const seen = [];
  const unsubscribe = broker.subscribe("task-1", event => seen.push(event.id));
  broker.publish({ id: "1", task_id: "task-2", type: "task.state", payload: {} });
  broker.publish({ id: "2", task_id: "task-1", type: "task.state", payload: {} });
  unsubscribe();
  broker.publish({ id: "3", task_id: "task-1", type: "task.state", payload: {} });
  assert.deepEqual(seen, ["2"]);
});

test("SSE output carries replay id and event type", () => {
  const text = formatSseEvent({ id: "42", task_id: "task-1", type: "confirmation.requested", payload: { risk_level: "R2" } });
  assert.match(text, /^id: 42\nevent: confirmation\.requested\ndata: /);
  assert.match(text, /"risk_level":"R2"/);
  assert.ok(text.endsWith("\n\n"));
});
