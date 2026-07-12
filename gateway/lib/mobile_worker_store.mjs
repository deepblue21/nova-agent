import crypto from "node:crypto";
import { q, withTx } from "./db.mjs";
import { hashEquals, sha256hex } from "./keys.mjs";
import { allowedWorkerPrompts, parseWorkerReport } from "./mobile_worker_policy.mjs";

const DEVICE_ID = "emulator-5554";
const LEASE_MS = 2 * 60 * 1000;
const TASK_COLUMNS = "id, user_id, prompt, device_id, status, created_at, updated_at";
const LEASE_COLUMNS = "id, task_id, device_id, token_hash, state, expires_at, closed_at, created_at";
const EVENT_COLUMNS = "id, task_id, type, payload, created_at";
const PHASES = {
  observing: ["observing", "worker.observing"],
  running: ["executing", "worker.running"],
  completed: ["completed", "worker.completed"],
  failed: ["failed", "worker.failed"],
  waiting_for_device: ["waiting_for_device", "worker.waiting_for_device"],
  waiting_for_compute: ["waiting_for_compute", "worker.waiting_for_compute"],
};

export const MobileWorkerStoreErrorCode = Object.freeze({
  INVALID_REQUEST: "INVALID_REQUEST",
  TASK_NOT_FOUND: "TASK_NOT_FOUND",
  LEASE_NOT_ACTIVE: "LEASE_NOT_ACTIVE",
});

export class MobileWorkerStoreError extends Error {
  constructor(code) {
    super(code);
    this.name = "MobileWorkerStoreError";
    this.code = code;
  }
}

function storeError(code) {
  return new MobileWorkerStoreError(code);
}

function normalizeEvent(event) {
  return { ...event, id: String(event.id) };
}

function leaseToken(randomBytes) {
  return randomBytes(32).toString("hex");
}

function expiresAt(now) {
  return new Date(now().getTime() + LEASE_MS);
}

async function insertEvent(client, taskId, type, payload) {
  const result = await client.query(
    `INSERT INTO mobile_task_events (task_id, type, payload)
     VALUES ($1,$2,$3)
     RETURNING ${EVENT_COLUMNS}`,
    [taskId, type, payload],
  );
  return normalizeEvent(result.rows[0]);
}

async function claimNext(transaction, { deviceId, policy, now, randomBytes }) {
  if (deviceId !== DEVICE_ID) throw storeError(MobileWorkerStoreErrorCode.INVALID_REQUEST);
  const prompts = allowedWorkerPrompts(policy);
  if (!prompts.length) return null;

  return transaction(async (client) => {
    const taskResult = await client.query(
      `SELECT t.${TASK_COLUMNS.replaceAll(", ", ", t.")}
         FROM mobile_tasks t
        WHERE t.status='queued'
          AND lower(regexp_replace(trim(t.prompt), '\\s+', ' ', 'g')) = ANY($1)
        ORDER BY t.created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1`,
      [prompts],
    );
    const task = taskResult.rows[0];
    if (!task) return null;

    const token = leaseToken(randomBytes);
    const leaseResult = await client.query(
      `INSERT INTO mobile_worker_leases (task_id, device_id, token_hash, expires_at)
       VALUES ($1,$2,$3,$4)
       RETURNING ${LEASE_COLUMNS}`,
      [task.id, DEVICE_ID, sha256hex(token), expiresAt(now)],
    );
    const updatedResult = await client.query(
      `UPDATE mobile_tasks
          SET status='executing', device_id=$1, updated_at=now()
        WHERE id=$2
        RETURNING ${TASK_COLUMNS}`,
      [DEVICE_ID, task.id],
    );
    const updatedTask = updatedResult.rows[0];
    const events = [
      await insertEvent(client, task.id, "worker.claimed", { status: updatedTask.status }),
      await insertEvent(client, task.id, "worker.executing", { status: updatedTask.status }),
    ];
    const { token_hash, ...lease } = leaseResult.rows[0];
    return { task: updatedTask, lease: { ...lease, token }, events };
  });
}

async function getActiveStatus(query, { taskId, token, now }) {
  const task = await query("SELECT id FROM mobile_tasks WHERE id=$1", [taskId]);
  if (!task.rows[0]) throw storeError(MobileWorkerStoreErrorCode.TASK_NOT_FOUND);
  const result = await query(
    `SELECT t.id AS task_id, t.user_id AS task_user_id, t.prompt AS task_prompt,
            t.device_id AS task_device_id, t.status AS task_status,
            t.created_at AS task_created_at, t.updated_at AS task_updated_at,
            l.id AS lease_id, l.device_id AS lease_device_id, l.token_hash,
            l.state AS lease_state, l.expires_at AS lease_expires_at,
            l.closed_at AS lease_closed_at, l.created_at AS lease_created_at
       FROM mobile_worker_leases l
       JOIN mobile_tasks t ON t.id=l.task_id
      WHERE l.task_id=$1 AND l.state='active' AND l.expires_at > $2`,
    [taskId, now()],
  );
  const row = result.rows[0];
  if (!row || !hashEquals(row.token_hash, sha256hex(token))) {
    throw storeError(MobileWorkerStoreErrorCode.LEASE_NOT_ACTIVE);
  }
  return {
    task: {
      id: row.task_id,
      user_id: row.task_user_id,
      prompt: row.task_prompt,
      device_id: row.task_device_id,
      status: row.task_status,
      created_at: row.task_created_at,
      updated_at: row.task_updated_at,
    },
    lease: {
      id: row.lease_id,
      task_id: row.task_id,
      device_id: row.lease_device_id,
      state: row.lease_state,
      expires_at: row.lease_expires_at,
      closed_at: row.lease_closed_at,
      created_at: row.lease_created_at,
    },
  };
}

