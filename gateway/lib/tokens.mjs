// Extract REAL token usage from provider streaming payloads (pure, no deps).
// Replaces the approxTokens(~4 chars/token) estimate for accurate metering/billing.
// Each extractor returns { in, out } or null when a chunk carries no usage.
//
// Notes per provider:
//  - OpenAI: needs `stream_options:{ include_usage:true }` so the final chunk
//    includes `usage:{ prompt_tokens, completion_tokens }`.
//  - Anthropic: `message_start` carries input_tokens; `message_delta` carries
//    cumulative output_tokens.
//  - Gemini: chunks may include `usageMetadata` (usually on the last chunk).
//  - Ollama: the final `done:true` message has prompt_eval_count / eval_count.

export function usageFromOpenAI(o) {
  const u = o && o.usage;
  if (!u) return null;
  return { in: u.prompt_tokens || 0, out: u.completion_tokens || 0 };
}

export function usageFromAnthropic(o) {
  const u = (o && o.usage) || (o && o.message && o.message.usage);
  if (!u) return null;
  return { in: u.input_tokens || 0, out: u.output_tokens || 0 };
}

export function usageFromGemini(o) {
  const u = o && o.usageMetadata;
  if (!u) return null;
  return { in: u.promptTokenCount || 0, out: u.candidatesTokenCount || 0 };
}

export function usageFromOllama(o) {
  if (!o || (o.prompt_eval_count == null && o.eval_count == null)) return null;
  return { in: o.prompt_eval_count || 0, out: o.eval_count || 0 };
}

const EXTRACTORS = {
  openai: usageFromOpenAI,
  gateway: usageFromOpenAI,
  anthropic: usageFromAnthropic,
  gemini: usageFromGemini,
  ollama: usageFromOllama,
};

// Accumulate usage across a stream. Providers report cumulative or once-at-end,
// so keep the max seen per direction. Returns { observe, seen, get }.
export function makeUsageAccumulator(provider) {
  const pick = EXTRACTORS[provider] || (() => null);
  let acc = { in: 0, out: 0 };
  let seen = false;
  return {
    observe(obj) {
      const u = pick(obj);
      if (u) { seen = true; acc = { in: Math.max(acc.in, u.in), out: Math.max(acc.out, u.out) }; }
    },
    seen: () => seen,
    get: () => acc,
  };
}
