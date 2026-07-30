#!/usr/bin/env node
// design/nova-tokens.json → web (JS/CSS) + Android (Kotlin) üretimi.
// Kullanım: npm run tokens
//
// Üretilen dosyalar depoya işlenir; böylece build bu betiğe bağımlı olmaz.
// Renk/tema/animasyon değişikliği YALNIZ design/nova-tokens.json içinde yapılır.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const tokens = JSON.parse(readFileSync(resolve(root, "design/nova-tokens.json"), "utf8"));

const BANNER_JS = `// OTOMATİK ÜRETİLDİ — elle düzenleme.
// Kaynak: design/nova-tokens.json · Yeniden üret: npm run tokens
`;
const BANNER_KT = `// OTOMATİK ÜRETİLDİ — elle düzenleme.
// Kaynak: design/nova-tokens.json · Yeniden üret: npm run tokens
`;

/* ------------------------------ yardımcılar ------------------------------ */

const hexToRgb = (hex) => {
  const h = hex.replace("#", "");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
};

const cssColor = (entry) => {
  if (entry.alpha == null) return entry.hex;
  const [r, g, b] = hexToRgb(entry.hex);
  return `rgba(${r},${g},${b},${entry.alpha})`;
};

/** #22D3EE → 0xFF22D3EE · alpha varsa ARGB'ye katılır. */
const ktColor = (hexOrEntry) => {
  const entry = typeof hexOrEntry === "string" ? { hex: hexOrEntry } : hexOrEntry;
  const a = entry.alpha == null ? 255 : Math.round(entry.alpha * 255);
  return `Color(0x${a.toString(16).toUpperCase().padStart(2, "0")}${entry.hex.replace("#", "").toUpperCase()})`;
};

const pascal = (s) => s.charAt(0).toUpperCase() + s.slice(1);

const THEME_ALIASES = {
  aurora: "arctic",
  nova: "arctic",
  plum: "lavender",
  violet: "amethyst",
  aperture: "amethyst",
  kizil: "ruby",
  okyanus: "sapphire",
  zumrut: "emerald",
  gul: "coral",
  gunbatimi: "copper",
  amber: "amber",
};

const write = (relPath, body) => {
  const full = resolve(root, relPath);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, body, "utf8");
  console.log("  ✓ " + relPath);
};

/* --------------------------------- WEB ---------------------------------- */

