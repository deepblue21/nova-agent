// ============================================================
//  NOVA Gateway  —  single endpoint, multi-LLM router
//  OpenAI-compatible /v1/chat/completions  ->  Anthropic | Gemini | OpenAI | Ollama
//
//  Setup (Node 18+):
//    cd gateway && npm install
//    cp .env.example .env   # then fill in your keys / secret
//    npm start              # loads .env automatically (zero-dep loader)
//
//  UI "Gateway" base URL:  http://localhost:8088/v1
//  Model name format:  <provider>/<model>
//    ollama/qwen3:14b · gemini/gemini-2.5-flash
//    anthropic/claude-sonnet-4-20250514 · openai/gpt-4o-mini
//    auto  ->  DEFAULT_MODEL, image requests -> VISION_MODEL
//
//  Security (see README "Security" section):
//    GATEWAY_TOKEN   required bearer token (STRONGLY recommended; blank = open)
//    ALLOW_ORIGINS   CORS allowlist (comma list, "*" = any — dev only)
//    RATE_MAX        requests / window per IP (0 disables)
//    ALLOW_MODELS    optional model allowlist
// ============================================================

import express from "express";
import cors from "cors";
import { timingSafeEqual } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { principal } from "./lib/auth.mjs";
import { rateLimit as distributedRateLimit } from "./lib/cache.mjs";
import { checkQuota, recordUsage, approxTokens } from "./lib/usage.mjs";
import { appendMessage, getConversation } from "./lib/persistence.mjs";
import { requestLogger, logger } from "./lib/observability.mjs";
import { metricsMiddleware, metricsHandler, llmTokens } from "./lib/metrics.mjs";
import { flushUsage } from "./lib/billing.mjs";
import { makeUsageAccumulator } from "./lib/tokens.mjs";
import { runAgent } from "./lib/agent.mjs";
import { hasImageContent, pickDynamicModel } from "./lib/routing.mjs";
import { normalizeSttPayload, normalizeTtsPayload, synthesizeSpeech, transcribeAudio, voiceLimitsFromEnv } from "./lib/voice.mjs";
import { createVoiceQueue } from "./lib/voice_queue.mjs";
import { imageInputConfig, resolveImageInputs } from "./lib/image_inputs.mjs";
import { createProviderClient, emit, finish, messageText, pickParams, routeModel, sse } from "./lib/providers.mjs";
import { history } from "./routes/history.mjs";
import { media } from "./routes/media.mjs";
import { admin } from "./routes/admin.mjs";
import { usage as usageRoutes } from "./routes/usage.mjs";
import { knowledge } from "./routes/knowledge.mjs";
import { createTracer } from "./lib/tracing.mjs";
import { scheduled } from "./routes/scheduled.mjs";
import { memory } from "./routes/memory.mjs";
import { withMemory } from "./lib/memory_store.mjs";
import { getMcpTools, describeTools, parseServers } from "./lib/mcp.mjs";
import { workspaces } from "./routes/workspaces.mjs";
import * as schedStore from "./lib/scheduled_store.mjs";
import { nextRunAt as schedNextRunAt } from "./lib/scheduler.mjs";
import { createErrorReporter } from "./lib/errors.mjs";
import { runTeam, parsePlan, mapLimit } from "./lib/multiagent.mjs";
import { estimateCostMicros } from "./lib/pricing.mjs";

// ---------- zero-dependency .env loader ----------
// Loads KEY=VALUE pairs from ./.env into process.env WITHOUT overwriting
// values already present in the real environment. No external dependency,
// so secrets never need to be hard-coded in source.
(function loadEnv() {
  try {
    const here = dirname(fileURLToPath(import.meta.url));
    const txt = readFileSync(resolve(here, ".env"), "utf8");
    for (const raw of txt.split("\n")) {
      const line = raw.trim();
      if (!line || line.startsWith("#")) continue;
      const eq = line.indexOf("=");
      if (eq < 0) continue;
      const key = line.slice(0, eq).trim();
      let val = line.slice(eq + 1).trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      if (key && !(key in process.env)) process.env[key] = val;
    }
  } catch { /* no .env file — rely on real environment */ }
})();

const app = express();
app.disable("x-powered-by");
app.set("trust proxy", process.env.TRUST_PROXY === "1" ? 1 : false);

const KEYS = {
  anthropic: process.env.ANTHROPIC_API_KEY || "",
  gemini:    process.env.GEMINI_API_KEY    || "",
  openai:    process.env.OPENAI_API_KEY    || "",
};
const OLLAMA  = process.env.OLLAMA_URL    || "http://localhost:11434";
const DEFAULT = process.env.DEFAULT_MODEL || "ollama/qwen3:14b";
const VISION_MODEL = process.env.VISION_MODEL || "ollama/qwen3.5-omni:latest";
const PORT    = process.env.PORT          || 8088;

