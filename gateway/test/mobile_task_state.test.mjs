import { test } from "node:test";
import assert from "node:assert/strict";
import { applyTaskCommand, isTerminal } from "../lib/mobile_task_state.mjs";

test("pause and resume preserve a controllable run", () => {
  assert.equal(applyTaskCommand("executing", "pause"), "paused");
  assert.equal(applyTaskCommand("paused", "resume"), "queued");
});

test("cancel ends any non-terminal run", () => {
  assert.equal(applyTaskCommand("queued", "cancel"), "cancelled");
  assert.equal(applyTaskCommand("waiting_for_confirmation", "cancel"), "cancelled");
});

test("steer does not change status", () => {
  assert.equal(applyTaskCommand("paused", "steer"), "paused");
  assert.equal(applyTaskCommand("executing", "steer"), "executing");
});

test("invalid transitions throw", () => {
  assert.throws(() => applyTaskCommand("completed", "resume"), /terminal/);
  assert.throws(() => applyTaskCommand("queued", "resume"), /cannot resume/);
  assert.throws(() => applyTaskCommand("queued", "unknown"), /unknown command/);
});

test("terminal detection is explicit", () => {
  assert.equal(isTerminal("completed"), true);
  assert.equal(isTerminal("failed"), true);
  assert.equal(isTerminal("cancelled"), true);
  assert.equal(isTerminal("paused"), false);
});
