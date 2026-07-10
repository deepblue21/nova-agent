// Conversation + message persistence (scoped to the owning user).
import { q } from "./db.mjs";

export async function listConversations(userId, limit = 50) {
  const { rows } = await q(
    `SELECT id, title, created_at, updated_at FROM conversations
      WHERE user_id = $1 ORDER BY updated_at DESC LIMIT $2`, [userId, limit]);
  return rows;
}

export async function getConversation(userId, id) {
  const { rows } = await q(
    "SELECT id, title, created_at, updated_at FROM conversations WHERE id = $1 AND user_id = $2",
    [id, userId]);
  if (!rows.length) return null;
  const msgs = await q(
    `SELECT role, content, model, route, tokens_in, tokens_out, created_at
       FROM messages WHERE conversation_id = $1 ORDER BY created_at`, [id]);
  return { ...rows[0], messages: msgs.rows };
}

export async function createConversation(userId, title) {
  const { rows } = await q(
    `INSERT INTO conversations (user_id, title)
     VALUES ($1, COALESCE($2, 'Yeni sohbet'))
     RETURNING id, title, created_at, updated_at`, [userId, title || null]);
  return rows[0];
}

export async function appendMessage(conversationId, m) {
  const { rows } = await q(
    `INSERT INTO messages (conversation_id, role, content, model, route, tokens_in, tokens_out)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id, created_at`,
    [conversationId, m.role, m.content || "", m.model || null, m.route || null,
     m.tokens_in || 0, m.tokens_out || 0]);
  await q("UPDATE conversations SET updated_at = now() WHERE id = $1", [conversationId]);
  return rows[0];
}

export async function deleteConversation(userId, id) {
  const { rowCount } = await q(
    "DELETE FROM conversations WHERE id = $1 AND user_id = $2", [id, userId]);
  return rowCount > 0;
}
