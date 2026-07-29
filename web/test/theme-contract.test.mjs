import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const tokens = JSON.parse(
  await readFile(new URL("../../design/nova-tokens.json", import.meta.url), "utf8"),
);

const expectedThemeIds = [
  "amethyst",
  "arctic",
  "sapphire",
  "emerald",
  "lime",
  "amber",
  "copper",
  "coral",
  "ruby",
  "lavender",
  "moonstone",
];

test("theme catalog exposes the approved Android and web theme contract", () => {
  assert.deepEqual(
    tokens.accents.map((item) => item.id),
    expectedThemeIds,
  );

  for (const theme of tokens.accents) {
    assert.deepEqual(
      Object.keys(theme.aperture).sort(),
      ["core", "deep", "glow", "light", "mid"],
    );
    assert.equal(
      theme.sourceAsset,
      `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-${theme.id}.svg`,
    );
  }
});

test("ruby keeps crimson in the mark while using black-dominant surfaces", () => {
  const ruby = tokens.accents.find((item) => item.id === "ruby");

  assert.ok(ruby, "ruby theme must exist");
  assert.deepEqual(ruby.surface, {
    bg: "#050507",
    bg2: "#0A0A0D",
    bg3: "#030304",
    tint: "#17070C",
  });
  assert.equal(ruby.aperture.mid, "#EF3158");
  assert.equal(ruby.aperture.deep, "#B80E36");
});
