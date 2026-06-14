// Multi-agent orchestration tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { mapLimit, buildSynthesisPrompt, runTeam, parsePlan } from "../lib/multiagent.mjs";

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

test("mapLimit: order preserved + concurrency capped", async () => {
  let active = 0, maxActive = 0;
  const out = await mapLimit([10, 20, 30, 40, 50], 2, async (n) => {
    active++; maxActive = Math.max(maxActive, active);
    await delay(10);
    active--;
    return n * 2;
  });
  assert.deepEqual(out, [20, 40, 60, 80, 100]);
  assert.ok(maxActive <= 2, "concurrency exceeded: " + maxActive);
  assert.deepEqual(await mapLimit([], 3, async (x) => x), []);
});

test("buildSynthesisPrompt: task + results + error marker", () => {
  const p = buildSynthesisPrompt("X araştır", [
    { role: "araştırmacı", content: "bulgu A", ok: true },
    { role: "analist", ok: false, error: "timeout" },
  ]);
  assert.match(p, /Ana görev: X araştır/);
  assert.match(p, /araştırmacı/);
  assert.match(p, /bulgu A/);
  assert.match(p, /\[hata: timeout\]/);
});

test("runTeam: fan-out + sources + synthesis; failing subtask isolated", async () => {
  const runOne = async (_prompt, role) => {
    if (role === "bad") throw new Error("boom");
    return { content: "cevap:" + role, sources: [{ n: 1, url: "http://x/" + role }] };
  };
  const synthesize = async () => ({ content: "SENTEZ" });
  const out = await runTeam({
    task: "görev",
    subtasks: [{ role: "a", prompt: "p1" }, { role: "bad", prompt: "p2" }, { role: "b", prompt: "p3" }],
    runOne, synthesize, concurrency: 2,
  });
  assert.equal(out.results.length, 3);
  assert.equal(out.results[0].content, "cevap:a");
  assert.equal(out.results[1].ok, false);
  assert.equal(out.results[2].content, "cevap:b");
  assert.equal(out.sources.length, 2);           // failing subtask contributed none
  assert.equal(out.synthesis.content, "SENTEZ");
});

test("parsePlan: extracts subtasks from planner JSON (handles fences/prose/bad input)", () => {
  const out = parsePlan('İşte plan:\n```json\n[{"role":"araştırmacı","prompt":"X ara"},{"role":"yazar","prompt":"özetle"}]\n```');
  assert.equal(out.length, 2);
  assert.equal(out[0].role, "araştırmacı");
  assert.equal(out[1].prompt, "özetle");
  assert.equal(parsePlan("plan yok, düz metin"), null);
  assert.equal(parsePlan('[{"role":"a"}]'), null);   // prompt yok → elendi → null
  assert.equal(parsePlan("[bozuk json"), null);
  assert.equal(parsePlan(""), null);
});

test("runTeam: requires subtasks + runOne", async () => {
  await assert.rejects(() => runTeam({ task: "x", subtasks: [], runOne: () => {} }), /subtasks required/);
  await assert.rejects(() => runTeam({ task: "x", subtasks: [{ prompt: "p" }] }), /runOne/);
});
