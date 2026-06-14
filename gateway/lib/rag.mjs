// RAG deposu: belge parçalarını embed edip pgvector'a yaz, sorgu için en yakın
// komşuları getir. Kişisel (user_id) VEYA çalışma alanı (workspace_id) kapsamlı:
// üye olduğun workspace'lerin belgeleri de aranır/listelenir.
import { q, withTx } from "./db.mjs";
import { embed, toVectorLiteral, chunkText } from "./embed.mjs";
import { listWorkspaceIds } from "./workspace_store.mjs";

// Belgeyi parçalara böl, her parçayı embed et, sakla. workspaceId verilirse belge
// o çalışma alanına ait olur (üyeler erişir). { documentId, chunks } döner.
export async function ingestDocument(userId, title, text, signal, workspaceId = null) {
  const chunks = chunkText(text);
  if (!chunks.length) throw new Error("boş belge");
  const vectors = [];
  for (const c of chunks) vectors.push(await embed(c, signal)); // sıralı (yerel, hızlı)
  return withTx(async (cx) => {
    const { rows } = await cx.query(
      "INSERT INTO documents (user_id, title, bytes, chunks, workspace_id) VALUES ($1,$2,$3,$4,$5) RETURNING id",
      [userId, title.slice(0, 200), Buffer.byteLength(text), chunks.length, workspaceId]);
    const docId = rows[0].id;
    for (let i = 0; i < chunks.length; i++) {
      await cx.query(
        "INSERT INTO doc_chunks (document_id, user_id, idx, content, embedding) VALUES ($1,$2,$3,$4,$5)",
        [docId, userId, i, chunks[i], toVectorLiteral(vectors[i])]);
    }
    return { documentId: docId, chunks: chunks.length };
  });
}

// Sorguya en yakın K parça — kişisel + üye olunan workspace belgeleri. [{content, title, score}].
export async function search(userId, query, k = 5, signal) {
  const v = toVectorLiteral(await embed(query, signal));
  const wsIds = await listWorkspaceIds(userId);
  const { rows } = await q(
    `SELECT c.content, d.title, 1 - (c.embedding <=> $2) AS score
       FROM doc_chunks c JOIN documents d ON d.id = c.document_id
      WHERE d.user_id = $1 OR d.workspace_id = ANY($4::uuid[])
      ORDER BY c.embedding <=> $2
      LIMIT $3`,
    [userId, v, k, wsIds]);
  return rows;
}

export async function listDocuments(userId) {
  const wsIds = await listWorkspaceIds(userId);
  const { rows } = await q(
    `SELECT id, title, bytes, chunks, workspace_id, created_at
       FROM documents WHERE user_id = $1 OR workspace_id = ANY($2::uuid[])
      ORDER BY created_at DESC`, [userId, wsIds]);
  return rows;
}

// Belge sahiplik/kapsam bilgisi (silme yetkisi kararı için).
export async function getDocMeta(id) {
  const { rows } = await q("SELECT user_id, workspace_id FROM documents WHERE id = $1", [id]);
  return rows[0] || null;
}
export async function deleteDocumentById(id) {
  const { rowCount } = await q("DELETE FROM documents WHERE id = $1", [id]);
  return rowCount > 0;
}
export async function deleteDocument(userId, id) {
  const { rowCount } = await q("DELETE FROM documents WHERE id = $1 AND user_id = $2", [id, userId]);
  return rowCount > 0;
}
