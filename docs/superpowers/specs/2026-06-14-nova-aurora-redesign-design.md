# Nova "Aurora" UI Redesign — Design Spec

**Date:** 2026-06-14
**Status:** Approved (direction) — pending spec review
**Scope step:** G (yeni arayüz + animasyon + renk paleti)

## Goal

Give the Nova web UI a brand-new look in the "glass / aurora (modern SaaS)"
direction — animated aurora background, glassmorphism surfaces, an indigo+cyan
palette, refreshed layout composition, and tasteful micro-animations — **without
changing any existing functionality** (chat, agent/team mode, voice/orb, RAG,
artifacts, settings, scheduled tasks, usage panel).

Decision level: **restyle + layout renewal** (not a full rewrite). All work stays
in `web/src/nova-agent.jsx` (single React component, inline CSS-in-JS string) plus
existing structure; only presentation (CSS tokens, className/layout, animation
keyframes) changes.

## Non-goals

- No new behavior or features.
- No component splitting / file restructure (separate from this design).
- No heavy animation dependency (e.g. Framer Motion). Pure CSS keyframes + the
  existing JS-driven animation pattern only.

## Current baseline (as-is)

- Single file `web/src/nova-agent.jsx` (~2442 lines), inline CSS string.
- Token system already present: `--bg #06070b`, `--bg2`, `--surface`, `--surface-2`,
  `--line`, `--line-bright`, `--text #e9edf6`, `--muted`, `--cyan #38e1d6`,
  `--azure #2ba0ff`, `--coral #ff8a5b`, `--glow`.
- Keyframes already present: `drift`, `markPulse`, `dotPulse`, `ripple`,
  `speakPulse`, `hueFlow`, `floatY`, `heroIn`.
- Layout: persistent left rail (brand + search + conversation list + settings),
  chat area, floating dock (input + agent/team/voice toggles), settings panel,
  artifact preview panel, voice orb / hero empty-state.

## Design

### 1. Palette / tokens (new)

Replace the palette values; **keep existing token names** and remap the old accent
names so the ~2442 lines of `var(--cyan)` / `var(--azure)` references keep working
against the new palette (no churn, no broken references).

```
--bg:         #0a0e1a        (deeper blue-night, replaces #06070b)
--bg2:        #0d1326
--surface:    rgba(255,255,255,0.04)
--surface-2:  rgba(255,255,255,0.07)
--line:       rgba(255,255,255,0.08)
--line-bright:rgba(129,140,248,0.32)
--text:       #eef1fb
--muted:      #9aa3bd
--muted-2:    #5b6276

# new aurora accents
--aurora-1:   #6366f1   (indigo)
--aurora-2:   #22d3ee   (cyan)
--aurora-3:   #a855f7   (violet)
--accent:     #818cf8

# back-compat remaps (existing names → new palette)
--cyan:  #22d3ee   (was #38e1d6)
--azure: #6366f1   (was #2ba0ff)
--coral: #fb7185   (warm rose, kept for voice "speaking" state)
--glow:  0 0 48px rgba(99,102,241,0.30)
--glass-blur: 16px
```

### 2. Aurora background (animated)

A fixed, full-viewport, `pointer-events:none` layer behind all content:
- 2–3 large blurred radial-gradient "clouds" (indigo / cyan / violet).
- Slow drift via an extended `drift`-style keyframe (~30–40s loop), GPU-friendly
  (`transform` + `opacity` only).
- Optional faint grain/noise overlay (very low opacity) for texture.
- Sits between `--bg` base and the app content.

### 3. Glassmorphism surfaces

Apply frosted-glass treatment to: left rail, dock, settings panel, artifact panel,
AI message bubbles.
- `background: var(--glass)` + `backdrop-filter: blur(var(--glass-blur))`.
- Thin luminous border (`--glass-line` / `--line-bright` on hover).
- Soft elevation shadow; hover raises elevation + edge glow (`--glow`).

### 4. Layout renewal

- **Left rail:** slimmer, glass; brand + search on top, conversation list in the
  middle, user/settings pinned to the bottom; soft slide-in on mount.
- **Chat area:** more breathing room / vertical rhythm; AI bubbles glass.
- **Dock:** centered floating glass "pill"; agent/team/voice toggles iconic, with
  aurora-gradient fill when active.
- **Empty state (hero):** aurora orb + larger typography, building on existing
  `heroIn` / `floatY`.

Functionality, event handlers, and component logic are untouched — only container
structure / className / ordering and CSS change.

### 5. Animation system (pure CSS / JS)

Consistent with the existing keyframe approach (no new dependency):
- Message entrance stagger, hover lift, toggle transitions, tool-step appearance,
  panel slide, aurora drift, orb pulse.
- All animations use `transform` / `opacity`.
- **Accessibility:** a `@media (prefers-reduced-motion: reduce)` block disables all
  decorative animations (aurora drift, pulses, float, stagger) and falls back to
  instant/opacity-only states.

### 6. Scope, risk & verification

- All changes in `web/src/nova-agent.jsx` (CSS string + JSX className/layout).
- **No functional change** — chat / agent / team / voice / RAG / artifacts /
  settings / scheduled / usage all behave identically.
- Risk: medium (layout edits can shift DOM); mitigated by incremental edits and a
  `npm run build` check after each meaningful change, plus a manual visual
  pass in the running dev server.

## Acceptance criteria

1. New palette applied via remapped tokens; no broken `var(--…)` references.
2. Animated aurora background renders behind content, GPU-friendly.
3. Glass treatment on left rail, dock, settings panel, artifact panel, AI bubbles.
4. Renewed layout (rail / chat / dock / hero) per section 4.
5. Micro-animations present; `prefers-reduced-motion` disables decorative motion.
6. `npm run build` passes; all existing features work unchanged in a manual pass.
7. No new runtime dependency added to `web/package.json`.