// OpenClaw agent layer (its own API — token-protected rooms/agents)
const OPENCLAW_URL   = process.env.OPENCLAW_URL   || "http://localhost:3000";
const OPENCLAW_TOKEN = process.env.OPENCLAW_TOKEN || "";
// Message-send path; {agent} placeholder. Adjust per OpenClaw version.
const OPENCLAW_PATH  = process.env.OPENCLAW_PATH  || "/api/agents/{agent}/messages";

// Voice layer — OpenAI-compatible STT/TTS servers (faster-whisper-server, openedai-speech, Kokoro, etc.)
const WHISPER_URL   = process.env.WHISPER_URL   || "http://localhost:8000/v1/audio/transcriptions";
const WHISPER_MODEL = process.env.WHISPER_MODEL || "Systran/faster-whisper-small";
const TTS_URL       = process.env.TTS_URL       || "http://localhost:8001/v1/audio/speech";
const TTS_MODEL     = process.env.TTS_MODEL     || "tts-1";
const TTS_VOICE     = process.env.TTS_VOICE     || "alloy";
const TTS_FORMAT    = process.env.TTS_FORMAT    || "wav";   // wav = en net; mp3 boğuk olabilir (opus/flac/mp3 de olur)

// --- robustness / security knobs ---
const GATEWAY_TOKEN = process.env.GATEWAY_TOKEN || "";                 // blank = auth OFF
const tracer = createTracer();   // opt-in OTLP tracing; no-op unless OTEL_* env set
const errorReporter = createErrorReporter();   // opt-in; no-op unless ERROR_WEBHOOK_URL set
const TEAM_CONCURRENCY = parseInt(process.env.TEAM_CONCURRENCY || "3", 10);   // çoklu ajan paralel alt-görev sınırı
const TIMEOUT_MS    = parseInt(process.env.REQ_TIMEOUT_MS || "60000", 10);
const MAX_RETRIES   = parseInt(process.env.MAX_RETRIES || "2", 10);    // on 429/5xx/network error
const ALLOW         = (process.env.ALLOW_MODELS || "").split(",").map(s => s.trim()).filter(Boolean);
const BODY_LIMIT    = process.env.BODY_LIMIT || "25mb";
const MAX_MESSAGES  = parseInt(process.env.MAX_MESSAGES || "400", 10);
const MAX_MESSAGE_CHARS = parseInt(process.env.MAX_MESSAGE_CHARS || "100000", 10);
const PROD          = process.env.NODE_ENV === "production";
const VOICE_CONFIG  = {
  whisperUrl: WHISPER_URL,
  whisperModel: WHISPER_MODEL,
  ttsUrl: TTS_URL,
  ttsModel: TTS_MODEL,
  ttsVoice: TTS_VOICE,
  ttsFormat: TTS_FORMAT,
  ...voiceLimitsFromEnv(process.env),
};
const IMAGE_CONFIG = imageInputConfig(process.env);

const voiceJobs = createVoiceQueue({
  env: process.env,
  logger,
  handlers: {
    stt: (payload) => transcribeAudio(payload, VOICE_CONFIG),
    tts: async (payload) => {
      const out = await synthesizeSpeech(payload, VOICE_CONFIG);
      return { mime: out.mime, audio: out.buffer.toString("base64") };
    },
  },
});
const providerClient = createProviderClient({
  keys: KEYS,
  ollamaUrl: OLLAMA,
  openclawUrl: OPENCLAW_URL,
  openclawToken: OPENCLAW_TOKEN,
  openclawPath: OPENCLAW_PATH,
  maxRetries: MAX_RETRIES,
});

// CORS allowlist. Default to the Vite dev/preview origins. "*" = any (dev only).
const ORIGINS = (process.env.ALLOW_ORIGINS ||
  "http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173")
  .split(",").map(s => s.trim()).filter(Boolean);
const ANY_ORIGIN = ORIGINS.includes("*");

// Per-IP fixed-window rate limit.
const RATE_WINDOW_MS = parseInt(process.env.RATE_WINDOW_MS || "60000", 10);
const RATE_MAX       = parseInt(process.env.RATE_MAX || "120", 10);   // 0 disables
const MULTI_USER     = !!process.env.DATABASE_URL && process.env.MULTI_USER !== "0";
const BILLING_FLUSH_MS = parseInt(process.env.BILLING_FLUSH_MS || "3600000", 10);

