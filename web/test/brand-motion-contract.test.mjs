import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";
import { brandCss } from "../src/styles/brand.mjs";

let vite;
let NovaMark;
let cacheDir;

before(async () => {
  cacheDir = await mkdtemp(join(tmpdir(), "nova-brand-motion-"));
  vite = await createServer({
    root: fileURLToPath(new URL("..", import.meta.url)),
    cacheDir,
    configFile: false,
    logLevel: "silent",
    server: { middlewareMode: true },
  });
  ({ NovaMark } = await vite.ssrLoadModule("/src/ui/NovaMark.jsx"));
});

after(async () => {
  await vite?.close();
  await rm(cacheDir, { force: true, recursive: true });
});

function renderMark(props = {}) {
  return renderToStaticMarkup(React.createElement(NovaMark, props));
}

test("NovaMark renders brand, thinking, and preview as distinct motion modes", () => {
  assert.match(
    renderMark({ className: "legacy-class" }),
    /class="nova-mark is-animated is-brand legacy-class"/,
  );
  assert.match(renderMark({ motion: "thinking" }), /class="nova-mark is-animated is-thinking"/);
  assert.match(renderMark({ motion: "preview" }), /class="nova-mark is-animated is-preview"/);
  assert.match(renderMark({ animated: false }), /class="nova-mark is-brand"/);
});

test("thinking enables the sparse halo by default while explicit halo choices remain authoritative", () => {
  const thinking = renderMark({ motion: "thinking" });
  const brand = renderMark();

  assert.match(thinking, /class="nm-halo"/);
  assert.equal(
    thinking.match(/<circle/g).length - brand.match(/<circle/g).length,
    15,
    "thinking adds exactly fourteen sparse dots and one halo guide",
  );
  assert.doesNotMatch(renderMark({ motion: "thinking", halo: false }), /class="nm-halo"/);
  assert.match(renderMark({ motion: "brand", halo: true }), /class="nm-halo"/);
  assert.doesNotMatch(brand, /class="nm-halo"/);
});

test("brand CSS scopes generated motion durations to each mode and gates preview motion on its parent", () => {
  const css = brandCss({
    motion: {
      breathMs: 1101,
      corePulseMs: 2202,
      orbitMs: 3303,
      shimmerMs: 4404,
      microTiltDeg: 0.7,
      easing: "linear",
    },
  });

  assert.match(
    css,
    /\.nova-mark\.is-brand\.is-animated \.nm-breath\s*{\s*animation: nmBreath 1101ms linear infinite alternate;/,
  );
  assert.match(
    css,
    /\.nova-mark\.is-thinking\.is-animated \.nm-core\s*{\s*animation: nmCore 2202ms linear infinite alternate;/,
  );
  assert.match(
    css,
    /\.nova-mark\.is-thinking\.is-animated \.nm-orbit\s*{\s*animation: nmOrbit 3303ms linear infinite;/,
  );
  assert.match(
    css,
    /\.is-active-preview > \.nova-mark\.is-preview\.is-animated \.nm-sweep[\s\S]*animation: nmSweep 4404ms linear infinite;/,
  );
  assert.match(css, /:hover > \.nova-mark\.is-preview\.is-animated \.nm-breath/);
  assert.doesNotMatch(
    css,
    /:focus-visible[^{]*\.nova-mark\.is-preview/,
    "keyboard focus alone is not a selected or hovered preview",
  );
});

test("reduced motion disables every Pulse Aperture animation", () => {
  const css = brandCss({
    motion: {
      breathMs: 1,
      corePulseMs: 2,
      orbitMs: 3,
      shimmerMs: 4,
      microTiltDeg: 0.7,
      easing: "linear",
    },
  });

  assert.match(
    css,
    /@media \(prefers-reduced-motion: reduce\)[\s\S]*\.nova-mark \*[\s\S]*animation: none !important;/,
  );
});