function buildWeb() {
  const c = tokens.color;
  const baseVars = Object.entries(c)
    .map(([k, v]) => `  --${v.css || k.replace(/([A-Z])/g, "-$1").toLowerCase()}: ${cssColor(v)};`)
    .join("\n");

  const radiusVars = Object.entries(tokens.radius)
    .map(([k, v]) => `  --radius-${k}: ${k === "pill" ? v + "px" : v + "px"};`)
    .join("\n");

  const spaceVars = Object.entries(tokens.space)
    .map(([k, v]) => `  --space-${k}: ${v}px;`)
    .join("\n");

  const motionVars = [
    ...Object.entries(tokens.motion.duration).map(
      ([k, v]) => `  --dur-${k.replace(/([A-Z])/g, "-$1").toLowerCase()}: ${v}ms;`,
    ),
    ...Object.entries(tokens.motion.easing).map(([k, v]) => `  --ease-${k}: ${v};`),
  ].join("\n");

  // Aksanı olmayan tema için ametist markayı yedek al: ikon her temada
  // görünür kalsın, sabit renge düşmesin.
  const fallbackAperture = (tokens.accents.find((a) => a.aperture) || {}).aperture || {};

  const accentBlocks = tokens.accents
    .map((a) => {
      const [pr, pg, pb] = hexToRgb(a.primary);
      const [sr, sg, sb] = hexToRgb(a.secondary);
      const [tr, tg, tb] = hexToRgb(a.tertiary);
      const ap = { ...fallbackAperture, ...(a.aperture || {}) };
      const [gr, gg, gb] = hexToRgb(ap.glow);
      const [mr, mg, mb] = hexToRgb(ap.mid);
      const surfaceVars = a.surface
        ? `
  --bg: ${a.surface.bg};
  --bg2: ${a.surface.bg2};
  --bg3: ${a.surface.bg3};
  --panel: color-mix(in srgb, ${a.surface.tint} 78%, transparent);
  --line: rgba(${pr},${pg},${pb},0.16);
  --blur-tint: color-mix(in srgb, ${a.surface.tint} 74%, transparent);
  --scrim: rgba(0,0,0,0.78);`
        : "";
      return `[data-accent="${a.id}"]{
  --accent: ${a.primary};
  --accent-rgb: ${pr},${pg},${pb};
  --accent-2: ${a.secondary};
  --accent-2-rgb: ${sr},${sg},${sb};
  --accent-3: ${a.tertiary};
  --accent-3-rgb: ${tr},${tg},${tb};
  --on-accent: ${a.onPrimary};
  --ap-light: ${ap.light};
  --ap-mid: ${ap.mid};
  --ap-mid-rgb: ${mr},${mg},${mb};
  --ap-deep: ${ap.deep};
  --ap-glow: ${ap.glow};
  --ap-glow-rgb: ${gr},${gg},${gb};
  --ap-core: ${ap.core};${surfaceVars}
}`;
    })
    .join("\n");

  /* --------------------------- yüzey şeması ------------------------------ */
  // `dark` ana `color` bloğudur ve varsayılan olarak :root'ta zaten yazılıdır.
  // `light` yalnız farkları [data-scheme="light"] altında yeniden tanımlar;
  // aksan blokları şemadan bağımsızdır, yani 11 tema iki şemada da çalışır.
  const schemeBlocks = (() => {
    const light = tokens.schemes && tokens.schemes.light;
    if (!light) return "";
    const vars = Object.entries(light.color)
      .map(([k, v]) => {
        const name = (tokens.color[k] && tokens.color[k].css) || k.replace(/([A-Z])/g, "-$1").toLowerCase();
        return `  --${name}: ${typeof v === "string" ? v : cssColor(v)};`;
      })
      .join("\n");
    const ap = light.aperture || {};
    // Marka işareti: açık zeminde beyaz çekirdek erir. Parlaklık aksanın
    // gövde rengine, kontur koyuya çekilir; halo noktaları nötr griye döner.
    const apVars = [
      `  --ap-core-hi: var(--ap-${ap.coreHighlight || "mid"});`,
      `  --ap-stroke-alpha: ${ap.strokeAlpha != null ? ap.strokeAlpha : 0.58};`,
      `  --ap-halo-dot: ${ap.haloDot || "#6B7488"};`,
    ].join("\n");
    const aurora = light.aurora || {};
    return `
[data-scheme="light"] {
${vars}
${apVars}
  --aurora-blend: ${aurora.blend || "multiply"};
  --aurora-opacity: ${aurora.opacity != null ? aurora.opacity : 0.2};
  --aurora-blur: ${aurora.blur || 96}px;
  --glass-1: color-mix(in srgb, #FFFFFF 88%, transparent);
  --glass-2: color-mix(in srgb, #FFFFFF 74%, transparent);
  --glass-shadow: rgba(23,26,36,0.12);
  --code-bg: #FBF9F4;
  --code-fg: #1C2230;
  --grid-line: rgba(19,23,34,0.045);
  color-scheme: light;
}`;
  })();

  const css = `:root, .nova-root {
${baseVars}
${radiusVars}
${spaceVars}
${motionVars}
  --line-bright: rgba(var(--accent-rgb), 0.34);
  --glow: 0 0 48px rgba(var(--accent-2-rgb), 0.30);
  --glass-blur: 16px;

  /* Koyu şema varsayılanları — [data-scheme="light"] bunları ezer. */
  --aurora-blend: screen;
  --aurora-opacity: 0.5;
  --aurora-blur: 92px;
  --glass-1: rgba(16,20,38,0.80);
  --glass-2: rgba(10,14,26,0.88);
  --glass-shadow: rgba(0,0,0,0.38);
  --code-bg: #070A11;
  --code-fg: #CFE8FF;
  --grid-line: rgba(255,255,255,0.025);

  /* Marka işareti — açık şemada ezilir (bkz. schemes.light.aperture). */
  --ap-core-hi: #FFFFFF;
  --ap-stroke-alpha: ${tokens.brand.aperture.strokeAlpha};
  --ap-halo-dot: #E1E5FF;
}
${accentBlocks}
${schemeBlocks}`;

  const webAccents = tokens.accents.map((a) => ({
    id: a.id,
    name: a.name,
    primary: a.primary,
    secondary: a.secondary,
    tertiary: a.tertiary,
    onPrimary: a.onPrimary,
    aperture: { ...fallbackAperture, ...(a.aperture || {}) },
    surface: a.surface || null,
    sourceAsset: a.sourceAsset,
  }));

  const js = `${BANNER_JS}
/** design/nova-tokens.json'dan üretilen CSS değişkenleri (base + aksan temaları). */
export const TOKENS_CSS = ${JSON.stringify(css)};

/** Ortak aksan temaları — Android \`NOVA_ACCENTS\` ile birebir aynı sıradadır. */
export const ACCENTS = ${JSON.stringify(webAccents, null, 2)};

export const DEFAULT_ACCENT = ${JSON.stringify(
    (tokens.accents.find((a) => a.webDefault) || tokens.accents[0]).id,
  )};

/** Eski kaydedilmiş tema kimliklerini güncel ortak kataloğa taşır. */
export const THEME_ALIASES = ${JSON.stringify(THEME_ALIASES, null, 2)};

/** Ortak gezinme hedefleri — Android NavigationBar ile aynı sıra ve etiketler. */
export const DESTINATIONS = ${JSON.stringify(tokens.navigation.destinations, null, 2)};

/** Semantik ikon sözlüğü (lucide adları). */
export const ICON_NAMES = ${JSON.stringify(
    Object.fromEntries(
      Object.entries(tokens.icons)
        .filter(([, v]) => v && typeof v === "object")
        .map(([k, v]) => [k, v.lucide]),
    ),
    null,
    2,
  )};

/** Hareket sabitleri (orb + geçişler). */
export const MOTION = ${JSON.stringify(tokens.motion, null, 2)};

/** Durum → renk anahtarı eşlemesi. */
export const STATUS_TONE = ${JSON.stringify(tokens.status, null, 2)};

/**
 * NOVA Pulse Aperture marka işareti — geometri, yollar ve hareket zamanlaması.
 * Android \`NovaApertureTokens\` ile birebir aynı sayılar.
 */
export const APERTURE = ${JSON.stringify(tokens.brand.aperture, null, 2)};
`;

  write("web/src/design/tokens.generated.mjs", js);
  buildFavicon();
}

