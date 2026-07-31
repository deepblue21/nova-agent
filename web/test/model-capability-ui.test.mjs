import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";

let vite;
let Dock;
let ModelList;
let cacheDir;

before(async () => {
  cacheDir = await mkdtemp(join(tmpdir(), "nova-capability-ui-"));
  vite = await createServer({
    root: fileURLToPath(new URL("..", import.meta.url)),
    cacheDir,
    configFile: false,
    logLevel: "silent",
    server: { middlewareMode: true },
  });
  ({ Dock } = await vite.ssrLoadModule("/src/layout/Dock.jsx"));
  ({ ModelList } = await vite.ssrLoadModule("/src/ui/ModelList.jsx"));
});

after(async () => {
  await vite?.close();
  await rm(cacheDir, { force: true, recursive: true });
});

function DummyIcon() {
  return React.createElement("span", null, "N");
}

function renderDock(curItem) {
  return renderToStaticMarkup(React.createElement(Dock, {
    view: "sohbet",
    onView() {},
    curItem: { icon: DummyIcon, ...curItem },
    models: [],
    modelId: "m",
    onPickModel() {},
    live: true,
    modelsErr: "",
    onRefreshModels() {},
    openDD: null,
    setOpenDD() {},
    effort: "balanced",
    onEffort() {},
    reasoning: true,
    onReasoning() {},
    agentMode: true,
    onAgentMode() {},
    teamMode: true,
    onTeamMode() {},
  }));
}

test("Dock: desteklenmeyen modelde düşünme ve araç kontrolleri görünür fakat pasiftir", () => {
  const html = renderDock({
    name: "Hermes 3",
    thinking: false,
    thinkingSource: "probe",
    thinkingMode: "none",
    tools: false,
    toolsSource: "probe",
  });

  assert.equal((html.match(/class="eff-opt[^"]*"[^>]*disabled=""/g) || []).length, 4);
  assert.match(html, /class="toggle-btn[^"]*"[^>]*disabled=""[^>]*>.*Düşünme/s);
  assert.match(html, /class="toggle-btn ember[^"]*"[^>]*disabled=""[^>]*>.*Ajan/s);
  assert.match(html, /class="toggle-btn ember[^"]*"[^>]*disabled=""[^>]*>.*Takım/s);
  assert.doesNotMatch(html, /class="toggle-btn ember on"/, "pasif araç tercihi etkin renkte kalmamalı");
  assert.match(html, /tabindex="0"[^>]*class="cap-info"/);
  assert.match(html, /role="tooltip">Hermes 3 düşünme özelliğini desteklemiyor\./);
  assert.match(html, /role="tooltip">Hermes 3 araç çağırmayı desteklemiyor; ajan, takım ve web araması kullanılamaz\./);
});

test("Dock: doğrulanmış modelde yetenek kontrolleri kullanılabilir kalır", () => {
  const html = renderDock({
    name: "Qwen 3.5",
    thinking: true,
    thinkingSource: "probe",
    thinkingMode: "toggle",
    tools: true,
    toolsSource: "probe",
  });

  assert.equal((html.match(/class="eff-opt[^"]*"[^>]*disabled=""/g) || []).length, 0);
  assert.doesNotMatch(html, /class="cap-info"/);
  assert.doesNotMatch(html, /class="toggle-btn[^"]*" disabled=""/);
});

test("ModelList: ölçülmüş araç ve düşünme yeteneklerini ayrı rozetler", () => {
  const html = renderToStaticMarkup(React.createElement(ModelList, {
    groups: [{ group: "Yerel", items: [{
      id: "qwen",
      name: "qwen3.5:9b",
      desc: "9B",
      icon: DummyIcon,
      available: true,
      tools: true,
      toolsVerified: true,
      thinking: true,
      thinkingVerified: true,
    }] }],
    modelId: "qwen",
    onPick() {},
    live: true,
    err: "",
    onRefresh() {},
  }));

  assert.match(html, />araç</);
  assert.match(html, />düşünme</);
});
