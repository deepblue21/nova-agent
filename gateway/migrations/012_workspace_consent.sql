-- Legacy memberships contain no proof of recipient consent. Owners keep access;
-- other memberships become pending invitations with their original role intact.
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS accepted_at timestamptz;
UPDATE workspace_members m SET accepted_at = now()
  FROM workspaces w WHERE w.id = m.workspace_id AND w.owner_id = m.user_id;

-- Do not expose old shared runs which may have read the creator's private data.
ALTER TABLE scheduled_tasks ADD COLUMN IF NOT EXISTS result_scope_version integer NOT NULL DEFAULT 0;
