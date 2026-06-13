import { lookup as dnsLookup } from "node:dns/promises";
import net from "node:net";

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

function isLocalHostName(hostname) {
  const h = String(hostname || "").toLowerCase().replace(/\.$/, "");
  return h === "localhost" || h.endsWith(".localhost");
}

export function isPrivateAddress(address) {
  const ipKind = net.isIP(address);
  if (ipKind === 4) {
    const parts = address.split(".").map(Number);
    const [a, b] = parts;
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && (b === 0 || b === 168)) return true;
    if (a === 198 && (b === 18 || b === 19)) return true;
    if (a >= 224) return true;
    return false;
  }
  if (ipKind === 6) {
    const s = address.toLowerCase();
    if (s === "::" || s === "::1") return true;
    if (s.startsWith("fc") || s.startsWith("fd")) return true;
    if (s.startsWith("fe8") || s.startsWith("fe9") || s.startsWith("fea") || s.startsWith("feb")) return true;
    const mapped = /::ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(s);
    if (mapped) return isPrivateAddress(mapped[1]);
  }
  return false;
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

async function assertPublicTarget(url, config, ctx) {
  const u = new URL(url);
  if (u.protocol !== "http:" && u.protocol !== "https:") {
    throw new ImageInputError("remote image URL must use http or https", 400);
  }
  if (u.username || u.password) throw new ImageInputError("remote image URL credentials are not allowed", 400);
  if (config.allowPrivate) return;
  if (isLocalHostName(u.hostname)) throw new ImageInputError("remote image URL points to a private host", 400);
  if (isPrivateAddress(u.hostname)) throw new ImageInputError("remote image URL points to a private address", 400);

  const resolveHost = ctx.resolveHost || ((host) => dnsLookup(host, { all: true, verbatim: false }));
  const addresses = await resolveHost(u.hostname);
  for (const a of addresses || []) {
    const address = typeof a === "string" ? a : a.address;
    if (address && isPrivateAddress(address)) {
      throw new ImageInputError("remote image URL resolves to a private address", 400);
    }
  }
}

async function fetchRemoteImage(url, config, ctx = {}, redirects = 0) {
  if (!config.remoteEnabled) {
    throw new ImageInputError("remote image URLs disabled (set REMOTE_IMAGE_URLS_ENABLED=1)", 400);
  }
  await assertPublicTarget(url, config, ctx);

  const fetchFn = ctx.fetchFn || fetch;
  const r = await fetchFn(url, {
    method: "GET",
    redirect: "manual",
    headers: { Accept: "image/*", "User-Agent": "NOVA-Gateway/1.0" },
    signal: ctx.signal,
  });

  if (r.status >= 300 && r.status < 400 && r.headers.get("location")) {
    if (redirects >= config.maxRedirects) throw new ImageInputError("remote image URL redirected too many times", 400);
    return fetchRemoteImage(new URL(r.headers.get("location"), url).toString(), config, ctx, redirects + 1);
  }

  if (!r.ok) throw new ImageInputError("remote image fetch failed: " + r.status, 502);
  const mime = (r.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
  if (!mime.startsWith("image/")) throw new ImageInputError("remote URL did not return an image", 415);

  const len = Number.parseInt(r.headers.get("content-length") || "0", 10);
  if (len && len > config.maxBytes) throw new ImageInputError("remote image too large", 413);

  const buffer = await readBodyWithLimit(r, config.maxBytes);
  if (buffer.length > config.maxBytes) throw new ImageInputError("remote image too large", 413);
  return { mime, b64: buffer.toString("base64") };
}

async function readBodyWithLimit(response, maxBytes) {
  if (!response.body?.getReader) {
    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length > maxBytes) throw new ImageInputError("remote image too large", 413);
    return buffer;
  }
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    const chunk = Buffer.from(value);
    total += chunk.length;
    if (total > maxBytes) throw new ImageInputError("remote image too large", 413);
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
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
