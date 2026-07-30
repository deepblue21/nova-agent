// OTOMATİK ÜRETİLDİ — elle düzenleme.
// Kaynak: design/nova-tokens.json · Yeniden üret: npm run tokens

/** design/nova-tokens.json'dan üretilen CSS değişkenleri (base + aksan temaları). */
export const TOKENS_CSS = ":root, .nova-root {\n  --bg: #0A0E1A;\n  --bg2: #0D1326;\n  --bg3: #080A11;\n  --surface1: rgba(255,255,255,0.05);\n  --surface2: rgba(255,255,255,0.08);\n  --panel: rgba(13,19,38,0.78);\n  --blur-tint: rgba(10,14,26,0.72);\n  --scrim: rgba(0,0,0,0.68);\n  --line: rgba(255,255,255,0.09);\n  --text: #EEF1FB;\n  --muted: #9AA3BD;\n  --muted2: #5B6276;\n  --success: #53D6A6;\n  --warning: #FFC857;\n  --danger: #FB7185;\n  --ember: #FF8A5B;\n  --radius-xs: 8px;\n  --radius-sm: 10px;\n  --radius-md: 13px;\n  --radius-lg: 16px;\n  --radius-xl: 20px;\n  --radius-xxl: 26px;\n  --radius-pill: 999px;\n  --space-xxs: 4px;\n  --space-xs: 6px;\n  --space-sm: 8px;\n  --space-md: 12px;\n  --space-lg: 16px;\n  --space-xl: 20px;\n  --space-xxl: 26px;\n  --space-xxxl: 32px;\n  --dur-instant: 120ms;\n  --dur-fast: 180ms;\n  --dur-base: 240ms;\n  --dur-slow: 420ms;\n  --dur-reveal: 620ms;\n  --dur-orb-cycle: 9000ms;\n  --dur-aurora-drift: 28000ms;\n  --ease-standard: cubic-bezier(0.2, 0.7, 0.3, 1);\n  --ease-emphasized: cubic-bezier(0.16, 1, 0.3, 1);\n  --ease-exit: cubic-bezier(0.4, 0, 1, 1);\n  --line-bright: rgba(var(--accent-rgb), 0.34);\n  --glow: 0 0 48px rgba(var(--accent-2-rgb), 0.30);\n  --glass-blur: 16px;\n\n  /* Koyu şema varsayılanları — [data-scheme=\"light\"] bunları ezer. */\n  --aurora-blend: screen;\n  --aurora-opacity: 0.5;\n  --aurora-blur: 92px;\n  --glass-1: rgba(16,20,38,0.80);\n  --glass-2: rgba(10,14,26,0.88);\n  --glass-shadow: rgba(0,0,0,0.38);\n  --code-bg: #070A11;\n  --code-fg: #CFE8FF;\n  --grid-line: rgba(255,255,255,0.025);\n\n  /* Marka işareti — açık şemada ezilir (bkz. schemes.light.aperture). */\n  --ap-core-hi: #FFFFFF;\n  --ap-stroke-alpha: 0.42;\n  --ap-halo-dot: #E1E5FF;\n}\n[data-accent=\"amethyst\"]{\n  --accent: #7558FF;\n  --accent-rgb: 117,88,255;\n  --accent-2: #5EE8FF;\n  --accent-2-rgb: 94,232,255;\n  --accent-3: #B7A7FF;\n  --accent-3-rgb: 183,167,255;\n  --on-accent: #FFFAF1;\n  --ap-light: #B7A7FF;\n  --ap-mid: #7558FF;\n  --ap-mid-rgb: 117,88,255;\n  --ap-deep: #4525ED;\n  --ap-glow: #5EE8FF;\n  --ap-glow-rgb: 94,232,255;\n  --ap-core: #FFFAF1;\n}\n[data-accent=\"arctic\"]{\n  --accent: #27D9E8;\n  --accent-rgb: 39,217,232;\n  --accent-2: #B0FFFF;\n  --accent-2-rgb: 176,255,255;\n  --accent-3: #8FF7FF;\n  --accent-3-rgb: 143,247,255;\n  --on-accent: #030C10;\n  --ap-light: #8FF7FF;\n  --ap-mid: #27D9E8;\n  --ap-mid-rgb: 39,217,232;\n  --ap-deep: #0795B6;\n  --ap-glow: #B0FFFF;\n  --ap-glow-rgb: 176,255,255;\n  --ap-core: #F8FFFF;\n}\n[data-accent=\"sapphire\"]{\n  --accent: #398CFF;\n  --accent-rgb: 57,140,255;\n  --accent-2: #74F3FF;\n  --accent-2-rgb: 116,243,255;\n  --accent-3: #8ED7FF;\n  --accent-3-rgb: 142,215,255;\n  --on-accent: #040914;\n  --ap-light: #8ED7FF;\n  --ap-mid: #398CFF;\n  --ap-mid-rgb: 57,140,255;\n  --ap-deep: #1454E8;\n  --ap-glow: #74F3FF;\n  --ap-glow-rgb: 116,243,255;\n  --ap-core: #F8FEFF;\n}\n[data-accent=\"emerald\"]{\n  --accent: #20BD9C;\n  --accent-rgb: 32,189,156;\n  --accent-2: #66F3E7;\n  --accent-2-rgb: 102,243,231;\n  --accent-3: #78E9C4;\n  --accent-3-rgb: 120,233,196;\n  --on-accent: #040D0A;\n  --ap-light: #78E9C4;\n  --ap-mid: #20BD9C;\n  --ap-mid-rgb: 32,189,156;\n  --ap-deep: #078C78;\n  --ap-glow: #66F3E7;\n  --ap-glow-rgb: 102,243,231;\n  --ap-core: #F6FFF9;\n}\n[data-accent=\"lime\"]{\n  --accent: #9FE02A;\n  --accent-rgb: 159,224,42;\n  --accent-2: #E7FF94;\n  --accent-2-rgb: 231,255,148;\n  --accent-3: #D7FF76;\n  --accent-3-rgb: 215,255,118;\n  --on-accent: #080D03;\n  --ap-light: #D7FF76;\n  --ap-mid: #9FE02A;\n  --ap-mid-rgb: 159,224,42;\n  --ap-deep: #61A80B;\n  --ap-glow: #E7FF94;\n  --ap-glow-rgb: 231,255,148;\n  --ap-core: #FFFDF0;\n}\n[data-accent=\"amber\"]{\n  --accent: #F6AD2F;\n  --accent-rgb: 246,173,47;\n  --accent-2: #FFC85B;\n  --accent-2-rgb: 255,200,91;\n  --accent-3: #FFE18A;\n  --accent-3-rgb: 255,225,138;\n  --on-accent: #0E0A03;\n  --ap-light: #FFE18A;\n  --ap-mid: #F6AD2F;\n  --ap-mid-rgb: 246,173,47;\n  --ap-deep: #D87808;\n  --ap-glow: #FFC85B;\n  --ap-glow-rgb: 255,200,91;\n  --ap-core: #FFF9E7;\n}\n[data-accent=\"copper\"]{\n  --accent: #DC7136;\n  --accent-rgb: 220,113,54;\n  --accent-2: #FFC27F;\n  --accent-2-rgb: 255,194,127;\n  --accent-3: #F5B17C;\n  --accent-3-rgb: 245,177,124;\n  --on-accent: #0E0704;\n  --ap-light: #F5B17C;\n  --ap-mid: #DC7136;\n  --ap-mid-rgb: 220,113,54;\n  --ap-deep: #A63F18;\n  --ap-glow: #FFC27F;\n  --ap-glow-rgb: 255,194,127;\n  --ap-core: #FFF7EA;\n}\n[data-accent=\"coral\"]{\n  --accent: #FF5F8F;\n  --accent-rgb: 255,95,143;\n  --accent-2: #FFBF8F;\n  --accent-2-rgb: 255,191,143;\n  --accent-3: #FFAC9E;\n  --accent-3-rgb: 255,172,158;\n  --on-accent: #0D060B;\n  --ap-light: #FFAC9E;\n  --ap-mid: #FF5F8F;\n  --ap-mid-rgb: 255,95,143;\n  --ap-deep: #D82375;\n  --ap-glow: #FFBF8F;\n  --ap-glow-rgb: 255,191,143;\n  --ap-core: #FFF8ED;\n}\n[data-accent=\"ruby\"]{\n  --accent: #EF3158;\n  --accent-rgb: 239,49,88;\n  --accent-2: #FFAD9E;\n  --accent-2-rgb: 255,173,158;\n  --accent-3: #FF8F9F;\n  --accent-3-rgb: 255,143,159;\n  --on-accent: #0E0407;\n  --ap-light: #FF8F9F;\n  --ap-mid: #EF3158;\n  --ap-mid-rgb: 239,49,88;\n  --ap-deep: #B80E36;\n  --ap-glow: #FFAD9E;\n  --ap-glow-rgb: 255,173,158;\n  --ap-core: #FFF9F2;\n  --bg: #050507;\n  --bg2: #0A0A0D;\n  --bg3: #030304;\n  --panel: color-mix(in srgb, #17070C 78%, transparent);\n  --line: rgba(239,49,88,0.16);\n  --blur-tint: color-mix(in srgb, #17070C 74%, transparent);\n  --scrim: rgba(0,0,0,0.78);\n}\n[data-accent=\"lavender\"]{\n  --accent: #AD7EE9;\n  --accent-rgb: 173,126,233;\n  --accent-2: #E8CFFF;\n  --accent-2-rgb: 232,207,255;\n  --accent-3: #DCC5FF;\n  --accent-3-rgb: 220,197,255;\n  --on-accent: #0A0713;\n  --ap-light: #DCC5FF;\n  --ap-mid: #AD7EE9;\n  --ap-mid-rgb: 173,126,233;\n  --ap-deep: #8050C8;\n  --ap-glow: #E8CFFF;\n  --ap-glow-rgb: 232,207,255;\n  --ap-core: #FFFAFB;\n}\n[data-accent=\"moonstone\"]{\n  --accent: #9EABC1;\n  --accent-rgb: 158,171,193;\n  --accent-2: #CCEFFF;\n  --accent-2-rgb: 204,239,255;\n  --accent-3: #E1E8F2;\n  --accent-3-rgb: 225,232,242;\n  --on-accent: #070A11;\n  --ap-light: #E1E8F2;\n  --ap-mid: #9EABC1;\n  --ap-mid-rgb: 158,171,193;\n  --ap-deep: #5F708E;\n  --ap-glow: #CCEFFF;\n  --ap-glow-rgb: 204,239,255;\n  --ap-core: #FFFDF8;\n}\n\n[data-scheme=\"light\"] {\n  --bg: #F6F3EC;\n  --bg2: #FFFFFF;\n  --bg3: #EDE9E0;\n  --surface1: rgba(19,23,34,0.045);\n  --surface2: rgba(19,23,34,0.075);\n  --line: rgba(19,23,34,0.13);\n  --panel: rgba(255,255,255,0.82);\n  --blur-tint: rgba(255,255,255,0.72);\n  --scrim: rgba(42,38,32,0.42);\n  --text: #171A24;\n  --muted: #5A6274;\n  --muted2: #8A93A6;\n  --ap-core-hi: var(--ap-mid);\n  --ap-stroke-alpha: 0.58;\n  --ap-halo-dot: #6B7488;\n  --aurora-blend: multiply;\n  --aurora-opacity: 0.2;\n  --aurora-blur: 96px;\n  --glass-1: color-mix(in srgb, #FFFFFF 88%, transparent);\n  --glass-2: color-mix(in srgb, #FFFFFF 74%, transparent);\n  --glass-shadow: rgba(23,26,36,0.12);\n  --code-bg: #FBF9F4;\n  --code-fg: #1C2230;\n  --grid-line: rgba(19,23,34,0.045);\n  color-scheme: light;\n}";

