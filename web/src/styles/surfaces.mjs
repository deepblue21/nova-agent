// Yüzey ve kontrol katmanı: kart, çip, buton, form, açılır liste, satır listesi,
// politika seçici, görev/model kartları. Ölçüler Compose ekranlarıyla aynı
// (kart 20dp yarıçap / 18dp iç boşluk, çip pill, kenarlık 1dp).

export const SURFACES_CSS = `
/* -------------------------------- kart ---------------------------------- */

.card {
  background: var(--surface1);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  padding: 18px;
  display: flex; flex-direction: column; gap: var(--space-md);
  transition: border-color var(--dur-base) var(--ease-standard), box-shadow var(--dur-base) var(--ease-standard);
}
.card.glass { backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); }
.card.hoverable:hover { border-color: var(--line-bright); box-shadow: 0 10px 30px var(--glass-shadow); }
.card.accent { border-color: rgba(var(--accent-rgb), .32); background: rgba(var(--accent-rgb), .06); }
.card-head { display: flex; align-items: center; gap: 13px; }
.card-head .ch-ic {
  width: 40px; height: 40px; border-radius: var(--radius-md); flex-shrink: 0;
  display: grid; place-items: center; color: var(--accent);
  background: linear-gradient(135deg, rgba(var(--accent-rgb), .16), rgba(var(--accent-2-rgb), .10));
}
.card-head .ch-tx { flex: 1; min-width: 0; }
.card-head .ch-t { font-size: 14.5px; font-weight: 500; }
.card-head .ch-d { font-size: 11.5px; color: var(--muted); margin-top: 2px; line-height: 1.45; }

.card-title-lg { font-size: 22px; font-weight: 700; font-family: 'Syne', sans-serif; line-height: 1.15; }
.card-sub-accent { color: var(--accent); font-size: 13px; }

.hint { font-size: 12px; color: var(--muted); line-height: 1.5; }
.hint b { color: var(--text); font-weight: 500; }
.hint.err { color: var(--danger); }

/* -------------------------------- çipler -------------------------------- */

.chip {
  display: inline-flex; align-items: center; gap: 5px;
  font-family: 'JetBrains Mono', monospace; font-size: 10.5px;
  background: var(--surface1); border: 1px solid var(--line);
  border-radius: var(--radius-xs); padding: 3px 8px; color: var(--muted);
  white-space: nowrap;
}
.chip svg { color: var(--muted2); }
.chip.accent { color: color-mix(in srgb, var(--accent) 72%, var(--text));
  background: rgba(var(--accent-rgb), .09); border-color: rgba(var(--accent-rgb), .22); }
.chip.accent svg { color: var(--accent); }
.chip.ok { color: var(--success); background: rgba(83,214,166,.09); border-color: rgba(83,214,166,.24); }
.chip.ok svg { color: var(--success); }
.chip.warn { color: var(--warning); background: rgba(255,200,87,.09); border-color: rgba(255,200,87,.24); }
.chip.warn svg { color: var(--warning); }
.chip.err { color: var(--danger); background: rgba(251,113,133,.09); border-color: rgba(251,113,133,.24); }
.chip.err svg { color: var(--danger); }
.chip.muted { color: var(--muted2); }

/* ------------------------------- butonlar ------------------------------- */

.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 7px;
  height: 40px; padding: 0 16px; border-radius: var(--radius-md);
  border: 1px solid var(--line); background: var(--surface1); color: var(--muted);
  font-family: inherit; font-size: 13px; cursor: pointer;
  transition: color var(--dur-fast), border-color var(--dur-fast), background var(--dur-fast),
              transform var(--dur-instant), box-shadow var(--dur-base);
}
.btn:hover:not(:disabled) { color: var(--text); border-color: var(--line-bright); background: var(--surface2); }
.btn:active:not(:disabled) { transform: scale(.96); }
.btn:disabled { opacity: .45; cursor: not-allowed; }
.btn.primary {
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  color: var(--on-accent); border-color: transparent; font-weight: 600;
}
.btn.primary:hover:not(:disabled) { box-shadow: 0 0 24px rgba(var(--accent-rgb), .38); filter: brightness(1.06); color: var(--on-accent); }
.btn.ghost { background: transparent; }
.btn.danger { color: var(--danger); border-color: rgba(251,113,133,.3); }
.btn.danger:hover:not(:disabled) { background: rgba(251,113,133,.1); color: var(--danger); }
.btn.sm { height: 32px; padding: 0 11px; font-size: 12px; border-radius: var(--radius-sm); }
.btn.pill { border-radius: var(--radius-pill); }
.btn.block { width: 100%; }

.btn-accent-soft {
  display: flex; align-items: center; gap: 9px; width: 100%;
  padding: 11px 13px; border-radius: var(--radius-md);
  border: 1px solid rgba(var(--accent-rgb), .34); background: rgba(var(--accent-rgb), .09);
  color: var(--accent); cursor: pointer; font-size: 13.5px; font-family: inherit;
  transition: background var(--dur-fast) var(--ease-standard);
}
.btn-accent-soft:hover { background: rgba(var(--accent-rgb), .16); }

/* ------------------------------ form alanları ---------------------------- */

.field { display: flex; flex-direction: column; gap: 6px; }
.field label {
  font-family: 'JetBrains Mono', monospace; font-size: 10px;
  letter-spacing: .5px; color: var(--muted2); text-transform: uppercase;
}
.input, .textarea, .select {
  width: 100%; background: var(--bg2); border: 1px solid var(--line);
  border-radius: var(--radius-sm); padding: 10px 13px;
  color: var(--text); font-size: 13px; font-family: inherit; outline: none;
  transition: border-color var(--dur-fast) var(--ease-standard);
}
.input:focus, .textarea:focus, .select:focus { border-color: var(--line-bright); }
.input::placeholder, .textarea::placeholder { color: var(--muted2); }
.input.mono { font-family: 'JetBrains Mono', monospace; font-size: 12.5px; }
.textarea { resize: vertical; min-height: 62px; line-height: 1.55; }
.select { cursor: pointer; appearance: none;
  background-image: linear-gradient(45deg, transparent 50%, var(--muted) 50%), linear-gradient(135deg, var(--muted) 50%, transparent 50%);
  background-position: calc(100% - 16px) center, calc(100% - 11px) center;
  background-size: 5px 5px, 5px 5px; background-repeat: no-repeat; padding-right: 30px; }
.select option { background: var(--bg2); color: var(--text); }
.row { display: flex; gap: var(--space-sm); align-items: center; }
.row.end { justify-content: flex-end; }
.row.wrap { flex-wrap: wrap; }
.grow { flex: 1; min-width: 0; }

/* anahtar (switch) — mobil Switch ile aynı ölçü */
.switch {
  display: inline-flex; align-items: center; gap: 9px;
  background: none; border: none; cursor: pointer; padding: 0;
  color: var(--muted); font-family: inherit; font-size: 12.5px;
  transition: color var(--dur-fast);
}
.switch.on { color: var(--accent); }
.switch .track {
  width: 34px; height: 19px; border-radius: var(--radius-pill);
  background: var(--surface2); position: relative; flex-shrink: 0;
  transition: background var(--dur-base) var(--ease-standard);
}
.switch.on .track { background: rgba(var(--accent-rgb), .38); }
.switch .track::after {
  content: ''; position: absolute; width: 15px; height: 15px; border-radius: 50%;
  background: var(--muted); top: 2px; left: 2px;
  transition: left var(--dur-base) var(--ease-emphasized), background var(--dur-base);
}
.switch.on .track::after { left: 17px; background: var(--accent); }

/* ------------------------- seçim ızgarası (politika) --------------------- */

.pick-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); }
@media (max-width: 560px) { .pick-grid { grid-template-columns: 1fr; } }
.pick {
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px;
  text-align: left; min-height: 64px; padding: 12px;
  background: var(--surface1); border: 1px solid var(--line); border-radius: var(--radius-lg);
  color: var(--text); font-family: inherit; cursor: pointer;
  transition: border-color var(--dur-fast), background var(--dur-fast), transform var(--dur-instant);
}
.pick:hover:not(.disabled) { border-color: var(--line-bright); background: var(--surface2); }
.pick:active:not(.disabled) { transform: scale(.985); }
.pick.sel { border-color: rgba(var(--accent-rgb), .5); background: rgba(var(--accent-rgb), .14); }
.pick.disabled { opacity: .45; cursor: not-allowed; }
.pick .pk-ic { width: 34px; height: 34px; border-radius: var(--radius-sm); display: grid; place-items: center;
  background: var(--surface2); color: var(--accent); flex-shrink: 0; }
.pick .pk-t { display: block; font-size: 13.5px; font-weight: 600; line-height: 1.25; }
.pick .pk-d { display: block; font-size: 11.5px; color: var(--muted); line-height: 1.35; margin-top: 2px; }
.pick .pk-check { color: var(--accent); }

/* Pulse Aperture tema seçici — küçük görsel, tam erişilebilir hedef. */
.theme-pick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
}
.theme-pick {
  min-width: 0;
  min-height: 56px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--panel) 84%, transparent);
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 200ms var(--ease-standard),
              background 200ms var(--ease-standard),
              transform 180ms var(--ease-standard);
}
.theme-pick:hover {
  border-color: rgba(var(--accent-rgb), .30);
  background: color-mix(in srgb, var(--surface2) 64%, var(--panel));
}
.theme-pick:active { transform: scale(.985); }
.theme-pick:focus-visible {
  outline: 2px solid rgba(var(--accent-rgb), .72);
  outline-offset: 2px;
}
.theme-pick.sel {
  border-color: rgba(var(--accent-rgb), .46);
  background: rgba(var(--accent-rgb), .10);
}
.theme-pick-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: rgba(var(--ap-mid-rgb), .08);
}
.theme-pick-copy { min-width: 0; }
.theme-pick-name {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.theme-pick-meta {
  display: block;
  margin-top: 2px;
  color: var(--muted2);
  font-size: 9.5px;
  line-height: 1.2;
}
.theme-pick-check { color: var(--accent); }
@media (max-width: 700px) {
  .theme-pick-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

/* --------------------------- satır listesi (KB) -------------------------- */

.list { display: flex; flex-direction: column; gap: 5px; }
.list-row {
  display: flex; align-items: center; gap: 9px;
  padding: 9px 12px; background: var(--surface1);
  border: 1px solid var(--line); border-radius: var(--radius-sm); font-size: 12.5px;
  transition: border-color var(--dur-fast);
}
.list-row:hover { border-color: var(--line-bright); }
.list-row > svg:first-child { color: var(--accent); flex-shrink: 0; }
.list-row .lr-name { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.list-row .lr-meta { color: var(--muted2); font-size: 11px; font-family: 'JetBrains Mono', monospace; white-space: nowrap; }
.list-row .lr-meta.accent { color: var(--accent); }
.icon-act { background: none; border: none; color: var(--muted2); cursor: pointer; display: flex; padding: 2px;
  transition: color var(--dur-fast); }
.icon-act:hover { color: var(--text); }
.icon-act.danger:hover { color: var(--danger); }

/* ------------------------------ sohbet satırı --------------------------- */

.conv-row {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 11px; border-radius: var(--radius-sm); cursor: pointer;
  color: var(--muted); transition: background var(--dur-fast), color var(--dur-fast);
}
.conv-row:hover { background: var(--surface2); color: var(--text); }
.conv-row.on { background: rgba(var(--accent-rgb), .12); color: var(--text); }
.conv-row .cr-title { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-row .cr-cloud { color: var(--accent); opacity: .7; flex-shrink: 0; }
.conv-row .cr-del { opacity: 0; border: none; background: transparent; color: var(--muted2); cursor: pointer; display: flex;
  transition: opacity var(--dur-fast), color var(--dur-fast); }
.conv-row:hover .cr-del { opacity: 1; }
.conv-row .cr-del:hover { color: var(--danger); }

/* ------------------------------ açılır liste ---------------------------- */

.selector { position: relative; }
.sel-btn {
  display: flex; align-items: center; gap: 9px; padding: 9px 13px;
  border-radius: var(--radius-md); background: transparent; border: none;
  cursor: pointer; color: var(--text); font-size: 13px; font-family: inherit;
  transition: background var(--dur-fast);
}
.sel-btn:hover { background: var(--surface2); }
.sel-btn .sel-icon { color: var(--accent); display: flex; }
.sel-btn .sel-meta { display: flex; flex-direction: column; align-items: flex-start; line-height: 1.15; }
.sel-btn .sel-meta .lbl { font-size: 9.5px; color: var(--muted2); font-family: 'JetBrains Mono', monospace; letter-spacing: .5px; }
.sel-btn .sel-meta .val { font-size: 13px; font-weight: 500; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sel-btn .chev { color: var(--muted); transition: transform var(--dur-fast) var(--ease-standard); }
.sel-btn.open .chev { transform: rotate(180deg); }

.dropdown {
  position: absolute; bottom: calc(100% + 10px); left: 0; z-index: 50;
  min-width: 280px; max-height: 60vh; overflow-y: auto;
  background: var(--bg2); border: 1px solid var(--line); border-radius: var(--radius-lg);
  padding: 7px; box-shadow: 0 20px 60px var(--glass-shadow);
  animation: fadeUp var(--dur-fast) var(--ease-standard);
}
.dropdown.below { bottom: auto; top: calc(100% + 8px); left: auto; right: 0; width: 330px; max-height: 64vh; }

.dd-group-label {
  font-size: 9.5px; color: var(--muted2); font-family: 'JetBrains Mono', monospace;
  letter-spacing: .8px; padding: 9px 11px 5px; text-transform: uppercase;
}
.dd-item {
  display: flex; align-items: center; gap: 11px; padding: 10px 11px;
  border-radius: var(--radius-sm); cursor: pointer;
  transition: background var(--dur-fast);
}
.dd-item:hover { background: var(--surface2); }
.dd-item.sel { background: rgba(var(--accent-rgb), .12); }
.dd-item.disabled { opacity: .42; cursor: not-allowed; }
.dd-item.disabled:hover { background: transparent; }
.dd-item .di-ic { width: 30px; height: 30px; border-radius: var(--radius-sm); background: var(--surface2);
  display: grid; place-items: center; color: var(--muted); flex-shrink: 0; }
.dd-item.sel .di-ic { color: var(--accent); background: rgba(var(--accent-rgb), .16); }
.dd-item .di-txt { flex: 1; min-width: 0; }
.dd-item .di-txt .t { font-size: 13.5px; display: flex; align-items: center; gap: 7px; }

/* Araç (agentic) yeteneği rozeti — Android ModelsScreen ile aynı sinyal. */
.tools-badge {
  font-family: 'JetBrains Mono', monospace; font-size: 9.5px; letter-spacing: .3px;
  color: var(--accent); background: rgba(var(--accent-rgb), .12);
  border-radius: var(--radius-pill); padding: 2px 7px; flex-shrink: 0;
}
.dd-item .di-txt .d { font-size: 11px; color: var(--muted); margin-top: 2px; font-family: 'JetBrains Mono', monospace;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dd-item .di-check { color: var(--accent); margin-left: auto; flex-shrink: 0; }

.dd-live-bar {
  display: flex; align-items: center; gap: 7px; padding: 7px 10px 8px;
  font-size: 11px; color: var(--muted2); border-bottom: 1px solid var(--line); margin-bottom: 4px;
}
.dd-live-err { padding: 6px 11px 8px; font-size: 11px; color: var(--danger); line-height: 1.45; }
.dd-custom { display: flex; gap: 6px; padding: 6px 8px; }

/* --------------------------- kullanım / ölçüm ---------------------------- */

.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-sm); }
@media (max-width: 720px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
.stat-box { background: var(--surface1); border: 1px solid var(--line); border-radius: var(--radius-md);
  padding: 12px 8px; text-align: center; }
.stat-box .sv { font-size: 16.5px; font-weight: 700; color: var(--accent); font-family: 'JetBrains Mono', monospace; }
.stat-box .sl { font-size: 10.5px; color: var(--muted); margin-top: 3px; letter-spacing: .4px; }

.meter { height: 7px; border-radius: var(--radius-pill); background: var(--surface2); overflow: hidden; }
.meter span { display: block; height: 100%; border-radius: var(--radius-pill);
  background: linear-gradient(90deg, var(--accent), var(--accent-2)); transition: width var(--dur-slow) var(--ease-emphasized); }
.meter-note { font-size: 11px; color: var(--muted); margin-top: 5px; }

.kv-row {
  display: flex; justify-content: space-between; gap: 10px;
  font-size: 11.5px; font-family: 'JetBrains Mono', monospace;
  padding: 7px 10px; background: var(--surface1);
  border: 1px solid var(--line); border-radius: var(--radius-xs);
}
.kv-row .k { color: color-mix(in srgb, var(--accent) 45%, #fff); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kv-row .v { color: var(--muted); white-space: nowrap; }

/* ------------------------------ boş durum -------------------------------- */

.empty-panel {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 34px 20px; text-align: center; color: var(--muted);
  border: 1px dashed var(--line); border-radius: var(--radius-xl); background: rgba(255,255,255,.015);
}
.empty-panel svg { color: var(--muted2); }
.empty-panel .ep-t { font-size: 14px; color: var(--text); }
.empty-panel .ep-d { font-size: 12.5px; max-width: 380px; line-height: 1.55; }
`;