/* ------------------------ favicon / PWA ikonu ---------------------------- */

/**
 * Sekme ve PWA ikonu. Uygulama içindeki NovaMark ile aynı yolları kullanır;
 * marka paleti olarak `aperture` aksanı sabittir (tarayıcı sekmesi temaya
 * göre değişemez). Animasyon yok: favicon'lar güvenilir biçimde oynatmaz.
 */
function buildFavicon() {
  const ap = tokens.brand.aperture;
  // Sekme ikonu web varsayılan temasını kullanır: uygulama açıldığında
  // görülen renkle sekmedeki renk aynı olsun.
  const brandAccent = tokens.accents.find((a) => a.webDefault) || tokens.accents[0];
  const c = brandAccent.aperture;
  const bg = tokens.color.bg.hex;
  const vb = ap.viewport;
  const pad = 0.13 * vb;           // yuvarlatılmış zeminde nefes payı

  const svg = `<?xml version="1.0" encoding="UTF-8"?>
<!-- OTOMATİK ÜRETİLDİ — kaynak: design/nova-tokens.json · npm run tokens -->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${vb} ${vb}" width="${vb}" height="${vb}">
  <defs>
    <linearGradient id="field" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#15162B"/>
      <stop offset="0.54" stop-color="${bg}"/>
      <stop offset="1" stop-color="#060711"/>
    </linearGradient>
    <linearGradient id="body" gradientUnits="userSpaceOnUse"
      x1="${ap.bodyGradient.startX}" y1="${ap.bodyGradient.startY}"
      x2="${ap.bodyGradient.endX}" y2="${ap.bodyGradient.endY}">
      <stop offset="0" stop-color="${c.light}"/>
      <stop offset="0.44" stop-color="${c.mid}"/>
      <stop offset="1" stop-color="${c.deep}"/>
    </linearGradient>
    <linearGradient id="fold" gradientUnits="userSpaceOnUse"
      x1="${ap.foldGradient.startX}" y1="${ap.foldGradient.startY}"
      x2="${ap.foldGradient.endX}" y2="${ap.foldGradient.endY}">
      <stop offset="0" stop-color="${c.light}" stop-opacity="0.92"/>
      <stop offset="0.55" stop-color="${c.mid}" stop-opacity="0.68"/>
      <stop offset="1" stop-color="${c.deep}" stop-opacity="0.16"/>
    </linearGradient>
    <radialGradient id="coreGlow" cx="50%" cy="50%" r="50%">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.95"/>
      <stop offset="0.42" stop-color="${c.core}" stop-opacity="0.72"/>
      <stop offset="1" stop-color="${c.light}" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="bodyClip"><path d="${ap.bodyPath}"/></clipPath>
  </defs>

  <rect width="${vb}" height="${vb}" rx="${Math.round(vb * 0.22)}" fill="url(#field)"/>
  <circle cx="${vb * 0.48}" cy="${vb * 0.44}" r="${vb * 0.34}" fill="${c.mid}" opacity="0.16"/>

  <g transform="translate(${vb / 2} ${vb / 2}) scale(${((vb - pad * 2) / vb).toFixed(4)}) translate(${-ap.originX} ${-ap.originY})">
    <path d="${ap.bodyPath}" fill="url(#body)"/>
    <g clip-path="url(#bodyClip)">
      <circle cx="247" cy="242" r="74" fill="${c.glow}" fill-opacity="0.48"/>
      <circle cx="327" cy="316" r="92" fill="${c.deep}" fill-opacity="0.62"/>
      <path d="${ap.foldPath}" fill="url(#fold)"/>
    </g>
    <path d="${ap.bodyPath}" fill="none" stroke="${c.light}" stroke-opacity="${ap.strokeAlpha}" stroke-width="1.5"/>
    <circle cx="${ap.coreX}" cy="${ap.coreY}" r="${ap.coreGlowRadius}" fill="url(#coreGlow)"/>
    <circle cx="${ap.coreX}" cy="${ap.coreY}" r="${ap.coreRadius}" fill="${c.core}"/>
    <circle cx="${ap.highlightX}" cy="${ap.highlightY}" r="${ap.highlightRadius}" fill="#FFFFFF" fill-opacity="0.9"/>
  </g>
</svg>
`;
  write("web/public/icon.svg", svg);
}

