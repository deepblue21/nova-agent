-- 004 — scheduled / automated agent tasks (Faz 6)
-- Idempotent. gen_random_uuid() is core since PG13.

CREATE TABLE IF NOT EXISTS scheduled_tasks (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       text NOT NULL,
  prompt      text NOT NULL,
  model       text,
  agent       boolean NOT NULL DEFAULT true,
  schedule    text NOT NULL,                 -- "every:30m" | "daily:09:00"
  enabled     boolean NOT NULL DEFAULT true,
  next_run_at timestamptz NOT NULL,
  last_run_at timestamptz,
  last_status text,                          -- ok | error
  last_result text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS scheduled_tasks_due_idx  ON scheduled_tasks (enabled, next_run_at);
CREATE INDEX IF NOT EXISTS scheduled_tasks_user_idx ON scheduled_tasks (user_id);
