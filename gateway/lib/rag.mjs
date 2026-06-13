// RAG deposu: belge parçalarını embed edip pgvector'a yaz, sorgu için en yakın
// komşuları getir. Kullanıcıya kapsamlı (user_id ile izole).
import { q, withTx } from "./db.mjs";
import { embed, toVectorLiteral, chunkText } from "./embed.mjs";

// Belgeyi parçalara böl, her parçayı embed et, sakla. { documentId, chunks } döner.
export async function ingestDocument(userId, title, text, signal) {
  const chunks = chunkText(text);
  if (!chunks.length) throw new Error("boş belge");
  const vectors = [];
  for (const c of chunks) vectors.push(await embed(c, signal)); // sıralı (yerel, hızlı)
  return withTx(async (cx) => {
    const { rows } = await cx.query(
      "INSERT INTO documents (user_id, title, bytes, chunks) VALUES ($1,$2,$3,$4) RETURNING id",
      [userId, title.slice(0, 200), Buffer.byteLength(text), chunks.length]);
    const docId = rows[0].id;
    for (let i = 0; i < chunks.length; i++) {
      await cx.query(
        "INSERT INTO doc_chunks (document_id, user_id, idx, content, embedding) VALUES ($1,$2,$3,$4,$5)",
        [docId, userId, i, chunks[i], toVectorLiteral(vectors[i])]);
    }
    return { documentId: docId, chunks: chunks.length };
  });
}

// Sorguya en yakın K parça. [{content, title, score}] döner.
export async function search(userId, query, k = 5, signal) {
  const v = toVectorLiteral(await embed(query, signal));
  const { rows } = await q(
    `SELECT c.content, d.title, 1 - (c.embedding <=> $2) AS score
       FROM doc_chunks c JOIN documents d ON d.id = c.document_id
      WHERE c.user_id = $1
      ORDER BY c.embedding <=> $2
      LIMIT $3`,
    [userId, v, k]);
  return rows;
}

export async function listDocuments(userId) {
  const { rows } = await q(
    "SELECT id, title, bytes, chunks, created_at FROM documents WHERE user_id = $1 ORDER BY created_at DESC", [userId]);
  return rows;
}
export async function deleteDocument(userId, id) {
  const { rowCount } = await q("DELETE FROM documents WHERE id = $1 AND user_id = $2", [id, userId]);
  return rowCount > 0;
}
