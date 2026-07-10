// REST endpoints for synced conversation history. Requires req.principal
// (mount the principal() middleware before this router).
import { Router } from "express";
import * as store from "../lib/persistence.mjs";

export const history = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

history.get("/v1/conversations", asyncRoute(async (req, res) => {
  res.json({ data: await store.listConversations(req.principal.userId) });
}));

history.post("/v1/conversations", asyncRoute(async (req, res) => {
  const title = typeof req.body?.title === "string" ? req.body.title.trim().slice(0, 200) : undefined;
  const c = await store.createConversation(req.principal.userId, title);
  res.status(201).json(c);
}));

history.get("/v1/conversations/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid conversation id" });
  const c = await store.getConversation(req.principal.userId, req.params.id);
  if (!c) return res.status(404).json({ error: "not found" });
  res.json(c);
}));

history.delete("/v1/conversations/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid conversation id" });
  const ok = await store.deleteConversation(req.principal.userId, req.params.id);
  res.status(ok ? 204 : 404).end();
}));