// --- production preflight: fail fast on insecure configuration ---
//  In NODE_ENV=production the gateway refuses to start if it would be
//  unauthenticated or open to any origin. This turns the README's "set these
//  before exposing" advice into an enforced invariant (Phase 0 hardening).
if (PROD) {
  const fatal = [];
  if (!MULTI_USER && !GATEWAY_TOKEN) fatal.push("GATEWAY_TOKEN is empty (auth would be disabled)");
  if (!MULTI_USER && GATEWAY_TOKEN && GATEWAY_TOKEN.length < 24) fatal.push("GATEWAY_TOKEN is too short (<24 chars) — use `openssl rand -hex 32`");
  if (ANY_ORIGIN)     fatal.push("ALLOW_ORIGINS='*' (any website could call this gateway)");
  if (process.env.OIDC_JWKS_URL && !process.env.OIDC_ISSUER) fatal.push("OIDC_JWKS_URL is set but OIDC_ISSUER is empty");
  if (process.env.HEALTH_DETAILS_ENABLED === "1") fatal.push("HEALTH_DETAILS_ENABLED=1 would expose runtime details on public /health");
  if (fatal.length) {
    console.error("FATAL (NODE_ENV=production): " + fatal.join("; ") + ".");
    console.error("Set them in gateway/.env before starting in production.");
    process.exit(1);
  }
}

// constant-time string compare (avoids token timing leaks)
function safeEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return timingSafeEqual(ba, bb);
}

// keep upstream error text out of client responses in production
const clientErr = (msg) => PROD ? "upstream error" : String(msg);

function isPublicPath(req) {
  return req.path === "/health" || req.path === "/metrics";
}

function validateMessages(messages) {
  let totalText = 0;
  for (const m of messages) {
    if (!m || typeof m !== "object") return "each message must be an object";
    if (!["system", "user", "assistant", "tool"].includes(String(m.role || ""))) return "invalid message role";
    const text = messageText(m);
    totalText += Buffer.byteLength(text, "utf8");
    if (totalText > MAX_MESSAGE_CHARS) return "messages text too large (max " + MAX_MESSAGE_CHARS + " bytes)";
  }
  return "";
}

async function recordChatCompletion(req, { route, model, messages, assistantText, usage }) {
  // Prefer REAL usage reported by the provider stream; fall back to the
  // ~4 chars/token estimate only when the provider sent no usage metadata.
  const real = usage && usage.seen() ? usage.get() : null;
  const tokensIn = real ? real.in : approxTokens(messages.map(messageText).join("\n"));
  const tokensOut = real ? real.out : approxTokens(assistantText || "");
  llmTokens.inc({ route, direction: "in" }, tokensIn);
  llmTokens.inc({ route, direction: "out" }, tokensOut);

  if (!req.principal) return;

  try {
    await recordUsage({ userId: req.principal.userId, route, tokensIn, tokensOut });

    const conversationId = req.body?.conversation_id;
    if (!conversationId) return;

    const conversation = await getConversation(req.principal.userId, conversationId);
    if (!conversation) {
      req.log?.warn?.({ conversationId }, "conversation not found for persistence");
      return;
    }

    const lastUserText = messageText([...messages].reverse().find(m => m.role === "user"));
    if (lastUserText) await appendMessage(conversationId, { role: "user", content: lastUserText });
    if (assistantText) {
      await appendMessage(conversationId, {
        role: "assistant",
        content: assistantText,
        model,
        route,
        tokens_in: tokensIn,
        tokens_out: tokensOut,
      });
    }
  } catch (e) {
    req.log?.error?.({ err: e.message }, "usage persistence failed");
  }
}

// ============================================================
//  Security middleware  (order matters)
// ============================================================

// 1) baseline security response headers
app.use((req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
  next();
});

// 2) CORS allowlist (no Origin header = non-browser client → allowed)
app.use(requestLogger());
app.use(metricsMiddleware());

app.use(cors({
  origin(origin, cb) {
    if (!origin || ANY_ORIGIN || ORIGINS.includes(origin)) return cb(null, true);
    return cb(null, false);          // not allowlisted → browser blocks (no 500)
  },
  exposedHeaders: ["x-nova-route"],
  credentials: false,
  maxAge: 600,
}));

// 3) JSON body parser with size cap
app.use(express.json({ limit: BODY_LIMIT }));
app.use((err, _req, res, next) => {
  if (!err) return next();
  if (err.type === "entity.too.large") return res.status(413).json({ error: "request body too large" });
  if (err instanceof SyntaxError) return res.status(400).json({ error: "invalid JSON body" });
  return next(err);
});

