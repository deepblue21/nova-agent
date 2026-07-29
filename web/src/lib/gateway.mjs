// Gateway REST istemcisi. Tüm uçlar tek yerde toplandı; bileşenler fetch
// ayrıntısını bilmez. `prov` = { baseUrl, apiKey } (providers.gateway).
import { trim } from "./format.mjs";

/** ".../v1" son ekini atarak kök adresi verir. */
export const gwBase = (prov) => trim(prov && prov.baseUrl).replace(/\/v1$/, "");

const authHeaders = (prov) => (prov && prov.apiKey ? { Authorization: "Bearer " + prov.apiKey } : {});
const jsonHeaders = (prov) => ({ "Content-Type": "application/json", ...authHeaders(prov) });

async function req(prov, path, { method = "GET", body, headers, signal } = {}) {
  const res = await fetch(gwBase(prov) + path, {
    method,
    headers: headers || (body ? jsonHeaders(prov) : authHeaders(prov)),
    body: body ? JSON.stringify(body) : undefined,
    signal,
  });
  return res;
}

async function jsonOrNull(prov, path, opts) {
  try {
    const r = await req(prov, path, opts);
    if (!r.ok) return null;
    if (r.status === 204) return {};
    return await r.json();
  } catch (e) {
    return null;
  }
}

/** { ok, data|error } biçiminde; hata mesajı kullanıcıya gösterilebilir. */
async function result(prov, path, opts) {
  try {
    const r = await req(prov, path, opts);
    if (r.ok || r.status === 204) {
      const data = r.status === 204 ? {} : await r.json().catch(() => ({}));
      return { ok: true, data };
    }
    let error = "HTTP " + r.status;
    try { error = (await r.json()).error || error; } catch (e) {}
    return { ok: false, error, status: r.status };
  } catch (e) {
    return { ok: false, error: (e && e.message) || "İstek başarısız" };
  }
}

const list = async (prov, path) => ((await jsonOrNull(prov, path)) || {}).data || [];

/* ------------------------------- katalog -------------------------------- */

export async function fetchModels(prov, refresh) {
  const r = await fetch(gwBase(prov) + "/v1/models" + (refresh ? "?refresh=1" : ""), {
    headers: authHeaders(prov),
  });
  if (!r.ok) throw new Error("HTTP " + r.status);
  const d = await r.json();
  if (!d || !Array.isArray(d.data)) throw new Error("beklenmeyen yanıt");
  return d;
}

export const fetchHealth = (prov) => jsonOrNull(prov, "/health");
export const fetchUsage = (prov) => jsonOrNull(prov, "/v1/usage");

/* ---------------------------- bilgi tabanı ------------------------------ */

export const listDocs = (prov) => list(prov, "/v1/knowledge");
export const createDoc = (prov, body) => result(prov, "/v1/knowledge", { method: "POST", body });
export const deleteDoc = (prov, id) => result(prov, "/v1/knowledge/" + id, { method: "DELETE" });

/* -------------------------------- hafıza -------------------------------- */

export const listMems = (prov) => list(prov, "/v1/memory");
export const createMem = (prov, body) => result(prov, "/v1/memory", { method: "POST", body });
export const deleteMem = (prov, id) => result(prov, "/v1/memory/" + id, { method: "DELETE" });

/* --------------------------------- eval --------------------------------- */

export const runEval = (prov, body) => result(prov, "/v1/eval", { method: "POST", body });

/* ------------------------------ workspaces ------------------------------ */

export const listWorkspaces = (prov) => list(prov, "/v1/workspaces");
export const createWorkspace = (prov, name) => result(prov, "/v1/workspaces", { method: "POST", body: { name } });
export const listMembers = (prov, id) => list(prov, "/v1/workspaces/" + id + "/members");
export const inviteMember = (prov, id, body) =>
  result(prov, "/v1/workspaces/" + id + "/members", { method: "POST", body });
export const changeMemberRole = (prov, id, userId, role) =>
  result(prov, "/v1/workspaces/" + id + "/members/" + userId, { method: "PATCH", body: { role } });
