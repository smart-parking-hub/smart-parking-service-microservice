# PayOS cancellation synchronization

Owner: SPH payment backend. Last verified: 2026-11-15.

Existing endpoint: authenticated DRIVER `POST /api/v1/payments/sync`. The request and response schemas are unchanged. Ownership and the existing payment row lock remain authoritative; UI callbacks/query flags cannot change payment status directly.

For the matching provider order, an authoritative PayOS `CANCELLED` response with `amountPaid = 0` changes only a local `PENDING` payment to existing status `FAILED`. A positive paid amount changes it to `REQUIRES_REVIEW` with a reconciliation reason. Missing paid amount, mismatched order, negative amount and existing terminal/review statuses do not get downgraded by cancellation handling. Existing SUCCESS and paid reconciliation paths are preserved.

No booking cancellation, slot release, wallet credit/debit, refund or payment-completed event is emitted by this cancellation branch. Cached cancelled links remain blocked by the existing createPaymentLink verification; no additional charge is automatically created. New charge-attempt policy is outside this fix.

Evidence: `PaymentServiceImpl.syncPayOSPayment`, `PayOsCancellationSyncTest`, existing `PaymentServiceImplTest`/`PaymentAmountConsistencyTest`. The zero-paid/partial-paid cancellation tests failed before implementation and passed afterward. Payment service suite: 146 passed, 2 configured skips.

Rollback: restore the previous application artifact; no schema migration or database rollback. Runtime containers need the new application build for the status mapping. Frontend also closes a navigated/error frame independently while server state is still pending.
