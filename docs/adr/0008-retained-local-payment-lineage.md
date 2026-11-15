# ADR 0008: Preserve the released local V6/V7 Payment lineage

Date: 2026-11-15. Status: implemented for the retained local stack; pending repository review. Scope: payment-service only.

The user's cancelled-QR retry remained blocked because the retained local database applied a different released migration lineage. Canonical V16 duplicates the outbox already created by local V6, while old V7 and canonical V17 have different hold algorithms/checksums. Renaming applied history is incorrect.

Package two explicitly selected locations in the same artifact. Default db/migration stays canonical and unchanged. The opt-in db/local-v6-v7-compatible location is generated from canonical resources except V16, plus exact archived local V6/V7 resources. V6 represents the same released outbox DDL; canonical V17 still runs normally. No script/checksum/history row is edited and strict Flyway validation remains enabled. Local compose opts in; other environments retain their default location.

The chosen artifact applied six pending migrations to a full private MySQL copy. Every payment/wallet/withdrawal/ledger row and original V1–V7 history row remained byte-equivalent before/after; repeat migration applied zero. Only after that proof, a rollback database snapshot and prior image were retained, and the local service was updated and passed health checks.

Released archive bytes have regression tests and an LF-only `.gitattributes` rule so Windows checkout conversion cannot change those resources. Future canonical migration additions are copied by the build. This carries an explicit local-lineage maintenance obligation; do not expand it silently to other legacy databases or suppress validation errors. It changes no payment/deposit/deadline or money-posting rule.

Details, hashes, opt-in overlay and rollback: [local lineage contract](../api-contracts/payos-local-lineage.md).
