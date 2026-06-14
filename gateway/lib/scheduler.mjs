// Pure scheduling helpers for automated agent tasks. Zero-dependency and fully
// unit-testable (no DB/clock side effects beyond the `now` you pass in).
//
// Schedule spec strings (simple + unambiguous, no cron parser dependency):
//   "every:30m" | "every:6h" | "every:2d"   → fixed interval (minutes/hours/days)
//   "daily:09:00"                            → every day at HH:MM (server local time)

const UNIT_MS = { m: 60000, h: 3600000, d: 86400000 };
export const MIN_INTERVAL_MS = 60000;   // floor: never run more than once a minute

export function parseSchedule(spec) {
  const s = String(spec || "").trim().toLowerCase();
  let m = /^every:(\d+)(m|h|d)$/.exec(s);
  if (m) {
    const ms = parseInt(m[1], 10) * UNIT_MS[m[2]];
    if (ms >= MIN_INTERVAL_MS) return { kind: "every", ms };
    return null;
  }
  m = /^daily:([01]?\d|2[0-3]):([0-5]\d)$/.exec(s);
  if (m) return { kind: "daily", hour: parseInt(m[1], 10), minute: parseInt(m[2], 10) };
  return null;
}

export function isValidSchedule(spec) {
  return parseSchedule(spec) !== null;
}

// Next run timestamp (ms) strictly after `fromMs`. Returns null for bad specs.
export function nextRunAt(spec, fromMs = Date.now()) {
  const p = parseSchedule(spec);
  if (!p) return null;
  if (p.kind === "every") return fromMs + p.ms;
  // daily: next occurrence of HH:MM (local) strictly after fromMs
  const d = new Date(fromMs);
  d.setHours(p.hour, p.minute, 0, 0);
  let t = d.getTime();
  if (t <= fromMs) t += UNIT_MS.d;
  return t;
}

// Which tasks are due now: enabled and nextRunAt in the past.
export function dueTasks(tasks, nowMs = Date.now()) {
  return (tasks || []).filter(
    (t) => t && t.enabled && typeof t.nextRunAt === "number" && t.nextRunAt <= nowMs,
  );
}

// Human-readable label for the UI.
export function describeSchedule(spec) {
  const p = parseSchedule(spec);
  if (!p) return "geçersiz";
  if (p.kind === "every") {
    if (p.ms % UNIT_MS.d === 0) return "her " + p.ms / UNIT_MS.d + " günde bir";
    if (p.ms % UNIT_MS.h === 0) return "her " + p.ms / UNIT_MS.h + " saatte bir";
    return "her " + p.ms / UNIT_MS.m + " dakikada bir";
  }
  return "her gün " + String(p.hour).padStart(2, "0") + ":" + String(p.minute).padStart(2, "0");
}
