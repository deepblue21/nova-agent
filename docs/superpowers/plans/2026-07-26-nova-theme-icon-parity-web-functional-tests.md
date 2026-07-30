# NOVA Theme, Animated Icon, and Functional UI Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the eleven Android-derived Pulse Aperture themes in both web and Android, make Ruby/Kızıl black-dominant, add compact matte/blur web navigation, and verify the resulting UI and core functional flows.

**Architecture:** `design/nova-tokens.json` remains the only hand-edited runtime design manifest. The token generator produces the web and Android catalogs, while the existing `NovaMark`/`NovaThinkingIndicator` components consume identical geometry, palette, and motion values. Source SVGs are versioned as design references; runtime components stay token-driven so compact brand and thinking states remain accessible and controllable.

**Tech Stack:** React 18, Vite 8, CSS generated from JavaScript modules, Node `node:test`, Kotlin, Jetpack Compose, Compose UI Test, Gradle, Android emulator.

## Global Constraints

- Ship exactly these theme IDs in this order: `amethyst`, `arctic`, `sapphire`, `emerald`, `lime`, `amber`, `copper`, `coral`, `ruby`, `lavender`, `moonstone`.
- Use the existing `design-previews/nova-pulse-aperture-orbital-v4-*.svg` files as visual sources; do not redraw or approximate them.
- Production motion values remain: body breath `2750 ms`, core pulse `1375 ms`, orbit `4200 ms`, shimmer `5500 ms`, micro tilt `±0.7°`, halo dots `14`.
- Ruby/Kızıl surfaces are black-dominant: `bg #050507`, `bg2 #0A0A0D`, `bg3 #030304`, tint `#17070C`; red is reserved for accents and brand light.
- Web mobile navigation is a compact matte glass dock: `58 px` bar, minimum `44 px` targets, maximum `2 px` active lift, `18–22 px` blur, `180–250 ms` transitions.
- Respect `prefers-reduced-motion: reduce` on web and disabled system animations on Android.
- Add no new runtime dependencies.
- Preserve the existing dirty worktree. Stage only files from the current task.
- Do not delete or overwrite `.git/index.lock`. If it exists, skip commit steps and report the blocked commit while continuing safe verification.

---

### Task 1: Version the Source Assets and Define the Eleven-Theme Contrac

**Files:**
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-amethyst.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-arctic.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-sapphire.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-emerald.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-lime.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-amber.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-copper.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-coral.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-ruby.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-lavender.svg`
- Create: `design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-moonstone.svg`
- Modify: `design/nova-tokens.json`
- Create: `web/test/theme-contract.test.mjs`

**Interfaces:**
- Consumes: the eleven approved SVG files from `design-previews/`.
- Produces: `tokens.accents: ThemeToken[]`, where every entry has `id`, `name`, `primary`, `secondary`, `tertiary`, `onPrimary`, `aperture`, optional `surface`, and `sourceAsset`.

- [ ] **Step 1: Write the failing theme catalog test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const tokens = JSON.parse(await readFile(new URL("../../design/nova-tokens.json", import.meta.url)));
const expected = ["amethyst","arctic","sapphire","emerald","lime","amber","copper","coral","ruby","lavender","moonstone"];

test("theme catalog is the approved eleven-item Android/Web contract", () => {
  assert.deepEqual(tokens.accents.map((item) => item.id), expected);
  for (const item of tokens.accents) {
    assert.deepEqual(Object.keys(item.aperture).sort(), ["core","deep","glow","light","mid"]);
    assert.match(item.sourceAsset, /^design\/brand\/pulse-aperture\/.+\.svg$/);
  }
});

test("ruby is black-dominant and uses the approved crimson palette", () => {
  const ruby = tokens.accents.find((item) => item.id === "ruby");
  assert.deepEqual(ruby.surface, {
    bg: "#050507",
    bg2: "#0A0A0D",
    bg3: "#030304",
    panel: "rgba(255,255,255,0.035)",
    line: "rgba(255,255,255,0.075)",
    blurTint: "#17070C",
    scrim: "rgba(3,3,5,0.72)"
  });
  assert.equal(ruby.aperture.mid, "#EF3158");
  assert.equal(ruby.aperture.deep, "#B80E36");
});
```

