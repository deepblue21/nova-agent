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
export const RATE_SCRIPT = `
local n = redis.call('INCR', KEYS[1])
if redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
return {n, redis.call('PTTL', KEYS[1])}
`;
export async function rateLimit(subject, max, windowMs, { client, now = Date.now() } = {}) {
  if (max <= 0) return { allowed: true, count: 0, limit: max };
  const redis = client || getRedis();
  const key = rlBucket(subject, windowMs, now);
  const remaining = Math.max(1, windowMs - (now % windowMs));
  const [n, ttl] = await redis.eval(RATE_SCRIPT, 1, key, remaining);
  return { allowed: n <= max, count: n, limit: max, retryAfterMs: Math.max(1, ttl) };
}