export const removeMember = (prov, id, userId) =>
  result(prov, "/v1/workspaces/" + id + "/members/" + userId, { method: "DELETE" });

/* --------------------------- zamanlanmış görev -------------------------- */

export const listScheduled = (prov) => list(prov, "/v1/scheduled");
export const createScheduled = (prov, body) => result(prov, "/v1/scheduled", { method: "POST", body });
export const patchScheduled = (prov, id, body) => result(prov, "/v1/scheduled/" + id, { method: "PATCH", body });
export const deleteScheduled = (prov, id) => result(prov, "/v1/scheduled/" + id, { method: "DELETE" });

/* ------------------------------- ajan izi ------------------------------- */

export const listAgentRuns = (prov) => list(prov, "/v1/agent/runs");
export const deleteAgentRun = (prov, id) => result(prov, "/v1/agent/runs/" + id, { method: "DELETE" });

/* --------------------------------- MCP ---------------------------------- */

export const fetchMcpTools = (prov) => jsonOrNull(prov, "/v1/mcp/tools");

/* ------------------------------ sohbetler ------------------------------- */

export const listConversations = (prov) => list(prov, "/v1/conversations");
export const getConversation = (prov, id) => jsonOrNull(prov, "/v1/conversations/" + id);
export const createConversation = (prov, title) =>
  jsonOrNull(prov, "/v1/conversations", { method: "POST", body: { title } });

/* -------------------------------- medya --------------------------------- */

export function archiveMedia(prov, dataUrl) {
  return req(prov, "/v1/media", { method: "POST", body: { data_url: dataUrl } }).catch(() => {});
}

/* --------------------------- mobil görevler ----------------------------- */
// Android istemcisiyle aynı uçlar: web ve telefon aynı görev listesini görür.

export const listMobileTasks = (prov, limit = 50) => list(prov, "/v1/mobile/tasks?limit=" + limit);
export const getMobileTask = (prov, id) => jsonOrNull(prov, "/v1/mobile/tasks/" + id);
export const createMobileTask = (prov, prompt) =>
  result(prov, "/v1/mobile/tasks", { method: "POST", body: { prompt } });
export const commandMobileTask = (prov, id, command, note = "") =>
  result(prov, "/v1/mobile/tasks/" + id + "/commands", { method: "POST", body: { command, note } });
export const resolveMobileConfirmation = (prov, id, confirmationId, decision) =>
  result(prov, "/v1/mobile/tasks/" + id + "/confirmations/" + confirmationId, {
    method: "POST",
    body: { decision },
  });

/**
 * Görev olay akışı (SSE). EventSource başlık gönderemediği için fetch+stream
 * kullanılır; böylece Bearer belirteci Android istemcisiyle aynı şekilde gider.
 * Dönen fonksiyon akışı kapatır.
 */
export function streamMobileTaskEvents(prov, taskId, { onEvent, onError, lastEventId } = {}) {
  const ctrl = new AbortController();
  (async () => {
    try {
      const res = await fetch(gwBase(prov) + "/v1/mobile/tasks/" + taskId + "/events", {
        headers: {
          Accept: "text/event-stream",
          ...authHeaders(prov),
          ...(lastEventId ? { "Last-Event-ID": String(lastEventId) } : {}),
        },
        signal: ctrl.signal,
      });
      if (!res.ok || !res.body) throw new Error("HTTP " + res.status);
      const reader = res.body.getReader();
      const dec = new TextDecoder();
      let buf = "";
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        let sep;
        while ((sep = buf.indexOf("\n\n")) >= 0) {
          const raw = buf.slice(0, sep);
          buf = buf.slice(sep + 2);
          if (!raw.trim() || raw.startsWith(":")) continue;       // heartbeat
          const dataLine = raw.split("\n").find((l) => l.startsWith("data:"));
          if (!dataLine) continue;
          try { onEvent && onEvent(JSON.parse(dataLine.slice(5).trim())); } catch (e) {}
        }
      }
    } catch (e) {
      if (e && e.name === "AbortError") return;
      onError && onError((e && e.message) || "Olay akışı koptu");
    }
  })();
  return () => ctrl.abort();
}
