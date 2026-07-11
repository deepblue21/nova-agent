import { q, withTx } from "./db.mjs";
import { applyTaskCommand, isTerminal, TASK_STATUSES } from "./mobile_task_state.mjs";

const TASK_COLUMNS = "id, user_id, prompt, device_id, status, created_at, updated_at";
const EVENT_COLUMNS = "id, task_id, type, payload, created_at";
const CONFIRMATION_COLUMNS = "id, task_id, risk_level, action, resume_status, status, decided_at, created_at";
const MAX_EVENT_PAYLOAD_BYTES = 32 * 1024;

function cappedText(value, max) {
  return Array.from(String(value ?? "")).slice(0, max).join("");
}

function limit(value, fallback, maximum) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(maximum, Math.max(1, Math.floor(number)));
}

function ensureEventPayload(payload) {
  if (!payload || Array.isArray(payload) || typeof payload !== "object") {
    throw new Error("event payload must be a JSON object");
  }
  let json;
  try {
    json = JSON.stringify(payload);
  } catch {
    throw new Error("event payload must be JSON serializable");
  }
  if (!json || Buffer.byteLength(json, "utf8") > MAX_EVENT_PAYLOAD_BYTES) {
    throw new Error("event payload exceeds 32 KiB");
  }
  return payload;
}

function normalizeEvent(event) {
  return { ...event, id: String(event.id) };
}

async function insertEvent(client, taskId, type, payload) {
  const r = await client.query(
    `INSERT INTO mobile_task_events (task_id, type, payload)
     VALUES ($1,$2,$3)
     RETURNING ${EVENT_COLUMNS}`,
    [taskId, type, ensureEventPayload(payload)],
  );
  return normalizeEvent(r.rows[0]);
}

async function lockTask(client, userId, taskId) {
  const r = await client.query(
    `SELECT ${TASK_COLUMNS}
       FROM mobile_tasks
      WHERE id=$1 AND user_id=$2
      FOR UPDATE`,
    [taskId, userId],
  );
  return r.rows[0] || null;
}

async function createTask(transaction, userId, prompt, deviceId) {
  const normalizedPrompt = cappedText(prompt, 4000);
  if (!normalizedPrompt) throw new Error("prompt is required");

  return transaction(async (client) => {
    const taskResult = await client.query(
      `INSERT INTO mobile_tasks (user_id, prompt, device_id)
       VALUES ($1,$2,$3)
       RETURNING ${TASK_COLUMNS}`,
      [userId, normalizedPrompt, deviceId == null ? null : String(deviceId)],
    );
    const task = taskResult.rows[0];
    const event = await insertEvent(client, task.id, "task.created", { status: task.status });
    return { task, event };
  });
}

async function listTasks(query, userId, requestedLimit) {
  const r = await query(
    `SELECT ${TASK_COLUMNS}
       FROM mobile_tasks
      WHERE user_id=$1
      ORDER BY created_at DESC
      LIMIT $2`,
    [userId, limit(requestedLimit, 50, 100)],
  );
  return r.rows;
}

async function getTask(query, userId, taskId) {
  const r = await query(
    `SELECT ${TASK_COLUMNS}
       FROM mobile_tasks
      WHERE user_id=$1 AND id=$2`,
    [userId, taskId],
  );
  return r.rows[0] || null;
}

async function listEvents(query, userId, taskId, afterId, requestedLimit) {
  const r = await query(
    `SELECT e.${EVENT_COLUMNS.replaceAll(", ", ", e.")}
       FROM mobile_task_events e
       JOIN mobile_tasks t ON t.id=e.task_id
      WHERE t.user_id=$1 AND e.task_id=$2 AND e.id > $3
      ORDER BY e.id ASC
      LIMIT $4`,
    [userId, taskId, afterId, limit(requestedLimit, 500, 500)],
  );
  return r.rows.map(normalizeEvent);
}

async function applyCommand(transaction, userId, taskId, command, note) {
  const normalizedNote = cappedText(note, 1000);
  return transaction(async (client) => {
    const task = await lockTask(client, userId, taskId);
    if (!task) return null;

    const nextStatus = applyTaskCommand(task.status, command);
    const taskResult = await client.query(
      `UPDATE mobile_tasks
          SET status=$1, updated_at=now()
        WHERE id=$2 AND user_id=$3
        RETURNING ${TASK_COLUMNS}`,
      [nextStatus, taskId, userId],
    );
    const updatedTask = taskResult.rows[0];
    const event = await insertEvent(client, taskId, `task.${command}`, {
      command,
      note: normalizedNote,
      previous_status: task.status,
      status: nextStatus,
    });
    return { task: updatedTask, event };
  });
}

function confirmationInput(input) {
  const riskLevel = input?.riskLevel;
  if (riskLevel !== "R2" && riskLevel !== "R3") throw new Error("risk level must be R2 or R3");
  if (input?.action === undefined) throw new Error("confirmation action is required");
  const resumeStatus = cappedText(input.resumeStatus || "executing", 100);
  if (!TASK_STATUSES.includes(resumeStatus) || isTerminal(resumeStatus)) {
    throw new Error("resume status must be a non-terminal task status");
  }
  return {
    riskLevel,
    action: input.action,
    resumeStatus,
  };
}