// 4) per-IP fixed-window rate limit (skips /health)
const hits = new Map();
if (RATE_MAX > 0) {
  const t = setInterval(() => {
    const now = Date.now();
    for (const [ip, e] of hits) if (now > e.reset) hits.delete(ip);
  }, RATE_WINDOW_MS);
  if (t.unref) t.unref();
}
app.use((req, res, next) => {
  if (MULTI_USER || RATE_MAX <= 0 || isPublicPath(req)) return next();
  const ip = req.ip || req.socket?.remoteAddress || "unknown";
  const now = Date.now();
  let e = hits.get(ip);
  if (!e || now > e.reset) { e = { count: 0, reset: now + RATE_WINDOW_MS }; hits.set(ip, e); }
  e.count++;
  if (e.count > RATE_MAX) {
    res.setHeader("Retry-After", String(Math.ceil((e.reset - now) / 1000)));
    return res.status(429).json({ error: "rate limit exceeded" });
  }
  next();
});

// 5) Auth. Phase 1/2 mode uses per-user API keys/JWTs; legacy local mode keeps GATEWAY_TOKEN.
const principalMiddleware = principal();
app.use((req, res, next) => {
  if (isPublicPath(req)) return next();
  if (MULTI_USER) return principalMiddleware(req, res, next);
  if (!GATEWAY_TOKEN) return next();
  const auth = req.headers.authorization || "";
  if (auth.startsWith("Bearer ") && safeEqual(auth.slice(7), GATEWAY_TOKEN)) return next();
  res.status(401).json({ error: "unauthorized (GATEWAY_TOKEN required)" });
});

if (MULTI_USER) {
  app.use(history);
  app.use(media);
  app.use(admin); // /v1/admin/* — guarded by ADMIN_USER_IDS inside the router
  app.use(usageRoutes); // GET /v1/usage — kendi kullanım/kota görünümü
  app.use(knowledge);   // /v1/knowledge — RAG belge yükle/listele/sil
  app.use(scheduled);   // /v1/scheduled — zamanlanmış/otomatik ajan görevleri
  app.use(memory);      // /v1/memory — kişisel uzun-dönem hafıza (oto-hatırlama)
  app.use(workspaces);  // /v1/workspaces — çalışma alanı + RBAC (3 rol)
}

// ---------- Endpoints ----------
app.get("/health", (_req, res) => {
  if (PROD || process.env.HEALTH_DETAILS_ENABLED !== "1") return res.json({ ok: true });
  res.json({
    ok: true,
    default: DEFAULT,
    vision: VISION_MODEL,
    voice_queue: voiceJobs.enabled,
    remote_images: IMAGE_CONFIG.remoteEnabled,
  });
});
app.get("/metrics", metricsHandler);

app.get("/v1/models", (_req, res) => {
  res.json({ data: [
    { id: "auto" }, { id: "openclaw/default" },
    { id: "ollama/qwen3.5-9b-agent:latest" },
    { id: "ollama/titus-cyber:latest" },
    { id: "ollama/gemma4:latest" }, { id: "ollama/gemma4:e4b" }, { id: "ollama/gemma4:e2b" },
    { id: "ollama/qwen3.5-omni:latest" },
    { id: "ollama/qwen3:14b" }, { id: "ollama/qwen3.5:9b" },
    { id: "gemini/gemini-2.5-pro" }, { id: "gemini/gemini-2.5-flash" },
    { id: "anthropic/claude-sonnet-4-20250514" }, { id: "openai/gpt-4o-mini" },
  ]});
});

// Çoklu ajan: görevi paralel alt-görevlere böldürür (yerel model). null → tek ajana düş.
async function planSubtasks(model, task, signal) {
  const planMsg = [
    { role: "system", content: "Verilen görevi 2-4 paralel alt-göreve böl. SADECE bir JSON dizisi döndür, başka metin yazma: [{\"role\":\"kısa etiket\",\"prompt\":\"o alt-ajanın yapacağı net iş\"}]." },
    { role: "user", content: task },
  ];
  try {
    const r = await fetch(OLLAMA.replace(/\/$/, "") + "/api/chat", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages: planMsg, stream: false }), signal,
    });
    if (!r.ok) return null;
    const d = await r.json();
    return parsePlan((d.message && d.message.content) || "");
  } catch { return null; }
}

