// Sohbet, ses, kompozer, markdown, düşünme/araç izleri, görev zaman çizelgesi.

export const CHAT_CSS = `
/* ------------------------------ ses görünümü ---------------------------- */

.voice-view {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: var(--space-xxl); padding: 10px 20px;
  animation: fadeUp var(--dur-slow) var(--ease-emphasized);
}
.orb-wrap { position: relative; display: grid; place-items: center; }
.voice-status { text-align: center; min-height: 54px; }
.voice-status .vs-label {
  font-family: 'Syne', sans-serif; font-weight: 700; font-size: 23px; letter-spacing: .5px;
  background: linear-gradient(90deg, var(--text), color-mix(in srgb, var(--accent) 70%, var(--text)));
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.voice-status .vs-sub { color: var(--muted); font-size: 13px; margin-top: 6px; max-width: 460px; line-height: 1.5; }
.wavebar { display: flex; align-items: center; gap: 4px; height: 34px; }
.wavebar span { width: 3.5px; border-radius: 3px; background: linear-gradient(180deg, var(--accent), var(--accent-2));
  transition: height .08s linear; }
.voice-controls { display: flex; align-items: center; gap: var(--space-lg); }
.mic-btn {
  width: 74px; height: 74px; border-radius: 50%; cursor: pointer; position: relative;
  border: 1px solid var(--line-bright); background: rgba(var(--accent-rgb), .08);
  display: grid; place-items: center; color: var(--accent);
  transition: background var(--dur-base) var(--ease-standard), transform var(--dur-base) var(--ease-standard),
              box-shadow var(--dur-base);
}
.mic-btn:hover { background: rgba(var(--accent-rgb), .16); transform: scale(1.04); }
.mic-btn:active { transform: scale(.95); }
.mic-btn.active {
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: var(--on-accent); border-color: transparent;
  box-shadow: 0 0 40px rgba(var(--accent-rgb), .5);
}
.mic-btn.active::before {
  content: ''; position: absolute; inset: -6px; border-radius: 50%;
  border: 1.5px solid rgba(var(--accent-rgb), .5); animation: pulseRing 1.6s ease-out infinite;
}
.mic-btn.speaking {
  background: linear-gradient(135deg, var(--ember), #ff5c7a); color: #190a06; border-color: transparent;
  box-shadow: 0 0 46px rgba(255,138,91,.5);
}
.mic-btn.speaking::before {
  content: ''; position: absolute; inset: -7px; border-radius: 50%;
  border: 1.5px solid rgba(255,138,91,.5); animation: pulseRing 1.4s ease-out infinite;
}
.voice-fallback { display: flex; gap: 10px; width: 100%; max-width: 560px; }

/* ----------------------------- sohbet görünümü -------------------------- */

.chat-view { flex: 1; display: flex; flex-direction: column; min-height: 0; width: 100%; max-width: 900px; margin: 0 auto; }
.chat-scroll {
  flex: 1; overflow-y: auto; scroll-behavior: smooth;
  padding: 20px 26px 12px; display: flex; flex-direction: column; gap: 22px;
}

.hero {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: var(--space-xxl); text-align: center; padding: 20px;
  animation: scaleIn var(--dur-reveal) var(--ease-emphasized);
}
.hero .nova-badge { animation: markPulse 4s ease-in-out infinite, floatY 5s ease-in-out infinite; }
.hero .hero-title {
  font-family: 'Syne', sans-serif; font-weight: 700; font-size: 33px; letter-spacing: .3px;
  background: linear-gradient(90deg, var(--text), var(--accent), var(--accent-3), var(--text));
  background-size: 200% auto;
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  animation: hueFlow 7s linear infinite;
}
.hero .hero-title em { font-style: normal; -webkit-text-fill-color: var(--accent); }
.hero .hero-sub { color: var(--muted); font-size: 14.5px; max-width: 470px; line-height: 1.6; margin-top: 8px; }
.hero .hero-sub b { color: var(--text); font-weight: 500; }

.sugg-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 11px; max-width: 640px; width: 100%; }
@media (max-width: 620px) { .sugg-grid { grid-template-columns: 1fr; } }
.sugg-card {
  position: relative; overflow: hidden;
  display: flex; align-items: flex-start; gap: 12px; padding: 14px 15px;
  border-radius: var(--radius-lg); background: var(--surface1); border: 1px solid var(--line);
  cursor: pointer; text-align: left; font-family: inherit;
  transition: border-color var(--dur-fast), background var(--dur-fast),
              transform var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base);
}
.sugg-card:hover { border-color: var(--line-bright); background: var(--surface2); transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--glass-shadow); }
.sugg-card:active { transform: scale(.98); }
.sugg-card::after {
  content: ''; position: absolute; inset: 0; border-radius: var(--radius-lg); padding: 1px;
  background: linear-gradient(135deg, rgba(var(--accent-rgb), .55), rgba(var(--accent-3-rgb), .32), transparent 70%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor; mask-composite: exclude;
  opacity: 0; transition: opacity var(--dur-base); pointer-events: none;
}
.sugg-card:hover::after { opacity: 1; }
.sugg-card .sc-ic { width: 34px; height: 34px; border-radius: var(--radius-sm); flex-shrink: 0;
  display: grid; place-items: center; color: var(--accent);
  background: linear-gradient(135deg, rgba(var(--accent-rgb), .16), rgba(var(--accent-2-rgb), .12)); }
.sugg-card .sc-cat { font-size: 10.5px; letter-spacing: .6px; text-transform: uppercase; color: var(--accent); font-weight: 600; margin-bottom: 3px; }
.sugg-card .sc-t { font-size: 13.5px; color: var(--text); line-height: 1.35; }
.sugg-card .sc-d { font-size: 11.5px; color: var(--muted2); margin-top: 3px; }

/* -------------------------------- mesajlar ------------------------------- */

.msg { display: flex; gap: 14px; max-width: 100%; animation: fadeUp var(--dur-slow) var(--ease-standard); }
.msg.user { flex-direction: row-reverse; }
.avatar { width: 40px; height: 40px; border-radius: var(--radius-md); flex-shrink: 0; display: grid; place-items: center; }
.avatar.ai { position: relative; overflow: hidden; color: var(--on-accent); box-shadow: var(--glow);
  background: radial-gradient(circle at 30% 30%, var(--accent), var(--accent-2)); }
.avatar.ai::before {
  content: ''; position: absolute; inset: -50%;
  background: conic-gradient(from 0deg, transparent, rgba(255,255,255,.4), transparent 45%);
  opacity: 0; transition: opacity var(--dur-base);
}
.msg:hover .avatar.ai::before { opacity: 1; animation: spin 4s linear infinite; }
.avatar.ai svg { position: relative; z-index: 1; }
.avatar.ai.thinking { animation: gemPulse 1.3s ease-in-out infinite; }
@keyframes gemPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(var(--accent-rgb), .4); }
  50% { box-shadow: 0 0 0 7px rgba(var(--accent-rgb), 0); }
}
.avatar.me { background: var(--surface2); border: 1px solid var(--line); color: var(--muted);
  font-size: 13px; font-family: 'JetBrains Mono', monospace; }

.bubble {
  padding: 14px 18px; border-radius: var(--radius-xl); font-size: 14.5px; line-height: 1.65;
  max-width: 80%; white-space: pre-wrap; word-wrap: break-word;
}
.bubble.ai {
  background: linear-gradient(180deg, var(--surface2), var(--surface1));
  border: 1px solid var(--line); border-top-left-radius: 6px; color: var(--text);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  transition: border-color var(--dur-base), box-shadow var(--dur-base);
}
.bubble.ai:hover { border-color: var(--line-bright); box-shadow: 0 6px 22px var(--glass-shadow); }
.bubble.me {
  background: linear-gradient(135deg, rgba(var(--accent-rgb), .16), rgba(var(--accent-2-rgb), .16));
  border: 1px solid rgba(var(--accent-rgb), .24); border-top-right-radius: 6px;
  box-shadow: 0 4px 18px rgba(var(--accent-2-rgb), .12);
}

.msg-imgs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.msg-img { max-width: 160px; max-height: 160px; border-radius: var(--radius-sm); border: 1px solid var(--line); }

.msg-meta { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin: 7px 0 0 48px; }
.msg-meta .chip.time { margin-left: auto; }
.msg-actions { display: flex; gap: 5px; margin: 6px 0 0 48px; }
.msg-act {
  display: flex; align-items: center; gap: 5px; background: transparent;
  border: 1px solid transparent; border-radius: var(--radius-xs); padding: 5px 9px;
  cursor: pointer; color: var(--muted2); font-size: 11.5px; font-family: inherit;
  transition: color var(--dur-fast), border-color var(--dur-fast), background var(--dur-fast);
}
.msg-act:hover { color: var(--text); border-color: var(--line); background: var(--surface1); }

/* --------------------------- düşünme / araç izi -------------------------- */

.trace-slot { margin-left: 48px; }
.think-trace {
  background: var(--surface1); border: 1px solid var(--line);
  border-radius: var(--radius-md); max-width: 80%; overflow: hidden;
}
.think-head {
  display: flex; align-items: center; gap: 8px; padding: 11px 14px;
  font-size: 12px; color: var(--accent); font-family: 'JetBrains Mono', monospace;
  letter-spacing: .4px; cursor: pointer; user-select: none;
  transition: background var(--dur-fast);
}
.think-head:hover { background: rgba(var(--accent-rgb), .04); }
.think-head .think-title { flex: 1; }
.think-head .chev { transition: transform var(--dur-fast) var(--ease-standard); opacity: .75; }
.think-head.closed .chev { transform: rotate(-90deg); }
.think-real {
  font-family: 'JetBrains Mono', monospace; font-size: 11.5px; line-height: 1.55;
  color: var(--muted); white-space: pre-wrap; max-height: 220px; overflow-y: auto; padding: 0 14px 12px;
  animation: fadeIn var(--dur-fast);
}
.think-steps { padding: 0 14px 12px; }
.think-step { display: flex; align-items: center; gap: 9px; font-size: 12.5px; color: var(--muted); margin-top: 8px;
  animation: fadeIn var(--dur-slow) forwards; }
.think-step .sc { width: 15px; height: 15px; border-radius: 50%; border: 1.5px solid var(--muted2);
  display: grid; place-items: center; flex-shrink: 0; }
.think-step.done .sc { border-color: var(--accent); background: rgba(var(--accent-rgb), .16); color: var(--accent); }

.tool-trace {
  background: rgba(255,138,91,.05); border: 1px solid rgba(255,138,91,.22);
  border-radius: var(--radius-md); padding: 10px 13px; max-width: 80%;
}
.tt-head { display: flex; align-items: center; gap: 7px; font-size: 12px; color: var(--ember);
  font-family: 'JetBrains Mono', monospace; letter-spacing: .4px; margin-bottom: 7px; }
.tt-step { display: grid; grid-template-columns: auto auto 1fr; align-items: center; gap: 8px;
  font-size: 12.5px; color: var(--muted); margin-top: 5px; }
.tt-ic { width: 18px; height: 18px; border-radius: var(--radius-xs); background: rgba(255,138,91,.12);
  display: grid; place-items: center; color: var(--ember); flex-shrink: 0; }
.tt-name { color: var(--text); font-weight: 500; }
.tt-q { color: var(--muted2); font-family: 'JetBrains Mono', monospace; font-size: 11.5px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tt-sources { grid-column: 2 / -1; display: flex; gap: 6px; flex-wrap: wrap; }
.tt-source {
  display: inline-flex; align-items: center; gap: 4px; max-width: 220px;
  border: 1px solid var(--line); border-radius: var(--radius-pill); padding: 3px 8px;
  background: var(--surface1); color: var(--muted); font-size: 10.5px;
  text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  transition: transform var(--dur-fast), border-color var(--dur-fast), color var(--dur-fast);
}
a.tt-source:hover { color: var(--accent); border-color: var(--line-bright); transform: translateY(-1px); }

/* ---------------------------- bekleme durumu ---------------------------- */

.wait { display: flex; flex-direction: column; gap: 9px; padding: 4px 0 2px; min-width: 240px; }
.wait-status { display: flex; align-items: center; gap: 9px; font-size: 12.5px; color: var(--muted); }
.wait-orb { width: 16px; height: 16px; border-radius: 50%;
  background: conic-gradient(from 0deg, var(--accent), var(--accent-2), var(--ember), var(--accent));
  animation: spin 1.1s linear infinite; box-shadow: 0 0 12px rgba(var(--accent-rgb), .5); }
.wait-line {
  height: 11px; border-radius: 6px; background-size: 220% 100%;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), .05) 0%, rgba(var(--accent-rgb), .22) 30%,
              rgba(var(--accent-2-rgb), .22) 50%, rgba(var(--accent-rgb), .05) 80%);
  animation: shimmer 1.5s ease-in-out infinite;
}
.wait-line.l1 { width: 88%; } .wait-line.l2 { width: 96%; animation-delay: .18s; } .wait-line.l3 { width: 62%; animation-delay: .36s; }
.typing { display: flex; gap: 5px; padding: 6px 2px; }
.typing span { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); animation: typeBounce 1.2s infinite; }
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }

/* -------------------------------- kompozer ------------------------------- */

.composer { padding: 14px 26px 10px; }
.composer-inner {
  display: flex; align-items: flex-end; gap: 10px;
  background: var(--surface2); border: 1px solid var(--line);
  border-radius: var(--radius-xxl); padding: 10px 10px 10px 18px;
  backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px);
  box-shadow: 0 12px 40px var(--glass-shadow);
  transition: border-color var(--dur-base) var(--ease-standard), box-shadow var(--dur-base);
}
.composer-inner:focus-within { border-color: var(--line-bright); box-shadow: 0 12px 44px rgba(var(--accent-2-rgb), .18); }
.composer textarea {
  flex: 1; background: transparent; border: none; outline: none; color: var(--text);
  font-size: 14.5px; font-family: inherit; resize: none; max-height: 120px; line-height: 1.5; padding: 8px 0;
}
.composer textarea::placeholder { color: var(--muted2); }
.send-btn {
  width: 42px; height: 42px; border-radius: var(--radius-md); border: none; cursor: pointer; flex-shrink: 0;
  background: linear-gradient(135deg, var(--accent), var(--accent-2)); color: var(--on-accent);
  display: grid; place-items: center;
  transition: transform var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base), filter var(--dur-base);
}
.send-btn:hover:not(:disabled) { transform: scale(1.05); box-shadow: 0 0 22px rgba(var(--accent-rgb), .4); filter: brightness(1.07); }
.send-btn:active { transform: scale(.95); }
.send-btn:disabled { opacity: .4; cursor: not-allowed; transform: none; box-shadow: none; }
.send-btn.stop { background: var(--surface2); color: var(--danger); }
.attach-btn {
  width: 38px; height: 38px; border-radius: var(--radius-md); border: none; cursor: pointer; flex-shrink: 0;
  background: var(--surface2); color: var(--muted); display: grid; place-items: center; align-self: flex-end;
  transition: color var(--dur-fast), transform var(--dur-instant);
}
.attach-btn:hover { color: var(--accent); }
.attach-btn:active { transform: scale(.94); }
.attach-strip { display: flex; gap: 8px; padding: 0 4px 10px; flex-wrap: wrap; }
.attach-thumb { position: relative; width: 56px; height: 56px; border-radius: var(--radius-sm);
  overflow: hidden; border: 1px solid var(--line); }
.attach-thumb img { width: 100%; height: 100%; object-fit: cover; }
.attach-thumb button {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px; border-radius: 50%;
  border: none; cursor: pointer; background: rgba(0,0,0,0.6); color: #fff; display: grid; place-items: center;
}

/* --------------------------------- dock ---------------------------------- */

.dock {
  position: relative; z-index: 10; display: flex; align-items: center; justify-content: center;
  gap: var(--space-md); padding: 0 24px 20px; flex-wrap: wrap;
}
.dock-group {
  display: flex; align-items: center; gap: 6px; padding: 5px;
  background: var(--surface2); border: 1px solid var(--line); border-radius: var(--radius-lg);
  backdrop-filter: blur(16px) saturate(1.2); -webkit-backdrop-filter: blur(16px) saturate(1.2);
  box-shadow: 0 8px 28px var(--glass-shadow);
}
.mode-tab {
  padding: 9px 18px; border-radius: var(--radius-sm); cursor: pointer; font-size: 13px;
  color: var(--muted); display: flex; align-items: center; gap: 8px;
  border: none; background: transparent; font-family: inherit;
  transition: color var(--dur-fast), background var(--dur-base) var(--ease-emphasized), box-shadow var(--dur-base);
}
.mode-tab:hover { color: var(--text); }
.mode-tab.on {
  background: linear-gradient(135deg, rgba(var(--accent-rgb), .2), rgba(var(--accent-2-rgb), .14));
  color: var(--text); box-shadow: inset 0 0 0 1px rgba(var(--accent-rgb), .28);
}
.eff-opt {
  padding: 8px 12px; border-radius: var(--radius-sm); cursor: pointer; font-size: 12px;
  color: var(--muted); display: flex; align-items: center; gap: 6px; white-space: nowrap;
  border: none; background: transparent; font-family: inherit;
  transition: color var(--dur-fast), background var(--dur-base) var(--ease-emphasized);
}
.eff-opt:hover { color: var(--text); }
.eff-opt.on { color: var(--on-accent); background: linear-gradient(135deg, var(--accent), var(--accent-2)); }
.eff-opt:disabled, .toggle-btn:disabled {
  opacity: .38; cursor: not-allowed; filter: saturate(.55);
}
.eff-opt:disabled:hover, .toggle-btn:disabled:hover { color: var(--muted); }
.eff-opt.on:disabled { color: var(--muted); background: rgba(var(--accent-rgb), .08); }
.toggle-btn {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: var(--radius-sm);
  cursor: pointer; font-size: 12px; color: var(--muted); border: 1px solid transparent;
  background: transparent; font-family: inherit;
  transition: color var(--dur-fast), background var(--dur-fast), border-color var(--dur-fast);
}
.toggle-btn:hover { color: var(--text); }
.toggle-btn.on { color: var(--accent); }
.toggle-btn.ember.on {
  color: var(--ember); background: linear-gradient(135deg, rgba(255,138,91,.18), rgba(var(--accent-2-rgb), .14));
  border-color: rgba(255,138,91,.4);
}
.capability-dock-group { position: relative; }
.cap-info {
  width: 24px; height: 24px; display: grid; place-items: center; position: relative;
  color: var(--muted2); border-radius: var(--radius-pill); outline: none; cursor: help;
  transition: color var(--dur-fast), background var(--dur-fast), transform var(--dur-fast);
}
.cap-info:hover, .cap-info:focus-visible {
  color: var(--accent); background: rgba(var(--accent-rgb), .12); transform: translateY(-1px);
}
.cap-tip {
  position: absolute; z-index: 80; left: 50%; bottom: calc(100% + 9px); width: max-content;
  max-width: min(320px, 78vw); padding: 8px 10px; border-radius: var(--radius-sm);
  color: var(--text); background: var(--bg2); border: 1px solid var(--line);
  box-shadow: 0 10px 30px var(--glass-shadow); font-size: 11px; line-height: 1.4;
  opacity: 0; visibility: hidden; pointer-events: none;
  transform: translate(-50%, 4px); transition: opacity var(--dur-fast), transform var(--dur-fast), visibility var(--dur-fast);
}
.cap-info:hover .cap-tip, .cap-info:focus-visible .cap-tip {
  opacity: 1; visibility: visible; transform: translate(-50%, 0);
}
.thinking-badge { color: var(--accent-2); background: rgba(var(--accent-2-rgb), .12); }

@media (max-width: 720px) {
  .dock { gap: 8px; padding: 0 12px 14px; }
  .eff-opt span.txt { display: none; }
  .chat-scroll { padding: 16px 16px 10px; }
  .composer { padding: 12px 16px 8px; }
  .hero .hero-title { font-size: 26px; }
  .bubble { max-width: 88%; }
  .msg-meta, .msg-actions, .trace-slot { margin-left: 0; }
}

/* ------------------------------- markdown -------------------------------- */

.md { font-size: 14.5px; line-height: 1.65; }
.md p { margin: 0 0 9px; }
.md p:last-child { margin-bottom: 0; }
.md h1, .md h2, .md h3, .md h4 { font-family: 'Syne', sans-serif; margin: 12px 0 7px; line-height: 1.25; }
.md h1 { font-size: 19px; } .md h2 { font-size: 17px; } .md h3 { font-size: 15.5px; } .md h4 { font-size: 14px; }
.md ul, .md ol { margin: 0 0 9px; padding-left: 20px; }
.md li { margin: 3px 0; }
.md a { color: var(--accent); text-decoration: underline; text-underline-offset: 2px; }
.md blockquote { border-left: 2px solid var(--line-bright); margin: 0 0 9px; padding: 2px 0 2px 12px; color: var(--muted); }
.md-ic { background: var(--surface2); border: 1px solid var(--line); border-radius: 5px;
  padding: 1px 5px; font-family: 'JetBrains Mono', monospace; font-size: 12.5px; }

.code-block {
  margin: 9px 0; border: 1px solid var(--line); border-radius: var(--radius-md);
  overflow: hidden; background: var(--code-bg);
  transition: border-color var(--dur-base), box-shadow var(--dur-base);
}
.code-block:hover { border-color: var(--line-bright); box-shadow: 0 6px 20px rgba(0,0,0,.3); }
.code-bar { display: flex; align-items: center; justify-content: space-between; padding: 7px 12px;
  background: var(--surface1); border-bottom: 1px solid var(--line); }
.code-bar .lang { font-family: 'JetBrains Mono', monospace; font-size: 10.5px; color: var(--muted2);
  letter-spacing: .5px; text-transform: uppercase; }
.code-copy {
  display: flex; align-items: center; gap: 6px; background: transparent; border: none;
  cursor: pointer; color: var(--muted); font-size: 11.5px; font-family: inherit;
  transition: color var(--dur-fast);
}
.code-copy:hover { color: var(--accent); }
.code-block pre { margin: 0; padding: 12px 14px; overflow-x: auto; }
.code-block code { font-family: 'JetBrains Mono', monospace; font-size: 12.5px; line-height: 1.6;
  color: var(--code-fg); white-space: pre; }

/* ------------------------- görev zaman çizelgesi ------------------------- */

.timeline { display: flex; flex-direction: column; gap: 0; position: relative; }
.tl-item { display: grid; grid-template-columns: 22px 1fr; gap: 10px; padding-bottom: 14px; position: relative; }
.tl-item::before {
  content: ''; position: absolute; left: 10px; top: 20px; bottom: 0; width: 1px;
  background: var(--line);
}
.tl-item:last-child::before { display: none; }
.tl-dot {
  width: 20px; height: 20px; border-radius: 50%; display: grid; place-items: center;
  background: var(--surface2); border: 1px solid var(--line); color: var(--muted); z-index: 1;
}
.tl-dot.accent { border-color: rgba(var(--accent-rgb), .5); background: rgba(var(--accent-rgb), .16); color: var(--accent); }
.tl-dot.ok { border-color: rgba(83,214,166,.5); background: rgba(83,214,166,.16); color: var(--success); }
.tl-dot.warn { border-color: rgba(255,200,87,.5); background: rgba(255,200,87,.16); color: var(--warning); }
.tl-dot.err { border-color: rgba(251,113,133,.5); background: rgba(251,113,133,.16); color: var(--danger); }
.tl-body { min-width: 0; padding-top: 1px; }
.tl-label { font-size: 13px; color: var(--text); }
.tl-sum { font-size: 12px; color: var(--muted); margin-top: 2px; line-height: 1.5; word-break: break-word; }
.tl-time { font-size: 10.5px; color: var(--muted2); font-family: 'JetBrains Mono', monospace; margin-top: 3px; }
`;
