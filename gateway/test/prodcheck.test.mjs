// Production-readiness evaluation tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { cspConnectSrcLooksPublicSafe, evaluate, hardFailures, looksWeak } from "../lib/prodcheck.mjs";

const find = (rows, name) => rows.find((r) => r.name === name);

test("looksWeak: empties, short, and known defaults", () => {
  assert.equal(looksWeak(""), true);
  assert.equal(looksWeak("short"), true);
  assert.equal(looksWeak("changeme123!"), true);     // contains "changeme"
  assert.equal(looksWeak("postgres"), true);
  assert.equal(looksWeak("Tr0ub4dor&3xLongEnough"), false);
});

test("evaluate: insecure single-user dev config fails hard checks", () => {
  const rows = evaluate({});
  assert.ok(hardFailures(rows) > 0);
  assert.equal(find(rows, "NODE_ENV").pass, false);
  assert.equal(find(rows, "CORS").pass, false);
  assert.equal(find(rows, "auth").pass, false);
});

test("evaluate: a hardened single-user config passes all hard checks", () => {
  const rows = evaluate({
    NODE_ENV: "production",
    GATEWAY_TOKEN: "a".repeat(40),
    ALLOW_ORIGINS: "https://nova.example.com",
    ALLOW_MODELS: "ollama/*",
    TRUST_PROXY: "1",
    CSP_CONNECT_SRC: "'self'",
  });
  assert.equal(hardFailures(rows), 0);
  assert.equal(find(rows, "token-strength").pass, true);
});

test("evaluate: short token fails token-strength (single-user)", () => {
  const rows = evaluate({ NODE_ENV: "production", GATEWAY_TOKEN: "short", ALLOW_ORIGINS: "https://x" });
  assert.equal(find(rows, "token-strength").pass, false);
  assert.ok(hardFailures(rows) > 0);
});

test("evaluate: multi-user requires DATABASE_URL; admins is a soft warning", () => {
  const rows = evaluate({ NODE_ENV: "production", ALLOW_ORIGINS: "https://x", DATABASE_URL: "postgres://u@h/db" });
  assert.equal(find(rows, "database").pass, true);
  assert.equal(find(rows, "token-strength"), undefined); // not checked in multi-user
  const admins = find(rows, "admins");
  assert.equal(admins.hard, false);
});

test("evaluate: OIDC without an audience is a soft warning", () => {
  const base = { NODE_ENV: "production", ALLOW_ORIGINS: "https://x", DATABASE_URL: "postgres://u@h/db", CSP_CONNECT_SRC: "'self'" };
  let rows = evaluate({ ...base, OIDC_ISSUER: "https://auth.example.com/realms/nova" });
  const aud = find(rows, "oidc-audience");
  assert.equal(aud.pass, false);
  assert.equal(aud.hard, false);
  assert.equal(hardFailures(rows), 0);   // advisory only — must not fail the gate

  rows = evaluate({ ...base, OIDC_JWKS_URL: "https://auth.example.com/jwks", OIDC_AUDIENCE: "nova-web" });
  assert.equal(find(rows, "oidc-audience").pass, true);
});

test("evaluate: no OIDC config means no audience row", () => {
  const rows = evaluate({ NODE_ENV: "production", ALLOW_ORIGINS: "https://x", GATEWAY_TOKEN: "a".repeat(40) });
  assert.equal(find(rows, "oidc-audience"), undefined);
});

test("evaluate: weak infra secrets are flagged when present", () => {
  const rows = evaluate({
    NODE_ENV: "production", ALLOW_ORIGINS: "https://x", DATABASE_URL: "postgres://u@h/db",
    POSTGRES_PASSWORD: "changeme", KEYCLOAK_ADMIN_PASSWORD: "S7rong-K3ycloak-Pass!",
  });
  assert.equal(find(rows, "secret:POSTGRES_PASSWORD").pass, false);
  assert.equal(find(rows, "secret:KEYCLOAK_ADMIN_PASSWORD").pass, true);
});

test("evaluate: cleartext MCP over http is a soft warning", () => {
  const rows = evaluate({ NODE_ENV: "production", ALLOW_ORIGINS: "https://x", GATEWAY_TOKEN: "a".repeat(40), MCP_SERVERS: "x=http://host/mcp" });
  const mcp = find(rows, "mcp-tls");
  assert.equal(mcp.pass, false);
  assert.equal(mcp.hard, false);
});

test("evaluate: production CSP connect-src rejects local dev origins", () => {
  assert.equal(cspConnectSrcLooksPublicSafe("'self'"), true);
  assert.equal(cspConnectSrcLooksPublicSafe("'self' https://nova.example.com"), true);
  assert.equal(cspConnectSrcLooksPublicSafe("'self' http://localhost:*"), false);
  assert.equal(cspConnectSrcLooksPublicSafe("'self' http://127.0.0.1:*"), false);
  assert.equal(cspConnectSrcLooksPublicSafe("*"), false);

  const base = { NODE_ENV: "production", ALLOW_ORIGINS: "https://x", GATEWAY_TOKEN: "a".repeat(40) };
  let rows = evaluate({ ...base, CSP_CONNECT_SRC: "'self' http://localhost:*" });
  assert.equal(find(rows, "CSP_CONNECT_SRC").pass, false);
  assert.equal(find(rows, "CSP_CONNECT_SRC").hard, true);

  rows = evaluate({ ...base, CSP_CONNECT_SRC: "'self'" });
  assert.equal(find(rows, "CSP_CONNECT_SRC").pass, true);
});

test("evaluate: Keycloak Caddy host is required when KC_HOSTNAME is set", () => {
  const base = { NODE_ENV: "production", ALLOW_ORIGINS: "https://x", GATEWAY_TOKEN: "a".repeat(40), CSP_CONNECT_SRC: "'self'", KC_HOSTNAME: "https://auth.example.com" };
  let rows = evaluate(base);
  assert.equal(find(rows, "keycloak-caddy-host").pass, false);
  assert.equal(find(rows, "keycloak-caddy-host").hard, true);

  rows = evaluate({ ...base, KC_HOSTNAME_HOST: "https://auth.example.com" });
  assert.equal(find(rows, "keycloak-caddy-host").pass, false);

  rows = evaluate({ ...base, KC_HOSTNAME_HOST: "auth.example.com" });
  assert.equal(find(rows, "keycloak-caddy-host").pass, true);
});
