// Bilgi tabanı: belge yükle/listele/sil. Düz metin veya küçük PDF/DOCX dosyası kabul eder.
// Kişisel VEYA çalışma alanı (workspace) kapsamlı: workspace_id verilirse yazma
// yetkisi (editör/admin) gerekir; listeleme/arama üye olunan workspace'leri de kapsar.
// Requires req.principal (mount after principal()).
import { Router } from "express";
import * as rag from "../lib/rag.mjs";
import { getRole } from "../lib/workspace_store.mjs";
import { can } from "../lib/rbac.mjs";
import { normalizeKnowledgeInput } from "../lib/doc_extract.mjs";

export const knowledge = Router();
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

knowledge.get("/v1/knowledge", async (req, res) => {
  try { res.json({ data: await rag.listDocuments(req.principal.userId) }); }
  catch (e) { res.status(500).json({ error: "knowledge unavailable" }); }
});

knowledge.post("/v1/knowledge", async (req, res) => {
  try {
    const MAX = parseInt(process.env.MAX_DOC_BYTES || "1048576", 10); // 1 MB metin sınırı (DoS/abuse koruması)
    const MAX_FILE = parseInt(process.env.MAX_DOC_FILE_BYTES || "10485760", 10); // 10 MB ham dosya sınırı
    let workspaceId = null;
    const wid = (req.body || {}).workspace_id;
    if (wid) {
      if (!UUID_RE.test(String(wid))) return res.status(400).json({ error: "invalid workspace_id" });
      const role = await getRole(wid, req.principal.userId);
      if (!role) return res.status(404).json({ error: "workspace not found" });
      if (!can(role, "write")) return res.status(403).json({ error: "forbidden: requires write (editor/admin)" });
      workspaceId = wid;
    }
    const { title, text } = await normalizeKnowledgeInput(req.body || {}, { maxTextBytes: MAX, maxFileBytes: MAX_FILE });
    const r = await rag.ingestDocument(req.principal.userId, title || "Belge", text, req.signal, workspaceId);
    res.status(201).json(r);
  } catch (e) {
    if (e.status) return res.status(e.status).json({ error: e.message || "belge işlenemedi" });
    req.log?.error?.({ err: e.message }, "knowledge ingest failed");
    res.status(500).json({ error: "yükleme başarısız: " + (e.message || e) });
  }
});

knowledge.delete("/v1/knowledge/:id", async (req, res) => {
  try {
    if (!UUID_RE.test(String(req.params.id || ""))) return res.status(400).json({ error: "invalid id" });
    const meta = await rag.getDocMeta(req.params.id);
    if (!meta) return res.status(404).end();
    if (meta.workspace_id) {
      const role = await getRole(meta.workspace_id, req.principal.userId);
      if (!can(role, "write")) return res.status(403).json({ error: "forbidden: requires write (editor/admin)" });
    } else if (meta.user_id !== req.principal.userId) {
      return res.status(404).end(); // başkasının kişisel belgesi — varlığını gizle
    }
    res.status(await rag.deleteDocumentById(req.params.id) ? 204 : 404).end();
  } catch (e) { res.status(500).json({ error: "silme başarısız" }); }
});
