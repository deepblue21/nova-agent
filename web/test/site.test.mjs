// Web pure-helper tests — run with: npm --prefix web test  (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { extractWebsite } from "../src/lib/site.mjs";

test("extractWebsite: detects a full HTML document in a fenced block", () => {
  const reply = "İşte sayfan:\n```html\n<!doctype html><html><body><h1>Selam</h1></body></html>\n```\nUmarım beğenirsin.";
  const site = extractWebsite(reply);
  assert.ok(site && site.includes("<h1>Selam</h1>"));
  assert.ok(/^<!doctype html/i.test(site));
});

test("extractWebsite: <html> without a doctype still counts", () => {
  assert.ok(extractWebsite("```html\n<html><head></head><body>x</body></html>\n```"));
});

test("extractWebsite: ignores snippets without a full document, and bad input", () => {
  assert.equal(extractWebsite("```html\n<div class='x'>hi</div>\n```"), null);
  assert.equal(extractWebsite("sadece düz metin, kod yok"), null);
  assert.equal(extractWebsite(""), null);
  assert.equal(extractWebsite(null), null);
  assert.equal(extractWebsite(undefined), null);
});
