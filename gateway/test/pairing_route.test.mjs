import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import request from "supertest";
import { createPairingRouter, createFailureLimiter, PAIR_CLAIM_PATH } from "../routes/pairing.mjs";

function app({ store, enabled = true, limiter } = {}) {
  const a = express();
  a.use(express.json());
  a.use(createPairingRouter({ store, enabled, limiter }));
  return a;
}

const okStore = (apiKey = "nv_abc123_secret") => ({
  claim: async () => ({ ok: true, apiKey, label: "phone" }),
});

test("geçerli kod tek seferlik anahtar döner", async () => {
  const res = await request(app({ store: okStore() })).post(PAIR_CLAIM_PATH).send({ code: "H7K2-9M4P" });
  assert.equal(res.status, 200);
  assert.equal(res.body.api_key, "nv_abc123_secret");
  assert.equal(res.body.label, "phone");
});

test("kod normalize edilerek depoya geçer", async () => {
  const seen = [];
  const store = { claim: async (code) => { seen.push(code); return { ok: true, apiKey: "nv_x", label: "phone" }; } };
  await request(app({ store })).post(PAIR_CLAIM_PATH).send({ code: "h7k2-9m4p" });
  assert.deepEqual(seen, ["H7K29M4P"]);
});

test("biçimsiz kod depoya hiç ulaşmaz", async () => {
  let called = false;
  const store = { claim: async () => { called = true; return { ok: true }; } };
  const res = await request(app({ store })).post(PAIR_CLAIM_PATH).send({ code: "kısa" });
  assert.equal(res.status, 400);
  assert.equal(called, false, "geçersiz biçim veritabanına gitmemeli");
});

test("gövde yoksa 400", async () => {
  const res = await request(app({ store: okStore() })).post(PAIR_CLAIM_PATH).send({});
  assert.equal(res.status, 400);
});

test("bilinmeyen kod 404, süresi dolmuş 410, kullanılmış 410", async () => {
  const cases = [
    ["not_found", 404],
    ["expired", 410],
    ["claimed", 410],
  ];
  for (const [reason, status] of cases) {
    const store = { claim: async () => ({ ok: false, reason }) };
    const res = await request(app({ store })).post(PAIR_CLAIM_PATH).send({ code: "H7K29M4P" });
    assert.equal(res.status, status, reason + " → " + status);
    assert.ok(res.body.error, reason + " için hata mesajı olmalı");
    assert.equal(res.body.api_key, undefined, "hata yanıtında anahtar sızmamalı");
  }
});

test("depo patlarsa 500 döner ve ayrıntı sızmaz", async () => {
  const store = { claim: async () => { throw new Error("connection refused to 10.0.0.5"); } };
  const res = await request(app({ store })).post(PAIR_CLAIM_PATH).send({ code: "H7K29M4P" });
  assert.equal(res.status, 500);
  assert.ok(!JSON.stringify(res.body).includes("10.0.0.5"), "iç ayrıntı yanıta sızmamalı");
});

test("PAIRING_ENABLED kapalıyken 503", async () => {
  const res = await request(app({ store: okStore(), enabled: false })).post(PAIR_CLAIM_PATH).send({ code: "H7K29M4P" });
  assert.equal(res.status, 503);
});

test("başarısız denemeler IP başına sınırlanır", async () => {
  const store = { claim: async () => ({ ok: false, reason: "not_found" }) };
  const limiter = createFailureLimiter({ maxFailures: 3, windowMs: 60_000 });
  const a = app({ store, limiter });
  for (let i = 0; i < 3; i++) {
    const r = await request(a).post(PAIR_CLAIM_PATH).send({ code: "H7K29M4P" });
    assert.equal(r.status, 404, "deneme " + (i + 1));
  }
  const blocked = await request(a).post(PAIR_CLAIM_PATH).send({ code: "H7K29M4P" });
  assert.equal(blocked.status, 429);
  assert.ok(Number(blocked.headers["retry-after"]) > 0, "Retry-After başlığı olmalı");
});

// --- saf sınırlayıcı ---

test("limiter penceresi dolunca sıfırlanır", () => {
  let t = 1000;
  const l = createFailureLimiter({ maxFailures: 2, windowMs: 500, now: () => t });
  l.fail("ip"); l.fail("ip");
  assert.equal(l.blocked("ip"), true);
  t += 501;
  assert.equal(l.blocked("ip"), false, "pencere geçince serbest");
});

test("başarılı takas IP sayacını sıfırlar (aynı evdeki ikinci telefon cezalanmaz)", () => {
  const l = createFailureLimiter({ maxFailures: 2, windowMs: 60_000 });
  l.fail("ip");
  l.succeed("ip");
  l.fail("ip");
  assert.equal(l.blocked("ip"), false);
});

test("limiter IP'leri birbirinden ayırır", () => {
  const l = createFailureLimiter({ maxFailures: 1, windowMs: 60_000 });
  l.fail("a");
  assert.equal(l.blocked("a"), true);
  assert.equal(l.blocked("b"), false);
});

test("sweep süresi geçmiş kayıtları atar (bellek sızıntısı yok)", () => {
  let t = 0;
  const l = createFailureLimiter({ maxFailures: 5, windowMs: 100, now: () => t });
  l.fail("a"); l.fail("b");
  assert.equal(l.sweep(), 2);
  t = 200;
  assert.equal(l.sweep(), 0);
});
