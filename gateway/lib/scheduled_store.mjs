// Postgres CRUD for scheduled/automated agent tasks. All user-scoped except
// listDue() (the runner pulls due tasks across users). Timestamps in/out as ms.
import { q } from "./db.mjs";
import { listWorkspaceIds } from "./workspace_store.mjs";

const COLS =
  "id, title, prompt, model, agent, schedule, enabled, workspace_id, " +
  "(extract(epoch from next_run_at)*1000)::bigint AS next_run_at, " +
  "(extract(epoch from last_run_at)*1000)::bigint AS last_run_at, " +
  "last_status, CASE WHEN workspace_id IS NULL OR result_scope_version >= 1 THEN last_result ELSE NULL END AS last_result, " +
  "(extract(epoch from created_at)*1000)::bigint AS created_at";

// Personal tasks + tasks shared in the user's workspaces.
export async function listTasks(userId) {
  const wsIds = await listWorkspaceIds(userId);
  const r = await q(
    `SELECT ${COLS} FROM scheduled_tasks WHERE (user_id=$1 AND workspace_id IS NULL) OR workspace_id = ANY($2::uuid[]) ORDER BY created_at DESC`,
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

// Claim one immediately before execution; no unleased SELECT-and-run window.
export async function claimDue(nowMs, leaseMs = 330000) {
  const r = await q(
    `WITH due AS (SELECT id FROM scheduled_tasks
      WHERE enabled=true AND next_run_at <= to_timestamp($1/1000.0)
        AND (claim_until IS NULL OR claim_until <= now())
      ORDER BY next_run_at ASC FOR UPDATE SKIP LOCKED LIMIT 1)
     UPDATE scheduled_tasks t SET claim_token=gen_random_uuid(),
       claim_until=now()+($2::double precision * interval '1 millisecond')
     FROM due WHERE t.id=due.id
     RETURNING t.id,t.user_id,t.workspace_id,t.title,t.prompt,t.model,t.agent,t.schedule,t.claim_token`, [nowMs,leaseMs]);
  return r.rows[0] || null;
}

export async function markRun(id, { status, result, nextRunAt, claimToken }) {
  await q(
    `UPDATE scheduled_tasks
        SET last_run_at = now(), last_status = $2, last_result = $3, result_scope_version = 1,
          next_run_at = to_timestamp($4/1000.0),claim_token=NULL,claim_until=NULL
      WHERE id = $1 AND claim_token=$5`,
    [id, status, (result || "").slice(0, 8000), nextRunAt,claimToken]);
}
