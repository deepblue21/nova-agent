// Usage-based billing: flush unreported usage_events to Stripe metered billing.
// Run on a schedule (cron / setInterval / k8s CronJob). Idempotent via reported_at.
import Stripe from "stripe";
import { q, withTx } from "./db.mjs";

const stripe = process.env.STRIPE_SECRET_KEY ? new Stripe(process.env.STRIPE_SECRET_KEY) : null;

// Pure: micro-dollars -> billable quantity. Default unit = 1 cent (10_000 micros).
export const microsToCents = (micros) => Math.ceil(Number(micros || 0) / 10000);

export function createBilling({client = stripe, query = q, transaction = withTx} = {}) {
  return async function flushUsage() {
    if (!client) return {reported:0,skipped:'no STRIPE_SECRET_KEY'};
    await transaction(async c => {
      // Short creation lock only. Stripe delivery happens after immutable batches commit.
      await c.query("SELECT pg_advisory_xact_lock(68210411)");
      // Preserve prior semantics: usage without a metered subscription is not billed later.
      await c.query(`UPDATE usage_events e SET reported_at=now()
        WHERE e.reported_at IS NULL AND e.billing_batch_id IS NULL AND
        (e.cost_micros=0 OR NOT EXISTS (SELECT 1 FROM billing_accounts a WHERE a.user_id=e.user_id AND a.stripe_item_id IS NOT NULL))`);
      const {rows} = await c.query(`SELECT e.user_id,a.stripe_item_id AS item_id,
        sum(e.cost_micros)::bigint AS micros,array_agg(e.id) AS ids
        FROM usage_events e JOIN billing_accounts a ON a.user_id=e.user_id
        WHERE e.reported_at IS NULL AND e.billing_batch_id IS NULL AND a.stripe_item_id IS NOT NULL
        GROUP BY e.user_id,a.stripe_item_id`);
      for (const r of rows) {
        const batch = (await c.query(`INSERT INTO billing_batches(user_id,item_id,quantity,usage_timestamp)
          VALUES($1,$2,$3,floor(extract(epoch from now()))) RETURNING id`,[r.user_id,r.item_id,microsToCents(r.micros)])).rows[0];
        await c.query('UPDATE usage_events SET billing_batch_id=$1 WHERE id=ANY($2::bigint[])',[batch.id,r.ids]);
      }
    });
    const {rows} = await query("SELECT id FROM billing_batches WHERE state='pending' ORDER BY created_at LIMIT 100");
    let reported=0, manualReview=0;
    for (const {id} of rows) {
      // Stripe retains idempotency keys for at least 24 hours. Quarantine older
      // uncertain deliveries instead of silently charging a second time.
      const b=(await query(`UPDATE billing_batches SET
        state=CASE WHEN first_attempt_at < now()-interval '23 hours' THEN 'manual_review' ELSE state END,
        first_attempt_at=COALESCE(first_attempt_at,now()) WHERE id=$1 AND state='pending' RETURNING *`,[id])).rows[0];
      if (!b) continue;
      if (b.state==='manual_review') {manualReview++;continue;}
      await client.subscriptionItems.createUsageRecord(b.item_id,{
        quantity:Number(b.quantity),timestamp:Number(b.usage_timestamp),action:'increment',
      },{idempotencyKey:'nova-usage-'+b.id});
      await transaction(async c => {
        await c.query("UPDATE billing_batches SET state='reported',reported_at=now() WHERE id=$1",[id]);
        await c.query('UPDATE usage_events SET reported_at=now() WHERE billing_batch_id=$1',[id]);
      });
      reported++;
    }
    return {reported,manualReview};
  };
}

export const flushUsage = createBilling();
