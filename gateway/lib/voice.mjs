export class VoiceError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.name = "VoiceError";
    this.status = status;
  }
}

const DEFAULT_MAX_AUDIO_BYTES = 25 * 1024 * 1024;
const DEFAULT_MAX_TTS_CHARS = 8000;

function positiveInt(value, fallback) {
  const n = Number.parseInt(String(value || ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function stripDataUrl(value) {
  const s = String(value || "").trim();
  const comma = s.indexOf(",");
  return comma >= 0 && /^data:/i.test(s.slice(0, comma)) ? s.slice(comma + 1) : s;
}

export function voiceLimitsFromEnv(env = process.env) {
  return {
    maxAudioBytes: positiveInt(env.VOICE_QUEUE_MAX_AUDIO_BYTES || env.MAX_AUDIO_BYTES, DEFAULT_MAX_AUDIO_BYTES),
    maxTtsChars: positiveInt(env.TTS_MAX_INPUT_CHARS, DEFAULT_MAX_TTS_CHARS),
  };
}

export function normalizeSttPayload(payload = {}, limits = {}) {
  const audio = payload.audio;
  if (!audio || typeof audio !== "string") throw new VoiceError("audio (base64) required", 400);

  const b64 = stripDataUrl(audio).replace(/\s/g, "");
  if (!b64 || b64.length % 4 === 1 || !/^[A-Za-z0-9+/]+={0,2}$/.test(b64)) {
    throw new VoiceError("audio must be base64", 400);
  }

  const buffer = Buffer.from(b64, "base64");
  if (!buffer.length) throw new VoiceError("audio is empty", 400);

  const maxAudioBytes = positiveInt(limits.maxAudioBytes, DEFAULT_MAX_AUDIO_BYTES);
  if (buffer.length > maxAudioBytes) throw new VoiceError("audio too large", 413);

  return {
    audio: b64,
    buffer,
    mime: typeof payload.mime === "string" && payload.mime.trim() ? payload.mime.trim() : "audio/webm",
    language: typeof payload.language === "string" && payload.language.trim() ? payload.language.trim() : "tr",
  };
}

export function normalizeTtsPayload(payload = {}, limits = {}) {
  const input = typeof payload.input === "string" ? payload.input.trim() : "";
  if (!input) throw new VoiceError("input required", 400);

  const maxTtsChars = positiveInt(limits.maxTtsChars, DEFAULT_MAX_TTS_CHARS);
  if (input.length > maxTtsChars) throw new VoiceError("input too large", 413);

  return {
    input,
    voice: typeof payload.voice === "string" && payload.voice.trim() ? payload.voice.trim() : undefined,
    model: typeof payload.model === "string" && payload.model.trim() ? payload.model.trim() : undefined,
  };
}

async function safeText(response) {
  try { return await response.text(); } catch { return ""; }
}

export async function transcribeAudio(payload, config = {}, ctx = {}) {
  const p = normalizeSttPayload(payload, config);
  const fd = new FormData();
  fd.append("file", new Blob([p.buffer], { type: p.mime }), "audio.webm");
  fd.append("model", config.whisperModel || "Systran/faster-whisper-small");
  fd.append("language", p.language);

  const fetchFn = ctx.fetchFn || fetch;
  const r = await fetchFn(config.whisperUrl || "http://localhost:8000/v1/audio/transcriptions", {
    method: "POST",
    body: fd,
    signal: ctx.signal,
  });
  if (!r.ok) throw new VoiceError("whisper " + r.status + " " + (await safeText(r)).slice(0, 200), 502);
  const d = await r.json();
  return { text: d.text || d.transcript || "" };
}

export async function synthesizeSpeech(payload, config = {}, ctx = {}) {
  const p = normalizeTtsPayload(payload, config);
  const fetchFn = ctx.fetchFn || fetch;
  const r = await fetchFn(config.ttsUrl || "http://localhost:8001/v1/audio/speech", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: p.model || config.ttsModel || "tts-1",
      voice: p.voice || config.ttsVoice || "alloy",
      input: p.input,
      response_format: "mp3",
    }),
    signal: ctx.signal,
  });
  if (!r.ok) throw new VoiceError("tts " + r.status + " " + (await safeText(r)).slice(0, 200), 502);
  return {
    mime: r.headers.get("content-type") || "audio/mpeg",
    buffer: Buffer.from(await r.arrayBuffer()),
  };
}
