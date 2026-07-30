import { test } from "node:test";
import assert from "node:assert/strict";

import { normalizeThemeId, themePreviewStyle } from "../src/lib/theme.mjs";

test("legacy theme IDs migrate to the approved catalog", () => {
  const legacyCases = {
    aurora: "arctic",
    nova: "arctic",
    plum: "lavender",
    violet: "amethyst",
    aperture: "amethyst",
    kizil: "ruby",
    okyanus: "sapphire",
    zumrut: "emerald",
    gul: "coral",
    gunbatimi: "copper",
    amber: "amber",
  };

  for (const [legacyId, expectedId] of Object.entries(legacyCases)) {
    assert.equal(normalizeThemeId(legacyId), expectedId);
  }

  assert.equal(normalizeThemeId("unknown"), "amethyst");
  assert.equal(normalizeThemeId(null), "amethyst");
});

test("theme preview styles expose the complete Pulse Aperture palette", () => {
  assert.deepEqual(themePreviewStyle("ruby"), {
    "--ap-light": "#FF8F9F",
    "--ap-mid": "#EF3158",
    "--ap-mid-rgb": "239,49,88",
    "--ap-deep": "#B80E36",
    "--ap-glow": "#FFAD9E",
    "--ap-glow-rgb": "255,173,158",
    "--ap-core": "#FFF9F2",
  });
});
