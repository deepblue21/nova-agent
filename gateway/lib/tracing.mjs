// Zero-dependency, opt-in OpenTelemetry trace export over OTLP/HTTP (JSON).
// Disabled unless OTEL_EXPORTER_OTLP_ENDPOINT (or ..._TRACES_ENDPOINT) is set, so
// it has zero cost and pulls no SDK in the default local-first setup. Spans are
// fire-and-forget POSTs; export errors never touch the request path.
import { randomBytes } from "node:crypto";

export function tracingConfig(env = process.env) {
  const explicit = env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT;
  const base = env.OTEL_EXPORTER_OTLP_ENDPOINT;
  const endpoint = explicit
    ? explicit
    : (base ? base.replace(/\/+$/, "") + "/v1/traces" : "");
  return {
    enabled: !!endpoint,
    endpoint,
    serviceName: env.OTEL_SERVICE_NAME || "nova-gateway",
    timeoutMs: Math.max(1, parseInt(env.OTEL_EXPORTER_TIMEOUT_MS || "3000", 10) || 3000),
  };
}

// OTLP/JSON attribute value typing.
function attrValue(v) {
  if (typeof v === "boolean") return { boolValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { intValue: String(v) } : { doubleValue: v };
  return { stringValue: String(v) };
}

export function toAttributes(obj = {}) {
  return Object.entries(obj)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([key, v]) => ({ key, value: attrValue(v) }));
}

// Pure: build the OTLP/HTTP JSON body for a single span (easy to unit-test).
export function buildOtlpSpan({
  serviceName = "nova-gateway",
  name,
  traceId,
  spanId,
  startTimeUnixNano,
  endTimeUnixNano,
  attributes = {},
  statusCode = 0,   // 0=unset, 1=OK, 2=ERROR
  kind = 2,         // SPAN_KIND_SERVER
}) {
  return {
    resourceSpans: [{
      resource: { attributes: toAttributes({ "service.name": serviceName }) },
      scopeSpans: [{
        scope: { name: serviceName },
        spans: [{
          traceId,
          spanId,
          name,
          kind,
          startTimeUnixNano: String(startTimeUnixNano),
          endTimeUnixNano: String(endTimeUnixNano),
          attributes: toAttributes(attributes),
          status: statusCode ? { code: statusCode } : {},
        }],
      }],
    }],
  };
}

const nowUnixNano = () => BigInt(Date.now()) * 1000000n;

// createTracer({env}, fetchFn?) → { enabled, startSpan(name, attrs) → { end(extra, statusCode) } }
export function createTracer(env = process.env, fetchFn = globalThis.fetch) {
  const cfg = tracingConfig(env);
  const noopSpan = { end() {} };

  function startSpan(name, attributes = {}) {
    if (!cfg.enabled) return noopSpan;
    const traceId = randomBytes(16).toString("hex");
    const spanId = randomBytes(8).toString("hex");
    const start = nowUnixNano();
    let ended = false;
    return {
      end(extra = {}, statusCode = 1) {
        if (ended) return;
        ended = true;
        const payload = buildOtlpSpan({
          serviceName: cfg.serviceName,
          name,
          traceId,
          spanId,
          startTimeUnixNano: start,
          endTimeUnixNano: nowUnixNano(),
          attributes: { ...attributes, ...extra },
          statusCode,
        });
        try {
          const ctrl = new AbortController();
          const t = setTimeout(() => ctrl.abort(), cfg.timeoutMs);
          Promise.resolve(fetchFn(cfg.endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
            signal: ctrl.signal,
          })).catch(() => {}).finally(() => clearTimeout(t));
        } catch { /* never throw into the request path */ }
      },
    };
  }

  return { enabled: cfg.enabled, startSpan, config: cfg };
}
