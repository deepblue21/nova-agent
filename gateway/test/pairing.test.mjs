import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PAIRING_ALPHABET,
  PAIRING_CODE_LENGTH,
  newPairingCode,
  normalizePairingCode,
  formatPairingCode,
  pairBaseUrl,
  buildPairUri,
  parsePairUri,
} from "../lib/pairing.mjs";

test("alfabede görsel ikizler yok (I, L, O, U)", () => {
  for (const ch of "ILOU") assert.equal(PAIRING_ALPHABET.includes(ch), false, ch + " alfabede olmamalı");
  assert.equal(PAIRING_ALPHABET.length, 32);
  assert.equal(new Set(PAIRING_ALPHABET).size, 32, "alfabede yinelenen karakter olmamalı");
});

test("newPairingCode doğru uzunlukta ve yalnız alfabeden üretir", () => {
  for (let i = 0; i < 200; i++) {
    const code = newPairingCode();
    assert.equal(code.length, PAIRING_CODE_LENGTH);
    for (const ch of code) assert.ok(PAIRING_ALPHABET.includes(ch), "beklenmeyen karakter: " + ch);
  }
});

test("newPairingCode enjekte edilen rastgeleliği kullanır (deterministik test)", () => {
  const code = newPairingCode(() => 0);
  assert.equal(code, "00000000");
});

test("normalizePairingCode görsel ikizleri düzeltir", () => {
  assert.equal(normalizePairingCode("h7k2-9m4p"), "H7K29M4P");
  assert.equal(normalizePairingCode("O0IL1234"), "00111234", "O→0, I→1, L→1");
  assert.equal(normalizePairingCode("  H7K2 9M4P  "), "H7K29M4P");
  assert.equal(normalizePairingCode("H7K2_9M4P"), "H7K29M4P");
});

test("normalizePairingCode geçersiz girdide kısmi kod uydurmaz", () => {
  assert.equal(normalizePairingCode(""), "");
  assert.equal(normalizePairingCode(null), "");
  assert.equal(normalizePairingCode("H7K2"), "", "kısa");
  assert.equal(normalizePairingCode("H7K29M4PX"), "", "uzun");
  assert.equal(normalizePairingCode("H7K29M4U"), "", "U alfabede yok");
  assert.equal(normalizePairingCode("H7K29M4!"), "", "alfabe dışı");
});

test("formatPairingCode insan gözü için ayırır, geçersizde girdiyi bozmaz", () => {
  assert.equal(formatPairingCode("h7k29m4p"), "H7K2-9M4P");
  assert.equal(formatPairingCode("bozuk"), "bozuk");
});

test("pairBaseUrl her zaman /v1 ile biter", () => {
  assert.equal(pairBaseUrl({ host: "192.168.1.20", port: 8088 }), "http://192.168.1.20:8088/v1");
  assert.equal(pairBaseUrl({ host: "horus.local", port: 443, tls: true }), "https://horus.local/v1");
  assert.equal(pairBaseUrl({ host: "horus.local", port: 80 }), "http://horus.local/v1");
  assert.equal(pairBaseUrl({ host: "fe80::1", port: 8088 }), "http://[fe80::1]:8088/v1", "IPv6 köşeli paranteze alınır");
});

test("pairBaseUrl geçersiz girdide boş döner", () => {
  assert.equal(pairBaseUrl({ host: "", port: 8088 }), "");
  assert.equal(pairBaseUrl({ host: "1.2.3.4", port: 0 }), "");
  assert.equal(pairBaseUrl({ host: "1.2.3.4", port: 70000 }), "");
  assert.equal(pairBaseUrl({ host: "1.2.3.4", port: "abc" }), "");
});

test("buildPairUri → parsePairUri gidiş-dönüş", () => {
  const uri = buildPairUri({ host: "192.168.1.20", port: 8088, code: "h7k2-9m4p", name: "SALIH-PC" });
  assert.ok(uri.startsWith("horus://pair?"));
  const parsed = parsePairUri(uri);
  assert.deepEqual(parsed, {
    version: 1,
    code: "H7K29M4P",
    host: "192.168.1.20",
    port: 8088,
    tls: false,
    baseUrl: "http://192.168.1.20:8088/v1",
    name: "SALIH-PC",
  });
});

test("buildPairUri tls bayrağını taşır", () => {
  const uri = buildPairUri({ host: "horus.example", port: 443, code: "H7K29M4P", tls: true });
  assert.equal(parsePairUri(uri).baseUrl, "https://horus.example/v1");
  assert.equal(parsePairUri(uri).tls, true);
});

test("buildPairUri bozuk girdide QR üretmez", () => {
  assert.equal(buildPairUri({ host: "1.2.3.4", port: 8088, code: "kısa" }), "");
  assert.equal(buildPairUri({ host: "", port: 8088, code: "H7K29M4P" }), "");
});

test("parsePairUri tanınmayan girdide null döner", () => {
  assert.equal(parsePairUri(""), null);
  assert.equal(parsePairUri(null), null);
  assert.equal(parsePairUri("https://example.com"), null, "başka şema");
  assert.equal(parsePairUri("horus://other?code=H7K29M4P"), null, "başka eylem");
  assert.equal(parsePairUri("horus://pair?v=2&code=H7K29M4P&host=1.2.3.4&port=1"), null, "bilinmeyen sürüm");
  assert.equal(parsePairUri("horus://pair?code=H7K29M4P"), null, "host/port yok");
  assert.equal(parsePairUri("horus://pair?host=1.2.3.4&port=8088"), null, "kod yok");
});

test("parsePairUri v parametresi yoksa 1 varsayar", () => {
  const parsed = parsePairUri("horus://pair?code=H7K29M4P&host=1.2.3.4&port=8088");
  assert.equal(parsed.version, 1);
  assert.equal(parsed.baseUrl, "http://1.2.3.4:8088/v1");
});

test("parsePairUri isim alanını sınırlar", () => {
  const long = "A".repeat(200);
  const parsed = parsePairUri(buildPairUri({ host: "1.2.3.4", port: 8088, code: "H7K29M4P", name: long }));
  assert.equal(parsed.name.length, 64);
});
