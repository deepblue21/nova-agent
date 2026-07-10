// Agent run history pure-helper tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { formatRunTools } from "../lib/agent_runs_store.mjs";

test("formatRunTools: counts repeats and joins", () => {
  assert.equal(formatRunTools(["web_search", "web_search", "calculator"]), "web_search×2, calculator");
  assert.equal(formatRunTools(["doc_search"]), "doc_search");
  assert.equal(formatRunTools([]), "");
  assert.equal(formatRunTools(null), "");
  assert.equal(formatRunTools(["", "  ", "x"]), "x");
});
