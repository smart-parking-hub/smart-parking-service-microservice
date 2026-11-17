# Unpaid hold protection

Owner: booking-service (policy), payment-service (payment evidence). Last verified: 2026-11-17.
Status: approved implementation. Evidence: `UnpaidHoldPolicyIntegrationTest`, `UnpaidHoldPolicyTest`, `UnpaidHoldMigrationTest`, `HoldPolicyContractTest`, `UnpaidHoldAssessmentServiceTest`, `UnpaidHoldInternalContractTest`.

## Rules

| Scope | Default | Result |
| --- | --- | --- |
| Vehicle, across all lots | One `PENDING_PAYMENT` booking | 409; link to the existing booking only when it belongs to the caller |
| Driver, across all lots | Three `PENDING_PAYMENT` bookings | 409; resolve existing holds first |
| Driver + lot | Two confirmed unpaid releases in 60 minutes | 429 until 30 minutes after the threshold release |
| Driver request rate | Removed by user request (2026-11-17) | No request-count wait during booking creation |

Booking creation no longer calls HoldRateLimiter. Historical rate-window rows, including exhausted/future-dated rows, are ignored and are not incremented by new attempts. The legacy evaluator/schema remain for compatibility; removing the API-path dependency is intentional. No schema migration or manual row deletion is required. Ownership, capacity, schedule conflict checks, transactional account/vehicle locks, request-key idempotency and pending-request double-submit protection remain active.

The server owns all decisions. Changing vehicle, requestId, browser tab or restarting the service does not bypass the persisted driver/lot cooldown. This does not claim to prevent coordinated abuse using multiple accounts or identities.

Quota includes pending bookings during the existing expiry grace period: they still occupy capacity until terminal cleanup. Existing pending bookings count toward quota. Historical abandoned bookings are not backfilled as violations. The 15-minute payment deadline, three-minute expiry grace, pricing, deposit, refund, check-in/out rules are unchanged.

Only a committed cancellation or expiry **from PENDING_PAYMENT** creates a candidate release. Closing/cancelling a PayOS session alone creates no candidate. A failed create/slot RPC creates no candidate. Paid no-shows are excluded. Candidate insertion is idempotent by booking ID and does not move the original release time.

UNPAID requires verified evidence. Paid, partial, refunded or review attempts are protected. Unknown provider results produce no strike. If two possible releases require verification, creation returns temporary 503 with a one-minute retry time, rather than an abuse cooldown. UNPAID assessments are rechecked on a subsequent create and by a bounded scheduler; later money changes the assessment to PROTECTED. The cooldown is derived from immutable release times; pressing retry does not extend it.

## Public contract

The request for `POST /api/v1/bookings` is unchanged. Successful, completed idempotent replay returns the original booking before quota checks and never extends its deadline. New attempts do not consume a technical rate window. In-flight duplicate requests still cannot create another hold.

Restriction response (normal ApiResponse envelope):

```json
{
  "success": false,
  "code": "BOOKING_HOLD_COOLDOWN",
  "message": "Bạn đã bỏ hoặc hủy 2 lượt giữ chỗ chưa thanh toán tại bãi này trong 60 phút. Vui lòng chờ trước khi giữ chỗ mới.",
  "data": { "reason": "COOLDOWN", "retryAt": "2026-11-15T14:00:00" }
}
```

`retryAt` is Vietnam wall-clock time (`Asia/Ho_Chi_Minh`, UTC+7), consistent with existing booking timestamps. Timed responses include `Retry-After` in seconds. Nullable fields may be absent due to envelope serialization.

| reason/code suffix | HTTP | Additional data |
| --- | --- | --- |
| VEHICLE_LIMIT | 409 | existingBookingId only for a caller-owned hold |
| ACCOUNT_LIMIT | 409 | none |
| COOLDOWN | 429 | retryAt |
| RATE_LIMIT (legacy only) | 429 from an older server | No longer emitted by the booking creation path |
| PAYMENT_CHECK_PENDING | 503 | retryAt; not an abuse finding |

A legacy RATE_LIMIT response does not lock the frontend or display a spam warning/countdown: the form retains its inputs and reports that creation failed, with an immediate explicit retry. No automatic booking or fake success is introduced. Other restrictions retain their server message/deadline and ticket link. Regression evidence: `UnpaidHoldPolicyIntegrationTest.bookingCreationIgnoresAnExhaustedFutureRateWindowAndAllowsImmediateRetry`, frontend `BookingHoldRestrictionNotice.test.tsx` and `booking-rate-limit.spec.ts`.

## Internal contracts and payment safety