app.post("/v1/chat/completions", async (req, res) => {
  let { messages = [], stream = false, think = false, effort, agent = false, team = false } = req.body || {};
  // input validation
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: "messages must be a non-empty array" });
  }
  if (messages.length > MAX_MESSAGES) {
    return res.status(413).json({ error: "too many messages (max " + MAX_MESSAGES + ")" });
  }
  const invalid = validateMessages(messages);
  if (invalid) return res.status(invalid.includes("too large") ? 413 : 400).json({ error: invalid });
  // "auto" / empty → dynamic routing decides by effort + context
  let modelStr = req.body?.model;
  if (!modelStr || modelStr === "auto") {
    modelStr = pickDynamicModel({
      effort,
      messages,
      keys: KEYS,
      defaultModel: DEFAULT,
      visionModel: VISION_MODEL,
      env: process.env,
    });
  }
  const { provider, model } = routeModel(modelStr, DEFAULT);
  const full = provider + "/" + model;
  if (ALLOW.length && !ALLOW.includes(full) && !ALLOW.includes(provider + "/*")) {
    return res.status(403).json({ error: "model not allowed: " + full });
  }
  res.setHeader("x-nova-route", full);
  const span = tracer.startSpan("chat", { "nova.route": full, "nova.provider": provider, "nova.model": model, "nova.agent": !!agent, "nova.stream": !!stream });
  res.on("finish", () => span.end({ "http.status_code": res.statusCode }, res.statusCode >= 500 ? 2 : 1));

  if (req.principal) {
    try {
      const rl = await distributedRateLimit(req.principal.userId, RATE_MAX, RATE_WINDOW_MS);
      if (!rl.allowed) {
        res.setHeader("Retry-After", String(Math.ceil(rl.retryAfterMs / 1000)));
        return res.status(429).json({ error: "rate limit exceeded" });
      }

      const quota = await checkQuota(req.principal.userId);
      if (!quota.allowed) {
        return res.status(402).json({ error: "quota exceeded", used: quota.used, limit: quota.limit });
      }
    } catch (e) {
      req.log?.error?.({ err: e.message }, "quota or rate-limit check failed");
      return res.status(503).json({ error: "quota or rate-limit unavailable" });
    }
  }

  // cancel upstream if client disconnects + enforce timeout.
  // NB: use res "close" (fires when the RESPONSE ends or the client drops the
  // connection). req "close" fires as soon as the POST body is read — before we
  // respond — which would abort every upstream call instantly.
  const up = new AbortController();
  const to = setTimeout(() => up.abort(), TIMEOUT_MS);
  res.on("close", () => { if (!res.writableEnded) up.abort(); });
  const usage = makeUsageAccumulator(provider);
  const ctx = { signal: up.signal, think, params: pickParams(req.body), retries: MAX_RETRIES, usage };
  try {
    let assistantText;
    // kişisel uzun-dönem hafızayı sistem prompt'una otomatik kat (multi-user; hata-toleranslı)
    if (req.principal) messages = await withMemory(messages, req.principal.userId);
    // --- AJAN MODU: yerel modelle araç çağırma döngüsü (web arama, hesap, saat) ---
    // --- ÇOKLU AJAN (TEAM): planla → paralel alt-ajanlar → sentez ---
    if (team && provider === "ollama" && !hasImageContent(messages)) {
      if (stream) sse(res);
      const task = messageText(messages[messages.length - 1]) || "";
      let subtasks = await planSubtasks(model, task, up.signal);
      if (!subtasks || !subtasks.length) subtasks = [{ role: "ajan", prompt: task }];
      if (stream) for (const st of subtasks)
        res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: "subtask", args: { role: st.role } } } }] }) + "\n\n");
      const sysMsg = messages.find(m => m.role === "system");
      const baseSys = (sysMsg && sysMsg.content) || "Sen NOVA'nın bir alt-ajanısın; verilen alt-görevi net ve eksiksiz yerine getir.";
      const uid = req.principal && req.principal.userId;
      const mcp = await getMcpTools(up.signal);
      const runOne = (prompt) => runAgent({ ollamaBase: OLLAMA, model, messages: [{ role: "system", content: baseSys }, { role: "user", content: prompt }], signal: up.signal, userId: uid, extraTools: mcp.specs, extraDispatch: mcp.dispatch });
      const synthesize = (synthPrompt) => runAgent({ ollamaBase: OLLAMA, model, messages: [{ role: "user", content: synthPrompt }], signal: up.signal, userId: uid });
      const teamOut = await runTeam({
        task, subtasks, runOne, synthesize, concurrency: TEAM_CONCURRENCY,
        onResult: (out) => { if (stream) res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: "subtask", args: { role: out.role }, done: true } } }] }) + "\n\n"); },
        onSynthesize: () => { if (stream) res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: "synthesis" } } }] }) + "\n\n"); },
      });
      if (stream) res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: "synthesis", done: true } } }] }) + "\n\n");
      let text = (teamOut.synthesis && teamOut.synthesis.content) || "";
      if (teamOut.sources && teamOut.sources.length) {
        text += "\n\n**Kaynaklar:**\n" + teamOut.sources.map(s => (s.url ? `- [${s.n}] [${s.title || s.url}](${s.url})` : `- ${s.title || "Kaynak"}`)).join("\n");
      }
      if (stream) { emit(res, text); finish(res); }
      else res.json({ choices: [{ message: { role: "assistant", content: text } }], nova_team: subtasks.map(s => s.role) });
      assistantText = text;
      await recordChatCompletion(req, { route: full + " (team)", model, messages, assistantText, usage });
      return;
    }
    if (agent && provider === "ollama" && !hasImageContent(messages)) {
      if (stream) sse(res);
      const mcp = await getMcpTools(up.signal);
      const r = await runAgent({
        ollamaBase: OLLAMA, model, messages, signal: up.signal,
        userId: req.principal && req.principal.userId,
        extraTools: mcp.specs, extraDispatch: mcp.dispatch,
        onStep: (ev) => {
          if (!stream) return;
          if (ev.type === "tool_call")
            res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: ev.name, args: ev.args } } }] }) + "\n\n");
          if (ev.type === "tool_result")
            res.write("data: " + JSON.stringify({ choices: [{ delta: { tool_step: { name: ev.name, done: true, sources: ev.sources || [] } } }] }) + "\n\n");
        },
      });
      let text = r.content || "";
      if (r.sources && r.sources.length) {
        text += "\n\n**Kaynaklar:**\n" + r.sources.map(s => (
          s.url
            ? `- [${s.n}] [${s.title || s.url}](${s.url})`
            : `- [${s.n}] ${s.title || "Belge"}${s.score ? ` (${Math.round(s.score * 100)}%)` : ""}`
        )).join("\n");
      }
      if (stream) { emit(res, text); finish(res); }
      else res.json({ choices: [{ message: { role: "assistant", content: text } }], nova_tools: r.toolsUsed });
      assistantText = text;
      await recordChatCompletion(req, { route: full + " (agent)", model, messages, assistantText, usage });
      return;
    }
    const providerMessages = (provider === "ollama" || provider === "gemini" || provider === "anthropic")
      ? await resolveImageInputs(messages, IMAGE_CONFIG, { signal: up.signal })
      : messages;
    assistantText = await providerClient.chat({ provider, model, messages: providerMessages, stream, ctx, res });
    await recordChatCompletion(req, { route: full, model, messages, assistantText, usage });
    return;
  } catch (e) {
    if (e && e.name === "AbortError") { if (!res.writableEnded) { try { res.end(); } catch {} } return; }
    if (res.headersSent) { try { emit(res, "⚠️ " + clientErr(e.message)); finish(res); } catch {} return; }
    if (stream) { sse(res); emit(res, "⚠️ " + clientErr(e.message)); return finish(res); }
    res.status(e && e.status ? e.status : 500).json({ error: clientErr(e.message || e) });
  } finally { clearTimeout(to); }
});

