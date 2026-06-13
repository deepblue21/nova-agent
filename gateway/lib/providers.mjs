const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const backoff = (n) => Math.min(4000, 400 * Math.pow(2, n)) + Math.random() * 200;

export function routeModel(model, defaultModel) {
  const selected = (!model || model === "auto") ? defaultModel : model;
  const i = selected.indexOf("/");
  if (i < 0) return { provider: "ollama", model: selected };
  return { provider: selected.slice(0, i), model: selected.slice(i + 1) };
}

export function pickParams(body) {
  const p = {};
  if (body && body.max_tokens != null) p.max_tokens = body.max_tokens;
  if (body && body.temperature != null) p.temperature = body.temperature;
  if (body && body.top_p != null) p.top_p = body.top_p;
  return p;
}

export function sse(res) {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
}

export const emit = (res, text) =>
  res.write("data: " + JSON.stringify({ choices: [{ delta: { content: text } }] }) + "\n\n");

export const finish = (res) => {
  res.write("data: [DONE]\n\n");
  res.end();
};

async function eachLine(body, cb) {
  const reader = body.getReader();
  const dec = new TextDecoder();
  let buf = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf("\n")) >= 0) {
      cb(buf.slice(0, i));
      buf = buf.slice(i + 1);
    }
  }
  if (buf.trim()) cb(buf);
}

function sseLine(pick, observe) {
  return (line) => {
    line = line.trim();
    if (!line.startsWith("data:")) return "";
    const d = line.slice(5).trim();
    if (!d || d === "[DONE]") return "";
    try {
      const o = JSON.parse(d);
      observe?.(o);
      return pick(o) || "";
    } catch {
      return "";
    }
  };
}

export function normMsg(m = {}) {
  if (typeof m.content === "string") return { role: m.role, text: m.content, images: [] };
  const text = [];
  const images = [];
  for (const part of (m.content || [])) {
    if (part.type === "text") text.push(part.text || "");
    else if (part.type === "image_url") {
      const u = (part.image_url && part.image_url.url) || "";
      const mm = /^data:([^;]+);base64,(.*)$/i.exec(u);
      if (mm) images.push({ mime: mm[1], b64: mm[2] });
    }
  }
  return { role: m.role, text: text.join("\n"), images };
}

export function messageText(m = {}) {
  return normMsg(m).text || "";
}

function sysText(messages) {
  return messages
    .filter(m => m.role === "system")
    .map(m => typeof m.content === "string" ? m.content : "")
    .join("\n");
}

function pickReply(d) {
  if (typeof d === "string") return d;
  return d?.reply || d?.message?.content || d?.message || d?.response || d?.text ||
         d?.choices?.[0]?.message?.content || (d ? JSON.stringify(d).slice(0, 800) : "");
}

async function relay(res, stream, body, lineToToken) {
  if (stream) sse(res);
  let full = "";
  await eachLine(body, (line) => {
    const t = lineToToken(line);
    if (!t) return;
    const think = typeof t === "object" ? (t.think || "") : "";
    const text = typeof t === "object" ? (t.text || "") : t;
    if (think && stream) {
      res.write("data: " + JSON.stringify({ choices: [{ delta: { reasoning_content: think } }] }) + "\n\n");
    }
    if (text) {
      full += text;
      if (stream) emit(res, text);
    }
  });
  if (stream) {
    finish(res);
    return full;
  }
  res.json({ choices: [{ message: { role: "assistant", content: full } }] });
  return full;
}

