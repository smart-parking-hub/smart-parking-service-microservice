# Booking schedule pre-PR review

- Reviewed on 2026-11-17, based on origin/main `3ceb3cf`.
- Scope approved by the user: the complete booking/parking schedule change, its frontend dependencies and removal of booking request-rate blocking.
- Main was pulled before staging. A local Compose conflict was resolved by retaining main's Kafka/JWT configuration; schedule-related changes were then reviewed against the updated base.

## Findings resolved

| Severity | Finding | Resolution and evidence |
| --- | --- | --- |
| P1 | The post-checkout queue job marked work complete when Parking availability was unavailable or full, preventing a later retry. | Added an explicit success result for queue promotion; only completed attempts are marked. A cursor lets later lots progress while blocked work remains retryable. Three scheduler regressions cover failure, recovery and forward progress; the failure case was observed before the fix. |
| P2 | The migration preservation test covered demo bookings but did not deliberately exercise odd-minute/microsecond timestamps and decimal amounts, and compared only a subset of columns. | Added a sentinel legacy booking and comparison of every original column before/after V12. Verified on a fresh isolated MySQL schema. |
| P2 | The retained local startup helper omitted the final no-request-rate overlay and could start the old limiter image again. | Appended the overlay and documented its local-only image prerequisite. |

No unresolved P1/P2 defect was identified in the reviewed scope after these corrections. This is an engineering review and local verification, not production acceptance.

## Reviewed invariants

- Parking owns reservation allocation and actual occupancy in its own database. Booking owns lifecycle, money and the snapshotted policy; no cross-database joins or foreign keys.
- Mutation paths serialize against existing layout/group keys and enforce full-window physical-slot compatibility and the 30-minute buffer. Physical allocation is not inferred by combining free fragments.
- Existing pending/paid holds and overdue vehicles stay conservative until verified release or checkout; failed post-commit delivery is left for bounded reconciliation.
- Migration/adoption requires the internal token, retains legacy timestamps and check-in rights and is explicit per booking.
- Capacity/layout changes cannot invalidate outstanding promised intervals. Completed idempotent creates retain their booking and deadline.
- Removing request-rate enforcement does not remove ownership, account/vehicle hold policy, schedule conflicts, pending-submit protection or request-key idempotency.
- Public availability projections do not expose driver IDs, plates or booking IDs.
- CI now exercises the schedule concurrency and preservation scenarios on isolated MySQL schemas using the historical migration collation.

## Verification

| Command / environment | Outcome |
| --- | --- |
| Java 21, `gradlew.bat test --offline --console=plain --max-workers=2` after final corrections | BUILD SUCCESSFUL; 854 tests recorded, 841 passed, 13 environment-gated skips, zero failures/errors |
| Isolated MySQL 8.4 schedule suite and Booking V11-to-V12 upgrade | 14 reservation tests and one preservation test passed; existing application schemas were not used |
| Scoped staged whitespace and conflict-marker inspection | Passed |

The final full test run includes the three queue regressions. MySQL results were obtained with unique `sph_schedule_test_*` schemas; no destructive cleanup or live booking/payment was performed. The skipped checks remain explicitly environment gated, rather than being reported as executed.

## Release and compatibility

Release Parking V9 and its reservation API first, Booking V12 second, then the matching frontend schedule UI. The frontend PR requires these endpoints. Applied migrations were not rewritten. Follow [the rollout/rollback procedure](../deployment/booking-schedule-rollout.md); after accepting schedules, rolling back to an old all-day allocator requires draining/reconciling those commitments.

The local Compose image tags are retained local artifacts and are not registry publication instructions. This PR review did not deploy the new queue change or perform production operations. Existing local data and the separate parking photo/presentation changes were preserved.
