# L04 filesystem PR readiness

This draft combines the pending L04 layout/physical-slot implementation with the user-requested filesystem storage replacement. The original Firebase-specific adapter, SDK and credential configuration are absent. Including the L04 code is necessary because its storage types and callers did not previously exist on main.

The PR is prepared on main commit 6d1780640eb0b039ea6495b4b3efb65f70d078ed. It preserves main's notification, dispute and exception-handling changes. The new layout migration is V8, after main's existing parking-lot V7; booking physical snapshots also use that service's next V8. No deployed migration checksum is modified.

## Acceptance still required

The original L04 review finding set R1–R15 has not received a complete current acceptance review. Several fixes were reported and unit probes passed, but this draft does not claim that the entire set is resolved. Before marking ready for merge, review:

- Feature flags on all explicit and automatic physical-allocation paths, plus release/drain behavior when flags or storage are unavailable.
- Consistent lock order across publication, capacity changes, mode activation and hold/release, proven with competing requests against MySQL.
- Booking/publish idempotency and compensation under transaction conflicts, retries and partial failures.
- Filter-chain/HTTP ownership, role, public/private source access and error-status contracts.
- Fresh/upgrade migration runs on real MySQL and a local HTTP flow from upload through publish, selected booking, edit and release.
- Linux symlink/path protections, hard-link publication and container recreation with a retained private volume; a coordinated off-host MySQL/file backup and restore.

Filesystem tests cover bounded writes/reads, immutable concurrent creates, checksum/generation verification, restart through a new adapter and failure handling. Windows symlink tests require OS privileges and may be skipped; they do not prove Linux behavior. No VPS rollout, cloud data import or Google billing operation is part of this PR. Flags remain off by default, and adding a volume overlay is an explicit operator action.

See [ADR 0006](../adr/0006-parking-layout-private-filesystem.md) and [the filesystem runbook](../deployment/parking-layout-filesystem.md). Test/build results for the exact PR branch are recorded in the PR description after validation; prior dirty-workspace results are not a substitute.

## Validation on this PR branch

- Focused parking-lot, booking and gateway test tasks, parking-lot bootJar and GCS dependency inspection completed successfully (1m15s; 9 tasks executed, 13 up-to-date). The executable JAR contains filesystem storage and the V8 layout migration, with no Firebase adapter or GCS SDK.
- parking-lot-service: 140 cases, 138 passed and 2 Windows symlink cases skipped. booking-service: 84 cases, 82 passed and 2 MySQL cases skipped. api-gateway: 6 passed. Cached results are identified by Gradle task status.
- The full backend test command failed at the unchanged IdentitySchemaMigrationTest: two tests require an accessible MySQL on localhost:3306. Identity has no changes in this PR; no tests were disabled or weakened to conceal the environment failure.
- Compose overlay syntax was validated; Linux/container/VPS and real MySQL L04 acceptance are still pending.