- [ ] **Step 2: Run the test and verify the current catalog fails**

Run: `npm --prefix web test -- test/theme-contract.test.mjs`
Expected: FAIL because the current IDs are the legacy accent catalog and `sourceAsset`/`surface` are absent.

- [ ] **Step 3: Copy the eleven approved SVGs without modifying their visual content**

Copy each `design-previews/nova-pulse-aperture-orbital-v4-<id>.svg` to `design/brand/pulse-aperture/`. Verify SHA-256 equality between each source/destination pair.

- [ ] **Step 4: Replace the accent catalog in `design/nova-tokens.json`**

Use the exact table and Ruby surface values from the approved spec. Add `sourceAsset` to all entries. For non-Ruby themes omit `surface` so the global matte defaults apply.

- [ ] **Step 5: Run the contract test**

Run: `npm --prefix web test -- test/theme-contract.test.mjs`
Expected: PASS, 2 tests, 0 failures.

- [ ] **Step 6: Commit the source contract if the repository lock is clear**

```powershell
git add design/nova-tokens.json design/brand/pulse-aperture web/test/theme-contract.test.mjs
git commit -m "feat: define eleven shared NOVA themes"
```

### Task 2: Generate Matching Web/Android Tokens and Migrate Legacy IDs

**Files:**
- Modify: `scripts/sync-design-tokens.mjs`
- Create: `web/src/lib/theme.mjs`
- Create: `web/test/theme-migration.test.mjs`
- Modify (generated): `web/src/design/tokens.generated.mjs`
- Modify (generated): `nova-android/app/src/main/java/com/nova/agent/ui/theme/NovaTokens.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: `ThemeToken[]` from Task 1.
- Produces:
  - `normalizeThemeId(id: unknown): ThemeId`
  - `ACCENTS`, `DEFAULT_ACCENT`, `THEME_ALIASES`
  - optional CSS variables `--bg`, `--bg2`, `--bg3`, `--panel`, `--line`, `--blur-tint`, `--scrim`
  - Kotlin `NovaSurfaceColors?` on `NovaAccent`.

- [ ] **Step 1: Write the failing migration test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeThemeId } from "../src/lib/theme.mjs";

test("legacy IDs migrate to the eleven-theme catalog", () => {
  const cases = {
    aurora: "arctic", nova: "arctic", plum: "lavender",
    violet: "amethyst", aperture: "amethyst", kizil: "ruby",
    okyanus: "sapphire", zumrut: "emerald", gul: "coral",
    gunbatimi: "copper", amber: "amber",
  };
  for (const [legacy, current] of Object.entries(cases)) {
    assert.equal(normalizeThemeId(legacy), current);
  }
  assert.equal(normalizeThemeId("unknown"), "amethyst");
  assert.equal(normalizeThemeId(null), "amethyst");
});
```

- [ ] **Step 2: Verify the helper is missing**

Run: `npm --prefix web test -- test/theme-migration.test.mjs`
Expected: FAIL with `ERR_MODULE_NOT_FOUND`.

- [ ] **Step 3: Implement `normalizeThemeId`**

```js
import { ACCENTS, DEFAULT_ACCENT, THEME_ALIASES } from "../design/tokens.generated.mjs";

const ids = new Set(ACCENTS.map((item) => item.id));

export function normalizeThemeId(value) {
  if (typeof value !== "string") return DEFAULT_ACCENT;
  const mapped = THEME_ALIASES[value] || value;
  return ids.has(mapped) ? mapped : DEFAULT_ACCENT;
}
```

- [ ] **Step 4: Extend the generator**

Generate `THEME_ALIASES`, `sourceAsset`, optional surface CSS overrides, and Kotlin `NovaSurfaceColors`. The Ruby CSS block must override the seven surface variables; other themes inherit base variables.

- [ ] **Step 5: Regenerate both platforms**

Run: `npm run tokens`
Expected: generator reports updates to `web/src/design/tokens.generated.mjs`, `web/public/icon.svg`, and `nova-android/.../NovaTokens.kt`.

- [ ] **Step 6: Make Android `NovaTheme` honor optional surfaces**

Resolve `accent.surface ?: NovaSurfaceColors.default()` and use it for `background`, `surface`, `surfaceContainer`, outline tint, and system bars without changing semantic success/warning/danger colors.

