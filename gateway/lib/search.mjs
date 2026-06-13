// Web search via self-hosted SearXNG (JSON API). Returns compact, model-ready
// snippets with source URLs. No API key, runs locally.
const SEARX = process.env.SEARXNG_URL || "http://searxng:8080";

export async function webSearch(query, { count = 5, signal } = {}) {
  const url = SEARX.replace(/\/$/, "") + "/search?format=json&q=" + encodeURIComponent(query);
  const r = await fetch(url, { signal, headers: { Accept: "application/json" } });
  if (!r.ok) throw new Error("searxng " + r.status);
  const data = await r.json();
  const results = (data.results || []).slice(0, count).map((x, i) => ({
    n: i + 1,
    title: x.title || "",
    url: x.url || "",
    snippet: (x.content || "").slice(0, 300),
  }));
  return results;
}

// Modele verilecek düz-metin biçimi (kaynak numaralı).
export function formatResults(results) {
  if (!results.length) return "Arama sonucu bulunamadı.";
  return results.map(r => `[${r.n}] ${r.title}\n${r.url}\n${r.snippet}`).join("\n\n");
}
