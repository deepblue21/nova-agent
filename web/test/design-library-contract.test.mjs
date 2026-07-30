import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const libraryPath = resolve(repoRoot, "docs/design/Nova İkon.dc.html");
const libraryHtml = readFileSync(libraryPath, "utf8");
const tokens = JSON.parse(
  readFileSync(resolve(repoRoot, "design/nova-tokens.json"), "utf8"),
);

const expectedVariants = [
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

function tagsWith(attribute) {
  return [...libraryHtml.matchAll(new RegExp(`<[^>]+\\b${attribute}="[^"]+"[^>]*>`, "g"))]
    .map((match) => match[0]);
}

function attribute(tag, name) {
  return tag.match(new RegExp(`\\b${name}="([^"]+)"`))?.[1];
}

function relativeLuminance(hex) {
  const channels = hex
    .slice(1)
    .match(/.{2}/g)
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) =>
      channel <= 0.04045
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4,
    );

  return (
    0.2126 * channels[0]
    + 0.7152 * channels[1]
    + 0.0722 * channels[2]
  );
}

test("renders every Pulse Aperture source asset declared by the design tokens", () => {
  const variantImages = tagsWith("data-variant");
  const renderedVariants = variantImages.map((tag) => attribute(tag, "data-variant"));

  assert.deepEqual(renderedVariants, expectedVariants);
  assert.deepEqual(
    tokens.accents.map((accent) => accent.id),
    expectedVariants,
  );

  for (const [index, tag] of variantImages.entries()) {
    const src = attribute(tag, "src");
    const tokenAsset = tokens.accents[index].sourceAsset;
    const expectedSrc = `../../${tokenAsset.replaceAll("\\", "/")}`;

    assert.equal(src, expectedSrc);
    assert.equal(existsSync(resolve(dirname(libraryPath), src)), true);
  }
});

test("uses only relative image sources and never embeds or invents SVG artwork", () => {
  assert.doesNotMatch(libraryHtml, /<svg\b/i);
  assert.doesNotMatch(libraryHtml, /data:image\/svg\+xml/i);

  const imageTags = [...libraryHtml.matchAll(/<img\b[^>]*>/g)].map(
    (match) => match[0],
  );
  assert.ok(imageTags.length >= expectedVariants.length);

  for (const tag of imageTags) {
    const src = attribute(tag, "src");
    assert.ok(src);
    assert.doesNotMatch(src, /^(?:[a-z]+:|\/)/i);
    assert.equal(existsSync(resolve(dirname(libraryPath), src)), true);
  }
});

test("demonstrates brand, thinking, and preview use states", () => {
  assert.deepEqual(
    tagsWith("data-state").map((tag) => attribute(tag, "data-state")),
    ["brand", "thinking", "preview"],
  );
});

test("keeps compact-size samples at 96, 72, 48, and 36 pixels", () => {
  const sizeSamples = tagsWith("data-size");
  assert.deepEqual(
    sizeSamples.map((tag) => Number(attribute(tag, "data-size"))),
    [96, 72, 48, 36],
  );

  for (const tag of sizeSamples) {
    const size = attribute(tag, "data-size");
    assert.equal(attribute(tag, "width"), size);
    assert.equal(attribute(tag, "height"), size);
  }
});

test("shows Ruby on a black-dominant surface", () => {
  const [surface] = tagsWith("data-surface");

  assert.ok(surface, "Ruby surface sample is missing");
  assert.equal(attribute(surface, "data-surface"), "ruby-black");
  assert.equal(attribute(surface, "data-accent"), "ruby");

  const sampleHex = attribute(surface, "data-surface-color");
  assert.match(sampleHex, /^#[0-9a-f]{6}$/i);
  assert.ok(relativeLuminance(sampleHex) < 0.01);
});

test("includes an explicit reduced-motion example with animation disabled", () => {
  const [sample] = tagsWith("data-motion");

  assert.ok(sample, "Reduced-motion sample is missing");
  assert.equal(attribute(sample, "data-motion"), "reduced");
  assert.equal(attribute(sample, "data-animation"), "none");
  assert.match(sample, /\bstyle="[^"]*animation:\s*none/i);
  assert.match(libraryHtml, /Azaltılmış hareket/);
});
