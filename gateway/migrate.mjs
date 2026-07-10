// Migration runner: applies migrations/*.sql in filename order, once each,
// tracking applied files in a schema_migrations table. Idempotent + transactional.
//   node migrate.mjs        (uses DATABASE_URL)
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { pool } from "./lib/db.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const dir  = resolve(here, "migrations");

async function main() {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS schema_migrations (
       name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`);

  const files = readdirSync(dir).filter(f => f.endsWith(".sql")).sort();
  for (const f of files) {
    const { rows } = await pool.query("SELECT 1 FROM schema_migrations WHERE name = $1", [f]);
    if (rows.length) { console.log("skip   ", f); continue; }
    const sql = readFileSync(join(dir, f), "utf8");
    const c = await pool.connect();
    try {
      await c.query("BEGIN");
      await c.query(sql);
      await c.query("INSERT INTO schema_migrations (name) VALUES ($1)", [f]);
      await c.query("COMMIT");
      console.log("applied", f);
    } catch (e) {
      await c.query("ROLLBACK");
      throw e;
    } finally {
      c.release();
    }
  }
  await pool.end();
  console.log("migrations done");
}

main().catch((e) => { console.error("migration failed:", e.message); process.exit(1); });
