// Postgres CRUD for workspaces + memberships. Role logic lives in rbac.mjs.
import { q, withTx } from "./db.mjs";

// Workspaces the user belongs to, with their role.
export async function listForUser(userId) {
  const r = await q(
    `SELECT w.id, w.name, w.owner_id, m.role,
            (extract(epoch from w.created_at)*1000)::bigint AS created_at
       FROM workspaces w
       JOIN workspace_members m ON m.workspace_id = w.id
      WHERE m.user_id = $1 AND m.accepted_at IS NOT NULL
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
      `INSERT INTO workspace_members (workspace_id, user_id, role, accepted_at) VALUES ($1,$2,'admin',now())`,
      [ws.id, userId]);
    return { ...ws, role: "admin" };
  });
}

// All workspace ids the user is a member of (for scoping shared resources).
export async function listWorkspaceIds(userId) {
  const r = await q(`SELECT workspace_id FROM workspace_members WHERE user_id=$1 AND accepted_at IS NOT NULL`, [userId]);
  return r.rows.map((x) => x.workspace_id);
}

// A user's role in a workspace, or null if not a member.
export async function getRole(workspaceId, userId) {
  const r = await q(
    `SELECT role FROM workspace_members WHERE workspace_id=$1 AND user_id=$2 AND accepted_at IS NOT NULL`,
    [workspaceId, userId]);
  return r.rows[0] ? r.rows[0].role : null;
}

export async function listMembers(workspaceId) {
  const r = await q(
    `SELECT m.user_id, m.role, u.email,
            (extract(epoch from m.created_at)*1000)::bigint AS created_at
       FROM workspace_members m
       JOIN users u ON u.id = m.user_id
      WHERE m.workspace_id = $1 AND m.accepted_at IS NOT NULL
      ORDER BY m.created_at ASC`, [workspaceId]);
  return r.rows;
}

// Find a user id by email (for invites). Null if no such user.
export async function findUserByEmail(email) {
  const r = await q(`SELECT id FROM users WHERE lower(email) = lower($1)`, [email]);
  return r.rows[0] ? r.rows[0].id : null;
}

// Role changes never create membership or accept an invitation.
export async function setMember(workspaceId, userId, role) {
  await q(
    `UPDATE workspace_members SET role=$3 WHERE workspace_id=$1 AND user_id=$2`,
    [workspaceId, userId, role]);
}

export async function getMemberState(workspaceId, userId) {
  return (await q('SELECT role, accepted_at FROM workspace_members WHERE workspace_id=$1 AND user_id=$2', [workspaceId,userId])).rows[0] || null;
}

export async function cancelInvitation(workspaceId, email) {
  await q(`DELETE FROM workspace_members m USING users u WHERE m.user_id=u.id
    AND m.workspace_id=$1 AND lower(u.email)=lower($2) AND m.accepted_at IS NULL`,[workspaceId,email]);
}

export async function inviteMember(workspaceId, email, role) {
  // Same response for unknown email, existing member and new invitation.
  await q(`INSERT INTO workspace_members(workspace_id, user_id, role)
    SELECT $1, id, $3 FROM users WHERE lower(email)=lower($2)
    ON CONFLICT (workspace_id, user_id) DO NOTHING`, [workspaceId, email, role]);
}

export async function listInvitations(userId) {
  const r = await q(`SELECT w.id, w.name, m.role FROM workspace_members m
    JOIN workspaces w ON w.id=m.workspace_id
    WHERE m.user_id=$1 AND m.accepted_at IS NULL ORDER BY m.created_at DESC`, [userId]);
  return r.rows;
}

export async function respondToInvitation(workspaceId, userId, accept) {
  const r = await q(accept
    ? `UPDATE workspace_members SET accepted_at=now() WHERE workspace_id=$1 AND user_id=$2 AND accepted_at IS NULL`
    : `DELETE FROM workspace_members WHERE workspace_id=$1 AND user_id=$2 AND accepted_at IS NULL`, [workspaceId, userId]);
  return r.rowCount > 0;
}

export async function removeMember(workspaceId, userId) {
  const r = await q(`DELETE FROM workspace_members WHERE workspace_id=$1 AND user_id=$2`, [workspaceId, userId]);
  return r.rowCount > 0;
}

// Count admins (used to block removing/demoting the last admin).
export async function adminCount(workspaceId) {
  const r = await q(
    `SELECT count(*)::int AS n FROM workspace_members WHERE workspace_id=$1 AND role='admin' AND accepted_at IS NOT NULL`,
    [workspaceId]);
  return r.rows[0].n;
}

export async function deleteWorkspace(workspaceId) {
  const r = await q(`DELETE FROM workspaces WHERE id=$1`, [workspaceId]);
  return r.rowCount > 0;
}
