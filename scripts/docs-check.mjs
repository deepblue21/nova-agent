#!/usr/bin/env node
// docs-check.mjs — dokümanlardaki sayısal iddiaları koddan doğrular.
//
// Amaç: README/ROADMAP'teki test ve migration sayıları koddan sapınca
// (ör. süit büyüyünce) CI'da yakalamak. Yalnız statik sayılabilen şeyleri
// denetler; gateway'in çalışma zamanı test sayısı (node --test alt testleri)
// kasıtlı olarak kapsam dışıdır.
//
// Kullanım: node scripts/docs-check.mjs   (repo kökünden; `npm run docs-check`)
// Çıkış: uyuşmazlık varsa 1, yoksa 0.

import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const failures = [];
const read = (p) => readFileSync(p, "utf8");

function countMatches(text, re) {
  return (text.match(re) ?? []).length;
}

function walkFiles(dir, ext) {
  return readdirSync(dir, { recursive: true, withFileTypes: false })
    .filter((f) => String(f).endsWith(ext))
    .map((f) => join(dir, String(f)));
}

function countInTree(dir, ext, re) {
  let n = 0;
  for (const f of walkFiles(dir, ext)) n += countMatches(read(f), re);
  return n;
}

function checkClaims(file, re, expected, label) {
  const text = read(file);
  const claims = [...text.matchAll(re)];
  if (claims.length === 0) {
    failures.push(`${file}: '${label}' iddiası bulunamadı (belge mi değişti?)`);
    return;
  }
  for (const m of claims) {
    const got = m.slice(1).map(Number);
    if (got.some((v, i) => v !== expected[i])) {
      failures.push(
        `${file}: '${m[0].trim()}' — kod ${expected.join("+")} diyor (${label})`,
      );
    }
  }
}

// --- 1) Android birim + enstrümanlı test sayıları -------------------------
const androidUnit = countInTree("nova-android/app/src/test", ".kt", /@Test\b/g);
const androidInstr = countInTree("nova-android/app/src/androidTest", ".kt", /@Test\b/g);
const birimRe = /(\d+)\s*birim\s*\+\s*(\d+)\s*enstrümanlı/gu;
checkClaims("ROADMAP.md", birimRe, [androidUnit, androidInstr], "birim+enstrümanlı");
checkClaims("nova-android/README.md", birimRe, [androidUnit, androidInstr], "birim+enstrümanlı");

// --- 2) Migration aralığı --------------------------------------------------
const migMax = readdirSync("gateway/migrations")
  .map((f) => Number(f.slice(0, 3)))
  .filter(Number.isFinite)
  .sort((a, b) => a - b)
  .at(-1);
const migStr = String(migMax).padStart(3, "0");
if (!read("README.md").includes(`\`001\` through \`${migStr}\``)) {
  failures.push(`README.md: migration aralığı '001 through ${migStr}' değil`);
}
if (!read("README.tr.md").includes(`\`001\` ile \`${migStr}\` arası`)) {
  failures.push(`README.tr.md: migration aralığı '001 ile ${migStr} arası' değil`);
}

// --- 3) mobile-worker test sayıları (Node smoke + Python) ------------------
const pyTests = countInTree("mobile-worker/tests", ".py", /^\s*(?:async\s+)?def test/gm);
const nodeSmoke =
  countMatches(read("scripts/smoke-mobile-control-plane.test.mjs"), /^\s*test\(/gm) +
  countMatches(read("scripts/smoke-mobilerun-worker.test.mjs"), /^\s*test\(/gm);
const workerRe = /(\d+)\s*Node\s*\+\s*(\d+)\s*Python\s*test/g;
checkClaims("README.md", workerRe, [nodeSmoke, pyTests], "Node+Python");
checkClaims("README.tr.md", workerRe, [nodeSmoke, pyTests], "Node+Python");

// --- Sonuç -----------------------------------------------------------------
console.log(
  `sayımlar: android birim=${androidUnit} enstrümanlı=${androidInstr} ` +
    `migration=${migStr} python=${pyTests} node-smoke=${nodeSmoke}`,
);
if (failures.length) {
  console.error("\ndocs-check BAŞARISIZ:");
  for (const f of failures) console.error("  - " + f);
  console.error("\nBelgeyi ya da (yanlışsa) bu denetimi güncelle.");
  process.exit(1);
}
console.log("docs-check OK — doküman sayıları kodla uyumlu.");
