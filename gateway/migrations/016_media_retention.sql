CREATE TABLE media_objects (
  object_key text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  bytes bigint NOT NULL CHECK (bytes > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);
CREATE INDEX media_objects_user ON media_objects(user_id);
CREATE INDEX media_objects_expiry ON media_objects(expires_at);