export function createProviderClient({
  keys = {},
  ollamaUrl = "http://localhost:11434",
  openclawUrl = "http://localhost:3000",
  openclawToken = "",
  openclawPath = "/api/agents/{agent}/messages",
  maxRetries = 2,
} = {}) {
  async function upFetch(url, init, ctx) {
    const retries = (ctx && ctx.retries != null) ? ctx.retries : maxRetries;
    let attempt = 0;
    let lastErr;
    for (;;) {
      try {
        const r = await fetch(url, { ...init, signal: ctx && ctx.signal });
        if (r.ok) return r;
        if ((r.status === 429 || r.status >= 500) && attempt < retries) {
          await sleep(backoff(attempt));
          attempt++;
          continue;
        }
        throw new Error(r.status + " " + (await r.text()).slice(0, 200));
      } catch (e) {
        if (e && e.name === "AbortError") throw e;
        if (attempt < retries) {
          lastErr = e;
          await sleep(backoff(attempt));
          attempt++;
          continue;
        }
        throw lastErr || e;
      }
    }
  }

  async function viaOllama(res, model, messages, stream, ctx) {
    const p = ctx.params || {};
    const options = {};
    if (p.temperature != null) options.temperature = p.temperature;
    if (p.top_p != null) options.top_p = p.top_p;
    if (p.max_tokens != null) options.num_predict = p.max_tokens;
    const msgs = messages.map(m => {
      const n = normMsg(m);
      const o = { role: n.role, content: n.text };
      if (n.images.length) o.images = n.images.map(i => i.b64);
      return o;
    });
    const r = await upFetch(ollamaUrl.replace(/\/$/, "") + "/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages: msgs, stream: true, think: !!ctx.think, options }),
    }, ctx).catch(e => { throw new Error("ollama " + e.message); });
    return relay(res, stream, r.body, (line) => {
      line = line.trim();
      if (!line) return "";
      try {
        const o = JSON.parse(line);
        ctx.usage?.observe(o);
        const m = o?.message;
        if (m && (m.thinking || m.content)) return { think: m.thinking || "", text: m.content || "" };
        return "";
      } catch {
        return "";
      }
    });
  }

  async function viaOpenAI(res, model, messages, stream, ctx) {
    if (!keys.openai) throw new Error("OPENAI_API_KEY not set");
    const r = await upFetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + keys.openai },
      body: JSON.stringify({
        model,
        messages,
        stream: true,
        stream_options: { include_usage: true },
        ...(ctx.params || {}),
      }),
    }, ctx).catch(e => { throw new Error("openai " + e.message); });
    return relay(res, stream, r.body, sseLine(
      (o) => o?.choices?.[0]?.delta?.content || "",
      (o) => ctx.usage?.observe(o),
    ));
  }

  async function viaGemini(res, model, messages, stream, ctx) {
    if (!keys.gemini) throw new Error("GEMINI_API_KEY not set");
    const sys = sysText(messages);
    const contents = messages.filter(m => m.role !== "system").map(m => {
      const n = normMsg(m);
      const parts = [];
      if (n.text) parts.push({ text: n.text });
      n.images.forEach(i => parts.push({ inlineData: { mimeType: i.mime, data: i.b64 } }));
      return { role: m.role === "assistant" ? "model" : "user", parts: parts.length ? parts : [{ text: "" }] };
    });
    const p = ctx.params || {};
    const gen = {};
    if (p.temperature != null) gen.temperature = p.temperature;
    if (p.top_p != null) gen.topP = p.top_p;
    if (p.max_tokens != null) gen.maxOutputTokens = p.max_tokens;
    const url = "https://generativelanguage.googleapis.com/v1beta/models/" + model +
      ":streamGenerateContent?alt=sse&key=" + encodeURIComponent(keys.gemini);
    const r = await upFetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents,
        systemInstruction: sys ? { parts: [{ text: sys }] } : undefined,
        generationConfig: Object.keys(gen).length ? gen : undefined,
      }),
    }, ctx).catch(e => { throw new Error("gemini " + e.message); });
    return relay(res, stream, r.body, sseLine(
      (o) => (o?.candidates?.[0]?.content?.parts || []).map(p => p.text || "").join(""),
      (o) => ctx.usage?.observe(o),
    ));
  }

  async function viaAnthropic(res, model, messages, stream, ctx) {
    if (!keys.anthropic) throw new Error("ANTHROPIC_API_KEY not set");
    const sys = sysText(messages);
    const msgs = messages.filter(m => m.role !== "system").map(m => {
      const n = normMsg(m);
      if (!n.images.length) return { role: n.role, content: n.text };
      const blocks = n.images.map(i => ({
        type: "image",
        source: { type: "base64", media_type: i.mime, data: i.b64 },
      }));
      if (n.text) blocks.push({ type: "text", text: n.text });
      return { role: n.role, content: blocks };
    });
    const p = ctx.params || {};
    const body = {
      model,
      max_tokens: p.max_tokens || 1024,
      system: sys || undefined,
      messages: msgs,
      stream: true,
    };
    if (p.temperature != null) body.temperature = p.temperature;
    if (p.top_p != null) body.top_p = p.top_p;
    const r = await upFetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": keys.anthropic,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify(body),
    }, ctx).catch(e => { throw new Error("anthropic " + e.message); });
    return relay(res, stream, r.body, sseLine(
      (o) => o.type === "content_block_delta" && o.delta?.text ? o.delta.text : "",
      (o) => ctx.usage?.observe(o),
    ));
  }

  async function viaOpenClaw(res, agent, messages, stream, ctx) {
    const path = openclawPath.replace("{agent}", agent || "default");
    const lastUser = [...messages].reverse().find(m => m.role === "user")?.content || "";
    const r = await upFetch(openclawUrl.replace(/\/$/, "") + path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(openclawToken ? { Authorization: "Bearer " + openclawToken } : {}),
      },
      body: JSON.stringify({ messages, message: lastUser, stream: !!stream }),
    }, ctx).catch(e => { throw new Error("openclaw " + e.message); });

    const ct = (r.headers.get("content-type") || "").toLowerCase();
    if (ct.includes("event-stream")) {
      return relay(res, stream, r.body, sseLine((o) =>
        o?.delta?.content || o?.choices?.[0]?.delta?.content || o?.token || o?.text || ""));
    }
    if (ct.includes("ndjson") || ct.includes("x-ndjson")) {
      return relay(res, stream, r.body, (line) => {
        line = line.trim();
        if (!line) return "";
        try {
          const o = JSON.parse(line);
          return o?.delta?.content || o?.message?.content || o?.token || o?.text || "";
        } catch {
          return "";
        }
      });
    }
    const text = ct.includes("json") ? pickReply(await r.json()) : await r.text();
    if (stream) {
      sse(res);
      if (text) emit(res, text);
      finish(res);
      return text;
    }
    res.json({ choices: [{ message: { role: "assistant", content: text } }] });
    return text;
  }

  return {
    async chat({ provider, model, messages, stream, ctx, res }) {
      if (provider === "openclaw") return viaOpenClaw(res, model, messages, stream, ctx);
      if (provider === "ollama") return viaOllama(res, model, messages, stream, ctx);
      if (provider === "gemini") return viaGemini(res, model, messages, stream, ctx);
      if (provider === "anthropic") return viaAnthropic(res, model, messages, stream, ctx);
      if (provider === "openai") return viaOpenAI(res, model, messages, stream, ctx);
      throw new Error("unknown provider: " + provider);
    },
  };
}