/* -------------------------------- ANDROID ------------------------------- */

function buildAndroid() {
  const c = tokens.color;
  const ap = tokens.brand.aperture;
  const colorLines = Object.entries(c)
    .map(([k, v]) => `val ${pascal(k)} = ${ktColor(v)}`)
    .join("\n");

  const fallbackAperture = (tokens.accents.find((a) => a.aperture) || {}).aperture || {};

  const accentLines = tokens.accents
    .map((a) => {
      const ap = { ...fallbackAperture, ...(a.aperture || {}) };
      const surface = a.surface
        ? (
          `        surface = NovaSurfaceColors(\n` +
          `            bg = ${ktColor(a.surface.bg)},\n` +
          `            bg2 = ${ktColor(a.surface.bg2)},\n` +
          `            bg3 = ${ktColor(a.surface.bg3)},\n` +
          `            tint = ${ktColor(a.surface.tint)},\n` +
          `        ),\n`
        )
        : "";
      return (
        `    NovaAccent(\n` +
        `        id = "${a.id}",\n` +
        `        name = "${a.name}",\n` +
        `        primary = ${ktColor(a.primary)},\n` +
        `        secondary = ${ktColor(a.secondary)},\n` +
        `        tertiary = ${ktColor(a.tertiary)},\n` +
        `        onPrimary = ${ktColor(a.onPrimary)},\n` +
        `        aperture = NovaApertureColors(\n` +
        `            light = ${ktColor(ap.light)},\n` +
        `            mid = ${ktColor(ap.mid)},\n` +
        `            deep = ${ktColor(ap.deep)},\n` +
        `            glow = ${ktColor(ap.glow)},\n` +
        `            core = ${ktColor(ap.core)},\n` +
        `        ),\n` +
        surface +
        `    ),`
      );
    })
    .join("\n");

  const androidDefault = (tokens.accents.find((a) => a.androidDefault) || tokens.accents[0]).id;
  const androidAliasLines = Object.entries(THEME_ALIASES)
    .map(([legacy, current]) => `    "${legacy}" to "${current}",`)
    .join("\n");

  const radiusLines = Object.entries(tokens.radius)
    .map(([k, v]) => `    val ${k} = ${v}.dp`)
    .join("\n");
  const spaceLines = Object.entries(tokens.space)
    .map(([k, v]) => `    val ${k} = ${v}.dp`)
    .join("\n");
  const durLines = Object.entries(tokens.motion.duration)
    .map(([k, v]) => `    const val ${k} = ${v}`)
    .join("\n");

  const kt = `${BANNER_KT}package com.nova.agent.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* ---- Temel renkler (web CSS değişkenleriyle birebir) ---- */
${colorLines}

/** Pulse Aperture marka işaretinin tema başına renkleri. */
data class NovaApertureColors(
    val light: Color,
    val mid: Color,
    val deep: Color,
    val glow: Color,
    val core: Color,
)

/** Temaya özel mat yüzeyler; tanımlı değilse ortak koyu yüzeyler kullanılır. */
data class NovaSurfaceColors(
    val bg: Color,
    val bg2: Color,
    val bg3: Color,
    val tint: Color,
) {
    companion object {
        fun default(): NovaSurfaceColors = NovaSurfaceColors(
            bg = Bg,
            bg2 = Bg2,
            bg3 = Bg3,
            tint = Bg2,
        )
    }
}

/** Aksan teması — web \`ACCENTS\` dizisiyle aynı id/sıra. */
data class NovaAccent(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val aperture: NovaApertureColors,
    val surface: NovaSurfaceColors? = null,
)

val NOVA_ACCENTS: List<NovaAccent> = listOf(
${accentLines}
)

const val DEFAULT_ACCENT_ID = "${androidDefault}"

val NOVA_THEME_ALIASES: Map<String, String> = mapOf(
${androidAliasLines}
)

fun normalizeThemeId(id: String?): String {
    val mapped = id?.let { NOVA_THEME_ALIASES[it] ?: it }
    return mapped?.takeIf { candidate -> NOVA_ACCENTS.any { it.id == candidate } }
        ?: DEFAULT_ACCENT_ID
}

fun accentFor(id: String?): NovaAccent =
    NOVA_ACCENTS.first { it.id == normalizeThemeId(id) }

/** Köşe yarıçapları — web \`--radius-*\` ile aynı. */
object NovaRadius {
${radiusLines}
}

/** Boşluk skalası — web \`--space-*\` ile aynı. */
object NovaSpace {
${spaceLines}
}

/** Hareket süreleri (ms) — web \`--dur-*\` ile aynı. */
object NovaDuration {
${durLines}
}

/** Orb geometrisi — web canvas orb'u ile aynı formül. */
object NovaOrb {
    const val blobCount = ${tokens.motion.orb.blobCount}
    const val particleCount = ${tokens.motion.orb.particleCount}
    const val coreRadiusRatio = ${tokens.motion.orb.coreRadiusRatio}f
    const val ringRadiusRatio = ${tokens.motion.orb.ringRadiusRatio}f
    const val levelGain = ${tokens.motion.orb.levelGain}f
}

/**
 * NOVA Pulse Aperture marka işareti — web \`APERTURE\` ile birebir aynı
 * geometri, yollar ve zamanlama. Renkler burada DEĞİL: aktif aksanın
 * [NovaAccent.aperture] setinden gelir.
 */
object NovaApertureTokens {
    const val viewport = ${ap.viewport}f
    const val originX = ${ap.originX}f
    const val originY = ${ap.originY}f
    const val componentSizeDp = ${ap.componentSize}f
    const val haloDiameterDp = ${ap.haloDiameter}f
    const val haloDotCount = ${ap.haloDotCount}
    const val markRestSizeDp = ${ap.markRestSize}f
    const val markPeakSizeDp = ${ap.markPeakSize}f
    const val coreX = ${ap.coreX}f
    const val coreY = ${ap.coreY}f
    const val coreGlowRadius = ${ap.coreGlowRadius}f
    const val coreRadius = ${ap.coreRadius}f
    const val highlightX = ${ap.highlightX}f
    const val highlightY = ${ap.highlightY}f
    const val highlightRadius = ${ap.highlightRadius}f
    const val strokeAlpha = ${ap.strokeAlpha}f

    const val breathDurationMillis = ${ap.motion.breathMs}
    const val corePulseDurationMillis = ${ap.motion.corePulseMs}
    const val orbitDurationMillis = ${ap.motion.orbitMs}
    const val shimmerDurationMillis = ${ap.motion.shimmerMs}
    const val microTiltDegrees = ${ap.motion.microTiltDeg}f

    const val bodyPathData = "${ap.bodyPath}"

    const val foldPathData = "${ap.foldPath}"

    const val bodyGradientStartX = ${ap.bodyGradient.startX}f
    const val bodyGradientStartY = ${ap.bodyGradient.startY}f
    const val bodyGradientEndX = ${ap.bodyGradient.endX}f
    const val bodyGradientEndY = ${ap.bodyGradient.endY}f
    const val foldGradientStartX = ${ap.foldGradient.startX}f
    const val foldGradientStartY = ${ap.foldGradient.startY}f
    const val foldGradientEndX = ${ap.foldGradient.endX}f
    const val foldGradientEndY = ${ap.foldGradient.endY}f
}
`;

  write("nova-android/app/src/main/java/com/nova/agent/ui/theme/NovaTokens.kt", kt);
}

console.log("NOVA tasarım token'ları üretiliyor…");
buildWeb();
buildAndroid();
console.log("Bitti.");
