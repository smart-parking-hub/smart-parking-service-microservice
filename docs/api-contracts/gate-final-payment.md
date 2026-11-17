# Gate final payment and cash confirmation

Owner: SmartParkingHub. Last verified: 2026-11-18. Scope: SP-CHECKOUT-03A, local implementation; not deployed.

Reserved-time pricing, 20% deposit, overtime and cancellation/refund policies are unchanged. A 40,000 VND bill minus an 8,000 VND deposit leaves 32,000 VND, including early departure under the reserved-time policy. This change does not implement checkout refunds.

## Public contract

- `POST /api/v1/merchant/check-out` adds `bookingId` and nullable `finalPaymentMethod`. Repeated AWAITING_FINAL_PAYMENT returns the stored bill; repeated COMPLETED returns `amountDue=0` and does not release/promote/publish again.
- `POST /api/v1/merchant/confirm-cash` remains MERCHANT-only. Body `{bookingCode, parkingLotId, amount}` now requires a positive amount exactly matching the outstanding server bill. Older clients omitting amount receive validation failure. Ownership and lot are checked before payment coordination.
- `GET /api/v1/merchant/bookings/{id}` adds nullable `finalPaymentMethod` and `finalPaymentAmount`. Null on an old completed booking does not prove any payment method.
- Driver `POST /api/v1/payments` continues deriving FINAL_PAYMENT amounts from the booking quote. It rejects new collection while cash is PENDING or COMPLETED; deposit and refund behavior remain unchanged.

The gate no longer fabricates a PayOS URL or calls confirm-cash for QR. Drivers use the existing authenticated booking payment flow. The gate reads merchant booking status every three seconds while its dialog is active and has a manual check. It reports completion only after successful cash acknowledgement or server status COMPLETED; errors do not imply failure to collect and require checking before receiving more money.

## Internal coordination

New routes require `X-Internal-Service-Token`, checked nonblank and in constant time:

1. Booking calls `POST /api/v1/internal/payments/{bookingId}/cash-checkout` with `{merchantId, parkingLotId, amount}` derived from its owned booking and authenticated merchant. No booking lock spans this HTTP call.
2. Payment uses its existing FINAL_PAYMENT attempt-group lock. All preceding final attempts must be PayOS provider-confirmed CANCELLED with zero amountPaid and matching order/amount. Paid, partial, unknown, pending, review, refunded, non-PayOS and provider failures block cash.
3. Payment commits a PENDING cash receipt before the booking callback. Merchant/lot/amount cannot change on retry. Missing or timed-out callbacks keep the reservation and block new PayOS orders.
4. Payment calls `POST /api/v1/internal/bookings/{bookingId}/confirm-cash-checkout` outside its transaction. Booking locks its row, validates ownership/bill, records CASH/reference/amount, completes, releases the slot and publishes its existing completion outbox. A repeated accepted cash command does not repeat side effects.
5. True acknowledgement marks the receipt COMPLETED; explicit business rejection marks REJECTED. A scheduler cycles through up to 20 pending receipts per batch every 30 seconds (`app.cash-checkout-recovery-delay-ms`). Timeout alone never releases a cash reservation.

Create, sync and webhook share the group lock. Cash decision reads are locking current reads, avoiding old MySQL REPEATABLE_READ snapshots after waiting on a lock. Paid PayOS after cash selection goes to REQUIRES_REVIEW without escrow or another success event. Late external bank transfers remain a reconciliation case.

Internal `confirm-payment` adds paymentId for FINAL_PAYMENT. The first matching payment saves PAYOS/paymentId/amount on Booking; the same payment can replay after completion. Another/missing payment ID, CASH, or an unclassified historical completion is rejected. DEPOSIT compatibility is retained. A valid legacy PAYMENT_COMPLETED event with a payment ID can establish an empty PAYOS decision while awaiting final payment, but cannot replace another decision.

The final-payment event consumer now publishes BOOKING_COMPLETED via Booking outbox after completing/releasing, enabling existing idempotent settlement. Event shape/topic is unchanged.

## Schema, accounting and rollout

- Booking V11 adds nullable final_payment_method/reference/amount. No historical method is fabricated.
- Payment V20 adds cash_checkout_receipts keyed by booking ID, carrying merchant, lot, amount, status, updated_at and a recovery index.
- Cash is held by the merchant: no new payments row, GATEWAY_CLEARING posting or merchant wallet credit is created for it. This task does not invent a new fee collection policy for merchant-held cash; existing deposit settlement remains.
- Apply additive schemas, deploy Booking callback/decisions, then Payment coordinator/recovery, then FE. Old FE requests lacking amount are deliberately rejected. Clients have 3-second connection and 5-second read bounds plus circuit breakers.
- Keep compatible recovery while cash work is pending. Do not roll back to a payment version that ignores reservations. Keep additive schema/history; never delete journals/receipts to simulate rollback.
- Slot release is still an HTTP side effect without a shared database transaction. The pre-existing release-success/DB-commit-failure window needs separate slot reconciliation; this task does not claim to solve it.
- No real payment, deployment or historical repair is performed by these changes.
