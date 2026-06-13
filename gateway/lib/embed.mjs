// Ollama embeddings (nomic-embed-text, 768 boyut). RAG için metin → vektör.
const OLLAMA = process.env.OLLAMA_URL || "http://localhost:11434";
const MODEL = process.env.EMBED_MODEL || "nomic-embed-text";

export async function embed(text, signal) {
  const r = await fetch(OLLAMA.replace(/\/$/, "") + "/api/embeddings", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: MODEL, prompt: String(text).slice(0, 8000) }),
    signal,
  });
  if (!r.ok) throw new Error("embed " + r.status);
  const d = await r.json();
  return d.embedding; // number[768]
}

// pgvector literal: '[0.1,0.2,...]'
export const toVectorLiteral = (arr) => "[" + arr.map(x => (+x).toFixed(6)).join(",") + "]";

// Basit ama etkili parçalama: paragraf sınırlarını koruyarak ~maxChars'lık bloklar.
export function chunkText(text, maxChars = 1100, overlap = 150) {
  const paras = String(text).replace(/\r/g, "").split(/\n{2,}/).map(p => p.trim()).filter(Boolean);
  const chunks = [];
  let buf = "";
  for (const p of paras) {
    if ((buf + "\n\n" + p).length > maxChars && buf) {
      chunks.push(buf);
      buf = buf.slice(Math.max(0, buf.length - overlap)) + "\n\n" + p;
    } else {
      buf = buf ? buf + "\n\n" + p : p;
    }
  }
  if (buf.trim()) chunks.push(buf);
  // çok uzun tek paragrafları sert böl
  return chunks.flatMap(c => c.length <= maxChars * 1.5 ? [c] : c.match(new RegExp(`.{1,${maxChars}}`, "gs")) || [c]);
}