async function requestConfirmation(transaction, userId, taskId, input) {
  const confirmationInputValue = confirmationInput(input);
  return transaction(async (client) => {
    const task = await lockTask(client, userId, taskId);
    if (!task) return null;
    if (isTerminal(task.status)) throw new Error("terminal task cannot be changed");

    const pending = await client.query(
      `SELECT id
         FROM mobile_confirmations
        WHERE task_id=$1 AND status='pending'
        FOR UPDATE`,
      [taskId],
    );
    if (pending.rows[0]) throw new Error("task already has a pending confirmation");

    const confirmationResult = await client.query(
      `INSERT INTO mobile_confirmations (task_id, risk_level, action, resume_status)
       VALUES ($1,$2,$3,$4)
       RETURNING ${CONFIRMATION_COLUMNS}`,
      [taskId, confirmationInputValue.riskLevel, confirmationInputValue.action, confirmationInputValue.resumeStatus],
    );
    const confirmation = confirmationResult.rows[0];
    const taskResult = await client.query(
      `UPDATE mobile_tasks
          SET status='waiting_for_confirmation', updated_at=now()
        WHERE id=$1 AND user_id=$2
        RETURNING ${TASK_COLUMNS}`,
      [taskId, userId],
    );
    const updatedTask = taskResult.rows[0];
    const event = await insertEvent(client, taskId, "confirmation.requested", {
      confirmation_id: confirmation.id,
      risk_level: confirmation.risk_level,
      status: updatedTask.status,
    });
    return { task: updatedTask, event, confirmation };
  });
}

async function resolveConfirmation(transaction, userId, taskId, confirmationId, decision) {
  if (decision !== "approve" && decision !== "reject") {
    throw new Error("confirmation decision must be approve or reject");
  }

  return transaction(async (client) => {
    const task = await lockTask(client, userId, taskId);
    if (!task) return null;
    const isWaitingForConfirmation = task.status === "waiting_for_confirmation";
    if (decision === "approve" && !isWaitingForConfirmation) {
      throw new Error("task must be waiting_for_confirmation to resolve confirmation");
    }

    const confirmationResult = await client.query(
      `SELECT c.${CONFIRMATION_COLUMNS.replaceAll(", ", ", c.")}
         FROM mobile_confirmations c
         JOIN mobile_tasks t ON t.id=c.task_id
        WHERE c.id=$1 AND c.task_id=$2 AND t.user_id=$3 AND c.status='pending'
        FOR UPDATE OF c`,
      [confirmationId, taskId, userId],
    );
    const confirmation = confirmationResult.rows[0];
    if (!confirmation) return null;

    const confirmationStatus = decision === "approve" ? "approved" : "rejected";
    const savedConfirmationResult = await client.query(
      `UPDATE mobile_confirmations
          SET status=$1, decided_at=now()
        WHERE id=$2 AND task_id=$3
        RETURNING ${CONFIRMATION_COLUMNS}`,
      [confirmationStatus, confirmationId, taskId],
    );
    const savedConfirmation = savedConfirmationResult.rows[0];
    const nextStatus = decision === "approve" ? confirmation.resume_status
      : (isWaitingForConfirmation ? "paused" : task.status);
    let updatedTask = task;
    if (isWaitingForConfirmation) {
      const taskResult = await client.query(
        `UPDATE mobile_tasks
            SET status=$1, updated_at=now()
          WHERE id=$2 AND user_id=$3
          RETURNING ${TASK_COLUMNS}`,
        [nextStatus, taskId, userId],
      );
      updatedTask = taskResult.rows[0];
    }
    const eventType = decision === "approve" ? "confirmation.approved" : "confirmation.rejected";
    const event = await insertEvent(client, taskId, eventType, { status: nextStatus });
    return { task: updatedTask, event, confirmation: savedConfirmation };
  });
}

export function createMobileTaskStore({ q: query = q, withTx: transaction = withTx } = {}) {
  return {
    createTask: (userId, { prompt, deviceId = null }) => createTask(transaction, userId, prompt, deviceId),
    listTasks: (userId, requestedLimit = 50) => listTasks(query, userId, requestedLimit),
    getTask: (userId, taskId) => getTask(query, userId, taskId),
    listEvents: (userId, taskId, afterId = 0, requestedLimit = 500) => listEvents(query, userId, taskId, afterId, requestedLimit),
    applyCommand: (userId, taskId, command, note = "") => applyCommand(transaction, userId, taskId, command, note),
    requestConfirmation: (userId, taskId, input) => requestConfirmation(transaction, userId, taskId, input),
    resolveConfirmation: (userId, taskId, confirmationId, decision) => resolveConfirmation(transaction, userId, taskId, confirmationId, decision),
  };
}
