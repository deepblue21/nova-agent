-- 007 — workspace-scoped memory + scheduled tasks (Faz 7 / collaboration)
-- Shared notes / tasks belong to a workspace; personal ones keep workspace_id NULL.
-- Idempotent.

ALTER TABLE user_memory     ADD COLUMN IF NOT EXISTS workspace_id uuid REFERENCES workspaces(id) ON DELETE CASCADE;
ALTER TABLE scheduled_tasks ADD COLUMN IF NOT EXISTS workspace_id uuid REFERENCES workspaces(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS user_memory_workspace_idx     ON user_memory (workspace_id);
CREATE INDEX IF NOT EXISTS scheduled_tasks_workspace_idx ON scheduled_tasks (workspace_id);