- [ ] **Step 7: Run migration and contract tests**

Run: `npm --prefix web test -- test/theme-contract.test.mjs test/theme-migration.test.mjs`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 8: Commit generated parity if the repository lock is clear**

```powershell
git add scripts/sync-design-tokens.mjs web/src/lib/theme.mjs web/test/theme-migration.test.mjs web/src/design/tokens.generated.mjs web/public/icon.svg nova-android/app/src/main/java/com/nova/agent/ui/theme/NovaTokens.kt nova-android/app/src/main/java/com/nova/agent/ui/theme/Theme.k
git commit -m "feat: generate shared NOVA theme surfaces"
```

### Task 3: Bring Android Motion Modes to the Web Mark

**Files:**
- Modify: `web/src/ui/NovaMark.jsx`
- Modify: `web/src/styles/brand.mjs`
- Create: `web/test/brand-motion-contract.test.mjs`

**Interfaces:**
- Consumes: generated `APERTURE`, CSS theme variables, `variant`/`motion`.
- Produces:
  - `NovaMark({ size, halo, animated, variant, motion, title, className, style })`
  - motion classes `is-brand`, `is-thinking`, `is-preview`
  - existing `NovaThinkingMark` and `NovaBadge` compatibility.

- [ ] **Step 1: Write the failing source contract test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("NovaMark exposes Android-equivalent modes and reduced motion", async () => {
  const jsx = await readFile(new URL("../src/ui/NovaMark.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/styles/brand.mjs", import.meta.url), "utf8");
  assert.match(jsx, /motion = "brand"/);
  assert.match(jsx, /is-thinking/);
  assert.match(jsx, /is-preview/);
  assert.match(css, /prefers-reduced-motion: reduce/);
  assert.match(css, /m\.breathMs/);
  assert.match(css, /m\.corePulseMs/);
  assert.match(css, /m\.orbitMs/);
  assert.match(css, /m\.shimmerMs/);
});
```

- [ ] **Step 2: Verify the new modes fail**

Run: `npm --prefix web test -- test/brand-motion-contract.test.mjs`
Expected: FAIL because `motion` and the two new state classes are absent.

- [ ] **Step 3: Add mode classes without duplicating geometry**

Build the class list from `motion`:

```jsx
const modeClass = motion === "thinking"
  ? " is-thinking"
  : motion === "preview"
    ? " is-preview"
    : " is-brand";
```

Keep halo on only for thinking unless explicitly requested. Keep the production body path, fold path, core, internal sweep, and sparse halo generated from `APERTURE`.

- [ ] **Step 4: Align CSS motion**

Use the generated durations. Brand mode gets restrained breath/core/sweep and no halo. Thinking mode gets breath/core/sweep/orbit. Preview mode animates only while its parent has `.is-active-preview` or `:hover`.

- [ ] **Step 5: Preserve reduced-motion behavior**

The media query must disable every body, aura, core, tilt, orbit, sweep, preview, settings, and navigation animation while retaining visible selected states.

- [ ] **Step 6: Run brand tests and build**

Run: `npm --prefix web test -- test/brand-motion-contract.test.mjs`
Expected: PASS.

Run: `npm --prefix web run build`
Expected: Vite exits 0 with a generated `dist/`.

- [ ] **Step 7: Commit web mark parity if the repository lock is clear**

```powershell
git add web/src/ui/NovaMark.jsx web/src/styles/brand.mjs web/test/brand-motion-contract.test.mjs
git commit -m "feat: align web Pulse Aperture motion with Android"
```

### Task 4: Replace the Web Theme Picker and Persist Migrated Selections

**Files:**
- Modify: `web/src/settings/sections/AppearanceSection.jsx`
- Modify: `web/src/app/NovaAgent.jsx`
- Modify: `web/src/styles/surfaces.mjs`
- Create: `web/test/appearance-contract.test.mjs`

**Interfaces:**
- Consumes: `ACCENTS`, `normalizeThemeId`, `NovaMark`.
- Produces: compact eleven-item theme cards; only selected/hovered preview animates; saved legacy IDs migrate on load.

- [ ] **Step 1: Write the failing appearance contract test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("appearance picker uses NovaMark previews and compact theme cards", async () => {
  const source = await readFile(new URL("../src/settings/sections/AppearanceSection.jsx", import.meta.url), "utf8");
  assert.match(source, /NovaMark/);
  assert.match(source, /motion="preview"/);
  assert.match(source, /theme-pick-grid/);
  assert.doesNotMatch(source, /linear-gradient\(135deg/);
});
```

