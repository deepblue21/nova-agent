// Prometheus metrics. Scrape GET /metrics (wire metricsHandler in gateway.mjs).
import client from "prom-client";

export const registry = new client.Registry();
client.collectDefaultMetrics({ register: registry });

export const httpRequests = new client.Counter({
  name: "nova_http_requests_total",
  help: "Total HTTP requests",
  labelNames: ["method", "route", "status"],
  registers: [registry],
});

export const httpDuration = new client.Histogram({
  name: "nova_http_request_duration_seconds",
  help: "HTTP request duration in seconds",
  labelNames: ["method", "route", "status"],
  buckets: [0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 30],
  registers: [registry],
});

export const llmTokens = new client.Counter({
  name: "nova_llm_tokens_total",
  help: "LLM tokens by route and direction",
  labelNames: ["route", "direction"],   // direction: in | out
  registers: [registry],
});

// Pure: collapse high-cardinality path segments (uuids, numbers) into labels.
export function routeLabel(path) {
  return String(path || "")
    .replace(/\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, "/:id")
    .replace(/\/\d+/g, "/:n");
}

export function metricsMiddleware() {
  return (req, res, next) => {
    const stop = httpDuration.startTimer();
    res.on("finish", () => {
      const labels = { method: req.method, route: routeLabel(req.path), status: res.statusCode };
      httpRequests.inc(labels);
      stop(labels);
    });
    next();
  };
}

export async function metricsHandler(_req, res) {
  res.setHeader("Content-Type", registry.contentType);
  res.end(await registry.metrics());
}
