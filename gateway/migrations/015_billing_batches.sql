CREATE TABLE IF NOT EXISTS billing_batches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_id text NOT NULL,
  quantity bigint NOT NULL,
  usage_timestamp bigint NOT NULL,
  state text NOT NULL DEFAULT 'pending' CHECK(state IN ('pending','reported','manual_review')),
  first_attempt_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  reported_at timestamptz
);
ALTER TABLE usage_events ADD COLUMN IF NOT EXISTS billing_batch_id uuid REFERENCES billing_batches(id);
