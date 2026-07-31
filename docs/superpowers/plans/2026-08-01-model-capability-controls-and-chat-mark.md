# Model Capability Controls and Chat Mark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect real Ollama model capabilities, gate thinking/tool controls honestly, map NOVA effort to supported Ollama thinking modes, and improve the assistant message mark motion.

**Architecture:** The gateway remains the source of truth: it probes `/api/show` once per catalog refresh and publishes capability values plus their provenance. The web client normalizes those fields, derives control availability and request values in a small pure module, then renders accessible disabled states without mutating the user’s saved preferences. The existing Pulse Aperture component supplies both pending and resting message motion.

**Tech Stack:** Node.js ESM, React 18, Ollama HTTP API, Node test runner, React server rendering, Vite, CSS modules exported as strings.

## Global Constraints

- Use `/api/show` capability metadata before family-name fallbacks.
- Unknown custom-model capabilities are disabled, never optimistically enabled.
- Keep unsupported controls visible and explain the reason on hover and keyboard focus.
- Preserve the current Pulse Aperture geometry and `prefers-reduced-motion` behavior.
- Preserve unrelated dirty changes in `C:\Users\salih\Project_Horus`.
- Delete only verified reproducible artifacts after an exact-path audit.

---

### Task 1: Gateway capability catalog

**Files:**
- Modify: `gateway/lib/model_catalog.mjs`
- Test: `gateway/test/model_catalog.test.mjs`

**Interfaces:**
- Produces: `parseShowCapabilitySet(payload) -> string[] | null`
- Produces: `probeOllamaCapabilities(baseUrl, modelName, options) -> Promise<object | null>`
- Produces: catalog fields `tools`, `toolsSource`, `thinking`, `thinkingSource`, `thinkingMode`

- [ ] **Step 1: Write failing capability-set tests**

Add assertions that a measured payload with `tools` and `thinking` returns both,
that an existing capability array without them returns measured `false`, and that a
missing array remains `null`.

- [ ] **Step 2: Run the focused gateway test and confirm RED**

Run: `node --test gateway/test/model_catalog.test.mjs`
Expected: FAIL because the generalized parser and thinking fields do not exist.

- [ ] **Step 3: Implement one `/api/show` probe per model**

Normalize capability names, preserve the existing tools wrapper for compatibility,
add conservative thinking-family fallback, and expose source fields. Set
`thinkingMode: "levels"` only for GPT-OSS; use `"toggle"` otherwise.

- [ ] **Step 4: Run the focused gateway test and confirm GREEN**

Run: `node --test gateway/test/model_catalog.test.mjs`
Expected: all tests pass.

### Task 2: Web capability policy and request mapping

**Files:**
- Create: `web/src/lib/model-capabilities.mjs`
- Modify: `web/src/lib/constants.mjs`
- Test: `web/test/model-capabilities.test.mjs`

**Interfaces:**
- Produces: `capabilityState(item, capability) -> { supported, known, reason }`
- Produces: `resolveThinkRequest(item, { reasoning, effort }) -> false | true | "low" | "medium" | "high"`
- Consumes: gateway capability and provenance fields from Task 1.

- [ ] **Step 1: Write failing pure-policy tests**

Cover measured support, measured rejection, unknown custom models, GPT-OSS level
mapping, boolean-model mapping, and disabled thinking returning `false`.

- [ ] **Step 2: Run the focused web test and confirm RED**

Run: `node --test web/test/model-capabilities.test.mjs`
Expected: FAIL because the policy module does not exist.

- [ ] **Step 3: Implement the minimal policy module and catalog mapping**

Pass through capability values, sources and thinking mode. Add truthful fallback
metadata for curated gateway/cloud entries so offline fallback does not pretend an
unknown state is verified.

- [ ] **Step 4: Run the focused web test and confirm GREEN**

Run: `node --test web/test/model-capabilities.test.mjs`
Expected: all tests pass.

### Task 3: Accessible capability-gated dock

**Files:**
- Modify: `web/src/layout/Dock.jsx`
- Modify: `web/src/styles/chat.mjs`
- Modify: `web/src/ui/ModelList.jsx`
- Modify: `web/src/views/ModelsView.jsx`
- Test: `web/test/model-capability-ui.test.mjs`

**Interfaces:**
- Consumes: `capabilityState` from Task 2.
- Produces: disabled effort/reasoning/agent/team controls and keyboard-focusable explanations.

- [ ] **Step 1: Write failing server-rendered UI tests**

