// YEDEK — eski ses orb'u (canvas). Ses ekranının merkezindeki büyük görsel
// Pulse Aperture marka işaretiyle değiştirildi; bu dosya geri dönmek istenirse
// diye olduğu gibi saklanıyor. Uygulamada HİÇBİR YERDEN çağrılmıyor.
//
// Geri almak için: views/VoiceView.jsx içinde <NovaVoiceMark .../> yerine
// bu hook'u kullan (canvas + useOrb ikilisi).

// Ses orbu — Compose `Orb.kt` ile aynı geometri ve aynı hareket sabitleri
// (design/nova-tokens.json → NovaOrb). Renkler aktif aksan temasından okunur,
// böylece tema değişince orb da değişir.
import { useEffect, useRef } from "react";
import { MOTION } from "../design/tokens.generated.mjs";

const ORB = MOTION.orb;

const hexToRgb = (hex) => {
  const h = String(hex || "").replace("#", "");
  if (h.length < 6) return [34, 211, 238];
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
};

/**
 * @param canvasRef    hedef canvas
 * @param voiceStateRef "idle" | "listening" | "thinking" | "speaking"
 * @param waveRef      dalga çubukları kabı (DOM üzerinden güncellenir, re-render yok)
 * @param reducedRef   prefers-reduced-motion
 * @param extLevelRef  >= 0 ise gerçek mikrofon/TTS seviyesi
 * @param accentRef    { primary, secondary, tertiary } aktif aksan
 */
