-- Faz 3 — RAG / belgelerle sohbet. pgvector ile embedding tabanlı arama.
-- nomic-embed-text -> 768 boyut.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       text NOT NULL,
  bytes       integer NOT NULL DEFAULT 0,
  chunks      integer NOT NULL DEFAULT 0,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS documents_user_idx ON documents (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS doc_chunks (
  id          bigserial PRIMARY KEY,
  document_id uuid NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  idx         integer NOT NULL,
  content     text NOT NULL,
  embedding   vector(768)
);
CREATE INDEX IF NOT EXISTS doc_chunks_user_idx ON doc_chunks (user_id);
-- yaklaşık en yakın komşu (cosine). Liste sayısı küçük veri için yeterli.
CREATE INDEX IF NOT EXISTS doc_chunks_embed_idx ON doc_chunks
  USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
