import {
  ACCENTS,
  DEFAULT_ACCENT,
  THEME_ALIASES,
} from "../design/tokens.generated.mjs";

const themeIds = new Set(ACCENTS.map((item) => item.id));

export function normalizeThemeId(value) {
  if (typeof value !== "string") return DEFAULT_ACCENT;
  const normalized = THEME_ALIASES[value] || value;
  return themeIds.has(normalized) ? normalized : DEFAULT_ACCENT;
}

function rgbTriplet(hex) {
  const value = hex.replace("#", "");
  return [0, 2, 4]
    .map((offset) => Number.parseInt(value.slice(offset, offset + 2), 16))
    .join(",");
}

export function themePreviewStyle(themeId) {
  const normalized = normalizeThemeId(themeId);
  const theme = ACCENTS.find((item) => item.id === normalized)
    || ACCENTS.find((item) => item.id === DEFAULT_ACCENT)
    || ACCENTS[0];
  const { aperture } = theme;

  return {
    "--ap-light": aperture.light,
    "--ap-mid": aperture.mid,
    "--ap-mid-rgb": rgbTriplet(aperture.mid),
    "--ap-deep": aperture.deep,
    "--ap-glow": aperture.glow,
    "--ap-glow-rgb": rgbTriplet(aperture.glow),
    "--ap-core": aperture.core,
  };
}
