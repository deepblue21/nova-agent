// REST endpoints for personal long-term memory. Requires req.principal
// (mount the principal() middleware before this router). All user-scoped.
import { Router } from "express";
import * as store from "../lib/memory_store.mjs";
import { getRole } from "../lib/workspace_store.mjs";
import { can } from "../lib/rbac.mjs";

export const memory = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

memory.get("/v1/memory", asyncRoute(async (req, res) => {
  res.json({ data: await store.listMemories(req.principal.userId) });
}));

memory.post("/v1/memory", asyncRoute(async (req, res) => {
  const content = typeof (req.body || {}).content === "string" ? req.body.content.trim() : "";
  if (!content) return res.status(400).json({ error: "content required" });
  let workspaceId = null;
  const wid = (req.body || {}).workspace_id;
  if (wid) {
    if (!UUID_RE.test(String(wid))) return res.status(400).json({ error: "invalid workspace_id" });
    const role = await getRole(wid, req.principal.userId);
    if (!role) return res.status(404).json({ error: "workspace not found" });
    if (!can(role, "write")) return res.status(403).json({ error: "forbidden: requires write (editor/admin)" });
    workspaceId = wid;
  }
  const m = await store.addMemory(req.principal.userId, content, workspaceId);
  if (!m) return res.status(400).json({ error: "empty content" });
  res.status(201).json(m);
}));

memory.delete("/v1/memory/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
  const meta = await store.getMemMeta(req.params.id);
  if (!meta) return res.status(404).end();
  if (meta.workspace_id) {
    const role = await getRole(meta.workspace_id, req.principal.userId);
    if (!can(role, "write")) return res.status(403).json({ error: "forbidden: requires write (editor/admin)" });
  } else if (meta.user_id !== req.principal.userId) {
    return res.status(404).end();
  }
  res.status(await store.deleteMemoryById(req.params.id) ? 204 : 404).end();
}));
