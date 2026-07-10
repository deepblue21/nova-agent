-- 008 — agent run history (Faz 8 / agent deepening)
-- Persists a summary of each agent/team run so users can review past runs.
-- Idempotent.

CREATE TABLE IF NOT EXISTS agent_runs (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  mode       text NOT NULL,                 -- 'agent' | 'team'
  model      text,
  prompt     text,
  tools      text,                          -- comma summary, e.g. "web_search×2, calculator"
  result     text,
  rounds     int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS agent_runs_user_idx ON agent_runs (user_id, created_at DESC);
