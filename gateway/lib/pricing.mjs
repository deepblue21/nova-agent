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

// Resolve exact route first, then "provider/*", else free.
export function priceFor(route) {
  if (PRICES[route]) return PRICES[route];
  const prov = String(route || "").split("/")[0];
  return PRICES[prov + "/*"] || [0, 0];
}

// Rough estimate when the provider doesn't return usage (~4 chars/token).
export const approxTokens = (text) => Math.ceil(String(text || "").length / 4);

export function estimateCostMicros(route, tokensIn, tokensOut) {
  const [pin, pout] = priceFor(route);
  return Math.round(pin * (tokensIn || 0) + pout * (tokensOut || 0));
}
