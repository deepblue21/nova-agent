// Kabuk: sol ray, üst çubuk, sahne, dar ekran alt gezinme çubuğu, çekmece,
// modal ve artefakt paneli. Gezinme yerleşimi Android NavigationBar ile
// aynı sıra/etiket/ikon kümesini kullanır.

export const SHELL_CSS = `
/* ------------------------------- sol ray --------------------------------- */

.rail { display: none; }
.rail-only-narrow { display: inline-flex; }

@media (min-width: 981px) {
  .rail {
    display: flex; flex-direction: column; gap: var(--space-sm);
    position: fixed; left: 0; top: 0; bottom: 0; width: 240px; z-index: 40;
    padding: 14px 10px;
    background: linear-gradient(180deg, var(--glass-1), var(--glass-2));
    backdrop-filter: blur(20px) saturate(1.16);
    -webkit-backdrop-filter: blur(20px) saturate(1.16);
    border-right: 1px solid var(--line);
    box-shadow: 1px 0 30px var(--glass-shadow);
    animation: railIn var(--dur-slow) var(--ease-standard);
  }
  .nova-root.has-rail > .topbar,
  .nova-root.has-rail > .stage,
  .nova-root.has-rail > .dock { margin-left: 240px; }
  .rail-only-narrow { display: none; }
}

.rail-brand {
  display: flex; align-items: center; gap: 9px;
  font-family: 'Syne', sans-serif; font-weight: 700; font-size: 16px;
  letter-spacing: 1px; padding: 4px 6px 2px;
}

/* Bölüm gezinmesi — mobil alt çubuğun masaüstü karşılığı. */
.rail-nav { display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex; align-items: center; gap: 11px;
  min-height: 44px; padding: 7px 10px; border-radius: var(--radius-md);
  background: transparent; border: 1px solid transparent;
  color: var(--muted); font-size: 13.5px; font-family: inherit;
  cursor: pointer; text-align: left; width: 100%;
  transition: color var(--dur-fast) var(--ease-standard),
              background var(--dur-fast) var(--ease-standard),
              border-color var(--dur-fast) var(--ease-standard);
}
.rail > .btn.block,
.rail > .btn-accent-soft { min-height: 44px; }
.nav-item:hover { color: var(--text); background: var(--surface1); }
.nav-item.on {
  color: var(--text);
  background: linear-gradient(135deg, rgba(var(--accent-rgb), 0.18), rgba(var(--accent-2-rgb), 0.12));
  border-color: rgba(var(--accent-rgb), 0.28);
}
.nav-item.on svg { color: var(--accent); }
.nav-item .nav-badge {
  margin-left: auto; min-width: 20px; height: 20px; padding: 0 6px;
  border-radius: var(--radius-pill); display: grid; place-items: center;
  font-family: 'JetBrains Mono', monospace; font-size: 10.5px;
  background: rgba(var(--accent-rgb), 0.16); color: var(--accent);
}

.rail-search { position: relative; }
.rail-search input {
  width: 100%; background: var(--surface1); border: 1px solid var(--line);
  border-radius: var(--radius-sm); padding: 9px 30px 9px 12px;
  color: var(--text); font-size: 13px; font-family: inherit; outline: none;
  transition: border-color var(--dur-fast) var(--ease-standard);
}
.rail-search input:focus { border-color: var(--line-bright); }
.rail-search button {
  position: absolute; right: 7px; top: 50%; transform: translateY(-50%);
  background: none; border: none; color: var(--muted2); cursor: pointer; display: flex;
}

.rail-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 3px; margin: 0 -4px; padding: 0 4px; }
.rail-empty { color: var(--muted2); font-size: 12.5px; text-align: center; padding: 18px 0; }

.rail-export { display: flex; flex-wrap: wrap; gap: 6px; font-size: 11px; color: var(--muted2); padding: 0 2px; }
.rail-export > span { flex: 1 0 100%; line-height: 1.2; }
.rail-export button {
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  flex: 1 1 calc(50% - 3px); min-width: 0;
  background: transparent; border: 1px solid var(--line); border-radius: var(--radius-xs);
  padding: 5px 7px; color: var(--muted); font-size: 11px; font-family: inherit;
  cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  transition: color var(--dur-fast), border-color var(--dur-fast);
}
.rail-export button:hover { color: var(--accent); border-color: var(--line-bright); }

/* ------------------------------ üst çubuk -------------------------------- */

.topbar {
  position: relative; z-index: 10;
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-md); padding: 16px 26px;
}
.brand { display: flex; align-items: center; gap: 13px; min-width: 0; }
/* Marka işareti artık NovaMark bileşeni (styles/brand.mjs); burada yalnız
   üst çubuktaki nabız veriliyor. */
.topbar .nova-badge { animation: markPulse 4s ease-in-out infinite; }
.brand-text h1 {
  font-family: 'Syne', sans-serif; font-weight: 800; font-size: 19px;
  letter-spacing: 1.5px; line-height: 1;
  background: linear-gradient(90deg, var(--text), color-mix(in srgb, var(--accent) 65%, var(--text)));
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.brand-text .sub {
  font-family: 'JetBrains Mono', monospace; font-size: 10.5px;
  color: var(--muted); letter-spacing: .4px; margin-top: 3px;
  display: flex; align-items: center; gap: 6px;
}
.brand-text .sub b { color: var(--accent); font-weight: 500; }
.topright { display: flex; align-items: center; gap: var(--space-sm); }

.status-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0;
  background: var(--accent); box-shadow: 0 0 8px var(--accent); animation: softPulse 2s infinite; }
.status-dot.off { background: var(--muted2); box-shadow: none; animation: none; }
.status-dot.ok { background: var(--success); box-shadow: 0 0 8px var(--success); }
.status-dot.warn { background: var(--warning); box-shadow: 0 0 8px var(--warning); }
.status-dot.err { background: var(--danger); box-shadow: 0 0 8px var(--danger); animation: none; }

.status-pill {
  display: flex; align-items: center; gap: var(--space-sm);
  padding: 8px 14px; border-radius: var(--radius-pill);
  background: var(--surface1); border: 1px solid var(--line);
  font-size: 12px; color: var(--muted); font-family: 'JetBrains Mono', monospace;
  max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  transition: border-color var(--dur-fast), background var(--dur-fast);
}
.status-pill.clickable { cursor: pointer; }
.status-pill.clickable:hover, .status-pill.clickable.open { border-color: var(--line-bright); background: var(--surface2); }

.icon-btn {
  width: 40px; height: 40px; border-radius: var(--radius-md);
  background: var(--surface1); border: 1px solid var(--line); color: var(--muted);
  cursor: pointer; display: grid; place-items: center;
  transition: color var(--dur-fast), border-color var(--dur-fast), background var(--dur-fast), transform var(--dur-instant);
}
.icon-btn:hover { color: var(--text); border-color: var(--line-bright); background: var(--surface2); }
.icon-btn:active { transform: scale(.94); }

.settings-motion { transform-origin: center; }
.settings-motion svg {
  transform-origin: center;
  transition: transform var(--dur-fast) var(--ease-emphasized);
}
.settings-motion:active { transform: scale(.96); }
.settings-motion:active svg { transform: rotate(22deg); }

.nav-item:focus-visible, .bottom-nav button:focus-visible, .settings-motion:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* -------------------------------- sahne ---------------------------------- */

.stage { position: relative; z-index: 5; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.panel-scroll {
  flex: 1; min-height: 0; overflow-y: auto;
  padding: 4px 26px 24px;
  width: 100%; max-width: 900px; margin: 0 auto;
  display: flex; flex-direction: column; gap: var(--space-lg);
  animation: fadeUp var(--dur-base) var(--ease-standard);
}

/* --------------------- dar ekran alt gezinme çubuğu ---------------------- */
/* Android NavigationBar'ın birebir web karşılığı. */

.bottom-nav { display: none; }
@media (max-width: 980px) {
  .bottom-nav {
    display: grid; grid-auto-flow: column; grid-auto-columns: 1fr;
    position: relative; z-index: 10;
    height: calc(58px + env(safe-area-inset-bottom, 0px));
    min-height: 58px;
    background: var(--glass-2); border-top: 1px solid var(--line);
    backdrop-filter: blur(20px) saturate(1.12);
    -webkit-backdrop-filter: blur(20px) saturate(1.12);
    padding: 4px 4px calc(4px + env(safe-area-inset-bottom, 0px));
  }
  .bottom-nav button {
    display: flex; flex-direction: column; align-items: center; gap: 3px;
    min-width: 44px; min-height: 44px;
    background: none; border: none; cursor: pointer;
    color: var(--muted); font-family: inherit; font-size: 11px; padding: 2px;
    transition: color var(--dur-fast) var(--ease-standard),
                transform var(--dur-fast) var(--ease-standard);
  }
  .bottom-nav .bn-ic {
    width: 50px; height: 28px; border-radius: var(--radius-pill);
    display: grid; place-items: center;
    transition: background var(--dur-base) var(--ease-emphasized),
                color var(--dur-fast) var(--ease-standard),
                transform var(--dur-fast) var(--ease-emphasized);
  }
  .bottom-nav button.on { color: var(--text); }
  .bottom-nav button.on .bn-ic {
    background: rgba(var(--accent-rgb), 0.16);
    color: var(--accent);
    transform: translateY(-2px);
  }
}

/* ------------------------------- çekmece --------------------------------- */

.scrim {
  position: fixed; inset: 0; z-index: 90;
  background: var(--scrim); backdrop-filter: blur(4px);
  animation: fadeIn var(--dur-base) var(--ease-standard);
}
.drawer {
  position: fixed; top: 0; left: 0; bottom: 0; z-index: 91;
  width: 300px; max-width: 86vw;
  background: linear-gradient(180deg, var(--glass-1), var(--glass-2));
  backdrop-filter: blur(20px);
  border-right: 1px solid var(--line);
  padding: 18px 14px; display: flex; flex-direction: column; gap: var(--space-xs);
  animation: slideFromLeft var(--dur-base) var(--ease-standard);
}
.drawer-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-sm); }
.drawer-head .title { font-family: 'Syne', sans-serif; font-weight: 700; font-size: 15px; }

/* -------------------------------- modal ---------------------------------- */

.overlay {
  position: fixed; inset: 0; z-index: 100;
  background: var(--scrim); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
  animation: fadeIn var(--dur-base) var(--ease-standard);
}
.modal {
  width: 100%; max-width: 560px; max-height: 90vh; overflow-y: auto;
  border-radius: var(--radius-xxl); padding: 24px;
  background: linear-gradient(180deg, var(--glass-1), var(--glass-2));
  backdrop-filter: blur(22px) saturate(1.2);
  border: 1px solid var(--line);
  box-shadow: 0 30px 80px var(--glass-shadow), 0 0 0 1px var(--line);
  animation: scaleIn var(--dur-base) var(--ease-emphasized);
}
.modal-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-md); }
.modal h2 {
  font-family: 'Syne', sans-serif; font-weight: 700; font-size: 20px;
  display: flex; align-items: center; gap: 10px;
}
.modal .m-sub { color: var(--muted); font-size: 13px; margin-top: 5px; }
.m-section { margin-top: 22px; }

/* Ayarlar bölüm başlıkları — açılır/kapanır. */
.m-acc { border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface1); margin-bottom: 10px; overflow: hidden; }
.m-acc-head {
  width: 100%; display: flex; align-items: center; gap: 10px;
  padding: 13px 15px; background: none; border: none; cursor: pointer;
  color: var(--text); font-family: inherit; font-size: 13.5px; text-align: left;
  transition: background var(--dur-fast);
}
.m-acc-head:hover { background: var(--surface2); }
.m-acc-head svg:first-child { color: var(--accent); flex-shrink: 0; }
.m-acc-head .m-acc-t { flex: 1; }
.m-acc-head .m-acc-d { display: block; font-size: 11.5px; color: var(--muted); margin-top: 2px; }
.m-acc-head .chev { color: var(--muted); transition: transform var(--dur-fast) var(--ease-standard); }
.m-acc-head.open .chev { transform: rotate(180deg); }
.m-acc-body { padding: 0 15px 15px; animation: fadeIn var(--dur-fast) var(--ease-standard); }

/* ---------------------------- artefakt paneli ---------------------------- */

.artifact-panel {
  position: fixed; top: 0; right: 0; bottom: 0; z-index: 60;
  width: 46%; min-width: 380px; max-width: 720px;
  display: flex; flex-direction: column;
  background: linear-gradient(180deg, var(--glass-1), var(--glass-2));
  backdrop-filter: blur(20px) saturate(1.15);
  border-left: 1px solid var(--line);
  box-shadow: -18px 0 50px var(--glass-shadow);
  animation: slideFromRight var(--dur-base) var(--ease-standard);
}
.ap-head { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px; border-bottom: 1px solid var(--line); }
.ap-title { display: flex; align-items: center; gap: 8px; font-size: 13.5px; }
.ap-title svg { color: var(--accent); }
.ap-type { font-family: 'JetBrains Mono', monospace; font-size: 11.5px; color: var(--muted); text-transform: uppercase; letter-spacing: .5px; }
.ap-warn { font-family: 'JetBrains Mono', monospace; font-size: 10.5px; color: var(--warning);
  border: 1px solid rgba(255,200,87,.28); border-radius: var(--radius-xs); padding: 2px 6px; background: rgba(255,200,87,.08); }
.ap-actions { display: flex; gap: 4px; }
.ap-btn {
  width: 32px; height: 32px; border-radius: var(--radius-sm);
  background: transparent; border: 1px solid transparent; color: var(--muted);
  display: grid; place-items: center; cursor: pointer;
  transition: color var(--dur-fast), border-color var(--dur-fast), background var(--dur-fast);
}
.ap-btn:hover { color: var(--text); border-color: var(--line); background: var(--surface1); }
.ap-browser { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: var(--surface2); border-bottom: 1px solid var(--line); }
.ap-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.ap-dot.r { background: #ff5f57; } .ap-dot.y { background: #febc2e; } .ap-dot.g { background: #28c840; }
.ap-url {
  flex: 1; margin-left: 8px; display: flex; align-items: center; gap: 6px;
  height: 27px; padding: 0 12px; border-radius: var(--radius-xs);
  background: rgba(255,255,255,.05); border: 1px solid var(--line);
  color: var(--muted); font-family: 'JetBrains Mono', monospace; font-size: 11px;
}
.ap-url svg { color: var(--accent); }
.ap-frame { flex: 1; border: none; width: 100%; background: #fff; }

@media (max-width: 980px) { .artifact-panel { width: 100%; min-width: 0; max-width: none; } }

@media (max-width: 720px) {
  .topbar { padding: 12px 16px; }
  .panel-scroll { padding: 4px 16px 20px; }
  .brand-text .sub { display: none; }
  .status-pill { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .settings-motion,
  .settings-motion svg,
  .bottom-nav button,
  .bottom-nav .bn-ic { transition: none !important; }
  .settings-motion:active, .settings-motion:active svg, .bottom-nav button.on .bn-ic {
    transform: none !important;
    transition: none !important;
  }
}
`;
