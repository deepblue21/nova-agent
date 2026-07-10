-- 005 — personal long-term memory (Faz 6 / step E)
-- Per-user persistent notes auto-recalled into the chat system prompt.
-- Idempotent. gen_random_uuid() is core since PG13.

CREATE TABLE IF NOT EXISTS user_memory (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content    text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS user_memory_user_idx ON user_memory (user_id, created_at DESC);
