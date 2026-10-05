// Pure scheduling helpers for automated agent tasks. Zero-dependency and fully
// unit-testable (no DB/clock side effects beyond the `now` you pass in).
//
// Schedule spec strings (simple + unambiguous, no cron parser dependency):
//   "every:30m" | "every:6h" | "every:2d"   → fixed interval (minutes/hours/days)
//   "daily:09:00" → UTC; "daily:09:00@Europe/Istanbul" → explicit IANA zone

const UNIT_MS = { m: 60000, h: 3600000, d: 86400000 };
export const MIN_INTERVAL_MS = 60000;   // floor: never run more than once a minute

export function parseSchedule(spec) {
  const s = String(spec || "").trim();
  let m = /^every:(\d+)(m|h|d)$/.exec(s);
  if (m) {
    const ms = parseInt(m[1], 10) * UNIT_MS[m[2]];
    if (ms >= MIN_INTERVAL_MS) return { kind: "every", ms };
    return null;
  }
  m = /^daily:([01]?\d|2[0-3]):([0-5]\d)(?:@([A-Za-z0-9_+\-/]+))?$/.exec(s);
  if (m) {
    if (m[3]) { try { new Intl.DateTimeFormat('en', {timeZone:m[3]}).format(); } catch { return null; } }
    return {kind:'daily', hour:Number(m[1]), minute:Number(m[2]), ...(m[3] ? {timeZone:m[3]} : {})};
  }
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
  if (p.timeZone) {
    const fmt = new Intl.DateTimeFormat('en-GB', {timeZone:p.timeZone, hour:'2-digit', minute:'2-digit', hourCycle:'h23'});
    const target = String(p.hour).padStart(2,'0') + ':' + String(p.minute).padStart(2,'0');
    // Minute-resolution scan also handles DST gaps/repeated hours without
    // interpreting local calendar values in the host's timezone.
    for (let t = Math.floor(fromMs / 60000) * 60000 + 60000; t <= fromMs + 2 * UNIT_MS.d; t += 60000)
      if (fmt.format(t) === target) return t;
    return null;
  }
  // Legacy daily strings now have a stable, documented UTC interpretation.
  const d = new Date(fromMs);
  d.setUTCHours(p.hour, p.minute, 0, 0);
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
