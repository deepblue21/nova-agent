// Akış motoru: sağlayıcıya özgü SSE/NDJSON protokollerini tek bir
// onToken/onThought/onTool/onRoute arayüzüne indirger.
import { trim, dataUrlParts } from "./format.mjs";

export async function readStream(res, onLine) {
  if (!res.ok || !res.body) {
    let t = "";
    try { t = await res.text(); } catch (e) {}
    throw new Error("HTTP " + res.status + " " + t.slice(0, 200));
  }
  const reader = res.body.getReader();
  const dec = new TextDecoder();
  let buf = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf("\n")) >= 0) {
      onLine(buf.slice(0, i));
      buf = buf.slice(i + 1);
    }
  }
  if (buf.trim()) onLine(buf);
}

export const sseHandler = (onObj) => (line) => {
  const l = line.trim();
  if (!l.startsWith("data:")) return;
  const d = l.slice(5).trim();
  if (!d || d === "[DONE]") return;
  try { onObj(JSON.parse(d)); } catch (e) {}
};

export const ndjsonHandler = (onObj) => (line) => {
  const l = line.trim();
  if (!l) return;
  try { onObj(JSON.parse(l)); } catch (e) {}
};

export { needsLiveData as needsLiveTool } from './live_intent.mjs';

/* ---- çoklu-medya mesaj adaptörleri (m.images: data URL dizisi) ---- */

export function oaContent(m) {
  if (!m.images || !m.images.length) return m.content;
  const arr = [];
  if (m.content) arr.push({ type: "text", text: m.content });
  for (const u of m.images) arr.push({ type: "image_url", image_url: { url: u } });
  return arr;
}

export function ollamaMsg(m) {
  const o = { role: m.role, content: m.content || "" };
  if (m.images && m.images.length) o.images = m.images.map((u) => dataUrlParts(u).b64);
  return o;
}

export function geminiParts(m) {
  const parts = [];
  if (m.content) parts.push({ text: m.content });
  (m.images || []).forEach((u) => {
    const { mime, b64 } = dataUrlParts(u);
    parts.push({ inlineData: { mimeType: mime, data: b64 } });
  });
  return parts.length ? parts : [{ text: "" }];
}

export function anthroMsg(m) {
  if (!m.images || !m.images.length) return { role: m.role, content: m.content };
  const blocks = m.images.map((u) => {
    const { mime, b64 } = dataUrlParts(u);
    return { type: "image", source: { type: "base64", media_type: mime, data: b64 } };
  });
  if (m.content) blocks.push({ type: "text", text: m.content });
  return { role: m.role, content: blocks };
}