Both endpoints require the shared `APP_INTERNAL_SERVICE_TOKEN` via `X-Internal-Service-Token`. Missing, blank or incorrect tokens are rejected. User ID/role headers do not substitute for the service token.

- Booking `GET /api/v1/internal/bookings/{id}/unpaid-hold-state`: `{ bookingId, driverId, status, unpaidReleaseCandidate }` in ApiResponse. Read-only, without booking row locks.
- Payment `POST /api/v1/internal/payments/{id}/unpaid-hold-assessment`: `{ disposition: "UNPAID" | "PROTECTED" | "UNKNOWN" }` in ApiResponse. Payment obtains its existing booking/DEPOSIT attempt-group lock before re-reading the booking state. Non-terminal/non-candidate bookings are never cancelled by this assessment.

Payment inspects all deposit attempts. Local SUCCESS/REFUNDED/REQUIRES_REVIEW protects the release. Otherwise it checks PayOS order identity, original amount and a nonnegative reported amountPaid. Positive/PAID/UNDERPAID evidence becomes REQUIRES_REVIEW with a reconciliation reason. A zero-paid PENDING link is cancelled, then the returned evidence is checked again. Only zero-paid CANCELLED/EXPIRED attempts, or no attempts under the group lock, prove UNPAID. PROCESSING, provider failures, missing/mismatched data and unconfirmed cancellation remain UNKNOWN. Assessment creates no ledger entry or fake payment-success event.

No service queries another database. Booking uses a bounded RestClient (2s connect/5s read) and the existing Resilience4j Spring Cloud starter pattern, newly enabled for booking-service. This is the same BOM-managed dependency already used by payment-service; no new broker, cache or policy engine is introduced. A raw HTTP-only alternative would have no circuit breaker when payment is unavailable.

## Concurrency and schema ownership

Booking V9 adds:

- `booking_hold_policy_locks`: stable serialization keys, account before vehicle; separate RATE keys.
- `booking_hold_rate_windows`: per-driver anchored window; separate REQUIRES_NEW transaction retains attempted-request counts after business rollback.
- `booking_unpaid_hold_releases`: immutable original release, one row per booking, current assessment/last checked time.
- Composite booking indexes for vehicle/status and driver/status quota reads.

Creation transactions use READ_COMMITTED. Explicit key locks serialize conflicting creates and a second locked requestKey lookup catches in-flight replay. This avoids MySQL absent-row gap-lock deadlocks between unrelated drivers. Rejected gates run before booking/slot creation. Business failures still roll back the booking transaction and retain existing compensation behavior. RATE locks/windows are no longer consulted by this path.

The scheduler processes at most 100 recent unprotected candidates per cycle (default 60s delay), each in its own verification transaction. Confirmed PROTECTED rows are terminal for this policy; UNPAID/UNKNOWN rows are revisited during the 90-minute horizon. Gate checks also verify the caller/lot releases, so the scheduler is not the sole enforcement path. Database records remain available for audit; no destructive retention job is introduced.

## Configuration and rollback

Active Booking defaults are environment configurable:

| Variable | Default |
| --- | --- |
| BOOKING_HOLD_POLICY_ENABLED | true |
| BOOKING_HOLD_MAX_PENDING | 3 |
| BOOKING_HOLD_ABANDONMENT_THRESHOLD | 2 |
| BOOKING_HOLD_WINDOW_MINUTES | 60 |
| BOOKING_HOLD_COOLDOWN_MINUTES | 30 |

Legacy BOOKING_HOLD_RATE_LIMIT and BOOKING_HOLD_RATE_WINDOW_SECONDS have no effect on booking creation after removal of the rate-limiter call.

Deploy payment first, then booking. Preserve shared token and each service's existing DB/Flyway configuration. Validate migration on private local copies and retain previous images/database snapshots before local activation. Local Payment's retained V6/V7-compatible lineage remains required; this feature adds no Payment migration.

Rollback gate/recheck with `BOOKING_HOLD_POLICY_ENABLED=false`, or restore the previous images. Keep additive Booking V8/V9 schema, immutable releases and original booking/payment/ledger history; do not repair/rewrite Flyway or reset deadlines. A full database restore requires separate reconciliation if new transactions occurred after the snapshot.

## Verified checks

TDD quota regressions failed on the old implementation, then passed. H2 integration covers concurrent same vehicle/account, independent drivers, replay, cancel/expiry deduplication, temporary unknown evidence, late-payment revocation, original rate boundary and transaction rollback. MySQL 8.4 repeats the concurrency suite and verifies V8→V9 preserves original booking fields and applies no retrospective penalties. Browser coverage uses intercepted API responses on the real `/parking` route at 390px/1440px; provider calls are mocked and no bank transfer is performed.