async function report(transaction, input) {
  let report;
  try {
    report = parseWorkerReport(input);
  } catch {
    throw storeError(MobileWorkerStoreErrorCode.INVALID_REQUEST);
  }
  const taskId = input?.taskId;
  const token = input?.token;
  if (!taskId || !token || !report.lease_id || !report.report_id) {
    throw storeError(MobileWorkerStoreErrorCode.INVALID_REQUEST);
  }
  const [status, type] = PHASES[report.phase];

  return transaction(async (client) => {
    const taskResult = await client.query("SELECT id FROM mobile_tasks WHERE id=$1 FOR KEY SHARE", [taskId]);
    if (!taskResult.rows[0]) throw storeError(MobileWorkerStoreErrorCode.TASK_NOT_FOUND);
    const locked = await client.query(
      `SELECT l.${LEASE_COLUMNS.replaceAll(", ", ", l.")}, t.status AS task_status
         FROM mobile_worker_leases l
         JOIN mobile_tasks t ON t.id=l.task_id
        WHERE l.id=$1 AND l.task_id=$2
        FOR UPDATE`,
      [report.lease_id, taskId],
    );
    const lease = locked.rows[0];
    if (!lease || !hashEquals(lease.token_hash, sha256hex(token))) {
      throw storeError(MobileWorkerStoreErrorCode.LEASE_NOT_ACTIVE);
    }

    const saved = await client.query(
      `SELECT lease_id, report_id, phase, event_id, task_status
         FROM mobile_worker_reports
        WHERE lease_id=$1 AND report_id=$2`,
      [lease.id, report.report_id],
    );
    if (saved.rows[0]) {
      const event = await client.query(`SELECT ${EVENT_COLUMNS} FROM mobile_task_events WHERE id=$1`, [saved.rows[0].event_id]);
      return { task: { id: taskId, status: saved.rows[0].task_status }, event: normalizeEvent(event.rows[0] || { id: saved.rows[0].event_id }), replayed: true };
    }
    if (lease.state !== "active" || new Date(lease.expires_at) <= input.now()) {
      throw storeError(MobileWorkerStoreErrorCode.LEASE_NOT_ACTIVE);
    }

    const updated = await client.query(
      `UPDATE mobile_tasks SET status=$1, updated_at=now() WHERE id=$2 RETURNING ${TASK_COLUMNS}`,
      [status, taskId],
    );
    const task = updated.rows[0];
    const payload = { status };
    for (const field of ["summary", "steps", "error_code"]) {
      if (report[field] !== undefined) payload[field] = report[field];
    }
    const event = await insertEvent(client, taskId, type, payload);
    await client.query(
      `INSERT INTO mobile_worker_reports (lease_id, report_id, phase, event_id, task_status)
       VALUES ($1,$2,$3,$4,$5)`,
      [lease.id, report.report_id, report.phase, event.id, status],
    );
    if (report.phase !== "observing" && report.phase !== "running") {
      await client.query(`UPDATE mobile_worker_leases SET state='closed', closed_at=now() WHERE id=$1`, [lease.id]);
    }
    return { task, event, replayed: false };
  });
}

async function expireLeases(transaction, at) {
  return transaction(async (client) => {
    const leases = await client.query(
      `SELECT l.${LEASE_COLUMNS.replaceAll(", ", ", l.")}, t.status AS task_status
         FROM mobile_worker_leases l
         JOIN mobile_tasks t ON t.id=l.task_id
        WHERE l.state='active' AND l.expires_at <= $1
        FOR UPDATE SKIP LOCKED`,
      [at],
    );
    const expired = [];
    for (const lease of leases.rows) {
      await client.query(`UPDATE mobile_worker_leases SET state='expired' WHERE id=$1 AND state='active'`, [lease.id]);
      const taskResult = await client.query(
        `UPDATE mobile_tasks SET status='waiting_for_device', updated_at=now() WHERE id=$1 RETURNING ${TASK_COLUMNS}`,
        [lease.task_id],
      );
      const task = taskResult.rows[0];
      const event = await insertEvent(client, lease.task_id, "worker.lease_expired", { status: task.status });
      expired.push({ task, event });
    }
    return expired;
  });
}

export function createMobileWorkerStore({ q: query = q, withTx: transaction = withTx, now = () => new Date(), randomBytes = crypto.randomBytes } = {}) {
  return {
    claimNext: ({ deviceId, policy }) => claimNext(transaction, { deviceId, policy, now, randomBytes }),
    getActiveStatus: ({ taskId, token }) => getActiveStatus(query, { taskId, token, now }),
    report: input => report(transaction, { ...input, now }),
    expireLeases: (at = now()) => expireLeases(transaction, at),
  };
}
