// Eşleme kodu deposu — kodu tek seferde, atomik olarak taze bir API anahtarına
// takas eder.
//
// Değişmez: düz metin sır asla saklanmaz. Kod yalnız özet olarak yazılır; API
// anahtarı takas ANINDA üretilir, düz metin hâli tek bir kez çağırana döner.
import { q, withTx } from "./db.mjs";
import { newApiKey } from "./keys.mjs";
import { sha256hex, PAIRING_TTL_MS } from "./pairing.mjs";

/**
 * @param deps  test için enjekte edilebilir; üretimde gerçek db/keys.
 */
export function createPairingStore({ query = q, tx = withTx, makeKey = newApiKey } = {}) {
  return {
    /** Yeni kodun özetini yazar. Düz kodu çağıran gösterir, biz saklamayız. */
    async issueCode({ userId, code, label = "phone", ttlMs = PAIRING_TTL_MS }) {
      const expiresAt = new Date(Date.now() + ttlMs);
      const { rows } = await query(
        `INSERT INTO pairing_codes (code_hash, user_id, label, expires_at)
              VALUES ($1, $2, $3, $4)
         RETURNING id, expires_at`,
        [sha256hex(code), userId, String(label).slice(0, 64), expiresAt],
      );
      return { id: rows[0].id, expiresAt: rows[0].expires_at };
    },

    /**
     * Kodu takas eder. Başarıda `{ ok: true, apiKey, label }`.
     * Başarısızlıkta `{ ok: false, reason }` — reason:
     * `not_found` | `expired` | `claimed`.
     *
     * Tek kullanımlık güvencesi UPDATE ... WHERE claimed_at IS NULL ile
     * veritabanı seviyesinde; iki telefon aynı anda denese biri kaybeder.
     */
    async claim(code) {
      const codeHash = sha256hex(code);
      return tx(async (c) => {
        const claimed = await c.query(
          `UPDATE pairing_codes
              SET claimed_at = now()
            WHERE code_hash = $1
              AND claimed_at IS NULL
              AND expires_at > now()
        RETURNING id, user_id, label`,
          [codeHash],
        );

        if (!claimed.rows.length) {
          const { rows } = await c.query(
            "SELECT claimed_at, expires_at FROM pairing_codes WHERE code_hash = $1",
            [codeHash],
          );
          if (!rows.length) return { ok: false, reason: "not_found" };
          if (rows[0].claimed_at) return { ok: false, reason: "claimed" };
          return { ok: false, reason: "expired" };
        }

        const row = claimed.rows[0];
        const key = makeKey();
        const inserted = await c.query(
          `INSERT INTO api_keys (user_id, prefix, token_hash) VALUES ($1, $2, $3) RETURNING id`,
          [row.user_id, key.prefix, key.token_hash],
        );
        await c.query("UPDATE pairing_codes SET claimed_key = $1 WHERE id = $2", [
          inserted.rows[0].id,
          row.id,
        ]);
        return { ok: true, apiKey: key.full, label: row.label };
      });
    },

    /** Süresi dolmuş kodların temizliği. Kayıt tutulmasına gerek yok. */
    async purgeExpired(olderThanMs = 24 * 60 * 60 * 1000) {
      const cutoff = new Date(Date.now() - olderThanMs);
      const { rowCount } = await query("DELETE FROM pairing_codes WHERE expires_at < $1", [cutoff]);
      return rowCount || 0;
    },
  };
}
