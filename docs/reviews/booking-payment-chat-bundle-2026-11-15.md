# Booking/payment delivery review

Owner: booking-service / payment-service. Last verified: 2026-11-15. Companion frontend: https://github.com/smart-parking-hub/smart-parking-app-v2/pull/83.

This PR delivers the remaining backend work approved in the booking/payment chat: Vietnam-clock booking/deadline/checkout fixes (previous closed/unmerged #49), PayOS payment-attempt recovery after cancellation, and persisted unpaid-hold abuse protection. It excludes unrelated parking-layout/storage changes in the shared workspace.

## Main safeguards

- Group locks serialize payment creation, sync, webhook and unpaid-release assessment. Fresh allocator order codes preserve old attempts and ledger links. Ambiguous/paid/partial/review/refunded results cannot silently create another charge.
- A new checkout rechecks driver ownership, the booking quote, provider order/amount/received money and original payment eligibility/deadline. Cancelled zero-paid attempts can be replaced; cancellation alone does not cancel the booking.
- Account and vehicle locks protect pending-hold quotas across simultaneous requests. A separate persisted rate window survives request rollback. READ_COMMITTED prevents absent-row gap-lock contention between independent drivers; request replay is re-read under lock.
- Only committed unpaid cancellation/expiry candidates can cause cooldown. Payment evidence is verified through token-protected internal APIs; unknown results are temporary verification states, and late funds revoke unpaid classification. No cross-service SQL or fake ledger credit is used.
- Booking V9 and Payment V18 are additive. Canonical migration resources are preserved. The retained local V6/V7 lineage is opt-in, byte-verified and LF-pinned; default/fresh schemas keep canonical Flyway.

## Verification

The isolated checkout passed the full affected Booking/Payment suites: 286 cases, 280 passed and 6 environment-gated skips, plus bootJar packaging. A separate run on four fresh local MySQL 8.4 schemas passed 16 checks for real locking, quota/rate/cooldown/replay, cancellation/expiry deduplication, payment retry races, timeout persistence and additive migrations. The new CI step repeats these MySQL scenarios in disposable databases. Provider boundaries are mocked; no bank transfer or production database mutation is part of these checks.

The frontend companion passed 758 unit/integration tests, build/typecheck and all 44 distinct selected browser scenarios after three stale compact-view assertions were corrected. Secrets/build artifacts/live snapshots are excluded by the explicit delivery manifest.

The first GitHub run exposed a UTC-host regression in legacy JDBC timestamp conversion. It was reproduced locally under `-Duser.timezone=UTC`: each rate-window read could shift the stored business wall-clock value. Both rate-window and release reads now use JDBC 4.2 `LocalDateTime` directly. Boundary assertions verify the original minute/30-minute deadlines rather than accepting a shifted deadline.

## Deployment / rollback

Deploy this backend before the frontend companion. Both services require the same `APP_INTERNAL_SERVICE_TOKEN`. Keep each environment's real DB/Flyway configuration. Apply the additive migrations through normal startup; use the compatibility location only for databases with the matching released V6/V7 lineage. Existing local copy-verification/rollback-image evidence is documented separately; it is not a production rollout.

Disable `PAYOS_RECOVERY_ENABLED` or `BOOKING_HOLD_POLICY_ENABLED` for a feature rollback while retaining historical orders, releases, allocator and migrations. Do not reset deadlines, rewrite Flyway history or remove financial records. This PR does not merge or trigger production deployment.
