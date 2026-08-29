// Telefon eşleme kodu üretir ve terminale QR olarak basar.
//
//   node scripts/pair.mjs --host 192.168.1.20 --port 18088 --name SALIH-PC
//
// Kullanıcı telefonda yalnız QR'ı okutur. API anahtarı BURADA üretilmez —
// telefon kodu takas ettiğinde (POST /v1/pair/claim) o an üretilir. Yani bu
// script'in çıktısı bir ekran görüntüsünde kalsa bile, kod tüketildikten ya da
// 5 dakika geçtikten sonra hiçbir işe yaramaz.
import qrcode from "qrcode-terminal";
import { q, pool } from "../lib/db.mjs";
import { createPairingStore } from "../lib/pairing_store.mjs";
import {
  newPairingCode,
  formatPairingCode,
  buildPairUri,
  pairBaseUrl,
  PAIRING_TTL_MS,
} from "../lib/pairing.mjs";

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith("--")) out[key] = "1";
    else { out[key] = next; i++; }
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
const host = String(args.host || "").trim();
const port = Number(args.port || 18088);
const name = String(args.name || "PC").trim();
const email = String(args.email || "phone@horus.local").trim();
const label = String(args.label || "phone").trim();
const tls = args.tls === "1";
const usd = String(args.quota || "5");
const ttlMs = args.ttl ? Number(args.ttl) * 1000 : PAIRING_TTL_MS;

if (!host) {
  console.error("kullanım: node scripts/pair.mjs --host <PC-IP> [--port 18088] [--name PC-adı]");
  console.error("  --host zorunlu: container kendi LAN IP'sini bilemez, host'tan verilmeli.");
  process.exit(1);
}
if (!pairBaseUrl({ host, port, tls })) {
  console.error("geçersiz host/port:", host, port);
  process.exit(1);
}

async function main() {
  // Kullanıcıyı bul veya oluştur (bootstrap-user ile aynı mantık).
  let u = (await q("SELECT id FROM users WHERE lower(email) = lower($1)", [email])).rows[0];
  if (!u) u = (await q("INSERT INTO users (email) VALUES ($1) RETURNING id", [email])).rows[0];

  const limitMicros = Math.round(parseFloat(usd) * 1_000_000);
  await q(
    `INSERT INTO quotas (subject_id, period, limit_micros, resets_at)
       VALUES ($1, 'month', $2, date_trunc('month', now()) + interval '1 month')
     ON CONFLICT (subject_id) DO UPDATE SET limit_micros = EXCLUDED.limit_micros`,
    [u.id, limitMicros],
  );

  const code = newPairingCode();
  const store = createPairingStore();
  const { expiresAt } = await store.issueCode({ userId: u.id, code, label, ttlMs });
  await store.purgeExpired().catch(() => {});

  const uri = buildPairUri({ host, port, code, name, tls });
  if (!uri) {
    console.error("eşleme URI'si kurulamadı — host/port/kod geçersiz.");
    process.exit(1);
  }

  const minutes = Math.round(ttlMs / 60000);
  console.log("");
  console.log("=================== TELEFONU BAĞLA ===================");
  console.log("  Uygulamada:  Ayarlar > PC'yi bağla > QR tara");
  console.log("");
  qrcode.generate(uri, { small: true }, (art) => console.log(art));
  console.log("  Kamera yoksa elle gir:");
  console.log("    Kod      : " + formatPairingCode(code));
  console.log("    Adres    : " + pairBaseUrl({ host, port, tls }));
  console.log("");
  console.log("  Bu kod " + minutes + " dakika geçerli ve TEK kullanımlık.");
  console.log("  Son geçerlilik: " + new Date(expiresAt).toLocaleTimeString());
  console.log("======================================================");
  console.log("");

  await pool.end();
}

main().catch((e) => {
  console.error("eşleme kodu üretilemedi:", e.message);
  process.exit(1);
});
