# Retained local Payment migration lineage

Owner: SPH payment. `last_verified`: 2026-11-15. Scope: the local V6/V7 database only.

## Why this is needed

The retained local image applied outbox as V6 and a per-request withdrawal hold as V7. Canonical source has outbox V16 and a different aggregate missing-hold V17, plus pending dispute migrations V12–V15. Relabelling V7 as V17 fails checksum validation. Running V16 over the V6-created table fails duplicate DDL.

The compatibility path keeps every applied version, script and checksum unchanged. It does not run repair, edit/delete schema-history rows or weaken Flyway validation.

## One artifact, explicitly selected lineage

Released V6/V7 resources are retained byte-for-byte under `db/local-v6-v7-history`, extracted from the existing local app.jar. Provenance SHA-256:

- V6: `6481671c9d59c472770ec2a526544670db3a1da498bf6bf3cd228757a3128aec`
- V7: `f9cfb7b363c8b72a2be433e5af6278bdfde5e025280f95bdb2fd8e748937c893`

Gradle processResources generates `db/local-v6-v7-compatible` from the unchanged canonical scripts plus those two resources, omitting only V16 because the identical outbox DDL is already represented by V6. It still includes canonical V17 and V18. The default `classpath:db/migration` remains unchanged. Both locations ship in the same bootJar/image.

Use `docker/compose/overlays/compose.payos-local-v6-v7.yaml` with the normal local compose configuration, or set `SPRING_FLYWAY_LOCATIONS=classpath:db/local-v6-v7-compatible` for this retained local database only. Do not use it for canonical databases or change default migration configuration globally. Future migration additions are copied by the build; never edit released V6/V7 resources.

## Verified local rollout

Before rollout, a full database snapshot was streamed to an isolated schema in the same private MySQL 8.4 instance. No record dump or credential was written into an artifact or printed. Six pending migrations (12,13,14,15,17,18) applied; strict validation succeeded and a second migrate applied zero.

Byte hashes of all payment, wallet, withdrawal, historical ledger-entry, journal and posting records were identical before/after on the copy. Existing V1–V7 history/version/checksum rows stayed identical. Missing hold adjustment was zero. Thus the local compatibility step did not add financial movements or reinterpret the old V7 as the newer V17.

The deployment helper required the exact copy-tested bootJar hash and application-history fingerprint, retained an unmigrated rollback database copy and the previous image, and updated only the local payment container. The new payment service passed health checks. Local metadata is in `D:/DuAnOjt/.artifacts/payos-recovery-local-image.json`; clone proof is in `.artifacts/payos-compatible-copy-proof.json` and `.artifacts/payos-compatible-copy.log`. No production/VPS operation or bank transfer was performed.

Rollback uses the retained previous image and its original environment. Keep additive columns/tables and all payment history; do not reset the allocator. A data restore requires a separately reviewed maintenance action, not an automatic deletion/rewrite.

## Retry behavior

Cancelled PayOS QR URLs stay cancelled. The booking payment action requests a fresh server-approved attempt while the original booking is payable. The old attempt remains in history, and active/unknown/partial/paid/review/refunded states cannot create an additional charge. A pending matching checkout is reused only after provider verification. Expired holds require a new booking, not a deadline extension.

Tests: `LegacyPaymentMigrationResourcesTest`, full payment-service tests, MySQL copy verification, and frontend cancelled-checkout journeys. Live bank transfer/webhook settlement is not part of this verification.