// ---------- Model eval / comparison (step F) ----------
// Run one prompt against several models in parallel and return each output with
// latency + tokens + cost so the user can compare. Reuses the provider client
// (stream:false → returns text). Usage is metered like a normal chat.
const EVAL_CONCURRENCY = Math.max(1, parseInt(process.env.EVAL_CONCURRENCY || "3", 10));
const EVAL_MAX_MODELS  = Math.max(1, parseInt(process.env.EVAL_MAX_MODELS || "6", 10));
app.post("/v1/eval", async (req, res) => {
  const body = req.body || {};
  const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
  const system = typeof body.system === "string" ? body.system.trim() : "";
  const models = Array.isArray(body.models)
    ? [...new Set(body.models.filter(m => typeof m === "string" && m.trim()).map(m => m.trim()))].slice(0, EVAL_MAX_MODELS)
    : [];
  if (!prompt) return res.status(400).json({ error: "prompt required" });
  if (!models.length) return res.status(400).json({ error: "models required" });

  if (req.principal) {
    try {
      const rl = await distributedRateLimit(req.principal.userId, RATE_MAX, RATE_WINDOW_MS);
      if (!rl.allowed) { res.setHeader("Retry-After", String(Math.ceil(rl.retryAfterMs / 1000))); return res.status(429).json({ error: "rate limit exceeded" }); }
      const quota = await checkQuota(req.principal.userId);
      if (!quota.allowed) return res.status(402).json({ error: "quota exceeded", used: quota.used, limit: quota.limit });
    } catch (e) {
      req.log?.error?.({ err: e.message }, "eval quota/rate check failed");
      return res.status(503).json({ error: "quota or rate-limit unavailable" });
    }
  }

  const baseMessages = system ? [{ role: "system", content: system }] : [];
  baseMessages.push({ role: "user", content: prompt });
  const up = new AbortController();
  const to = setTimeout(() => up.abort(), TIMEOUT_MS);
  res.on("close", () => { if (!res.writableEnded) up.abort(); });
  try {
    const results = await mapLimit(models, EVAL_CONCURRENCY, async (modelStr) => {
      const { provider, model } = routeModel(modelStr, DEFAULT);
      const full = provider + "/" + model;
      if (ALLOW.length && !ALLOW.includes(full) && !ALLOW.includes(provider + "/*"))
        return { model: full, ok: false, error: "model not allowed" };
      const usage = makeUsageAccumulator(provider);
      const ctx = { signal: up.signal, params: pickParams(body), retries: MAX_RETRIES, usage };
      const t0 = Date.now();
      try {
        const text = await providerClient.chat({ provider, model, messages: baseMessages, stream: false, ctx, res: null });
        const real = usage.seen() ? usage.get() : null;
        const tokensIn  = real ? real.in  : approxTokens(prompt);
        const tokensOut = real ? real.out : approxTokens(text);
        if (req.principal) { try { await recordUsage({ userId: req.principal.userId, route: full + " (eval)", tokensIn, tokensOut }); } catch {} }
        return { model: full, ok: true, content: text, ms: Date.now() - t0, tokens_in: tokensIn, tokens_out: tokensOut, cost_micros: estimateCostMicros(full, tokensIn, tokensOut) };
      } catch (e) {
        if (e && e.name === "AbortError") return { model: full, ok: false, error: "aborted", ms: Date.now() - t0 };
        return { model: full, ok: false, error: clientErr(e && e.message ? e.message : e), ms: Date.now() - t0 };
      }
    });
    res.json({ prompt, results });
  } catch (e) {
    res.status(500).json({ error: clientErr(e && e.message ? e.message : e) });
  } finally { clearTimeout(to); }
});

