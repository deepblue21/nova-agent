import { test } from "node:test";
import assert from "node:assert/strict";
import {
  TYPE,
  CLASS_IN,
  CLASS_FLUSH,
  encodeName,
  decodeName,
  encodeTxt,
  decodeTxt,
  encodeSrv,
  encodeIPv4,
  encodeMessage,
  decodeMessage,
  serviceRecords,
  answersFor,
} from "./lib/mdns.mjs";

test("encodeName uzunluk önekli etiketler üretir", () => {
  assert.deepEqual([...encodeName("_horus._tcp.local")].slice(0, 8), [6, 0x5f, 0x68, 0x6f, 0x72, 0x75, 0x73, 4]);
  assert.equal(encodeName("local")[encodeName("local").length - 1], 0, "sonlandırıcı sıfır");
});

test("encodeName sondaki noktayı yok sayar", () => {
  assert.deepEqual(encodeName("local."), encodeName("local"));
});

test("encodeName 63 baytı aşan etiketi reddeder", () => {
  assert.throws(() => encodeName("a".repeat(64)), RangeError);
});

test("decodeName gidiş-dönüş", () => {
  const buf = encodeName("SALIH-PC._horus._tcp.local");
  const { name, offset } = decodeName(buf, 0);
  assert.equal(name, "SALIH-PC._horus._tcp.local");
  assert.equal(offset, buf.length);
});

test("decodeName sıkıştırma işaretçisini takip eder", () => {
  // [0] "local" ... sonra 0xC000 işaretçisi
  const target = encodeName("local");
  const ptr = Buffer.from([0xc0, 0x00]);
  const buf = Buffer.concat([target, ptr]);
  const { name, offset } = decodeName(buf, target.length);
  assert.equal(name, "local");
  assert.equal(offset, buf.length, "offset işaretçiden SONRAYA gitmeli, hedefe değil");
});

test("decodeName sıkıştırma döngüsünde patlar (sonsuz döngü yok)", () => {
  const buf = Buffer.from([0xc0, 0x00]); // kendine işaret ediyor
  assert.throws(() => decodeName(buf, 0), RangeError);
});

test("decodeName tampon dışına taşmada patlar", () => {
  assert.throws(() => decodeName(Buffer.from([5, 0x61]), 0), RangeError, "etiket uzunluğu yalan söylüyor");
});

test("TXT gidiş-dönüş", () => {
  const rec = { v: "1", path: "/v1", name: "SALIH-PC" };
  assert.deepEqual(decodeTxt(encodeTxt(rec)), rec);
});

test("boş TXT tek sıfır bayt (RFC 6763)", () => {
  const buf = encodeTxt({});
  assert.equal(buf.length, 1);
  assert.equal(buf[0], 0);
  assert.deepEqual(decodeTxt(buf), {});
});

test("decodeTxt '=' içermeyen girdiyi bayrak sayar", () => {
  assert.deepEqual(decodeTxt(Buffer.from([4, 0x66, 0x6c, 0x61, 0x67])), { flag: true });
});

test("encodeSrv port ve hedefi yazar", () => {
  const buf = encodeSrv({ port: 8088, target: "pc.local" });
  assert.equal(buf.readUInt16BE(0), 0, "priority");
  assert.equal(buf.readUInt16BE(2), 0, "weight");
  assert.equal(buf.readUInt16BE(4), 8088, "port");
  assert.equal(decodeName(buf, 6).name, "pc.local");
});

test("encodeIPv4 dört bayt yazar, geçersizi reddeder", () => {
  assert.deepEqual([...encodeIPv4("192.168.1.20")], [192, 168, 1, 20]);
  assert.throws(() => encodeIPv4("192.168.1"), RangeError);
  assert.throws(() => encodeIPv4("192.168.1.999"), RangeError);
  assert.throws(() => encodeIPv4("fe80::1"), RangeError);
});

test("encodeMessage → decodeMessage gidiş-dönüş (soru)", () => {
  const msg = encodeMessage({ flags: 0, questions: [{ name: "_horus._tcp.local", type: TYPE.PTR, class: CLASS_IN }] });
  const decoded = decodeMessage(msg);
  assert.equal(decoded.questions.length, 1);
  assert.deepEqual(decoded.questions[0], { name: "_horus._tcp.local", type: TYPE.PTR, class: CLASS_IN });
});

