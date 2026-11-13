# PayOS checkout amount validation

- Owner: SPH
- last_verified: 2026-11-14
- Scope: POST /api/v1/payments; additive validation, unchanged request/response shape.

The booking service owns the amount due. A 20,000 VND estimate produces a 4,000 VND deposit; no 50,000 VND minimum is applied to deposits. The withdrawal minimum is a separate policy. Payment creation continues to ignore client-supplied amount/merchant/lot fields and uses the authenticated booking quote.

Before returning a cached PENDING checkout, payment-service now revalidates the booking amount and merchant/lot against the stored payment, then queries PayOS to verify the order code, amount and PENDING status. A mismatch is rejected with HTTP 409. An unavailable verification request returns PAYMENT_FAILED/HTTP 400 through the existing error envelope. It never creates another charge as a retry fallback.

A newly created PayOS response must match the requested amount and order code and include a nonblank checkout URL before it is returned or saved. Invalid responses return PAYMENT_FAILED/HTTP 400. Existing successful-payment redirects, payment demo confirmation, synchronization and webhook behavior are preserved.

Cached links with stale amount/merchant/lot are deliberately blocked for investigation; this change neither replaces/cancels external orders nor rewrites historical payment rows. Link validation adds a booking lookup and one PayOS GET when reopening a cached checkout. If those services are unavailable, checkout remains closed and the user can retry.

## Evidence and limits

- PaymentAmountConsistencyTest covers 4,000 VND sent exactly to PayOS despite a client value of 50,000, provider amount/order mismatch, stale cached amount, provider mismatch, matching cache reuse, provider failure/cancellation, changed merchant/lot and missing URL.
- ParkingChargeTest asserts 20,000 x 20% = 4,000.
- Frontend PayOsPaymentPanel test asserts the 4,000 VND display and no iframe when the server rejects the stale link. Its selector accepts Intl's nonbreaking whitespace, and mocks are reset between tests.
- The original 8-test reproducer failed 7 cases before the fix. Final validation is recorded in the PR review notes.
- Provider calls are mocked. The reported local 4,000-to-50,000 transaction was not inspected because the local services/browser transaction were no longer running and its booking/link was not provided. This validates the identified code paths, not the cause of that exact historical transaction.
- No real payment, database repair or deployment was performed. Rollback reverts the added checkout validations and their tests; no schema rollback is required.
