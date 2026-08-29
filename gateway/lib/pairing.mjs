// Saf eşleme yardımcıları — yalnız node:crypto. Yan etkisiz, birim testli.
//
// Eşleme kodu Crockford Base32 kullanır: I, L, O, U alfabede yok. Kullanıcı
// bir kodu elle yazarken 0/O ve 1/I/L karıştırması normalizasyonda düzeltilir,
// yani "yanlış yazdım" hatası pratikte ortadan kalkar.
import { createHash, randomInt } from "node:crypto";

/** Crockford Base32 — I, L, O, U yok (görsel karışma ve istenmeyen kelimeler). */
export const PAIRING_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

/** Kod uzunluğu. 8 karakter × 5 bit = 40 bit entropi. */
export const PAIRING_CODE_LENGTH = 8;

/** Varsayılan geçerlilik: 5 dakika. Kod QR olarak ekranda dururken yeter. */
export const PAIRING_TTL_MS = 5 * 60 * 1000;

export const sha256hex = (s) => createHash("sha256").update(String(s)).digest("hex");

/**
 * Kriptografik olarak güvenli yeni bir eşleme kodu üretir (normalize biçimde).
 * `rand` yalnız test için enjekte edilir; üretimde node:crypto randomInt.
 */
export function newPairingCode(rand = (n) => randomInt(n)) {
  let out = "";
  for (let i = 0; i < PAIRING_CODE_LENGTH; i++) {
    out += PAIRING_ALPHABET[rand(PAIRING_ALPHABET.length)];
  }
  return out;
}

/**
 * Kullanıcı girdisini kanonik biçime çevirir: büyük harf, ayraçlar atılır,
 * görsel ikizler eşlenir (O→0, I/L→1). Geçersizse "" döner — kısmi kod
 * uydurulmaz.
 */
export function normalizePairingCode(input) {
  const raw = String(input ?? "")
    .toUpperCase()
    .replace(/[\s\-_.]/g, "")
    .replace(/O/g, "0")
    .replace(/[IL]/g, "1");
  if (raw.length !== PAIRING_CODE_LENGTH) return "";
  for (const ch of raw) if (!PAIRING_ALPHABET.includes(ch)) return "";
  return raw;
}

/** İnsan gözü için "H7K2-9M4P". Geçersiz kodda girdi olduğu gibi döner. */
export function formatPairingCode(code) {
  const c = normalizePairingCode(code);
  if (!c) return String(code ?? "");
  return c.slice(0, 4) + "-" + c.slice(4);
}

/** Host bir IPv6 literali ise URL için köşeli paranteze alır. */
function bracketHost(host) {
  const h = String(host || "").trim();
  if (!h) return "";
  if (h.includes(":") && !h.startsWith("[")) return "[" + h + "]";
  return h;
}

/**
 * Eşleme sonrası istemcinin kullanacağı Base URL'i kurar.
 * Her zaman `/v1` ile biter — istemcinin varsayımıyla birebir aynı.
 */
export function pairBaseUrl({ host, port, tls = false }) {
  const h = bracketHost(host);
  if (!h) return "";
  const p = Number(port);
  if (!Number.isInteger(p) || p < 1 || p > 65535) return "";
  const scheme = tls ? "https" : "http";
  const defaultPort = tls ? 443 : 80;
  const authority = p === defaultPort ? h : h + ":" + p;
  return scheme + "://" + authority + "/v1";
}

/**
 * QR'a gömülecek URI'yi kurar.
 *   horus://pair?v=1&code=H7K29M4P&host=192.168.1.20&port=8088&name=SALIH-PC
 * Eksik/geçersiz alanda "" döner (bozuk QR üretmektense hiç üretme).
 */
export function buildPairUri({ host, port, code, name = "", tls = false }) {
  const c = normalizePairingCode(code);
  if (!c) return "";
  if (!pairBaseUrl({ host, port, tls })) return "";
  const params = new URLSearchParams();
  params.set("v", "1");
  params.set("code", c);
  params.set("host", String(host).trim());
  params.set("port", String(Number(port)));
  if (tls) params.set("tls", "1");
  const n = String(name || "").trim().slice(0, 64);
  if (n) params.set("name", n);
  return "horus://pair?" + params.toString();
}

/**
 * QR'dan okunan URI'yi ayrıştırır. Tanınmayan/bozuk girdide `null` — kısmi
 * veri uydurulmaz (istemci "eksik alanı kullanıcı doldursun" moduna düşer).
 */
export function parsePairUri(uri) {
  const raw = String(uri ?? "").trim();
  if (!/^horus:\/\/pair\?/i.test(raw)) return null;
  let params;
  try {
    params = new URLSearchParams(raw.slice(raw.indexOf("?") + 1));
  } catch {
    return null;
  }
  const version = params.get("v") || "1";
  if (version !== "1") return null;

  const code = normalizePairingCode(params.get("code"));
  if (!code) return null;

  const host = String(params.get("host") || "").trim();
  const port = Number(params.get("port"));
  const tls = params.get("tls") === "1";
  const baseUrl = pairBaseUrl({ host, port, tls });
  if (!baseUrl) return null;

  return {
    version: 1,
    code,
    host,
    port,
    tls,
    baseUrl,
    name: String(params.get("name") || "").trim().slice(0, 64),
  };
}
