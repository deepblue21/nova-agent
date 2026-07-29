// NOVA Pulse Aperture — uygulamadaki TEK marka işareti.
//
// Geometri, yollar ve zamanlama design/nova-tokens.json'dan gelir; Compose
// tarafındaki `NovaThinkingIndicator` aynı sayıları kullanır, bu yüzden ikon
// iki platformda birebir aynı nefes alır. Renkler sabit değildir: aktif
// aksanın `--ap-*` değişkenlerinden okunur, tema değişince ikon da değişir.
//
// Hareket katmanları (Compose ile aynı):
//   gövde nefesi   2750 ms  ters yönlü  → boyut 18→23 oranı
//   çekirdek nabzı 1375 ms  ters yönlü  → 0.82→1.18
//   yörünge        4200 ms  doğrusal    → 14 nokta + süpürme yayı
//   ışık süpürmesi 5500 ms  doğrusal    → gövde içinde kayan parlaklık
//   mikro eğim     2750 ms  ters yönlü  → ±0.7°
import React, { useId } from "react";
import { APERTURE } from "../design/tokens.generated.mjs";

const A = APERTURE;

/** Yörünge noktaları — Compose'daki `index % 3` / `index % 5` kuralıyla aynı. */
const HALO_DOTS = Array.from({ length: A.haloDotCount }, (_, i) => ({
  angle: (i * 360) / A.haloDotCount,
  cyan: i % 3 === 0,
  emphasized: i % 5 === 0,
}));

/**
 * Hareket kipleri:
 *   brand    — sakin nefes; üst çubuk, ray, avatar, karşılama (varsayılan)
 *   thinking — çekirdek nabzı + yörünge halosu; yanıt beklerken
 *   preview  — hareket ebeveyne bağlı: tema kartı hover/seçili olunca canlanır,
 *              yoksa durur. On bir kartın aynı anda animasyon çalıştırmasını önler.
 *
 * @param size      px kenar uzunluğu (varsayılan 44 — Compose ile aynı)
 * @param halo      yörünge noktaları + süpürme yayı; verilmezse `thinking`'de açık
 * @param animated  false ise Compose'un durağan karesiyle aynı görünüm
 * @param motion    "brand" | "thinking" | "preview"
 * @param title     erişilebilirlik etiketi; yoksa ikon dekoratif sayılır
 */
