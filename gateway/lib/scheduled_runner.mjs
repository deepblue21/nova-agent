// Scheduled task execution. Kept outside gateway.mjs so the agent/direct-chat
// split is unit-testable without starting an HTTP server.
import { runAgent } from "./agent.mjs";
import { makeUsageAccumulator } from "./tokens.mjs";
import { routeModel } from "./providers.mjs";
import { checkQuota, recordUsage, approxTokens } from "./usage.mjs";
import { rateLimit } from "./cache.mjs";
import { isAllowed } from "./model_catalog.mjs";

export async function runScheduledTask(task, {
  defaultModel = "ollama/qwen3:14b",
  ollamaBase = "http://localhost:11434",
  providerClient,
  timeoutMs = 60000,
  maxRetries = 2,
  runAgentImpl = runAgent,
  checkQuotaImpl = checkQuota,
  recordUsageImpl = recordUsage,
  rateLimitImpl = rateLimit,
  env = process.env,
} = {}) {
  const { provider, model } = routeModel(task.model || defaultModel, defaultModel);
  if (provider !== "ollama") {
    return { status: "error", result: "zamanlanmış görevler yerel (ollama) model gerektirir" };
  }
  const messages = [
    { role: "system", content: "Sen NOVA'nın otomatik görev ajanısın. Görevi kısa, net ve eksiksiz yerine getir; gerekiyorsa araçları kullan." },
    { role: "user", content: task.prompt },
  ];
  const ctrl = new AbortController();
  const to = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const route = provider + '/' + model;
    const allow = (env.ALLOW_MODELS || '').split(',').map(s=>s.trim()).filter(Boolean);
    if (!isAllowed(route,allow)) return {status:'error',result:'model not allowed'};
    if (!(await rateLimitImpl(task.user_id,Number(env.RATE_MAX || 120),Number(env.RATE_WINDOW_MS || 60000))).allowed)
      return {status:'error',result:'rate limit exceeded'};
    if (!(await checkQuotaImpl(task.user_id)).allowed) return {status:'error',result:'quota exceeded'};
    if (task.agent === false) {
      if (!providerClient || typeof providerClient.chat !== "function") {
        return { status: "error", result: "provider client unavailable" };
      }
      const usage = makeUsageAccumulator(provider);
      const text = await providerClient.chat({
        provider,
        model,
        messages,
        stream: false,
        ctx: { signal: ctrl.signal, params: {}, retries: maxRetries, usage },
        res: null,
      });
      const tokens=usage.seen()?usage.get():{in:approxTokens(task.prompt),out:approxTokens(text)};
      await recordUsageImpl({userId:task.user_id,route,tokensIn:tokens.in,tokensOut:tokens.out});
      return { status: "ok", result: text || "" };
    }
    const r = await runAgentImpl({ ollamaBase, model, messages, signal: ctrl.signal, userId: task.user_id,
      documentScope: task.workspace_id ? { workspaceId: task.workspace_id } : undefined });
    await recordUsageImpl({userId:task.user_id,route,tokensIn:r.usage?.in ?? approxTokens(task.prompt),tokensOut:r.usage?.out ?? approxTokens(r.content)});
    return { status: "ok", result: r.content || "" };
  } catch (e) {
    return { status: "error", result: String(e.message || e) };
  } finally {
    clearTimeout(to);
  }
}
