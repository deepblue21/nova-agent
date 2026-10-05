// Per-user quota enforcement + usage metering.
import { q, withTx } from "./db.mjs";
import { estimateCostMicros, approxTokens } from "./pricing.mjs";

// All windows use UTC calendar boundaries, independently of the database session timezone.
const nextWindow = period => `(CASE WHEN ${period} = 'day'
  THEN date_trunc('day', now() AT TIME ZONE 'UTC') + interval '1 day'
  ELSE date_trunc('month', now() AT TIME ZONE 'UTC') + interval '1 month'
  END AT TIME ZONE 'UTC')`;

export function createUsageStore({ query = q, transaction = withTx } = {}) {
// UPDATE takes a row lock: competing checks/charges cannot reset the same window twice.
async function checkQuota(userId) {
  const { rows } = await query(
    `UPDATE quotas SET
       used_micros = CASE WHEN resets_at <= now() THEN 0 ELSE used_micros END,
       resets_at = CASE WHEN resets_at <= now() THEN ${nextWindow('period')} ELSE resets_at END
     WHERE subject_id = $1 RETURNING limit_micros, used_micros, resets_at`, [userId]);
  if (!rows.length) return { allowed: true, used: 0, limit: null };
  const Q = rows[0];
  return {
    allowed: Number(Q.used_micros) < Number(Q.limit_micros),
    used:    Number(Q.used_micros),
    limit:   Number(Q.limit_micros),
  };
}

// Log one request's usage and increment the rolling quota atomically.
async function recordUsage({ userId, route, tokensIn, tokensOut }) {
  const cost = estimateCostMicros(route, tokensIn, tokensOut);
  await transaction(async (c) => {
    await c.query(
      `INSERT INTO usage_events (user_id, model, tokens_in, tokens_out, cost_micros)
       VALUES ($1, $2, $3, $4, $5)`,
      [userId, route, tokensIn, tokensOut, cost]);
    await c.query(
      `UPDATE quotas SET
         used_micros = (CASE WHEN resets_at <= now() THEN 0 ELSE used_micros END) + $2,
         resets_at = CASE WHEN resets_at <= now() THEN ${nextWindow('period')} ELSE resets_at END
       WHERE subject_id = $1`,
      [userId, cost]);
  });
  return { cost };
}

async function setQuota(userId, period, limit) {
  await query(`INSERT INTO quotas (subject_id, period, limit_micros, resets_at)
    VALUES ($1, $2, $3, ${nextWindow('$2::text')})
    ON CONFLICT (subject_id) DO UPDATE SET
      limit_micros = EXCLUDED.limit_micros,
      used_micros = CASE WHEN quotas.period <> EXCLUDED.period OR quotas.resets_at <= now()
        THEN 0 ELSE quotas.used_micros END,
      resets_at = CASE WHEN quotas.period <> EXCLUDED.period OR quotas.resets_at <= now()
        THEN EXCLUDED.resets_at ELSE quotas.resets_at END,
      period = EXCLUDED.period`, [userId, period, limit]);
}
return { checkQuota, recordUsage, setQuota };
}

export const { checkQuota, recordUsage, setQuota } = createUsageStore();

export { approxTokens };
