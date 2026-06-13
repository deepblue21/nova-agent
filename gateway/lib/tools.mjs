// Sunucu tarafı araçlar. Model bir aracı çağırır, gateway burada çalıştırır,
// sonucu modele geri verir (agent.mjs döngüsü). Araçlar OpenAI/Ollama uyumlu
// JSON şema formatında tanımlanır. Yeni araç eklemek: SPECS + EXECUTORS.

import { webSearch, formatResults } from "./search.mjs";
import { search as ragSearch } from "./rag.mjs";
import { codeToolEnabled, runJavaScriptSandbox } from "./code_sandbox.mjs";

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

export const TOOL_SPECS = codeToolEnabled() ? [...BASE_TOOL_SPECS, CODE_TOOL_SPEC] : BASE_TOOL_SPECS;

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
