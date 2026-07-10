// Postgres CRUD for scheduled/automated agent tasks. All user-scoped except
// listDue() (the runner pulls due tasks across users). Timestamps in/out as ms.
import { q } from "./db.mjs";
import { listWorkspaceIds } from "./workspace_store.mjs";

const COLS =
  "id, title, prompt, model, agent, schedule, enabled, workspace_id, " +
  "(extract(epoch from next_run_at)*1000)::bigint AS next_run_at, " +
  "(extract(epoch from last_run_at)*1000)::bigint AS last_run_at, " +
  "last_status, last_result, (extract(epoch from created_at)*1000)::bigint AS created_at";

// Personal tasks + tasks shared in the user's workspaces.
export async function listTasks(userId) {
  const wsIds = await listWorkspaceIds(userId);
  const r = await q(
    `SELECT ${COLS} FROM scheduled_tasks WHERE user_id=$1 OR workspace_id = ANY($2::uuid[]) ORDER BY created_at DESC`,
    [userId, wsIds]);
  return r.rows;
}

export async function createTask(userId, { title, prompt, model, agent, schedule, nextRunAt, workspaceId = null }) {
  const r = await q(
    `INSERT INTO scheduled_tasks (user_id, title, prompt, model, agent, schedule, next_run_at, workspace_id)
     VALUES ($1,$2,$3,$4,$5,$6, to_timestamp($7/1000.0), $8)
     RETURNING ${COLS}`,
    [userId, title, prompt, model || null, !!agent, schedule, nextRunAt, workspaceId]);
  return r.rows[0];
}

export async function getTaskMeta(id) {
  const r = await q(`SELECT user_id, workspace_id FROM scheduled_tasks WHERE id=$1`, [id]);
  return r.rows[0] || null;
}

// id-based variants (access already authorized by the route via getTaskMeta + RBAC).
export async function updateTaskById(id, fields) {
  const sets = [], vals = []; let i = 1;
  for (const k of ["title", "prompt", "model", "agent", "schedule", "enabled"]) {
    if (fields[k] !== undefined) { sets.push(`${k}=$${i++}`); vals.push(fields[k]); }
  }
  if (fields.nextRunAt !== undefined) { sets.push(`next_run_at=to_timestamp($${i++}/1000.0)`); vals.push(fields.nextRunAt); }
  if (!sets.length) {
    const r = await q(`SELECT ${COLS} FROM scheduled_tasks WHERE id=$1`, [id]);
    return r.rows[0] || null;
  }
  vals.push(id);
  const r = await q(`UPDATE scheduled_tasks SET ${sets.join(", ")} WHERE id=$${i} RETURNING ${COLS}`, vals);
  return r.rows[0] || null;
}
export async function deleteTaskById(id) {
  const r = await q(`DELETE FROM scheduled_tasks WHERE id=$1`, [id]);
  return r.rowCount > 0;
}

export async function updateTask(userId, id, fields) {
  const sets = [], vals = []; let i = 1;
  for (const k of ["title", "prompt", "model", "agent", "schedule", "enabled"]) {
    if (fields[k] !== undefined) { sets.push(`${k}=$${i++}`); vals.push(fields[k]); }
  }
  if (fields.nextRunAt !== undefined) { sets.push(`next_run_at=to_timestamp($${i++}/1000.0)`); vals.push(fields.nextRunAt); }
  if (!sets.length) {
    const r = await q(`SELECT ${COLS} FROM scheduled_tasks WHERE user_id=$1 AND id=$2`, [userId, id]);
    return r.rows[0] || null;
  }
  vals.push(userId, id);
  const r = await q(
    `UPDATE scheduled_tasks SET ${sets.join(", ")} WHERE user_id=$${i++} AND id=$${i} RETURNING ${COLS}`, vals);
  return r.rows[0] || null;
}

export async function deleteTask(userId, id) {
  const r = await q(`DELETE FROM scheduled_tasks WHERE user_id=$1 AND id=$2`, [userId, id]);
  return r.rowCount > 0;
}

// Runner: due + enabled tasks across all users (oldest first), capped.
export async function listDue(nowMs, limit = 20) {
  const r = await q(
    `SELECT id, user_id, title, prompt, model, agent, schedule
       FROM scheduled_tasks
      WHERE enabled = true AND next_run_at <= to_timestamp($1/1000.0)
      ORDER BY next_run_at ASC LIMIT $2`, [nowMs, limit]);
  return r.rows;
}

export async function markRun(id, { status, result, nextRunAt }) {
  await q(
    `UPDATE scheduled_tasks
        SET last_run_at = now(), last_status = $2, last_result = $3, next_run_at = to_timestamp($4/1000.0)
      WHERE id = $1`,
    [id, status, (result || "").slice(0, 8000), nextRunAt]);
}
