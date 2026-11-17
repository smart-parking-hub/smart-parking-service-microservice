# Driver wallet and deposit statements

Owner: payment / booking. last_verified: 2026-11-17. Status: implemented and verified locally; not deployed.

All public calls use the existing authenticated gateway and ApiResponse envelope. Driver identity comes from authentication, never a supplied owner ID. Money is VND. Page responses use content/currentPage/totalPages/totalElements with zero-based pages, size 1..100.

| Method / path | Role | Request | data |
|---|---|---|---|
| GET /api/v1/driver/wallet | DRIVER | — | availableAmount, heldAmount, currency, enabled |
| GET /api/v1/driver/wallet/transactions | DRIVER | page=0, size=20 | Page of id, bookingId, paymentId, entryType, amount, heldDelta, createdAt |
| POST /api/v1/payments/wallet | DRIVER | { bookingId } | PaymentDto: id/status identifies outcome to query |
| GET /api/v1/payments/wallet?bookingId={id} | DRIVER | positive bookingId | Latest WALLET PaymentDto belonging to authenticated driver and booking; no attempt means null data (omitted by ApiResponse NON_NULL) |
| GET /api/v1/payments/{id} | DRIVER owner | — | PaymentDto; cross-owner access forbidden |
| GET /api/v1/merchant/revenue/deposits | MERCHANT | page=0, size=20 | Own deposit statements |
| GET /api/v1/admin/deposits | ADMIN | page=0, size=20 | All deposit statements |
| POST /api/v1/admin/refunds/{id}/retry-wallet | ADMIN | — | RefundRequestDto; rechecks all funding/source conditions |

Wallet history types: REFUND increases available; HOLD moves available to held; PAYMENT consumes held (available delta zero); RELEASE restores held to available. Do not sum a partial history page to derive the balance. A new driver reads zero without creating a record on GET.

POST wallet reserves locally and attempts booking confirmation. SUCCESS is confirmed capture; PENDING means the hold remains for recovery; FAILED means booking rejected the attempt and the hold was released. A pending PayOS attempt blocks a wallet payment. Insufficient funds rolls back the reservation and payment creation. Errors never authorize a second payment source. POST with the same booking does not debit again.

Recovery update (2026-11-18): clients look up the wallet attempt by booking before offering payment and after a POST response is lost, and repeat the lookup while the payment screen is open. Reads never accept a client-supplied driverId. Empty data is a snapshot that no own wallet attempt is currently recorded, not proof that an earlier HTTP request can never arrive. Server-side attempt locks remain authoritative. After any POST outcome, refresh balance, booking and attempt; do not auto-retry a debit. A failed lookup or PENDING/REQUIRES_REVIEW blocks another payment source. Refreshing the browser does not require remembering the original paymentId.

Booking cancellation accepts acceptWalletRefund=true on the existing POST /api/v1/bookings/{id}/cancel. Policies remain 100% at/before T−2h, 50% in (T−2h,T), zero at/after T/no-show/unpaid. The server uses the actual cancellation instant. Existing clients without the consent flag preserve manual bank handling. BookingDto includes refundChannel; refund status events include refundChannel as an additive field.

PaymentDto on GET /api/v1/payments includes optional refundAmount/refundStatus/refundChannel/refundReference. A partially refunded source remains SUCCESS, so the refund fields must be shown independently. COMPLETED with channel WALLET means credited to the internal wallet, not money transferred to a bank.

Deposit statement fields: paymentId, bookingId, refundId, paymentStatus, gateway, depositCollected, refundAmount (policy obligation), refundCompleted (posted payout/reclassification), refundOutstanding (booked payable), refundStatus, refundChannel, escrowRemaining, depositPlatformFee, depositMerchantNet, reconciled. Values come from payment-linked postings including refund adjustments; no current-rate recalculation. The conservation check is depositCollected = refundCompleted + refundOutstanding + escrowRemaining + depositPlatformFee + depositMerchantNet. A policy request awaiting funding can still need reconciliation even when booked journal amounts balance. This is distinct from settled batch snapshots and merchant bank withdrawals.

Internal-only POST /api/v1/internal/bookings/{id}/confirm-wallet-payment requires X-Internal-Service-Token and { paymentId, driverId, merchantId, amount }. It validates booking ownership and amount and persists accepted/rejected decisions by paymentId. This route is not added to the public gateway.
