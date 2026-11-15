# ADR 0007: Preserve PayOS attempts and serialize payment recovery

Date: 2026-11-15. Status: implemented following user approval of the PayOS return/recovery plan. Owner: SPH payment.

## Context

The old booking-derived orderCode and one stable idempotency key permitted one checkout only. A cancelled provider order could not be reused. Replacing that row would lose webhook identity/history; creating a second order without serialization would expose two-tab races and duplicate collection. The return page also needs an authoritative permission to offer recovery rather than interpreting query parameters.

## Decision

Keep every payment attempt. Add V18 group lock keys (booking/type) and an auto-increment order allocator seeded above historical codes. All real/demo new orders use the allocator. Create/sync/webhook acquire group then payment locks; locking reads see newly committed attempts under MySQL REPEATABLE READ. One current valid pending checkout is reused. Replacement requires all old orders to be provider-confirmed cancelled with zero received money and a fresh payable booking quote.

Persist the pending reservation before sending the provider request and retain it after handled provider errors/timeouts. An ambiguous attempt is queried again rather than silently replaced. Late older paid callbacks and additional successful attempts require reconciliation before booking confirmation/escrow credit. Existing terminal refund/review states stay terminal.

Extend sync DTO with default-false canRetry/canResume booleans. These are presentation capabilities; create repeats every check. Disable replacements using PAYOS_RECOVERY_ENABLED=false without deleting history or reverting the allocator. No new service, broker, dependency, cross-database query, pricing rule or hold extension is introduced.

## Consequences and validation

Two small payment-owned tables and an ordered lock are added. Provider reads hold the same short-lived transaction used by checkout creation, so failures must remain bounded by provider/client timeouts. Recovery rechecks all historical attempts; unusually large attempt histories may require operational rate limits rather than removing verification. Additive migrations stay in place on rollback.

H2 and MySQL 8.4 isolated-schema tests cover concurrent first create/retry, durable timeout reservations and allocator behavior; migration tests preserve historical orders. Unit tests cover mismatched return identities, cancellation with funds, old-return/current-attempt behavior, late webhooks and the recovery switch. Live bank settlement is outside these automated tests.

Contract and rollback detail: [PayOS recovery](../api-contracts/payos-payment-recovery.md).