export function useOrb(canvasRef, voiceStateRef, waveRef, reducedRef, extLevelRef, accentRef) {
  const levelRef = useRef(0.08);
  const colRef = useRef([34, 211, 238]);

  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    const ctx = cv.getContext("2d");
    let raf;
    const start = performance.now();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    function resize() {
      const r = cv.getBoundingClientRect();
      cv.width = r.width * dpr;
      cv.height = r.height * dpr;
    }
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(cv);

    const accent = () => (accentRef && accentRef.current) || {};
    const palette = () => {
      const a = accent();
      return [
        hexToRgb(a.primary || "#22D3EE"),
        hexToRgb(a.secondary || "#6366F1"),
        hexToRgb(a.tertiary || "#A855F7"),
        [255, 138, 91],                       // ember — token ile ortak
      ];
    };
    const stateColor = (s) => {
      const a = accent();
      if (s === "listening") return hexToRgb(a.secondary || "#6366F1");
      if (s === "thinking") return hexToRgb(a.tertiary || "#A855F7");
      if (s === "speaking") return [255, 138, 91];
      return hexToRgb(a.primary || "#22D3EE");
    };

    function targetLevel(t) {
      // gerçek ses (mikrofon/TTS) varsa onu kullan
      if (extLevelRef && extLevelRef.current >= 0) return Math.min(1, extLevelRef.current);
      const s = voiceStateRef.current;
      if (reducedRef && reducedRef.current) return 0.05;
      const n = (Math.sin(t * 0.013) * 0.5 + 0.5) * (Math.sin(t * 0.027) * 0.5 + 0.5);
      if (s === "listening") return 0.30 + n * 0.45;
      if (s === "thinking") return 0.18 + (Math.sin(t * 0.006) * 0.5 + 0.5) * 0.20;
      if (s === "speaking") return 0.34 + n * 0.50;
      return 0.06 + (Math.sin(t * 0.0018) * 0.5 + 0.5) * 0.08;
    }

    function frame(now) {
      const reduced = reducedRef && reducedRef.current;
      const sp = reduced ? 0.12 : 1;
      const t = (now - start) * sp;
      const W = cv.width;
      const H = cv.height;

      levelRef.current += (targetLevel(t) - levelRef.current) * 0.12;
      const level = levelRef.current;
      const cx = W / 2;
      const cy = H / 2;

      const tcol = stateColor(voiceStateRef.current);
      const col = colRef.current;
      for (let k = 0; k < 3; k++) col[k] += (tcol[k] - col[k]) * 0.04;
      const C0 = Math.round(col[0]);
      const C1 = Math.round(col[1]);
      const C2 = Math.round(col[2]);

      const base = Math.min(W, H) * 0.25;
      const R = base * (1 + level * ORB.levelGain);
      const pal = palette();

      ctx.clearRect(0, 0, W, H);

      // bloom
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, R * 2.6);
      g.addColorStop(0, `rgba(${C0},${C1},${C2},${0.16 + level * 0.28})`);
      g.addColorStop(0.5, `rgba(${pal[1][0]},${pal[1][1]},${pal[1][2]},0.05)`);
      g.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, W, H);

      // canlı bloblar (additive)
      ctx.globalCompositeOperation = "lighter";
      for (let i = 0; i < ORB.blobCount; i++) {
        const a = t * 0.0006 * (1 + i * 0.15) + i * ((Math.PI * 2) / ORB.blobCount);
        const rad = R * (0.42 + 0.20 * Math.sin(t * 0.001 * (1 + i) + i));
        const dist = R * 0.30 * (0.6 + 0.4 * Math.sin(t * 0.0008 * (i + 1)));
        const x = cx + Math.cos(a) * dist * (1 + level * 0.5);
        const y = cy + Math.sin(a * 1.1) * dist * (1 + level * 0.5);
        const c = pal[i % pal.length];
        const bg = ctx.createRadialGradient(x, y, 0, x, y, rad);
        bg.addColorStop(0, `rgba(${c[0]},${c[1]},${c[2]},${0.5 + level * 0.3})`);
        bg.addColorStop(1, `rgba(${c[0]},${c[1]},${c[2]},0)`);
        ctx.fillStyle = bg;
        ctx.beginPath();
        ctx.arc(x, y, rad, 0, Math.PI * 2);
        ctx.fill();
      }

      // parlak çekirdek
      const coreR = R * ORB.coreRadiusRatio;
      const core = ctx.createRadialGradient(cx, cy, 0, cx, cy, coreR);
      core.addColorStop(0, `rgba(238,255,255,${0.5 + level * 0.4})`);
      core.addColorStop(0.4, "rgba(170,240,255,0.22)");
      core.addColorStop(1, "rgba(170,240,255,0)");
      ctx.fillStyle = core;
      ctx.beginPath();
      ctx.arc(cx, cy, coreR, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalCompositeOperation = "source-over";

      // parçacık halkası
      for (let i = 0; i < ORB.particleCount; i++) {
        const a = (i / ORB.particleCount) * Math.PI * 2 + t * 0.0003;
        const w = Math.sin(t * 0.0022 + i * 0.5) * 0.5 + 0.5;
        const rr = R * 1.30 + w * 8 * (1 + level * 2);
        const x = cx + Math.cos(a) * rr;
        const y = cy + Math.sin(a) * rr;
        const s = (0.7 + w * 1.6 * (1 + level)) * dpr;
        ctx.fillStyle = `rgba(150,230,255,${0.12 + w * 0.5})`;
        ctx.beginPath();
        ctx.arc(x, y, s, 0, Math.PI * 2);
        ctx.fill();
      }

      // ince halka + dönen yay
      const ringR = R * ORB.ringRadiusRatio;
      ctx.strokeStyle = `rgba(${C0},${C1},${C2},${0.10 + level * 0.20})`;
      ctx.lineWidth = 1 * dpr;
      ctx.beginPath();
      ctx.arc(cx, cy, ringR, 0, Math.PI * 2);
      ctx.stroke();

      const arcA = t * 0.0012;
      ctx.strokeStyle = `rgba(${C0},${C1},${C2},${0.5 + level * 0.35})`;
      ctx.lineWidth = 2 * dpr;
      ctx.beginPath();
      ctx.arc(cx, cy, ringR, arcA, arcA + 0.9);
      ctx.stroke();

      // dalga çubuklarını DOM üzerinden güncelle (re-render YOK)
      const wc = waveRef && waveRef.current;
      if (wc) {
        const idle = voiceStateRef.current === "idle";
        const bars = wc.children;
        for (let i = 0; i < bars.length; i++) {
          const j = Math.sin(now * 0.012 + i * 0.6) * 0.5 + 0.5;
          const h = Math.max(4, (5 + level * 36) * (0.4 + j * 0.9));
          bars[i].style.height = h + "px";
          bars[i].style.opacity = idle ? 0.35 : 0.9;
        }
      }

      raf = requestAnimationFrame(frame);
    }

    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, [canvasRef, voiceStateRef, waveRef, reducedRef, extLevelRef, accentRef]);

  return levelRef;
}
