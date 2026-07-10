// Principal resolution: accept EITHER a per-user NOVA API key OR an OIDC JWT.
// Sets req.principal = { userId, via, email? } or responds 401.
import { createRemoteJWKSet, jwtVerify } from "jose";
import { q } from "./db.mjs";
import { parseBearer, isApiKey, keyPrefix, sha256hex, hashEquals } from "./keys.mjs";

const ISSUER   = process.env.OIDC_ISSUER   || "";
const AUDIENCE = process.env.OIDC_AUDIENCE || "";
const JWKS = process.env.OIDC_JWKS_URL ? createRemoteJWKSet(new URL(process.env.OIDC_JWKS_URL)) : null;

async function userFromApiKey(token) {
  const prefix = keyPrefix(token);
  if (!prefix) return null;
  const { rows } = await q(
    "SELECT id, user_id, token_hash, revoked_at FROM api_keys WHERE prefix = $1", [prefix]);
  for (const k of rows) {
    if (!k.revoked_at && hashEquals(k.token_hash, sha256hex(token))) {
      q("UPDATE api_keys SET last_used_at = now() WHERE id = $1", [k.id]).catch(() => {});
      return { userId: k.user_id, via: "api_key" };
    }
  }
  return null;
}

async function userFromJwt(token) {
  if (!JWKS) return null;
  const { payload } = await jwtVerify(token, JWKS, {
    issuer:   ISSUER   || undefined,
    audience: AUDIENCE || undefined,
  });
  if (!payload.sub) return null;
  // Upsert by stable IdP subject. Also LINK pre-existing accounts created
  // before OIDC (bootstrap CLI: same email, oidc_sub IS NULL) — otherwise the
  // email unique index rejects the insert and the login 401s.
  const email = String(payload.email || "").trim().slice(0, 320);
  const name = String(payload.name || "").trim().slice(0, 200) || null;
  let rows = (await q(
    `UPDATE users SET oidc_sub = $2, email = COALESCE(NULLIF($1,''), email)
      WHERE oidc_sub = $2 OR (oidc_sub IS NULL AND $1 <> '' AND lower(email) = lower($1))
      RETURNING id`,
    [email, payload.sub])).rows;
  if (!rows.length) {
    rows = (await q(
      `INSERT INTO users (email, name, oidc_sub)
         VALUES ($1, $2, $3)
       ON CONFLICT (oidc_sub) DO UPDATE SET email = EXCLUDED.email
       RETURNING id`,
      [email, name, String(payload.sub)])).rows;
  }
  return { userId: rows[0].id, via: "jwt", email: payload.email };
}

// Express middleware factory. Mount AFTER /health so liveness stays public.
export function principal() {
  return async (req, res, next) => {
    try {
      const token = parseBearer(req.headers.authorization);
      if (!token) return res.status(401).json({ error: "missing bearer token" });
      const p = isApiKey(token) ? await userFromApiKey(token) : await userFromJwt(token);
      if (!p) return res.status(401).json({ error: "invalid credentials" });
      req.principal = p;
      next();
    } catch {
      res.status(401).json({ error: "unauthorized" });
    }
  };
}
