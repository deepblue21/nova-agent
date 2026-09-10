// Agent run history: persist a compact summary of each agent/team run.
// recordRun is best-effort and must never break a chat (callers catch).
import { q } from "./db.mjs";

const COLS = "id, mode, model, prompt, tools, result, rounds, (extract(epoch from created_at)*1000)::bigint AS created_at";

// Pure: summarize a tool-name list as "web_search×2, calculator". Testable.
export function formatRunTools(toolsUsed) {
  const counts = new Map();
  for (const t of toolsUsed || []) {
    const name = String(t || "").trim();
    if (!name) continue;
    counts.set(name, (counts.get(name) || 0) + 1);
  }
  return [...counts.entries()].map(([n, c]) => (c > 1 ? `${n}×${c}` : n)).join(", ");
}

/**
 * PC devri (OpenClaw) bir ajan koşumudur — Faz 11.
 *
 * Neden ayrı bir yardımcı: `/v1/chat/completions` içindeki ajan döngüsü
 * `provider === "ollama"` ile sınırlı. OpenClaw `provider: "openclaw"` ile
 * kayıtlı olduğu için o dala HİÇ girmiyor, dolayısıyla telefondan devredilen
 * iş `agent_runs`'a yazılmıyordu. Android tarafındaki KDoc ise "otomatik
 * kaydolur" diyordu; yani kod kendi hakkında yanlış konuşuyordu.
 *
 * Kural dar tutuldu: SADECE openclaw. `agent: true` gönderilmiş bir bulut
 * modeli ajan koşumu DEĞİLDİR (araç döngüsü çalışmadı), onu geçmişe yazmak
 * ilkini düzeltirken ikinci bir yalan üretirdi.
 *
 * @returns kaydedilecek koşum nesnesi ya da null (kayıt yok).
 */
export function openclawRunFromCompletion({ provider, model, prompt, result } = {}) {
  if (provider !== "openclaw") return null;
  return {
    mode: "openclaw",
    model: model || null,
    prompt: String(prompt || ""),
    // Araç izi yok: OpenClaw yanıtı düz metin olarak röle ediliyor, hangi
    // araçları çağırdığını gateway görmüyor. Uydurmak yerine boş bırakılır.
    tools: "",
    result: String(result || ""),
    rounds: 0,
  };
}

export async function recordRun(userId, { mode, model, prompt, tools, result, rounds }) {
  if (!userId) return;
  await q(
    `INSERT INTO agent_runs (user_id, mode, model, prompt, tools, result, rounds)
     VALUES ($1,$2,$3,$4,$5,$6,$7)`,
    [userId, mode, model || null, String(prompt || "").slice(0, 2000),
     String(tools || "").slice(0, 500), String(result || "").slice(0, 8000), rounds || 0]);
}

export async function listRuns(userId, limit = 30) {
  const r = await q(`SELECT ${COLS} FROM agent_runs WHERE user_id=$1 ORDER BY created_at DESC LIMIT $2`,
    [userId, Math.min(100, Math.max(1, limit))]);
  return r.rows;
}

export async function deleteRun(userId, id) {
  const r = await q(`DELETE FROM agent_runs WHERE user_id=$1 AND id=$2`, [userId, id]);
  return r.rowCount > 0;
}
