// DEFAULT_MODEL / ROUTE_* env değerleri yüklü olmayan bir tag'e işaret
// edebiliyor. pickInstalledModel bu durumda makul bir vekil seçer — ve
// seçtiğini substituted=true ile açıkça bildirir.
import test from "node:test";
import assert from "node:assert/strict";
import { pickInstalledModel } from "../lib/model_catalog.mjs";

const installed = [
  { name: "gemma3:4b" },
  { name: "llama3.1:8b" },
  { name: "qwen3:8b", tools: true },
  { name: "qwen3:latest", tools: true },
];

test("yüklü model aynen kullanılır — vekil seçilmez", () => {
  const r = pickInstalledModel("qwen3:8b", installed);
  assert.equal(r.model, "qwen3:8b");
  assert.equal(r.substituted, false);
  assert.equal(r.reason, "exact");
});

test("aynı taban ad: farklı etiket tercih edilir, :latest öncelikli", () => {
  const r = pickInstalledModel("qwen3:14b", installed);
  assert.equal(r.model, "qwen3:latest");
  assert.equal(r.substituted, true);
  assert.equal(r.reason, "same-base");
});

test("aynı aile: sürüm son eki kırpılarak eşleşir (qwen3.5 → qwen3)", () => {
  const r = pickInstalledModel("qwen3.5:35b", installed);
  assert.equal(r.reason, "same-family");
  assert.ok(r.model.startsWith("qwen3"));
  assert.equal(r.substituted, true);
});

test("aile yoksa araç destekleyen modele düşer", () => {
  const r = pickInstalledModel("mistral-nemo:12b", installed);
  assert.equal(r.reason, "tool-capable");
  assert.equal(r.model, "qwen3:latest");
});

test("hiçbiri yoksa ilk yüklü model", () => {
  const noTools = [{ name: "phi4:14b" }, { name: "gemma3:4b" }];
  const r = pickInstalledModel("mistral:7b", noTools);
  assert.equal(r.reason, "first-installed");
  assert.equal(r.model, "phi4:14b");
  assert.equal(r.substituted, true);
});

test("katalog boşsa (Ollama kapalı) hiçbir şey uydurulmaz", () => {
  const r = pickInstalledModel("qwen3:14b", []);
  assert.equal(r.model, "qwen3:14b");
  assert.equal(r.substituted, false);
  assert.equal(r.reason, "no-catalog");
  // bozuk girdi de çökmez
  assert.equal(pickInstalledModel("x", null).substituted, false);
  assert.equal(pickInstalledModel(null, installed).substituted, true);
});

test("compose'daki örnek değerler gerçekçi bir kuruluma düşüyor", () => {
  // docker-compose.yml varsayılanları — çoğu makinede yüklü değil
  for (const wanted of ["gemma4:latest", "gemma4:e2b", "qwen3.5:35b", "qwen3.6:35b"]) {
    const r = pickInstalledModel(wanted, installed);
    assert.equal(r.substituted, true, wanted);
    assert.ok(installed.some((m) => m.name === r.model), wanted + " → " + r.model);
  }
});