- [ ] **Step 2: Verify the legacy swatch fails**

Run: `npm --prefix web test -- test/appearance-contract.test.mjs`
Expected: FAIL because the picker uses a generic gradient square.

- [ ] **Step 3: Implement compact Pulse Aperture cards**

Render one `NovaMark` per theme with `style={{ "--ap-*": ... }}` scoped to the preview. Add selected semantics through `aria-pressed`, visible check state, and `data-theme-id`.

- [ ] **Step 4: Migrate persisted state during hydration**

In the saved-state load path, call `normalizeThemeId(g.accent)` before `setAccent`. Save only normalized IDs thereafter.

- [ ] **Step 5: Add compact matte picker styling**

Use two columns on narrow screens and three on wide settings modals. The target remains at least `44 px`; the icon is `28–32 px`; labels remain legible at 12–13 px.

- [ ] **Step 6: Run web tests**

Run: `npm --prefix web test`
Expected: all web Node tests pass.

- [ ] **Step 7: Commit picker/persistence if the repository lock is clear**

```powershell
git add web/src/settings/sections/AppearanceSection.jsx web/src/app/NovaAgent.jsx web/src/styles/surfaces.mjs web/test/appearance-contract.test.mjs
git commit -m "feat: add animated NOVA theme picker"
```

### Task 5: Apply Matte Surfaces and Compact Navigation Microinteractions

**Files:**
- Modify: `web/src/layout/SideRail.jsx`
- Modify: `web/src/layout/TopBar.jsx`
- Modify: `web/src/styles/shell.mjs`
- Create: `web/test/navigation-style-contract.test.mjs`

**Interfaces:**
- Consumes: theme CSS variables and existing `DESTINATIONS`.
- Produces: the approved matte glass mobile dock, compact desktop nav, animated settings control, unchanged navigation callbacks/semantics.

- [ ] **Step 1: Write the failing navigation style test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("mobile navigation follows the approved compact dock measurements", async () => {
  const css = await readFile(new URL("../src/styles/shell.mjs", import.meta.url), "utf8");
  assert.match(css, /\.bottom-nav[\s\S]*height:\s*58px/);
  assert.match(css, /min-height:\s*44px/);
  assert.match(css, /backdrop-filter:\s*blur\(20px\)/);
  assert.match(css, /translateY\(-2px\)/);
  assert.match(css, /\.settings-motion:active/);
});
```

- [ ] **Step 2: Verify the old large capsule style fails**

Run: `npm --prefix web test -- test/navigation-style-contract.test.mjs`
Expected: FAIL because the existing bar has no fixed compact dock geometry and uses a 56×30 active capsule.

- [ ] **Step 3: Implement the compact dock**

Keep all four destination buttons and callbacks. Make the outer dock `58 px`, button targets `44 px`, active lift `-2 px`, and active surface a low-opacity theme tint rather than a solid fill.

- [ ] **Step 4: Add settings microinteraction**

Add `settings-motion` to top bar and rail settings buttons. Use a maximum `22deg` icon rotation plus `.96` press scale, with no delayed click handling.

- [ ] **Step 5: Make desktop rail controls smaller without reducing accessibility**

Reduce visual padding/radius, retain full-width targets and focus rings, and use icon/label translation and opacity for selected states.

- [ ] **Step 6: Run style tests and build**

Run: `npm --prefix web test -- test/navigation-style-contract.test.mjs`
Expected: PASS.

Run: `npm --prefix web run build`
Expected: exit 0.

- [ ] **Step 7: Commit navigation polish if the repository lock is clear**

```powershell
git add web/src/layout/SideRail.jsx web/src/layout/TopBar.jsx web/src/styles/shell.mjs web/test/navigation-style-contract.test.mjs
git commit -m "feat: refine compact matte NOVA navigation"
```

### Task 6: Turn the Design File into the Reusable Animated Library

**Files:**
- Modify: `docs/design/Nova İkon.dc.html`
- Modify: `design/README.md`
- Create: `web/test/design-library-contract.test.mjs`

**Interfaces:**
- Consumes: versioned SVG assets and theme IDs.
- Produces: a design file containing all eleven live variants, brand/thinking/preview states, four small-size checks, Ruby surface sample, and reduced-motion sample.

- [ ] **Step 1: Write the failing design library test**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("design file references every versioned animated theme", async () => {
  const html = await readFile(new URL("../../docs/design/Nova İkon.dc.html", import.meta.url), "utf8");
  for (const id of ["amethyst","arctic","sapphire","emerald","lime","amber","copper","coral","ruby","lavender","moonstone"]) {
    assert.match(html, new RegExp(`nova-pulse-aperture-orbital-v4-${id}\\\\.svg`));
  }
  for (const size of ["96", "72", "48", "36"]) assert.match(html, new RegExp(`>${size}px<`));
  assert.match(html, /Azaltılmış hareket/);
});
```