test("encodeMessage → decodeMessage gidiş-dönüş (cevaplar + cache-flush)", () => {
  const r = serviceRecords({
    instance: "SALIH-PC", serviceType: "_horus._tcp", hostname: "salih-pc",
    ip: "192.168.1.20", port: 8088, txt: { v: "1", path: "/v1" },
  });
  const decoded = decodeMessage(encodeMessage({ answers: [r.ptr, r.srv, r.txt, r.a] }));
  assert.equal(decoded.answers.length, 4);
  assert.equal(decoded.answers[0].type, TYPE.PTR);
  assert.equal(decodeName(decoded.answers[0].rdata, 0).name, "SALIH-PC._horus._tcp.local");
  assert.equal(decoded.answers[1].flush, true, "SRV cache-flush bitli olmalı");
  assert.equal(decoded.answers[0].flush, false, "PTR paylaşımlı kayıt — flush OLMAMALI");
  assert.deepEqual(decodeTxt(decoded.answers[2].rdata), { v: "1", path: "/v1" });
  assert.deepEqual([...decoded.answers[3].rdata], [192, 168, 1, 20]);
});

test("decodeMessage bozuk paketi atar, patlamaz", () => {
  assert.equal(decodeMessage(Buffer.alloc(3)), null, "kısa paket");
  assert.equal(decodeMessage(null), null);
  assert.equal(decodeMessage("değil"), null);
  const lying = Buffer.alloc(12);
  lying.writeUInt16BE(5, 4); // 5 soru diyor ama gövde boş
  assert.equal(decodeMessage(lying), null);
});

test("CLASS_FLUSH biti sınıf alanından ayrıştırılır", () => {
  const r = serviceRecords({
    instance: "PC", serviceType: "_horus._tcp", hostname: "pc", ip: "10.0.0.2", port: 1,
  });
  const decoded = decodeMessage(encodeMessage({ answers: [r.a] }));
  assert.equal(decoded.answers[0].class, CLASS_IN, "flush biti sınıftan çıkarılmalı");
  assert.equal(decoded.answers[0].flush, true);
  assert.equal(CLASS_FLUSH, 0x8000);
});

// --- answersFor: yayıncının tek karar noktası, saf ---

const records = serviceRecords({
  instance: "SALIH-PC", serviceType: "_horus._tcp", hostname: "salih-pc",
  ip: "192.168.1.20", port: 8088, txt: { v: "1", path: "/v1" },
});

test("servis tipine PTR sorgusu tüm kümeyi döner", () => {
  const out = answersFor([{ name: "_horus._tcp.local", type: TYPE.PTR }], records);
  assert.deepEqual(out.map((r) => r.type), [TYPE.PTR, TYPE.SRV, TYPE.TXT, TYPE.A]);
});

test("örnek adına SRV sorgusu SRV + A döner (ek gidiş-dönüş olmasın)", () => {
  const out = answersFor([{ name: "salih-pc._horus._tcp.local", type: TYPE.SRV }], records);
  assert.deepEqual(out.map((r) => r.type), [TYPE.SRV, TYPE.A]);
});

test("host adına A sorgusu yalnız A döner", () => {
  const out = answersFor([{ name: "salih-pc.local", type: TYPE.A }], records);
  assert.deepEqual(out.map((r) => r.type), [TYPE.A]);
});

test("ANY sorgusu örnek adına SRV+A+TXT döner", () => {
  const out = answersFor([{ name: "salih-pc._horus._tcp.local", type: TYPE.ANY }], records);
  assert.deepEqual(out.map((r) => r.type).sort(), [TYPE.SRV, TYPE.TXT, TYPE.A].sort());
});

test("ad karşılaştırması büyük/küçük harf duyarsız (DNS kuralı)", () => {
  const out = answersFor([{ name: "_HORUS._TCP.LOCAL", type: TYPE.PTR }], records);
  assert.equal(out.length, 4);
});

test("ilgisiz sorguya cevap verilmez (ağ gereksiz doldurulmaz)", () => {
  assert.deepEqual(answersFor([{ name: "_airplay._tcp.local", type: TYPE.PTR }], records), []);
  assert.deepEqual(answersFor([{ name: "baska-pc.local", type: TYPE.A }], records), []);
  assert.deepEqual(answersFor([], records), []);
  assert.deepEqual(answersFor(undefined, records), []);
});

test("aynı kayıt iki soruyla istense bile bir kez döner", () => {
  const out = answersFor(
    [{ name: "_horus._tcp.local", type: TYPE.PTR }, { name: "salih-pc.local", type: TYPE.A }],
    records,
  );
  assert.equal(out.filter((r) => r.type === TYPE.A).length, 1);
});