// MCP tool introspection (agent deepening) — list configured servers + discovered tools.
app.get("/v1/mcp/tools", async (req, res) => {
  const up = new AbortController();
  const to = setTimeout(() => up.abort(), TIMEOUT_MS);
  try {
    const mcp = await getMcpTools(up.signal);
    res.json({ servers: parseServers(process.env.MCP_SERVERS).map((s) => s.name), tools: describeTools(mcp.specs) });
  } catch (e) {
    res.status(500).json({ error: clientErr(e && e.message ? e.message : e) });
  } finally { clearTimeout(to); }
});

function sendVoiceError(res, e) {
  res.status(e && e.status ? e.status : 500).json({ error: clientErr(e && e.message ? e.message : e) });
}

function normalizeVoiceJob(body) {
  const type = String((body && body.type) || "").toLowerCase();
  if (type === "stt") {
    const p = normalizeSttPayload(body, VOICE_CONFIG);
    return { type, data: { audio: p.audio, mime: p.mime, language: p.language } };
  }
  if (type === "tts") {
    return { type, data: normalizeTtsPayload(body, VOICE_CONFIG) };
  }
  const err = new Error("type must be stt or tts");
  err.status = 400;
  throw err;
}

// ---------- Voice jobs: BullMQ-backed async STT/TTS ----------
app.post("/v1/voice/jobs", async (req, res) => {
  try {
    if (!voiceJobs.enabled) return res.status(503).json({ error: "voice queue disabled (set VOICE_QUEUE_ENABLED=1)" });
    const job = normalizeVoiceJob(req.body || {});
    res.status(202).json(await voiceJobs.add(job.type, job.data));
  } catch (e) { sendVoiceError(res, e); }
});

app.get("/v1/voice/jobs/:id", async (req, res) => {
  try {
    if (!voiceJobs.enabled) return res.status(503).json({ error: "voice queue disabled (set VOICE_QUEUE_ENABLED=1)" });
    const job = await voiceJobs.get(req.params.id);
    if (!job) return res.status(404).json({ error: "job not found" });
    res.json(job);
  } catch (e) { sendVoiceError(res, e); }
});

app.get("/v1/voice/jobs/:id/audio", async (req, res) => {
  try {
    if (!voiceJobs.enabled) return res.status(503).json({ error: "voice queue disabled (set VOICE_QUEUE_ENABLED=1)" });
    const job = await voiceJobs.get(req.params.id);
    if (!job) return res.status(404).json({ error: "job not found" });
    if (job.state !== "completed" || job.type !== "tts" || !job.result?.audio) {
      return res.status(409).json({ error: "audio not ready" });
    }
    res.setHeader("Content-Type", job.result.mime || "audio/mpeg");
    res.send(Buffer.from(job.result.audio, "base64"));
  } catch (e) { sendVoiceError(res, e); }
});

// ---------- Voice: STT (Whisper) ----------
app.post("/stt", async (req, res) => {
  try {
    res.json(await transcribeAudio(req.body || {}, VOICE_CONFIG));
  } catch (e) { sendVoiceError(res, e); }
});

