# PayOS return and cancelled checkout recovery

Owner: SPH payment. `last_verified`: 2026-11-15. User-approved plan: `D:/DuAnOjt/plans/payos-return-recovery-ux-2026-11-15.md`.

## HTTP contract

Existing DRIVER endpoints and authentication stay unchanged:

- `POST /api/v1/payments` accepts `{ bookingId, paymentType: DEPOSIT | FINAL_PAYMENT }` and returns the wrapped checkout URL string. Client-supplied price, merchant and lot never override the booking-service quote.
- `POST /api/v1/payments/sync` accepts `{ bookingId, orderCode? }`. A supplied unknown order is NOT_FOUND; a supplied order belonging to a different booking is rejected. The authenticated driver must own the selected payment. Old return URLs resolve the current attempt of that booking/type, and any attempt requiring reconciliation blocks payment recovery.
- `PaymentDto` adds `canRetry` and `canResume` booleans. Both default false on other response paths. Sync grants them only after a matching PayOS order/amount, zero amountPaid, verified ownership and a current payable booking quote. `canRetry`: provider CANCELLED and recovery enabled. `canResume`: provider PENDING, local PENDING, saved checkout URL. Neither field is inferred from return URL flags.

The frontend may offer the approved recovery action, but POST payments always repeats provider/quote checks while holding a database lock. API/provider errors propagate; there are no demo checkout URL or success fallbacks.

## Attempt identity and concurrency

V18 is additive. `payment_attempt_groups.group_key` is `booking:{id}:{type}`; a unique-key upsert followed by SELECT FOR UPDATE serializes create/sync/webhook across service instances. Group locking precedes payment row locking. A locking repository read sees attempts committed by the previous lock holder under MySQL REPEATABLE READ.

`payment_order_codes` allocates fresh auto-increment order codes for ALL new real/demo payments. The migration seeds it above the largest historical orderCode. Existing codes, payments, ledger entries and idempotency keys are untouched. A new attempt retains the old payment and uses `booking:{id}:{type}:attempt:{orderCode}`. All older attempts must be provider-confirmed CANCELLED with zero received money before another attempt is created. A valid current PENDING URL is reused.

The booking quote determines price and deadline; retry does not extend a hold. Deposit checkout expiry is the original booking expiry interpreted in Asia/Ho_Chi_Minh. Final payment has no deposit countdown. Existing SUCCESS, REFUNDED, REQUIRES_REVIEW, partial/unknown funds, PROCESSING, invalid order/amount, expired booking and unavailable provider/ownership checks cannot create another charge.

Reservation is flushed before contacting PayOS. The create transaction commits that reservation on handled AppException, including a provider timeout; an ambiguous order remains PENDING without a checkout URL. A later request queries that same order and does not blindly create a different one. Unhandled database failures before the provider call roll back normally.

Paid callbacks are serialized with creation. A late paid older attempt or duplicate payment for the same booking/type is marked REQUIRES_REVIEW before booking confirmation or escrow credit. Existing refund/review/success statuses cannot be downgraded by a failed webhook. Cancellation sync alone emits no booking cancellation or wallet event.

## Operation and rollback

Apply V18 through normal Flyway startup before enabling the new binary. No manual migration against application data was run as part of the code change. Local automated MySQL checks use newly created `*_payos_recovery_it` / `*_payos_migration_it` schemas only.

`PAYOS_RECOVERY_ENABLED=false` (or `--app.payos-recovery-enabled=false`) disables creation of replacement attempts and retry permission while preserving existing pending checkouts, history and the order allocator. Retain V18 on rollback. Do not reset the allocator or delete attempts. Prefer disabling recovery to reverting to a binary that uses the old booking-derived order formula; uniqueness constraints still protect issued orders, but that old formula may conflict with newly allocated codes.

Tests: PaymentRecoveryTest; PaymentRecoveryIntegrationTest (real database two-request races and timeout persistence); PaymentAttemptMigrationTest (historical rows and allocator seed); PaymentAmountConsistencyTest; PayOsCancellationSyncTest; full payment-service suite. Provider calls are mocked; these checks do not transfer funds or certify live bank/webhook delivery.

Observed results: full service suite has 162 cases (159 passed, 3 conditional skips); an explicit isolated MySQL 8.4 run passed 6 migration/concurrency/timeout checks. The frontend's real-router browser tests use mocked payment APIs and must not be described as proof that the retained old local backend has been upgraded.

### Retained local database lineage

The existing local stack has applied V6/V7 resources absent from the current repository's canonical migration chain. Replacing its payment image failed and automatically restored the prior image (health UP). Application history was unchanged. A schema/history-only copy reproduced unresolved V6/V7; naive remapping additionally failed V17 checksum validation because the old per-request hold migration differs from current aggregate missing-hold SQL. V12–V15 dispute objects are absent, and V16 would duplicate the old outbox table if run blindly. The implemented retry API therefore remains inactive on this legacy local database pending a separately reviewed transition. No repair, ignore-validation configuration or history rewrite was applied. Evidence/next work: `D:/DuAnOjt/plans/payos-local-rollout-blocker-2026-11-15.md`.

Provider contract reference: [PayOS API](https://payos.vn/docs/api/), [Return URL](https://payos.vn/docs/du-lieu-tra-ve/return-url/).


### Runtime activation update — 2026-11-15

The initial retained-local blocker above is now resolved without repairing/renaming history. The same artifact contains an explicitly opt-in local V6/V7-compatible chain. A full-copy MySQL upgrade preserved all financial records and old history, strict validation and repeat migration passed, and the local payment container is now UP at V18. See [compatible lineage and rollout evidence](./payos-local-lineage.md). Canonical/default migrations are unchanged. Cancelled checkouts can now create a fresh eligible attempt on that local backend; old cancelled QR URLs remain unusable.
