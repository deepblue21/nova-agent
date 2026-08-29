// POST /v1/pair/claim — telefonun tek kullanımlık kodu kalıcı API anahtarına
// takas ettiği TEK kimlik doğrulamasız uç nokta.
//
// Neden güvenli:
//   1. Kod 40 bit entropili, 5 dakika ömürlü ve TEK kullanımlık (DB seviyesinde).
//   2. IP başına başarısız deneme sınırı var; genel hız sınırlayıcı public
//      yolları atladığı için burada kendi sınırlayıcımız duruyor.
//   3. Kod ancak PC'de fiziksel olarak biri `pair.mjs` çalıştırdıysa var olur;
//      kendiliğinden açık kalan bir kapı değildir.
//   4. Başarılı takas kodu anında tüketir — QR ekran görüntüsü sonradan işe yaramaz.
import express from "express";
import { normalizePairingCode } from "../lib/pairing.mjs";

export const PAIR_CLAIM_PATH = "/v1/pair/claim";

const DEFAULT_MAX_FAILURES = 10;
const DEFAULT_WINDOW_MS = 10 * 60 * 1000;

/**
 * IP başına başarısız deneme sayacı — saf, saat enjekte edilebilir.
 * Başarılı takas sayacı sıfırlar (aynı evdeki ikinci telefon cezalanmasın).
 */
export function createFailureLimiter({
  maxFailures = DEFAULT_MAX_FAILURES,
  windowMs = DEFAULT_WINDOW_MS,
  now = () => Date.now(),
} = {}) {
  const seen = new Map();
  const current = (ip) => {
    const e = seen.get(ip);
    if (!e || now() > e.reset) return null;
    return e;
  };
  return {
    blocked(ip) {
      const e = current(ip);
      return !!e && e.count >= maxFailures;
    },
    retryAfterSec(ip) {
      const e = current(ip);
      return e ? Math.max(1, Math.ceil((e.reset - now()) / 1000)) : 0;
    },
    fail(ip) {
      const e = current(ip);
      if (!e) seen.set(ip, { count: 1, reset: now() + windowMs });
      else e.count += 1;
    },
    succeed(ip) {
      seen.delete(ip);
    },
    /** Süresi geçmiş kayıtları at — sınırsız büyümeyi engeller. */
    sweep() {
      const t = now();
      for (const [ip, e] of seen) if (t > e.reset) seen.delete(ip);
      return seen.size;
    },
  };
}

const REASONS = {
  not_found: { status: 404, error: "eşleme kodu bulunamadı" },
  expired: { status: 410, error: "eşleme kodunun süresi doldu" },
  claimed: { status: 410, error: "eşleme kodu zaten kullanıldı" },
};

/**
 * @param store    createPairingStore(...) — testte sahte verilebilir
 * @param enabled  PAIRING_ENABLED=0 ile kapatılabilir
 */
export function createPairingRouter({
  store,
  enabled = true,
  limiter = createFailureLimiter(),
  logger = null,
} = {}) {
  const router = express.Router();

  router.post(PAIR_CLAIM_PATH, async (req, res) => {
    if (!enabled) return res.status(503).json({ error: "eşleme kapalı" });

    const ip = req.ip || req.socket?.remoteAddress || "unknown";
    if (limiter.blocked(ip)) {
      res.setHeader("Retry-After", String(limiter.retryAfterSec(ip)));
      return res.status(429).json({ error: "çok fazla başarısız deneme" });
    }

    const code = normalizePairingCode(req.body?.code);
    if (!code) {
      limiter.fail(ip);
      return res.status(400).json({ error: "geçersiz eşleme kodu biçimi" });
    }

    let result;
    try {
      result = await store.claim(code);
    } catch (e) {
      logger?.error?.({ err: e?.message || String(e) }, "pair claim failed");
      return res.status(500).json({ error: "eşleme sırasında iç hata" });
    }

    if (!result?.ok) {
      limiter.fail(ip);
      const r = REASONS[result?.reason] || REASONS.not_found;
      return res.status(r.status).json({ error: r.error });
    }

    limiter.succeed(ip);
    // Anahtar YALNIZ burada, yalnız bir kez döner. Loglanmaz.
    logger?.info?.({ label: result.label }, "device paired");
    return res.json({ api_key: result.apiKey, label: result.label });
  });

  return router;
}
