// Pure helpers for bearer parsing + NOVA API-key generation/verification.
// Only node:crypto — no external deps, so this is unit-testable in isolation.
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

export const sha256hex = (s) => createHash("sha256").update(String(s)).digest("hex");

// "Authorization: Bearer <x>" -> "<x>" (or "").
export function parseBearer(header) {
  const h = String(header || "");
  return h.startsWith("Bearer ") ? h.slice(7).trim() : "";
}

// A NOVA API key is "nv_<6hex>_<secret>". Only the hash is ever stored.
export function newApiKey() {
  const prefix = "nv_" + randomBytes(3).toString("hex");      // nv_ab12cd
  const secret = randomBytes(24).toString("base64url");       // ~32 chars
  const full   = prefix + "_" + secret;
  return { full, prefix, token_hash: sha256hex(full) };
}

export const isApiKey = (t) => /^nv_[0-9a-f]{6}_/.test(String(t || ""));

export function keyPrefix(full) {
  const m = /^(nv_[0-9a-f]{6})_/.exec(String(full || ""));
  return m ? m[1] : "";
}

// Constant-time hex compare (avoids hash timing leaks).
export function hashEquals(a, b) {
  const ba = Buffer.from(String(a)), bb = Buffer.from(String(b));
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}
