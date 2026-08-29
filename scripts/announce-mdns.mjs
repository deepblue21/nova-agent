// Horus Gateway'i yerel ağda `_horus._tcp` olarak duyurur.
//
//   node scripts/announce-mdns.mjs --port 18088 [--ip 192.168.1.20] [--name SALIH-PC]
//
// NEDEN HOST'TA ÇALIŞIR, CONTAINER'DA DEĞİL:
// mDNS 224.0.0.251'e multicast ile konuşur. Docker'ın varsayılan bridge ağı
// multicast'i LAN'a geçirmez — container içinden yayın yapılsa telefon duymaz.
// Bu yüzden yayıncı Windows/WSL host'unda, gateway'in yanında koşar.
//
// Sıfır npm bağımlılığı: yalnız node:dgram + scripts/lib/mdns.mjs.
import dgram from "node:dgram";
import os from "node:os";
import {
  TYPE,
  encodeMessage,
  decodeMessage,
  serviceRecords,
  answersFor,
} from "./lib/mdns.mjs";

const MDNS_ADDR = "224.0.0.251";
const MDNS_PORT = 5353;
const SERVICE_TYPE = "_horus._tcp";

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith("--")) continue;
    const key = argv[i].slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith("--")) out[key] = "1";
    else { out[key] = next; i++; }
  }
  return out;
}

/** Özel (RFC1918) IPv4 adreslerini bulur. Tailscale CGNAT (100.64/10) hariç. */
export function privateIPv4s(interfaces = os.networkInterfaces()) {
  const found = [];
  for (const [iface, addrs] of Object.entries(interfaces || {})) {
    for (const a of addrs || []) {
      const family = a.family === 4 || a.family === "IPv4";
      if (!family || a.internal) continue;
      const ip = a.address;
      const isPrivate =
        /^10\./.test(ip) ||
        /^192\.168\./.test(ip) ||
        /^172\.(1[6-9]|2\d|3[01])\./.test(ip);
      if (isPrivate) found.push({ iface, ip });
    }
  }
  return found;
}

const args = parseArgs(process.argv.slice(2));
const port = Number(args.port || 18088);
const tls = args.tls === "1";
const apiPath = String(args.path || "/v1");
const verbose = args.verbose === "1";

const candidates = privateIPv4s();
const ip = String(args.ip || candidates[0]?.ip || "").trim();
if (!ip) {
  console.error("mDNS: yerel ağ IPv4 adresi bulunamadı. --ip ile elle ver.");
  process.exit(1);
}
if (!args.ip && candidates.length > 1) {
  console.warn("mDNS: birden fazla yerel ağ bulundu, " + ip + " seçildi. Yanlışsa --ip ile belirt.");
  for (const c of candidates) console.warn("  - " + c.iface + " " + c.ip);
}

const rawHost = String(args.name || os.hostname() || "horus").split(".")[0];
// DNS-SD örnek adı: yalnız güvenli karakterler, en fazla 40 karakter.
const displayName = rawHost.replace(/[^A-Za-z0-9 _-]/g, "").slice(0, 40) || "horus";
const hostname = displayName.toLowerCase().replace(/[^a-z0-9-]/g, "-");

const records = serviceRecords({
  instance: displayName,
  serviceType: SERVICE_TYPE,
  hostname,
  ip,
  port,
  txt: { v: "1", path: apiPath, tls: tls ? "1" : "0", name: displayName },
});

const socket = dgram.createSocket({ type: "udp4", reuseAddr: true });
let closed = false;

socket.on("error", (err) => {
  console.error("mDNS soket hatası:", err.message);
  // 5353 başka bir responder'da (Bonjour/avahi) olabilir. Bu ölümcül değil:
  // duyuru yapılamaz ama QR yolu çalışmaya devam eder. Dürüstçe söyle ve çık.
  shutdown(1);
});

socket.on("message", (msg, rinfo) => {
  const decoded = decodeMessage(msg);
  if (!decoded || (decoded.flags & 0x8000) !== 0) return; // yanıtları yok say
  const answers = answersFor(decoded.questions, records);
  if (!answers.length) return;
  if (verbose) console.log("mDNS: sorgu " + rinfo.address + " → " + answers.length + " kayıt");
  send(answers);
});

function send(answers, ttlOverride) {
  const payload = ttlOverride === undefined
    ? answers
    : answers.map((r) => ({ ...r, ttl: ttlOverride }));
  const buf = encodeMessage({ answers: payload });
  socket.send(buf, 0, buf.length, MDNS_PORT, MDNS_ADDR, (err) => {
    if (err && !closed) console.warn("mDNS gönderilemedi:", err.message);
  });
}

const allRecords = [records.ptr, records.srv, records.txt, records.a];

socket.bind(MDNS_PORT, () => {
  try {
    socket.setMulticastTTL(255);
    socket.addMembership(MDNS_ADDR, ip);
    socket.setMulticastInterface(ip);
  } catch (e) {
    console.warn("mDNS: multicast grubuna katılınamadı (" + e.message + "). Yalnız yanıt modu.");
  }
  console.log("mDNS yayında: " + records.fqdn + " → " + ip + ":" + port + apiPath);

  // RFC 6762 §8.3: duyuru en az iki kez, aralarında en az 1 sn ile gönderilir.
  send(allRecords);
  setTimeout(() => !closed && send(allRecords), 1000);
  setTimeout(() => !closed && send(allRecords), 3000);
});

// TTL'in yarısında tazele — telefon uygulaması açıkken kayıt düşmesin.
const refresh = setInterval(() => !closed && send(allRecords), 60_000);
if (refresh.unref) refresh.unref();

function shutdown(code = 0) {
  if (closed) return;
  closed = true;
  clearInterval(refresh);
  // Vedalaşma: TTL=0 ile aynı kayıtlar → istemciler önbelleği hemen düşürür,
  // "hayalet PC" listede kalmaz.
  try {
    const buf = encodeMessage({ answers: allRecords.map((r) => ({ ...r, ttl: 0 })) });
    socket.send(buf, 0, buf.length, MDNS_PORT, MDNS_ADDR, () => socket.close(() => process.exit(code)));
    setTimeout(() => process.exit(code), 500).unref();
  } catch {
    process.exit(code);
  }
}

process.on("SIGINT", () => shutdown(0));
process.on("SIGTERM", () => shutdown(0));

// Sağlık kendi kendine test modu: bağlan, duyur, çık. CI'da soket yolunu doğrular.
if (args.selftest === "1") setTimeout(() => shutdown(0), 1500);

export { TYPE, records };
