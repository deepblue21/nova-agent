import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("appearance picker renders the real Pulse Aperture preview for every theme", async () => {
  const source = await readFile(
    new URL("../src/settings/sections/AppearanceSection.jsx", import.meta.url),
    "utf8",
  );

  assert.match(source, /NovaMark/);
  assert.match(source, /motion="preview"/);
  assert.match(source, /theme-pick-grid/);
  assert.match(source, /aria-pressed=/);
  assert.match(source, /data-theme-id=/);
  assert.doesNotMatch(source, /linear-gradient\(135deg/);
});

test("saved accent hydration normalizes legacy theme IDs", async () => {
  const source = await readFile(
    new URL("../src/app/NovaAgent.jsx", import.meta.url),
    "utf8",
  );

  assert.match(source, /normalizeThemeId/);
  assert.match(source, /setAccent\(normalizeThemeId\(g\.accent\)\)/);
});
