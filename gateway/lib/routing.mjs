// Pure routing helpers for the gateway. Kept outside gateway.mjs so image-aware
// dynamic routing can be unit-tested without starting the HTTP server.

export function messageTextLength(message) {
  if (!message) return 0;
  if (typeof message.content === "string") return message.content.length;
  if (!Array.isArray(message.content)) return 0;
  return message.content.reduce((sum, part) => {
    if (!part || part.type !== "text") return sum;
    return sum + String(part.text || "").length;
  }, 0);
}

export function hasImageContent(messages = []) {
  return messages.some(message => {
    if (!message || !Array.isArray(message.content)) return false;
    return message.content.some(part => {
      if (!part) return false;
      if (part.type === "image_url") return !!(part.image_url && part.image_url.url);
      if (part.type === "image") return true;
      return false;
    });
  });
}

export function pickDynamicModel({
  effort,
  messages = [],
  keys = {},
  defaultModel,
  visionModel,
  env = {},
}) {
  if (hasImageContent(messages)) {
    return env.ROUTE_VISION || visionModel || (keys.gemini ? "gemini/gemini-2.5-flash" : defaultModel);
  }

  const level = String(effort || "balanced").toUpperCase();
  const effortOverride = env["ROUTE_" + level];
  if (effortOverride) return effortOverride;

  const hasAnthropic = !!keys.anthropic;
  const hasGemini = !!keys.gemini;
  const total = messages.reduce((sum, message) => sum + messageTextLength(message), 0);

  if (total > 8000 && hasGemini) return "gemini/gemini-2.5-pro";
  if (effort === "fast") return hasGemini ? "gemini/gemini-2.5-flash" : defaultModel;
  if (effort === "deep" || effort === "max") {
    return hasAnthropic
      ? "anthropic/claude-sonnet-4-20250514"
      : (hasGemini ? "gemini/gemini-2.5-pro" : defaultModel);
  }
  return hasGemini
    ? "gemini/gemini-2.5-flash"
    : (hasAnthropic ? "anthropic/claude-sonnet-4-20250514" : defaultModel);
}
