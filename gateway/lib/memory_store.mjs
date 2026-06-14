// Personal long-term memory: per-user notes that are auto-recalled into the chat
// system prompt. CRUD is user-scoped. Pure helpers (buildMemoryBlock / mergeMemory)
// hold the prompt-shaping logic so they stay unit-testable without a DB.
import { q } from "./db.mjs";

// Default on; set MEMORY_ENABLED=0 to disable recall + writes entirely.
export const MEMORY_ENABLED = process.env.MEMORY_ENABLED !== "0";
const MAX_ITEMS = Math.max(1, parseInt(process.env.MEMORY_MAX_ITEMS || "60", 10));
const MAX_CHARS = Math.max(20, parseInt(process.env.MEMORY_MAX_CHARS || "500", 10));

const COLS = "id, content, (extract(epoch from created_at)*1000)::bigint AS created_at";

export async function listMemories(userId) {
  const r = await q(`SELECT ${COLS} FROM user_memory WHERE user_id=$1 ORDER BY created_at DESC LIMIT $2`,
    [userId, MAX_ITEMS]);
  return r.rows;
}

export async function addMemory(userId, content) {
  const text = String(content || "").trim().slice(0, MAX_CHARS);
  if (!text) return null;
  const r = await q(
    `INSERT INTO user_memory (user_id, content) VALUES ($1,$2) RETURNING ${COLS}`,
    [userId, text]);
  return r.rows[0];
}

export async function deleteMemory(userId, id) {
  const r = await q(`DELETE FROM user_memory WHERE user_id=$1 AND id=$2`, [userId, id]);
  return r.rowCount > 0;
}

// Pure: format memory rows into a system-prompt block (empty string if none).
export function buildMemoryBlock(items) {
  const lines = (items || [])
    .map((m) => "- " + String((m && m.content) || "").trim())
    .filter((l) => l.length > 2);
  if (!lines.length) return "";
  return "Kullanıcı hakkında kalıcı notlar (hafıza) — ilgiliyse dikkate al, alakasızsa yok say:\n" + lines.join("\n");
}

// Pure: merge a memory block into a messages array. Prepends to an existing
// system message, or inserts a new system message at the front. Never mutates input.
export function mergeMemory(messages, block) {
  if (!block) return messages;
  const out = [...(messages || [])];
  const sysIdx = out.findIndex((m) => m && m.role === "system");
  if (sysIdx >= 0) {
    const sys = out[sysIdx];
    const base = typeof sys.content === "string" ? sys.content : "";
    out[sysIdx] = { ...sys, content: base ? base + "\n\n" + block : block };
  } else {
    out.unshift({ role: "system", content: block });
  }
  return out;
}

// Async convenience: fetch a user's memories and merge them into messages.
// Never throws — memory must never break a chat request.
export async function withMemory(messages, userId) {
  if (!MEMORY_ENABLED || !userId) return messages;
  try {
    const items = await listMemories(userId);
    return mergeMemory(messages, buildMemoryBlock(items));
  } catch {
    return messages;
  }
}