- [ ] **Step 2: Verify the legacy orbit/core design file fails**

Run: `npm --prefix web test -- test/design-library-contract.test.mjs`
Expected: FAIL because the design file still contains the obsolete ring/core concept.

- [ ] **Step 3: Replace the design file content with the shared asset library**

Reference the versioned SVG files; do not paste new hand-drawn geometry. Include theme ID/source name metadata and all required state/size sections.

- [ ] **Step 4: Document the source-of-truth workflow**

Update `design/README.md`: color/motion changes begin in tokens and approved SVG references, `npm run tokens` regenerates runtime outputs, and the design file consumes the same sources.

- [ ] **Step 5: Run the design contract test**

Run: `npm --prefix web test -- test/design-library-contract.test.mjs`
Expected: PASS.

- [ ] **Step 6: Commit the reusable library if the repository lock is clear**

```powershell
git add "docs/design/Nova İkon.dc.html" design/README.md web/test/design-library-contract.test.mjs
git commit -m "docs: add animated NOVA theme library"
```

### Task 7: Verify Android Theme Selection and Persistence

**Files:**
- Modify: `nova-android/app/src/test/java/com/nova/agent/NovaAccentTest.kt`
- Modify: `nova-android/app/src/androidTest/java/com/nova/agent/SettingsPanelTest.kt`
- Modify: `nova-android/app/src/androidTest/java/com/nova/agent/NovaAppSettingsSyncTest.kt`
- Modify only if required by failing tests: `nova-android/app/src/main/java/com/nova/agent/feature/settings/SettingsPanel.kt`
- Modify only if required by failing tests: `nova-android/app/src/main/java/com/nova/agent/data/SettingsStore.kt`

**Interfaces:**
- Consumes: generated `NOVA_ACCENTS`, `NovaSurfaceColors`, existing `theme_<id>` semantics.
- Produces: all eleven selectable themes, Ruby black surfaces, persistence across activity recreation/relaunch.

- [ ] **Step 1: Replace the legacy catalog assertions**

```kotlin
@Tes
fun shippedThemesMatchWebOrder() {
    assertEquals(
        listOf("amethyst", "arctic", "sapphire", "emerald", "lime", "amber",
            "copper", "coral", "ruby", "lavender", "moonstone"),
        NOVA_ACCENTS.map { it.id },
    )
}

@Tes
fun rubyUsesBlackDominantSurfaces() {
    val ruby = accentFor("ruby")
    assertEquals(Color(0xFF050507), ruby.surface?.bg)
    assertEquals(Color(0xFF0A0A0D), ruby.surface?.bg2)
    assertEquals(Color(0xFF030304), ruby.surface?.bg3)
}
```

- [ ] **Step 2: Run the unit test and verify legacy expectations fail**

Run from `nova-android`: `.\gradlew.bat testDebugUnitTest --tests com.nova.agent.NovaAccentTest --console=plain`
Expected: FAIL until generated catalog and defaults match.

- [ ] **Step 3: Add Compose theme-picker coverage**

Open Settings, scroll to every `theme_<id>` tag, click Ruby, and assert the callback receives `ruby`. Repeat for `moonstone` to ensure the last item is reachable.

- [ ] **Step 4: Add persistence coverage**

Select Ruby through the activity UI, wait for the store write, recreate/relaunch the activity, and assert `theme_ruby` is selected and `nova_brand_mark` remains visible.

