// Agent run history endpoints. Requires req.principal (mount after principal()).
import { Router } from "express";
import * as store from "../lib/agent_runs_store.mjs";

export const agentRuns = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

agentRuns.get("/v1/agent/runs", asyncRoute(async (req, res) => {
  res.json({ data: await store.listRuns(req.principal.userId) });
}));

agentRuns.delete("/v1/agent/runs/:id", asyncRoute(async (req, res) => {
  if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
  const ok = await store.deleteRun(req.principal.userId, req.params.id);
  res.status(ok ? 204 : 404).end();
}));
