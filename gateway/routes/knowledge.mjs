// Bilgi tabanı: belge yükle/listele/sil. Düz metin veya küçük PDF/DOCX dosyası kabul eder.
// Requires req.principal (mount after principal()).
import { Router } from "express";
import * as rag from "../lib/rag.mjs";
import { normalizeKnowledgeInput } from "../lib/doc_extract.mjs";

export const knowledge = Router();

knowledge.get("/v1/knowledge", async (req, res) => {
  try { res.json({ data: await rag.listDocuments(req.principal.userId) }); }
  catch (e) { res.status(500).json({ error: "knowledge unavailable" }); }
});

knowledge.post("/v1/knowledge", async (req, res) => {
  try {
    const MAX = parseInt(process.env.MAX_DOC_BYTES || "1048576", 10); // 1 MB metin sınırı (DoS/abuse koruması)
    const MAX_FILE = parseInt(process.env.MAX_DOC_FILE_BYTES || "10485760", 10); // 10 MB ham dosya sınırı
    const { title, text } = await normalizeKnowledgeInput(req.body || {}, { maxTextBytes: MAX, maxFileBytes: MAX_FILE });
    const r = await rag.ingestDocument(req.principal.userId, title || "Belge", text, req.signal);
    res.status(201).json(r);
  } catch (e) {
    if (e.status) return res.status(e.status).json({ error: e.message || "belge işlenemedi" });
    req.log?.error?.({ err: e.message }, "knowledge ingest failed");
    res.status(500).json({ error: "yükleme başarısız: " + (e.message || e) });
  }
});

knowledge.delete("/v1/knowledge/:id", async (req, res) => {
  try { res.status(await rag.deleteDocument(req.principal.userId, req.params.id) ? 204 : 404).end(); }
  catch (e) { res.status(500).json({ error: "silme başarısız" }); }
});
