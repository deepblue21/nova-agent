// REST endpoints for scheduled / automated agent tasks. Requires req.principal
// (mount the principal() middleware before this router). All user-scoped.
import { Router } from "express";
import * as store from "../lib/scheduled_store.mjs";
import { isValidSchedule, nextRunAt } from "../lib/scheduler.mjs";
import { getRole } from "../lib/workspace_store.mjs";
import { can } from "../lib/rbac.mjs";

export const scheduled = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const str = (v, max) => (typeof v === "string" ? v.trim().slice(0, max) : "");

// Can this user modify a task? Personal → must own it; workspace → needs write role.
// Returns true/false; responds 404 when the task does not exist.
async function canModifyTask(req, res, id) {
  const meta = await store.getTaskMeta(id);
  if (!meta) { res.status(404).json({ error: "not found" }); return false; }
  if (meta.workspace_id) {
    const role = await getRole(meta.workspace_id, req.principal.userId);
    if (!can(role, "write")) { res.status(403).json({ error: "forbidden: requires write (editor/admin)" }); return false; }
    return true;
  }
  if (meta.user_id !== req.principal.userId) { res.status(404).json({ error: "not found" }); return false; }
  return true;
}

scheduled.get("/v1/scheduled", asyncRoute(async (req, res) => {
  res.json({ data: await store.listTasks(req.principal.userId) });
}));

scheduled.post("/v1/scheduled", asyncRoute(async (req, res) => {
  const b = req.body || {};
  const title = str(b.title, 120), prompt = str(b.prompt, 4000), schedule = str(b.schedule, 40);
  if (!title || !prompt) return res.status(400).json({ error: "title and prompt required" });
  if (!isValidSchedule(schedule)) return res.status(400).json({ error: "invalid schedule (use every:30m / every:6h / daily:09:00)" });
  let workspaceId = null;
  if (b.workspace_id) {
    if (!UUID_RE.test(String(b.workspace_id))) return res.status(400).json({ error: "invalid workspace_id" });
    const role = await getRole(b.workspace_id, req.principal.userId);
    if (!role) return res.status(404).json({ error: "workspace not found" });
    if (!can(role, "write")) return res.status(403).json({ error: "forbidden: requires write (editor/admin)" });
    workspaceId = b.workspace_id;
  }
  const t = await store.createTask(req.principal.userId, {
    title, prompt, schedule,
    model: b.model ? str(b.model, 100) : null,
    agent: b.agent === undefined ? true : !!b.agent,
    nextRunAt: nextRunAt(schedule),
    workspaceId,
  });
  res.status(201).json(t);
}));

scheduled.patch("/v1/scheduled/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
  const b = req.body || {}, fields = {};
  if (b.title !== undefined) fields.title = str(b.title, 120);
  if (b.prompt !== undefined) fields.prompt = str(b.prompt, 4000);
  if (b.model !== undefined) fields.model = b.model ? str(b.model, 100) : null;
  if (b.agent !== undefined) fields.agent = !!b.agent;
  if (b.enabled !== undefined) fields.enabled = !!b.enabled;
  if (b.schedule !== undefined) {
    const s = str(b.schedule, 40);
    if (!isValidSchedule(s)) return res.status(400).json({ error: "invalid schedule" });
    fields.schedule = s;
    fields.nextRunAt = nextRunAt(s);
  }
  if (!(await canModifyTask(req, res, req.params.id))) return;
  const t = await store.updateTaskById(req.params.id, fields);
  if (!t) return res.status(404).json({ error: "not found" });
  res.json(t);
}));

scheduled.delete("/v1/scheduled/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
  if (!(await canModifyTask(req, res, req.params.id))) return;
  const ok = await store.deleteTaskById(req.params.id);
  res.status(ok ? 204 : 404).end();
}));
