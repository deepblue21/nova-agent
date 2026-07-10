// Admin endpoints: API key lifecycle + per-user quota.
// Mount AFTER principal(); guarded by ADMIN_USER_IDS (comma-separated user ids).
import { Router } from "express";
import { q } from "../lib/db.mjs";
import { newApiKey } from "../lib/keys.mjs";

export const admin = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const API_KEY_PREFIX_RE = /^nv_[0-9a-f]{6}$/;

// Read lazily (not at import time): gateway.mjs loads .env AFTER imports run,
// so a top-level read would miss ADMIN_USER_IDS values coming from .env.
const admins = () => (process.env.ADMIN_USER_IDS || "").split(",").map(s => s.trim()).filter(Boolean);

function requireAdmin(req, res, next) {
  if (req.principal && admins().includes(req.principal.userId)) return next();
  return res.status(403).json({ error: "admin only" });
}

const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function requireUuid(req, res, next) {
  if (!UUID_RE.test(String(req.params.userId || ""))) return res.status(400).json({ error: "invalid user id" });
  next();
}

// Issue a new API key for a user. The full secret is returned ONCE.
admin.post("/v1/admin/users/:userId/api-keys", requireAdmin, requireUuid, asyncRoute(async (req, res) => {
  const k = newApiKey();
  const scopes = Array.isArray(req.body?.scopes)
    ? req.body.scopes.map(s => String(s).trim()).filter(Boolean).slice(0, 20)
    : [];
  await q(
    "INSERT INTO api_keys (user_id, prefix, token_hash, scopes) VALUES ($1,$2,$3,$4)",
    [req.params.userId, k.prefix, k.token_hash, scopes]);
  res.status(201).json({ prefix: k.prefix, key: k.full });
}));

// List a user's keys (never returns secrets/hashes).
admin.get("/v1/admin/users/:userId/api-keys", requireAdmin, requireUuid, asyncRoute(async (req, res) => {
  const { rows } = await q(
    `SELECT id, prefix, scopes, created_at, last_used_at, revoked_at
       FROM api_keys WHERE user_id = $1 ORDER BY created_at DESC`, [req.params.userId]);
  res.json({ data: rows });
}));

// Revoke a key by its visible prefix.
admin.delete("/v1/admin/api-keys/:prefix", requireAdmin, asyncRoute(async (req, res) => {
  if (!API_KEY_PREFIX_RE.test(String(req.params.prefix || ""))) return res.status(400).json({ error: "invalid key prefix" });
  const { rowCount } = await q(
    "UPDATE api_keys SET revoked_at = now() WHERE prefix = $1 AND revoked_at IS NULL",
    [req.params.prefix]);
  res.status(rowCount ? 204 : 404).end();
}));

// Set / update a user's rolling budget (limit in micro-dollars).
admin.put("/v1/admin/users/:userId/quota", requireAdmin, requireUuid, asyncRoute(async (req, res) => {
  const limit = Number(req.body?.limit_micros);
  const period = req.body?.period === "day" ? "day" : "month";
  if (!Number.isFinite(limit) || limit < 0 || limit > 1_000_000_000_000)
    return res.status(400).json({ error: "limit_micros required" });
  const resets = period === "day" ? "date_trunc('day', now()) + interval '1 day'"
                                   : "date_trunc('month', now()) + interval '1 month'";
  await q(
    `INSERT INTO quotas (subject_id, period, limit_micros, resets_at)
       VALUES ($1, $2, $3, ${resets})
     ON CONFLICT (subject_id) DO UPDATE
       SET limit_micros = EXCLUDED.limit_micros, period = EXCLUDED.period`,
    [req.params.userId, period, limit]);
  res.status(204).end();
}));
