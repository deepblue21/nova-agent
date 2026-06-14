// Sunucu tarafı araçlar. Model bir aracı çağırır, gateway burada çalıştırır,
// sonucu modele geri verir (agent.mjs döngüsü). Araçlar OpenAI/Ollama uyumlu
// JSON şema formatında tanımlanır. Yeni araç eklemek: SPECS + EXECUTORS.

import { lookup as dnsLookup } from "node:dns/promises";
import { webSearch, formatResults } from "./search.mjs";
import { search as ragSearch } from "./rag.mjs";
import { codeToolEnabled, runJavaScriptSandbox } from "./code_sandbox.mjs";
import { isPrivateAddress } from "./image_inputs.mjs";

// Opt-in web page reader. Off by default (SSRF surface); enable with FETCH_TOOL_ENABLED=1.
const FETCH_TOOL_ENABLED = process.env.FETCH_TOOL_ENABLED === "1";

const BASE_TOOL_SPECS = [
  {
    type: "function",
    function: {
      name: "doc_search",
      description: "Kullanıcının yüklediği belgelerde (bilgi tabanı) arama yapar. Kişiye özel doküman içeriğiyle ilgili sorularda kullan.",
      parameters: {
        type: "object",
        properties: { query: { type: "string", description: "Belgede aranacak konu/soru" } },
        required: ["query"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "web_search",
      description: "Güncel bilgi, haber, fiyat, kişi/olay gibi modelin eğitim verisinde olmayan şeyler için web'de arama yapar. Kaynak linkleriyle sonuç döner.",
      parameters: {
        type: "object",
        properties: {
          query: { type: "string", description: "Arama sorgusu (kısa ve net tut)" },
        },
        required: ["query"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "calculator",
      description: "Aritmetik/matematik ifadesini güvenli şekilde hesaplar. Örn: '(1234*7)/3', 'sqrt(2)+sin(0)'.",
      parameters: {
        type: "object",
        properties: { expression: { type: "string", description: "Hesaplanacak ifade" } },
        required: ["expression"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "current_time",
      description: "Şu anki tarih ve saati döndürür (sunucu saati).",
      parameters: { type: "object", properties: {} },
    },
  },
];

const CODE_TOOL_SPEC = {
  type: "function",
  function: {
    name: "code_run",
    description: "Local-only QuickJS sandbox'ta küçük JavaScript kodu çalıştırır. Shell, dosya sistemi, process, require ve network yoktur. Küçük hesaplama/veri dönüştürme/algoritma denemeleri için kullan. Kod içinde sonuç için `return ...` veya `console.log(...)` kullan.",
    parameters: {
      type: "object",
      properties: {
        code: { type: "string", description: "Çalıştırılacak JavaScript. Bir fonksiyon gövdesi gibi değerlendirilir; `return` kullanılabilir." },
        input: { description: "Koda `input` sabiti olarak verilen JSON uyumlu veri." },
        timeout_ms: { type: "number", description: "İsteğe bağlı süre limiti. Sunucu üst sınırı aşılmaz." },
      },
      required: ["code"],
    },
  },
};

const FETCH_TOOL_SPEC = {
  type: "function",
  function: {
    name: "fetch_url",
    description: "Bir web sayfasının (http/https) okunabilir metin içeriğini getirir. web_search özet+link döner; tam sayfa metni gerektiğinde bunu kullan.",
    parameters: {
      type: "object",
      properties: { url: { type: "string", description: "Getirilecek http(s) URL" } },
      required: ["url"],
    },
  },
};

export const TOOL_SPECS = [
  ...BASE_TOOL_SPECS,
  ...(codeToolEnabled() ? [CODE_TOOL_SPEC] : []),
  ...(FETCH_TOOL_ENABLED ? [FETCH_TOOL_SPEC] : []),
];

// --- fetch_url güvenliği: SSRF koruması (pure, test edilebilir) ---
const LOCAL_HOSTNAMES = new Set(["localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback"]);
export function isUnsafeFetchHost(hostname) {
  const h = String(hostname || "").toLowerCase().replace(/\.$/, "");
  if (!h) return true;
  if (LOCAL_HOSTNAMES.has(h)) return true;
  if (h.endsWith(".local") || h.endsWith(".internal")) return true;
  if (h === "metadata.google.internal") return true;
  if (isPrivateAddress(h)) return true;   // literal private/loopback IP
  return false;
}

// Kaba HTML → metin: script/style at, etiketleri sök, entity çöz, kısalt.
export function htmlToText(html, max = 6000) {
  let s = String(html || "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<\/(p|div|li|h[1-6]|tr|br)>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/[ \t]+/g, " ").replace(/\n[ \t]*(\n[ \t]*)+/g, "\n\n").trim();
  return s.length > max ? s.slice(0, max) + "\n…(kısaltıldı)" : s;
}

// Güvenli aritmetik: sıkı allowlist. Yalnızca sayı/operatör/parantez/virgül ve
// izinli Math fonksiyon adları. Uzunluk + karakter + token denetimi; başka
// hiçbir identifier (constructor, global, this, vb.) geçemez.
const CALC_FNS = ["sqrt", "sin", "cos", "tan", "log", "abs", "pow", "min", "max", "round", "floor", "ceil", "pi", "e"];
function safeEval(expr) {
  const cleaned = String(expr).replace(/\s+/g, "");
  if (cleaned.length > 200) throw new Error("ifade çok uzun");
  // izinli karakter kümesi: rakam, . , + - * / ( ) % ^ ve küçük harf (fonksiyon adları)
  if (!/^[0-9+\-*/().,%^a-z]+$/.test(cleaned)) throw new Error("geçersiz karakter");
  const tokens = cleaned.match(/[a-z]+/g) || [];
  for (const t of tokens) if (!CALC_FNS.includes(t)) throw new Error("izin verilmeyen sembol: " + t);
  const scope = "const {sqrt,sin,cos,tan,log,abs,pow,min,max,round,floor,ceil,PI:pi,E:e}=Math;";
  const js = cleaned.replace(/\^/g, "**");
  // eslint-disable-next-line no-new-func
  const fn = new Function(scope + '"use strict";return (' + js + ");");
  const v = fn();
  if (typeof v !== "number" || !isFinite(v)) throw new Error("hesaplanamadı");
  return v;
}

const EXECUTORS = {
  async doc_search(args, ctx) {
    if (!ctx || !ctx.userId) return { text: "Belge araması için oturum gerekli." };
    const rows = await ragSearch(ctx.userId, args.query, 5, ctx.signal);
    if (!rows.length) return { text: "Bilgi tabanında ilgili içerik bulunamadı." };
    return {
      text: rows.map((r, i) => `[${i + 1}] ${r.title} (benzerlik ${(r.score * 100).toFixed(0)}%)\n${r.content.slice(0, 400)}`).join("\n\n"),
      sources: rows.map((r, i) => ({ n: i + 1, title: r.title, score: r.score, type: "doc" })),
    };
  },
  async web_search(args, ctx) {
    const results = await webSearch(args.query, { signal: ctx && ctx.signal });
    return { text: formatResults(results), sources: results.map(r => ({ n: r.n, title: r.title, url: r.url })) };
  },
  async calculator(args) {
    return { text: String(safeEval(args.expression)) };
  },
  async current_time() {
    return { text: new Date().toLocaleString("tr-TR", { dateStyle: "full", timeStyle: "medium" }) };
  },
  async code_run(args) {
    if (!codeToolEnabled()) return { text: "Kod çalıştırma aracı kapalı. Local kullanımda CODE_TOOL_ENABLED=1 ile açılabilir." };
    const r = await runJavaScriptSandbox(args || {});
    return { text: r.text };
  },
  async fetch_url(args, ctx) {
    if (!FETCH_TOOL_ENABLED) return { text: "fetch_url aracı kapalı (FETCH_TOOL_ENABLED=1 ile aç)." };
    let u;
    try { u = new URL(String(args.url || "").trim()); } catch { return { text: "Geçersiz URL." }; }
    if (u.protocol !== "http:" && u.protocol !== "https:") return { text: "Sadece http/https desteklenir." };
    if (u.username || u.password) return { text: "URL içinde kimlik bilgisi kabul edilmez." };
    if (isUnsafeFetchHost(u.hostname)) return { text: "Özel/yerel/loopback adresler engellendi (SSRF koruması)." };
    try {
      const addrs = await dnsLookup(u.hostname, { all: true });
      for (const a of addrs) if (isPrivateAddress(a.address)) return { text: "Host özel bir adrese çözümleniyor; engellendi." };
    } catch { return { text: "Host çözümlenemedi." }; }
    const MAX = parseInt(process.env.FETCH_TOOL_MAX_BYTES || "2000000", 10);
    const r = await fetch(u.toString(), { redirect: "manual", signal: ctx && ctx.signal, headers: { "User-Agent": "NOVA-Agent/1.0", Accept: "text/html,text/plain,*/*" } });
    if (r.status >= 300 && r.status < 400) return { text: "Yönlendirme engellendi (SSRF güvenliği için takip edilmez): HTTP " + r.status };
    if (!r.ok) return { text: "Sayfa getirilemedi: HTTP " + r.status };
    const ct = r.headers.get("content-type") || "";
    const buf = Buffer.from(await r.arrayBuffer());
    if (buf.length > MAX) return { text: "İçerik çok büyük (" + buf.length + " bayt)." };
    const body = buf.toString("utf8");
    const text = /html/i.test(ct) ? htmlToText(body) : body.slice(0, 6000);
    return { text: text || "(boş içerik)", sources: [{ n: 1, title: u.hostname, url: u.toString() }] };
  },
};

// Bir araç çağrısını çalıştır. { ok, name, text, sources } döner.
export async function runTool(name, args, ctx) {
  const fn = EXECUTORS[name];
  if (!fn) return { ok: false, name, text: "Bilinmeyen araç: " + name };
  try {
    const r = await fn(args || {}, ctx);
    return { ok: true, name, text: r.text || "", sources: r.sources || [] };
  } catch (e) {
    return { ok: false, name, text: "Araç hatası (" + name + "): " + (e.message || e) };
  }
}
