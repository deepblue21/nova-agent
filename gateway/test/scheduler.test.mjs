// Pure scheduler tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseSchedule, isValidSchedule, nextRunAt, dueTasks, describeSchedule, MIN_INTERVAL_MS } from "../lib/scheduler.mjs";

test("parseSchedule: every + daily + invalid", () => {
  assert.deepEqual(parseSchedule("every:30m"), { kind: "every", ms: 1800000 });
  assert.deepEqual(parseSchedule("every:6h"), { kind: "every", ms: 21600000 });
  assert.deepEqual(parseSchedule("every:2d"), { kind: "every", ms: 172800000 });
  assert.deepEqual(parseSchedule("daily:09:05"), { kind: "daily", hour: 9, minute: 5 });
  assert.equal(parseSchedule("every:0m"), null);          // below floor
  assert.equal(parseSchedule("every:30s"), null);         // unsupported unit
  assert.equal(parseSchedule("daily:25:00"), null);       // bad hour
  assert.equal(parseSchedule("daily:12:60"), null);       // bad minute
  assert.equal(parseSchedule("garbage"), null);
  assert.ok(isValidSchedule("every:1d") && !isValidSchedule("weekly:1"));
  assert.ok(MIN_INTERVAL_MS === 60000);
});

test("nextRunAt: interval adds, daily rolls forward, bad → null", () => {
  const t0 = Date.UTC(2026, 0, 1, 12, 0, 0);
  assert.equal(nextRunAt("every:1h", t0), t0 + 3600000);
  assert.equal(nextRunAt("every:30m", t0), t0 + 1800000);
  const n = nextRunAt("daily:00:00", t0);                 // local-time independent bounds
  assert.ok(n > t0 && n <= t0 + 86400000);
  assert.equal(nextRunAt("nope", t0), null);
});

test("dueTasks: only enabled tasks with past nextRunAt", () => {
  const now = 1000;
  const tasks = [
    { id: "a", enabled: true,  nextRunAt: 500 },
    { id: "b", enabled: true,  nextRunAt: 1500 },
    { id: "c", enabled: false, nextRunAt: 100 },
    { id: "d", enabled: true,  nextRunAt: 1000 },
  ];
  assert.deepEqual(dueTasks(tasks, now).map((t) => t.id), ["a", "d"]);
  assert.deepEqual(dueTasks([], now), []);
  assert.deepEqual(dueTasks(null, now), []);
});

test("describeSchedule: human labels", () => {
  assert.equal(describeSchedule("every:30m"), "her 30 dakikada bir");
  assert.equal(describeSchedule("every:6h"), "her 6 saatte bir");
  assert.equal(describeSchedule("every:2d"), "her 2 günde bir");
  assert.equal(describeSchedule("daily:09:05"), "her gün 09:05");
  assert.equal(describeSchedule("bad"), "geçersiz");
});
