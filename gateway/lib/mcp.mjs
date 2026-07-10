// MCP (Model Context Protocol) client — connects external MCP servers over
// Streamable HTTP and exposes their tools to the agent loop. Opt-in via
// MCP_SERVERS. Zero-dependency: JSON-RPC 2.0 over fetch, SSE-or-JSON responses.
//
// The protocol/parse helpers are pure so they can be unit-tested without a live
// server; the network functions (listTools/callTool) are thin wrappers.

const PROTOCOL_VERSION = "2025-06-18";
const TOOL_PREFIX = "mcp__";
const CACHE_MS = Math.max(0, parseInt(process.env.MCP_CACHE_MS || "300000", 10)); // 5 min

// ---- pure helpers ----

// Parse MCP_SERVERS into [{name, url, token}]. Accepts either a JSON array
// ([{"name","url","token"}]) or a comma list of "name=url" pairs.
export function parseServers(raw) {
  const s = String(raw || "").trim();
  if (!s) return [];
  if (s.startsWith("[")) {
    try {
      const arr = JSON.parse(s);
      if (!Array.isArray(arr)) return [];
      return arr
        .map((x) => ({ name: String((x && x.name) || "").trim(), url: String((x && x.url) || "").trim(), token: x && x.token ? String(x.token) : "" }))
        .filter((x) => x.name && /^https?:\/\//.test(x.url));
    } catch { return []; }
  }
  return s.split(",").map((pair) => {
    const i = pair.indexOf("=");
    if (i < 0) return null;
    const name = pair.slice(0, i).trim();
    const url = pair.slice(i + 1).trim();
    return name && /^https?:\/\//.test(url) ? { name, url, token: "" } : null;
  }).filter(Boolean);
}

// MCP tool name <-> prefixed agent tool name (server-scoped to avoid collisions).
export const prefixedName = (server, tool) => TOOL_PREFIX + server + "__" + tool;
export function splitToolName(name) {
  const s = String(name || "");
  if (!s.startsWith(TOOL_PREFIX)) return null;
  const rest = s.slice(TOOL_PREFIX.length);
  const i = rest.indexOf("__");
  if (i < 0) return null;
  return { server: rest.slice(0, i), tool: rest.slice(i + 2) };
}
export const isMcpTool = (name) => String(name || "").startsWith(TOOL_PREFIX);

// Describe agent tool specs as MCP server/tool pairs (for an introspection UI).
// Non-MCP specs are skipped. Pure → testable.
export function describeTools(specs) {
  const out = [];
  for (const s of specs || []) {
    const name = s && s.function && s.function.name;
    const parts = splitToolName(name);
    if (!parts) continue;
    out.push({ server: parts.server, tool: parts.tool, name, description: (s.function.description || "").slice(0, 300) });
  }
  return out;
}

// Convert an MCP tool definition to an Ollama/OpenAI function tool spec.
export function toToolSpec(server, mcpTool) {
  const tool = mcpTool || {};
  const schema = tool.inputSchema && typeof tool.inputSchema === "object"
    ? tool.inputSchema
    : { type: "object", properties: {} };
  return {
    type: "function",
    function: {
      name: prefixedName(server, tool.name),
      description: String(tool.description || ("MCP tool " + tool.name)).slice(0, 1024),
      parameters: schema,
    },
  };
}

// Extract a JSON-RPC payload from a Streamable-HTTP response body, which may be
// either plain JSON or an SSE stream (event: message / data: {json}).
export function parseRpcBody(contentType, bodyText) {
  const ct = String(contentType || "");
  const text = String(bodyText || "");
  if (ct.includes("text/event-stream")) {
    const datas = text.split(/\r?\n/).filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trim());
    for (let i = datas.length - 1; i >= 0; i--) {
      try { return JSON.parse(datas[i]); } catch { /* keep scanning back */ }
    }
    return null;
  }
  try { return JSON.parse(text); } catch { return null; }
}

