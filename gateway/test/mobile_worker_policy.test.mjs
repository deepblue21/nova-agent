import { test } from "node:test";
import assert from "node:assert/strict";
import { allowedWorkerPrompts, isAllowedWorkerPrompt, parseWorkerReport } from "../lib/mobile_worker_policy.mjs";

const REPORT_ID = "11111111-1111-4111-8111-111111111111";
const LEASE_ID = "22222222-2222-4222-8222-222222222222";

test("settings policy accepts only normalized safe goals", () => {
  assert.equal(isAllowedWorkerPrompt("  Open Settings and tell me the Android version ", "settings_android_version"), true);
  assert.equal(isAllowedWorkerPrompt("Send a message", "settings_android_version"), false);
  assert.deepEqual(allowedWorkerPrompts("settings_android_version"), [
    "open settings and tell me the android version",
    "ayarlari ac ve android surumunu soyle",
  ]);
});

test("worker reports normalize only bounded safe fields", () => {
  const report = parseWorkerReport({
    lease_id: LEASE_ID,
    report_id: REPORT_ID,
    phase: "completed",
    summary: "Android version reported",
    steps: 4,
    error: "upstream details must not persist",
  });

  assert.deepEqual(report, {
    lease_id: LEASE_ID,
    report_id: REPORT_ID,
    phase: "completed",
    summary: "Android version reported",
    steps: 4,
  });
  assert.equal(Object.isFrozen(report), true);
});

test("worker reports reject unknown phases and invalid bounded fields", () => {
  assert.throws(() => parseWorkerReport({ phase: "delete_everything" }), /invalid worker phase/);
  assert.throws(() => parseWorkerReport({ phase: "completed", report_id: "not-a-uuid" }), /invalid report id/);
  assert.throws(() => parseWorkerReport({ phase: "completed", lease_id: "not-a-uuid" }), /invalid lease id/);
  assert.throws(() => parseWorkerReport({ phase: "completed", summary: "a".repeat(1001) }), /invalid worker summary/);
  assert.throws(() => parseWorkerReport({ phase: "completed", steps: 9 }), /invalid worker steps/);
  assert.throws(() => parseWorkerReport({ phase: "failed", error_code: "unsafe_error" }), /invalid worker error code/);
});

test("worker report summary limits count Unicode code points", () => {
  const report = parseWorkerReport({ phase: "running", summary: "x".repeat(999) + "\u{1F680}" });
  assert.equal(Array.from(report.summary).length, 1000);
});
