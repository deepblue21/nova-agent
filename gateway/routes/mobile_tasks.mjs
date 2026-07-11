import { Router } from "express";
import { mobileEventBroker, formatSseEvent } from "../lib/mobile_event_broker.mjs";
import { createMobileTaskStore } from "../lib/mobile_task_store.mjs";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const COMMANDS = new Set(["cancel", "pause", "resume", "steer"]);
const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function clampLimit(value, fallback = 50) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(100, Math.max(1, Math.floor(number)));
}

function heartbeatInterval(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 15000;
}

function validId(value) {
  return UUID_RE.test(String(value || ""));
}

export function parseLastEventId(value) {
  const text = Array.isArray(value) ? value[0] : value;
  if (!/^\d+$/.test(String(text || ""))) return 0;
  const id = Number(text);
  return Number.isSafeInteger(id) ? id : 0;
}

function invalidId(res) {
  return res.status(400).json({ error: "invalid id" });
}

function transitionError(res, error) {
  return res.status(409).json({ error: error?.message || "task transition conflict" });
}

export function createMobileTasksRouter({
  store = createMobileTaskStore(),
  broker = mobileEventBroker,
  heartbeatMs = process.env.MOBILE_SSE_HEARTBEAT_MS,
} = {}) {
  const router = Router();
  const sseHeartbeatMs = heartbeatInterval(heartbeatMs);

  router.get("/v1/mobile/tasks", asyncRoute(async (req, res) => {
    const data = await store.listTasks(req.principal.userId, clampLimit(req.query.limit));
    res.json({ data });
  }));

  router.post("/v1/mobile/tasks", asyncRoute(async (req, res) => {
    const prompt = typeof req.body?.prompt === "string" ? req.body.prompt.trim() : "";
    if (!prompt || Array.from(prompt).length > 4000) {
      return res.status(400).json({ error: "prompt must be between 1 and 4000 characters" });
    }

    const created = await store.createTask(req.principal.userId, {
      prompt,
      deviceId: req.body?.deviceId ?? null,
    });
    broker.publish(created.event);
    res.status(201).json(created.task);
  }));

  router.get("/v1/mobile/tasks/:id", asyncRoute(async (req, res) => {
    if (!validId(req.params.id)) return invalidId(res);
    const task = await store.getTask(req.principal.userId, req.params.id);
    if (!task) return res.status(404).json({ error: "task not found" });
    res.json(task);
  }));

  router.get("/v1/mobile/tasks/:id/events", asyncRoute(async (req, res) => {
    if (!validId(req.params.id)) return invalidId(res);

    const task = await store.getTask(req.principal.userId, req.params.id);
    if (!task) return res.status(404).json({ error: "task not found" });

    const afterId = parseLastEventId(req.get("Last-Event-ID"));
    const replay = await store.listEvents(req.principal.userId, req.params.id, afterId);
    let closed = false;
    let unsubscribe;
    let heartbeat;
    const unsubscribeOnce = () => {
      const dispose = unsubscribe;
      unsubscribe = undefined;
      if (dispose) dispose();
    };
    const close = () => {
      if (closed) return;
      closed = true;
      if (heartbeat) clearInterval(heartbeat);
      unsubscribeOnce();
    };

    res.set({
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    });
    res.flushHeaders?.();
    res.on("close", close);

    for (const savedEvent of replay) res.write(formatSseEvent(savedEvent));
    if (closed) return;

    unsubscribe = broker.subscribe(req.params.id, savedEvent => {
      if (!closed) res.write(formatSseEvent(savedEvent));
    });
    if (closed) {
      unsubscribeOnce();
      return;
    }

    heartbeat = setInterval(() => {
      if (!closed) res.write(": heartbeat\n\n");
    }, sseHeartbeatMs);
    heartbeat.unref?.();
  }));

  router.post("/v1/mobile/tasks/:id/commands", asyncRoute(async (req, res) => {
    if (!validId(req.params.id)) return invalidId(res);
    const command = req.body?.command;
    if (!COMMANDS.has(command)) return res.status(400).json({ error: "invalid command" });

    try {
      const updated = await store.applyCommand(req.principal.userId, req.params.id, command, req.body?.note);
      if (!updated) return res.status(404).json({ error: "task not found" });
      broker.publish(updated.event);
      res.json(updated.task);
    } catch (error) {
      transitionError(res, error);
    }
  }));

  router.post("/v1/mobile/tasks/:id/confirmations/:confirmationId", asyncRoute(async (req, res) => {
    if (!validId(req.params.id) || !validId(req.params.confirmationId)) return invalidId(res);
    const decision = req.body?.decision;
    if (decision !== "approve" && decision !== "reject") {
      return res.status(400).json({ error: "decision must be approve or reject" });
    }

    try {
      const updated = await store.resolveConfirmation(
        req.principal.userId,
        req.params.id,
        req.params.confirmationId,
        decision,
      );
      if (!updated) return res.status(404).json({ error: "confirmation not found" });
      broker.publish(updated.event);
      res.json(updated.task);
    } catch (error) {
      transitionError(res, error);
    }
  }));

  return router;
}

export const mobileTasks = createMobileTasksRouter();
