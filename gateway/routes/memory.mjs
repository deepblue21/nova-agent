// REST endpoints for personal long-term memory. Requires req.principal
// (mount the principal() middleware before this router). All user-scoped.
import { Router } from "express";
import * as store from "../lib/memory_store.mjs";

export const memory = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

memory.get("/v1/memory", asyncRoute(async (req, res) => {
  res.json({ data: await store.listMemories(req.principal.userId) });
}));

memory.post("/v1/memory", asyncRoute(async (req, res) => {
  const content = typeof (req.body || {}).content === "string" ? req.body.content.trim() : "";
  if (!content) return res.status(400).json({ error: "content required" });
  const m = await store.addMemory(req.principal.userId, content);
  if (!m) return res.status(400).json({ error: "empty content" });
  res.status(201).json(m);
}));

memory.delete("/v1/memory/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
  const ok = await store.deleteMemory(req.principal.userId, req.params.id);
  res.status(ok ? 204 : 404).end();
}));
