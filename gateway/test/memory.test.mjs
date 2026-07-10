// Personal memory pure-helper tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildMemoryBlock, mergeMemory } from "../lib/memory_store.mjs";

test("buildMemoryBlock: formats notes, skips empties, '' when none", () => {
  assert.equal(buildMemoryBlock([]), "");
  assert.equal(buildMemoryBlock(null), "");
  assert.equal(buildMemoryBlock([{ content: "  " }, { content: "" }]), "");  // empties → filtered → none
  const skipped = buildMemoryBlock([{ content: "  " }, { content: "x" }]);   // empty dropped, "x" kept
  assert.match(skipped, /- x/);
  assert.doesNotMatch(skipped, /- {2,}/);
  const block = buildMemoryBlock([{ content: "Adı Salih" }, { content: "TypeScript sever" }]);
  assert.match(block, /kalıcı notlar/);
  assert.match(block, /- Adı Salih/);
  assert.match(block, /- TypeScript sever/);
});

test("mergeMemory: prepends to existing system message", () => {
  const out = mergeMemory(
    [{ role: "system", content: "Sen NOVA'sın." }, { role: "user", content: "selam" }],
    "HAFIZA");
  assert.equal(out.length, 2);
  assert.equal(out[0].role, "system");
  assert.match(out[0].content, /Sen NOVA'sın\./);
  assert.match(out[0].content, /HAFIZA/);
  assert.equal(out[1].content, "selam");
});

test("mergeMemory: inserts a new system message when none exists", () => {
  const out = mergeMemory([{ role: "user", content: "selam" }], "HAFIZA");
  assert.equal(out.length, 2);
  assert.equal(out[0].role, "system");
  assert.equal(out[0].content, "HAFIZA");
  assert.equal(out[1].role, "user");
});

test("mergeMemory: empty block returns input unchanged; never mutates", () => {
  const input = [{ role: "user", content: "x" }];
  assert.equal(mergeMemory(input, ""), input);          // same ref, no-op
  const out = mergeMemory(input, "M");
  assert.notEqual(out, input);                           // new array
  assert.equal(input.length, 1);                         // original untouched
});
