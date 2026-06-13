// Per-user quota enforcement + usage metering.
import { q, withTx } from "./db.mjs";
import { estimateCostMicros, approxTokens } from "./pricing.mjs";

// { allowed, used, limit }. No quota row => unlimited. Expired window => treated as reset.
export async function checkQuota(userId) {
  const { rows } = await q(
    "SELECT limit_micros, used_micros, resets_at FROM quotas WHERE subject_id = $1", [userId]);
  if (!rows.length) return { allowed: true, used: 0, limit: null };
  const Q = rows[0];
  if (new Date(Q.resets_at) < new Date())
    return { allowed: true, used: 0, limit: Number(Q.limit_micros) };
  return {
    allowed: Number(Q.used_micros) < Number(Q.limit_micros),
    used:    Number(Q.used_micros),
    limit:   Number(Q.limit_micros),
  };
}

// Log one request's usage and increment the rolling quota atomically.
export async function recordUsage({ userId, route, tokensIn, tokensOut }) {
  const cost = estimateCostMicros(route, tokensIn, tokensOut);
  await withTx(async (c) => {
    await c.query(
      `INSERT INTO usage_events (user_id, model, tokens_in, tokens_out, cost_micros)
       VALUES ($1, $2, $3, $4, $5)`,
      [userId, route, tokensIn, tokensOut, cost]);
    await c.query(
      "UPDATE quotas SET used_micros = used_micros + $2 WHERE subject_id = $1",
      [userId, cost]);
  });
  return { cost };
}

export { approxTokens };
