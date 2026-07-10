export class MediaInputError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.name = "MediaInputError";
    this.status = status;
  }
}

const DEFAULT_MAX_BYTES = 25 * 1024 * 1024;
const DEFAULT_ALLOWED = [
  "image/png",
  "image/jpeg",
  "image/webp",
  "image/gif",
  "audio/webm",
  "audio/mpeg",
  "audio/wav",
  "audio/ogg",
  "video/mp4",
  "video/webm",
  "application/pdf",
];

function positiveInt(value, fallback) {
  const n = Number.parseInt(String(value || ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function allowedSet(env = process.env) {
  const raw = String(env.ALLOWED_MEDIA_MIME_TYPES || "").trim();
  const values = raw ? raw.split(",").map(s => s.trim()).filter(Boolean) : DEFAULT_ALLOWED;
  return new Set(values.map(s => s.toLowerCase()));
}

function stripDataUrl(value) {
  const raw = String(value || "");
  const m = /^data:([^;,]+)?(?:;[^,]*)?;base64,(.*)$/i.exec(raw);
  return m ? { mime: m[1] || "", b64: m[2] || "" } : { mime: "", b64: raw };
}

export function mediaInputConfig(env = process.env) {
  return {
    maxBytes: positiveInt(env.MAX_MEDIA_BYTES, DEFAULT_MAX_BYTES),
    allowedTypes: allowedSet(env),
  };
}

export function normalizeMediaInput(body = {}, config = mediaInputConfig()) {
  let { mime, b64 } = body || {};
  const dataUrl = body?.data_url || body?.dataUrl || "";
  if (dataUrl) {
    const parsed = stripDataUrl(dataUrl);
    mime = mime || parsed.mime;
    b64 = parsed.b64;
  }

  mime = String(mime || "").split(";")[0].trim().toLowerCase();
  b64 = String(b64 || "").replace(/\s+/g, "");

  if (!mime || !b64) throw new MediaInputError("data_url or {mime,b64} required", 400);
  if (!config.allowedTypes?.has(mime)) throw new MediaInputError("media type not allowed", 415);
  if (b64.length % 4 === 1 || !/^[A-Za-z0-9+/]+={0,2}$/.test(b64)) {
    throw new MediaInputError("media must be base64", 400);
  }

  const estimated = Math.floor((b64.length * 3) / 4);
  if (estimated > config.maxBytes + 2) throw new MediaInputError("media too large", 413);

  const buffer = Buffer.from(b64, "base64");
  if (!buffer.length) throw new MediaInputError("media is empty", 400);
  if (buffer.length > config.maxBytes) throw new MediaInputError("media too large", 413);
  return { mime, buffer };
}

export const DEFAULT_MEDIA_TYPES = DEFAULT_ALLOWED;