export async function streamChat({
  prov, model, system, history, think,
  onToken, signal, extra, onRoute, onThought, onTool,
}) {
  const kind = prov.kind;
  const H = { "Content-Type": "application/json" };

  if (kind === "ollama") {
    const res = await fetch(trim(prov.baseUrl) + "/api/chat", {
      method: "POST", headers: H, signal,
      body: JSON.stringify({
        model, stream: true, think: !!think,
        messages: [{ role: "system", content: system }, ...history.map(ollamaMsg)],
      }),
    });
    await readStream(res, ndjsonHandler((o) => {
      const th = o && o.message && o.message.thinking;
      if (th && onThought) onThought(th);
      const t = o && o.message && o.message.content;
      if (t) onToken(t);
    }));
    return;
  }

  if (kind === "gemini") {
    const url = trim(prov.baseUrl) + "/v1beta/models/" + model +
      ":streamGenerateContent?alt=sse&key=" + encodeURIComponent(prov.apiKey);
    const contents = history.map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: geminiParts(m),
    }));
    const body = { contents, systemInstruction: { parts: [{ text: system }] } };
    if (think) body.generationConfig = { thinkingConfig: { includeThoughts: true } };
    const res = await fetch(url, { method: "POST", headers: H, signal, body: JSON.stringify(body) });
    await readStream(res, sseHandler((o) => {
      const parts = (o.candidates && o.candidates[0] && o.candidates[0].content && o.candidates[0].content.parts) || [];
      for (const p of parts) {
        if (!p.text) continue;
        if (p.thought && onThought) onThought(p.text);
        else onToken(p.text);
      }
    }));
    return;
  }

  if (kind === "anthropic") {
    if (prov.apiKey) {
      const body = {
        model, max_tokens: think ? 3072 : 1024, system,
        messages: history.map(anthroMsg), stream: true,
      };
      if (think) body.thinking = { type: "enabled", budget_tokens: 1536 };
      const res = await fetch(trim(prov.baseUrl) + "/v1/messages", {
        method: "POST", signal,
        headers: {
          ...H,
          "x-api-key": prov.apiKey,
          "anthropic-version": "2023-06-01",
          "anthropic-dangerous-direct-browser-access": "true",
        },
        body: JSON.stringify(body),
      });
      await readStream(res, sseHandler((o) => {
        if (o.type !== "content_block_delta" || !o.delta) return;
        if (o.delta.type === "thinking_delta" && o.delta.thinking) {
          if (onThought) onThought(o.delta.thinking);
          return;
        }
        if (o.delta.text) onToken(o.delta.text);
      }));
      return;
    }
    throw new Error('Anthropic API anahtarı gerekli. Ayarlar’dan bir anahtar girin veya Gateway kullanın.');
  }

  // openai uyumlu (openai, gateway, openrouter, ollama /v1 …)
  const res = await fetch(trim(prov.baseUrl) + "/chat/completions", {
    method: "POST", signal,
    headers: prov.apiKey ? { ...H, Authorization: "Bearer " + prov.apiKey } : H,
    body: JSON.stringify({
      model, stream: true,
      messages: [
        { role: "system", content: system },
        ...history.map((m) => ({ role: m.role, content: oaContent(m) })),
      ],
      ...(extra || {}),
    }),
  });
  if (onRoute) {
    try {
      const r = res.headers.get("x-nova-route");
      if (r) onRoute(r);
    } catch (e) {}
  }
  await readStream(res, sseHandler((o) => {
    const d = o.choices && o.choices[0] && o.choices[0].delta;
    if (d && d.reasoning_content && onThought) onThought(d.reasoning_content);   // gateway think relay
    if (d && d.tool_step && onTool) {                                            // ajan: yapısal araç adımı
      const ts = d.tool_step;
      const q = ts.args && (ts.args.query || ts.args.location || ts.args.expression || ts.args.role || "");
      onTool({ name: ts.name, q, done: !!ts.done, sources: ts.sources || [] });
    }
    const t = d && d.content;
    if (t) onToken(t);
  }));
}

/** Hata mesajını kullanıcıya çözüm önerecek biçimde çevirir. */
export function errHint(e, prov) {
  const m = (e && e.message) || String(e);
  if (e && e.name === "AbortError") return "";
  const net = /Failed to fetch|NetworkError|TypeError|load failed/i.test(m);
  if (prov.kind === "ollama") {
    return "⚠️ Ollama'ya ulaşılamadı (" + prov.baseUrl +
      "). Açık mı? Tarayıcı erişimi için `OLLAMA_ORIGINS=* ollama serve`. Detay: " + m;
  }
  if (/HTTP 401|unauthorized/i.test(m)) {
    return "⚠️ Gateway yetki hatası. Canlı web/hava araçları için Ayarlar → Gateway → Anahtar alanına " +
      "`gateway/.env` içindeki `GATEWAY_TOKEN` değerini yapıştır. Detay: " + m;
  }
  if (net) {
    return "⚠️ Bağlantı/CORS engeli. Tarayıcıdan doğrudan çağrı kapalı olabilir — Gateway'i çalıştırıp " +
      "'Dinamik Yönlendirme' (gateway) sağlayıcısını seç. Detay: " + m;
  }
  return "⚠️ Hata: " + m;
}
