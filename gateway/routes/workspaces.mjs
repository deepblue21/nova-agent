// Workspace + RBAC management endpoints. Requires req.principal (mount after
// principal()). Every workspace-scoped route resolves the caller's role and
// enforces the rbac.can() permission matrix.
import { Router } from "express";
import * as store from "../lib/workspace_store.mjs";
import { can, isRole } from "../lib/rbac.mjs";

export const workspaces = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const str = (v, max) => (typeof v === "string" ? v.trim().slice(0, max) : "");

// Resolve the caller's role in :id and require `action`. On failure, responds
// and returns null; on success returns the role string.
async function require(req, res, action) {
  const id = String(req.params.id || "");
  if (!UUID_RE.test(id)) { res.status(400).json({ error: "invalid workspace id" }); return null; }
  const role = await store.getRole(id, req.principal.userId);
  if (!role) { res.status(404).json({ error: "workspace not found" }); return null; } // hide existence from non-members
  if (!can(role, action)) { res.status(403).json({ error: "forbidden: requires " + action }); return null; }
  return role;
}

workspaces.get("/v1/workspaces", asyncRoute(async (req, res) => {
  res.json({ data: await store.listForUser(req.principal.userId) });
}));

workspaces.get("/v1/workspace-invitations", asyncRoute(async (req, res) => {
  res.json({ data: await store.listInvitations(req.principal.userId) });
}));
workspaces.post("/v1/workspace-invitations/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(req.params.id) || typeof req.body?.accept !== "boolean")
    return res.status(400).json({ error: "invalid invitation response" });
  const changed = await store.respondToInvitation(req.params.id, req.principal.userId, req.body.accept);
  res.status(changed ? 204 : 404).end();
}));

workspaces.post("/v1/workspaces", asyncRoute(async (req, res) => {
  const name = str((req.body || {}).name, 120);
  if (!name) return res.status(400).json({ error: "name required" });
  res.status(201).json(await store.createWorkspace(req.principal.userId, name));
}));

workspaces.get("/v1/workspaces/:id/members", asyncRoute(async (req, res) => {
  if (!(await require(req, res, "read"))) return;
  res.json({ data: await store.listMembers(req.params.id) });
}));

workspaces.post("/v1/workspaces/:id/members", asyncRoute(async (req, res) => {
  if (!(await require(req, res, "manage"))) return;
  const b = req.body || {};
  const email = str(b.email, 200);
  const role = str(b.role, 20) || "viewer";
  if (!email) return res.status(400).json({ error: "email required" });
  if (!isRole(role)) return res.status(400).json({ error: "invalid role (admin|editor|viewer)" });
  await store.inviteMember(req.params.id, email, role);
  res.status(202).json({ status: "invitation_requested" });
}));

workspaces.patch("/v1/workspaces/:id/members/:userId", asyncRoute(async (req, res) => {
  if (!(await require(req, res, "manage"))) return;
  const role = str((req.body || {}).role, 20);
  if (!isRole(role)) return res.status(400).json({ error: "invalid role" });
  const target = String(req.params.userId || "");
  if (!UUID_RE.test(target)) return res.status(400).json({ error: "invalid user id" });
  const state = await store.getMemberState(req.params.id, target);
  const current = state?.role;
  if (!current) return res.status(404).json({ error: "member not found" });
  // never leave the workspace without an admin
  if (state.accepted_at && current === "admin" && role !== "admin" && (await store.adminCount(req.params.id)) <= 1)
    return res.status(409).json({ error: "cannot demote the last admin" });
  await store.setMember(req.params.id, target, role);
  res.json({ user_id: target, role });
}));

workspaces.delete("/v1/workspaces/:id/invitations", asyncRoute(async (req,res) => {
  if (!(await require(req,res,"manage"))) return;
  const email = str(req.body?.email,320);
  if (!email) return res.status(400).json({error:"email required"});
  await store.cancelInvitation(req.params.id,email);
  res.status(204).end(); // no email existence signal
}));

workspaces.delete("/v1/workspaces/:id/members/:userId", asyncRoute(async (req, res) => {
  const target = String(req.params.userId || "");
  if (!UUID_RE.test(target)) return res.status(400).json({ error: "invalid user id" });
  const self = target === req.principal.userId;
  // managers may remove anyone; a member may always remove themselves (leave).
  const role = self ? await requireMember(req, res) : await require(req, res, "manage");
  if (!role) return;
  const state = await store.getMemberState(req.params.id, target);
  const current = state?.role;
  if (!current) return res.status(404).json({ error: "member not found" });
  if (state.accepted_at && current === "admin" && (await store.adminCount(req.params.id)) <= 1)
    return res.status(409).json({ error: "cannot remove the last admin" });
  await store.removeMember(req.params.id, target);
  res.status(204).end();
}));

workspaces.delete("/v1/workspaces/:id", asyncRoute(async (req, res) => {
  if (!(await require(req, res, "manage"))) return;
  await store.deleteWorkspace(req.params.id);
  res.status(204).end();
}));

// Like require() but only checks membership (any role) — used for self-leave.
async function requireMember(req, res) {
  const id = String(req.params.id || "");
  if (!UUID_RE.test(id)) { res.status(400).json({ error: "invalid workspace id" }); return null; }
  const role = await store.getRole(id, req.principal.userId);
  if (!role) { res.status(404).json({ error: "workspace not found" }); return null; }
  return role;
}
