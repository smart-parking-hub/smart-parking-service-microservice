# Withdrawal decisions and ordering

Owner: SPH; last_verified: 2026-11-13 (local API, MySQL/Kafka and browser).

## Decision event

Topic: `withdrawal-decisions.events`; dead letter topic: `withdrawal-decisions.events.DLT`. Produced only by Payment after a successful decision in the same database transaction, using `admin_event_outbox`. Notification consumes with group `notification-withdrawal-decisions` and the existing bounded retry/DLT factory.

Envelope: `eventId`, `eventType` (`WITHDRAWAL_APPROVED` or `WITHDRAWAL_REJECTED`), `timestamp`, `schemaVersion: 1`, `aggregateId`. Payload: `withdrawalId`, `merchantId`, positive `amount`, `currency: VND`. It excludes bank details and decision free text. Notification creates one IN_APP PAYMENT message with targetType WITHDRAWAL and targetId withdrawalId, owned by merchantId. Retry preserves eventId; a duplicate cannot create another record for the recipient.

## Server ordering

- `GET /api/v1/admin/withdrawals`: default `requestedAt,desc`; supported sort fields `requestedAt`, `amount`, `id`, with asc/desc directions. An ID tie-break keeps pagination stable. Unsupported fields fall back to requestedAt. Existing ADMIN authorization remains authoritative.
- `GET /api/v1/notifications`: default `createdAt,desc`, also supports `createdAt,asc`; the same ID direction breaks timestamp ties. Other client sort fields are ignored. Results and unread/read operations stay scoped to the authenticated user.
- Sort applies in the database before pagination. Page size limits remain unchanged. The chronological sort option extends ADR-0005's previous fixed-order feed contract; the default stays newest first.

Frontend resets to page zero when ordering changes and includes the sort in query keys. Admin withdrawals expose new/old and amount ascending/descending; Admin and Merchant notification pages expose new/old. Refresh uses existing endpoints.
