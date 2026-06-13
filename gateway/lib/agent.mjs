// Ajan döngüsü: Ollama native tool-calling. Model araç çağırır → gateway
// çalıştırır → sonucu geri besler → model nihai cevabı üretir. Maks N tur.
// Akış (onStep) ile UI'a "araç kullanılıyor" izleri verilir.
import { TOOL_SPECS, runTool } from "./tools.mjs";
import { agentRuns, agentToolCalls, agentToolDuration } from "./metrics.mjs";

const MAX_ROUNDS = parseInt(process.env.AGENT_MAX_ROUNDS || "4", 10);

// ollama mesaj normalize (multimodal değil, ajan turu düz metin)
function toOllama(messages) {
  return messages.map(m => {
    const o = { role: m.role, content: typeof m.content === "string" ? m.content : "" };
    if (m.tool_calls) o.tool_calls = m.tool_calls;
    if (m.tool_name) o.tool_name = m.tool_name;
    return o;
  });
}

// Tek bir Ollama /api/chat çağrısı (stream yok, tools ile)
async function ollamaChat(ollamaBase, model, messages, tools, signal) {
  const r = await fetch(ollamaBase.replace(/\/$/, "") + "/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages: toOllama(messages), tools, stream: false }),
    signal,
  });
  if (!r.ok) throw new Error("ollama " + r.status + " " + (await r.text()).slice(0, 200));
  const d = await r.json();
  return d.message || {};
}

// messages: OpenAI tarzı [{role,content}]. system zaten içinde.
// onStep(evt): { type:"tool_call"|"tool_result", name, args?, text? }
// Döner: { content, sources:[], rounds, toolsUsed:[] }
export async function runAgent({ ollamaBase, model, messages, signal, onStep, userId }) {
  agentRuns.inc();
  const convo = [...messages];
  const sources = [];
  const toolsUsed = [];
  let rounds = 0;

  for (; rounds < MAX_ROUNDS; rounds++) {
    const msg = await ollamaChat(ollamaBase, model, convo, TOOL_SPECS, signal);
    const calls = msg.tool_calls || [];
    if (!calls.length) {
      return { content: msg.content || "", sources, rounds, toolsUsed };
    }
    // asistanın araç çağrısı mesajını geçmişe ekle
    convo.push({ role: "assistant", content: msg.content || "", tool_calls: calls });
    for (const c of calls) {
      const name = c.function && c.function.name;
      let args = c.function && c.function.arguments;
      if (typeof args === "string") { try { args = JSON.parse(args); } catch (e) { args = {}; } }
      toolsUsed.push(name);
      onStep && onStep({ type: "tool_call", name, args });
      const toolLabel = name || "unknown";
      const t0 = process.hrtime.bigint();
      let res;
      try {
        res = await runTool(name, args, { signal, userId });
      } finally {
        agentToolDuration.observe({ tool: toolLabel }, Number(process.hrtime.bigint() - t0) / 1e9);
      }
      agentToolCalls.inc({ tool: toolLabel, status: res && res.ok === false ? "error" : "ok" });
      if (res.sources && res.sources.length) for (const s of res.sources) sources.push(s);
      onStep && onStep({ type: "tool_result", name, text: res.text, sources: res.sources || [] });
      convo.push({ role: "tool", tool_name: name, content: res.text });
    }
  }
  // tur limiti: son bir kez araçsız özet iste
  const fin = await ollamaChat(ollamaBase, model, convo, undefined, signal);
  return { content: fin.content || "", sources, rounds, toolsUsed };
}
