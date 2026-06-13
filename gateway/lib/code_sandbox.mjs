import { getQuickJS, shouldInterruptAfterDeadline } from "quickjs-emscripten";

const DEFAULT_TIMEOUT_MS = 1000;
const DEFAULT_MEMORY_MB = 16;
const DEFAULT_MAX_CODE_CHARS = 6000;
const DEFAULT_MAX_OUTPUT_CHARS = 4000;

function intEnv(name, fallback, min, max) {
  const n = parseInt(process.env[name] || "", 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}

function truncate(s, max) {
  const text = String(s || "");
  return text.length > max ? text.slice(0, max) + "\n... [truncated]" : text;
}

function wrapCode(code, input) {
  return `
(() => {
  "use strict";
  const input = ${JSON.stringify(input ?? null)};
  const __logs = [];
  const __format = (value) => {
    if (typeof value === "undefined") return "undefined";
    if (typeof value === "string") return value;
    if (typeof value === "bigint") return value.toString() + "n";
    try {
      const json = JSON.stringify(value);
      return typeof json === "undefined" ? String(value) : json;
    } catch (e) {
      try { return String(value); } catch (err) { return "[unprintable]"; }
    }
  };
  const console = Object.freeze({
    log: (...args) => { if (__logs.length < 50) __logs.push(args.map(__format).join(" ")); },
    error: (...args) => { if (__logs.length < 50) __logs.push(args.map(__format).join(" ")); },
  });
  const result = (() => {
${String(code || "")}
  })();
  return JSON.stringify({ result: __format(result), logs: __logs });
})()
`;
}

export function codeToolEnabled() {
  return /^(1|true|yes|on)$/i.test(process.env.CODE_TOOL_ENABLED || "");
}

export async function runJavaScriptSandbox({ code, input, timeout_ms } = {}) {
  const maxCodeChars = intEnv("CODE_TOOL_MAX_CODE_CHARS", DEFAULT_MAX_CODE_CHARS, 200, 50000);
  const maxOutputChars = intEnv("CODE_TOOL_MAX_OUTPUT_CHARS", DEFAULT_MAX_OUTPUT_CHARS, 500, 50000);
  const timeout = Math.max(50, Math.min(
    Number(timeout_ms) || intEnv("CODE_TOOL_TIMEOUT_MS", DEFAULT_TIMEOUT_MS, 50, 5000),
    intEnv("CODE_TOOL_TIMEOUT_MS", DEFAULT_TIMEOUT_MS, 50, 5000),
  ));
  const memoryBytes = intEnv("CODE_TOOL_MEMORY_MB", DEFAULT_MEMORY_MB, 4, 128) * 1024 * 1024;
  const source = String(code || "");

  if (!source.trim()) throw new Error("code gerekli");
  if (source.length > maxCodeChars) throw new Error("code çok uzun (max " + maxCodeChars + " karakter)");

  const QuickJS = await getQuickJS();
  let raw;
  try {
    raw = QuickJS.evalCode(wrapCode(source, input), {
      shouldInterrupt: shouldInterruptAfterDeadline(Date.now() + timeout),
      memoryLimitBytes: memoryBytes,
    });
  } catch (e) {
    throw new Error("sandbox: " + (e.message || e));
  }

  let parsed;
  try { parsed = JSON.parse(String(raw)); }
  catch (e) { parsed = { result: String(raw), logs: [] }; }

  const logs = Array.isArray(parsed.logs) ? parsed.logs.map(s => truncate(s, maxOutputChars)) : [];
  const result = truncate(parsed.result, maxOutputChars);
  const output = [
    logs.length ? "stdout:\n" + logs.join("\n") : "",
    "result:\n" + result,
  ].filter(Boolean).join("\n\n");

  return {
    text: truncate(output, maxOutputChars),
    result,
    logs,
    timeout_ms: timeout,
    memory_mb: Math.round(memoryBytes / 1024 / 1024),
  };
}
