// Pure production-readiness evaluation. evaluate(env) returns a list of checks;
// the scripts/prod-check.mjs wrapper prints them and sets the exit code. Pure →
// unit-testable without a real environment.

// Common default/weak secret values that must never reach production.
const WEAK = [
  "changeme", "change-me", "changethis", "password", "passwd", "admin", "root",
  "postgres", "secret", "keycloak", "minioadmin", "test", "example", "123456",
  "nova", "token", "default",
];

// A secret looks weak if empty, short (<12), or equals/contains a known default.
export function looksWeak(value) {
  const s = String(value || "").trim().toLowerCase();
  if (!s || s.length < 12) return true;
  return WEAK.some((w) => s === w || s.includes(w));
}

export function evaluate(env = {}) {
  const rows = [];
  const add = (name, pass, detail, hard = true) => rows.push({ name, pass: !!pass, detail, hard });
  const multiUser = env.MULTI_USER === "on" || !!env.DATABASE_URL;

  add("auth", multiUser || !!env.GATEWAY_TOKEN, "GATEWAY_TOKEN set or multi-user (DATABASE_URL)");
  if (!multiUser) {
    add("token-strength", !!env.GATEWAY_TOKEN && env.GATEWAY_TOKEN.length >= 32,
      "GATEWAY_TOKEN is >= 32 chars (use `openssl rand -hex 32`)");
  }
  add("NODE_ENV", env.NODE_ENV === "production", "NODE_ENV=production");
  add("CORS", !!env.ALLOW_ORIGINS && env.ALLOW_ORIGINS !== "*", "ALLOW_ORIGINS is a fixed allowlist (not *)");

  if (multiUser) {
    add("database", !!env.DATABASE_URL, "DATABASE_URL set (multi-user persistence)");
    add("admins", !!env.ADMIN_USER_IDS, "ADMIN_USER_IDS set (at least one admin)", false);
  }

  // Default/weak infrastructure secrets (only checked when present).
  for (const [key, label] of [
    ["POSTGRES_PASSWORD", "Postgres"],
    ["KEYCLOAK_ADMIN_PASSWORD", "Keycloak admin"],
    ["MINIO_ROOT_PASSWORD", "MinIO root"],
    ["REDIS_PASSWORD", "Redis"],
  ]) {
    if (env[key] !== undefined && env[key] !== "") {
      add("secret:" + key, !looksWeak(env[key]), label + " password is strong (not default/weak)");
    }
  }

  add("ALLOW_MODELS", !!env.ALLOW_MODELS, "model allowlist set (cost/exposure control)", false);
  add("TRUST_PROXY", env.TRUST_PROXY === "1", "TRUST_PROXY=1 (behind a TLS reverse proxy)", false);
  add("HEALTH_DETAILS", env.HEALTH_DETAILS_ENABLED !== "1", "HEALTH_DETAILS_ENABLED != 1 in prod", false);
  add("rate-limit", env.RATE_MAX !== "0", "rate limiting enabled (RATE_MAX != 0)", false);
  if (env.MCP_SERVERS && /http:\/\//.test(env.MCP_SERVERS)) {
    add("mcp-tls", false, "MCP_SERVERS uses http:// — prefer https for remote MCP servers", false);
  }
  return rows;
}

// Number of failed hard (required) checks.
export function hardFailures(rows) {
  return rows.filter((r) => !r.pass && r.hard).length;
}
