import { fetchPublicResource } from "./public_fetch.mjs";
export { isPrivateAddress } from "./public_fetch.mjs";

export class ImageInputError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.name = "ImageInputError";
    this.status = status;
  }
}

const DEFAULT_MAX_BYTES = 10 * 1024 * 1024;

function positiveInt(value, fallback) {
  const n = Number.parseInt(String(value || ""), 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function imageInputConfig(env = process.env) {
  return {
    remoteEnabled: env.REMOTE_IMAGE_URLS_ENABLED === "1",
    allowPrivate: env.REMOTE_IMAGE_ALLOW_PRIVATE === "1",
    maxBytes: positiveInt(env.REMOTE_IMAGE_MAX_BYTES, DEFAULT_MAX_BYTES),
    maxRedirects: positiveInt(env.REMOTE_IMAGE_MAX_REDIRECTS, 2),
  };
}

function parseDataImage(url) {
  const raw = String(url || "");
  const m = /^data:([^;]+);base64,([A-Za-z0-9+/=\s]+)$/i.exec(raw);
  if (!m && /^data:/i.test(raw)) throw new ImageInputError("image data must be base64", 400);
  if (!m) return null;
  if (!m[1].toLowerCase().startsWith("image/")) throw new ImageInputError("data URL must be an image", 415);
  return { mime: m[1], b64: m[2].replace(/\s/g, "") };
}

function assertImageData(data, config) {
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(data.b64) || data.b64.length % 4 === 1) {
    throw new ImageInputError("image data must be base64", 400);
  }
  const bytes = Buffer.byteLength(data.b64, "base64");
  if (!bytes) throw new ImageInputError("image is empty", 400);
  if (bytes > config.maxBytes) throw new ImageInputError("image too large", 413);
  return data;
}

async function fetchRemoteImage(url, config, ctx = {}, redirects = 0) {
  if (!config.remoteEnabled) {
    throw new ImageInputError("remote image URLs disabled (set REMOTE_IMAGE_URLS_ENABLED=1)", 400);
  }
  const r = await fetchPublicResource(url, {
    maxBytes: config.maxBytes, allowPrivate: config.allowPrivate,
    resolveHost: ctx.resolveHost, fetchFn: ctx.fetchFn,
    headers: { Accept: "image/*", "User-Agent": "NOVA-Gateway/1.0" },
    signal: ctx.signal,
  });

  if (r.status >= 300 && r.status < 400 && r.headers.get("location")) {
    if (redirects >= config.maxRedirects) throw new ImageInputError("remote image URL redirected too many times", 400);
    return fetchRemoteImage(new URL(r.headers.get("location"), url).toString(), config, ctx, redirects + 1);
  }

  if (r.status < 200 || r.status >= 300) throw new ImageInputError("remote image fetch failed: " + r.status, 502);
  const mime = (r.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
  if (!mime.startsWith("image/")) throw new ImageInputError("remote URL did not return an image", 415);

  return { mime, b64: r.buffer.toString("base64") };
}

export async function resolveImageInputs(messages = [], config = imageInputConfig(), ctx = {}) {
  const out = [];
  for (const msg of messages || []) {
    if (!Array.isArray(msg?.content)) { out.push(msg); continue; }
    const content = [];
    for (const part of msg.content) {
      if (part?.type !== "image_url") { content.push(part); continue; }
      const url = part.image_url?.url || "";
      const parsed = parseDataImage(url);
      const data = parsed ? assertImageData(parsed, config) : await fetchRemoteImage(url, config, ctx);
      content.push({ ...part, image_url: { ...(part.image_url || {}), url: `data:${data.mime};base64,${data.b64}` } });
    }
    out.push({ ...msg, content });
  }
  return out;
}
