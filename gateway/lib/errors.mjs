// Opt-in error reporting. Disabled unless ERROR_WEBHOOK_URL is set — then errors
// are POSTed (fire-and-forget JSON) to any sink: a Sentry relay, a Slack/Discord
// webhook, or a custom collector. Zero-dependency; never throws into requests.

export function errorReportingConfig(env = process.env) {
  return {
    enabled: !!env.ERROR_WEBHOOK_URL,
    url: env.ERROR_WEBHOOK_URL || "",
    service: env.OTEL_SERVICE_NAME || "nova-gateway",
    environment: env.NODE_ENV || "development",
    timeoutMs: Math.max(1, parseInt(env.ERROR_WEBHOOK_TIMEOUT_MS || "3000", 10) || 3000),
  };
}

// Pure: build the JSON event for an error (easy to unit-test).
export function buildErrorEvent(err, ctx = {}, cfg = {}) {
  const e = err || {};
  return {
    service: cfg.service || "nova-gateway",
    environment: cfg.environment || "development",
    level: "error",
    name: e.name || "Error",
    message: String((e && e.message) || e || "unknown error").slice(0, 500),
    stack: typeof e.stack === "string" ? e.stack.slice(0, 4000) : undefined,
    reqId: ctx.reqId,
    method: ctx.method,
    path: ctx.path,
    status: ctx.status,
    ts: new Date().toISOString(),
  };
}

export function createErrorReporter(env = process.env, fetchFn = globalThis.fetch) {
  const cfg = errorReportingConfig(env);
  return {
    enabled: cfg.enabled,
    report(err, ctx = {}) {
      if (!cfg.enabled) return;
      try {
        const ctrl = new AbortController();
        const t = setTimeout(() => ctrl.abort(), cfg.timeoutMs);
        Promise.resolve(
          fetchFn(cfg.url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(buildErrorEvent(err, ctx, cfg)),
            signal: ctrl.signal,
          }),
        ).catch(() => {}).finally(() => clearTimeout(t));
      } catch { /* never throw into the request path */ }
    },
  };
}
