// The session lock covers discovery and DDL, including first-time schema setup.
export async function applyMigrations(client, files, onApplied = () => {}) {
  await client.query('SELECT pg_advisory_lock(68210412)');
  try {
    await client.query('CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())');
    for (const {name, sql} of files) {
      if ((await client.query('SELECT 1 FROM schema_migrations WHERE name=$1', [name])).rows.length) continue;
      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations(name) VALUES($1)', [name]);
        await client.query('COMMIT');
        onApplied(name);
      } catch (e) { await client.query('ROLLBACK'); throw e; }
    }
  } finally { await client.query('SELECT pg_advisory_unlock(68210412)'); }
}
