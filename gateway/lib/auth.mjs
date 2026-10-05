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
    "SELECT id, user_id, token_hash, revoked_at, scopes FROM api_keys WHERE prefix = $1", [prefix]);
  for (const k of rows) {
    if (!k.revoked_at && hashEquals(k.token_hash, sha256hex(token))) {
      q("UPDATE api_keys SET last_used_at = now() WHERE id = $1", [k.id]).catch(() => {});
      return { userId: k.user_id, via: "api_key", scopes: k.scopes };
    }
  }
  return null;
}

async function userFromJwt(token) {
  if (!JWKS || !ISSUER || !AUDIENCE) return null;
  const { payload } = await jwtVerify(token, JWKS, {
    issuer: ISSUER,
    audience: AUDIENCE,
  });
  return resolveOidcUser(payload);
}

// Only called after JWT signature, issuer and audience verification.
export async function resolveOidcUser(payload, query = q) {
  if (typeof payload.sub !== "string" || !payload.sub) return null;
  const email = payload.email_verified === true && typeof payload.email === "string"
    && payload.email.trim().length <= 320 ? payload.email.trim() || null : null;
  const existing = (await query("SELECT id, email FROM users WHERE oidc_sub=$1", [payload.sub])).rows[0];
  if (existing) {
    if (email && email !== existing.email) {
      // A verified email update must never merge two existing accounts.
      try {
        const updated = await query(`UPDATE users SET email=$2 WHERE id=$1
          AND NOT EXISTS (SELECT 1 FROM users WHERE lower(email)=lower($2) AND id<>$1) RETURNING email`,[existing.id,email]);
        if (updated.rows[0]) existing.email=updated.rows[0].email;
      } catch (e) { if (e.code !== '23505') throw e; }
    }
    return { userId: existing.id, via: "jwt", email: existing.email };
  }
  const name = String(payload.name || "").trim().slice(0, 200) || null;
  let rows = email ? (await query(
    `UPDATE users SET oidc_sub = $2
      WHERE oidc_sub IS NULL AND lower(email) = lower($1)
      RETURNING id`,
    [email, payload.sub])).rows : [];
  if (!rows.length) {
    rows = (await query(
      `INSERT INTO users (email, name, oidc_sub)
         VALUES ($1, $2, $3)
       ON CONFLICT (oidc_sub) DO UPDATE SET oidc_sub = EXCLUDED.oidc_sub
       RETURNING id`,
      [email, name, String(payload.sub)])).rows;
  }
  return { userId: rows[0].id, via: "jwt", email };
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
