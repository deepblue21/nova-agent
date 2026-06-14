// Çoklu ajan iş birliği: bir görevi paralel alt-ajan koşumlarına dağıt, sonra
// tek bir yanıta sentezle. LLM bağımlılığı YOK — `runOne` (alt-ajan koşumu) ve
// `synthesize` (birleştirme) dışarıdan enjekte edilir; gateway bunları runAgent
// ile sağlar. Böylece orchestration saf ve test edilebilir kalır.

// Eşzamanlılık sınırlı paralel map; sonuç sırası korunur.
export async function mapLimit(items, limit, fn) {
  const list = Array.isArray(items) ? items : [];
  const lim = Math.max(1, Number.parseInt(limit, 10) || 1);
  const out = new Array(list.length);
  let i = 0;
  async function worker() {
    while (i < list.length) {
      const idx = i++;
      out[idx] = await fn(list[idx], idx);
    }
  }
  await Promise.all(Array.from({ length: Math.min(lim, list.length) }, worker));
  return out;
}

// Saf: alt-sonuçlardan sentez promptu üretir.
export function buildSynthesisPrompt(task, results) {
  const parts = (results || []).map((r, i) =>
    `## Alt-görev ${i + 1}${r && r.role ? " (" + r.role + ")" : ""}\n` +
    (r && r.ok === false ? "[hata: " + (r.error || "bilinmiyor") + "]" : ((r && r.content) || "").trim()));
  return (
    `Ana görev: ${task}\n\n` +
    "Aşağıdaki alt-ajan sonuçlarını tek, tutarlı ve eksiksiz bir yanıtta birleştir. " +
    "Tekrarları ele, çelişkileri açıkça belirt, kaynak verildiyse koru.\n\n" +
    parts.join("\n\n")
  );
}

// Saf: planlayıcı modelin çıktısından alt-görev listesi ([{role,prompt}]) ayıkla.
// JSON dizisini metin/```json çitleri içinden bulur; geçersizse null (çağıran fallback yapar).
export function parsePlan(text, max = 5) {
  const m = /\[[\s\S]*\]/.exec(String(text || ""));
  if (!m) return null;
  try {
    const arr = JSON.parse(m[0]);
    if (!Array.isArray(arr)) return null;
    const subs = arr
      .map((x) => ({
        role: String((x && (x.role || x.name)) || "ajan").slice(0, 40),
        prompt: String((x && (x.prompt || x.task)) || "").trim().slice(0, 1000),
      }))
      .filter((s) => s.prompt);
    return subs.length ? subs.slice(0, max) : null;
  } catch { return null; }
}

// Orchestrate: fan-out → paralel alt-ajanlar → sentez. Bir alt-görev hata verirse
// diğerleri etkilenmez (sonuçta ok:false olarak işaretlenir).
export async function runTeam({ task, subtasks, runOne, synthesize, concurrency = 3 }) {
  if (!Array.isArray(subtasks) || subtasks.length === 0) throw new Error("subtasks required");
  if (typeof runOne !== "function") throw new Error("runOne function required");
  const results = await mapLimit(subtasks, concurrency, async (st) => {
    try {
      const r = await runOne(st.prompt, st.role);
      return { role: st.role, content: (r && r.content) || "", sources: (r && r.sources) || [], ok: true };
    } catch (e) {
      return { role: st.role, content: "", sources: [], ok: false, error: String((e && e.message) || e) };
    }
  });
  const sources = [];
  for (const r of results) for (const s of (r.sources || [])) sources.push(s);
  const synthesis = synthesize ? await synthesize(buildSynthesisPrompt(task, results), results) : null;
  return { results, sources, synthesis };
}
