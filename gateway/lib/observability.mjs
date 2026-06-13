// Structured logging + per-request id. Replaces ad-hoc console.log.
import pino from "pino";
import { randomUUID } from "node:crypto";

export const logger = pino({
  level: process.env.LOG_LEVEL || "info",
  redact: ["req.headers.authorization", "req.headers.cookie", "req.headers['x-api-key']"],
});

// Express middleware: attach req.id + a child logger, log completion with latency.
export function requestLogger() {
  return (req, res, next) => {
    const id = String(req.headers["x-request-id"] || randomUUID());
    req.id = id;
    res.setHeader("x-request-id", id);
    req.log = logger.child({ reqId: id, method: req.method, path: req.path });
    const start = process.hrtime.bigint();
    res.on("finish", () => {
      const ms = Number(process.hrtime.bigint() - start) / 1e6;
      req.log.info(
        { status: res.statusCode, ms: Math.round(ms), route: res.getHeader("x-nova-route") || undefined },
        "request");
    });
    next();
  };
}
