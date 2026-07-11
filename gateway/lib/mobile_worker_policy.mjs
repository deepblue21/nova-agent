export const WORKER_PHASES = new Set([
  "observing",
  "running",
  "completed",
  "failed",
  "waiting_for_device",
  "waiting_for_compute",
]);

const WORKER_ERROR_CODES = new Set([
  "device_unavailable",
  "compute_unavailable",
  "execution_limit",
  "execution_failed",
]);

const SETTINGS_ANDROID_VERSION_PROMPTS = Object.freeze([
  "open settings and tell me the android version",
  "ayarlari ac ve android surumunu soyle",
]);

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function normalizeWorkerPrompt(value) {
  return String(value ?? "").trim().replace(/\s+/g, " ").toLocaleLowerCase("en-US");
}

export function allowedWorkerPrompts(policy) {
  if (policy !== "settings_android_version") return [];
  return SETTINGS_ANDROID_VERSION_PROMPTS;
}

export function isAllowedWorkerPrompt(prompt, policy) {
  return allowedWorkerPrompts(policy).includes(normalizeWorkerPrompt(prompt));
}

function parseOptionalUuid(value, name) {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || !UUID_PATTERN.test(value)) throw new Error(`invalid ${name}`);
  return value.toLowerCase();
}

function parseSummary(value) {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || Array.from(value).length > 1000) throw new Error("invalid worker summary");
  return value.trim();
}

function parseSteps(value) {
  if (value === undefined) return undefined;
  if (!Number.isInteger(value) || value < 0 || value > 8) throw new Error("invalid worker steps");
  return value;
}

function parseErrorCode(value) {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || !WORKER_ERROR_CODES.has(value)) throw new Error("invalid worker error code");
  return value;
}

export function parseWorkerReport(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid worker report");
  if (typeof input.phase !== "string" || !WORKER_PHASES.has(input.phase)) throw new Error("invalid worker phase");

  const report = { phase: input.phase };
  for (const [name, value] of [
    ["lease_id", parseOptionalUuid(input.lease_id, "lease id")],
    ["report_id", parseOptionalUuid(input.report_id, "report id")],
    ["summary", parseSummary(input.summary)],
    ["steps", parseSteps(input.steps)],
    ["error_code", parseErrorCode(input.error_code)],
  ]) {
    if (value !== undefined) report[name] = value;
  }
  return Object.freeze(report);
}
