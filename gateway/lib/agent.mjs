// Ajan döngüsü: Ollama native tool-calling. Model araç çağırır → gateway
// çalıştırır → sonucu geri besler → model nihai cevabı üretir. Maks N tur.
// Akış (onStep) ile UI'a "araç kullanılıyor" izleri verilir.
import { TOOL_SPECS, runTool } from "./tools.mjs";
import { agentRuns, agentToolCalls, agentToolDuration } from "./metrics.mjs";

const MAX_ROUNDS = parseInt(process.env.AGENT_MAX_ROUNDS || "4", 10);

// Canlı/güncel veri gerektiren sorgular: bu durumda gateway ajan modunu otomatik
// açar ki web_search çalışsın (yoksa model uydurur). Pure → test edilebilir.
const LIVE_PATTERNS = [
  /hava\s*durum|hava\s*nas[ıi]l|s[ıi]cakl[ıi]k|ya[ğg]mur|ya[ğg][ıi]ş|ka[çc]\s*derece|rüzg[âa]r/i,
  /haber|son\s*dakika|g[üu]ndem|g[üu]ncel|bug[üu]n|yar[ıi]n|şu\s*an|şimdi|en\s*son|bu\s*hafta/i,
  /borsa|d[öo]viz|dolar|euro|alt[ıi]n|kur\b|fiyat|maç|skor|puan\s*durum/i,
  /internette|internet(?:ten)?|web(?:'|’)?de|web\s*arama|ara[şs]t[ıi]r|kaynakl[ıi]|kaynak\s+göster|link(?:li)?/i,
  /\b(weather|forecast|temperature|news|today|tomorrow|tonight|current|latest|right now|price|stock|score|headlines)\b/i,
  /\b(web search|search the web|research online|look up|sources?|citations?|links?)\b/i,
];
export function needsLiveData(text) {
  const s = String(text || "");
  return LIVE_PATTERNS.some((re) => re.test(s));
}

const TR_LOCATIONS = [
  "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin",
  "Aydın", "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa",
  "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır", "Edirne", "Elazığ", "Erzincan",
  "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari", "Hatay", "Isparta",
  "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir",
  "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla",
  "Muş", "Nevşehir", "Niğde", "Ordu", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop",
  "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van", "Yozgat",
  "Zonguldak", "Aksaray", "Bayburt", "Karaman", "Kırıkkale", "Batman", "Şırnak", "Bartın",
  "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce",
];

const WEATHER_RE = /hava\s*durum|hava\s*nas[ıi]l|s[ıi]cakl[ıi]k|ya[ğg]mur|ya[ğg][ıi]ş|rüzg[âa]r|\b(weather|forecast|temperature)\b/i;
const NEWS_RE = /haber|son\s*dakika|g[üu]ndem|g[üu]ncel|internette|internet(?:ten)?|web(?:'|’)?de|web\s*arama|ara[şs]t[ıi]r|kaynakl[ıi]|kaynak\s+göster|\b(news|latest|web search|search the web|research online|sources?|citations?)\b/i;

function lastUserText(messages) {
  return [...(messages || [])].reverse().find(m => m.role === "user")?.content || "";
}

function guessLocation(text) {
  const s = String(text || "");
  const found = TR_LOCATIONS.find(city => new RegExp("\\b" + city + "\\b", "iu").test(s));
  if (found) return found + ", Türkiye";
  const quoted = /(?:konum|şehir|il|location|city)\s*[:=]\s*([^\n,.;]+)/i.exec(s);
  return quoted ? quoted[1].trim() : "Manisa, Türkiye";
}

function fallbackToolCalls(messages) {
  const text = lastUserText(messages);
  if (!needsLiveData(text)) return [];
  const calls = [];
  if (WEATHER_RE.test(text)) {
    calls.push({ id: "fb_weather", type: "function", function: { name: "weather_forecast", arguments: { location: guessLocation(text) } } });
  }
  if (NEWS_RE.test(text) || !calls.length) {
    calls.push({ id: "fb_web", type: "function", function: { name: "web_search", arguments: { query: String(text).slice(0, 256) } } });
  }
  return calls;
}

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
async function ollamaChat(ollamaBase, model, messages, tools, { signal, think = false, params = {} } = {}) {
  const options = {};
  if (params.temperature != null) options.temperature = params.temperature;
  if (params.top_p != null) options.top_p = params.top_p;
  if (params.max_tokens != null) options.num_predict = params.max_tokens;
  const r = await fetch(ollamaBase.replace(/\/$/, "") + "/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      messages: toOllama(messages),
      tools,
      stream: false,
      think: !!think,
      ...(Object.keys(options).length ? { options } : {}),
    }),
    signal,
  });
  if (!r.ok) throw new Error("ollama " + r.status + " " + (await r.text()).slice(0, 200));
  const d = await r.json();
  return d.message || {};
}

// messages: OpenAI tarzı [{role,content}]. system zaten içinde.
// onStep(evt): { type:"tool_call"|"tool_result", name, args?, text? }
// Döner: { content, sources:[], rounds, toolsUsed:[] }
export async function runAgent({ ollamaBase, model, messages, signal, onStep, userId, extraTools = [], extraDispatch, think = false, params = {} }) {
  agentRuns.inc();
  const convo = [...messages];
  const sources = [];
  const toolsUsed = [];
  let rounds = 0;

  const specs = extraTools && extraTools.length ? [...TOOL_SPECS, ...extraTools] : TOOL_SPECS;
  const extraNames = new Set((extraTools || []).map((t) => t.function && t.function.name).filter(Boolean));

  for (; rounds < MAX_ROUNDS; rounds++) {
    const msg = await ollamaChat(ollamaBase, model, convo, specs, { signal, think, params });
    let calls = msg.tool_calls || [];
    // Yerel model araç çağırmadıysa ama soru canlı veri gerektiriyorsa (ilk tur)
    // güvenli bir geri-dönüş çağrısı üret: weather_forecast / web_search.
    if (!calls.length && rounds === 0) {
      const fb = fallbackToolCalls(convo);
      if (fb.length) { calls = fb; onStep && onStep({ type: "fallback", count: fb.length }); }
    }
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
        res = (extraNames.has(name) && extraDispatch)
          ? await extraDispatch(name, args, { signal, userId })
          : await runTool(name, args, { signal, userId });
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
  const fin = await ollamaChat(ollamaBase, model, convo, undefined, { signal, think, params });
  return { content: fin.content || "", sources, rounds, toolsUsed };
}
