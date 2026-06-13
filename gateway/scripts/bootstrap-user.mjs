// Bootstrap the first user: create user + API key + monthly quota in one shot.
//   node scripts/bootstrap-user.mjs <email> [limitUSD]
// Prints the full API key ONCE (only its hash is stored).
import { q, pool } from "../lib/db.mjs";
import { newApiKey } from "../lib/keys.mjs";

const [email, usd = "5"] = process.argv.slice(2);
if (!email) {
  console.error("usage: node scripts/bootstrap-user.mjs <email> [limitUSD]");
  process.exit(1);
}

async function main() {
  let u = (await q("SELECT id FROM users WHERE lower(email) = lower($1)", [email])).rows[0];
  if (!u) u = (await q("INSERT INTO users (email) VALUES ($1) RETURNING id", [email])).rows[0];

  const k = newApiKey();
  await q("INSERT INTO api_keys (user_id, prefix, token_hash) VALUES ($1,$2,$3)",
    [u.id, k.prefix, k.token_hash]);

  const limitMicros = Math.round(parseFloat(usd) * 1_000_000); // $1 = 1e6 micro-dollars
  await q(
    `INSERT INTO quotas (subject_id, period, limit_micros, resets_at)
       VALUES ($1, 'month', $2, date_trunc('month', now()) + interval '1 month')
     ON CONFLICT (subject_id) DO UPDATE SET limit_micros = EXCLUDED.limit_micros`,
    [u.id, limitMicros]);

  console.log("user id :", u.id);
  console.log("email   :", email);
  console.log("quota   : $" + usd + "/month");
  console.log("API key (save now, shown once):");
  console.log("  " + k.full);
  await pool.end();
}

main().catch((e) => { console.error("bootstrap failed:", e.message); process.exit(1); });