// ---------- Voice: TTS (returns audio bytes) ----------
app.post("/tts", async (req, res) => {
  try {
    const out = await synthesizeSpeech(req.body || {}, VOICE_CONFIG);
    res.setHeader("Content-Type", out.mime);
    res.send(out.buffer);
  } catch (e) { sendVoiceError(res, e); }
});

app.use((err, req, res, _next) => {
  req.log?.error?.({ err: err?.message || err }, "unhandled request error");
  errorReporter.report(err, { reqId: req.id, method: req.method, path: req.path, status: err?.status || 500 });
  if (res.headersSent) return res.end();
  res.status(err?.status || 500).json({ error: clientErr(err?.message || "internal error") });
});

if (process.env.STRIPE_SECRET_KEY && process.env.DATABASE_URL && BILLING_FLUSH_MS > 0) {
  const billingTimer = setInterval(() => {
    flushUsage()
      .then(r => logger.info(r, "billing flush"))
      .catch(e => logger.error({ err: e.message }, "billing flush failed"));
  }, BILLING_FLUSH_MS);
  if (billingTimer.unref) billingTimer.unref();
}

// ---------- Scheduled / automated agent tasks (Faz 6) ----------
// Opt-in: SCHEDULER_ENABLED=1 + multi-user + DATABASE_URL. In-process poller runs
// due tasks through the agent loop (tools available) and stores the last result.
async function runScheduledTask(task) {
  const { provider, model } = routeModel(task.model || DEFAULT, DEFAULT);
  if (provider !== "ollama") return { status: "error", result: "zamanlanmış görevler yerel (ollama) model gerektirir" };
  const messages = [
    { role: "system", content: "Sen NOVA'nın otomatik görev ajanısın. Görevi kısa, net ve eksiksiz yerine getir; gerekiyorsa araçları kullan." },
    { role: "user", content: task.prompt },
  ];
  const ctrl = new AbortController();
  const to = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const r = await runAgent({ ollamaBase: OLLAMA, model, messages, signal: ctrl.signal, userId: task.user_id });
    return { status: "ok", result: r.content || "" };
  } catch (e) {
    return { status: "error", result: String(e.message || e) };
  } finally { clearTimeout(to); }
}

if (MULTI_USER && process.env.SCHEDULER_ENABLED === "1" && process.env.DATABASE_URL) {
  const tickMs = parseInt(process.env.SCHEDULER_TICK_MS || "30000", 10);
  const tick = async () => {
    try {
      const due = await schedStore.listDue(Date.now(), 20);
      for (const task of due) {
        const out = await runScheduledTask(task);
        await schedStore.markRun(task.id, { ...out, nextRunAt: schedNextRunAt(task.schedule, Date.now()) });
        logger.info({ taskId: task.id, status: out.status }, "scheduled task ran");
      }
    } catch (e) { logger.warn({ err: e.message || e }, "scheduler tick failed"); }
  };
  const schedTimer = setInterval(tick, tickMs);
  if (schedTimer.unref) schedTimer.unref();
  logger.info({ tickMs }, "scheduler enabled");
}

app.listen(PORT, () => {
  console.log("NOVA Gateway → http://localhost:" + PORT + "/v1");
  console.log("default model:", DEFAULT, "| ollama:", OLLAMA);
  console.log("keys:",
    "anthropic=" + (KEYS.anthropic ? "✓" : "—"),
    "gemini=" + (KEYS.gemini ? "✓" : "—"),
    "openai=" + (KEYS.openai ? "✓" : "—"),
    "| openclaw=" + OPENCLAW_URL + (OPENCLAW_TOKEN ? " (token ✓)" : ""));
  console.log("guard:",
    "auth=" + (MULTI_USER ? "multi-user" : (GATEWAY_TOKEN ? "token" : "off")),
    "cors=" + (ANY_ORIGIN ? "* (any)" : ORIGINS.join(",")),
    "rate=" + (RATE_MAX > 0 ? RATE_MAX + "/" + Math.round(RATE_WINDOW_MS / 1000) + "s" : "off"),
    "timeout=" + TIMEOUT_MS + "ms",
    "retries=" + MAX_RETRIES,
    "allowlist=" + (ALLOW.length ? ALLOW.join(",") : "all"),
    "multiUser=" + (MULTI_USER ? "on" : "off"));
  if (!MULTI_USER && !GATEWAY_TOKEN) console.warn("⚠️  GATEWAY_TOKEN is empty — the gateway is UNAUTHENTICATED. Set it before exposing beyond localhost.");
  if (ANY_ORIGIN)     console.warn("⚠️  ALLOW_ORIGINS='*' — any website can call this gateway. Use a fixed allowlist outside local dev.");
});
// (tokens.mjs real-usage + admin router wired: 2026-06-10)
