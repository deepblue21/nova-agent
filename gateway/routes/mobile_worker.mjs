import { Router } from "express";
import { createWorkerAuth } from "../lib/mobile_worker_auth.mjs";
import { mobileEventBroker } from "../lib/mobile_event_broker.mjs";
import { parseWorkerReport } from "../lib/mobile_worker_policy.mjs";
import { createMobileWorkerStore, MobileWorkerStoreErrorCode } from "../lib/mobile_worker_store.mjs";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DEVICE_ID = "emulator-5554";
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function validId(value) { return UUID_RE.test(String(value || "")); }
function leaseToken(req) { return String(req.get("X-Horus-Lease-Token") || "").trim(); }
function invalid(res) { return res.status(400).json({ error: "invalid worker request" }); }
function conflict(res) { return res.status(409).json({ error: "worker lease is not active" }); }
function safeTask(task) { return { id: task.id, status: task.status }; }
function safeStatus(status) { return { status: status.task.status, lease_expires_at: status.lease.expires_at }; }
function hasStoreErrorCode(error, code) { return error?.code === code; }
function storeOutcome(res, error) {
  if (hasStoreErrorCode(error, MobileWorkerStoreErrorCode.TASK_NOT_FOUND)) {
    return res.status(404).json({ error: "task not found" });
  }
  if (hasStoreErrorCode(error, MobileWorkerStoreErrorCode.LEASE_NOT_ACTIVE)) return conflict(res);
  return false;
}

export function createMobileWorkerRouter({
  store = createMobileWorkerStore(),
  broker = mobileEventBroker,
  enabled = process.env.MOBILE_WORKER_ENABLED === "1",
  token = process.env.MOBILE_WORKER_TOKEN || "",
  policy = process.env.MOBILE_WORKER_GOAL_POLICY || "settings_android_version",
} = {}) {
  const router = Router();
  router.use("/v1/internal/mobile-worker", createWorkerAuth({ enabled, token }));

  router.post("/v1/internal/mobile-worker/claims", asyncRoute(async (req, res) => {
    if (req.body?.device_id !== DEVICE_ID) return invalid(res);
    const claimed = await store.claimNext({ deviceId: DEVICE_ID, policy });
    if (!claimed) return res.status(204).end();
    for (const savedEvent of claimed.events) broker.publish(savedEvent);
    res.status(201).json({ task: claimed.task, lease: claimed.lease });
  }));

  router.get("/v1/internal/mobile-worker/tasks/:id/status", asyncRoute(async (req, res) => {
    if (!validId(req.params.id)) return invalid(res);
    const token = leaseToken(req);
    if (!token) return conflict(res);
    try {
      const status = await store.getActiveStatus({ taskId: req.params.id, token });
      res.json(safeStatus(status));
    } catch (error) {
      if (storeOutcome(res, error)) return;
      throw error;
    }
  }));

  router.post("/v1/internal/mobile-worker/tasks/:id/reports", asyncRoute(async (req, res) => {
    if (!validId(req.params.id)) return invalid(res);
    const token = leaseToken(req);
    if (!token) return conflict(res);
    let report;
    try { report = parseWorkerReport(req.body); } catch { return invalid(res); }
    if (!report.lease_id || !report.report_id) return invalid(res);
    try {
      const result = await store.report({ taskId: req.params.id, token, ...report });
      if (!result.replayed) broker.publish(result.event);
      res.json(safeTask(result.task));
    } catch (error) {
      if (storeOutcome(res, error)) return;
      throw error;
    }
  }));

  router.post("/v1/internal/mobile-worker/leases/expire", asyncRoute(async (_req, res) => {
    const expired = await store.expireLeases();
    for (const { event } of expired) broker.publish(event);
    res.json({ data: expired.map(({ task }) => safeTask(task)) });
  }));
  router.use("/v1/internal/mobile-worker", (_error, _req, res, _next) => {
    res.status(500).json({ error: "mobile worker request failed" });
  });
  return router;
}
