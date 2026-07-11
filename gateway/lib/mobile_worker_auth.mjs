import { timingSafeEqual } from "node:crypto";

function equal(left, right) {
  const a = Buffer.from(String(left));
  const b = Buffer.from(String(right));
  return a.length === b.length && timingSafeEqual(a, b);
}

export function createWorkerAuth({ enabled, token }) {
  return (req, res, next) => {
    if (!enabled) return res.status(404).end();
    const authorization = req.get?.("Authorization") ?? req.headers?.authorization ?? "";
    const bearer = /^Bearer\s+(.+)$/i.exec(authorization)?.[1] || "";
    if (!token || !equal(bearer, token)) return res.status(401).json({ error: "worker unauthorized" });
    next();
  };
}
