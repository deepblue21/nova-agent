// Minimal mDNS (RFC 6762 / DNS-SD RFC 6763) tel biçimi kodlayıcı/çözücü.
//
// Neden elle: kök `scripts/` bugüne kadar SIFIR npm bağımlılığıyla çalışıyor
// (kök package.json'da `dependencies` yok). Bir servis YAYINLAMAK için gereken
// alt küme küçük ve saf olarak test edilebilir, o yüzden bu değişmezi bozmuyoruz.
//
// Kapsam — dürüst sınırlar:
//   • Yalnız IPv4 (A kaydı). AAAA yok.
//   • Yalnız yanıtlayıcı (responder). Sorgu/çözümleme yok.
//   • Çakışma sondalaması (probing/conflict resolution, RFC 6762 §8) YOK.
//     Aynı ağda iki Horus PC'si aynı adı kullanırsa isimler çakışabilir;
//     bunu taklit etmiyoruz, `announce-mdns.mjs` ad sonuna host adını ekler.
//   • Ad sıkıştırma yazarken kullanılmaz (sadece okunurken çözülür).

export const TYPE = { A: 1, PTR: 12, TXT: 16, SRV: 33, ANY: 255 };
export const CLASS_IN = 1;
/** Sınıf alanının üst biti: soruda "unicast yanıt", cevapta "cache-flush". */
export const CLASS_FLUSH = 0x8000;
export const FLAG_RESPONSE = 0x8400; // QR=1, AA=1

/** "_horus._tcp.local" → uzunluk önekli etiketler + sonlandırıcı 0. */
export function encodeName(name) {
  const labels = String(name || "").replace(/\.$/, "").split(".").filter(Boolean);
  const parts = [];
  for (const label of labels) {
    const buf = Buffer.from(label, "utf8");
    if (buf.length > 63) throw new RangeError("DNS etiketi 63 baytı aşamaz: " + label);
    parts.push(Buffer.from([buf.length]), buf);
  }
  parts.push(Buffer.from([0]));
  return Buffer.concat(parts);
}

/**
 * Adı çözer; 0xC0 sıkıştırma işaretçilerini takip eder.
 * @returns {{ name: string, offset: number }} offset = ad bittikten SONRAKİ konum
 */
export function decodeName(buf, offset) {
  const labels = [];
  let pos = offset;
  let end = -1;
  let jumps = 0;
  for (;;) {
    if (pos >= buf.length) throw new RangeError("mDNS: ad tampon dışına taştı");
    const len = buf[pos];
    if (len === 0) { pos += 1; break; }
    if ((len & 0xc0) === 0xc0) {
      if (pos + 1 >= buf.length) throw new RangeError("mDNS: eksik sıkıştırma işaretçisi");
      if (++jumps > 16) throw new RangeError("mDNS: sıkıştırma döngüsü");
      const ptr = ((len & 0x3f) << 8) | buf[pos + 1];
      if (end < 0) end = pos + 2;
      pos = ptr;
      continue;
    }
    if ((len & 0xc0) !== 0) throw new RangeError("mDNS: bilinmeyen etiket tipi");
    pos += 1;
    if (pos + len > buf.length) throw new RangeError("mDNS: etiket tampon dışına taştı");
    labels.push(buf.toString("utf8", pos, pos + len));
    pos += len;
  }
  return { name: labels.join("."), offset: end >= 0 ? end : pos };
}

/** TXT rdata: her anahtar için uzunluk önekli "key=value". */
export function encodeTxt(record) {
  const entries = Object.entries(record || {});
  if (!entries.length) return Buffer.from([0]); // boş TXT = tek sıfır bayt
  const parts = [];
  for (const [k, v] of entries) {
    const s = Buffer.from(v === true ? String(k) : String(k) + "=" + String(v), "utf8");
    if (s.length > 255) throw new RangeError("TXT girdisi 255 baytı aşamaz: " + k);
    parts.push(Buffer.from([s.length]), s);
  }
  return Buffer.concat(parts);
}

export function decodeTxt(buf) {
  const out = {};
  let pos = 0;
  while (pos < buf.length) {
    const len = buf[pos];
    pos += 1;
    if (len === 0) continue;
    if (pos + len > buf.length) break;
    const s = buf.toString("utf8", pos, pos + len);
    pos += len;
    const eq = s.indexOf("=");
    if (eq < 0) out[s] = true;
    else out[s.slice(0, eq)] = s.slice(eq + 1);
  }
  return out;
}

/** SRV rdata: priority, weight, port, target. */
export function encodeSrv({ priority = 0, weight = 0, port, target }) {
  const head = Buffer.alloc(6);
  head.writeUInt16BE(priority, 0);
  head.writeUInt16BE(weight, 2);
  head.writeUInt16BE(port, 4);
  return Buffer.concat([head, encodeName(target)]);
}

/** A rdata: noktalı IPv4. Geçersizse atar — yanlış adres yayınlamaktansa patla. */
export function encodeIPv4(ip) {
  const parts = String(ip || "").split(".");
  if (parts.length !== 4) throw new RangeError("geçersiz IPv4: " + ip);
  const buf = Buffer.alloc(4);
  for (let i = 0; i < 4; i++) {
    const n = Number(parts[i]);
    if (!Number.isInteger(n) || n < 0 || n > 255) throw new RangeError("geçersiz IPv4: " + ip);
    buf[i] = n;
  }
  return buf;
}

