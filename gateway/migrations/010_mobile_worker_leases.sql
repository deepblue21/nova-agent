CREATE TABLE IF NOT EXISTS mobile_worker_leases (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id uuid NOT NULL REFERENCES mobile_tasks(id) ON DELETE CASCADE,
  device_id text NOT NULL CHECK (char_length(device_id) BETWEEN 1 AND 200),
  token_hash text NOT NULL,
  state text NOT NULL DEFAULT 'active' CHECK (state IN ('active','closed','expired')),
  expires_at timestamptz NOT NULL,
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS mobile_one_active_worker_lease_idx
  ON mobile_worker_leases(task_id) WHERE state = 'active';

CREATE TABLE IF NOT EXISTS mobile_worker_reports (
  lease_id uuid NOT NULL REFERENCES mobile_worker_leases(id) ON DELETE CASCADE,
  report_id uuid NOT NULL,
  phase text NOT NULL,
  event_id bigint REFERENCES mobile_task_events(id) ON DELETE SET NULL,
  task_status text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (lease_id, report_id)
);
