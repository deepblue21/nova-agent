# Gateway upgrade instructions

## Database and account access

Run `npm --prefix gateway run migrate` before starting the updated gateway. Migrations
012–016 add invitation consent, nullable verified email, scheduler leases, billing
batches and media reservations. A PostgreSQL advisory lock serializes migration
runners across replicas. Back up the database using your normal operational process.

Workspace owners remain accepted. Other existing memberships require the recipient
to accept the invitation in Settings → Workspaces. Shared notes no longer become
system instructions. Shared scheduled tasks can search documents only in their own
workspace. Previously stored shared results remain hidden until the next scoped run.

API keys with an empty scope list retain legacy full access. Newly scoped keys must
include the relevant permissions: `read`, `write`, `chat`, `voice`, `mobile`, `tools`,
or `admin`. The admin scope does not replace the `ADMIN_USER_IDS` check. In multi-user
mode, server-configured MCP tools are available only to those admin users.

## Public login and HTTPS

Set the public `OIDC_ISSUER`, internal/reachable `OIDC_JWKS_URL`,
`OIDC_AUDIENCE=nova-gateway`, and optional `OIDC_WEB_CLIENT_ID=nova-web`.
The browser reads public login settings from `/v1/config`; the JWKS URL is not exposed.
The production Compose overlay expects `KC_HOSTNAME=https://auth.example.com`,
`KC_HOSTNAME_HOST=auth.example.com`, `DOMAIN=nova.example.com` and an exact
`ALLOW_ORIGINS`. Its CSP permits the auth host for token exchange.

**Existing Keycloak realms need an explicit client upgrade.** Startup realm import
does not replace an existing realm ([Keycloak import documentation](https://www.keycloak.org/server/importExport)).
Before switching the gateway audience, configure `nova-web` with:

- Valid redirect URI: the exact UI origin followed by `/`.
- Web origin: the exact UI origin.
- PKCE method: `S256`.
- Audience mapper named `nova-gateway-audience`, custom audience `nova-gateway`,
  included in access tokens, excluded from ID tokens.

The same update can be applied idempotently with
`node gateway/scripts/configure-oidc.mjs`. Supply `KEYCLOAK_ADMIN_URL` (HTTPS auth
origin, or HTTP loopback for development), `NOVA_PUBLIC_ORIGIN`, and a short-lived
`KEYCLOAK_ADMIN_TOKEN` through the environment. Optional `OIDC_REALM` defaults to
`nova`. This changes only the `nova-web` client and its audience mapper; it does not
re-import the realm or replace users. The token requires client-management privileges
and must not be committed or pasted into shell command arguments. Remove it from the
environment afterward. Sign out/in to obtain tokens with the new audience.

Caddy serves the application through HTTPS and redirects HTTP automatically
([Caddy documentation](https://caddyserver.com/docs/automatic-https)). Local Compose
uses `https://localhost` with Caddy's local CA; trust that CA for local browser use.
Production deployments need working DNS and externally reachable ports 80/443.
The bare local gateway keeps its authenticated LAN development option.

Browser provider keys and login tokens are held only in the open page. Reloading
requires signing in or entering a key again. Existing browser storage copies are
scrubbed when loaded. Android backup and device transfer exclude connection secrets.

## Resource and usage limits

New paid models require explicit verified pricing in `MODEL_PRICES_JSON`, an object
mapping full provider/model routes to `[input, output]` USD per million tokens.
Unpriced paid models are unavailable; local routes remain free. Check the provider's
current published prices before configuring values. `ALLOW_MODELS` remains available.
Expired daily/monthly quotas roll forward at UTC calendar boundaries.

`REQ_TIMEOUT_MS` defaults to 60000, `AGENT_TIMEOUT_MS` to 180000 and
`TEAM_TIMEOUT_MS` to 300000. All authenticated routes share per-user admission limits,
and the IP limiter also applies in multi-user mode. PDF/DOCX parsing and QuickJS use
bounded workers (at most two concurrent jobs); overload returns 503, deadlines return
504, and unsupported/oversized archives are rejected.

Scheduled tasks use leased database claims and the creator's quota, model allowlist
and metering. New daily UI schedules include the browser's IANA time zone, for example
`daily:09:00@Europe/Istanbul`. Existing `daily:09:00` strings mean 09:00 UTC. Review
existing schedules if they previously relied on a host's non-UTC timezone.

Media uploads reserve storage before S3 upload. Defaults are 100 MiB per user and
7-day retention (`MEDIA_QUOTA_BYTES`, `MEDIA_RETENTION_DAYS`). Cleanup runs every ten
minutes; failures retain quota reservations and are logged. `DELETE /v1/media` with
JSON `{ "key": "the-upload-key" }` deletes only the caller's own recorded upload.
Pre-upgrade objects have no reservation metadata: inventory these separately and
apply the intended bucket lifecycle policy before considering old storage reclaimed.

Stripe usage is grouped into immutable batches with stable idempotency keys. If a
batch remains unacknowledged for 23 hours after its first attempt, it enters
`manual_review`; reconcile that key with Stripe before changing database state.
This stops uncertain retries beyond Stripe's documented retention window
([Stripe idempotency](https://docs.stripe.com/api/idempotent_requests)).

## Kubernetes

Replace both `REPLACE_WITH_RELEASE_DIGEST` image references in
`k8s/gateway-deployment.yaml` with the same real published SHA-256 digest and your
registry owner before applying it. The manifest is a deployment template, not a
reference to a pre-published image. Containers run without privilege escalation or
capabilities, with a read-only root and a bounded writable `/tmp`. Service-account
token mounting is disabled. Keep the existing database/Redis/S3 secrets and resource
limits configured. Run `npm run prod-check` with the deployment environment loaded.
