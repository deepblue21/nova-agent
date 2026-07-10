// Scheduled task execution. Kept outside gateway.mjs so the agent/direct-chat
// split is unit-testable without starting an HTTP server.
import { runAgent } from "./agent.mjs";
import { makeUsageAccumulator } from "./tokens.mjs";
import { routeModel } from "./providers.mjs";

export async function runScheduledTask(task, {
  defaultModel = "ollama/qwen3:14b",
  ollamaBase = "http://localhost:11434",
  providerClient,
  timeoutMs = 60000,
  maxRetries = 2,
  runAgentImpl = runAgent,
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
      return { status: "ok", result: text || "" };
    }
    const r = await runAgentImpl({ ollamaBase, model, messages, signal: ctrl.signal, userId: task.user_id });
    return { status: "ok", result: r.content || "" };
  } catch (e) {
    return { status: "error", result: String(e.message || e) };
  } finally {
    clearTimeout(to);
  }
}
