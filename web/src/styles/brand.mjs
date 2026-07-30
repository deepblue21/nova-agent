// NOVA Pulse Aperture hareket katmanı.
//
// Süreler design/nova-tokens.json > brand.aperture.motion'dan gelir ve
// styles/index.mjs tarafından çalışma zamanında enjekte edilir; burada sabit
// milisaniye YAZILMAZ. Compose `NovaThinkingIndicator` aynı eğri ve süreleri
// kullanır.
//
// Üç hareket kipi vardır ve her biri KENDİ katmanlarını çalıştırır:
//
//   is-brand    sakin nefes + çekirdek + süpürme. Üst çubuk, ray, avatar,
//               karşılama. Halo yalnız eski API ile açıkça istenirse görünür.
//   is-thinking nefes + çekirdek nabzı + yörünge + ışık süpürmesi. Yalnız
//               yanıt beklerken; "çalışıyor" sinyali burada anlamlı.
//   is-preview  hareket EBEVEYNE bağlı. Tema kartı hover ya da seçili
//               (.is-active-preview) olmadıkça durur — on bir tema kartı aynı
//               anda animasyon çalıştırıp pili tüketmesin.

export function brandCss(ap) {
  const m = ap.motion;
  return `
.nova-mark { display: block; overflow: visible; }

/* Durağan kare — Compose'un animated=false hâliyle aynı değerler.
   Animasyon eklenmeyen her kip bu görünümde kalır. */
.nova-mark .nm-breath { transform: scale(var(--nm-scale-rest)); }
.nova-mark .nm-breath .nm-aura { opacity: .72; }
.nova-mark .nm-core { transform: scale(1); }
.nova-mark .nm-sweep { transform: translateX(300px); }
.nova-mark .nm-orbit { transform: rotate(18deg); }
.nova-mark .nm-tilt { transform: rotate(0deg); }

/* --------------------------- marka kipi (sakin) -------------------------- */

.nova-mark.is-brand.is-animated .nm-breath {
  animation: nmBreath ${m.breathMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-brand.is-animated .nm-aura {
  animation: nmAura ${m.breathMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-brand.is-animated .nm-core {
  animation: nmCore ${m.corePulseMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-brand.is-animated .nm-sweep {
  animation: nmSweep ${m.shimmerMs}ms ${m.easing} infinite;
}
.nova-mark.is-brand.is-animated .nm-halo .nm-orbit {
  animation: nmOrbit ${m.orbitMs}ms linear infinite;
}

/* ------------------------- düşünme kipi (tam güç) ------------------------ */

.nova-mark.is-thinking.is-animated .nm-breath {
  animation: nmBreath ${m.breathMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-thinking.is-animated .nm-aura {
  animation: nmAura ${m.breathMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-thinking.is-animated .nm-tilt {
  animation: nmTilt ${m.breathMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-thinking.is-animated .nm-core {
  animation: nmCore ${m.corePulseMs}ms ${m.easing} infinite alternate;
}
.nova-mark.is-thinking.is-animated .nm-orbit {
  animation: nmOrbit ${m.orbitMs}ms linear infinite;
}
.nova-mark.is-thinking.is-animated .nm-sweep {
  animation: nmSweep ${m.shimmerMs}ms ${m.easing} infinite;
}

/* ---------------- önizleme kipi (ebeveyn hover / seçili) ----------------- */

.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-breath,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-breath,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-breath,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-breath {
  animation: nmBreath ${m.breathMs}ms ${m.easing} infinite alternate;
}
.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-aura,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-aura,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-aura,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-aura {
  animation: nmAura ${m.breathMs}ms ${m.easing} infinite alternate;
}
.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-core,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-core,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-core,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-core {
  animation: nmCore ${m.corePulseMs}ms ${m.easing} infinite alternate;
}
.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-tilt,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-tilt,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-tilt,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-tilt {
  animation: nmTilt ${m.breathMs}ms ${m.easing} infinite alternate;
}
.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-orbit,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-orbit,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-orbit,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-orbit {
  animation: nmOrbit ${m.orbitMs}ms linear infinite;
}
.theme-pick:hover > .nova-mark.is-preview.is-animated .nm-sweep,
.is-active-preview > .nova-mark.is-preview.is-animated .nm-sweep,
.theme-pick:hover .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-sweep,
.is-active-preview .theme-pick-mark > .nova-mark.is-preview.is-animated .nm-sweep {
  animation: nmSweep ${m.shimmerMs}ms ${m.easing} infinite;
}

/* ------------------------------ kare dizileri ---------------------------- */

@keyframes nmBreath {
  from { transform: scale(var(--nm-scale-rest)); }
  to   { transform: scale(var(--nm-scale-peak)); }
}
@keyframes nmAura   { from { opacity: .38; } to { opacity: .94; } }
@keyframes nmCore   { from { transform: scale(.82); } to { transform: scale(1.18); } }
@keyframes nmTilt   { from { transform: rotate(-${m.microTiltDeg}deg); } to { transform: rotate(${m.microTiltDeg}deg); } }
@keyframes nmOrbit  { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes nmSweep  { from { transform: translateX(-8px); } to { transform: translateX(500px); } }

/* Marka kutusu: ikonun kendi ışığını taşıyan yumuşak zemin. */
.nova-badge {
  display: inline-grid; place-items: center; flex-shrink: 0; position: relative;
  background:
    radial-gradient(circle at 32% 28%, rgba(var(--ap-glow-rgb), .22), transparent 62%),
    linear-gradient(140deg, rgba(var(--ap-mid-rgb), .26), rgba(var(--ap-mid-rgb), .06));
  border: 1px solid rgba(var(--ap-mid-rgb), .28);
  box-shadow: 0 0 26px rgba(var(--ap-mid-rgb), .22);
  transition: box-shadow var(--dur-base) var(--ease-standard),
              border-color var(--dur-base) var(--ease-standard);
}
.nova-badge:hover { box-shadow: 0 0 34px rgba(var(--ap-mid-rgb), .34); border-color: rgba(var(--ap-mid-rgb), .44); }

/* Sohbet avatarı: kutusuz, doğrudan işaret. */
.avatar.ai.nova-avatar { background: none; box-shadow: none; overflow: visible; }
.avatar.ai.nova-avatar::before { content: none; }

/* Bekleme göstergesi — eski dönen orb'un yerini alır. */
.wait-status .nova-mark { flex-shrink: 0; }

/* ------------------------- yüzey şeması seçimi --------------------------- */

.scheme-pick-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-sm); }
@media (max-width: 560px) { .scheme-pick-row { grid-template-columns: 1fr; } }

.scheme-pick {
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 9px;
  padding: 9px 11px; text-align: left; cursor: pointer; font-family: inherit;
  background: var(--surface1); border: 1px solid var(--line);
  border-radius: var(--radius-md); color: var(--text);
  transition: border-color var(--dur-fast), background var(--dur-fast);
}
.scheme-pick:hover { border-color: var(--line-bright); background: var(--surface2); }
.scheme-pick.sel { border-color: rgba(var(--accent-rgb), .5); background: rgba(var(--accent-rgb), .1); }
.scheme-pick-copy { display: flex; flex-direction: column; min-width: 0; }
.scheme-pick-name { font-size: 13px; font-weight: 600; }
.scheme-pick-meta { font-size: 10.5px; color: var(--muted2); margin-top: 1px; }

/* Örnek yüzey: seçmeden önce şemanın nasıl duracağını gösterir. */
.scheme-swatch {
  width: 26px; height: 26px; border-radius: var(--radius-xs); flex-shrink: 0;
  border: 1px solid var(--line);
}
.scheme-swatch.is-dark { background: linear-gradient(135deg, #0A0E1A, #1A2033); }
.scheme-swatch.is-light { background: linear-gradient(135deg, #F6F3EC, #FFFFFF); }
.scheme-swatch.is-auto { background: linear-gradient(135deg, #0A0E1A 0 50%, #F6F3EC 50% 100%); }

/* ------------------------- tema seçim kartları --------------------------- */

.theme-pick-grid {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm);
}
@media (max-width: 560px) { .theme-pick-grid { grid-template-columns: 1fr; } }

.theme-pick {
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px;
  padding: 10px 12px; text-align: left; cursor: pointer; font-family: inherit;
  background: var(--surface1); border: 1px solid var(--line);
  border-radius: var(--radius-lg); color: var(--text);
  transition: border-color var(--dur-fast), background var(--dur-fast),
              transform var(--dur-instant) var(--ease-standard);
}
.theme-pick:hover { border-color: var(--line-bright); background: var(--surface2); }
.theme-pick:active { transform: scale(.985); }
.theme-pick.sel { border-color: rgba(var(--ap-mid-rgb), .55); background: rgba(var(--ap-mid-rgb), .12); }
.theme-pick-mark {
  display: grid; place-items: center; width: 38px; height: 38px; flex-shrink: 0;
  border-radius: var(--radius-md);
  background: radial-gradient(circle at 32% 28%, rgba(var(--ap-glow-rgb), .18), transparent 66%);
}
.theme-pick-copy { display: flex; flex-direction: column; min-width: 0; }
.theme-pick-name { font-size: 13.5px; font-weight: 600; line-height: 1.25; }
.theme-pick-meta { font-size: 10.5px; color: var(--muted2); margin-top: 2px; }
.theme-pick-check { color: var(--ap-mid); }

/* ---------------------- ses ekranındaki marka işareti --------------------- */
/* Eski canvas orb'unun yerini alır. --nm-level her karede JS'ten yazılır;
   React yeniden render olmaz. Duruma göre renk kayar. */

.nova-voice-mark {
  --nm-level: 0.08;
  position: relative;
  display: grid; place-items: center;
  transform: scale(calc(0.92 + var(--nm-level) * 0.16));
  transition: transform 90ms linear;
}
.nova-voice-mark .nova-mark { position: relative; z-index: 1; }

.nova-voice-mark .nvm-bloom {
  position: absolute; inset: -12%;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(var(--ap-mid-rgb), calc(0.10 + var(--nm-level) * 0.26)) 0%,
    rgba(var(--ap-glow-rgb), calc(0.05 + var(--nm-level) * 0.12)) 42%,
    transparent 72%
  );
  filter: blur(calc(18px + var(--nm-level) * 22px));
  pointer-events: none;
}

/* Durum paleti — eski orb'un STATE_COL eşlemesiyle aynı mantık. */
.nova-voice-mark[data-voice="listening"] { --ap-glow: var(--accent-2); --ap-mid-rgb: var(--accent-2-rgb); }
.nova-voice-mark[data-voice="thinking"]  { --ap-glow: var(--accent-3); --ap-mid-rgb: var(--accent-3-rgb); }
.nova-voice-mark[data-voice="speaking"]  { --ap-glow: var(--ember); --ap-glow-rgb: 255,138,91; }

@media (max-width: 720px) {
  .nova-voice-mark { transform: scale(calc(0.86 + var(--nm-level) * 0.14)); }
}

/* --------------------------- azaltılmış hareket -------------------------- */

@media (prefers-reduced-motion: reduce) {
  .nova-mark,
  .nova-mark * {
    animation: none !important;
    transition: none !important;
  }
  .nova-voice-mark { transform: none; transition: none; }
}
`;
}
