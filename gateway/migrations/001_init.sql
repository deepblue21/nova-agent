-- ============================================================
--  NOVA — Phase 1 schema (multi-user core)
--  Target: PostgreSQL 14+. Idempotent (IF NOT EXISTS) so it can
--  be re-applied safely. gen_random_uuid() is core since PG13.
-- ============================================================

CREATE TABLE IF NOT EXISTS orgs (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email       text NOT NULL,
  name        text,
  oidc_sub    text UNIQUE,                       -- 'sub' claim from the IdP
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_idx ON users (lower(email));

CREATE TABLE IF NOT EXISTS memberships (
  user_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  org_id   uuid NOT NULL REFERENCES orgs(id)  ON DELETE CASCADE,
  role     text NOT NULL DEFAULT 'member',       -- owner | admin | member
  PRIMARY KEY (user_id, org_id)
);

-- Per-user API keys. Only the SHA-256 hash is stored; the secret is shown once.
CREATE TABLE IF NOT EXISTS api_keys (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  prefix       text NOT NULL,                    -- visible id, e.g. 'nv_ab12cd'
  token_hash   text NOT NULL,                    -- sha-256 hex of the full secret
  scopes       text[] NOT NULL DEFAULT '{}',
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  revoked_at   timestamptz
);
CREATE INDEX IF NOT EXISTS api_keys_prefix_idx ON api_keys (prefix);

CREATE TABLE IF NOT EXISTS conversations (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title       text NOT NULL DEFAULT 'Yeni sohbet',
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversations_user_idx ON conversations (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS messages (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role            text NOT NULL,                 -- user | assistant | system
  content         text NOT NULL,
  model           text,
  route           text,                          -- x-nova-route
  tokens_in       integer NOT NULL DEFAULT 0,
  tokens_out      integer NOT NULL DEFAULT 0,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS messages_conv_idx ON messages (conversation_id, created_at);

-- Append-only metering log. Cost stored in micro-dollars (integer) to avoid float drift.
CREATE TABLE IF NOT EXISTS usage_events (
  id           bigserial PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  model        text NOT NULL,
  tokens_in    integer NOT NULL DEFAULT 0,
  tokens_out   integer NOT NULL DEFAULT 0,
  cost_micros  bigint  NOT NULL DEFAULT 0,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS usage_user_time_idx ON usage_events (user_id, created_at);

-- Rolling budget per subject (user or org). Enforced before each request.
CREATE TABLE IF NOT EXISTS quotas (
  subject_id   uuid PRIMARY KEY,
  period       text   NOT NULL DEFAULT 'month',  -- day | month
  limit_micros bigint NOT NULL,
  used_micros  bigint NOT NULL DEFAULT 0,
  resets_at    timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_configs (
  provider     text PRIMARY KEY,                 -- anthropic | gemini | openai | ollama | openclaw
  enabled      boolean NOT NULL DEFAULT true,
  allow_models text[] NOT NULL DEFAULT '{}'
);
