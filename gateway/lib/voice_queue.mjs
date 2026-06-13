import { Queue, Worker } from "bullmq";
import IORedis from "ioredis";

function positiveInt(value, fallback) {
  const n = Number.parseInt(String(value || ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function isVoiceQueueEnabled(env = process.env) {
  return env.VOICE_QUEUE_ENABLED === "1";
}

export function voiceQueueConfig(env = process.env) {
  return {
    enabled: isVoiceQueueEnabled(env),
    redisUrl: env.REDIS_URL || "redis://localhost:6379",
    name: env.VOICE_QUEUE_NAME || "nova_voice",
    concurrency: positiveInt(env.VOICE_QUEUE_CONCURRENCY, 2),
    attempts: positiveInt(env.VOICE_QUEUE_ATTEMPTS, 2),
    resultTtlSec: positiveInt(env.VOICE_QUEUE_RESULT_TTL_SEC, 3600),
    failedTtlSec: positiveInt(env.VOICE_QUEUE_FAILED_TTL_SEC, 86400),
    keepCompleted: positiveInt(env.VOICE_QUEUE_KEEP_COMPLETED, 100),
    keepFailed: positiveInt(env.VOICE_QUEUE_KEEP_FAILED, 100),
  };
}

export function createVoiceQueue({ env = process.env, logger = console, handlers = {} } = {}) {
  const cfg = voiceQueueConfig(env);
  if (!cfg.enabled) {
    return {
      enabled: false,
      async add() { throw new Error("voice queue disabled"); },
      async get() { return null; },
      async close() {},
    };
  }

  const connection = new IORedis(cfg.redisUrl, { maxRetriesPerRequest: null });
  connection.on("error", () => {});

  const queue = new Queue(cfg.name, {
    connection,
    defaultJobOptions: {
      attempts: cfg.attempts,
      backoff: { type: "exponential", delay: 1000 },
      removeOnComplete: { age: cfg.resultTtlSec, count: cfg.keepCompleted },
      removeOnFail: { age: cfg.failedTtlSec, count: cfg.keepFailed },
    },
  });

  const worker = new Worker(cfg.name, async (job) => {
    await job.updateProgress(10);
    if (job.name === "stt") {
      const result = await handlers.stt(job.data, job);
      await job.updateProgress(100);
      return result;
    }
    if (job.name === "tts") {
      const result = await handlers.tts(job.data, job);
      await job.updateProgress(100);
      return result;
    }
    throw new Error("unknown voice job: " + job.name);
  }, { connection, concurrency: cfg.concurrency });

  worker.on("failed", (job, err) => {
    if (logger && logger.warn) logger.warn({ jobId: job?.id, type: job?.name, err: err.message }, "voice job failed");
  });

  return {
    enabled: true,
    cfg,
    async add(type, data) {
      if (type !== "stt" && type !== "tts") throw new Error("type must be stt or tts");
      const job = await queue.add(type, data);
      return { id: String(job.id), type, state: "queued", status_url: "/v1/voice/jobs/" + job.id };
    },
    async get(id) {
      const job = await queue.getJob(id);
      if (!job) return null;
      const state = await job.getState();
      const out = {
        id: String(job.id),
        type: job.name,
        state,
        progress: job.progress || 0,
      };
      if (state === "completed") out.result = job.returnvalue || null;
      if (state === "failed") out.error = job.failedReason || "job failed";
      return out;
    },
    async close() {
      await worker.close();
      await queue.close();
      await connection.quit();
    },
  };
}
