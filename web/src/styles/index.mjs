// Tek stil giriş noktası. Token katmanı design/nova-tokens.json'dan üretilir;
// geri kalan katmanlar yalnız o değişkenleri kullanır (hex sabit yazılmaz).
import { TOKENS_CSS, APERTURE } from "../design/tokens.generated.mjs";
import { FONTS_CSS, BASE_CSS } from "./base.mjs";
import { SHELL_CSS } from "./shell.mjs";
import { SURFACES_CSS } from "./surfaces.mjs";
import { CHAT_CSS } from "./chat.mjs";
import { brandCss } from "./brand.mjs";

// Sıra önemli: @import en başta olmalı, token'lar diğer katmanlardan önce.
export const NOVA_CSS = [
  FONTS_CSS,
  TOKENS_CSS,
  BASE_CSS,
  SHELL_CSS,
  SURFACES_CSS,
  CHAT_CSS,
  brandCss(APERTURE),
].join("\n");