// Flatten an MCP tools/call result's content array into plain text.
export function flattenContent(result) {
  const content = result && Array.isArray(result.content) ? result.content : [];
  const parts = content.map((c) => {
    if (!c || typeof c !== "object") return "";
    if (c.type === "text") return String(c.text || "");
    if (c.type === "resource" && c.resource) return String(c.resource.text || c.resource.uri || "");
    return "";
  }).filter(Boolean);
  return parts.join("\n").trim();
}

// ---- network (thin) ----

let _id = 0;
const nextId = () => ++_id;

async function rpc(server, method, params, { signal, sessionId, notification = false } = {}) {
  const headers = {
    "Content-Type": "application/json",
    "Accept": "application/json, text/event-stream",
    "MCP-Protocol-Version": PROTOCOL_VERSION,
  };
  if (server.token) headers["Authorization"] = "Bearer " + server.token;
  if (sessionId) headers["Mcp-Session-Id"] = sessionId;
  const body = notification
    ? { jsonrpc: "2.0", method, params }
    : { jsonrpc: "2.0", id: nextId(), method, params };
  const r = await fetch(server.url, { method: "POST", headers, body: JSON.stringify(body), signal });
  const newSession = r.headers.get("mcp-session-id") || sessionId || null;
  if (notification) return { sessionId: newSession };
  if (!r.ok) throw new Error("mcp " + r.status + " " + (await r.text()).slice(0, 200));
  const parsed = parseRpcBody(r.headers.get("content-type"), await r.text());
  if (parsed && parsed.error) throw new Error("mcp rpc: " + (parsed.error.message || JSON.stringify(parsed.error)));
  return { result: parsed && parsed.result, sessionId: newSession };
}

// Handshake + tools/list for one server. Returns { specs, session }.
async function listServerTools(server, signal) {
  const init = await rpc(server, "initialize", {
    protocolVersion: PROTOCOL_VERSION,
    capabilities: {},
    clientInfo: { name: "nova-gateway", version: "1.0.0" },
  }, { signal });
  const session = init.sessionId;
  await rpc(server, "notifications/initialized", {}, { signal, sessionId: session, notification: true }).catch(() => {});
  const listed = await rpc(server, "tools/list", {}, { signal, sessionId: session });
  const tools = (listed.result && Array.isArray(listed.result.tools)) ? listed.result.tools : [];
  return { specs: tools.map((t) => ({ spec: toToolSpec(server.name, t), raw: t })), session };
}

// Cached load of all configured servers' tools + a dispatch function.
// Returns { specs:[toolSpec...], dispatch(name,args,ctx) } — empty when disabled.
let _cache = null; // { at, specs, routes:Map<prefixedName,{server,tool,session}> }

export async function getMcpTools(signal, nowMs = Date.now()) {
  const servers = parseServers(process.env.MCP_SERVERS);
  if (!servers.length) return { specs: [], dispatch: null };
  if (_cache && (nowMs - _cache.at) < CACHE_MS) return _cache.tools;

  const specs = [];
  const routes = new Map();
  for (const server of servers) {
    try {
      const { specs: list, session } = await listServerTools(server, signal);
      for (const { spec, raw } of list) {
        specs.push(spec);
        routes.set(spec.function.name, { server, tool: raw.name, session });
      }
    } catch { /* a down server must not break the agent */ }
  }

  const dispatch = async (name, args, ctx) => {
    const route = routes.get(name);
    if (!route) return { ok: false, name, text: "Bilinmeyen MCP aracı: " + name };
    try {
      const out = await rpc(route.server, "tools/call", { name: route.tool, arguments: args || {} },
        { signal: ctx && ctx.signal, sessionId: route.session });
      const text = flattenContent(out.result) || "(boş MCP sonucu)";
      const isErr = out.result && out.result.isError;
      return { ok: !isErr, name, text, sources: [] };
    } catch (e) {
      return { ok: false, name, text: "MCP hatası (" + name + "): " + ((e && e.message) || e) };
    }
  };

  const tools = { specs, dispatch };
  _cache = { at: nowMs, tools };
  return tools;
}

// Test seam: clear the in-memory tool cache.
export function _resetCache() { _cache = null; }