- [ ] **Step 5: Run focused Android tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.nova.agent.NovaAccentTest --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.SettingsPanelTest,com.nova.agent.NovaAppSettingsSyncTest,com.nova.agent.ui.brand.NovaThinkingIndicatorTest' --console=plain
```

Expected: all focused tests pass with 0 failures.

- [ ] **Step 6: Commit Android parity if the repository lock is clear**

```powershell
git add nova-android/app/src/test/java/com/nova/agent/NovaAccentTest.kt nova-android/app/src/androidTest/java/com/nova/agent/SettingsPanelTest.kt nova-android/app/src/androidTest/java/com/nova/agent/NovaAppSettingsSyncTest.kt nova-android/app/src/main/java/com/nova/agent/feature/settings/SettingsPanel.kt nova-android/app/src/main/java/com/nova/agent/data/SettingsStore.k
git commit -m "test: verify shared Android theme catalog"
```

### Task 8: Functional UI Validation and Final Verification

**Files:**
- Create: `audit/2026-07-26-theme-parity/functional-test-matrix.md`
- Create: `audit/2026-07-26-theme-parity/web-*.png`
- Create: `audit/2026-07-26-theme-parity/android-*.png`
- Modify: `design-qa.md`

**Interfaces:**
- Consumes: completed web/Android builds and an Android emulator.
- Produces: evidence-backed pass/fail matrix, screenshots, updated design QA, and build artifacts.

- [ ] **Step 1: Run all static/unit verification**

```powershell
npm --prefix web tes
npm --prefix web run build
npm run docs-check
```

Expected: all commands exit 0.

- [ ] **Step 2: Start the web app and exercise core flows**

Use the in-app browser at desktop and narrow mobile widths:

1. Load the app and confirm Ametist Pulse Aperture in the header.
2. Open Settings and confirm all eleven theme names.
3. Select Kızıl; confirm `data-accent="ruby"`, black surfaces, crimson mark.
4. Reload; confirm Kızıl persists.
5. Select Ametist, Arktik, Safir, Zümrüt, Limon, Amber, Bakır, Mercan, Lavanta, Aytaşı; confirm each changes both UI and mark.
6. Navigate Kontrol → İşler → Sohbet → Modeller; confirm correct active destination and compact transition.
7. Open/close Settings; confirm the settings microinteraction does not delay the modal.
8. Trigger the empty assistant waiting state; confirm the thinking mark appears and clears.
9. Emulate reduced motion; confirm brand and navigation animations stop while selected states remain visible.

- [ ] **Step 3: Run Android emulator functional flows**

Use Android Studio/emulator:

1. Launch NOVA and visit Kontrol, İşler, Sohbet, Modeller.
2. Open Settings and select Kızıl, Ametist, Arktik, and Aytaşı.
3. Relaunch after Kızıl; confirm persistence and black-dominant surfaces.
4. Open empty Chat and confirm `nova_brand_mark`.
5. Trigger/stop the response waiting state and confirm `nova_thinking_indicator`.
6. Verify settings close, model selection, chat input/send/stop, and task navigation remain functional.

- [ ] **Step 4: Run full Android verification**

Run from the working Android project:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

Expected: Gradle exits 0 and reports 0 failed connected tests.

- [ ] **Step 5: Perform visual QA against the sources**

Create same-state comparison images containing the source SVG and rendered web/Android captures for Ametist, Kızıl, Arktik, and Aytaşı. Check small-size reading, halo density, core position, matte/blur surfaces, clipping, typography, padding, and contrast.

- [ ] **Step 6: Record the functional matrix**

For each web and Android flow, record `PASS`, `FAIL`, or `BLOCKED` with command/screenshot evidence. Live gateway/model calls may be `BLOCKED` only when no configured service exists; local navigation, persistence, settings, and waiting-indicator flows must pass.

- [ ] **Step 7: Update design QA**

Append source paths, viewport sizes, fixes, comparison evidence, and `final result: passed` only when every required non-live flow passes.

- [ ] **Step 8: Commit verification evidence if the repository lock is clear**

```powershell
git add audit/2026-07-26-theme-parity design-qa.md
git commit -m "test: verify NOVA theme and icon parity"
```
