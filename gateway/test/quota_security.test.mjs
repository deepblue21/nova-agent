import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
import { createUsageStore } from '../lib/usage.mjs';

test('quota rollover, metering and admin changes execute against PostgreSQL SQL', async t => {
  const db = new PGlite();
  t.after(() => db.close());
  await db.exec(readFileSync(new URL('../migrations/001_init.sql', import.meta.url), 'utf8'));
  const { checkQuota, recordUsage, setQuota } = createUsageStore({
    query: db.query.bind(db), transaction: db.transaction.bind(db),
  });
  const user = (await db.query("INSERT INTO users(email) VALUES ('quota@example.invalid') RETURNING id")).rows[0].id;
  const charge = () => recordUsage({ userId: user, route: 'openai/gpt-4o-mini', tokensIn: 1000, tokensOut: 1000 });
  for (const period of ['day', 'month']) {
    await setQuota(user, period, 750);
    await db.query("UPDATE quotas SET used_micros=9999, resets_at='2000-01-01' WHERE subject_id=$1", [user]);
    assert.deepEqual(await checkQuota(user), { allowed: true, used: 0, limit: 750 });
    const reset = (await db.query('SELECT resets_at FROM quotas WHERE subject_id=$1', [user])).rows[0].resets_at;
    assert.ok(reset.getTime() > Date.now());
    assert.equal(reset.getUTCHours(), 0);
    if (period === 'month') assert.equal(reset.getUTCDate(), 1);
    await charge();
    assert.deepEqual(await checkQuota(user), { allowed: false, used: 750, limit: 750 });
    assert.deepEqual(await checkQuota(user), { allowed: false, used: 750, limit: 750 });
    // Updating a live limit must not forgive prior usage or change the window.
    await setQuota(user, period, 1000);
    assert.deepEqual(await checkQuota(user), { allowed: true, used: 750, limit: 1000 });
    assert.equal((await db.query('SELECT resets_at FROM quotas WHERE subject_id=$1', [user])).rows[0].resets_at.getTime(), reset.getTime());
    // A charge crossing expiry rolls over itself, even without a preceding check.
    await db.query("UPDATE quotas SET resets_at='2000-01-01' WHERE subject_id=$1", [user]);
    await charge();
    assert.equal((await checkQuota(user)).used, 750);
  }
  await setQuota(user, 'day', 0); // period change starts a coherent new window
  assert.deepEqual(await checkQuota(user), { allowed: false, used: 0, limit: 0 });
  await db.query("UPDATE quotas SET resets_at='2000-01-01' WHERE subject_id=$1", [user]);
  assert.equal((await checkQuota(user)).allowed, false); // expired zero budget stays blocked
  await setQuota(user, 'day', 100000);
  await Promise.all(Array.from({ length: 10 }, (_, i) => i % 2 ? charge() : checkQuota(user)));
  assert.equal((await checkQuota(user)).used, 3750);
  const event = (await db.query('SELECT model, cost_micros FROM usage_events ORDER BY id DESC LIMIT 1')).rows[0];
  assert.equal(event.model, 'openai/gpt-4o-mini');
  assert.equal(Number(event.cost_micros), 750);
  await assert.rejects(recordUsage({ userId: user, route: 'openai/unpriced', tokensIn: 1000, tokensOut: 1000 }), /price/);
});
