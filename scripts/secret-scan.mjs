#!/usr/bin/env node
// NOVA secret scanner — self-contained, zero-dependency.
//
// Scans tracked files (git ls-files) for high-confidence secret patterns
// (provider API keys, cloud credentials, private keys, tokens) plus a
// generic "secret = <high-entropy value>" heuristic. Known development
// placeholders are allowlisted so the clean repo passes.
//
// Usage:
//   node scripts/secret-scan.mjs          # scan tracked files (CI default)
//   node scripts/secret-scan.mjs --all    # also scan untracked, non-ignored files
//   node scripts/secret-scan.mjs --help
//
// Exit code: 0 = clean, 1 = potential secret(s) found, 2 = usage/run error.

import { execFileSync } from "node:child_process";
import { readFileSync, statSync, readdirSync } from "node:fs";
import { join, relative, sep } from "node:path";

const ROOT = process.cwd();
const args = new Set(process.argv.slice(2));
if (args.has("--help") || args.has("-h")) {
  console.log("Usage: node scripts/secret-scan.mjs [--all]\n  --all  also scan untracked (non git-ignored) files");
  process.exit(0);
}
const SCAN_ALL = args.has("--all");

// ── exclusions ────────────────────────────────────────────────────────────
const SKIP_DIR = new Set(["node_modules", ".git", "dist", "build", ".gradle", ".idea", "coverage"]);
const SKIP_FILE = new Set(["package-lock.json", "yarn.lock", "pnpm-lock.yaml"]);
const SKIP_EXT = new Set([
  "png", "jpg", "jpeg", "gif", "webp", "ico", "svg", "bmp", "pdf",
  "zip", "tar", "gz", "tgz", "jar", "war", "keystore", "jks",
  "woff", "woff2", "ttf", "otf", "eot", "mp3", "wav", "ogg", "mp4", "mov",
  "wasm", "bin", "lock", "map", "min.js",
]);
// Files that legitimately contain placeholders/examples or the patterns themselves.
const SKIP_PATH_RE = [
  /(^|\/)scripts\/secret-scan\.mjs$/,
  /\.example($|\.)/,            // .env.example, secret.example.yaml, ...
  /(^|\/)\.env\.example$/,
];

// Lines containing any of these (case-insensitive) are treated as safe
// development placeholders, not real secrets.
const ALLOW_SUBSTR = [
  "example", "changeme", "change-me", "change_me", "placeholder", "dummy",
  "your-", "your_", "yourkey", "redacted", "<your", "<token", "<secret",
  "randombytes", "process.env",
  "import.meta.env", "0123456789", "abcdef0123", "deadbeef", "xxxxxxxx",
  "0000000000", "1234567890", "sample", "fake", "test-token", "notarealkey",
  "replace-with", "replace_me", "insert-your", "<api", "<key", "******",
];

// ── patterns ──────────────────────────────────────────────────────────────
// High-confidence: a match is almost certainly a real credential.
const HIGH = [
  ["private-key", /-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----/],
  ["anthropic-key", /\bsk-ant-[A-Za-z0-9_-]{20,}/],
  ["openai-project-key", /\bsk-proj-[A-Za-z0-9_-]{20,}/],
  ["openai-key", /\bsk-[A-Za-z0-9]{32,}\b/],
  ["aws-access-key-id", /\b(?:AKIA|ASIA|AGPA|AROA|AIDA)[0-9A-Z]{16}\b/],
  ["gcp-api-key", /\bAIza[0-9A-Za-z_-]{35}\b/],
  ["stripe-secret", /\b(?:sk|rk)_live_[0-9A-Za-z]{20,}\b/],
  ["slack-token", /\bxox[abprs]-[0-9A-Za-z-]{10,}/],
  ["github-token", /\bgh[posru]_[0-9A-Za-z]{36,}\b/],
  ["github-pat", /\bgithub_pat_[0-9A-Za-z_]{40,}\b/],
  ["gitlab-pat", /\bglpat-[0-9A-Za-z_-]{20,}\b/],
  ["google-oauth", /\b[0-9]+-[0-9a-z]{32}\.apps\.googleusercontent\.com/],
];

