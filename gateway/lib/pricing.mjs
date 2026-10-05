// Pure cost model. Prices are in micro-dollars ($1e-6) PER TOKEN,
// as [inputPerToken, outputPerToken]. Tune to real provider rates.
// No external deps -> unit-testable in isolation.
export const PRICES = {
  "anthropic/claude-opus-4-20250514":   [15, 75],
  "anthropic/claude-sonnet-4-20250514": [3, 15],
  "gemini/gemini-2.5-pro":              [1.25, 10],
  "gemini/gemini-2.5-flash":            [0.3, 2.5],
  "openai/gpt-4o-mini":                 [0.15, 0.6],
  "ollama/*":                           [0, 0],     // local inference = free
  "openclaw/*":                         [0, 0],
};

// Unpriced routes must never silently consume a paid credential for free.
export function priceFor(route, env = process.env) {
  let configured;
  try { configured = JSON.parse(env.MODEL_PRICES_JSON || "{}"); }
  catch { throw Object.assign(new Error("invalid model price configuration"), { status: 503 }); }
  if (configured && Object.hasOwn(configured, route)) {
    const rates = configured[route];
    if (!Array.isArray(rates) || rates.length !== 2 || !rates.every(n => typeof n === "number" && Number.isFinite(n) && n >= 0) || rates[0] + rates[1] <= 0)
      throw Object.assign(new Error("invalid model price configuration"), { status: 503 });
    return rates;
  }
  if (Object.hasOwn(PRICES, route)) return PRICES[route];
  const prov = String(route || "").split("/")[0];
  if (Object.hasOwn(PRICES, prov + "/*")) return PRICES[prov + "/*"];
  throw Object.assign(new Error("model price is not configured"), { status: 400 });
}

export function hasPrice(route, env = process.env) {
  try { priceFor(route, env); return true; } catch { return false; }
}

// Rough estimate when the provider doesn't return usage (~4 chars/token).
export const approxTokens = (text) => Math.ceil(String(text || "").length / 4);

export function estimateCostMicros(route, tokensIn, tokensOut) {
  const [pin, pout] = priceFor(route);
  return Math.round(pin * (tokensIn || 0) + pout * (tokensOut || 0));
}
