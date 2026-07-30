// Ses ekranının merkezindeki büyük marka işareti.
//
// Eski canvas orb'unun yerini alır (yedeği: hooks/legacy/useOrbCanvas.js).
// Aynı Pulse Aperture geometrisini kullanır, ama iki ek davranışı vardır:
//
//   1. Gerçek ses seviyesine tepki verir — mikrofon/TTS genliği `extLevelRef`
//      üzerinden gelir; yoksa duruma göre sentetik bir nefes üretilir.
//   2. Duruma göre renk kayar — dinlerken ikincil, düşünürken üçüncül,
//      konuşurken ember. Eski orb'un durum paletiyle aynı mantık.
//
// Seviye React state'i DEĞİL: her karede CSS değişkeni yazılır, böylece
// saniyede 60 kez yeniden render olmaz.
import React, { useEffect, useRef } from "react";
import { NovaMark } from "./NovaMark.jsx";

/**
 * @param size          px kenar uzunluğu
 * @param voiceStateRef "idle" | "listening" | "thinking" | "speaking"
 * @param extLevelRef   >= 0 ise gerçek ses seviyesi, değilse sentetik
 * @param reducedRef    prefers-reduced-motion
 * @param waveRef       dalga çubukları kabı (aynı döngüde güncellenir)
 */
export function NovaVoiceMark({ size = 340, voiceStateRef, extLevelRef, reducedRef, waveRef }) {
  const hostRef = useRef(null);
  const levelRef = useRef(0.08);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return undefined;

    let raf;
    const start = performance.now();

    const targetLevel = (t) => {
      if (extLevelRef && extLevelRef.current >= 0) return Math.min(1, extLevelRef.current);
      if (reducedRef && reducedRef.current) return 0.05;
      const s = voiceStateRef.current;
      const n = (Math.sin(t * 0.013) * 0.5 + 0.5) * (Math.sin(t * 0.027) * 0.5 + 0.5);
      if (s === "listening") return 0.30 + n * 0.45;
      if (s === "thinking") return 0.18 + (Math.sin(t * 0.006) * 0.5 + 0.5) * 0.20;
      if (s === "speaking") return 0.34 + n * 0.50;
      return 0.06 + (Math.sin(t * 0.0018) * 0.5 + 0.5) * 0.08;
    };

    let lastState = "";
    const frame = (now) => {
      const reduced = reducedRef && reducedRef.current;
      const t = (now - start) * (reduced ? 0.12 : 1);

      // yumuşatma: ani sıçrama yerine takip
      levelRef.current += (targetLevel(t) - levelRef.current) * 0.12;
      const level = levelRef.current;
      host.style.setProperty("--nm-level", level.toFixed(3));

      const state = voiceStateRef.current;
      if (state !== lastState) {
        host.dataset.voice = state;
        lastState = state;
      }

      // dalga çubukları — eski orb ile aynı formül, DOM üzerinden
      const wc = waveRef && waveRef.current;
      if (wc) {
        const idle = state === "idle";
        const bars = wc.children;
        for (let i = 0; i < bars.length; i++) {
          const j = Math.sin(now * 0.012 + i * 0.6) * 0.5 + 0.5;
          bars[i].style.height = Math.max(4, (5 + level * 36) * (0.4 + j * 0.9)) + "px";
          bars[i].style.opacity = idle ? 0.35 : 0.9;
        }
      }

      raf = requestAnimationFrame(frame);
    };

    raf = requestAnimationFrame(frame);
    return () => cancelAnimationFrame(raf);
  }, [voiceStateRef, extLevelRef, reducedRef, waveRef]);

  return (
    <div className="nova-voice-mark" ref={hostRef} data-voice="idle" style={{ width: size, height: size }}>
      <span className="nvm-bloom" />
      <NovaMark size={size} halo animated title="NOVA" />
    </div>
  );
}
