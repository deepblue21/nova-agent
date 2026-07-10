-- ============================================================
--  NOVA — Phase 2 migration: usage-based billing
-- ============================================================

CREATE TABLE IF NOT EXISTS billing_accounts (
  user_id          uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  stripe_customer  text,
  stripe_item_id   text,                    -- metered subscription item id
  created_at       timestamptz NOT NULL DEFAULT now()
);

-- Mark which usage rows have been reported to the billing provider.
ALTER TABLE usage_events ADD COLUMN IF NOT EXISTS reported_at timestamptz;

-- Fast lookup of the unreported backlog the billing job flushes.
CREATE INDEX IF NOT EXISTS usage_unreported_idx
  ON usage_events (created_at) WHERE reported_at IS NULL;
