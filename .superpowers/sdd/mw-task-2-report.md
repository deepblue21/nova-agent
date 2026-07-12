# Mobilerun Task 2 Report

## Status

`DONE_WITH_CONCERNS`

Commit: `9a728f113e7afc1d46c5e9d9af07623f430ece85` (`feat: persist mobile worker leases`)

## TDD Evidence

- Initial requested RED command: `npm --prefix gateway test -- test/mobile_worker_store.test.mjs`.
  PowerShell blocked `npm.ps1` before Node ran because script execution is disabled.
- Root-cause check confirmed `npm.cmd` reaches the same package script. The first `npm.cmd`
  run exposed a syntax error in the new test file; the error was isolated to one extra
  parenthesis and corrected without changing production code.
- RED command rerun: `npm.cmd --prefix gateway test -- test/mobile_worker_store.test.mjs`.
  Result: expected `ERR_MODULE_NOT_FOUND` for `gateway/lib/mobile_worker_store.mjs`.
- Focused GREEN command: `npm.cmd --prefix gateway test -- test/mobile_worker_store.test.mjs`.
  Result: 4 tests passed, 0 failed.
- A later focused RED/GREEN cycle caught overlapping task/lease SQL column names in
  `getActiveStatus`; explicit SQL aliases and record mapping fixed it. Final focused result
  remained 4 passed, 0 failed.

## Verification

- Full Gateway suite: `npm.cmd --prefix gateway test`.
  Result: 149 tests passed, 0 failed, exit code 0.
- Migration command attempted as required:
  `wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose run --rm migrate`.
  Result: the accessible container reported skips through `009_mobile_tasks.sql` and
  `migrations done`, but did not list `010_mobile_worker_leases.sql`.
- Investigation: the Compose service builds a copied gateway image rather than mounting the
  source. The local 010 file exists; subsequent checks found no WSL distribution available
  and no reachable Docker Desktop daemon, so a rebuilt image could not be started to verify
  application of 010.

## Migration Facts

- Adds `mobile_worker_leases` with UUID primary key, task foreign key, bounded device ID,
  token hash only, active/closed/expired state, expiry, closure, and creation timestamps.
- Adds a partial unique index enforcing one active lease per task.
- Adds `mobile_worker_reports` keyed by `(lease_id, report_id)` with phase, stored event
  reference, saved task status, and timestamp for idempotent retries.

## Design Choices

- Claims require `emulator-5554`, use SQL prompt normalization against the policy allowlist,
  lock the oldest queued task with `FOR UPDATE SKIP LOCKED`, and store only a SHA-256 lease
  token hash.
- Claim, task update, lease creation, and both lifecycle events share one transaction.
- Reports parse the bounded Task 1 contract, lock lease and task, verify the token hash,
  replay a previously stored event for a duplicate report ID, map only the specified phases,
  and close leases for non-progress phases.
- Stored worker events contain status only, not tokens or raw worker report content.
- Expired active leases transition tasks to `waiting_for_device` and persist one expiry event.

## Files Changed

- `gateway/migrations/010_mobile_worker_leases.sql`
- `gateway/lib/mobile_worker_store.mjs`
- `gateway/test/mobile_worker_store.test.mjs`
- `README.md`
- `README.tr.md`

## Follow-up Verification

The initial migration check used a stale copied Gateway image. Root-cause investigation
confirmed that the Compose service does not mount the source tree and that the WSL Docker
daemon was available. The controller then ran:

`wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose run --build --rm migrate`

Result: `applied 010_mobile_worker_leases.sql`, followed by `migrations done`.

## Review Fix: Lease Edge Coverage

Status: `DONE`

Primary implementation commit: `27aaad035fd75f2566265fbc0b19217d7af03ab7`
(`test: cover mobile worker lease edge cases`)

### TDD Evidence

- RED command: `npm.cmd --prefix gateway test -- test/mobile_worker_store.test.mjs`.
  Result: 6 passed, 3 failed. The failures demonstrated that the prior fake client could
  not model oldest-safe-task selection, closed-lease rejection, or an unexpired expiry no-op.
- After replacing the fake's SQL-string branches with semantic in-memory task, lease, report,
  and event state, the same focused command was RED again: 8 passed, 1 failed. The remaining
  failure exposed `claimNext` returning the persisted `token_hash`.
- GREEN command: `npm.cmd --prefix gateway test -- test/mobile_worker_store.test.mjs`.
  Result: 9 passed, 0 failed.

### Coverage Added

- The claim model selects the oldest normalized allowed queued task and directly asserts
  `ORDER BY t.created_at ASC` plus `FOR UPDATE SKIP LOCKED`.
- All six report phase-to-status/event mappings are exercised; every non-progress phase is
  verified to close its lease.
- Reports reject both expired and non-active leases; non-emulator claims are rejected before
  any query; and unexpired active leases are an expiry no-op.
- The stateful fake now models task, lease, report, and event transitions, including replay
  keys scoped by lease and report ID, instead of only matching SQL fragments.
- Claim responses omit `token_hash`; report results and persisted event payloads are checked
  not to expose the lease token.

### Production Finding

`claimNext` exposed the stored `token_hash` in its returned lease. The minimum production
fix removes that internal verifier before returning the lease while retaining the one-time
claim token needed by the worker. No migration change was needed.

### Verification and Docs

- Full suite command: `npm.cmd --prefix gateway test`.
  Result: 154 passed, 0 failed, exit code 0.
- The Docker migration was not rerun because no migration source changed; the prior rebuilt
  image verification recorded above remains the migration evidence.
- `README.md` and `README.tr.md` now record the verified semantic lease edge coverage and
  retain the concrete next task: the isolated WSL Python worker and WSL-to-emulator ADB bridge.

## Review Follow-up: Claim Device Binding and Lease-Expiry Wording

### Files Changed

- `gateway/test/mobile_worker_store.test.mjs`
- `README.md`
- `README.tr.md`
- `.superpowers/sdd/mw-task-2-report.md`

### Focused Test Evidence

- `npm.cmd --prefix gateway test -- test/mobile_worker_store.test.mjs`
  Result: 9 passed, 0 failed, exit code 0.
- `npm.cmd --prefix gateway test`
  Result: 154 passed, 0 failed, exit code 0.

The claim test directly asserts `out.task.device_id === "emulator-5554"`. It also finds the
executing `UPDATE mobile_tasks` call, requires `device_id=$1` in that SQL statement, and checks
its bound parameters are `["emulator-5554", TASK_ID]`. This distinguishes a missing task device
binding from the fake client's broad executing-update state transition without claiming a new RED
result for existing correct behavior.

### Documentation Correction

`README.md` and `README.tr.md` now refer precisely to expired leases and lease-expiry recovery,
instead of describing them as expired claims. Both living-delivery sections retain the verified
completed work and the next concrete WSL worker/ADB bridge task.

### Commit and Concerns

Commit SHA: recorded in the final task result because a commit cannot contain its own
content-addressed SHA.

Concerns: none.
