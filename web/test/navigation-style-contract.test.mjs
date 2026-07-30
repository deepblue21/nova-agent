import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { createServer } from "vite";

import { SHELL_CSS } from "../src/styles/shell.mjs";

const webRoot = fileURLToPath(new URL("..", import.meta.url));
const viteCacheDir = await mkdtemp(join(tmpdir(), "nova-navigation-test-"));
let vite;
let SideRail;
let TopBar;

before(async () => {
  vite = await createServer({
    root: webRoot,
    cacheDir: viteCacheDir,
    configFile: false,
    appType: "custom",
    logLevel: "silent",
    optimizeDeps: { noDiscovery: true },
    server: { middlewareMode: true },
  });

  ({ SideRail } = await vite.ssrLoadModule("/src/layout/SideRail.jsx"));
  ({ TopBar } = await vite.ssrLoadModule("/src/layout/TopBar.jsx"));
});

after(async () => {
  await vite?.close();
  await rm(viteCacheDir, { recursive: true, force: true });
});

function cssBlocks(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return [...SHELL_CSS.matchAll(new RegExp(`${escaped}\\s*\\{([^}]+)\\}`, "g"))]
    .map((match) => match[1].replace(/\s+/g, " ").trim());
}

function cssBlockWith(selector, declaration) {
  const block = cssBlocks(selector).find((candidate) => candidate.includes(declaration));
  assert.ok(block, `${selector} should include ${declaration}`);
  return block;
}

function findElement(node, predicate) {
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findElement(child, predicate);
      if (found) return found;
    }
    return null;
  }

  if (!node || typeof node !== "object" || !("props" in node)) return null;
  if (predicate(node)) return node;
  return findElement(node.props.children, predicate);
}

test("compact mobile navigation keeps its bar and tap-target accessibility contract", () => {
  const bar = cssBlockWith(".bottom-nav", "height: calc(58px + env(safe-area-inset-bottom, 0px))");
  assert.match(bar, /backdrop-filter:\s*blur\(20px\)/);
  assert.match(bar, /-webkit-backdrop-filter:\s*blur\(20px\)/);

  const button = cssBlockWith(".bottom-nav button", "min-height: 44px");
  assert.match(button, /min-width:\s*44px/);

  const active = cssBlockWith(".bottom-nav button.on .bn-ic", "transform: translateY(");
  const lift = Number(active.match(/translateY\(-([0-9.]+)px\)/)?.[1]);
  assert.ok(Number.isFinite(lift) && lift <= 2, "active icon lift must be no more than 2px");
  assert.match(active, /background:\s*rgba\(var\(--accent-rgb\),\s*0\.\d+\)/);
  assert.doesNotMatch(active, /background:\s*var\(--accent\)/);
});

test("desktop rail is narrower while retaining 44px targets and explicit focus rings", () => {
  const rail = cssBlockWith(".rail", "width:");
  const width = Number(rail.match(/width:\s*([0-9.]+)px/)?.[1]);
  assert.ok(Number.isFinite(width) && width <= 248, "desktop rail should stay compact");

  const navItem = cssBlockWith(".nav-item", "min-height: 44px");
  assert.match(navItem, /min-height:\s*44px/);

  const focus = cssBlockWith(
    ".nav-item:focus-visible, .bottom-nav button:focus-visible, .settings-motion:focus-visible",
    "outline: 2px solid var(--accent)",
  );
  assert.match(focus, /outline-offset:\s*2px/);
});

test("rail and top-bar settings controls provide immediate bounded press feedback", () => {
  let railSettingsCalls = 0;
  let topBarSettingsCalls = 0;

  const railTree = SideRail({
    view: "kontrol",
    onView() {},
    taskBadge: 0,
    convs: [],
    activeId: null,
    onOpenConv() {},
    onNewConv() {},
    onDeleteConv() {},
    search: "",
    onSearch() {},
    hasMessages: false,
    onExport() {},
    onShare() {},
    shareNote: "",
    onSettings() { railSettingsCalls += 1; },
    authEmail: "",
  });
  const railSettings = findElement(
    railTree,
    (element) => element.type === "button" && element.props.className?.includes("settings-motion"),
  );
  assert.ok(railSettings, "rail settings button should opt into settings motion");
  railSettings.props.onClick({ stopPropagation() {} });
  assert.equal(railSettingsCalls, 1, "rail settings callback should run in the click handler");

  const topBarTree = TopBar({
    subtitle: "Hazır",
    statusTone: "ok",
    modelName: "Model",
    modelId: "",
    connected: true,
    openDD: null,
    setOpenDD() {},
    models: [],
    selectedModelId: "",
    onPickModel() {},
    live: false,
    modelsErr: "",
    onRefreshModels() {},
    view: "kontrol",
    onToggleVoice() {},
    onOpenDrawer() {},
    onSettings() { topBarSettingsCalls += 1; },
  });
  const topBarSettings = findElement(
    topBarTree,
    (element) => element.type === "button"
      && element.props["aria-label"] === "Ayarlar"
      && element.props.className?.includes("settings-motion"),
  );
  assert.ok(topBarSettings, "top-bar settings button should opt into settings motion");
  topBarSettings.props.onClick({ stopPropagation() {} });
  assert.equal(topBarSettingsCalls, 1, "top-bar settings callback should run in the click handler");

  const pressed = cssBlockWith(".settings-motion:active", "transform: scale(.96)");
  assert.match(pressed, /transform:\s*scale\(\.96\)/);

  const iconPressed = cssBlockWith(".settings-motion:active svg", "transform: rotate(");
  const rotation = Number(iconPressed.match(/rotate\(([0-9.]+)deg\)/)?.[1]);
  assert.ok(Number.isFinite(rotation) && rotation <= 22, "settings rotation must not exceed 22deg");

  const reduced = cssBlockWith(
    ".settings-motion:active, .settings-motion:active svg, .bottom-nav button.on .bn-ic",
    "transform: none !important",
  );
  assert.match(reduced, /transition:\s*none !important/);
});