function encodeRecord(rr) {
  const name = encodeName(rr.name);
  const head = Buffer.alloc(8);
  head.writeUInt16BE(rr.type, 0);
  head.writeUInt16BE((rr.class ?? CLASS_IN) | (rr.flush ? CLASS_FLUSH : 0), 2);
  head.writeUInt32BE(rr.ttl ?? 120, 4);
  const len = Buffer.alloc(2);
  len.writeUInt16BE(rr.rdata.length, 0);
  return Buffer.concat([name, head, len, rr.rdata]);
}

export function encodeMessage({ id = 0, flags = FLAG_RESPONSE, questions = [], answers = [], additionals = [] }) {
  const header = Buffer.alloc(12);
  header.writeUInt16BE(id, 0);
  header.writeUInt16BE(flags, 2);
  header.writeUInt16BE(questions.length, 4);
  header.writeUInt16BE(answers.length, 6);
  header.writeUInt16BE(0, 8);
  header.writeUInt16BE(additionals.length, 10);

  const parts = [header];
  for (const qd of questions) {
    const cls = Buffer.alloc(4);
    cls.writeUInt16BE(qd.type, 0);
    cls.writeUInt16BE(qd.class ?? CLASS_IN, 2);
    parts.push(encodeName(qd.name), cls);
  }
  for (const rr of [...answers, ...additionals]) parts.push(encodeRecord(rr));
  return Buffer.concat(parts);
}

/**
 * Gelen paketi çözer. Yayıncının ihtiyacı olan tek şey sorular; kayıtlar da
 * gidiş-dönüş testleri için çözülür.
 * Bozuk paket ATILIR (null) — ağdan gelen her şeye güvenilmez.
 */
export function decodeMessage(buf) {
  try {
    if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
    const id = buf.readUInt16BE(0);
    const flags = buf.readUInt16BE(2);
    const counts = [buf.readUInt16BE(4), buf.readUInt16BE(6), buf.readUInt16BE(8), buf.readUInt16BE(10)];
    let pos = 12;

    const questions = [];
    for (let i = 0; i < counts[0]; i++) {
      const n = decodeName(buf, pos);
      pos = n.offset;
      if (pos + 4 > buf.length) return null;
      questions.push({ name: n.name, type: buf.readUInt16BE(pos), class: buf.readUInt16BE(pos + 2) & 0x7fff });
      pos += 4;
    }

    const readRecords = (count) => {
      const out = [];
      for (let i = 0; i < count; i++) {
        const n = decodeName(buf, pos);
        pos = n.offset;
        if (pos + 10 > buf.length) return out;
        const type = buf.readUInt16BE(pos);
        const rawClass = buf.readUInt16BE(pos + 2);
        const ttl = buf.readUInt32BE(pos + 4);
        const rdlength = buf.readUInt16BE(pos + 8);
        pos += 10;
        if (pos + rdlength > buf.length) return out;
        out.push({
          name: n.name,
          type,
          class: rawClass & 0x7fff,
          flush: !!(rawClass & CLASS_FLUSH),
          ttl,
          rdata: buf.subarray(pos, pos + rdlength),
        });
        pos += rdlength;
      }
      return out;
    };

    const answers = readRecords(counts[1]);
    readRecords(counts[2]); // authority — kullanılmıyor
    const additionals = readRecords(counts[3]);
    return { id, flags, questions, answers, additionals };
  } catch {
    return null;
  }
}

/**
 * DNS-SD servis kayıt kümesini kurar (PTR + SRV + TXT + A).
 * Saf: soket yok, saat yok. `announce-mdns.mjs` bunu alıp yollar.
 */
export function serviceRecords({ instance, serviceType, hostname, ip, port, txt = {}, ttl = 120 }) {
  const service = serviceType + ".local";
  const fqdn = instance + "." + service;
  const host = hostname + ".local";
  return {
    fqdn,
    service,
    host,
    ptr: { name: service, type: TYPE.PTR, ttl, rdata: encodeName(fqdn) },
    srv: { name: fqdn, type: TYPE.SRV, ttl, flush: true, rdata: encodeSrv({ port, target: host }) },
    txt: { name: fqdn, type: TYPE.TXT, ttl, flush: true, rdata: encodeTxt(txt) },
    a: { name: host, type: TYPE.A, ttl, flush: true, rdata: encodeIPv4(ip) },
  };
}

/**
 * Bir sorguya hangi kayıtlarla cevap verileceğine karar verir — SAF.
 * İlgisiz sorguya boş dizi döner (ağı gereksiz doldurmayız).
 */
export function answersFor(questions, records) {
  const out = [];
  const add = (rr) => { if (!out.includes(rr)) out.push(rr); };
  for (const qd of questions || []) {
    const name = String(qd.name || "").toLowerCase();
    const wantsAny = qd.type === TYPE.ANY;
    if (name === records.service.toLowerCase() && (wantsAny || qd.type === TYPE.PTR)) {
      add(records.ptr); add(records.srv); add(records.txt); add(records.a);
    } else if (name === records.fqdn.toLowerCase()) {
      if (wantsAny || qd.type === TYPE.SRV) { add(records.srv); add(records.a); }
      if (wantsAny || qd.type === TYPE.TXT) add(records.txt);
    } else if (name === records.host.toLowerCase() && (wantsAny || qd.type === TYPE.A)) {
      add(records.a);
    }
  }
  return out;
}
