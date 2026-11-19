# VPS 2 incident recovery and release repair - 2026-11-19

## Approved scope

The user approved recovery of Payment, Notification and AI plus permanent CI/CD
repair. Application logic, SQL, infra containers, volumes and provisioned env are
outside this change. Work is isolated from an existing dirty backend checkout.

## Evidence and root cause

Main was `61b8d7fbcfe45b4c33456ad669f0701ba348f035`, CD run `44463217045`.
Two earlier runs overlapped on VPS 2. The rerun then encountered conflicting
never-started replacement containers after old apps were stopped. The committed
workflow used mutable latest, whole-file Compose up with remove-orphans, prune,
and no fail-fast/serialization/health confirmation. Its final successful command
masked the failed activation. GitHub success did not prove a running release.

## Recovery performed

- Captured sanitized image/container/state/mount metadata and env/Compose hashes
  under `.deploy-history/20261119T155936152543Z-vps2-recovery-before.json` on VPS 2.
- Retained old Payment and Notification images under local rollback tags. AI had
  no remaining old container; no ready old AI rollback was claimed.
- Acquired the shared host lock. Verified exact CI digests already cached locally.
  Removed only the three owned created replacements that had never started.
- Activated Payment, Notification, then AI by pinned digest and `--no-deps`,
  verifying running image and health for each. No registry/image substitution.
- Independent check at approximately 23:07 UTC+7: all seven services CURRENT,
  all pending=0, exit 0. Report remains local in ignored build output.
- MySQL/Kafka container IDs, image IDs, startedAt and mounts compared identical
  to the captured snapshot. Env and VPS 2 Compose hashes also compared identical.

## Permanent changes

- Workflow concurrency plus shared Linux host lock for manual and CI deployment.
- Main-only image publication, SHA revision labels and unique SHA/run/attempt tags;
  exact digest manifest passed to each node. No reliance on latest for activation.
- Fail-fast SSH shells and propagated native exit codes. Each app receives its own
  health deadline. Stop ambiguous container sets before pull/cleanup/up.
- App-only activation and safe interrupted-created cleanup. Retain before/expected/
  verified identities in deployment history; no prune/down/volume or infra changes.
- Ephemeral CI registry auth; canonical Git origin with per-command authentication.
- Menu 1.7 CI/Local source, cancellation and loading; menu 1.9 independent verification.
  Other unpublished Compose/Casso work is deliberately excluded.

## Checks and rollback

Release contracts cover interrupted created+exited recovery, wrong/missing digest,
health failure, ambiguous containers, Docker/SSH/Gradle failure propagation, live
batch-menu edits and cancellation. Linux CI executes real fail-fast workflow shells
against fake external commands and checks contention on the real flock primitive.
YAML and embedded Bash syntax checked locally. Full Java/MySQL CI must pass before
merging; no application source or migrations change in this release.

If activation fails, do not rerun an old workflow or reset databases. Inspect the
recorded failed app only, verify the captured image exists, and activate that exact
prior image with a service-scoped override. Preserve healthy apps and env. Old AI
image availability must be established before promising rollback.

## Publication and final verification

Completed: release PR 74 merged as main `2f56c82189653d5a636def0b36cca57ea29f63f6`.
CD `44564988649` built the release and deployed all three nodes successfully.
Independent verification at 2026-11-19 23:26 UTC+7 returned all seven services
CURRENT, pending=0, exit 0. Full Java/MySQL PR CI `44563470612` and main CI
`44564988948` succeeded. Windows release contracts: 45 executed/pass, 2 Linux-only
skipped; Linux: 34 executed/pass, 13 Windows-only skipped, including real flock
contention and fail-fast workflow shells.

All-node infra container IDs, image IDs, start times and mount contents remained
identical; env hashes were unchanged. MySQL mount order differed in one inspect
response but sorting by destination confirmed identical contents. No infra was
added/removed, and Git origin no longer stores a workflow token.

Manual CI-mode SSH dry-run connected to all nodes without Docker Desktop. Existing
persistent VPS GHCR credentials are denied; manual deploy needs valid read:packages
auth through menu 1.7 -> 7. Automatic CD uses fresh temporary auth and succeeded.
Do not Re-run the historical unsafe 61b8d7f workflow. No real financial transaction
was executed as smoke. No app source, migrations, volumes or env changed in that repair.
