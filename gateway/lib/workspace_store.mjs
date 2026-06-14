// Postgres CRUD for workspaces + memberships. Role logic lives in rbac.mjs.
import { q, withTx } from "./db.mjs";

// Workspaces the user belongs to, with their role.
export async function listForUser(userId) {
  const r = await q(
    `SELECT w.id, w.name, w.owner_id, m.role,
            (extract(epoch from w.created_at)*1000)::bigint AS created_at
       FROM workspaces w
       JOIN workspace_members m ON m.workspace_id = w.id
      WHERE m.user_id = $1
      ORDER BY w.created_at DESC`, [userId]);
  return r.rows;
}

// Create a workspace; creator becomes its admin (atomic).
export async function createWorkspace(userId, name) {
  return withTx(async (c) => {
    const w = await c.query(
      `INSERT INTO workspaces (name, owner_id) VALUES ($1,$2)
       RETURNING id, name, owner_id, (extract(epoch from created_at)*1000)::bigint AS created_at`,
      [name, userId]);
    const ws = w.rows[0];
    await c.query(
      `INSERT INTO workspace_members (workspace_id, user_id, role) VALUES ($1,$2,'admin')`,
      [ws.id, userId]);
    return { ...ws, role: "admin" };
  });
}

// All workspace ids the user is a member of (for scoping shared resources).
export async function listWorkspaceIds(userId) {
  const r = await q(`SELECT workspace_id FROM workspace_members WHERE user_id=$1`, [userId]);
  return r.rows.map((x) => x.workspace_id);
}

// A user's role in a workspace, or null if not a member.
export async function getRole(workspaceId, userId) {
  const r = await q(
    `SELECT role FROM workspace_members WHERE workspace_id=$1 AND user_id=$2`,
    [workspaceId, userId]);
  return r.rows[0] ? r.rows[0].role : null;
}

export async function listMembers(workspaceId) {
  const r = await q(
    `SELECT m.user_id, m.role, u.email,
            (extract(epoch from m.created_at)*1000)::bigint AS created_at
       FROM workspace_members m
       JOIN users u ON u.id = m.user_id
      WHERE m.workspace_id = $1
      ORDER BY m.created_at ASC`, [workspaceId]);
  return r.rows;
}

// Find a user id by email (for invites). Null if no such user.
export async function findUserByEmail(email) {
  const r = await q(`SELECT id FROM users WHERE lower(email) = lower($1)`, [email]);
  return r.rows[0] ? r.rows[0].id : null;
}

// Add or update a member's role (idempotent upsert).
export async function setMember(workspaceId, userId, role) {
  await q(
    `INSERT INTO workspace_members (workspace_id, user_id, role) VALUES ($1,$2,$3)
     ON CONFLICT (workspace_id, user_id) DO UPDATE SET role = EXCLUDED.role`,
    [workspaceId, userId, role]);
}

export async function removeMember(workspaceId, userId) {
  const r = await q(`DELETE FROM workspace_members WHERE workspace_id=$1 AND user_id=$2`, [workspaceId, userId]);
  return r.rowCount > 0;
}

// Count admins (used to block removing/demoting the last admin).
export async function adminCount(workspaceId) {
  const r = await q(
    `SELECT count(*)::int AS n FROM workspace_members WHERE workspace_id=$1 AND role='admin'`,
    [workspaceId]);
  return r.rows[0].n;
}

export async function deleteWorkspace(workspaceId) {
  const r = await q(`DELETE FROM workspaces WHERE id=$1`, [workspaceId]);
  return r.rowCount > 0;
}
