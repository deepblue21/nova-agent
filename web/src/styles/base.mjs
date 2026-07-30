// Temel katman: tipografi, zemin (aurora + grid + grain), ortak animasyonlar,
// erişilebilirlik. Renk/ölçü değerleri token değişkenlerinden gelir.

/**
 * Yazı tipleri. CSS kuralı gereği `@import` stil sayfasının EN BAŞINDA olmalı;
 * bu yüzden ayrı dışa aktarılır ve styles/index.mjs içinde ilk sıraya konur.
 * Token bloğundan sonra kalırsa tarayıcı satırı sessizce yok sayar ve
 * uygulama sistem yazı tipine düşer.
 */
export const FONTS_CSS =
  "@import url('https://fonts.googleapis.com/css2?family=Syne:wght@600;700;800" +
  "&family=Sora:wght@200;300;400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');";

export const BASE_CSS = `
*, *::before, *::after { box-sizing: border-box; }
body, h1, h2, h3, h4, p, figure, blockquote, dl, dd { margin: 0; }

.nova-root {
  position: relative;
  width: 100%;
  height: 100dvh;
  min-height: 640px;
  background: var(--bg);
  color: var(--text);
  font-family: 'Sora', system-ui, -apple-system, sans-serif;
  font-size: 14px;
  overflow: hidden;
  -webkit-font-smoothing: antialiased;
  display: flex;
  flex-direction: column;
  isolation: isolate;
}

.nova-root :focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: var(--radius-xs);
}

/* ---------------------------- zemin katmanları --------------------------- */

.bg-aurora { position: absolute; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; }
.bg-aurora .blob {
  position: absolute; border-radius: 50%;
  filter: blur(var(--aurora-blur)); opacity: var(--aurora-opacity); mix-blend-mode: var(--aurora-blend);
  animation: drift var(--dur-aurora-drift) ease-in-out infinite;
}
.bg-aurora .b1 { width: 520px; height: 520px; left: -8%; top: -12%;
  background: radial-gradient(circle, rgba(var(--accent-2-rgb), 0.55), transparent 70%); }
.bg-aurora .b2 { width: 600px; height: 600px; right: -10%; top: 8%; animation-duration: 34s; animation-delay: -7s;
  background: radial-gradient(circle, rgba(var(--accent-rgb), 0.45), transparent 70%); }
.bg-aurora .b3 { width: 480px; height: 480px; left: 35%; bottom: -18%; animation-duration: 31s; animation-delay: -14s;
  background: radial-gradient(circle, rgba(var(--accent-3-rgb), 0.40), transparent 70%); }

@keyframes drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(40px, 30px) scale(1.08); }
  66% { transform: translate(-30px, 20px) scale(0.95); }
}

.bg-grid {
  position: absolute; inset: 0; z-index: 1; pointer-events: none; opacity: 0.4;
  background-image:
    linear-gradient(var(--grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-line) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, black 30%, transparent 90%);
  -webkit-mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, black 30%, transparent 90%);
}

.bg-grain {
  position: absolute; inset: 0; z-index: 2; pointer-events: none; opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

/* ------------------------- paylaşılan animasyonlar ----------------------- */
/* Süreler design/nova-tokens.json'dan; Compose tarafı NovaDuration kullanır. */

@keyframes fadeUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes scaleIn { from { opacity: 0; transform: translateY(14px) scale(.98); } to { opacity: 1; transform: none; } }
@keyframes slideFromLeft { from { transform: translateX(-100%); } to { transform: none; } }
@keyframes slideFromRight { from { transform: translateX(100%); } to { transform: none; } }
@keyframes railIn { from { opacity: 0; transform: translateX(-12px); } to { opacity: 1; transform: none; } }
@keyframes pulseRing { 0% { transform: scale(1); opacity: .8; } 100% { transform: scale(1.5); opacity: 0; } }
@keyframes softPulse { 0%, 100% { opacity: 1; } 50% { opacity: .35; } }
@keyframes markPulse {
  0%, 100% { box-shadow: 0 0 30px rgba(var(--accent-rgb), 0.25); }
  50% { box-shadow: 0 0 48px rgba(var(--accent-rgb), 0.5); }
}
@keyframes floatY { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-7px); } }
@keyframes hueFlow { 0% { background-position: 0% 50%; } 100% { background-position: 200% 50%; } }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer {
  0% { background-position: 120% 0; opacity: .55; }
  50% { opacity: 1; }
  100% { background-position: -120% 0; opacity: .55; }
}
@keyframes typeBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

.anim-in { animation: fadeUp var(--dur-base) var(--ease-standard); }
.view-enter { animation: fadeUp var(--dur-slow) var(--ease-emphasized); }

/* ------------------------------ tipografi -------------------------------- */

.t-display { font-family: 'Syne', sans-serif; font-weight: 800; letter-spacing: .3px; }
.t-mono { font-family: 'JetBrains Mono', ui-monospace, monospace; }
.section-label {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 11px; letter-spacing: 1.2px; text-transform: uppercase;
  color: var(--muted2); margin-bottom: var(--space-sm);
}

/* ----------------------------- kaydırma çubuğu --------------------------- */

.nova-root ::-webkit-scrollbar { width: 8px; height: 8px; }
.nova-root ::-webkit-scrollbar-thumb { background: var(--surface2); border-radius: var(--radius-sm); }
.nova-root ::-webkit-scrollbar-thumb:hover { background: var(--line); }
.nova-root ::-webkit-scrollbar-track { background: transparent; }

/* Üçüncü parti eklenti rozetlerini (Grammarly vb.) kompozerden uzak tut. */
grammarly-extension, grammarly-popups, grammarly-card, div[data-grammarly-part] { display: none !important; }

@media (prefers-reduced-motion: reduce) {
  .nova-root *, .nova-root *::before, .nova-root *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
  .bg-aurora .blob { animation: none !important; }
}
`;