Render the Dock for supported, unsupported and unknown models. Assert visible button
labels, native `disabled` attributes, tooltip text, and capability badges.

- [ ] **Step 2: Run the focused UI test and confirm RED**

Run: `node --test web/test/model-capability-ui.test.mjs`
Expected: FAIL because every control is currently clickable.

- [ ] **Step 3: Implement disabled states, info affordances and badges**

Use native disabled buttons, a focusable help control, `role="tooltip"`, subdued
mat styling, and existing design tokens. Do not add a new icon or color system.

- [ ] **Step 4: Run the focused UI test and confirm GREEN**

Run: `node --test web/test/model-capability-ui.test.mjs`
Expected: all tests pass.

### Task 4: Effective request state in NOVA

**Files:**
- Modify: `web/src/app/NovaAgent.jsx`
- Modify: `web/src/lib/stream.mjs`
- Modify: `gateway/lib/providers.mjs`
- Modify: `gateway/lib/agent.mjs`
- Test: `web/test/model-capabilities.test.mjs`
- Test: `gateway/test/agent.test.mjs`

**Interfaces:**
- Consumes: `resolveThinkRequest` from Task 2.
- Produces: requests that omit unsupported tools and send only a supported boolean or level-valued `think`.

- [ ] **Step 1: Extend tests for string-valued thinking**

Assert GPT-OSS sends `"low"/"medium"/"high"` unchanged and agent/direct Ollama
paths do not coerce it to boolean.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `node --test web/test/model-capabilities.test.mjs gateway/test/agent.test.mjs`
Expected: FAIL because current paths use `!!think`.

- [ ] **Step 3: Apply effective capability state at request time**

Keep saved preferences intact across model switches, but derive `effectiveThinking`,
`effectiveAgent` and `effectiveTeam`. Use the effective values in system text,
message trace state, route extras, voice requests and subtitles.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `node --test web/test/model-capabilities.test.mjs gateway/test/agent.test.mjs`
Expected: all tests pass.

### Task 5: Larger, continuously animated assistant mark

**Files:**
- Modify: `web/src/views/ChatView.jsx`
- Modify: `web/src/styles/chat.mjs`
- Test: `web/test/chat-mark-contract.test.mjs`

**Interfaces:**
- Consumes: existing `NovaMark` motion modes.
- Produces: 40 px `thinking` motion while pending and 40 px animated `brand` motion when complete.

- [ ] **Step 1: Write a failing message-mark contract test**

Render pending and complete assistant messages and assert `size="40"`, thinking
motion for pending output, brand motion for completed output, and animation in both.

- [ ] **Step 2: Run the focused contract test and confirm RED**

Run: `node --test web/test/chat-mark-contract.test.mjs`
Expected: FAIL because the current mark is 34 px and static after completion.

- [ ] **Step 3: Implement the minimal mark and avatar-slot change**

Select `motion={pending ? "thinking" : "brand"}`, keep `animated`, and update the
avatar slot to 40 px without changing user avatars or brand geometry.

- [ ] **Step 4: Run brand and chat-mark tests and confirm GREEN**

Run: `node --test web/test/brand-motion-contract.test.mjs web/test/chat-mark-contract.test.mjs`
Expected: all tests pass.

### Task 6: Documentation, safe cleanup and final verification

**Files:**
- Modify: `README.md`
- Modify: `README.tr.md`
- Modify: `design-qa.md` in the writable project workspace when visual QA is run

**Interfaces:**
- Consumes: verified behavior from Tasks 1–5 and the independent cleanup/model audits.
- Produces: reproducible operator guidance, timestamped recommendations and a cleanup record.

- [ ] **Step 1: Update both README files**

Document live capability refresh, disabled-control meanings, Ollama commands,
tool-backed web search, and hardware-aware 2026 model recommendations.

- [ ] **Step 2: Audit exact cleanup targets before deletion**

Resolve every target to an absolute path under the intended project, measure it,
confirm it is ignored/generated, then remove only the approved reproducible set.

- [ ] **Step 3: Run full automated verification**

Run: `npm.cmd test`
Run: `npm.cmd run build`
Expected: zero failures and a successful Vite production build.

- [ ] **Step 4: Run same-state visual QA**

Capture the approved chat state and the implementation at the same viewport, compare
them together, correct visible spacing/motion-state issues, and record the passed
check in `design-qa.md`.

- [ ] **Step 5: Review repository state and integrate safely**

Inspect `git diff --check`, `git status --short`, and the complete diff. Commit the
isolated branch, then integrate without overwriting unrelated dirty files in the
main worktree.
