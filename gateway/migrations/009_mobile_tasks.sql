CREATE TABLE IF NOT EXISTS mobile_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  prompt text NOT NULL CHECK (char_length(prompt) BETWEEN 1 AND 4000),
  device_id text,
  status text NOT NULL DEFAULT 'queued' CHECK (status IN (
    'queued','routing','observing','planning','executing','verifying',
    'waiting_for_confirmation','waiting_for_device','waiting_for_compute',
    'paused','completed','failed','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mobile_task_events (
  id bigserial PRIMARY KEY,
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  type text NOT NULL CHECK (char_length(type) BETWEEN 1 AND 100),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mobile_confirmations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  risk_level text NOT NULL CHECK (risk_level IN ('R2','R3')),
  action jsonb NOT NULL,
  resume_status text NOT NULL DEFAULT 'executing',
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  decided_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS mobile_tasks_user_idx ON mobile_tasks(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS mobile_events_task_idx ON mobile_task_events(task_id, id);
CREATE INDEX IF NOT EXISTS mobile_confirmations_task_idx ON mobile_confirmations(task_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS mobile_one_pending_confirmation_idx
  ON mobile_confirmations(task_id) WHERE status = 'pending';
