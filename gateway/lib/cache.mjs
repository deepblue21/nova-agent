// Redis client + distributed fixed-window rate limit (replaces the in-process Map).
import Redis from "ioredis";

let redisClient;

export function getRedis() {
  if (!redisClient) {
    redisClient = new Redis(process.env.REDIS_URL || "redis://localhost:6379", {
      maxRetriesPerRequest: 2,
    });
    redisClient.on("error", () => {});
  }
  return redisClient;
}

export const redis = {
  incr: (...args) => getRedis().incr(...args),
  pexpire: (...args) => getRedis().pexpire(...args),
  quit: (...args) => redisClient?.quit(...args),
};

// Pure: which fixed-window bucket a request falls into (exported for tests).
export const rlBucket = (subject, windowMs, now = Date.now()) =>
  `rl:${subject}:${Math.floor(now / windowMs)}`;

// Atomic INCR; set TTL on the first hit of a window. Works across N gateway instances.
export async function rateLimit(subject, max, windowMs) {
  if (max <= 0) return { allowed: true, count: 0, limit: max };
  const redis = getRedis();
  const key = rlBucket(subject, windowMs);
  const n = await redis.incr(key);
  if (n === 1) await redis.pexpire(key, windowMs);
  return { allowed: n <= max, count: n, limit: max, retryAfterMs: windowMs };
}