// Generic assignment heuristic: SECRET-ish name = long opaque value.
const ASSIGN_RE =
  /(?:pass(?:word|wd)?|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret|auth[_-]?token|bearer)["']?\s*[:=]\s*["']([^"'\s]{16,})["']/i;

// Skip generic-assignment matches inside test fixtures / docs (examples live there).
const ASSIGN_SKIP_PATH_RE = /(^|\/)(test|tests|__tests__|__mocks__)\//i;

function looksAllowlisted(line) {
  const l = line.toLowerCase();
  return ALLOW_SUBSTR.some((s) => l.includes(s));
}

// Shannon entropy (bits/char) — used to suppress low-entropy assignment hits.
function entropy(str) {
  const freq = Object.create(null);
  for (const ch of str) freq[ch] = (freq[ch] || 0) + 1;
  let h = 0;
  const n = str.length;
  for (const k in freq) {
    const p = freq[k] / n;
    h -= p * Math.log2(p);
  }
  return h;
}

function listTrackedFiles() {
  try {
    const out = execFileSync("git", SCAN_ALL
      ? ["ls-files", "--cached", "--others", "--exclude-standard"]
      : ["ls-files", "--cached"], { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
    return out.split("\n").map((s) => s.trim()).filter(Boolean);
  } catch {
    return null; // git not available → fall back to walk
  }
}

function walk(dir, acc) {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIR.has(name)) continue;
    const abs = join(dir, name);
    let st;
    try { st = statSync(abs); } catch { continue; }
    if (st.isDirectory()) walk(abs, acc);
    else acc.push(relative(ROOT, abs).split(sep).join("/"));
  }
  return acc;
}

function extOf(path) {
  const base = path.split("/").pop() || "";
  if (base.endsWith(".min.js")) return "min.js";
  const dot = base.lastIndexOf(".");
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : "";
}

function shouldSkip(path) {
  const base = path.split("/").pop() || "";
  if (SKIP_FILE.has(base)) return true;
  if (SKIP_EXT.has(extOf(path))) return true;
  if (path.split("/").some((p) => SKIP_DIR.has(p))) return true;
  if (SKIP_PATH_RE.some((re) => re.test(path))) return true;
  return false;
}

function redact(s) {
  const str = String(s);
  if (str.length <= 8) return str[0] + "…";
  return str.slice(0, 4) + "…" + str.slice(-2) + ` (len ${str.length})`;
}

// ── scan ──────────────────────────────────────────────────────────────────
let files = listTrackedFiles();
const usedGit = files !== null;
if (!files) files = walk(ROOT, []);
files = files.filter((f) => !shouldSkip(f));

const findings = [];
for (const rel of files) {
  let text;
  try {
    const buf = readFileSync(join(ROOT, rel));
    if (buf.includes(0)) continue; // binary
    text = buf.toString("utf8");
  } catch { continue; }
  if (!text) continue;

  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.length > 4000) continue; // skip giant minified-ish lines
    if (looksAllowlisted(line)) continue;

    for (const [name, re] of HIGH) {
      const m = line.match(re);
      if (m) findings.push({ rel, ln: i + 1, rule: name, hit: redact(m[0]) });
    }

    if (!ASSIGN_SKIP_PATH_RE.test(rel)) {
      const m = line.match(ASSIGN_RE);
      if (m && m[1]) {
        const val = m[1];
        // Suppress obvious non-secrets: urls, templating, low entropy.
        const isTemplate = /[${}]/.test(val) || val.includes("://");
        if (!isTemplate && entropy(val) >= 3.2) {
          findings.push({ rel, ln: i + 1, rule: "generic-secret-assignment", hit: redact(val) });
        }
      }
    }
  }
}

// ── report ────────────────────────────────────────────────────────────────
const mode = usedGit ? (SCAN_ALL ? "tracked+untracked" : "tracked") : "filesystem-walk";
console.log(`secret-scan: scanned ${files.length} files (${mode})`);
if (!findings.length) {
  console.log("✓ no secrets found");
  process.exit(0);
}
console.error(`\n✗ ${findings.length} potential secret(s) found:\n`);
for (const f of findings) console.error(`  ${f.rel}:${f.ln}  [${f.rule}]  ${f.hit}`);
console.error("\nIf a finding is a false positive, add a placeholder marker to the line");
console.error("(e.g. 'example', 'change-me') or extend ALLOW_SUBSTR in scripts/secret-scan.mjs.");
process.exit(1);