/** Ortak aksan temaları — Android `NOVA_ACCENTS` ile birebir aynı sıradadır. */
export const ACCENTS = [
  {
    "id": "amethyst",
    "name": "Ametist",
    "primary": "#7558FF",
    "secondary": "#5EE8FF",
    "tertiary": "#B7A7FF",
    "onPrimary": "#FFFAF1",
    "aperture": {
      "light": "#B7A7FF",
      "mid": "#7558FF",
      "deep": "#4525ED",
      "glow": "#5EE8FF",
      "core": "#FFFAF1"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-amethyst.svg"
  },
  {
    "id": "arctic",
    "name": "Arktik",
    "primary": "#27D9E8",
    "secondary": "#B0FFFF",
    "tertiary": "#8FF7FF",
    "onPrimary": "#030C10",
    "aperture": {
      "light": "#8FF7FF",
      "mid": "#27D9E8",
      "deep": "#0795B6",
      "glow": "#B0FFFF",
      "core": "#F8FFFF"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-arctic.svg"
  },
  {
    "id": "sapphire",
    "name": "Safir",
    "primary": "#398CFF",
    "secondary": "#74F3FF",
    "tertiary": "#8ED7FF",
    "onPrimary": "#040914",
    "aperture": {
      "light": "#8ED7FF",
      "mid": "#398CFF",
      "deep": "#1454E8",
      "glow": "#74F3FF",
      "core": "#F8FEFF"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-sapphire.svg"
  },
  {
    "id": "emerald",
    "name": "Zümrüt",
    "primary": "#20BD9C",
    "secondary": "#66F3E7",
    "tertiary": "#78E9C4",
    "onPrimary": "#040D0A",
    "aperture": {
      "light": "#78E9C4",
      "mid": "#20BD9C",
      "deep": "#078C78",
      "glow": "#66F3E7",
      "core": "#F6FFF9"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-emerald.svg"
  },
  {
    "id": "lime",
    "name": "Limon",
    "primary": "#9FE02A",
    "secondary": "#E7FF94",
    "tertiary": "#D7FF76",
    "onPrimary": "#080D03",
    "aperture": {
      "light": "#D7FF76",
      "mid": "#9FE02A",
      "deep": "#61A80B",
      "glow": "#E7FF94",
      "core": "#FFFDF0"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-lime.svg"
  },
  {
    "id": "amber",
    "name": "Amber",
    "primary": "#F6AD2F",
    "secondary": "#FFC85B",
    "tertiary": "#FFE18A",
    "onPrimary": "#0E0A03",
    "aperture": {
      "light": "#FFE18A",
      "mid": "#F6AD2F",
      "deep": "#D87808",
      "glow": "#FFC85B",
      "core": "#FFF9E7"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-amber.svg"
  },
  {
    "id": "copper",
    "name": "Bakır",
    "primary": "#DC7136",
    "secondary": "#FFC27F",
    "tertiary": "#F5B17C",
    "onPrimary": "#0E0704",
    "aperture": {
      "light": "#F5B17C",
      "mid": "#DC7136",
      "deep": "#A63F18",
      "glow": "#FFC27F",
      "core": "#FFF7EA"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-copper.svg"
  },
  {
    "id": "coral",
    "name": "Mercan",
    "primary": "#FF5F8F",
    "secondary": "#FFBF8F",
    "tertiary": "#FFAC9E",
    "onPrimary": "#0D060B",
    "aperture": {
      "light": "#FFAC9E",
      "mid": "#FF5F8F",
      "deep": "#D82375",
      "glow": "#FFBF8F",
      "core": "#FFF8ED"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-coral.svg"
  },
  {
    "id": "ruby",
    "name": "Kızıl",
    "primary": "#EF3158",
    "secondary": "#FFAD9E",
    "tertiary": "#FF8F9F",
    "onPrimary": "#0E0407",
    "aperture": {
      "light": "#FF8F9F",
      "mid": "#EF3158",
      "deep": "#B80E36",
      "glow": "#FFAD9E",
      "core": "#FFF9F2"
    },
    "surface": {
      "bg": "#050507",
      "bg2": "#0A0A0D",
      "bg3": "#030304",
      "tint": "#17070C"
    },
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-ruby.svg"
  },
  {
    "id": "lavender",
    "name": "Lavanta",
    "primary": "#AD7EE9",
    "secondary": "#E8CFFF",
    "tertiary": "#DCC5FF",
    "onPrimary": "#0A0713",
    "aperture": {
      "light": "#DCC5FF",
      "mid": "#AD7EE9",
      "deep": "#8050C8",
      "glow": "#E8CFFF",
      "core": "#FFFAFB"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-lavender.svg"
  },
  {
    "id": "moonstone",
    "name": "Aytaşı",
    "primary": "#9EABC1",
    "secondary": "#CCEFFF",
    "tertiary": "#E1E8F2",
    "onPrimary": "#070A11",
    "aperture": {
      "light": "#E1E8F2",
      "mid": "#9EABC1",
      "deep": "#5F708E",
      "glow": "#CCEFFF",
      "core": "#FFFDF8"
    },
    "surface": null,
    "sourceAsset": "design/brand/pulse-aperture/nova-pulse-aperture-orbital-v4-moonstone.svg"
  }
];

export const DEFAULT_ACCENT = "amethyst";

/** Eski kaydedilmiş tema kimliklerini güncel ortak kataloğa taşır. */
export const THEME_ALIASES = {
  "aurora": "arctic",
  "nova": "arctic",
  "plum": "lavender",
  "violet": "amethyst",
  "aperture": "amethyst",
  "kizil": "ruby",
  "okyanus": "sapphire",
  "zumrut": "emerald",
  "gul": "coral",
  "gunbatimi": "copper",
  "amber": "amber"
};

/** Ortak gezinme hedefleri — Android NavigationBar ile aynı sıra ve etiketler. */
export const DESTINATIONS = [
  {
    "id": "kontrol",
    "label": "Kontrol",
    "icon": "control"
  },
  {
    "id": "isler",
    "label": "İşler",
    "icon": "tasks"
  },
  {
    "id": "sohbet",
    "label": "Sohbet",
    "icon": "chat"
  },
  {
    "id": "modeller",
    "label": "Modeller",
    "icon": "models"
  }
];

/** Semantik ikon sözlüğü (lucide adları). */
export const ICON_NAMES = {
  "brand": "NovaMark",
  "control": "LayoutDashboard",
  "tasks": "ListChecks",
  "chat": "MessageSquare",
  "models": "Boxes",
  "voice": "Mic",
  "settings": "Settings",
  "shield": "ShieldCheck",
  "local": "Smartphone",
  "gateway": "Link2",
  "cloud": "Cloud",
  "cpu": "Cpu",
  "agent": "Waves",
  "team": "Workflow",
  "think": "Brain",
  "stop": "Square",
  "send": "Send",
  "history": "History",
  "delete": "Trash2",
  "download": "Download",
  "check": "Check",
  "pause": "Pause",
  "resume": "Play",
  "cancel": "X",
  "retry": "RotateCcw"
};

/** Hareket sabitleri (orb + geçişler). */
export const MOTION = {
  "duration": {
    "instant": 120,
    "fast": 180,
    "base": 240,
    "slow": 420,
    "reveal": 620,
    "orbCycle": 9000,
    "auroraDrift": 28000
  },
  "easing": {
    "standard": "cubic-bezier(0.2, 0.7, 0.3, 1)",
    "emphasized": "cubic-bezier(0.16, 1, 0.3, 1)",
    "exit": "cubic-bezier(0.4, 0, 1, 1)"
  },
  "orb": {
    "blobCount": 5,
    "particleCount": 54,
    "coreRadiusRatio": 0.55,
    "ringRadiusRatio": 1.16,
    "levelGain": 0.22,
    "desc": "Web canvas orb'u ile Compose Orb.kt aynı formülü kullanır."
  }
};

/** Durum → renk anahtarı eşlemesi. */
export const STATUS_TONE = {
  "note": "Bağlantı ve görev durumlarının ortak renk eşlemesi.",
  "connection": {
    "READY": "success",
    "CHECKING": "warning",
    "AUTH_REQUIRED": "danger",
    "UNREACHABLE": "danger",
    "INVALID_URL": "danger",
    "UNKNOWN": "muted"
  },
  "task": {
    "queued": "muted",
    "routing": "warning",
    "observing": "warning",
    "planning": "warning",
    "executing": "accent",
    "verifying": "accent",
    "waiting_for_confirmation": "warning",
    "waiting_for_device": "warning",
    "waiting_for_compute": "warning",
    "paused": "muted",
    "completed": "success",
    "failed": "danger",
    "cancelled": "muted"
  }
};

/**
 * NOVA Pulse Aperture marka işareti — geometri, yollar ve hareket zamanlaması.
 * Android `NovaApertureTokens` ile birebir aynı sayılar.
 */
export const APERTURE = {
  "viewport": 560,
  "originX": 280,
  "originY": 282,
  "componentSize": 44,
  "haloDiameter": 36,
  "haloDotCount": 14,
  "markRestSize": 18,
  "markPeakSize": 23,
  "coreX": 280,
  "coreY": 286,
  "coreGlowRadius": 58,
  "coreRadius": 22,
  "highlightX": 273,
  "highlightY": 278,
  "highlightRadius": 6.5,
  "strokeAlpha": 0.42,
  "motion": {
    "breathMs": 2750,
    "corePulseMs": 1375,
    "orbitMs": 4200,
    "shimmerMs": 5500,
    "microTiltDeg": 0.7,
    "easing": "cubic-bezier(0.45, 0, 0.25, 1)"
  },
  "bodyPath": "M280,128 C319,128 319,205 354,226 C381,242 429,244 437,277 C445,311 390,323 361,342 C327,365 319,433 280,436 C241,433 233,365 199,342 C170,323 115,311 123,277 C131,244 179,242 206,226 C241,205 241,128 280,128Z",
  "foldPath": "M250,130 C285,178 324,198 316,239 C309,277 250,282 246,324 C243,359 286,386 294,434 C261,420 248,379 219,354 C188,327 153,319 126,297 C164,285 203,267 225,238 C249,207 242,165 250,130Z",
  "bodyGradient": {
    "startX": 138,
    "startY": 128,
    "endX": 420,
    "endY": 428
  },
  "foldGradient": {
    "startX": 174,
    "startY": 132,
    "endX": 332,
    "endY": 420
  }
};
