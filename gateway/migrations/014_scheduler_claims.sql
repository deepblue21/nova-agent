ALTER TABLE scheduled_tasks ADD COLUMN IF NOT EXISTS claim_token uuid;
ALTER TABLE scheduled_tasks ADD COLUMN IF NOT EXISTS claim_until timestamptz;
