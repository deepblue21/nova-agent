// Usage-based billing: flush unreported usage_events to Stripe metered billing.
// Run on a schedule (cron / setInterval / k8s CronJob). Idempotent via reported_at.
import Stripe from "stripe";
import { q } from "./db.mjs";

const stripe = process.env.STRIPE_SECRET_KEY ? new Stripe(process.env.STRIPE_SECRET_KEY) : null;

// Pure: micro-dollars -> billable quantity. Default unit = 1 cent (10_000 micros).
export const microsToCents = (micros) => Math.ceil(Number(micros || 0) / 10000);

async function subscriptionItemFor(userId) {
  const { rows } = await q(
    "SELECT stripe_item_id FROM billing_accounts WHERE user_id = $1", [userId]);
  return rows[0]?.stripe_item_id || null;
}

// Aggregate unreported usage per user, report to Stripe, mark reported.
export async function flushUsage() {
  if (!stripe) return { reported: 0, skipped: "no STRIPE_SECRET_KEY" };
  const { rows } = await q(
    `SELECT user_id, sum(cost_micros)::bigint AS micros, array_agg(id) AS ids
       FROM usage_events WHERE reported_at IS NULL GROUP BY user_id`);
  let reported = 0;
  for (const r of rows) {
    const itemId = await subscriptionItemFor(r.user_id);
    if (itemId && Number(r.micros) > 0) {
      await stripe.subscriptionItems.createUsageRecord(itemId, {
        quantity: microsToCents(r.micros),
        timestamp: Math.floor(Date.now() / 1000),
        action: "increment",
      });
      reported++;
    }
    await q("UPDATE usage_events SET reported_at = now() WHERE id = ANY($1)", [r.ids]);
  }
  return { reported, users: rows.length };
}