export function NovaMark({
  size = 44, halo, animated = true, motion = "brand", title, className = "", style,
}) {
  // Halo varsayılanı kipe bağlıdır; açıkça verilen değer her zaman kazanır.
  const showHalo = halo == null ? motion === "thinking" : halo;
  const uid = useId().replace(/:/g, "");
  const id = (n) => `nm-${uid}-${n}`;

  const vb = A.viewport;
  const half = vb / 2;

  // Gövde, bileşen kutusunun içinde nefes alır: 18/44 → 23/44 oranı.
  const restRatio = A.markRestSize / A.componentSize;
  const peakRatio = A.markPeakSize / A.componentSize;
  const haloR = (vb * (A.haloDiameter / A.componentSize)) / 2;

  // Yol koordinatları 560'lık kutuda ve merkezi (280,282); ölçeklerken oraya taşı.
  const markScaleRest = (vb * restRatio) / 314;
  const markScalePeak = (vb * peakRatio) / 314;

  return (
    <svg
      className={
        "nova-mark"
        + (animated ? " is-animated" : "")
        + " is-" + motion
        + (className ? " " + className : "")
      }
      style={style}
      width={size}
      height={size}
      viewBox={`0 0 ${vb} ${vb}`}
      role={title ? "img" : "presentation"}
      aria-label={title || undefined}
      aria-hidden={title ? undefined : "true"}
      focusable="false"
    >
      {title && <title>{title}</title>}

      <defs>
        <linearGradient
          id={id("body")}
          gradientUnits="userSpaceOnUse"
          x1={A.bodyGradient.startX} y1={A.bodyGradient.startY}
          x2={A.bodyGradient.endX} y2={A.bodyGradient.endY}
        >
          <stop offset="0" stopColor="var(--ap-light)" />
          <stop offset="0.44" stopColor="var(--ap-mid)" />
          <stop offset="1" stopColor="var(--ap-deep)" />
        </linearGradient>

        <linearGradient
          id={id("fold")}
          gradientUnits="userSpaceOnUse"
          x1={A.foldGradient.startX} y1={A.foldGradient.startY}
          x2={A.foldGradient.endX} y2={A.foldGradient.endY}
        >
          <stop offset="0" stopColor="var(--ap-light)" stopOpacity="0.92" />
          <stop offset="0.55" stopColor="var(--ap-mid)" stopOpacity="0.68" />
          <stop offset="1" stopColor="var(--ap-deep)" stopOpacity="0.16" />
        </linearGradient>

        <radialGradient id={id("aura")} cx="50%" cy="50%" r="50%">
          <stop offset="0" stopColor="var(--ap-mid)" stopOpacity="0.38" />
          <stop offset="0.55" stopColor="var(--ap-deep)" stopOpacity="0.13" />
          <stop offset="1" stopColor="var(--ap-deep)" stopOpacity="0" />
        </radialGradient>

        <radialGradient id={id("coreGlow")} cx="50%" cy="50%" r="50%">
          <stop offset="0" stopColor="var(--ap-core-hi)" stopOpacity="0.95" />
          <stop offset="0.42" stopColor="var(--ap-core)" stopOpacity="0.72" />
          <stop offset="1" stopColor="var(--ap-light)" stopOpacity="0" />
        </radialGradient>

        <linearGradient id={id("sweep")} gradientUnits="objectBoundingBox" x1="0" y1="0" x2="1" y2="0.6">
          <stop offset="0" stopColor="#FFFFFF" stopOpacity="0" />
          <stop offset="0.35" stopColor="var(--ap-glow)" stopOpacity="0.14" />
          <stop offset="0.55" stopColor="var(--ap-core-hi)" stopOpacity="0.52" />
          <stop offset="1" stopColor="#FFFFFF" stopOpacity="0" />
        </linearGradient>

        <linearGradient id={id("arc")} gradientUnits="userSpaceOnUse" x1={half - haloR} y1={half} x2={half + haloR} y2={half}>
          <stop offset="0" stopColor="var(--ap-glow)" stopOpacity="0" />
          <stop offset="0.5" stopColor="var(--ap-glow)" stopOpacity="0.88" />
          <stop offset="1" stopColor="var(--ap-light)" stopOpacity="0" />
        </linearGradient>

        <clipPath id={id("clip")}>
          <path d={A.bodyPath} />
        </clipPath>
      </defs>

      {/* --- yörünge halkası: nokta dizisi + süpürme yayı --- */}
      {showHalo && (
        <g className="nm-halo">
          <circle cx={half} cy={half} r={haloR} fill="none" stroke="#8792C8" strokeOpacity="0.13" strokeWidth={vb * 0.0016} />
          <g className="nm-orbit" style={{ transformOrigin: `${half}px ${half}px` }}>
            {HALO_DOTS.map((d, i) => {
              const rad = (d.angle * Math.PI) / 180;
              return (
                <circle
                  key={i}
                  cx={half + Math.cos(rad) * haloR}
                  cy={half + Math.sin(rad) * haloR}
                  r={(d.emphasized ? 1.15 : 0.72) * (vb / A.componentSize)}
                  fill={d.cyan ? "var(--ap-glow)" : "var(--ap-halo-dot)"}
                  fillOpacity={d.emphasized ? (d.cyan ? 0.94 : 0.82) : (d.cyan ? 0.58 : 0.46)}
                />
              );
            })}
            <path
              d={describeArc(half, half, haloR, -38, 38)}
              fill="none"
              stroke={`url(#${id("arc")})`}
              strokeWidth={1.15 * (vb / A.componentSize)}
              strokeLinecap="round"
            />
          </g>
        </g>
      )}

      {/* --- nefes alan gövde --- */}
      <g
        className="nm-breath"
        style={{
          transformOrigin: `${half}px ${half}px`,
          "--nm-scale-rest": markScaleRest,
          "--nm-scale-peak": markScalePeak,
        }}
      >
        <circle cx={half} cy={half} r={vb * peakRatio} fill={`url(#${id("aura")})`} className="nm-aura" />

        <g transform={`translate(${half} ${half})`}>
          <g className="nm-tilt">
            <g transform={`translate(${-A.originX} ${-A.originY})`}>
              <path d={A.bodyPath} fill={`url(#${id("body")})`} />

              <g clipPath={`url(#${id("clip")})`}>
                <circle cx="247" cy="242" r="74" fill="var(--ap-glow)" fillOpacity="0.48" />
                <circle cx="327" cy="316" r="92" fill="var(--ap-deep)" fillOpacity="0.62" />
                <path d={A.foldPath} fill={`url(#${id("fold")})`} />
                <rect className="nm-sweep" x="-80" y="100" width="160" height="370" fill={`url(#${id("sweep")})`} />
              </g>

              <path
                d={A.bodyPath}
                fill="none"
                stroke="var(--ap-light)"
                strokeOpacity="var(--ap-stroke-alpha)"
                strokeWidth="1.5"
              />

              <g className="nm-core" style={{ transformOrigin: `${A.coreX}px ${A.coreY}px` }}>
                <circle cx={A.coreX} cy={A.coreY} r={A.coreGlowRadius} fill={`url(#${id("coreGlow")})`} />
                <circle cx={A.coreX} cy={A.coreY} r={A.coreRadius} fill="var(--ap-core)" />
                <circle cx={A.highlightX} cy={A.highlightY} r={A.highlightRadius} fill="var(--ap-core-hi)" fillOpacity="0.9" />
              </g>
            </g>
          </g>
        </g>
      </g>
    </svg>
  );
}

/** Yay yolu (SVG'de sweepGradient yok; yayı elle çiziyoruz). */
function describeArc(cx, cy, r, startDeg, endDeg) {
  const p = (deg) => {
    const rad = (deg * Math.PI) / 180;
    return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
  };
  const [x0, y0] = p(startDeg);
  const [x1, y1] = p(endDeg);
  const large = Math.abs(endDeg - startDeg) > 180 ? 1 : 0;
  return `M ${x0} ${y0} A ${r} ${r} 0 ${large} 1 ${x1} ${y1}`;
}

/**
 * Yanıt beklerken gösterilen hâli — Compose `NovaThinkingIndicator` ile aynı:
 * gövde döner değil, nefes alır; halo çevresinde döner.
 */
export function NovaThinkingMark({ size = 44 }) {
  return <NovaMark size={size} motion="thinking" animated title="Yanıt hazırlanıyor" />;
}

/**
 * Marka kutusu — üst çubuk / sol ray / karşılama için yumuşak zeminli hâl.
 * Eski gradyan kare + Sparkles ikilisinin yerini alır.
 */
export function NovaBadge({ size = 38, radius, animated = true, halo = false, title }) {
  return (
    <span
      className="nova-badge"
      style={{ width: size, height: size, borderRadius: radius != null ? radius : Math.round(size * 0.32) }}
    >
      <NovaMark size={Math.round(size * 0.92)} animated={animated} halo={halo} title={title} />
    </span>
  );
}
